package com.pengxh.autodingding.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import com.pengxh.autodingding.extensions.openApplication
import com.pengxh.autodingding.ui.MainActivity
import com.pengxh.autodingding.utils.Constant
import com.pengxh.autodingding.utils.NetworkUtils
import com.pengxh.kt.lite.extensions.timestampToTime
import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MqttService : Service() {

    //要和main activity进行绑定
    private val binder = MqttBinder()
    private var callback: MyMqttCallback? = null

    interface MyMqttCallback {
        fun onMqttStatusChanged(status: String)
    }

    fun setMyMqttCallback(cb: MyMqttCallback) {
        this.callback = cb
    }

    inner class MqttBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private val channelId = "MqttServiceChannel"

    //mqtt set
    private lateinit var mqttServerUrl: String
    private lateinit var mqttClientId: String
    private lateinit var user: String
    private lateinit var pwd: String

    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var properties: Properties

    //mqtt配置文件导入
    private lateinit var mqttTopicTest: String
    private lateinit var mqttTopicTestResult: String
    private lateinit var mqttTopicCheckAppAlive: String
    private lateinit var mqttTopicCheckAppAliveResult: String
    private lateinit var mqttTopicDark: String
    private lateinit var mqttTopicDarkResult: String
    private lateinit var mqttTopicLastWill: String

    private var isConnecting = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        // MQTT 配置文件导入
        Log.d("AuToDark.connectToMqtt", "加载 MQTT 配置")
        loadProperties()

        //连接
        connectToMqtt()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundService() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MQTT Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("MQTT客户端正在运行")
            .setContentText("正在监听MQTT消息")
            .setSmallIcon(android.R.drawable.ic_menu_add) // 替换为你的图标
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun loadProperties() {
        properties = Properties()
        try {
            assets.open("config.properties").use { inputStream ->
                properties.load(inputStream)
                // 将配置文件中的值赋给类属性
                mqttServerUrl = properties.getProperty("mqttServerUrl") ?: ""
                mqttClientId = properties.getProperty("mqttClientId") ?: ""
                user = properties.getProperty("user") ?: ""
                pwd = properties.getProperty("pwd") ?: ""
                mqttTopicTest = properties.getProperty("mqttTopicTest") ?: ""
                mqttTopicTestResult = properties.getProperty("mqttTopicTestResult") ?: ""
                mqttTopicCheckAppAlive = properties.getProperty("mqttTopicCheckAppAlive") ?: ""
                mqttTopicCheckAppAliveResult = properties.getProperty("mqttTopicCheckAppAliveResult") ?: ""
                mqttTopicDark = properties.getProperty("mqttTopicDark") ?: ""
                mqttTopicDarkResult = properties.getProperty("mqttTopicDarkResult") ?: ""
                mqttTopicLastWill = properties.getProperty("mqttTopicLastWill") ?: ""
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun connectToMqtt() {
        Log.d("AuToDark.connectToMqtt", "尝试连接到 MQTT 代理")

        if (isMqttConnected() || isConnecting) {
            Log.d("AuToDark.connectToMqtt", "已经连接或正在连接中，取消连接请求")
            return
        }

        isConnecting = true
        Log.d("AuToDark.connectToMqtt", "设置连接状态为正在连接")

        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.e("AuToDark.connectToMqtt", "网络不可用，无法连接到 MQTT 代理")
            return
        }


        mqttClient = MqttAndroidClient(applicationContext, mqttServerUrl, mqttClientId, Ack.AUTO_ACK)
        val options = MqttConnectOptions().apply {
            isCleanSession = false
            connectionTimeout = 10
            keepAliveInterval = 20
            userName = user
            password = pwd.toCharArray()

            // 设置遗嘱消息
            val willQoS = 1 // 设置 QoS 为 1

            // 获取当前时间戳
            val willMessage = "darkPhone_offline_at_" + System.currentTimeMillis().timestampToTime()

            setWill(mqttTopicLastWill, willMessage.toByteArray(), willQoS, true)
        }

        try {
            Log.d("AuToDark.connectToMqtt", "连接到 MQTT 代理: $mqttServerUrl")
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("AuToDark.connectToMqtt", "MQTT 连接成功")
                    isConnecting = false

                    val topicsToSubscribe = arrayOf(mqttTopicTest, mqttTopicCheckAppAlive,mqttTopicDark,mqttTopicDarkResult)
                    val qosLevels = intArrayOf(1,1,1,1) // QoS 级别
                    Log.d("AuToDark.connectToMqtt", "连接成功，开始订阅主题: ${topicsToSubscribe.joinToString()}")
                    subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("AuToDark.connectToMqtt", "MQTT 通信失败: ${exception?.message}")
                    isConnecting = false
                }
            })
        } catch (e: MqttException) {
            Log.e("AuToDark.connectToMqtt", "MQTT 连接异常: ${e.message}")
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.e("AuToDark.connectToMqtt", "MQTT 连接丢失: ${cause?.message}，将自动尝试重连")
                // 开始重连
                startReconnect()
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msg = String(it.payload) // 将消息体转换为字符串
                    Log.d("AuToDark.connectToMqtt", "收到主题 $topic 的消息: $msg")

                    when (topic) {
                        mqttTopicDark -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicDark 的消息，打开相关应用")
                            openApplication(Constant.DING_DING)
                        }
                        mqttTopicTest -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicTest 的消息，发布测试消息")
                            publishMessage(mqttTopicTestResult, "darkPhone_testCheck", 1)
                        }
                        mqttTopicCheckAppAlive -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicCheckAppAlive 的消息，检查订阅主题的设备是否都正常连接")
                            publishMessage(mqttTopicCheckAppAliveResult, "darkPhone_alive", 1)
                        }
                        mqttTopicDarkResult -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicDarkResult 的消息")
                        }
                        else -> {

                        }
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })
    }

    private fun isMqttConnected(): Boolean {
        Log.d("AuToDark.connectToMqtt", "检查 MQTT 连接状态")

        return try {
            val isConnected = ::mqttClient.isInitialized && mqttClient.isConnected
            Log.d("AuToDark.connectToMqtt", "MQTT 连接状态: $isConnected")
            isConnected
        } catch (e: UninitializedPropertyAccessException) {
            Log.e("AuToDark.connectToMqtt", "MQTT 客户端未初始化，连接状态为 false")
            false
        }
    }

    private var reconnectHandler: Handler? = null
    private var reconnectRunnable: Runnable? = null
    private fun startReconnect() {
        reconnectHandler = Handler(Looper.getMainLooper())

        reconnectRunnable = object : Runnable {
            override fun run() {
                if (!mqttClient.isConnected) {
                    Log.d("AuToDark.connectToMqtt", "正在重连...")
                    connectToMqtt() // 尝试重新连接
                    reconnectHandler?.postDelayed(this, 10000) // 10秒后重试
                }
            }
        }

        reconnectHandler?.post(reconnectRunnable!!)
    }

    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("AuToDark.connectToMqtt", "成功订阅主题: ${topics.joinToString(", ")}")
                    callback?.onMqttStatusChanged("成功订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("AuToDark.connectToMqtt", "订阅失败: ${exception?.message}")
                    callback?.onMqttStatusChanged("订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e("AuToDark.connectToMqtt", "订阅异常: ${e.message}")
            e.printStackTrace()
            callback?.onMqttStatusChanged("订阅异常: ${e.message}")
        }
    }

    //mqtt解除订阅
    /**
     * use:
     * private fun someMethodToUnsubscribe() {
     *     val topicsToUnsubscribe = arrayOf("your/topic1", "your/topic2") // 替换为要解除订阅的主题
     *     unsubscribeFromTopics(topicsToUnsubscribe)
     * }
     */
    private fun unsubscribeFromTopics(topics: Array<String>) {
        Log.d("AuToDark.connectToMqtt", "尝试解除订阅主题: ${topics.joinToString(", ")}")

        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("AuToDark.connectToMqtt", "成功解除订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("AuToDark.connectToMqtt", "解除订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e("AuToDark.connectToMqtt", "解除订阅异常: ${e.message}")
            e.printStackTrace()
        }
    }


    //mqtt 发布
    fun publishMessage(topic: String, message: String, qos: Int = 1) {
        Log.d("AuToDark.connectToMqtt", "尝试发布消息到主题 $topic: $message")

        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(topic, mqttMessage, null, null)
            Log.d("AuToDark.connectToMqtt", "消息发布成功: $message")
        } catch (e: MqttException) {
            Log.e("AuToDark.connectToMqtt", "消息发布失败: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.putExtra("from_onTaskRemoved", true)
        applicationContext.startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            // 清理操作，停止重连
            reconnectHandler?.removeCallbacks(reconnectRunnable!!)
            //断开连接
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}
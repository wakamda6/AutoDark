package com.autodark.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.*
import androidx.annotation.RequiresApi
import com.autodark.extensions.openApplication
import com.autodark.ui.MainActivity
import com.autodark.utils.Constant
import com.autodark.utils.NetworkUtils
import com.pengxh.kt.lite.extensions.timestampToTime
import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.IOException
import java.util.*
import com.autodark.utils.LogUtils
import android.util.Log
import com.pengxh.kt.lite.extensions.timestampToCompleteDate
import com.pengxh.kt.lite.extensions.timestampToDate

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // 创建一个 Intent 来重新启动服务
        val restartServiceIntent = Intent(applicationContext, this::class.java)
        restartServiceIntent.putExtra("restart", true) // 可选参数，用于传递信息
        applicationContext.startService(restartServiceIntent) // 重新启动服务
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

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    var isConnecting = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        // MQTT 配置文件导入
        LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "加载 MQTT 配置")
        loadProperties()

        // 初始化 ConnectivityManager 和 NetworkCallback
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onReceive", "网络连接可用")

                // 检查 MQTT 客户端是否已连接
                if (!isMqttConnected() && !isConnecting){
                    LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onReceive", "MQTT 尚未连接，尝试连接")
                    connectToMqtt()
                }else{
                    LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onReceive", "MQTT 已连接")
                }
            }

            override fun onLost(network: Network) {
                // 网络丢失时可以选择执行其他操作
                LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onLost", "网络丢失,正在取消连接")
                if (!isMqttConnected()) {
                    mqttClient.disconnect()
                    LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onLost", "MQTT 连接已断开")
                }
            }
        }
        // 注册网络回调
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        //连接
        if (!isMqttConnected() && !isConnecting){
            LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onReceive", "MQTT 尚未连接，尝试连接")
            connectToMqtt()
        }else{
            LogUtils.log(Log.DEBUG,"AuToDark.NetworkChangeReceiver.onReceive", "MQTT 已连接")
        }
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

    fun connectToMqtt() {
        LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "尝试连接到 MQTT 代理")

        if (isMqttConnected() || isConnecting) {
            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "已经连接或正在连接中，取消连接请求")
            return
        }

        isConnecting = true
        LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "设置连接状态为正在连接")

        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.e("AuToDark.connectToMqtt", "网络不可用，无法连接到 MQTT 代理")
            return
        }


        mqttClient = MqttAndroidClient(applicationContext, mqttServerUrl, mqttClientId, Ack.AUTO_ACK)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 10
            userName = user
            password = pwd.toCharArray()

            // 设置遗嘱消息
            val willQoS = 1 // 设置 QoS 为 1

            // 获取当前时间戳
            val willMessage = "darkPhone_offline_at_" + System.currentTimeMillis().timestampToCompleteDate()

            setWill(mqttTopicLastWill, willMessage.toByteArray(), willQoS, true)
        }

        try {
            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "连接到 MQTT 代理: $mqttServerUrl")
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "MQTT 连接成功")
                    isConnecting = false

                    val topicsToSubscribe = arrayOf(mqttTopicTest, mqttTopicCheckAppAlive,mqttTopicDark)
                    val qosLevels = intArrayOf(1,1,1) // QoS 级别
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
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msg = String(it.payload) // 将消息体转换为字符串
                    LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "收到主题 $topic 的消息: $msg")

                    when (topic) {
                        mqttTopicDark -> {
                            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "处理主题 $mqttTopicDark 的消息，打开相关应用")
                            openApplication(Constant.DING_DING)
                        }
                        mqttTopicTest -> {
                            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "处理主题 $mqttTopicTest 的消息，发布测试消息")
                            publishMessage(mqttTopicTestResult, "darkPhone_testCheck", 1)
                        }
                        mqttTopicCheckAppAlive -> {
                            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "处理主题 $mqttTopicCheckAppAlive 的消息，设备是否都正常连接")
                            publishMessage(mqttTopicCheckAppAliveResult, "darkPhone_alive", 1)
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

    fun isMqttConnected(): Boolean {
        LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "检查 MQTT 连接状态")

        return try {
            val isConnected = ::mqttClient.isInitialized && mqttClient.isConnected
            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "MQTT 连接状态: $isConnected")
            isConnected
        } catch (e: UninitializedPropertyAccessException) {
            Log.e("AuToDark.connectToMqtt", "MQTT 客户端未初始化，连接状态为 false")
            false
        }
    }

    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "成功订阅主题: ${topics.joinToString(", ")}")
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
    private fun unsubscribeFromTopics(topics: Array<String>) {
        LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "尝试解除订阅主题: ${topics.joinToString(", ")}")

        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "成功解除订阅主题: ${topics.joinToString(", ")}")
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
        LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "尝试发布消息到主题 $topic: $message")

        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(topic, mqttMessage, null, null)
            LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "消息发布成功: $message")
        } catch (e: MqttException) {
            Log.e("AuToDark.connectToMqtt", "消息发布失败: ${e.message}")
        }
    }

    fun publishMqttDarkResult(message: String, qos: Int = 1) {
        //mqtt 发布
        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(mqttTopicDarkResult, mqttMessage, null, null)
            LogUtils.log(Log.DEBUG,"AuToDark.publishMqttDarkResult","消息发布成功: $mqttTopicDarkResult:$message")
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,"AuToDark.publishMqttDarkResult","消息发布失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // 注销网络回调
            connectivityManager.unregisterNetworkCallback(networkCallback)
            // 取消订阅
            unsubscribeFromTopics(arrayOf(mqttTopicTest, mqttTopicCheckAppAlive, mqttTopicDark))
            //断开连接
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}
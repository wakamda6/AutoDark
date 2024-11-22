package com.autodark.service

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
import com.autodark.utils.Constant
import com.autodark.utils.NetworkUtils
import com.autodark.R
import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.IOException
import java.util.*
import com.autodark.utils.LogUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.content.ContextCompat
import com.pengxh.kt.lite.extensions.timestampToCompleteDate
import com.pengxh.kt.lite.utils.WeakReferenceHandler

class MqttService : Service(), Handler.Callback  {
    private val channelId = "MqttServiceChannel"
    private val kTag = "AuToDark.MqttService"

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

    private var isConnecting = false
    private var isConnected = false

    //前台显示运行时间
    private var notificationManager: NotificationManager? = null
    private val weakReferenceHandler by lazy { WeakReferenceHandler(this) }
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var runningTime = 0L
    private lateinit var updateRunnable: Runnable

    //要和main activity进行通信
    private var callback: MyMqttCallback? = null
    interface MyMqttCallback {
        fun onMqttStatusChanged(status: String)
    }
    fun setMyMqttCallback(cb: MyMqttCallback) {
        this.callback = cb
    }

    //要和main activity进行绑定
    private val binder = MqttBinder()
    inner class MqttBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        // MQTT 配置文件导入
        LogUtils.log(Log.DEBUG,kTag, "加载 MQTT 配置")
        loadProperties()

        //连接
        if (!isMqttConnected() && !isConnecting){
            LogUtils.log(Log.DEBUG,kTag, "MQTT 尚未连接，尝试连接")
            connectToMqtt()
        }else{
            LogUtils.log(Log.DEBUG,kTag, "MQTT 已连接")
        }

        // 初始化 ConnectivityManager 和 NetworkCallback
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 检查 MQTT 客户端是否已连接
                if (!isMqttConnected() && !isConnecting){
                    LogUtils.log(Log.DEBUG,kTag, "网络连接可用，尝试连接mqtt服务器")
                    connectToMqtt()
                }else if(isConnecting){
                    LogUtils.log(Log.DEBUG,kTag, "网络连接可用，但MQTT 正在连接")
                }else if(isMqttConnected()){
                    LogUtils.log(Log.DEBUG,kTag, "网络连接可用，但MQTT 已连接")
                }
            }

            override fun onLost(network: Network) {
                // 网络丢失时可以选择执行其他操作
                if (!isMqttConnected()) {
                    mqttClient.disconnect()
                    LogUtils.log(Log.WARN,kTag, "网络丢失,MQTT 已主动断开连接")
                }
            }
        }
        // 注册网络回调
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        mStartForegroundService()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun mStartForegroundService() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MQTT Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager?.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MqttService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, notificationIntent)
        } else {
            startService(notificationIntent)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


            notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MQTT客户端正在运行")
            .setContentText("正在监听MQTT消息")
            .setSmallIcon(R.mipmap.logo) // 替换为你的图标
            .setContentIntent(pendingIntent)
//            .setOnlyAlertOnce(true)
            .setPriority(PRIORITY_HIGH)
                .setOngoing(true)

        val notification = notificationBuilder?.build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //记录通知被创建的时间
        if (!::updateRunnable.isInitialized) {
            runningTime = System.currentTimeMillis()
            updateRunnable = object : Runnable {
                override fun run() {
                    updateNotification()
                    weakReferenceHandler.postDelayed(this, 1000L * 60)
                }
            }
            weakReferenceHandler.post(updateRunnable)
        }

        return START_STICKY
    }

    private fun updateNotification() {
        // 计算运行时长
        val elapsedTime = System.currentTimeMillis() - runningTime
        val hours = (elapsedTime / (1000 * 60 * 60)).toInt()
        val minutes = (elapsedTime % (1000 * 60 * 60) / (1000 * 60)).toInt()

        notificationBuilder?.setContentTitle("已运行${hours}小时${minutes}分钟")
        val notification = notificationBuilder?.build()
        notificationManager?.notify(1, notification)
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
        if (isMqttConnected()){
            LogUtils.log(Log.DEBUG,kTag, "MQTT已经连接，取消连接请求")
            return
        }
        if (isConnecting) {
            LogUtils.log(Log.DEBUG,kTag, "MQTT正在连接中，取消连接请求")
            return
        }

        isConnecting = true

        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            LogUtils.log(Log.ERROR,kTag, "网络不可用，无法连接到 MQTT 代理")
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
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,kTag, "MQTT 连接成功 代理: $mqttServerUrl")

                    val topicsToSubscribe = arrayOf(mqttTopicTest, mqttTopicCheckAppAlive,mqttTopicDark)
                    val qosLevels = intArrayOf(1,1,1) // QoS 级别
                    subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题
                    isConnecting = false
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.log(Log.ERROR,kTag, "MQTT 通信失败: ${exception?.message}")
                    isConnecting = false
                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "MQTT 连接异常: ${e.message}")
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msg = String(it.payload) // 将消息体转换为字符串
                    LogUtils.log(Log.DEBUG,kTag, "msg: $msg")

                    when (topic) {
                        mqttTopicDark -> {
                            LogUtils.log(Log.DEBUG,kTag, "处理主题 $mqttTopicDark 的消息，打开相关应用")
                            openApplication(Constant.DING_DING)
                        }
                        mqttTopicTest -> {
                            LogUtils.log(Log.DEBUG,kTag, "处理主题 $mqttTopicTest 的消息，发布测试消息")
                            publishMessage(mqttTopicTestResult, "darkPhone_testCheck", 1)
                        }
                        mqttTopicCheckAppAlive -> {
                            LogUtils.log(Log.DEBUG,kTag, "处理主题 $mqttTopicCheckAppAlive 的消息，设备是否都正常连接")
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
        if (!::mqttClient.isInitialized) {
            return false
        }

        isConnected = mqttClient.isConnected
        return isConnected
    }

    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        //mqtt订阅
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,kTag, "成功订阅主题: ${topics.joinToString(", ")}")
                    callback?.onMqttStatusChanged("成功订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.log(Log.ERROR,kTag, "订阅失败: ${exception?.message}")
                    callback?.onMqttStatusChanged("订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "订阅异常: ${e.message}")
            e.printStackTrace()
            callback?.onMqttStatusChanged("订阅异常: ${e.message}")
        }
    }

    private fun unsubscribeFromTopics(topics: Array<String>) {
        //mqtt解除订阅
        LogUtils.log(Log.DEBUG,kTag, "尝试解除订阅主题: ${topics.joinToString(", ")}")

        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,kTag, "成功解除订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.log(Log.ERROR,kTag, "解除订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "解除订阅异常: ${e.message}")
            e.printStackTrace()
        }
    }

    fun publishMessage(topic: String, message: String, qos: Int = 1) {
        //mqtt 发布
        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(topic, mqttMessage, null, null)
            LogUtils.log(Log.DEBUG,kTag, "消息发布成功: $topic:$message")
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "消息发布失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        LogUtils.log(Log.DEBUG,kTag, "MQTT服务销毁")
        weakReferenceHandler.removeCallbacks(updateRunnable)
        weakReferenceHandler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
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
        isConnected = false
        super.onDestroy()
    }

    override fun handleMessage(msg: Message): Boolean {
        return true
    }
}
package com.autodark.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.*
import com.autodark.extensions.openApplication
import com.autodark.utils.Constant
import com.autodark.utils.NetworkUtils
import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import com.autodark.utils.LogUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autodark.R
import com.pengxh.kt.lite.extensions.timestampToCompleteDate
import com.pengxh.kt.lite.utils.WeakReferenceHandler
import java.io.PrintWriter
import java.io.StringWriter
import android.content.Context
import android.provider.Settings


/**
 * mqtt前台服务
 * */
class MqttService : Service(), Handler.Callback {

    private val kTag = "MqttService"
    private val notificationId = 1
    private val weakReferenceHandler by lazy { WeakReferenceHandler(this) }
    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var runningTime = 0L
    private lateinit var updateRunnable: Runnable

    override fun handleMessage(msg: Message): Boolean {
        return true
    }

    //mqtt设置
    private lateinit var mqttServerUrl: String
    private lateinit var mqttClientId: String
    private lateinit var user: String
    private lateinit var pwd: String
    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var mqttTopicCheckAppAlive: String
    private lateinit var mqttTopicCheckAppAliveResult: String
    private lateinit var mqttTopicDark: String
    private lateinit var mqttTopicDarkResult: String
    private lateinit var mqttTopicLastWill: String

    //网络相关
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    var isConnecting = false

    //广播器设置
    private lateinit var receiver: BroadcastReceiver
    private val mqttTopicAction = "com.example.MQTT_PUBLISH_DARK_TOPIC"
    val mqttPushAction = "com.example.MQTT_PUBLISH_DARK_RESULT"


    override fun onCreate() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Android 8.0（API 级别 26）及以上版本需要创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "${resources.getString(R.string.app_name)}前台服务"
            val channel = NotificationChannel(
                "foreground_running_service_channel", name, NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Channel for Foreground Running Service"
            notificationManager?.createNotificationChannel(channel)
        }
        notificationBuilder = NotificationCompat.Builder(this, "foreground_running_service_channel")
            .setSmallIcon(R.mipmap.logo)
            .setContentTitle("已运行0小时0分钟")
            .setContentText(Constant.FOREGROUND_RUNNING_SERVICE_TITLE)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 设置通知优先级setContentText
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notification = notificationBuilder?.build()
        startForeground(notificationId, notification)

        // MQTT 配置文件导入
        loadProperties()
        LogUtils.log(Log.DEBUG,kTag, "加载 MQTT 配置文件")

        // 创建并注册本地广播接收器
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理接收到的消息
                val message = intent?.getStringExtra("message")
                if (intent?.action == mqttPushAction) {
                    LogUtils.log(Log.DEBUG,kTag, "收到Main activity的发送打卡结果通知：$message")
                    if (message != null) {
                        publishMqttDarkResult(message,1)
                    }
                }
            }
        }
        val mqttFilter = IntentFilter(mqttPushAction)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, mqttFilter)

        // 初始化网络相关配置
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                LogUtils.log(Log.DEBUG,kTag, "网络连接可用")

                // 检查 MQTT 客户端是否已连接
                if (!isMqttConnected() && !isConnecting){
                    LogUtils.log(Log.DEBUG,kTag, "MQTT连接中")
                    connectToMqtt()
                }
            }

            override fun onLost(network: Network) {
                // 网络丢失时可以选择执行其他操作
            }
        }
        // 注册网络回调
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        //发送需要订阅的主题

        val message = "测试请求主题：$mqttTopicCheckAppAlive\n" +
                "测试回复主题:$mqttTopicCheckAppAliveResult\n" +
                "打卡请求主题:$mqttTopicDark\n" +
                "打卡回复主题:$mqttTopicDarkResult\n" +
                "遗嘱主题:$mqttTopicLastWill\n"
        sendBroadcast(message)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //记录通知被创建的时间
        runningTime = System.currentTimeMillis()
        updateRunnable = object : Runnable {
            override fun run() {
                updateNotification()
                weakReferenceHandler.postDelayed(this, 1000L * 60)
            }
        }
        weakReferenceHandler.post(updateRunnable)
        return START_STICKY
    }

    private fun updateNotification() {
        // 计算运行时长
        val elapsedTime = System.currentTimeMillis() - runningTime
        val hours = (elapsedTime / (1000 * 60 * 60)).toInt()
        val minutes = (elapsedTime % (1000 * 60 * 60) / (1000 * 60)).toInt()

        notificationBuilder?.setContentTitle("已运行${hours}小时${minutes}分钟")
        val notification = notificationBuilder?.build()
        notificationManager?.notify(notificationId, notification)
    }

    private fun loadProperties() {
        mqttServerUrl = "tcp://39.106.230.248:1883"
        mqttClientId = getUUID()
        LogUtils.log(Log.DEBUG,kTag, "设备唯一ID：$mqttClientId")
        mqttTopicCheckAppAlive = "/topic/$mqttClientId/checkAppAlive"
        mqttTopicCheckAppAliveResult = "/topic/$mqttClientId/checkAppAliveResult"
        mqttTopicDark = "/topic/$mqttClientId/dark"
        mqttTopicDarkResult = "/topic/$mqttClientId/darkResult"
        mqttTopicLastWill = "/topic/$mqttClientId/LastWill"
        user = mqttClientId
        pwd = mqttClientId
    }

    fun connectToMqtt() {
        LogUtils.log(Log.DEBUG,kTag, "尝试连接到 MQTT 代理")

        if (isMqttConnected() || isConnecting) {
            LogUtils.log(Log.WARN,kTag, "已经连接或正在连接中，取消连接请求")
            return
        }

        isConnecting = true

        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            LogUtils.log(Log.WARN,kTag, "网络不可用，无法连接到 MQTT 代理")
            isConnecting = false
            return
        }


        mqttClient = MqttAndroidClient(applicationContext, mqttServerUrl, mqttClientId, Ack.AUTO_ACK)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            userName = user
            password = pwd.toCharArray()

            // 设置遗嘱消息
            val willQoS = 2

            // 获取当前时间戳
            val willMessage = "darkPhone_offline_at_" + System.currentTimeMillis().timestampToCompleteDate()

            setWill(mqttTopicLastWill, willMessage.toByteArray(), willQoS, true)
        }
        options.isAutomaticReconnect = true

        try {
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,kTag, "$mqttServerUrl 连接成功")
                    isConnecting = false

                    val topicsToSubscribe = arrayOf(mqttTopicCheckAppAlive,mqttTopicDark)
                    val qosLevels = intArrayOf(1,1) // QoS 级别
                    subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.log(Log.ERROR,kTag, "MQTT 通信失败: ${exception?.message}")
                    isConnecting = false
                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "MQTT 连接异常: ${e.message}")
            isConnecting = false
        }

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectionLost(cause: Throwable?) {
                if (cause != null) {
                    LogUtils.log(Log.ERROR, kTag, "MQTT 连接断开：${cause.message}")
                    val stackTrace = StringWriter().also { writer ->
                        cause.printStackTrace(PrintWriter(writer))
                    }.toString()
                    LogUtils.log(Log.ERROR, kTag, "堆栈信息：\n$stackTrace")
                } else {
                    LogUtils.log(Log.ERROR, kTag, "MQTT 连接断开，原因未知")
                }
                isConnecting = true
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) {
                    LogUtils.log(Log.INFO, kTag, "重连成功")
                } else {
                    LogUtils.log(Log.INFO, kTag, "初次连接成功")
                }
                isConnecting = false

                val topicsToSubscribe = arrayOf(mqttTopicCheckAppAlive,mqttTopicDark)
                val qosLevels = intArrayOf(1,1) // QoS 级别
                subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msg = String(it.payload) // 将消息体转换为字符串
                    LogUtils.log(Log.DEBUG,kTag, "收到主题 $topic 的消息: $msg")

                    when (topic) {
                        mqttTopicDark -> {
                            LogUtils.log(Log.DEBUG,kTag, "处理主题 $mqttTopicDark 的消息，打开相关应用")
                            openApplication(Constant.DING_DING)
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
                LogUtils.log(Log.DEBUG,kTag, "消息发送成功：${token?.message?.toString()}")
            }
        })
    }

    fun isMqttConnected(): Boolean {
        return if (::mqttClient.isInitialized) {
            val isConnected = mqttClient.isConnected
            LogUtils.log(Log.DEBUG, kTag, "MQTT状态已连接")
            isConnected
        } else {
            false
        }
    }


    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG, kTag,"成功订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.log(Log.ERROR, kTag,"订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR, kTag,"订阅异常: ${e.message}")
            val stackTrace = StringWriter().also { writer ->
                e.printStackTrace(PrintWriter(writer))
            }.toString()
            LogUtils.log(Log.ERROR, kTag, "堆栈信息：\n$stackTrace")
        }
    }

    //mqtt解除订阅
    private fun unsubscribeFromTopics(topics: Array<String>) {
        LogUtils.log(Log.DEBUG,kTag, "尝试解除订阅主题: ${topics.joinToString(", ")}")

        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,"AuToDark.connectToMqtt", "成功解除订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.log(Log.ERROR,kTag,  "解除订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag,  "解除订阅异常: ${e.message}")
            val stackTrace = StringWriter().also { writer ->
                e.printStackTrace(PrintWriter(writer))
            }.toString()
            LogUtils.log(Log.ERROR, kTag, "堆栈信息：\n$stackTrace")
        }
    }


    //mqtt 发布
    fun publishMessage(topic: String, message: String, qos: Int = 1) {
        LogUtils.log(Log.DEBUG,kTag, "尝试发布消息到主题 $topic: $message")

        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(topic, mqttMessage, null, null)
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "消息发布失败: ${e.message}")
        }
    }

    fun publishMqttDarkResult(message: String, qos: Int = 1) {
        publishMessage(mqttTopicDarkResult,message,qos)
    }

    //获取设备唯一ID
    private fun getUUID(): String {
        return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun sendBroadcast(message: String) {
        LogUtils.log(Log.DEBUG,kTag, "发送本机mqtt主题到Main activity:$message")
        val intent = Intent(mqttTopicAction)
        intent.putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent) // 发送本地广播
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // 取消订阅
            unsubscribeFromTopics(arrayOf(mqttTopicCheckAppAlive, mqttTopicDark))
            //断开连接
            mqttClient.disconnect()
            // 注销网络回调
            connectivityManager.unregisterNetworkCallback(networkCallback)
            weakReferenceHandler.removeCallbacksAndMessages(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
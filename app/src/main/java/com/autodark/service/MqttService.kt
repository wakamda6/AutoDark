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
import com.autodark.utils.MqttAuthConfig
import com.autodark.utils.NetworkUtils
import com.autodark.utils.TlsConfig
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
import android.content.Context
import com.autodark.BaseApplication
import com.autodark.model.MqttConnectionState
import com.autodark.model.MqttStateHolder
import com.autodark.ui.MqttConfigHolder
import java.io.*

/**
 * mqtt前台服务
 * */
class MqttService : Service(), Handler.Callback {

    var id:String = ""
    private var mqttClient: MqttAndroidClient? = null

    private lateinit var mqttServerUrl: String
    private lateinit var mqttClientId: String
    private lateinit var user: String
    private lateinit var pwd: String

    private lateinit var mqttTopicCheckAppAlive: String
    private lateinit var mqttTopicCheckAppAliveResult: String
    private lateinit var mqttTopicDark: String
    private lateinit var mqttTopicDarkResult: String
    private lateinit var mqttTopicLastWill: String

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

    //网络相关
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    //打卡结果广播器设置
    private lateinit var receiver: BroadcastReceiver
    val mqttPushAction = "com.example.MQTT_PUBLISH_DARK_RESULT"

    override fun onCreate() {
        id = (applicationContext as BaseApplication).androidId

        // MQTT 配置文件导入
        val currentDomain = (applicationContext as BaseApplication).domainAddress
        mqttServerUrl = "ssl://${currentDomain}:${TlsConfig.mqttPort}"
        mqttClientId = (applicationContext as BaseApplication).mqttClientId
        mqttTopicCheckAppAlive = (applicationContext as BaseApplication).mqttTopicCheckAppAlive
        mqttTopicCheckAppAliveResult = (applicationContext as BaseApplication).mqttTopicCheckAppAliveResult
        mqttTopicDark = (applicationContext as BaseApplication).mqttTopicDark
        mqttTopicDarkResult = (applicationContext as BaseApplication).mqttTopicDarkResult
        mqttTopicLastWill = (applicationContext as BaseApplication).mqttTopicLastWill
        // MQTT 账号密码：留空则回退使用设备 ID
        user = MqttAuthConfig.username.ifBlank { id }
        pwd = MqttAuthConfig.password.ifBlank { id }
        LogUtils.log(Log.DEBUG,kTag, "设备唯一ID：$id")
        LogUtils.log(Log.DEBUG,kTag, "加载 MQTT 配置文件")

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

        // 创建并注册本地广播接收器
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理接收到的消息
                val message = intent?.getStringExtra("message")
                if (intent?.action == mqttPushAction) {
                    LogUtils.log(Log.DEBUG,kTag, "收到 打卡结果 广播：$message")
                    if (message != null) {
                        publishMqttDarkResult(message,2)
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
            }

            override fun onLost(network: Network) {
                // 网络丢失时可以选择执行其他操作
                MqttStateHolder.mqttState.postValue(MqttConnectionState.ERROR("无网络"))
            }
        }
        // 注册网络回调
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMqttConnected()) {
            try {
                initMqttClient()
                connectMqtt()

                //记录通知被创建的时间
                runningTime = System.currentTimeMillis()
                updateRunnable = object : Runnable {
                    override fun run() {
                        updateNotification()
                        weakReferenceHandler.postDelayed(this, 1000L * 60)
                    }
                }
                weakReferenceHandler.post(updateRunnable)
            } catch (e: Exception) {
                LogUtils.log(Log.ERROR, kTag, "MQTT 启动连接失败: ${e.message}")
            }
        } else {
            LogUtils.log(Log.INFO, kTag, "MQTT 已连接，无需重复连接")
            MqttStateHolder.mqttState.postValue(MqttConnectionState.CONNECTED)
        }

        return START_STICKY
    }

    private fun isMqttConnected(): Boolean {
        return mqttClient != null && mqttClient!!.isConnected
    }

    private fun initMqttClient() {
        mqttClient = MqttAndroidClient(applicationContext, mqttServerUrl, mqttClientId, Ack.AUTO_ACK)
        mqttClient?.setCallback(mqttCallback)
    }

    //mqtt连接配置项
    private fun getMqttConnectOptions(): MqttConnectOptions {
        return MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 20
            keepAliveInterval = 60
            userName = user
            password = pwd.toCharArray()
            isAutomaticReconnect = true

            val willQoS = 2
            // 获取当前时间戳
            val willMessage = "darkPhone_offline_at_" + System.currentTimeMillis().timestampToCompleteDate()
            setWill(mqttTopicLastWill, willMessage.toByteArray(), willQoS, true)

            MqttConfigHolder.mqttSslContext?.let {
                socketFactory = it.socketFactory
            }
        }
    }

    private fun connectMqtt() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            LogUtils.log(Log.WARN, kTag, "网络不可用，无法连接到 MQTT 代理")
            return
        }
        MqttStateHolder.mqttState.postValue(MqttConnectionState.CONNECTING)

        try {
            val options = getMqttConnectOptions()

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    MqttStateHolder.mqttState.postValue(MqttConnectionState.CONNECTED)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    if (exception is MqttException) {
                        LogUtils.log(Log.ERROR, kTag, "MQTT 连接失败: ${exception.message}")
                        LogUtils.log(Log.ERROR, kTag, "错误码: ${exception.reasonCode}")
                        exception.printStackTrace()
                        MqttStateHolder.mqttState.postValue(MqttConnectionState.ERROR("连接失败：${exception.message}\n错误码: ${exception.reasonCode}"))
                    } else {
                        LogUtils.log(Log.ERROR, kTag, "未知错误: ${exception?.message}")
                        MqttStateHolder.mqttState.postValue(MqttConnectionState.ERROR("连接失败：未知错误: ${exception?.message}"))
                    }

                }
            })
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR, kTag, "MQTT 连接异常: ${e.message}")
            MqttStateHolder.mqttState.postValue(MqttConnectionState.ERROR("连接失败：MQTT 连接异常: ${e.message}"))
        }
    }

    private val mqttCallback = object : MqttCallbackExtended {
        override fun connectionLost(cause: Throwable?) {
            if (cause == null) {
                LogUtils.log(Log.INFO, kTag, "MQTT 已正常断开")
            } else {
                LogUtils.log(Log.ERROR, kTag, "MQTT 异常断开：${cause.message}")
                MqttStateHolder.mqttState.postValue(MqttConnectionState.ERROR("连接断开：异常断开：${cause.message}"))
            }
        }

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            if (reconnect) {
                MqttStateHolder.mqttState.postValue(MqttConnectionState.RECONNECTED)
            }else{
                MqttStateHolder.mqttState.postValue(MqttConnectionState.CONNECTED)
            }
            subscribeToTopics(arrayOf(mqttTopicCheckAppAlive, mqttTopicDark), intArrayOf(2, 2))
            LogUtils.log(Log.INFO, kTag, if (reconnect) "重连成功" else "初次连接成功")
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val msg = message?.toString() ?: return
            LogUtils.log(Log.DEBUG, kTag, "收到主题 $topic 的消息: $msg")

            when (topic) {
                mqttTopicDark -> {
                    LogUtils.log(Log.DEBUG, kTag, "处理 $mqttTopicDark 消息，打开钉钉")
                    openApplication(Constant.DING_DING)
                }
                mqttTopicCheckAppAlive -> {
                    LogUtils.log(Log.DEBUG, kTag, "处理 $mqttTopicCheckAppAlive 消息，响应设备在线")
                    publishMessage(mqttTopicCheckAppAliveResult, "darkPhone_alive", 2)
                }
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            LogUtils.log(Log.DEBUG, kTag, "消息发送成功：${token?.message?.toString()}")
        }
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

    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient?.subscribe(topics, qos, null, object : IMqttActionListener {
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
            mqttClient?.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.log(Log.DEBUG,kTag, "成功解除订阅主题: ${topics.joinToString(", ")}")
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
    fun publishMessage(topic: String, message: String, qos: Int = 2) {
        LogUtils.log(Log.DEBUG,kTag, "尝试发布消息到主题 $topic: $message")

        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient?.publish(topic, mqttMessage, null, null)
        } catch (e: MqttException) {
            LogUtils.log(Log.ERROR,kTag, "消息发布失败: ${e.message}")
        }
    }

    fun publishMqttDarkResult(message: String, qos: Int = 2) {
        publishMessage(mqttTopicDarkResult,message,qos)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isMqttConnected()){
                // 取消订阅
                unsubscribeFromTopics(arrayOf(mqttTopicCheckAppAlive, mqttTopicDark))
                //断开连接
                mqttClient?.disconnect(null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        LogUtils.log(Log.DEBUG, kTag, "MQTT 断开成功")
                        mqttClient?.unregisterResources()
                        MqttStateHolder.mqttState.postValue(MqttConnectionState.DISCONNECTED)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        LogUtils.log(Log.ERROR, kTag, "断开连接失败: ${exception?.message}")
                    }
                })
            }
            //注销广播
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            // 注销网络回调
            connectivityManager.unregisterNetworkCallback(networkCallback)
            weakReferenceHandler.removeCallbacksAndMessages(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            mqttClient = null
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
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
import android.content.Context
import android.content.res.Resources
import com.autodark.BaseApplication
import com.autodark.utils.LogUtils.otherShow
import java.io.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.*


/**
 * mqtt前台服务
 * */
class MqttService : Service(), Handler.Callback {

    var id:String = ""
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
    var isConnecting = false

    //广播器设置
    private lateinit var receiver: BroadcastReceiver
    val mqttPushAction = "com.example.MQTT_PUBLISH_DARK_RESULT"

    override fun onCreate() {
        id = (applicationContext as BaseApplication).androidId

        // MQTT 配置文件导入
        mqttServerUrl = (applicationContext as BaseApplication).mqttServerUrl
        mqttClientId = (applicationContext as BaseApplication).mqttClientId
        mqttTopicCheckAppAlive = (applicationContext as BaseApplication).mqttTopicCheckAppAlive
        mqttTopicCheckAppAliveResult = (applicationContext as BaseApplication).mqttTopicCheckAppAliveResult
        mqttTopicDark = (applicationContext as BaseApplication).mqttTopicDark
        mqttTopicDarkResult = (applicationContext as BaseApplication).mqttTopicDarkResult
        mqttTopicLastWill = (applicationContext as BaseApplication).mqttTopicLastWill
        user = id
        pwd = id
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
                "网络异常".otherShow(this@MqttService)
            }
        }
        // 注册网络回调
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
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



    fun connectToMqtt() {
        LogUtils.log(Log.DEBUG,kTag, "尝试连接到 MQTT 代理")

        lateinit var encryptedP12File: File
        lateinit var encryptedCaFile: File

        if (isMqttConnected() || isConnecting) {
            LogUtils.log(Log.WARN,kTag, "已经连接或正在连接中，取消连接请求")
            return
        }

        isConnecting = true

        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            LogUtils.log(Log.WARN,kTag, "网络不可用，无法连接到 MQTT 代理")
            isConnecting = false
            "网络异常".otherShow(this@MqttService)
            return
        }

        try {
            // 尝试加载加密文件
            encryptedP12File = File(applicationContext.filesDir, "$id.en")

            encryptedCaFile = File(applicationContext.filesDir, "ca.en")
        } catch (e: Resources.NotFoundException) {
            // 如果文件不存在，打印异常信息
            LogUtils.log(Log.WARN, kTag, "加密文件不存在: ${e.message}")
            return // 直接返回
        }

        val key = generateKeyFromString(id)
        if (key.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "密钥生成失败")
            // 处理解密失败的情况，比如返回或终止操作
            return
        }

        val p12Bytes = FileInputStream(encryptedP12File).use { inputStream ->
            aesDecryptInMemory(inputStream, key)
        }
        if (p12Bytes.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "解密证书文件失败")
            // 处理解密失败的情况，比如返回或终止操作
            "解密证书文件失败".otherShow(this@MqttService)
            return
        }
        val caBytes = FileInputStream(encryptedCaFile).use { inputStream ->
            aesDecryptInMemory(inputStream, key)
        }
        if (caBytes.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "解密 CA 文件失败")
            // 处理解密失败的情况，比如返回或终止操作
            "解密 CA 文件失败".otherShow(this@MqttService)
            return
        }

        // 加载 .p12 文件
        val p12P = id.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        val p12InputStream = p12Bytes.inputStream()
        try {
            keyStore.load(p12InputStream, p12P)
            LogUtils.log(Log.INFO,kTag, "P12 证书加载成功")
        } catch (e: Exception) {
            LogUtils.log(Log.WARN,kTag, "P12 证书加载失败: ${e.message}")
        }

        // 创建 KeyManagerFactory 来管理客户端证书和私钥
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, p12P)

        // 加载 CA 根证书
        val caInputStream = caBytes.inputStream()
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val caCertificate = certificateFactory.generateCertificate(caInputStream)

        // 创建一个包含 CA 证书的 KeyStore
        val caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        caKeyStore.load(null, null)
        caKeyStore.setCertificateEntry("ca", caCertificate)

        // 初始化 TrustManagerFactory
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(caKeyStore)
        //trustManagerFactory.init(null as KeyStore?)  // 默认使用系统信任的证书.不使用系统默认证书，保证内网通信

        // 使用证书和密钥进行进一步的 SSLContext 设置，确保连接安全
        val sslContext = SSLContext.getInstance("TLSv1.3")
        try {
            sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
            LogUtils.log(Log.DEBUG,kTag, "SSLContext 初始化成功")
        } catch (e: Exception) {
            LogUtils.log(Log.WARN,kTag, "SSLContext 初始化失败: ${e.message}")
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

            // 使用自定义的 SSLContext
            socketFactory = sslContext.socketFactory
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

                    "Mqtt主题订阅成功".otherShow(this@MqttService)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    if (exception is MqttException) {
                        // 打印 MqttException 的详细信息
                        LogUtils.log(Log.ERROR, kTag, "MQTT 通信失败: ${exception.message}")
                        LogUtils.log(Log.ERROR, kTag, "MqttException 错误码: ${exception.reasonCode}")
                        LogUtils.log(Log.ERROR, kTag, "MqttException 错误详细信息: ${exception.localizedMessage}")

                        // 打印堆栈跟踪，帮助进一步排查
                        exception.printStackTrace()
                    } else {
                        // 其他异常类型
                        LogUtils.log(Log.ERROR, kTag, "未知错误: ${exception?.message}")
                    }
                    "mqtt通信失败".otherShow(this@MqttService)
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
                "mqtt连接丢失".otherShow(this@MqttService)
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
        "mqtt发布消息成功".otherShow(this@MqttService)
    }

    fun publishMqttDarkResult(message: String, qos: Int = 1) {
        publishMessage(mqttTopicDarkResult,message,qos)
    }

    // 计算字符串的SHA-256哈希
    private fun hashString(input: String): ByteArray {
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        return sha256.digest(input.toByteArray(Charsets.UTF_8))
    }

    private fun generateKeyFromString(inputString: String): ByteArray {
        return try {
            // 计算字符串的哈希值
            val stringHash = hashString(inputString)

            // 将哈希值每个字节加1，并将结果转换为 ByteArray
            val transformedHash = stringHash.map {
                ((it.toInt() + 1) % 256).toByte()
            }.toByteArray()

            // 使用前16字节
            transformedHash.take(16).toByteArray()

        } catch (e: Exception) {
            // 捕获任何异常并记录日志
            LogUtils.log(Log.ERROR, kTag, "生成密钥时发生异常: ${e.message}")
            ByteArray(0)  // 返回空字节数组表示生成密钥失败
        }
    }

    // 解密文件并在内存中处理（不保存到文件）
    private fun aesDecryptInMemory(inputStream: InputStream, key: ByteArray): ByteArray {
        try {
            // 读取加密文件，获取IV（前16字节）
            val iv = ByteArray(16) // AES的IV长度是16字节
            val bytesRead = inputStream.read(iv) // 读取IV
            if (bytesRead != 16) {
                LogUtils.log(Log.ERROR, kTag, "IV长度不正确，解密失败")
                return ByteArray(0)  // 返回空字节数组
            }

            // 使用AES解密
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            CipherInputStream(inputStream, cipher).use { cipherInputStream ->
                ByteArrayOutputStream().use { outputStream ->
                    val buffer = ByteArray(4096)
                    var bytesReadInLoop: Int
                    while (cipherInputStream.read(buffer).also { bytesReadInLoop = it } != -1) {
                        outputStream.write(buffer, 0, bytesReadInLoop)
                    }
                    return outputStream.toByteArray()
                }
            }

        } catch (e: Exception) {
            LogUtils.log(Log.ERROR, kTag, "解密失败: ${e.message}")
        }

        // 出现任何错误时返回空字节数组
        return ByteArray(0)
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
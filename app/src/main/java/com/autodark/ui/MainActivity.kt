package com.autodark.ui

import android.content.*
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import com.autodark.R
import com.autodark.databinding.ActivityMainBinding
import com.autodark.fragment.SettingsFragment
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.autodark.BaseApplication
import com.autodark.MqttConfigHolder
import com.autodark.MqttConfigHolder.isMqttFirstRun
import com.autodark.adapter.BaseFragmentAdapter
import com.autodark.extensions.initImmersionBar
import com.autodark.service.MqttService
import com.autodark.utils.LogUtils
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.collections.ArrayList

class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {

    private val kTag = "MainActivity"

    private var darkID:String = ""
    private var CATimes:String = ""

    //剩余天数
    private var days= 0L
    private var hours= 0L

    //页面设置
    private val fragmentPages = ArrayList<Fragment>()
    private val settingsFragment = SettingsFragment()
    private lateinit var insetsController: WindowInsetsControllerCompat
    private var clickTime: Long = 0

    init {
        fragmentPages.add(settingsFragment)
    }


    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        insetsController = WindowCompat.getInsetsController(window, binding.rootView)
        binding.rootView.initImmersionBar(this, true, R.color.mainBackground)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun initEvent() {

    }


    override fun initOnCreate(savedInstanceState: Bundle?) {

        // 初始化 LogUtils
        LogUtils.initialize(this)

        // 测试日志输出
        LogUtils.log(Log.INFO, kTag, "应用启动成功")

        //id获取
        darkID = (applicationContext as BaseApplication).androidId
        CATimes = (applicationContext as BaseApplication).CATimes

        val fragmentAdapter = BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size  // 强制加载所有 Fragment

        //判断证书是否存在，因为涉及文件下载，安卓强制非阻塞
        lifecycleScope.launch(Dispatchers.IO) {
            val result = getAndCheckCA(this@MainActivity, darkID)
            if (result != "CASuccess") {
                // 回到主线程再弹窗
                launch(Dispatchers.Main) {
                    deleteCA(this@MainActivity, darkID)
                    showRetryDialog(result,this@MainActivity, darkID)
                }
            }else {
                if(CATimes.isNotEmpty()){
                    settingsFragment.setIdText(CATimes)
                }
                if(isMqttFirstRun){
                    LogUtils.log(Log.DEBUG, kTag, "第一次启动mqtt")
                    startService(Intent(this@MainActivity, MqttService::class.java))
                }else{
                    if(!MqttConfigHolder.isconnected) {
                        LogUtils.log(Log.DEBUG, kTag, "mqtt重连")
                        val intent = Intent("RECONNECT_MQTT")
                        sendBroadcast(intent)
                    }
                }
            }
        }
    }

    private fun showRetryDialog(result:String,context: Context, ID: String) {
        var title = ""
        var message = ""
        when (result) {
            "CAisRevoked" -> {
                title = "证书已被吊销"
                message = "验证失败，请将页面截图发送给开发者后重试\n"
            }
            "CAGetFailed" -> {
                title = "证书文件下载失败"
                message = "请将页面截图发送给开发者后重试\n"
            }
            "CAisTimeout" -> {
                title = "证书已过期"
                message = "请联系开发者购买时长\n"
            }
        }
        AlertMessageDialog.Builder()
            .setContext(this)
            .setTitle(title)
            .setMessage(message + "ID：$ID")
            .setPositiveButton("重试")
            .setOnDialogButtonClickListener(object :
                AlertMessageDialog.OnDialogButtonClickListener {
                override fun onConfirmClick() {
                    lifecycleScope.launch(Dispatchers.IO) {
                        //点击后重新下载并检查
                        val result2 = getAndCheckCA(context, ID)
                        if (result2 != "CASuccess") {
                            // 回到主线程再弹窗
                            launch(Dispatchers.Main) {
                                deleteCA(context, ID)
                                showRetryDialog(result2,context, ID)
                            }
                        }else {
                            if(CATimes.isNotEmpty()){
                                settingsFragment.setIdText(CATimes)
                            }
                            if(isMqttFirstRun){
                                LogUtils.log(Log.DEBUG, kTag, "第一次启动mqtt")
                                startService(Intent(this@MainActivity, MqttService::class.java))
                            }else{
                                if(!MqttConfigHolder.isconnected) {
                                    LogUtils.log(Log.DEBUG, kTag, "mqtt重连")
                                    val intent = Intent("RECONNECT_MQTT")
                                    sendBroadcast(intent)
                                }
                            }
                        }
                    }
                }
            }).build().show()
    }

    //删除ca文件
    private fun deleteCA(context: Context, ID: String) {
        val clientEnPath = File(context.filesDir, "$ID.en")
        val caEnPath = File(context.filesDir, "ca.en")

        if (clientEnPath.exists()) {
            val deleted = clientEnPath.delete()
            if (deleted) {
                LogUtils.log(Log.DEBUG, kTag, "客户端证书删除成功")
            } else {
                LogUtils.log(Log.WARN, kTag, "客户端证书删除失败")
            }
        }

        if (caEnPath.exists()) {
            val deleted = caEnPath.delete()
            if (deleted) {
                LogUtils.log(Log.DEBUG, kTag, "CA证书删除成功")
            } else {
                LogUtils.log(Log.WARN, kTag, "CA证书删除失败")
            }
        }
    }

    //证书初始化应该包括下载，监测吊销和解密，所有完成后将ssl句柄传递给mqtt service
    private fun getAndCheckCA(context: Context, ID: String,retryCount: Int = 0): String {
        val clientEnPath = File(context.filesDir, "$ID.en")
        val caEnPath = File(context.filesDir, "ca.en")

        val baseUrl = "https://***REMOVED***/certs/${ID}/en_${ID}"
        val clientEnUrl = "$baseUrl/${ID}.en"
        val caEnUrl = "$baseUrl/ca.en"

        if (retryCount > 0){
            downloadFileSuspend(clientEnUrl, clientEnPath)
            downloadFileSuspend(caEnUrl, caEnPath)
        }else if (retryCount == 0){
            if (!clientEnPath.exists()) {
                downloadFileSuspend(clientEnUrl, clientEnPath)
            } else {
                LogUtils.log(Log.DEBUG, kTag, "客户端证书已存在")
            }

            if (!caEnPath.exists()) {
                downloadFileSuspend(caEnUrl, caEnPath)
            }else {
                LogUtils.log(Log.DEBUG, kTag, "CA证书已存在")
            }
        }

        if (!clientEnPath.exists() || !caEnPath.exists()) {
            LogUtils.log(Log.ERROR, kTag, "证书文件下载失败")
            return "CAGetFailed"
        }

        val key = generateKeyFromString(ID)
        if (key.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "密钥生成失败")
            // 处理解密失败的情况，比如返回或终止操作
            return "CAGetFailed"
        }

        val p12Bytes = FileInputStream(clientEnPath).use { inputStream ->
            aesDecryptInMemory(inputStream, key)
        }
        if (p12Bytes.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "解密证书文件失败")
            return "CAGetFailed"
        }
        val caBytes = FileInputStream(caEnPath).use { inputStream ->
            aesDecryptInMemory(inputStream, key)
        }
        if (caBytes.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "解密 CA 文件失败")
            return "CAGetFailed"
        }

        // 加载 .p12 文件
        val p12P = ID.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        val p12InputStream = p12Bytes.inputStream()
        try {
            keyStore.load(p12InputStream, p12P)
            LogUtils.log(Log.INFO,kTag, "P12 证书加载成功")

            // 吊销验证
            val alias = keyStore.aliases().nextElement() // 获取 p12 中的第一个别名
            val clientCert = keyStore.getCertificate(alias) as X509Certificate

            val crlUrl = URL("https://***REMOVED***/crl/crl.pem")
            val crlStream = crlUrl.openStream()
            val cf = CertificateFactory.getInstance("X.509")
            val crl = cf.generateCRL(crlStream) as X509CRL

            if (crl.isRevoked(clientCert)) {
                LogUtils.log(Log.WARN, kTag, "客户端证书已被吊销,重新下载证书验证")
//                if (retried) {
//                    LogUtils.log(Log.ERROR, kTag, "证书吊销验证失败，已重试过一次")
//                    return "CAisRevoked"
//                }
//                downloadFileSuspend(clientEnUrl, clientEnPath)
//                downloadFileSuspend(caEnUrl, caEnPath)
//
//                val result = getAndCheckCA(context, ID,retried = true)
//                return if (result != "CASuccess") {
//                    LogUtils.log(Log.WARN, kTag, "验证失败")
//                    "CAisRevoked"
//                }else{
//                    LogUtils.log(Log.INFO, kTag, "验证成功")
//                    "CASuccess"
//                }
                return if (retryCount >= 3) {
                    LogUtils.log(Log.ERROR, kTag, "证书吊销验证失败，已达最大重试次数")
                    "CAisRevoked"
                } else {
                    getAndCheckCA(context, ID, retryCount + 1)
                }

            }

            LogUtils.log(Log.INFO, kTag, "客户端证书有效，未被吊销")
            // 计算剩余天数
            val now = Date()
            val notAfter = clientCert.notAfter
            val diffInMillies = notAfter.time - now.time
            if (diffInMillies <= 0) {
                // 证书已过期
                days = 0
                hours = 0
                LogUtils.log(Log.WARN,kTag, "证书已过期")
                return "CAisTimeout"
            } else {
                days = TimeUnit.MILLISECONDS.toDays(diffInMillies)
                hours = TimeUnit.MILLISECONDS.toHours(diffInMillies) % 24

                CATimes = "剩余时长:"+days+"天"+hours+"小时"
            }
            LogUtils.log(Log.DEBUG,kTag, "证书剩余时间：$days 天 $hours 小时")

        } catch (e: Exception) {
            LogUtils.log(Log.WARN,kTag, "P12 证书加载失败: ${e.message}")
            return "CAisRevoked"
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

        // SSLContext 设置
        try {
            MqttConfigHolder.mqttSslContext = SSLContext.getInstance("TLSv1.3").apply {
                init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
            }
            LogUtils.log(Log.DEBUG,kTag, "mqttSslContext 初始化成功")
        } catch (e: Exception) {
            LogUtils.log(Log.WARN,kTag, "mqttSslContext 初始化失败: ${e.message}")
            return "CAGetFailed"
        }

        return "CASuccess"
    }

    private fun downloadFileSuspend(urlStr: String, destFile: File){
        //先删除
        if (destFile.exists()) {
            val deleted = destFile.delete()
            if (deleted) {
                LogUtils.log(Log.DEBUG, kTag, "证书删除成功")
            } else {
                LogUtils.log(Log.WARN, kTag, "证书删除失败")
            }
        }

        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.doInput = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val input = connection.inputStream
                val output = FileOutputStream(destFile)
                input.copyTo(output)
                output.close()
                input.close()
                LogUtils.log(Log.DEBUG,kTag, "下载成功：${destFile.name}")
            } else {
                LogUtils.log(Log.DEBUG,kTag, "下载失败：$urlStr，code=${connection.responseCode}")
            }

            connection.disconnect()
        } catch (e: Exception) {
            LogUtils.log(Log.DEBUG,kTag, "异常下载 $urlStr: ${e.message}")
        }
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

    override fun observeRequestState() {

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - clickTime > 2000) {
                "再按一次退出应用".show(this)
                clickTime = System.currentTimeMillis()
                true
            } else {
                super.onKeyDown(keyCode, event)
            }
        } else super.onKeyDown(keyCode, event)
    }

    //正常返回桌面后再进入需要检测证书
    override fun onRestart() {
        super.onRestart()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = getAndCheckCA(this@MainActivity, darkID)
            if (result != "CASuccess") {
                // 回到主线程再弹窗
                launch(Dispatchers.Main) {
                    deleteCA(this@MainActivity, darkID)
                    showRetryDialog(result,this@MainActivity, darkID)
                }
            }else {
                if(CATimes.isNotEmpty()){
                    settingsFragment.setIdText(CATimes)
                    LogUtils.log(Log.DEBUG, kTag, "设置证书时间")
                }
                if(!MqttConfigHolder.isconnected) {
                    LogUtils.log(Log.WARN, kTag, "mqtt未连接，开始重连")
                    val intent = Intent("RECONNECT_MQTT")
                    sendBroadcast(intent)
                }
            }
        }
    }
}
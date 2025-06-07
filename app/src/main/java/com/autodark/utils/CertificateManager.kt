package com.autodark.utils

import android.content.Context
import android.util.Log
import com.autodark.MqttConfigHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

object CertificateManager  {
    private const val kTag = "CertificateManager"

//    private fun showRetryDialog(result:String,context: Context, ID: String) {
//        var title = ""
//        var message = ""
//        when (result) {
//            "CAisRevoked" -> {
//                title = "证书已被吊销"
//                message = "验证失败，请将页面截图发送给开发者后重试\n"
//            }
//            "CAGetFailed" -> {
//                title = "证书文件下载失败"
//                message = "请将页面截图发送给开发者后重试\n"
//            }
//            "CAisTimeout" -> {
//                title = "证书已过期"
//                message = "请联系开发者购买时长\n"
//            }
//            "SSLError" -> {
//                title = "连接失败"
//                message = "请联系开发者\n"
//            }
//            "PermissionFailed" -> {
//                title = "权限不满足"
//                message = "请给与权限\n"
//            }
//        }
//        AlertMessageDialog.Builder()
//            .setContext(this)
//            .setTitle(title)
//            .setMessage(message + "ID：$ID")
//            .setPositiveButton("重试")
//            .setOnDialogButtonClickListener(object :
//                AlertMessageDialog.OnDialogButtonClickListener {
//                override fun onConfirmClick() {
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        //点击后重新下载并检查
//                        val result2 = getAndCheckCA(context, ID)
//                        if (result2 != "CASuccess") {
//                            // 回到主线程再弹窗
//                            launch(Dispatchers.Main) {
//                                deleteCA(context, ID)
//                                showRetryDialog(result2,context, ID)
//                            }
//                        }else {
//                            if(caTimes.isNotEmpty()){
//                                settingsFragment.setIdText(caTimes)
//                            }
//                            startService(Intent(this@MainActivity, MqttService::class.java))
//                        }
//                    }
//                }
//            }).build().show()
//    }

    //证书验证的主函数：
    suspend fun getAndCheckCA(context: Context, ID: String): String {
        val clientEnPath = File(context.filesDir, "$ID.en")
        val caEnPath = File(context.filesDir, "ca.en")
        val baseUrl = "https://***REMOVED***/certs/${ID}/en_${ID}"
        val clientEnUrl = "$baseUrl/${ID}.en"
        val caEnUrl = "$baseUrl/ca.en"

        //1获取证书
        val result = checkAndDownloadCerts(clientEnPath,caEnPath,clientEnUrl,caEnUrl)
        if (result != "CASuccess") return result

        //2解密证书
        val p12Bytes = decryptCertFile(clientEnPath, ID) ?: return "CAGetFailed"
        val caBytes = decryptCertFile(caEnPath, ID) ?: return "CAGetFailed"

        //3加载并验证证书
        val checkCertResult = checkCertRevoked(p12Bytes, ID)
        if (!checkCertResult.contains("CASuccess")) return result

        // 初始化 SSL
        if(!MqttConfigHolder.initSslContextIfNeeded(p12Bytes, ID.toCharArray(), caBytes)){
            return "SSLError"
        }

        return "CASuccess"
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

    //1. 获取证书：如果存在则直接校验，如果不存在则需要下载，下载三次，如果还不成功则返回下载失败
    private suspend fun checkAndDownloadCerts(clientEnPath:File, caEnPath:File, clientEnUrl:String, caEnUrl:String):String {
        val clientDownloaded = if (!clientEnPath.exists()) {
            downloadFile(clientEnUrl, clientEnPath)
        } else {
            LogUtils.log(Log.DEBUG, kTag, "客户端证书已存在")
            true
        }

        val caDownloaded = if (!caEnPath.exists()) {
            downloadFile(caEnUrl, caEnPath)
        } else {
            LogUtils.log(Log.DEBUG, kTag, "CA证书已存在")
            true
        }

        if (!clientDownloaded || !caDownloaded) {
            LogUtils.log(Log.ERROR, kTag, "证书文件下载失败")
            return "CAGetFailed"
        }

        return "CASuccess"
    }

    //2. 解密证书，失败则返回空
    private fun decryptCertFile(EnPath:File, ID: String): ByteArray? {
        val key = generateKeyFromString(ID)
        if (key.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "密钥生成失败")
            return null
        }
        val decodeBytes = FileInputStream(EnPath).use { aesDecryptInMemory(it, key) }
        if (decodeBytes.isEmpty()) {
            LogUtils.log(Log.ERROR, kTag, "解密证书文件失败")
            return null
        }
        return decodeBytes
    }

    //3. 加载并验证证书：获取剩余时长并返回，失败则返回验证失败
    private fun checkCertRevoked(bytes:ByteArray, ID: String): String{
        //剩余天数
        val caTimes: String

        val p12P = ID.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        try {
            keyStore.load(bytes.inputStream(), p12P)
            val alias = keyStore.aliases().nextElement()
            val clientCert = keyStore.getCertificate(alias) as X509Certificate

            val crl = CertificateFactory.getInstance("X.509")
                .generateCRL(URL("https://***REMOVED***/crl/crl.pem").openStream()) as X509CRL

            if (crl.isRevoked(clientCert)) {
                LogUtils.log(Log.WARN, kTag, "客户端证书已被吊销,重新下载证书验证")
                return "CAisRevoked"
            }

            caTimes = formatRemainingTime(clientCert.notAfter)

            return caTimes

        } catch (e: Exception) {
            LogUtils.log(Log.WARN, kTag, "P12 证书加载失败: ${e.message}")
            return "checkCertError"
        }
    }

    //1. 获取下载证书
    private suspend fun downloadFile(urlStr: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        // 切换到 IO 线程执行下载操作
        try {
            if (destFile.exists()) {
                val deleted = destFile.delete()
                if (deleted) {
                    LogUtils.log(Log.DEBUG, kTag, "证书删除成功")
                } else {
                    LogUtils.log(Log.WARN, kTag, "证书删除失败")
                }
            }

            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.doInput = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                LogUtils.log(Log.DEBUG, kTag, "下载成功：${destFile.name}")
                connection.disconnect()
                return@withContext true
            } else {
                LogUtils.log(Log.DEBUG, kTag, "下载失败：$urlStr，code=${connection.responseCode}")
                connection.disconnect()
                return@withContext false
            }
        } catch (e: Exception) {
            LogUtils.log(Log.DEBUG, kTag, "异常下载 $urlStr: ${e.message}")
            return@withContext false
        }
    }

    //2.解密:计算字符串的SHA-256哈希
    private fun hashString(input: String): ByteArray {
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        return sha256.digest(input.toByteArray(Charsets.UTF_8))
    }

    //2.解密:
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

    // 2.解密文件并在内存中处理（不保存到文件）
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

    //3. 验证
    private fun formatRemainingTime(notAfter: Date): String {
        val now = Date()
        val diff = notAfter.time - now.time
        if (diff <= 0) return "CAisTimeout"

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
        return "剩余时长:${days}天${hours}小时"
    }

}
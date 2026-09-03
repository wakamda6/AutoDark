package com.autodark.ui

import android.util.Log
import com.autodark.utils.LogUtils
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object MqttConfigHolder {
    private const val kTag = "mqttSslContext"

    private var lastSslHash: String? = null
    var mqttSslContext: SSLContext? = null
        private set

    // 清空缓存的 SSLContext，切换 TLS 模式后必须调用
    fun reset() {
        mqttSslContext = null
        lastSslHash = null
    }

    // 双向 TLS：携带客户端证书 + 信任 CA
    fun initMutualSslContext(p12Bytes: ByteArray, p12Password: CharArray, caBytes: ByteArray): Boolean {
        val newHash = (p12Bytes.contentHashCode().toString() + caBytes.contentHashCode().toString())

        if (newHash == lastSslHash && mqttSslContext != null) {
            LogUtils.log(Log.DEBUG, kTag, "证书哈希值相同，SSLContext 无需重新初始化")
            return true
        } else {
            LogUtils.log(Log.DEBUG, kTag, "证书发生变化，SSLContext 重新初始化")
        }

        return try {
            // 客户端证书
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(p12Bytes.inputStream(), p12Password)
            }

            // ===== 打印客户端证书域名信息 =====
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = keyStore.getCertificate(alias)

                if (cert is java.security.cert.X509Certificate) {
                    // 1️⃣ Subject DN（含 CN）
                    LogUtils.log(
                        Log.DEBUG,
                        kTag,
                        "客户端证书 Subject: ${cert.subjectX500Principal.name}"
                    )

                    // 2️⃣ SAN（Subject Alternative Name）
                    val sanList = cert.subjectAlternativeNames
                    if (sanList != null) {
                        for (san in sanList) {
                            val type = san[0] as Int
                            val value = san[1]

                            val typeName = when (type) {
                                2 -> "DNS"
                                7 -> "IP"
                                else -> "OTHER($type)"
                            }

                            LogUtils.log(
                                Log.DEBUG,
                                kTag,
                                "客户端证书 SAN: $typeName = $value"
                            )
                        }
                    } else {
                        LogUtils.log(Log.DEBUG, kTag, "客户端证书无 SAN 扩展")
                    }
                }
            }

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, p12Password)
            }

            // CA证书
            val caCertificate = CertificateFactory.getInstance("X.509").generateCertificate(caBytes.inputStream())
            val caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("ca", caCertificate)
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(caKeyStore)
            }

            mqttSslContext = SSLContext.getInstance("TLS").apply {
                init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
            }
            lastSslHash = newHash
            LogUtils.log(Log.DEBUG, "SslInitializer", "SSLContext 初始化成功")
            true
        } catch (e: Exception) {
            LogUtils.log(Log.ERROR, "SslInitializer", "SSLContext 初始化失败: ${e.message}")
            false
        }
    }
}

package com.autodark.utils

import com.pengxh.kt.lite.utils.SaveKeyValues

/**
 * TLS 认证模式配置。
 * 默认单向 TLS（仅校验服务器证书），可选双向 TLS（mTLS，额外携带客户端证书）。
 * 双向与单向使用不同端口，由开关决定。
 */
object TlsConfig {

    private const val KEY_MUTUAL_TLS = "mutual_tls_enabled"

    // 双向 TLS（mTLS）端口
    const val PORT_MUTUAL = 8883

    // 单向 TLS 端口
    const val PORT_ONE_WAY = 8884

    // 是否启用双向 TLS，默认 false = 单向
    var mutualTlsEnabled: Boolean
        get() = SaveKeyValues.getValue(KEY_MUTUAL_TLS, false) as Boolean
        set(value) = SaveKeyValues.putValue(KEY_MUTUAL_TLS, value)

    // 当前模式对应的 MQTT 端口
    val mqttPort: Int
        get() = if (mutualTlsEnabled) PORT_MUTUAL else PORT_ONE_WAY
}

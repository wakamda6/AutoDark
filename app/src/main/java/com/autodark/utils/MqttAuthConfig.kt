package com.autodark.utils

import com.pengxh.kt.lite.utils.SaveKeyValues

/**
 * MQTT 账号密码配置。
 * 由用户在页面动态填写并持久化，运行时读取。
 * 留空时回退使用设备 ID（兼容原有按设备 ID 认证的部署）。
 */
object MqttAuthConfig {

    private const val KEY_USERNAME = "mqtt_username"
    private const val KEY_PASSWORD = "mqtt_password"

    var username: String
        get() = SaveKeyValues.getValue(KEY_USERNAME, "") as String
        set(value) = SaveKeyValues.putValue(KEY_USERNAME, value.trim())

    var password: String
        get() = SaveKeyValues.getValue(KEY_PASSWORD, "") as String
        set(value) = SaveKeyValues.putValue(KEY_PASSWORD, value.trim())
}

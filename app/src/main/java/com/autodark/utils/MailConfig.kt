package com.autodark.utils

import com.pengxh.kt.lite.utils.SaveKeyValues

/**
 * 发送邮箱配置。
 * 邮箱账号与 SMTP 授权码由用户在页面动态填写并持久化到本地，不再硬编码。
 */
object MailConfig {

    private const val KEY_SENDER_ACCOUNT = "sender_mail_account"
    private const val KEY_SENDER_AUTH_CODE = "sender_mail_auth_code"

    // 发送邮箱账号（同时作为发件地址与登录账号）
    var senderAccount: String
        get() = SaveKeyValues.getValue(KEY_SENDER_ACCOUNT, "") as String
        set(value) = SaveKeyValues.putValue(KEY_SENDER_ACCOUNT, value.trim())

    // SMTP 授权码（QQ 邮箱授权码，非登录密码）
    var senderAuthCode: String
        get() = SaveKeyValues.getValue(KEY_SENDER_AUTH_CODE, "") as String
        set(value) = SaveKeyValues.putValue(KEY_SENDER_AUTH_CODE, value.trim())

    // 是否已配置完成
    val isConfigured: Boolean
        get() = senderAccount.isNotBlank() && senderAuthCode.isNotBlank()
}

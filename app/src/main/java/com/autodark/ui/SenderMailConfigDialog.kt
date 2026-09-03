package com.autodark.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.autodark.utils.MailConfig

/**
 * 发送邮箱配置弹窗（邮箱账号 + SMTP 授权码）。
 *
 * @param isFirstTime 首次启动强制填写：不可取消，取消则退出应用
 * @param onSaved 保存成功后的回调
 */
fun showSenderMailConfigDialog(
    context: Context,
    isFirstTime: Boolean = false,
    onSaved: () -> Unit = {}
) {
    val density = context.resources.displayMetrics.density
    val padding = (16 * density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding / 2, padding, padding / 2)
    }

    val accountEditText = EditText(context).apply {
        hint = "发送邮箱（QQ邮箱）"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        maxLines = 1
        setText(MailConfig.senderAccount)
    }
    val authCodeEditText = EditText(context).apply {
        hint = "SMTP授权码"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        maxLines = 1
        setText(MailConfig.senderAuthCode)
    }

    container.addView(accountEditText)
    container.addView(authCodeEditText)

    val dialog = AlertDialog.Builder(context)
        .setTitle("设置发送邮箱（仅支持QQ邮箱）")
        .setMessage("仅支持QQ邮箱，请填写QQ邮箱账号及对应的 SMTP 授权码")
        .setView(container)
        .setCancelable(!isFirstTime)
        .setNegativeButton(if (isFirstTime) "退出应用" else "取消", null)
        .setPositiveButton("确定", null)
        .create()

    dialog.show()

    // 取消/退出
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
        dialog.dismiss()
        if (isFirstTime && context is Activity) {
            context.finish()
        }
    }

    // 动态拦截确定按钮，校验输入
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val account = accountEditText.text.toString().trim()
        val authCode = authCodeEditText.text.toString().trim()
        if (account.isEmpty()) {
            accountEditText.error = "邮箱不能为空"
            return@setOnClickListener
        }
        if (authCode.isEmpty()) {
            authCodeEditText.error = "授权码不能为空"
            return@setOnClickListener
        }
        MailConfig.senderAccount = account
        MailConfig.senderAuthCode = authCode
        dialog.dismiss()
        onSaved()
    }
}

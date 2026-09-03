package com.autodark.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.autodark.utils.MqttAuthConfig

/**
 * MQTT 账号密码配置弹窗。
 * 留空则回退使用设备 ID 作为账号密码。
 *
 * @param onSaved 保存成功后的回调
 */
fun showMqttAuthConfigDialog(
    context: Context,
    onSaved: () -> Unit = {}
) {
    val density = context.resources.displayMetrics.density
    val padding = (16 * density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding / 2, padding, padding / 2)
    }

    val usernameEditText = EditText(context).apply {
        hint = "MQTT用户名（留空=设备ID）"
        inputType = InputType.TYPE_CLASS_TEXT
        maxLines = 1
        setText(MqttAuthConfig.username)
    }
    val passwordEditText = EditText(context).apply {
        hint = "MQTT密码（留空=设备ID）"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        maxLines = 1
        setText(MqttAuthConfig.password)
    }

    container.addView(usernameEditText)
    container.addView(passwordEditText)

    val dialog = AlertDialog.Builder(context)
        .setTitle("设置MQTT账号密码")
        .setMessage("填写 broker 的用户名和密码，留空则使用设备ID")
        .setView(container)
        .setCancelable(true)
        .setNegativeButton("取消", null)
        .setPositiveButton("确定", null)
        .create()

    dialog.show()

    // 动态拦截确定按钮，保存配置
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        MqttAuthConfig.username = usernameEditText.text.toString().trim()
        MqttAuthConfig.password = passwordEditText.text.toString().trim()
        dialog.dismiss()
        onSaved()
    }
}

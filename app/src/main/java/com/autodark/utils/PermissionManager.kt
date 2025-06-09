package com.autodark.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object PermissionManager {
    private const val kTag = "PermissionManager"

    // 声明一个变量保存AlertDialog引用
    private var currentDialog: AlertDialog? = null

    //检查
    fun allPermissionsGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context) &&
                isNotificationListenerEnabled(context) &&
                isIgnoringBatteryOptimizations(context)
    }

    fun checkAllPermissions(activity: Activity, onGranted: (() -> Unit)? = null) {
        when {
            !Settings.canDrawOverlays(activity) -> {
                showDialog(
                    activity,
                    "悬浮窗权限",
                    "App 需要悬浮窗权限以展示打卡倒计时，请授权。"
                ) { requestOverlayPermission(activity) }
            }

            !isNotificationListenerEnabled(activity) -> {
                showDialog(
                    activity,
                    "通知监听权限",
                    "App 需要读取通知内容以获取打卡结果，如不授权则无法获取打卡结果。"
                ) { requestNotificationPermission(activity) }
            }

            !isIgnoringBatteryOptimizations(activity) -> {
                showDialog(
                    activity,
                    "电池优化白名单",
                    "为保证后台正常运行，请将应用加入电池优化白名单。"
                ) { requestBatteryOptimizationWhitelist(activity) }
            }

            else -> {
                onGranted?.invoke()
            }
        }
    }

    // 悬浮窗
    private fun requestOverlayPermission(activity: Activity) {
        LogUtils.log(Log.DEBUG, kTag, "请求悬浮窗权限")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivity(intent)
    }

    // 通知监听
    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    private fun requestNotificationPermission(activity: Activity) {
        LogUtils.log(Log.DEBUG, kTag, "请求通知监听权限")
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        activity.startActivity(intent)
    }

    // 电池优化白名单
    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationWhitelist(activity: Activity) {
        LogUtils.log(Log.DEBUG, kTag, "请求电池优化白名单权限")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    private fun showDialog(
        activity: Activity,
        title: String,
        message: String,
        onPositive: () -> Unit
    ) {
        // 先关闭之前的Dialog（防止多次弹出）
        currentDialog?.dismiss()

        currentDialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("去授权") { _, _ ->
                onPositive()
                currentDialog = null
            }
            .setNegativeButton("退出") { _, _ ->
                currentDialog?.dismiss()
                currentDialog = null
                activity.finish()
            }
            .create()

        currentDialog?.show()
    }

    fun dismissDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }
}
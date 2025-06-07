package com.autodark.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object PermissionManager {
    private const val kTag = "PermissionManager"

    private const val PREFS_NAME = "permission_prefs"
    private const val KEY_AUTO_START_REMINDER = "auto_start_reminder_shown"

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

//            else -> {
//                // 判断是否已提醒过自启动权限
//                val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//                val alreadyReminded = prefs.getBoolean(KEY_AUTO_START_REMINDER, false)
//                if (!alreadyReminded) {
//                    showDialog(activity, "自启动权限", "为确保功能正常，请设置应用允许自启动") {
//                        requestAutoStartPermission(activity)
//                        // 记录已提醒，避免重复弹窗
//                        prefs.edit().putBoolean(KEY_AUTO_START_REMINDER, true).apply()
//                    }
//                }
//                // 如果已提醒过，就不再弹窗，避免循环
//                Toast.makeText(activity, "所有权限已授予，请自行确认自启动权限是否打开", Toast.LENGTH_SHORT).show()
//            }
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

    // 自启动（厂商定制）
    private fun requestAutoStartPermission(activity: Activity) {
        try {
            val intent = when {
                Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }

                Build.MANUFACTURER.equals("oppo", ignoreCase = true) -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }

                Build.MANUFACTURER.equals("vivo", ignoreCase = true) -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }

                Build.MANUFACTURER.equals("huawei", ignoreCase = true) -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }

                else -> {
                    // fallback：打开应用详情
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                }
            }

            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "无法打开自启动设置，请手动配置", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }


    private fun showDialog(
        activity: Activity,
        title: String,
        message: String,
        onPositive: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("去授权") { _, _ -> onPositive() }
            .setNegativeButton("退出") { _, _ -> activity.finish() }
            .show()
    }
}
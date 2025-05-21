package com.autodark.service

import com.autodark.utils.LogUtils
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autodark.extensions.createTextMail
import com.autodark.extensions.openApplication
import com.autodark.extensions.sendTextMail
import com.autodark.fragment.SettingsFragment
import com.autodark.ui.MainActivity
import com.autodark.utils.Constant
import com.autodark.utils.Constant.FOREGROUND_RUNNING_SERVICE_TITLE
import com.autodark.utils.CountDownTimerManager
import com.pengxh.kt.lite.extensions.getSystemService
import com.pengxh.kt.lite.extensions.show
import com.pengxh.kt.lite.extensions.timestampToCompleteDate
import com.pengxh.kt.lite.utils.SaveKeyValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NotificationMonitorService : NotificationListenerService(), LifecycleOwner {

    private val kTag = "NotificationMonitorService"
    private val registry = LifecycleRegistry(this)

    private fun sendBroadcast(message: String) {
        LogUtils.log(Log.DEBUG,kTag, "发送打卡结果到mqtt:$message")
        val intent = Intent("com.example.MQTT_PUBLISH_DARK_RESULT")
        intent.putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent) // 发送广播
    }

    override fun getLifecycle(): Lifecycle {
        return registry
    }

    private val notificationBeanDao by lazy { com.autodark.BaseApplication.get().daoSession.notificationBeanDao }
    private val batteryManager by lazy { getSystemService<BatteryManager>() }

    /**
     * 有可用的并且和通知管理器连接成功时回调
     */
    private fun isServiceInitialized(): Boolean {
        return getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_initialized", false)
    }

    private fun markServiceInitialized() {
        getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_initialized", true).apply()
    }

    private fun clearServiceInitialized() {
        getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .edit().remove("is_initialized").apply()
    }

    override fun onListenerConnected() {
        try {
            if (!isServiceInitialized()) {
                LogUtils.log(Log.DEBUG, kTag, "通知监听服务初始化")
                SettingsFragment.weakReferenceHandler?.sendEmptyMessage(2024090801)
                markServiceInitialized()
            } else {
                LogUtils.log(Log.DEBUG, kTag, "通知监听服务已经初始化,不再初始化...")
            }
        } catch (e: Exception) {
            LogUtils.log(Log.ERROR, kTag, "发生异常: ${e.message}")
            // 可清除标记以允许下次初始化
            getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
                .edit().remove("is_initialized").apply()
        }
    }

    override fun onListenerDisconnected() {
        LogUtils.log(Log.DEBUG,kTag, "通知监听服务已关闭")
        SettingsFragment.weakReferenceHandler?.sendEmptyMessage(2024090802)
        clearServiceInitialized()
    }

    /**
     * 当有新通知到来时会回调
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        LogUtils.log(Log.DEBUG,kTag, "收到新通知")

        val extras = sbn.notification.extras
        // 获取接收消息APP的包名
        val packageName = sbn.packageName
        // 获取接收消息的标题
        val title = extras.getString(Notification.EXTRA_TITLE)
        // 获取接收消息的内容
        val notice = extras.getString(Notification.EXTRA_TEXT)

        if (notice.isNullOrBlank()) {
            LogUtils.log(Log.DEBUG, kTag, "通知发出者包名: $packageName")
            LogUtils.log(Log.DEBUG,kTag, "通知内容为空，忽略")
            return
        }

        if(notice != FOREGROUND_RUNNING_SERVICE_TITLE){
            LogUtils.log(Log.DEBUG,kTag, "内容 : $notice")
        }

        val notificationBean = com.autodark.bean.NotificationBean().apply {
            uuid = UUID.randomUUID().toString()
            this.packageName = packageName
            this.notificationTitle = title
            this.notificationMsg = notice
            this.postTime = System.currentTimeMillis().timestampToCompleteDate()
        }

        notificationBeanDao.save(notificationBean)

        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
        if (emailAddress.isEmpty()) {
            LogUtils.log(Log.DEBUG,kTag, "邮箱地址为空")
            "邮箱地址为空".show(this)
            return
        }

        if (packageName == Constant.DING_DING) {
            if (notice.contains("成功")) {
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(1000)
                    backToMainActivity()
                }
                // 发送打卡成功的邮件
                // 在 SharedPreferences 中保存是否已经发送
                val hasSentKey = "has_sent_email"
                val isSentRecently = SaveKeyValues.getValue(hasSentKey, false) as Boolean

                if (!isSentRecently) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        "即将发送通知邮件，请注意查收".show(this@NotificationMonitorService)
                        withContext(Dispatchers.IO) {
                            val subject = SaveKeyValues.getValue(
                                Constant.EMAIL_TITLE, "打卡结果通知"
                            ) as String
                            notice.createTextMail(subject, emailAddress).sendTextMail()
                            LogUtils.log(Log.DEBUG, kTag, "邮件发送成功")

                            // 更新标志位，表示已经发送邮件
                            SaveKeyValues.putValue(hasSentKey, true)

                            // 设置冷却时间，例如 1 秒后可以再次发送
                            lifecycleScope.launch(Dispatchers.IO) {
                                delay(1000L)  // 延迟 1 秒
                                SaveKeyValues.putValue(hasSentKey, false)
                            }
                        }
                    }
                } else {
                    LogUtils.log(Log.DEBUG, kTag, "邮件发送冷却中，跳过发送")
                }


                //通过mqtt发送通知内容
                val notification = "dark_success:$title: $notice"
                sendBroadcast(notification)

            }
        } else if (packageName in listOf(Constant.WECHAT, Constant.QQ, Constant.TIM, Constant.ZFB)) {
            if (notice.contains("电量")) {
                val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                // 发送剩余电量的邮件
                lifecycleScope.launch(Dispatchers.IO) {
                    "当前手机剩余电量为：${capacity}%".createTextMail(
                        "查询手机电量通知", emailAddress
                    ).sendTextMail()
                    LogUtils.log(Log.DEBUG,kTag, "电量邮件发送，剩余电量: $capacity%")
                }
            } else {
                val key = SaveKeyValues.getValue(Constant.DING_DING_KEY, "打卡") as String
                if (notice.contains(key)) {
                    openApplication(Constant.DING_DING)
                    LogUtils.log(Log.DEBUG,kTag, "打开钉钉应用")
                }
            }
        }
    }

    private suspend fun backToMainActivity() {
        CountDownTimerManager.get.cancelTimer()

        if (SaveKeyValues.getValue(Constant.BACK_TO_HOME, false) as Boolean) {
            // 模拟点击Home键
            val home = Intent(Intent.ACTION_MAIN).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addCategory(Intent.CATEGORY_HOME)
            }
            startActivity(home)
            LogUtils.log(Log.DEBUG,kTag, "模拟点击Home键")
            delay(1000)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        LogUtils.log(Log.DEBUG,kTag, "返回主活动")
    }

    /**
     * 当有通知移除时会回调
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        LogUtils.log(Log.DEBUG,kTag, "通知已移除")
    }
}

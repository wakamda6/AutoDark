package com.autodark.service

import com.autodark.utils.LogUtils
import android.app.Notification
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
import com.autodark.ui.MainActivity
import com.autodark.utils.Constant
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

    private val kTag = "AuToDark.NotificationMonitorService"
    private val registry = LifecycleRegistry(this)

    private var isListenerConnected = false

    private fun sendMessageToMainActivity(message: String) {
        val mIntent = Intent("com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION")
        mIntent.putExtra("message", message)
        LogUtils.log(Log.DEBUG,kTag, "即将向MainActivity发送消息：$message")
        LocalBroadcastManager.getInstance(this).sendBroadcast(mIntent) // 发送本地广播
    }

    override fun getLifecycle(): Lifecycle {
        return registry
    }

    private val notificationBeanDao by lazy { com.autodark.BaseApplication.get().daoSession.notificationBeanDao }
    private val batteryManager by lazy { getSystemService<BatteryManager>() }

    /**
     * 有可用的并且和通知管理器连接成功时回调
     */
    override fun onListenerConnected() {
        if (!isListenerConnected) {
            LogUtils.log(Log.DEBUG, kTag, "创建通知监听服务")
            isListenerConnected = true
        } else {
            LogUtils.log(Log.DEBUG, kTag, "通知监听服务已经创建")
        }
    }

    /**
     * 当有新通知到来时会回调
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val extras = sbn.notification.extras
        // 获取接收消息APP的包名
        val packageName = sbn.packageName
        // 获取接收消息的标题
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        // 获取接收消息的内容
        val notice = extras.getString(Notification.EXTRA_TEXT)

        val key = sbn.key
        val postTime = sbn.postTime
        LogUtils.log(Log.DEBUG,kTag, "收到新通知：$notice from key: $key, postTime: $postTime")

        if (notice.isNullOrBlank()) {
            LogUtils.log(Log.DEBUG, kTag, "通知发出者包名: $packageName")
            LogUtils.log(Log.DEBUG,kTag, "通知内容为空，忽略")
            return
        }

        if (notice.contains("正在监听MQTT消息")){
            return
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
                    backToMainActivity()
                }
                // 发送打卡成功的邮件
                lifecycleScope.launch(Dispatchers.Main) {
                    "即将发送通知邮件，请注意查收".show(this@NotificationMonitorService)
                    withContext(Dispatchers.IO) {
                        val subject = SaveKeyValues.getValue(
                            Constant.EMAIL_TITLE, "打卡结果通知"
                        ) as String
                        notice.createTextMail(subject, emailAddress).sendTextMail()
                        LogUtils.log(Log.DEBUG,kTag, "邮件发送成功")
                    }
                }

                //通过mqtt发送通知内容
                val notification = "dark_success:$title: $notice"
                sendMessageToMainActivity(notification)

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
                val mKey = SaveKeyValues.getValue(Constant.DING_DING_KEY, "打卡") as String
                if (notice.contains(mKey)) {
                    LogUtils.log(Log.DEBUG,kTag, "打开钉钉应用")
                    openApplication(Constant.DING_DING)
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

    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        LogUtils.log(Log.DEBUG, kTag, "通知监听服务已销毁")
    }
}

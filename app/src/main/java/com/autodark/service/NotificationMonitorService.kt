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

    companion object {
        var isConnected = false
    }


    private fun sendBroadcast(message: String) {
        LogUtils.log(Log.DEBUG,kTag, "发送打卡结果到mqtt:$message")
        val intent = Intent("com.example.MQTT_PUBLISH_DARK_RESULT")
        intent.putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent) // 发送广播
    }

    override val lifecycle: Lifecycle
        get() = registry

    private val notificationBeanDao by lazy { com.autodark.BaseApplication.get().daoSession.notificationBeanDao }
    private val batteryManager by lazy { getSystemService<BatteryManager>() }

    /**
     * 有可用的并且和通知管理器连接成功时回调
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    /**
     * 当有新通知到来时会回调
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        LogUtils.log(Log.DEBUG,kTag, "收到新通知")

        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String

        val extras = sbn.notification.extras
        // 获取接收消息APP的包名
        val packageName = sbn.packageName
        // 获取接收消息的标题
        val title = extras.getString(Notification.EXTRA_TITLE)
        // 获取接收消息的内容
        val notice = extras.getString(Notification.EXTRA_TEXT)

        //过滤空通知
        if (notice.isNullOrBlank()) {
            LogUtils.log(Log.DEBUG, kTag, "通知发出者包名: $packageName")
            LogUtils.log(Log.DEBUG,kTag, "通知内容为空，忽略")
            return
        }

        //过滤本应用通知
        if(notice != FOREGROUND_RUNNING_SERVICE_TITLE){
            LogUtils.log(Log.DEBUG,kTag, "内容 : $notice")
        }else {
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

        if (packageName == Constant.DING_DING) {
            if (notice.contains("成功")) {
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(1000)
                    backToMainActivity()
                }

                //通过mqtt发送通知内容
                val notification = "dark_success:$title: $notice"
                sendBroadcast(notification)

                // 发送打卡成功的邮件
                if (emailAddress.isEmpty()) {
                    LogUtils.log(Log.DEBUG,kTag, "邮箱地址为空")
                    "邮箱地址为空".show(this)
                    return
                }

                lifecycleScope.launch(Dispatchers.Main) {
                    "即将发送通知邮件，请注意查收".show(this@NotificationMonitorService)
                    withContext(Dispatchers.IO) {
                        val subject = SaveKeyValues.getValue(
                            Constant.EMAIL_TITLE, "打卡结果通知"
                        ) as String
                        notice.createTextMail(subject, emailAddress).sendTextMail()
                        LogUtils.log(Log.DEBUG, kTag, "邮件发送成功")
                    }
                }
            }
//            if (notice.contains("记得打卡")) {
//                // 发送打卡提醒邮件
//                if (emailAddress.isEmpty()) {
//                    LogUtils.log(Log.DEBUG,kTag, "邮箱地址为空")
//                    "邮箱地址为空".show(this)
//                    return
//                }
//
//                lifecycleScope.launch(Dispatchers.Main) {
//                    "即将发送通知邮件，请注意查收".show(this@NotificationMonitorService)
//                    withContext(Dispatchers.IO) {
//                        val subject = SaveKeyValues.getValue(
//                            Constant.EMAIL_TITLE, "打卡提醒"
//                        ) as String
//                        notice.createTextMail(subject, emailAddress).sendTextMail()
//                        LogUtils.log(Log.DEBUG, kTag, "邮件发送成功")
//                    }
//                }
//            }
        } else if (packageName in listOf(Constant.WECHAT, Constant.QQ, Constant.TIM, Constant.ZFB)) {
            if (notice.contains("电量")) {
                if (emailAddress.isEmpty()) {
                    LogUtils.log(Log.DEBUG,kTag, "邮箱地址为空")
                    "邮箱地址为空".show(this)
                    return
                }
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

        // 模拟点击Home键
        val home = Intent(Intent.ACTION_MAIN).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addCategory(Intent.CATEGORY_HOME)
        }
        startActivity(home)
        LogUtils.log(Log.DEBUG,kTag, "模拟点击Home键")
        delay(1000)

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

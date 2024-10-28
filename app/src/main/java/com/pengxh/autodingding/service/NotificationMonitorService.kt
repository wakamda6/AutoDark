package com.pengxh.autodingding.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pengxh.autodingding.BaseApplication
import com.pengxh.autodingding.bean.NotificationBean
import com.pengxh.autodingding.extensions.createTextMail
import com.pengxh.autodingding.extensions.openApplication
import com.pengxh.autodingding.extensions.sendTextMail
import com.pengxh.autodingding.fragment.SettingsFragment
import com.pengxh.autodingding.ui.MainActivity
import com.pengxh.autodingding.utils.Constant
import com.pengxh.autodingding.utils.CountDownTimerManager
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

    private val kTag = "AuToDark.MonitorService"
    private val registry = LifecycleRegistry(this)

    private lateinit var receiver: BroadcastReceiver
    override fun onCreate() {
        super.onCreate()

        // 创建广播接收器
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理广播
                val message = intent?.getStringExtra("message")
                Log.d("NotificationMonitorService", "Received message: $message")
            }
        }

        // 注册本地广播接收器
        val filter = IntentFilter("com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION")
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注销本地广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    private fun sendMessageToMainActivity(message: String) {
        val mIntent = Intent("com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION")
        mIntent.putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(mIntent) // 发送本地广播
    }



    override fun getLifecycle(): Lifecycle {
        return registry
    }

    private val notificationBeanDao by lazy { BaseApplication.get().daoSession.notificationBeanDao }
    private val batteryManager by lazy { getSystemService<BatteryManager>() }

    /**
     * 有可用的并且和通知管理器连接成功时回调
     */
    override fun onListenerConnected() {
        Log.d(kTag, "onListenerConnected: 通知监听服务运行中")
        SettingsFragment.weakReferenceHandler?.sendEmptyMessage(2024090801)
    }

    /**
     * 当有新通知到来时会回调
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(kTag, "onNotificationPosted: 收到新通知")

        val extras = sbn.notification.extras
        // 获取接收消息APP的包名
        val packageName = sbn.packageName
        // 获取接收消息的标题
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        // 获取接收消息的内容
        val notice = extras.getString(Notification.EXTRA_TEXT)

        if (notice.isNullOrBlank()) {
            Log.d(kTag, "onNotificationPosted: 通知内容为空，忽略")
            return
        }

        Log.d(kTag, "onNotificationPosted: 内容 - $notice")
        SettingsFragment.weakReferenceHandler?.sendEmptyMessage(2024090801)

        val notificationBean = NotificationBean().apply {
            uuid = UUID.randomUUID().toString()
            this.packageName = packageName
            this.notificationTitle = title
            this.notificationMsg = notice
            this.postTime = System.currentTimeMillis().timestampToCompleteDate()
        }

        notificationBeanDao.save(notificationBean)
        Log.d(kTag, "onNotificationPosted: 保存通知信息至数据库")

        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
        if (emailAddress.isEmpty()) {
            Log.d(kTag, "onNotificationPosted: 邮箱地址为空")
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
                        Log.d(kTag, "onNotificationPosted: 邮件发送成功")
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
                    Log.d(kTag, "onNotificationPosted: 电量邮件发送，剩余电量: $capacity%")
                }
            } else {
                val key = SaveKeyValues.getValue(Constant.DING_DING_KEY, "打卡") as String
                if (notice.contains(key)) {
                    openApplication(Constant.DING_DING)
                    Log.d(kTag, "onNotificationPosted: 打开钉钉应用")
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
            Log.d(kTag, "backToMainActivity: 模拟点击Home键")
            delay(1000)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Log.d(kTag, "backToMainActivity: 返回主活动")
    }

    /**
     * 当有通知移除时会回调
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(kTag, "onNotificationRemoved: 通知已移除")
    }

    override fun onListenerDisconnected() {
        Log.d(kTag, "onListenerDisconnected: 通知监听服务已关闭")
        SettingsFragment.weakReferenceHandler?.sendEmptyMessage(2024090802)
    }
}

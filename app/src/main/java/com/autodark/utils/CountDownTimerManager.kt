package com.autodark.utils

import com.autodark.utils.LogUtils
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autodark.extensions.createTextMail
import com.autodark.extensions.sendTextMail
import com.autodark.service.FloatingWindowService
import com.autodark.ui.MainActivity
import com.pengxh.kt.lite.extensions.show
import com.pengxh.kt.lite.extensions.timestampToTime
import com.pengxh.kt.lite.utils.SaveKeyValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CountDownTimerManager private constructor() : LifecycleOwner {

    private val kTag = "AuToDark.CountDownTimerManager"
    private val registry = LifecycleRegistry(this)

    override fun getLifecycle(): Lifecycle {
        return registry
    }

    companion object {
        val get by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            CountDownTimerManager()
        }
    }

    private var timer: CountDownTimer? = null

    fun startTimer(context: Context, millisInFuture: Long, countDownInterval: Long) {
        LogUtils.log(Log.DEBUG,kTag, "startTimer: 开始倒计时")
        timer = object : CountDownTimer(millisInFuture, countDownInterval) {
            override fun onTick(millisUntilFinished: Long) {
                val tick = millisUntilFinished / 1000
                val handler = FloatingWindowService.weakReferenceHandler ?: return
                val message = handler.obtainMessage()
                message.what = 2024071701
                message.obj = tick
                handler.sendMessage(message)
            }

            override fun onFinish() {
                //如果倒计时结束，那么表明没有收到打卡成功的通知，需要将异常日志保存
                lifecycleScope.launch(Dispatchers.Main) {
                    if (SaveKeyValues.getValue(Constant.BACK_TO_HOME, false) as Boolean) {
                        //模拟点击Home键
                        val home = Intent(Intent.ACTION_MAIN)
                        home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        home.addCategory(Intent.CATEGORY_HOME)
                        context.startActivity(home)
                        LogUtils.log(Log.DEBUG,kTag, "onFinish: 模拟点击Home键")

                        delay(1000)
                    }

                    val intent = Intent(context, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)

                    val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
                    if (emailAddress.isEmpty()) {
                        "邮箱地址为空".show(context)
                        return@launch
                    }

                    "未监听到打卡通知，即将发送异常日志邮件，请注意查收".show(context)
                    withContext(Dispatchers.IO) {
                        val subject = SaveKeyValues.getValue(
                            Constant.EMAIL_TITLE, "打卡结果通知"
                        ) as String
                        "".createTextMail(subject, emailAddress).sendTextMail()
                    }

                    //通过mqtt发送
                    sendBroadcast(context, "未监听到打卡成功的通知" + System.currentTimeMillis().timestampToTime())
                }
            }
        }.start()
    }

    private fun sendBroadcast(context: Context, message: String) {
        LogUtils.log(Log.DEBUG,kTag, "发送打卡结果:$message")
        val intent = Intent("com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION")
        intent.putExtra("message", message)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent) // 发送本地广播
    }

    fun cancelTimer() {
        timer?.cancel()
        LogUtils.log(Log.DEBUG,kTag, "cancelTimer: 取消超时定时器")
    }
}
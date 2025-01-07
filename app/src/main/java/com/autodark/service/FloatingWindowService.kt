package com.autodark.service

import com.autodark.utils.LogUtils
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.autodark.R
import com.autodark.utils.Constant
import com.pengxh.kt.lite.extensions.getSystemService
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.pengxh.kt.lite.utils.WeakReferenceHandler


class FloatingWindowService : Service(), Handler.Callback {

    companion object {
        var weakReferenceHandler: WeakReferenceHandler? = null
    }

    private val kTag = "FloatingWindowService"
    private val windowManager by lazy { getSystemService<WindowManager>() }
    private val floatView by lazy {
        LayoutInflater.from(this).inflate(R.layout.window_floating, null)
    }
    private val textView by lazy { floatView.findViewById<TextView>(R.id.timeView) }
    private lateinit var floatLayoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? {
        LogUtils.log(Log.DEBUG,kTag, "onBind: 服务绑定")
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        LogUtils.log(Log.DEBUG,kTag, "onCreate: 创建悬浮窗服务")
        weakReferenceHandler = WeakReferenceHandler(this)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                // Android 7.1.1及以下
                WindowManager.LayoutParams.TYPE_PHONE
            }
        } else {
            // 其他版本
            WindowManager.LayoutParams.TYPE_TOAST
        }

        floatLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(floatView, floatLayoutParams)
            LogUtils.log(Log.DEBUG,kTag, "onCreate: 悬浮窗视图已添加")

            var lastX = 0
            var lastY = 0
            var paramX = 0
            var paramY = 0

            floatView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        paramX = floatLayoutParams.x
                        paramY = floatLayoutParams.y
                        LogUtils.log(Log.DEBUG,kTag, "onTouch: ACTION_DOWN，记录位置：$lastX, $lastY")
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX.toInt() - lastX
                        val dy = event.rawY.toInt() - lastY
                        floatLayoutParams.x = paramX + dx
                        floatLayoutParams.y = paramY + dy
                        // 更新悬浮窗位置
                        windowManager?.updateViewLayout(floatView, floatLayoutParams)
                        LogUtils.log(Log.DEBUG,kTag, "onTouch: ACTION_MOVE，更新位置：${floatLayoutParams.x}, ${floatLayoutParams.y}")
                    }
                }
                false
            }
        } catch (e: IllegalStateException) {
            LogUtils.log(Log.DEBUG,kTag, "onCreate: IllegalStateException")
        } catch (e: WindowManager.BadTokenException) {
            LogUtils.log(Log.DEBUG,kTag, "onCreate: BadTokenException")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.log(Log.DEBUG,kTag, "onDestroy: 销毁悬浮窗服务")
        windowManager?.removeView(floatView)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val time = SaveKeyValues.getValue(Constant.TIMEOUT, "15s") as String
        textView.text = time
        LogUtils.log(Log.DEBUG,kTag, "onStartCommand: 设置初始时间为 $time")
        return START_STICKY
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            2024071701 -> {
                val time = msg.obj as Long
                textView.text = "${time}s"
            }

            2024071702 -> {
                val time = msg.obj as String
                textView.text = time
                LogUtils.log(Log.DEBUG,kTag, "handleMessage: 更新显示时间为 $time")
            }
        }
        return true
    }
}

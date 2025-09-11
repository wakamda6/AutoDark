package com.autodark.ui

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import com.autodark.R
import com.autodark.databinding.ActivityMainBinding
import com.autodark.fragment.SettingsFragment
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import android.util.Log
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.autodark.BaseApplication
import com.autodark.adapter.BaseFragmentAdapter
import com.autodark.extensions.createTextMail
import com.autodark.extensions.initImmersionBar
import com.autodark.extensions.sendTextMail
import com.autodark.model.InitState
import com.autodark.model.InitViewModel
import com.autodark.model.MqttConnectionState
import com.autodark.model.MqttStateHolder
import com.autodark.service.MqttService
import com.autodark.utils.Constant
import com.autodark.utils.LogUtils
import com.autodark.utils.PermissionManager
import com.pengxh.kt.lite.utils.SaveKeyValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.collections.ArrayList

class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {

    private val kTag = "MainActivity"

    private var darkID:String = ""
    private var caTimes:String = ""

    //viewModel
    private val viewModel: InitViewModel by viewModels()

    //页面设置
    private val fragmentPages = ArrayList<Fragment>()
    private val settingsFragment = SettingsFragment()
    private lateinit var insetsController: WindowInsetsControllerCompat
    private var clickTime: Long = 0

    //电量检测广播
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale

            if (batteryPct <= 25) {
                // 在每次收到广播时重新获取邮箱地址
                val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String

                if (emailAddress.isBlank()) {
                    LogUtils.log(Log.WARN, kTag, "警告：邮箱地址为空，电量邮件未发送")
                    return
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        "当前手机剩余电量为：${batteryPct}%".createTextMail(
                            "警告：电量过低！", emailAddress
                        ).sendTextMail()
                        LogUtils.log(Log.DEBUG, kTag, "警告：电量过低，电量邮件发送，剩余电量: $batteryPct%")
                    } catch (e: Exception) {
                        LogUtils.log(Log.ERROR, kTag, "发送电量邮件失败: ${e.message}")
                    }
                }
            }
        }
    }


    init {
        fragmentPages.add(settingsFragment)
    }


    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        insetsController = WindowCompat.getInsetsController(window, binding.rootView)
        binding.rootView.initImmersionBar(this, true, R.color.mainBackground)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun initEvent() {

    }


    override fun initOnCreate(savedInstanceState: Bundle?) {

        // 初始化 LogUtils
        LogUtils.initialize(this)

        // 测试日志输出
        LogUtils.log(Log.INFO, kTag, "应用启动成功")

        //id获取
        darkID = (applicationContext as BaseApplication).androidId
        caTimes = (applicationContext as BaseApplication).caTimes

        val fragmentAdapter = BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size  // 强制加载所有 Fragment

        //证书验证
        viewModel.initState.observe(this) { state ->
            when (state) {
                is InitState.Success -> {
                    settingsFragment.setIdText(darkID,state.remaining)

                    //mqtt启动
                    if (state.forceRestartMqtt){
                        restartMqttService()
                    }else{
                        startService(Intent(this, MqttService::class.java))
                    }
                }
                is InitState.Failed -> {
                    showErrorDialog(state.reason)
                }
            }
        }

        //mqtt状态监听
        MqttStateHolder.mqttState.observe(this) { state ->
            when (state) {
                is MqttConnectionState.CONNECTING -> {
                    settingsFragment.setMqttText("正在连接")
                }
                is MqttConnectionState.CONNECTED -> {
                    settingsFragment.setMqttText("已连接")
                }
                is MqttConnectionState.RECONNECTED -> {
                    settingsFragment.setMqttText("已连接")
                    sendMqttStateEmail(true,"")
                }
                is MqttConnectionState.DISCONNECTED -> {
                    settingsFragment.setMqttText("已断开连接")
                }
                is MqttConnectionState.ERROR -> {
                    settingsFragment.setMqttText("连接错误")
                    sendMqttStateEmail(false,state.message)
                }
            }
        }

        //电量
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

    }

    // 发送mqtt连接失败邮件
    private fun sendMqttStateEmail(isReconnect: Boolean, message: String) {
        // 动态获取邮箱地址
        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String

        if (emailAddress.isBlank()) {
            LogUtils.log(Log.WARN, kTag, "警告：邮箱地址为空，电量邮件未发送")
            return
        }

        if (isReconnect){
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    "mqtt重连成功".createTextMail(
                        "mqtt恢复连接：mqtt重连成功", emailAddress
                    ).sendTextMail()
                    LogUtils.log(Log.DEBUG, kTag, message)
                } catch (e: Exception) {
                    LogUtils.log(Log.ERROR, kTag, "发送mqtt连接失败邮件失败: ${e.message}")
                }
            }
        }else {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    "mqtt连接出错：${message}".createTextMail(
                        "警告：mqtt连接出错！", emailAddress
                    ).sendTextMail()
                    LogUtils.log(Log.DEBUG, kTag, message)
                } catch (e: Exception) {
                    LogUtils.log(Log.ERROR, kTag, "发送mqtt连接失败邮件失败: ${e.message}")
                }
            }
        }
    }

    override fun observeRequestState() {

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - clickTime > 2000) {
                "再按一次退出应用".show(this)
                clickTime = System.currentTimeMillis()
                true
            } else {
                super.onKeyDown(keyCode, event)
            }
        } else super.onKeyDown(keyCode, event)
    }

    private fun showErrorDialog(reason: String) {
        AlertDialog.Builder(this)
            .setTitle("初始化失败")
            .setMessage(reason)
            .setCancelable(false)
            .setPositiveButton("重试") { _, _ ->
                viewModel.initCertificateCheck(darkID)
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .show()
    }

    //在证书验证失败删除证书后必须重启mqtt
    private fun restartMqttService() {
        LogUtils.log(Log.INFO, kTag, "重新启动mqtt服务")
        // 先停止服务，触发 onDestroy（释放 MQTT 连接）
        stopService(Intent(this, MqttService::class.java))

        // 延迟几百毫秒后再重启，避免冲突
        Handler(Looper.getMainLooper()).postDelayed({
            startService(Intent(this, MqttService::class.java))
        }, 1000)
    }


    //正常返回桌面后再进入需要检测证书
    override fun onResume() {
        super.onResume()
        //证书检查
        viewModel.initCertificateCheck(darkID)
    }

    override fun onDestroy() {
        PermissionManager.dismissDialog()
        //电量
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }
}
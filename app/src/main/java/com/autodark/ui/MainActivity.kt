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
import android.text.InputType
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import com.autodark.R
import com.autodark.databinding.ActivityMainBinding
import com.autodark.fragment.SettingsFragment
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
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
import com.autodark.utils.TlsConfig
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

    // 1. 在 Activity 或全局定义一个标记位
    private var hasSentLowBatteryWarning = false
    //电量检测广播
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale

            if (batteryPct <= 25) {
                // 2. 检查是否已经发过警报
                if (!hasSentLowBatteryWarning) {
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
                            // 3. 发送成功后，标记为已发送
                            hasSentLowBatteryWarning = true
                            LogUtils.log(Log.DEBUG, kTag, "警告：电量过低，电量邮件发送，剩余电量: $batteryPct%")
                        } catch (e: Exception) {
                            LogUtils.log(Log.ERROR, kTag, "发送电量邮件失败: ${e.message}")
                        }
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

    /**
     * 显示服务器地址输入弹窗
     * @param defaultDomain 当前地址，作为默认值
     */
    private fun showDomainInputDialog(defaultDomain: String = "") {
        val app = applicationContext as BaseApplication

        val inputEditText = EditText(this).apply {
            setText(defaultDomain)
            hint = "请输入服务器地址（域名或IP）"
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            if (defaultDomain.isNotEmpty()) setSelection(defaultDomain.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("修改服务器地址")
            .setMessage("请输入服务器地址（域名或IP）：")
            .setView(inputEditText)
            .setCancelable(false)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            dialog.dismiss()
        }

        // 动态拦截确定按钮，防止输入空格或留空
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val inputAddress = inputEditText.text.toString().trim()
            if (inputAddress.isEmpty()) {
                inputEditText.error = "地址不能为空！"
            } else {
                app.domainAddress = inputAddress
                dialog.dismiss()
                settingsFragment.refreshServerAddress()
                // 根据地址类型设置默认连接方式：IP→无加密，域名→单向
                TlsConfig.mode = if (TlsConfig.isIpAddress(inputAddress)) TlsConfig.MODE_NONE else TlsConfig.MODE_ONE_WAY
                settingsFragment.refreshConnectionMode()
                // 按新地址+模式重新初始化并重连 MQTT
                onTlsModeChanged()
            }
        }
    }

    private fun proceedWithInitialization() {
        val app = applicationContext as BaseApplication

        // id获取
        darkID = app.androidId
        caTimes = app.caTimes

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
                    showErrorDialog(state.reason, app.domainAddress)
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

        // 触发首次证书验证（仅当已配置服务器地址）
        if (app.domainAddress.isNotEmpty()) {
            viewModel.initCertificateCheck(darkID)
        }
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {

        // 初始化 LogUtils
        LogUtils.initialize(this)
        // 测试日志输出
        LogUtils.log(Log.INFO, kTag, "应用启动成功")

        // 直接进入设置页，不再强制输入服务器地址，由用户在页面内自行配置
        proceedWithInitialization()
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

    private fun showErrorDialog(reason: String, currentDomain: String) {
        AlertDialog.Builder(this)
            .setTitle("初始化失败")
            .setMessage(reason)
            .setCancelable(false)
            .setPositiveButton("重试") { _, _ ->
                viewModel.initCertificateCheck(darkID)
            }
            .setNeutralButton("修改服务器地址") { _, _ ->
                // 打开输入框，并传入当前地址作为默认值
                showDomainInputDialog(defaultDomain = currentDomain)
            }
            .setNegativeButton("退出双向认证") { _, _ ->
                exitMutualTlsMode()
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

    // TLS 模式切换后重新初始化：重置 SSL、停掉 MQTT、重新校验证书
    fun onTlsModeChanged() {
        LogUtils.log(Log.INFO, kTag, "TLS 模式切换，重新初始化")
        MqttConfigHolder.reset()
        stopService(Intent(this, MqttService::class.java))
        val app = applicationContext as BaseApplication
        if (darkID.isNotEmpty() && app.domainAddress.isNotEmpty()) {
            viewModel.initCertificateCheck(darkID)
        }
    }

    // 退出双向认证：回退到进入双向之前的模式
    private fun exitMutualTlsMode() {
        LogUtils.log(Log.INFO, kTag, "退出双向认证，回退到之前模式")
        TlsConfig.revertToPrevious()
        settingsFragment.refreshConnectionMode()
        onTlsModeChanged()
    }

    // MQTT 账号密码修改后重新连接
    fun onMqttAuthChanged() {
        LogUtils.log(Log.INFO, kTag, "MQTT 账号修改，重新连接")
        restartMqttService()
    }

    // 设置页点击修改服务器地址
    fun onEditDomain() {
        val app = applicationContext as BaseApplication
        showDomainInputDialog(defaultDomain = app.domainAddress)
    }


    //正常返回桌面后再进入需要检测证书
    override fun onResume() {
        super.onResume()
        //证书检查（服务器地址已配置时才进行）
        val app = applicationContext as BaseApplication
        if (darkID.isNotEmpty() && app.domainAddress.isNotEmpty()) {
            viewModel.initCertificateCheck(darkID)
        }
    }

    override fun onDestroy() {
        PermissionManager.dismissDialog()
        //电量
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }
}
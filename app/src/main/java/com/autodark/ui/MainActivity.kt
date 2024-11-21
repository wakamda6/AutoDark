package com.autodark.ui

import android.app.ActivityManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.gyf.immersionbar.ImmersionBar
import com.autodark.R
import com.autodark.databinding.ActivityMainBinding
import com.autodark.extensions.isAppAvailable
import com.autodark.fragment.DingDingFragment
import com.autodark.fragment.SettingsFragment
import com.autodark.utils.Constant
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import com.pengxh.kt.lite.utils.ActivityStackManager
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autodark.service.MqttService
import com.autodark.utils.LogUtils
import android.provider.Settings

class MainActivity : KotlinBaseActivity<ActivityMainBinding>(), MqttService.MyMqttCallback {

    private val kTag = "AuToDark.MainActivity"

    private lateinit var dingDingFragment: DingDingFragment
    private lateinit var settingsFragment: SettingsFragment


    private var menuItem: MenuItem? = null
    private var clickTime: Long = 0

    //和前台服务mqtt server进行绑定
    private lateinit var mqttService: MqttService
    private var isBound = false
    private lateinit var receiver: BroadcastReceiver

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MqttService.MqttBinder
            mqttService = binder.getService()
            isBound = true
            // 注册回调
            mqttService.setMyMqttCallback(this@MainActivity)
            LogUtils.log(Log.DEBUG,kTag, "MQTT前台服务已连接")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            LogUtils.log(Log.WARN,kTag, "MQTT前台服务已断开")
        }
    }


    override fun initOnCreate(savedInstanceState: Bundle?) {

        // 初始化 LogUtils
        LogUtils.initialize(this)

        // 测试日志输出
        LogUtils.log(Log.DEBUG, kTag, "应用启动成功")
        ActivityStackManager.addActivity(this)

        if (!isAppAvailable(Constant.DING_DING)) {
            LogUtils.log(Log.DEBUG,kTag, "DingDing 应用不可用，显示警告对话框")
            showAlertDialog()
            return
        }

        LogUtils.log(Log.DEBUG,kTag, "正在初始化页面")
        dingDingFragment = DingDingFragment()
        settingsFragment = SettingsFragment()
        val fragmentPages = ArrayList<Fragment>()

        fragmentPages.add(dingDingFragment)
        fragmentPages.add(settingsFragment)

        LogUtils.log(Log.DEBUG,kTag, "正在设置页面适配器")
        val fragmentAdapter =
            com.autodark.adapter.BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size



        // 创建并注册本地广播接收器
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理接收到的消息
                val message = intent?.getStringExtra("message")
                LogUtils.log(Log.DEBUG,kTag, "接收到来自应用内广播消息: $message")
                if (intent?.action == "com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION") {
                    if (message != null) {
                        pushMqttMessage("/topic/darkResult", message,1)
                    }
                }
            }

        }

        val mqttFilter = IntentFilter("com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION")
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, mqttFilter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }


    }

    override fun initEvent() {
        LogUtils.log(Log.DEBUG,kTag, "初始化底部导航监听")
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val itemId: Int = item.itemId
            LogUtils.log(Log.DEBUG,kTag, "选中的菜单项ID: $itemId")

            if (itemId == R.id.nav_dingding) {
                if (isAppAvailable(Constant.DING_DING)) {
                    LogUtils.log(Log.DEBUG,kTag, "DingDing 应用可用，切换到第一个页面")
                    binding.viewPager.currentItem = 0
                } else {
                    LogUtils.log(Log.DEBUG,kTag, "DingDing 应用不可用，显示警告对话框")
                    showAlertDialog()
                }
            } else if (itemId == R.id.nav_settings) {
                LogUtils.log(Log.DEBUG,kTag, "切换到设置页面")
                binding.viewPager.currentItem = 1
            }
            false
        }

        LogUtils.log(Log.DEBUG,kTag, "添加页面改变监听")
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // 添加滚动相关的日志
            }

            override fun onPageSelected(position: Int) {
                LogUtils.log(Log.DEBUG,kTag, "选中的页面: $position")

                if (menuItem != null) {
                    LogUtils.log(Log.DEBUG,kTag, "取消选中菜单项: ${menuItem!!.itemId}")
                    menuItem!!.isChecked = false
                } else {
                    LogUtils.log(Log.DEBUG,kTag, "取消选中默认菜单项")
                    binding.bottomNavigation.menu.getItem(0).isChecked = false
                }

                menuItem = binding.bottomNavigation.menu.getItem(position)
                LogUtils.log(Log.DEBUG,kTag, "选中菜单项: ${menuItem!!.itemId}")
                menuItem!!.isChecked = true
            }

            override fun onPageScrollStateChanged(state: Int) {
                // 添加页面滚动状态改变相关的日志
            }
        })
    }

    override fun onStart() {
        super.onStart()
        // 检查服务是否存活，如果没有则启动
        if (!isServiceRunning(MqttService::class.java)) {
            LogUtils.log(Log.INFO, kTag, "MQTT 服务未运行，正在启动")
            startService(Intent(this, MqttService::class.java))
        } else {
            LogUtils.log(Log.INFO, kTag, "MQTT 服务已运行")
        }

        // 绑定服务
        Intent(this, MqttService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        //判断mqtt前台服务是否运行
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onMqttStatusChanged(status: String) {
        // 显示通知或弹出消息
        runOnUiThread {
            status.show(this)
        }
    }

    fun pushMqttMessage(topic: String, message: String,qoS: Int) {
        // 调用发送消息的方法
        if (isBound) {
            mqttService.publishMessage(topic, message,qoS)
        }
    }

    override fun observeRequestState() {

    }

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        ImmersionBar.with(this).statusBarDarkFont(true).init()
    }

    private fun showAlertDialog() {
        AlertMessageDialog.Builder()
            .setContext(this)
            .setTitle("温馨提醒")
            .setMessage("手机没有安装《钉钉》软件，无法自动打卡")
            .setPositiveButton("知道了")
            .setOnDialogButtonClickListener(object :
                AlertMessageDialog.OnDialogButtonClickListener {
                override fun onConfirmClick() {

                }
            }).build().show()
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

    override fun onStop() {
        super.onStop()
        if (isBound) {
            LogUtils.log(Log.WARN, kTag, "解绑 MQTT 服务，但保持运行")
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        LogUtils.log(Log.DEBUG,kTag, "清理程序开始")
        // 注销本地广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        LogUtils.log(Log.DEBUG,kTag, "注销本地广播接收器")
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            LogUtils.log(Log.DEBUG,kTag, "解绑 MQTT 服务")
        }
        // 停止服务
        stopService(Intent(this, MqttService::class.java))
        LogUtils.log(Log.DEBUG,kTag, "停止 MQTT 服务")
        super.onDestroy()
    }
}
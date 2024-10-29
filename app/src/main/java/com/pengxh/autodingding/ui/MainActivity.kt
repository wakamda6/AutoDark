package com.pengxh.autodingding.ui

import android.content.*
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.gyf.immersionbar.ImmersionBar
import com.pengxh.autodingding.R
import com.pengxh.autodingding.adapter.BaseFragmentAdapter
import com.pengxh.autodingding.databinding.ActivityMainBinding
import com.pengxh.autodingding.extensions.isAppAvailable
import com.pengxh.autodingding.fragment.DingDingFragment
import com.pengxh.autodingding.fragment.SettingsFragment
import com.pengxh.autodingding.utils.Constant
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import com.pengxh.kt.lite.utils.ActivityStackManager
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pengxh.autodingding.service.MqttService

class MainActivity : KotlinBaseActivity<ActivityMainBinding>(), MqttService.MyMqttCallback {

    //和前台服务mqtt server进行绑定
    private lateinit var mqttService: MqttService
    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MqttService.MqttBinder
            mqttService = binder.getService()
            isBound = true
            // 注册回调
            mqttService.setMyMqttCallback(this@MainActivity)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }
    override fun onStart() {
        super.onStart()
        Intent(this, MqttService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onMqttStatusChanged(status: String) {
        // 显示通知或弹出消息
        runOnUiThread {
            status.show(this)
        }
    }

    // 调用发送消息的方法
    fun pushMqttMessage(topic: String, message: String,qoS: Int) {
        if (isBound) {
            mqttService.publishMessage(topic, message,qoS)
        }
    }

    private lateinit var dingDingFragment: DingDingFragment
    private lateinit var settingsFragment: SettingsFragment


    private var menuItem: MenuItem? = null
    private var clickTime: Long = 0

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        ImmersionBar.with(this).statusBarDarkFont(true).init()
    }

    private lateinit var receiver: BroadcastReceiver
    override fun initOnCreate(savedInstanceState: Bundle?) {
        Log.d("AuToDark.MainActivity", "将活动添加到栈中")
        ActivityStackManager.addActivity(this)

        if (!isAppAvailable(Constant.DING_DING)) {
            Log.d("AuToDark.MainActivity", "DingDing 应用不可用，显示警告对话框")
            showAlertDialog()
            return
        }

        Log.d("AuToDark.MainActivity", "正在初始化页面")
        dingDingFragment = DingDingFragment()
        settingsFragment = SettingsFragment()
        val fragmentPages = ArrayList<Fragment>()

        fragmentPages.add(dingDingFragment)
        fragmentPages.add(settingsFragment)

        Log.d("AuToDark.MainActivity", "正在设置页面适配器")
        val fragmentAdapter = BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size

        // 创建并注册本地广播接收器
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理接收到的消息
                val message = intent?.getStringExtra("message")
                Log.d("MainActivity", "Received message: $message")
                if (intent?.action == "com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION") {
                    Log.d("AuToDark.connectToMqtt", "收到通知：$message")
                    if (message != null) {
                        pushMqttMessage("/topic/darkResult", message,1)
                    }
                }
            }
        }

        val mqttFilter = IntentFilter("com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION")
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, mqttFilter)
    }

    override fun initEvent() {
        Log.d("AuToDark.MainActivity", "初始化底部导航监听")
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val itemId: Int = item.itemId
            Log.d("AuToDark.MainActivity", "选中的菜单项ID: $itemId")

            if (itemId == R.id.nav_dingding) {
                if (isAppAvailable(Constant.DING_DING)) {
                    Log.d("AuToDark.MainActivity", "DingDing 应用可用，切换到第一个页面")
                    binding.viewPager.currentItem = 0
                } else {
                    Log.d("AuToDark.MainActivity", "DingDing 应用不可用，显示警告对话框")
                    showAlertDialog()
                }
            } else if (itemId == R.id.nav_settings) {
                Log.d("AuToDark.MainActivity", "切换到设置页面")
                binding.viewPager.currentItem = 1
            }
            false
        }

        Log.d("AuToDark.MainActivity", "添加页面改变监听")
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // 添加滚动相关的日志
            }

            override fun onPageSelected(position: Int) {
                Log.d("AuToDark.MainActivity", "选中的页面: $position")

                if (menuItem != null) {
                    Log.d("AuToDark.MainActivity", "取消选中菜单项: ${menuItem!!.itemId}")
                    menuItem!!.isChecked = false
                } else {
                    Log.d("AuToDark.MainActivity", "取消选中默认菜单项")
                    binding.bottomNavigation.menu.getItem(0).isChecked = false
                }

                menuItem = binding.bottomNavigation.menu.getItem(position)
                Log.d("AuToDark.MainActivity", "选中菜单项: ${menuItem!!.itemId}")
                menuItem!!.isChecked = true
            }

            override fun onPageScrollStateChanged(state: Int) {
                // 添加页面滚动状态改变相关的日志
            }
        })
    }

    override fun observeRequestState() {

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


    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
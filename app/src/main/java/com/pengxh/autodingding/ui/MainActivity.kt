package com.pengxh.autodingding.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import org.eclipse.paho.client.mqttv3.*
import info.mqtt.android.service.MqttAndroidClient
import android.net.ConnectivityManager
import android.util.Log
import android.content.IntentFilter
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pengxh.autodingding.extensions.openApplication
import com.pengxh.autodingding.utils.NetworkUtils
import info.mqtt.android.service.Ack
import java.io.IOException
import java.util.Properties


class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {

    private lateinit var dingDingFragment: DingDingFragment
    private lateinit var settingsFragment: SettingsFragment

    //mqtt set
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var properties: Properties
    private lateinit var mqttServerUrl: String
    private lateinit var mqttClientId: String
    private lateinit var user: String
    private lateinit var pwd: String

    private var menuItem: MenuItem? = null
    private var clickTime: Long = 0

    //
    private lateinit var receiver: BroadcastReceiver


    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        ImmersionBar.with(this).statusBarDarkFont(true).init()
    }

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

        // MQTT 设置
        Log.d("AuToDark.MainActivity", "加载 MQTT 配置")
        loadProperties()

        // 网络变化接收器初始化
        Log.d("AuToDark.MainActivity", "初始化网络变化接收器")
        networkChangeReceiver = NetworkChangeReceiver(this)

        // 注册接收器，监听网络变化
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, filter)
        Log.d("AuToDark.MainActivity", "网络变化接收器已注册")

        // 创建并注册广播接收器
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理接收到的消息
                val message = intent?.getStringExtra("message")
                Log.d("MainActivity", "Received message: $message")
                if (intent?.action == "com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION") {
                    Log.d("AuToDark.connectToMqtt", "收到通知：$message")
                    if (message != null) {
                        publishMessage(mqttTopicDarkResult, message,1)
                    }  // 调用 MainActivity 的函数并传递参数
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

    //mqtt配置文件导入
    private lateinit var mqttTopicTest: String
    private lateinit var mqttTopicTestResult: String
    private lateinit var mqttTopicCheckAppAlive: String
    private lateinit var mqttTopicCheckAppAliveResult: String
    private lateinit var mqttTopicDark: String
    private lateinit var mqttTopicDarkResult: String
    private fun loadProperties() {
        properties = Properties()
        try {
            assets.open("config.properties").use { inputStream ->
                properties.load(inputStream)
                // 将配置文件中的值赋给类属性
                mqttServerUrl = properties.getProperty("mqttServerUrl") ?: ""
                mqttClientId = properties.getProperty("mqttClientId") ?: ""
                user = properties.getProperty("user") ?: ""
                pwd = properties.getProperty("pwd") ?: ""
                mqttTopicTest = properties.getProperty("mqttTopicTest") ?: ""
                mqttTopicTestResult = properties.getProperty("mqttTopicTestResult") ?: ""
                mqttTopicCheckAppAlive = properties.getProperty("mqttTopicCheckAppAlive") ?: ""
                mqttTopicCheckAppAliveResult = properties.getProperty("mqttTopicCheckAppAliveResult") ?: ""
                mqttTopicDark = properties.getProperty("mqttTopicDark") ?: ""
                mqttTopicDarkResult = properties.getProperty("mqttTopicDarkResult") ?: ""
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    var isConnecting = false
    fun connectToMqtt() {
        Log.d("AuToDark.connectToMqtt", "尝试连接到 MQTT 代理")

        if (isMqttConnected() || isConnecting) {
            Log.d("AuToDark.connectToMqtt", "已经连接或正在连接中，取消连接请求")
            return
        }

        isConnecting = true
        Log.d("AuToDark.connectToMqtt", "设置连接状态为正在连接")

        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.e("AuToDark.connectToMqtt", "网络不可用，无法连接到 MQTT 代理")
            return
        }

        mqttClient = MqttAndroidClient(applicationContext, mqttServerUrl, mqttClientId, Ack.AUTO_ACK)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
            userName = user
            password = pwd.toCharArray()
        }

        try {
            Log.d("AuToDark.connectToMqtt", "连接到 MQTT 代理: $mqttServerUrl")
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("AuToDark.connectToMqtt", "MQTT 连接成功")
                    isConnecting = false

                    val topicsToSubscribe = arrayOf(mqttTopicTest, mqttTopicCheckAppAlive,mqttTopicDark,mqttTopicDarkResult)
                    val qosLevels = intArrayOf(1,1,1,1) // QoS 级别
                    Log.d("AuToDark.connectToMqtt", "连接成功，开始订阅主题: ${topicsToSubscribe.joinToString()}")
                    subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("AuToDark.connectToMqtt", "MQTT 连接失败: ${exception?.message}")
                    isConnecting = false
                }
            })
        } catch (e: MqttException) {
            Log.e("AuToDark.connectToMqtt", "MQTT 连接异常: ${e.message}")
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.e("AuToDark.connectToMqtt", "MQTT 连接丢失: ${cause?.message}，将自动尝试重连")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msg = String(it.payload) // 将消息体转换为字符串
                    Log.d("AuToDark.connectToMqtt", "收到主题 $topic 的消息: $msg")

                    when (topic) {
                        mqttTopicDark -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicDark 的消息，打开相关应用")
                            "收到主题 $topic 的消息: $msg，即将进行Dark".show(this@MainActivity)
                            Thread.sleep(1000);
                            openApplication(Constant.DING_DING)
                            Thread.sleep(1000);
//                            publishMessage(mqttTopicDarkResult, "darkPhone_success", 1)
                        }
                        mqttTopicTest -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicTest 的消息，发布测试消息")
                            publishMessage(mqttTopicTestResult, "darkPhone_testCheck", 1)
                        }
                        mqttTopicCheckAppAlive -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicCheckAppAlive 的消息，检查订阅主题的设备是否都正常连接")
                            publishMessage(mqttTopicCheckAppAliveResult, "darkPhone_alive", 1)
                        }
                        mqttTopicDarkResult -> {
                            Log.d("AuToDark.connectToMqtt", "处理主题 $mqttTopicDarkResult 的消息")
                        }
                        else -> {

                        }
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d("AuToDark.connectToMqtt", "消息发送完成，消息 ID: ${token?.messageId}")
            }
        })
    }


    fun isMqttConnected(): Boolean {
        Log.d("AuToDark.MainActivity", "检查 MQTT 连接状态")

        return try {
            val isConnected = ::mqttClient.isInitialized && mqttClient.isConnected
            Log.d("AuToDark.MainActivity", "MQTT 连接状态: $isConnected")
            isConnected
        } catch (e: UninitializedPropertyAccessException) {
            Log.e("AuToDark.MainActivity", "MQTT 客户端未初始化，连接状态为 false")
            false
        }
    }

    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("AuToDark.MainActivity", "成功订阅主题: ${topics.joinToString(", ")}")
                    "成功订阅主题: ${topics.joinToString(", ")}".show(this@MainActivity)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("AuToDark.MainActivity", "订阅失败: ${exception?.message}")
                    "订阅失败: ${exception?.message}".show(this@MainActivity)
                }
            })
        } catch (e: MqttException) {
            Log.e("AuToDark.MainActivity", "订阅异常: ${e.message}")
            e.printStackTrace()
            "订阅异常: ${e.message}".show(this@MainActivity)
        }
    }

    //mqtt解除订阅
    /**
     * use:
     * private fun someMethodToUnsubscribe() {
     *     val topicsToUnsubscribe = arrayOf("your/topic1", "your/topic2") // 替换为要解除订阅的主题
     *     unsubscribeFromTopics(topicsToUnsubscribe)
     * }
     */
    private fun unsubscribeFromTopics(topics: Array<String>) {
        Log.d("AuToDark.MainActivity", "尝试解除订阅主题: ${topics.joinToString(", ")}")

        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("AuToDark.MainActivity", "成功解除订阅主题: ${topics.joinToString(", ")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("AuToDark.MainActivity", "解除订阅失败: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e("AuToDark.MainActivity", "解除订阅异常: ${e.message}")
            e.printStackTrace()
        }
    }


    //mqtt 发布
    private fun publishMessage(topic: String, message: String, qos: Int = 1) {
        Log.d("AuToDark.MainActivity", "尝试发布消息到主题 $topic: $message")

        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(topic, mqttMessage, null, null)
            Log.d("AuToDark.MainActivity", "消息发布成功: $message")
        } catch (e: MqttException) {
            Log.e("AuToDark.MainActivity", "消息发布失败: ${e.message}")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            // 注销接收器
            unregisterReceiver(networkChangeReceiver)
            mqttClient.disconnect()
            // 注销广播接收器
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

}
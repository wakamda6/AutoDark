package com.pengxh.autodingding.ui

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
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog
import org.eclipse.paho.client.mqttv3.*
import info.mqtt.android.service.MqttAndroidClient;
import android.net.ConnectivityManager
import android.util.Log
import android.content.IntentFilter
import android.widget.Toast
import com.pengxh.autodingding.extensions.openApplication
import com.pengxh.autodingding.utils.NetworkUtils
import info.mqtt.android.service.Ack
import java.io.IOException
import java.util.Properties


class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {

    //config set
    private lateinit var properties: Properties
    private lateinit var mqttServerUrl: String
    private lateinit var mqttClientId: String
    private lateinit var user: String
    private lateinit var pwd: String
    private lateinit var mqttTopic1: String
    private lateinit var mqttTopic2: String

    private var menuItem: MenuItem? = null
    private var clickTime: Long = 0

    //fragent
    private lateinit var dingDingFragment: DingDingFragment
    private lateinit var settingsFragment: SettingsFragment

    //mqtt set
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var mqttClient: MqttAndroidClient


    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        ImmersionBar.with(this).statusBarDarkFont(true).init()
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        ActivityStackManager.addActivity(this)

        if (!isAppAvailable(Constant.DING_DING)) {
            showAlertDialog()
            return
        }

        dingDingFragment = DingDingFragment()
        settingsFragment = SettingsFragment()
        val fragmentPages = ArrayList<Fragment>()

        fragmentPages.add(dingDingFragment)
        fragmentPages.add(settingsFragment)

        val fragmentAdapter = BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size

        val isFirst = SaveKeyValues.getValue("isFirst", true) as Boolean
        if (isFirst) {
            AlertMessageDialog.Builder()
                .setContext(this)
                .setTitle("温馨提醒")
                .setMessage("本软件仅供内部使用，严禁商用或者用作其他非法用途")
                .setPositiveButton("知道了")
                .setOnDialogButtonClickListener(object :
                    AlertMessageDialog.OnDialogButtonClickListener {
                    override fun onConfirmClick() {
                        SaveKeyValues.putValue("isFirst", false)
                    }
                }).build().show()
        }

        //mqtt set
        loadProperties()
        // 网络变化接收器初始化
        Log.d("networkChangeReceiver","init networkChangeReceiver")
        networkChangeReceiver = NetworkChangeReceiver(this)
        //mqtt connect
//        connectToMqtt()
    }

    override fun initEvent() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val itemId: Int = item.itemId
            if (itemId == R.id.nav_dingding) {
                if (isAppAvailable(Constant.DING_DING)) {
                    binding.viewPager.currentItem = 0
                } else {
                    showAlertDialog()
                }
            } else if (itemId == R.id.nav_settings) {
                binding.viewPager.currentItem = 1
            }
            false
        }

        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int, positionOffset: Float, positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                if (menuItem != null) {
                    menuItem!!.isChecked = false
                } else {
                    binding.bottomNavigation.menu.getItem(0).isChecked = false
                }
                menuItem = binding.bottomNavigation.menu.getItem(position)
                menuItem!!.isChecked = true
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

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
                mqttTopic1 = properties.getProperty("mqttTopic1") ?: ""
                mqttTopic2 = properties.getProperty("mqttTopic2") ?: ""
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
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

    override fun onStart() {
        super.onStart()
        // 注册接收器，监听网络变化
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        // 注销接收器
        unregisterReceiver(networkChangeReceiver)
    }

    var isConnecting = false
    fun connectToMqtt() {
        if (isMqttConnected() || isConnecting) {
            Log.d("MQTT", "Already connected or in the process of connecting.")
            return
        }
        isConnecting = true
        Log.d("MQTT", "Starting MQTT connection")
        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.e("MQTT", "No network available")
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
            Log.d("MQTT", "Connecting to MQTT broker at $mqttServerUrl")
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "MQTT connection successful")
//                    "MQTT连接成功".show(this@MainActivity)
                    isConnecting = false

                    val topicsToSubscribe = arrayOf(mqttTopic1, mqttTopic2)
                    val qosLevels = intArrayOf(0, 1) // 对应的QoS级别
                    subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题

                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "MQTT connection failed: ${exception?.message}")
//                    "MQTT连接失败: ${exception?.message}".show(this@MainActivity)
                    isConnecting = false
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "MQTT connection exception: ${e.message}")
            e.printStackTrace()
//            "MQTT连接异常: ${e.message}".show(this@MainActivity)
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                // 连接丢失的处理
                Log.e("MQTT", "Connection lost: ${cause?.message}")
//                Toast.makeText(this@MainActivity, "MQTT连接丢失，请检查网络", Toast.LENGTH_SHORT).show()
                // 重连由网络通知发起
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val msg = String(it.payload) // 将消息体转换为字符串
                    Log.d("MQTT", "Message arrived from topic $topic: $msg")

                    when (topic) {
                        mqttTopic1 -> {
                            // 当接收到特定主题的消息时，打开应用
                            openApplication(Constant.DING_DING)
                        }
                        mqttTopic2 -> {
                            // 发布一条测试消息
                            publishMessage(mqttTopic2, "test", 1)
                        }
                        // 可以根据需要添加其他主题的处理逻辑
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 发送完成的处理
                Log.d("MQTT", "Message delivery complete: ${token?.messageId}")
                Toast.makeText(this@MainActivity, "消息发送成功，ID: ${token?.messageId}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun isMqttConnected(): Boolean {
        return try {
            ::mqttClient.isInitialized && mqttClient.isConnected
        } catch (e: UninitializedPropertyAccessException) {
            false
        }
    }


    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    "成功订阅主题: ${topics.joinToString(", ")}".show(this@MainActivity)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    "订阅失败: ${exception?.message}".show(this@MainActivity)
                }
            })
        } catch (e: MqttException) {
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
        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    "成功解除订阅主题: ${topics.joinToString(", ")}".show(this@MainActivity)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    "解除订阅失败: ${exception?.message}".show(this@MainActivity)
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
            "解除订阅异常: ${e.message}".show(this@MainActivity)
        }
    }

    //mqtt 发布
    private fun publishMessage(topic: String, message: String, qos: Int = 1) {
        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                this.qos = qos // 设置质量服务级别
            }
            mqttClient.publish(topic, mqttMessage, null, null)
            Log.d("MQTT", "Message published: $message")
        } catch (e: MqttException) {
            Log.e("MQTT", "Failed to publish message: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

}
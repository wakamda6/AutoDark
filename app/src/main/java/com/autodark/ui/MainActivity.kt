package com.autodark.ui

import android.content.*
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import com.autodark.R
import com.autodark.databinding.ActivityMainBinding
import com.autodark.fragment.SettingsFragment
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import android.util.Log
import android.util.Patterns
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autodark.BaseApplication
import com.autodark.adapter.BaseFragmentAdapter
import com.autodark.extensions.createTextMail
import com.autodark.extensions.initImmersionBar
import com.autodark.extensions.sendTextMail
import com.autodark.service.MqttService
import com.autodark.utils.Constant
import com.autodark.utils.LogUtils
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {

    private val kTag = "MainActivity"

    var id:String = ""

    //ca文件存储位置
    private var clientEnPath:String = ""
    private var caEnPath :String = ""

    //页面设置
    private val fragmentPages = ArrayList<Fragment>()
    private lateinit var insetsController: WindowInsetsControllerCompat
    private var clickTime: Long = 0

    //广播设置
    private lateinit var notifyReceiver: BroadcastReceiver
    val notifyAction  = "com.example.ACTION_CALL_MAIN_ACTIVITY_FUNCTION"
    private lateinit var mqttReceiver: BroadcastReceiver
    val mqttTopicAction = "com.example.MQTT_PUBLISH_DARK_TOPIC"

    init {
        fragmentPages.add(SettingsFragment())
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
        id = (applicationContext as BaseApplication).androidId

        //ca文件存储位置
        clientEnPath = "${this.filesDir.absolutePath}/$id.en"
        caEnPath = this.filesDir.absolutePath + "/ca.en"

        val fragmentAdapter = BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size  // 强制加载所有 Fragment

        // 创建并注册本地广播接收器
        notifyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理接收到的消息
                val message = intent?.getStringExtra("message")
                if (intent?.action == notifyAction) {
                    LogUtils.log(Log.DEBUG,kTag, "收到CountDownTimerManager的通知：$message")
                    if (message != null) {
                        if (context != null) {
                            sendBroadcast(message)
                        }
                    }
                }
            }
        }
        val notifyFilter = IntentFilter(notifyAction)
        LocalBroadcastManager.getInstance(this).registerReceiver(notifyReceiver, notifyFilter)

        startService(Intent(this, MqttService::class.java))

//        //先设置邮箱
//        binding.viewPager.post {
//            val settingsFragment = fragmentPages[0] as? SettingsFragment
//            if (settingsFragment?.isAdded == true && settingsFragment.view != null) {
//                // 判断邮箱是否已经填入
//                val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
//                val isValidEmail = emailAddress.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()
//                if (!isValidEmail) {
//                    settingsFragment.onStartupCheck(2){}
//                }
//            }
//        }

        //判断证书是否存在，因为涉及文件下载，安卓强制非阻塞
        lifecycleScope.launch(Dispatchers.IO) {
            val success = initCertsBlocking(this@MainActivity, id)
            if (!success) {
                // 回到主线程再弹窗
                launch(Dispatchers.Main) {
                    showRetryDialog(this@MainActivity, id)
                }
            }
        }
    }

    private fun showRetryDialog(context: Context, id: String) {
        AlertMessageDialog.Builder()
            .setContext(this)
            .setTitle("证书文件下载失败")
            .setMessage("请将页面截图发送给开发者后重试\nID: $id")
            .setPositiveButton("重试")
            .setOnDialogButtonClickListener(object :
                AlertMessageDialog.OnDialogButtonClickListener {
                override fun onConfirmClick() {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val success = initCertsBlocking(this@MainActivity, id)
                        if (!success) {
                            // 回到主线程再弹窗
                            launch(Dispatchers.Main) {
                                showRetryDialog(this@MainActivity, id)
                            }
                        }
                    }
                }
            }).build().show()
    }

    fun other_init(){
        // 创建并注册mqtt前台服务，接收主页面的ID数据并广播给mqtt
//        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
//        mqttReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                // 处理接收到的消息
//                val message = intent?.getStringExtra("message")
//                if (intent?.action == mqttTopicAction) {
//                    // 发送本机主题邮件
//                    LogUtils.log(Log.DEBUG, kTag, "收到mqtt服务的通知：$message")
//                    if (emailAddress.isEmpty()) {
//                        LogUtils.log(Log.DEBUG,kTag, "onNotificationPosted: 邮箱地址为空")
//                        if (context != null) {
//                            "邮箱地址为空,请先设置邮箱并重启应用".show(context)
//                        }
//                        return
//                    }
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        message?.createTextMail(
//                            "控制手机需要订阅的主题", emailAddress
//                        )?.sendTextMail()
//                        LogUtils.log(Log.DEBUG, kTag, "发送主题成功")
//                    }
//                }
//            }
//        }
//        val mqttFilter = IntentFilter(mqttTopicAction)
//        LocalBroadcastManager.getInstance(this).registerReceiver(mqttReceiver, mqttFilter)

    }

    private fun initCertsBlocking(context: Context, id: String): Boolean {
        val clientEnPath = File(context.filesDir, "$id.en")
        val caEnPath = File(context.filesDir, "ca.en")

        val baseUrl = "https://***REMOVED***/certs/${id}/en_${id}"
        val clientEnUrl = "$baseUrl/${id}.en"
        val caEnUrl = "$baseUrl/ca.en"

        if (!clientEnPath.exists()) {
            downloadFileSuspend(clientEnUrl, clientEnPath)
        } else {
            LogUtils.log(Log.DEBUG, kTag, "客户端证书已存在")
        }

        if (!caEnPath.exists()) {
            downloadFileSuspend(caEnUrl, caEnPath)
        }else {
            LogUtils.log(Log.DEBUG, kTag, "CA证书已存在")
        }

        if (!clientEnPath.exists() || !caEnPath.exists()) {
            LogUtils.log(Log.ERROR, kTag, "证书文件下载失败")
            return false
        }

        return true
    }

    private fun downloadFileSuspend(urlStr: String, destFile: File){
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.doInput = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val input = connection.inputStream
                val output = FileOutputStream(destFile)
                input.copyTo(output)
                output.close()
                input.close()
                LogUtils.log(Log.DEBUG,kTag, "下载成功：${destFile.name}")
            } else {
                LogUtils.log(Log.DEBUG,kTag, "下载失败：$urlStr，code=${connection.responseCode}")
            }

            connection.disconnect()
        } catch (e: Exception) {
            LogUtils.log(Log.DEBUG,kTag, "异常下载 $urlStr: ${e.message}")
        }
    }

    private fun sendBroadcast(message: String) {
        LogUtils.log(Log.DEBUG,kTag, "发送打卡结果到Mqtt服务:$message")
        val intent = Intent("com.example.MQTT_PUBLISH_DARK_RESULT")
        intent.putExtra("message", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent) // 发送本地广播
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
}
package com.autodark.ui

import android.app.AlertDialog
import android.os.Bundle
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
import com.autodark.BaseApplication
import com.autodark.adapter.BaseFragmentAdapter
import com.autodark.extensions.initImmersionBar
import com.autodark.model.InitState
import com.autodark.model.InitViewModel
import com.autodark.utils.LogUtils
import com.autodark.utils.PermissionManager
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

                }
                is InitState.Failed -> {
                    showErrorDialog(state.reason)
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

    //正常返回桌面后再进入需要检测证书
    override fun onResume() {
        super.onResume()
        //证书检查
        viewModel.initCertificateCheck(darkID)
//        PermissionManager.checkAllPermissions(this)
    }
}
package com.autodark.fragment

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.autodark.R
import com.autodark.databinding.FragmentSettingsBinding
import com.autodark.extensions.initImmersionBar
import com.autodark.extensions.notificationEnable
import com.autodark.extensions.show
import com.autodark.service.FloatingWindowService
import com.autodark.service.NotificationMonitorService
import com.autodark.ui.NoticeRecordActivity
import com.autodark.ui.QuestionAndAnswerActivity
import com.autodark.utils.Constant
import com.pengxh.kt.lite.base.KotlinBaseFragment
import com.pengxh.kt.lite.extensions.convertColor
import com.pengxh.kt.lite.extensions.navigatePageTo
import com.pengxh.kt.lite.extensions.setScreenBrightness
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.pengxh.kt.lite.utils.WeakReferenceHandler
import com.pengxh.kt.lite.widget.dialog.AlertInputDialog
import com.pengxh.kt.lite.widget.dialog.BottomActionSheet
import com.autodark.utils.LogUtils
import android.util.Log


class SettingsFragment : KotlinBaseFragment<FragmentSettingsBinding>(), Handler.Callback {

    companion object {
        var weakReferenceHandler: WeakReferenceHandler? = null
    }

    private val timeArray = arrayListOf("15s", "30s", "45s", "60s")

    override fun setupTopBarLayout() {
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.setupTopBarLayout", "顶部栏布局设置完成")
        binding.rootView.initImmersionBar(this, true, R.color.white)
    }

    override fun observeRequestState() {

    }

    override fun initViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        weakReferenceHandler = WeakReferenceHandler(this)
        binding.appVersion.text = com.autodark.BuildConfig.VERSION_NAME
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initOnCreate", "Fragment 创建，应用版本: ${com.autodark.BuildConfig.VERSION_NAME}")
    }

    override fun initEvent() {
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "initEvent 被调用")

        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮箱布局被点击")
        binding.emailLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮箱布局点击事件触发")
            AlertInputDialog.Builder()
                .setContext(requireContext())
                .setTitle("设置邮箱")
                .setHintMessage("请输入邮箱")
                .setNegativeButton("取消")
                .setPositiveButton("确定")
                .setOnDialogButtonClickListener(object :
                    AlertInputDialog.OnDialogButtonClickListener {
                    override fun onConfirmClick(value: String) {
                        if (!TextUtils.isEmpty(value)) {
                            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮箱设置为: $value")
                            SaveKeyValues.putValue(Constant.EMAIL_ADDRESS, value)
                            binding.emailTextView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮箱输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮箱设置取消")
                    }
                }).build().show()
        }

        binding.emailTitleLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮件标题布局被点击")
            AlertInputDialog.Builder()
                .setContext(requireContext())
                .setTitle("设置邮件标题")
                .setHintMessage("请输入邮件标题")
                .setNegativeButton("取消")
                .setPositiveButton("确定")
                .setOnDialogButtonClickListener(object :
                    AlertInputDialog.OnDialogButtonClickListener {
                    override fun onConfirmClick(value: String) {
                        if (!TextUtils.isEmpty(value)) {
                            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮件标题设置为: $value")
                            SaveKeyValues.putValue(Constant.EMAIL_TITLE, value)
                            binding.emailTitleView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮件标题输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "邮件标题设置取消")
                    }
                }).build().show()
        }

        binding.timeoutLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "超时布局被点击")
            BottomActionSheet.Builder()
                .setContext(requireContext())
                .setActionItemTitle(timeArray)
                .setItemTextColor(R.color.colorAppThemeLight.convertColor(requireContext()))
                .setOnActionSheetListener(object : BottomActionSheet.OnActionSheetListener {
                    override fun onActionItemClick(position: Int) {
                        val time = timeArray[position]
                        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "超时时间设置为: $time")
                        binding.timeoutTextView.text = time
                        SaveKeyValues.putValue(Constant.TIMEOUT, time)

                        val handler = FloatingWindowService.weakReferenceHandler ?: return
                        val message = handler.obtainMessage()
                        message.what = 2024071702
                        message.obj = time
                        handler.sendMessage(message)
                    }
                }).build().show()
        }

        binding.keyLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "打卡口令布局被点击")
            AlertInputDialog.Builder()
                .setContext(requireContext())
                .setTitle("设置打卡口令")
                .setHintMessage("请输入打卡口令，如：打卡")
                .setNegativeButton("取消")
                .setPositiveButton("确定")
                .setOnDialogButtonClickListener(object :
                    AlertInputDialog.OnDialogButtonClickListener {
                    override fun onConfirmClick(value: String) {
                        if (!TextUtils.isEmpty(value)) {
                            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "打卡口令设置为: $value")
                            SaveKeyValues.putValue(Constant.DING_DING_KEY, value)
                            binding.keyTextView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "打卡口令输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "打卡口令设置取消")
                    }
                }).build().show()
        }

        binding.floatSwitch.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "悬浮开关被点击，当前状态: ${binding.floatSwitch.isChecked}")
            val sdkInt = Build.VERSION.SDK_INT
            if (sdkInt >= Build.VERSION_CODES.M) {
                if (sdkInt >= Build.VERSION_CODES.O) {
                    LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "请求悬浮窗权限")
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivityForResult(intent, 101)
                } else {
                    LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "请求悬浮窗权限（6.0以下）")
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:${requireContext().packageName}")
                    startActivityForResult(intent, 101)
                }
            } else {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "手机系统版本太低，无法请求权限")
                "手机系统版本太低".show(requireContext())
            }
        }

        binding.noticeSwitch.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "通知开关被点击")
            startActivityForResult(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), 100)
        }

        binding.openTestLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "打开测试布局被点击")
            val packageManager = requireContext().packageManager
            val resolveIntent = Intent(Intent.ACTION_MAIN, null)
            resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            resolveIntent.setPackage(Constant.DING_DING)
            val apps = packageManager.queryIntentActivities(resolveIntent, 0)
            val iterator: Iterator<ResolveInfo> = apps.iterator()
            if (!iterator.hasNext()) {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "没有找到钉钉应用")
                return@setOnClickListener
            }
            val resolveInfo = iterator.next()
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.component = ComponentName(
                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name
            )
            startActivity(intent)
        }

        binding.turnoffLightSwitch.setOnCheckedChangeListener { _, isChecked ->
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "亮度开关状态改变: $isChecked")
            if (isChecked) {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "设置为最低亮度")
                requireActivity().window.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
            } else {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "恢复默认亮度")
                requireActivity().window.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }

        binding.backToHomeSwitch.setOnCheckedChangeListener { _, isChecked ->
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "返回主页开关状态改变: $isChecked")
            SaveKeyValues.putValue(Constant.BACK_TO_HOME, isChecked)
        }

        binding.notificationLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "通知记录布局被点击")
            requireContext().navigatePageTo<NoticeRecordActivity>()
        }

        binding.introduceLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.initEvent", "问答介绍布局被点击")
            requireContext().navigatePageTo<QuestionAndAnswerActivity>()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "onActivityResult 被调用，requestCode: $requestCode, resultCode: $resultCode")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "处理请求码 100")
            if (requireContext().notificationEnable()) {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "通知已启用，禁用通知监听服务")
                requireContext().packageManager.setComponentEnabledSetting(
                    ComponentName(requireContext(), NotificationMonitorService::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )

                Thread.sleep(1000)

                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "重新启用通知监听服务")
                requireContext().packageManager.setComponentEnabledSetting(
                    ComponentName(requireContext(), NotificationMonitorService::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "通知未启用，跳过处理")
            }
        } else if (requestCode == 101) {
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "处理请求码 101")
            binding.floatSwitch.isChecked = Settings.canDrawOverlays(requireContext())
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onActivityResult", "悬浮窗权限状态: ${binding.floatSwitch.isChecked}")
        }
    }


    override fun handleMessage(msg: Message): Boolean {
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.handleMessage", "handleMessage 被调用，what: ${msg.what}")

        when (msg.what) {
            2024090801 -> {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.handleMessage", "处理消息: 服务运行中")
                "通知监听服务运行中".show(requireContext())
                binding.noticeSwitch.isChecked = true
                binding.tipsView.visibility = View.GONE
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.handleMessage", "通知开关状态: ${binding.noticeSwitch.isChecked}, 提示视图隐藏")
            }
            2024090802 -> {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.handleMessage", "处理消息: 服务已关闭")
                "通知监听服务已关闭".show(requireContext())
                binding.noticeSwitch.isChecked = false
                binding.tipsView.visibility = View.VISIBLE
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.handleMessage", "通知开关状态: ${binding.noticeSwitch.isChecked}, 提示视图显示")
            }
            else -> {
                LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.handleMessage", "未知消息类型: ${msg.what}")
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "onResume 被调用")

        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
        binding.emailTextView.text = emailAddress
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "邮箱地址更新为: $emailAddress")

        val emailTitle = SaveKeyValues.getValue(Constant.EMAIL_TITLE, "打卡结果通知") as String
        binding.emailTitleView.text = emailTitle
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "邮件标题更新为: $emailTitle")

        val timeout = SaveKeyValues.getValue(Constant.TIMEOUT, "15s") as String
        binding.timeoutTextView.text = timeout
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "超时设置更新为: $timeout")

        val dingDingKey = SaveKeyValues.getValue(Constant.DING_DING_KEY, "打卡") as String
        binding.keyTextView.text = dingDingKey
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "打卡口令更新为: $dingDingKey")

        binding.floatSwitch.isChecked = Settings.canDrawOverlays(requireContext())
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "悬浮开关状态: ${binding.floatSwitch.isChecked}")

        val serviceIntent = Intent(requireContext(), FloatingWindowService::class.java)
        if (binding.floatSwitch.isChecked) {
            requireContext().startService(serviceIntent)
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "启动悬浮窗服务")
        } else {
            requireContext().stopService(serviceIntent)
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "停止悬浮窗服务")
        }

        val backToHome = SaveKeyValues.getValue(Constant.BACK_TO_HOME, false) as Boolean
        binding.backToHomeSwitch.isChecked = backToHome
        LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "返回主界面开关状态: $backToHome")

        if (requireContext().notificationEnable()) {
            binding.tipsView.text = "通知监听服务状态查询中，请稍后"
            binding.tipsView.setTextColor(R.color.purple_500.convertColor(requireContext()))
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "通知监听服务状态查询中")
        } else {
            binding.tipsView.text = "通知监听服务未开启，无法监听打卡通知"
            binding.tipsView.setTextColor(R.color.red.convertColor(requireContext()))
            LogUtils.log(Log.DEBUG,"AuToDark.SettingsFragment.onResume", "通知监听服务未开启")
        }
    }

}
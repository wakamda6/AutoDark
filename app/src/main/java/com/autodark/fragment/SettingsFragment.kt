package com.autodark.fragment

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.autodark.R
import com.autodark.databinding.FragmentSettingsBinding
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
import com.pengxh.kt.lite.widget.dialog.AlertInputDialog
import com.pengxh.kt.lite.widget.dialog.BottomActionSheet
import com.autodark.utils.LogUtils
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingsFragment : KotlinBaseFragment<FragmentSettingsBinding>() {

    private val kTag = "AuToDark.SettingsFragment"

    private val timeArray = arrayListOf("15s", "30s", "45s", "60s")

    override fun initViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        binding.appVersion.text = com.autodark.BuildConfig.VERSION_NAME
    }

    override fun initEvent() {
        binding.emailLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "邮箱布局点击事件触发")
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
                            LogUtils.log(Log.DEBUG,kTag, "邮箱设置为: $value")
                            SaveKeyValues.putValue(Constant.EMAIL_ADDRESS, value)
                            binding.emailTextView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,kTag, "邮箱输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,kTag, "邮箱设置取消")
                    }
                }).build().show()
        }

        binding.emailTitleLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "邮件标题布局被点击")
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
                            LogUtils.log(Log.DEBUG,kTag, "邮件标题设置为: $value")
                            SaveKeyValues.putValue(Constant.EMAIL_TITLE, value)
                            binding.emailTitleView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,kTag, "邮件标题输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,kTag, "邮件标题设置取消")
                    }
                }).build().show()
        }

        binding.timeoutLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "超时布局被点击")
            BottomActionSheet.Builder()
                .setContext(requireContext())
                .setActionItemTitle(timeArray)
                .setItemTextColor(R.color.colorAppThemeLight.convertColor(requireContext()))
                .setOnActionSheetListener(object : BottomActionSheet.OnActionSheetListener {
                    override fun onActionItemClick(position: Int) {
                        val time = timeArray[position]
                        LogUtils.log(Log.DEBUG,kTag, "超时时间设置为: $time")
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
            LogUtils.log(Log.DEBUG,kTag, "打卡口令布局被点击")
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
                            LogUtils.log(Log.DEBUG,kTag, "打卡口令设置为: $value")
                            SaveKeyValues.putValue(Constant.DING_DING_KEY, value)
                            binding.keyTextView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,kTag, "打卡口令输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,kTag, "打卡口令设置取消")
                    }
                }).build().show()
        }

        binding.floatSwitch.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "悬浮开关被点击，当前状态: ${binding.floatSwitch.isChecked}")
            val sdkInt = Build.VERSION.SDK_INT
            if (sdkInt >= Build.VERSION_CODES.M) {
                if (sdkInt >= Build.VERSION_CODES.O) {
                    LogUtils.log(Log.DEBUG,kTag, "请求悬浮窗权限")
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivityForResult(intent, 101)
                } else {
                    LogUtils.log(Log.DEBUG,kTag, "请求悬浮窗权限（6.0以下）")
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:${requireContext().packageName}")
                    startActivityForResult(intent, 101)
                }
            } else {
                LogUtils.log(Log.DEBUG,kTag, "手机系统版本太低，无法请求权限")
                "手机系统版本太低".show(requireContext())
            }
        }

        binding.noticeSwitch.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "通知开关被点击")
            startActivityForResult(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), 100)
        }

        binding.openTestLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "打开测试布局被点击")
            val packageManager = requireContext().packageManager
            val resolveIntent = Intent(Intent.ACTION_MAIN, null)
            resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            resolveIntent.setPackage(Constant.DING_DING)
            val apps = packageManager.queryIntentActivities(resolveIntent, 0)
            val iterator: Iterator<ResolveInfo> = apps.iterator()
            if (!iterator.hasNext()) {
                LogUtils.log(Log.DEBUG,kTag, "没有找到钉钉应用")
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
            LogUtils.log(Log.DEBUG,kTag, "亮度开关状态改变: $isChecked")
            if (isChecked) {
                LogUtils.log(Log.DEBUG,kTag, "设置为最低亮度")
                requireActivity().window.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
            } else {
                LogUtils.log(Log.DEBUG,kTag, "恢复默认亮度")
                requireActivity().window.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }

        binding.backToHomeSwitch.setOnCheckedChangeListener { _, isChecked ->
            LogUtils.log(Log.DEBUG,kTag, "返回主页开关状态改变: $isChecked")
            SaveKeyValues.putValue(Constant.BACK_TO_HOME, isChecked)
        }

        binding.notificationLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "通知记录布局被点击")
            requireContext().navigatePageTo<NoticeRecordActivity>()
        }

        binding.introduceLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "问答介绍布局被点击")
            requireContext().navigatePageTo<QuestionAndAnswerActivity>()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (requireContext().notificationEnable()) {
                turnOnNotificationMonitorService()
                binding.noticeSwitch.isChecked = true
                binding.tipsView.visibility = View.GONE
                LogUtils.log(Log.DEBUG,kTag, "启用通知监听服务")
                LogUtils.log(Log.DEBUG,kTag, "通知监听按钮开启")
            }else{
                binding.noticeSwitch.isChecked = false
                binding.tipsView.visibility = View.VISIBLE
                binding.tipsView.setTextColor(R.color.red.convertColor(requireContext()))
                LogUtils.log(Log.DEBUG,kTag, "通知监听服务未授权")
                LogUtils.log(Log.DEBUG,kTag, "通知监听按钮关闭")
            }
        } else if (requestCode == 101) {
            binding.floatSwitch.isChecked = Settings.canDrawOverlays(requireContext())
        }
    }

    private fun turnOnNotificationMonitorService() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 获取 ActivityManager 实例
            val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)

            // 检查 NotificationMonitorService 是否在运行
            val isServiceRunning = runningServices.any { service ->
                service.service.className == NotificationMonitorService::class.java.name
            }

            // 如果服务没有在运行，才启用该服务
            if (!isServiceRunning) {
                withContext(Dispatchers.Main) {
                    requireContext().packageManager.setComponentEnabledSetting(
                        ComponentName(requireContext(), NotificationMonitorService::class.java),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }else{
                LogUtils.log(Log.DEBUG,kTag, "通知监听服务已经运行中")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
        binding.emailTextView.text = emailAddress
        LogUtils.log(Log.DEBUG,kTag, "邮箱地址更新为: $emailAddress")

        val emailTitle = SaveKeyValues.getValue(Constant.EMAIL_TITLE, "打卡结果通知") as String
        binding.emailTitleView.text = emailTitle
        LogUtils.log(Log.DEBUG,kTag, "邮件标题更新为: $emailTitle")

        val timeout = SaveKeyValues.getValue(Constant.TIMEOUT, "15s") as String
        binding.timeoutTextView.text = timeout
        LogUtils.log(Log.DEBUG,kTag, "超时设置更新为: $timeout")

        val dingDingKey = SaveKeyValues.getValue(Constant.DING_DING_KEY, "打卡") as String
        binding.keyTextView.text = dingDingKey
        LogUtils.log(Log.DEBUG,kTag, "打卡口令更新为: $dingDingKey")

        binding.floatSwitch.isChecked = Settings.canDrawOverlays(requireContext())
        LogUtils.log(Log.DEBUG,kTag, "悬浮开关状态: ${binding.floatSwitch.isChecked}")

        val serviceIntent = Intent(requireContext(), FloatingWindowService::class.java)
        if (binding.floatSwitch.isChecked) {
            requireContext().startService(serviceIntent)
            LogUtils.log(Log.DEBUG,kTag, "启动悬浮窗服务")
        } else {
            requireContext().stopService(serviceIntent)
            LogUtils.log(Log.DEBUG,kTag, "停止悬浮窗服务")
        }

        val backToHome = SaveKeyValues.getValue(Constant.BACK_TO_HOME, false) as Boolean
        binding.backToHomeSwitch.isChecked = backToHome
        LogUtils.log(Log.DEBUG,kTag, "返回主界面开关状态: $backToHome")

        if (requireContext().notificationEnable()) {
            binding.noticeSwitch.isChecked = true
            binding.tipsView.visibility = View.GONE
            LogUtils.log(Log.DEBUG,kTag, "通知监听服务已开启")
        } else {
            binding.noticeSwitch.isChecked = false
            binding.tipsView.visibility = View.VISIBLE
            binding.tipsView.setTextColor(R.color.red.convertColor(requireContext()))
            LogUtils.log(Log.DEBUG,kTag, "通知监听服务未授权")
        }
    }

    override fun setupTopBarLayout() {

    }

    override fun observeRequestState() {

    }

}
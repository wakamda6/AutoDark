package com.autodark.fragment

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import com.autodark.R
import com.autodark.databinding.FragmentSettingsBinding
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
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.autodark.BaseApplication
import com.autodark.extensions.*
import com.autodark.utils.PermissionManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.pengxh.kt.lite.extensions.show
import android.provider.Settings

class SettingsFragment : KotlinBaseFragment<FragmentSettingsBinding>() {

    private val kTag = "SettingsFragment"

    private var id:String = ""
    private var time:String = ""
    private var screenCoverView: View? = null
    private val timeArray = arrayListOf("15s", "30s", "45s", "60s")

    override fun setupTopBarLayout() {
        binding.rootView.initImmersionBar(this, true, R.color.white)
    }

    override fun observeRequestState() {

    }

    //设置证书时长
    @SuppressLint("SetTextI18n")
    fun setIdText(id: String, time: String) {
        this.id = id
        this.time = time
        if (isAdded) {
            binding.tvId.text = "本机ID：$id"
            binding.tvTimeout.text = time
        }
    }

    override fun initViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        binding.appVersion.text = com.autodark.BuildConfig.VERSION_NAME
        LogUtils.log(Log.DEBUG,kTag,"Fragment 创建，应用版本: ${com.autodark.BuildConfig.VERSION_NAME}")
    }

    override fun initEvent() {
        binding.emailLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag,"接收邮箱布局点击事件触发")
            AlertInputDialog.Builder()
                .setContext(requireContext())
                .setTitle("设置接收邮箱")
                .setHintMessage("请输入接收邮箱")
                .setNegativeButton("取消")
                .setPositiveButton("确定")
                .setOnDialogButtonClickListener(object :
                    AlertInputDialog.OnDialogButtonClickListener {
                    override fun onConfirmClick(value: String) {
                        if (!TextUtils.isEmpty(value)) {
                            LogUtils.log(Log.DEBUG,kTag,"接收邮箱设置为: $value")
                            SaveKeyValues.putValue(Constant.EMAIL_ADDRESS, value)
                            binding.emailTextView.text = value
                        } else {
                            LogUtils.log(Log.DEBUG,kTag,"接收邮箱输入为空")
                            "什么都还没输入呢！".show(requireContext())
                        }
                    }

                    override fun onCancelClick() {
                        LogUtils.log(Log.DEBUG,kTag,"接收邮箱设置取消")
                    }
                }).build().show()
        }

        binding.timeoutLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag,"超时布局被点击")
            BottomActionSheet.Builder()
                .setContext(requireContext())
                .setActionItemTitle(timeArray)
                .setItemTextColor(R.color.colorAppThemeLight.convertColor(requireContext()))
                .setOnActionSheetListener(object : BottomActionSheet.OnActionSheetListener {
                    override fun onActionItemClick(position: Int) {
                        val time = timeArray[position]
                        LogUtils.log(Log.DEBUG,kTag,"超时时间设置为: $time")
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
                // 显示黑色遮罩 + 禁用触摸
                showScreenCover()
            }
        }

        binding.notificationLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "通知记录布局被点击")
            requireContext().navigatePageTo<NoticeRecordActivity>()
        }

        binding.introduceLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "问答介绍布局被点击")
            requireContext().navigatePageTo<QuestionAndAnswerActivity>()
        }

        binding.idCodeLayout.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "二维码被点击")
            // 要显示为二维码的字符串
            val app = requireActivity().application as BaseApplication
            val stringToEncode = app.androidId

            // 生成二维码
            val bitmap = generateQRCode(stringToEncode)

            // 设置二维码到 ImageView
            showQRCodeDialog(bitmap)
        }
    }

    //防误触黑屏
    private fun showScreenCover() {
        if (screenCoverView != null) return

        val textView = TextView(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
            text = "长按屏幕解锁"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            isClickable = true // 阻止触摸事件传递
            isFocusable = true

            setOnLongClickListener {
                removeScreenCover()
                true
            }
        }

        screenCoverView = textView

        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        (requireActivity().window.decorView as ViewGroup).addView(screenCoverView, params)
    }

    //取消防误触
    private fun removeScreenCover() {
        screenCoverView?.let {
            (requireActivity().window.decorView as ViewGroup).removeView(it)
            screenCoverView = null
        }

        // 恢复亮度
        requireActivity().window.attributes = requireActivity().window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        // Switch 状态归位
        binding.turnoffLightSwitch.isChecked = false
    }

    private fun showQRCodeDialog(bitmap: Bitmap?) {
        // 如果二维码生成成功
        if (bitmap != null) {
            // 使用 AlertDialog 创建弹窗
            val builder = AlertDialog.Builder(requireContext())

            // 获取布局并设置二维码图片
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_qrcode, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.qrCodeImageView)
            imageView.setImageBitmap(bitmap)

            val dialog = builder.setView(dialogView)
                .setPositiveButton("确定") { dialog, _ ->
                    dialog.dismiss()  // 关闭弹窗
                }
                .setCancelable(true) // 点击外部区域也可以关闭对话框
                .create()

            // 设置窗口居中显示
            dialog.window?.setGravity(Gravity.CENTER)  // 设置弹窗在屏幕正中心
            dialog.show()
        }
    }

    private fun generateQRCode(data: String): Bitmap? {
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix: BitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 400, 400) // 设置二维码的尺寸
            val barcodeEncoder = BarcodeEncoder()
            return barcodeEncoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    //检查悬浮窗服务和通知监听服务
    private fun ensureServicesAreRunning() {
        val context = requireContext()
        val serviceIntent = Intent(context, FloatingWindowService::class.java)

        //悬浮框
        if (!requireContext().isServiceRunning(FloatingWindowService::class.java)) {
            context.startService(serviceIntent)
            LogUtils.log(Log.DEBUG, kTag, "启动悬浮窗服务")
        }

        if (!NotificationMonitorService.isConnected || !isNotificationServiceReallyEnabled(requireContext())) {
            LogUtils.log(Log.WARN, kTag, "通知监听服务尚未连接，尝试重启")
            val intent = Intent(requireContext(), NotificationMonitorService::class.java)
            requireContext().startService(intent)
        }else {
            LogUtils.log(Log.DEBUG, kTag, "通知监听服务正常")
        }
    }

    private fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    //通知监听服务判断
    private fun isNotificationServiceReallyEnabled(context: Context): Boolean {
        val cn = ComponentName(context, NotificationMonitorService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(cn.flattenToString()) == true
    }

    override fun onResume() {
        super.onResume()
        //权限检查
        if (PermissionManager.allPermissionsGranted(requireActivity())) {
            ensureServicesAreRunning()
        }else {
            // 3. 触发弹窗申请权限
            PermissionManager.checkAllPermissions(requireActivity()) {
                // 授权后再次检查服务
                ensureServicesAreRunning()
            }
        }

        val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
        binding.emailTextView.text = emailAddress
        LogUtils.log(Log.DEBUG,kTag,"邮箱地址更新为: $emailAddress")

        val timeout = SaveKeyValues.getValue(Constant.TIMEOUT, "15s") as String
        binding.timeoutTextView.text = timeout
        LogUtils.log(Log.DEBUG,kTag,"超时设置更新为: $timeout")
    }

}
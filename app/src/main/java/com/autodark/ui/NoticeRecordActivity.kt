package com.autodark.ui

import com.autodark.utils.LogUtils
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import com.autodark.R
import com.autodark.databinding.ActivityNoticeBinding
import com.autodark.extensions.initImmersionBar
import com.pengxh.kt.lite.adapter.NormalRecyclerAdapter
import com.pengxh.kt.lite.adapter.ViewHolder
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.divider.RecyclerViewItemDivider
import com.pengxh.kt.lite.utils.ActivityStackManager
import com.pengxh.kt.lite.utils.WeakReferenceHandler
import com.pengxh.kt.lite.widget.TitleBarView
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog

class NoticeRecordActivity : KotlinBaseActivity<ActivityNoticeBinding>(), Handler.Callback {

    private val kTag = "NoticeRecordActivity"

    private val notificationBeanDao by lazy { com.autodark.BaseApplication.get().daoSession.notificationBeanDao }
    private lateinit var weakReferenceHandler: WeakReferenceHandler
    private lateinit var noticeAdapter: NormalRecyclerAdapter<com.autodark.bean.NotificationBean>
    private var dataBeans: MutableList<com.autodark.bean.NotificationBean> = ArrayList()
    private var isRefresh = false
    private var isLoadMore = false
    private var offset = 0 // 本地数据库分页从0开始

    override fun initViewBinding(): ActivityNoticeBinding {
        return ActivityNoticeBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {

        // 初始化沉浸式状态栏
        binding.rootView.initImmersionBar(this, true, R.color.white)
        LogUtils.log(Log.DEBUG,kTag, "沉浸式状态栏已初始化，背景颜色设置为白色")

        // 设置标题栏点击事件
        binding.titleView.setOnClickListener(object : TitleBarView.OnClickListener {
            override fun onLeftClick() {
                LogUtils.log(Log.DEBUG,kTag, "左侧按钮点击，结束当前活动")
                finish()
            }

            override fun onRightClick() {
                LogUtils.log(Log.DEBUG,kTag, "右侧按钮点击，执行相应操作")
                AlertMessageDialog.Builder()
                    .setContext(this@NoticeRecordActivity)
                    .setTitle("温馨提示")
                    .setMessage("此操作将会清空所有通知记录，且不可恢复")
                    .setPositiveButton("知道了")
                    .setOnDialogButtonClickListener(object :
                        AlertMessageDialog.OnDialogButtonClickListener {
                        override fun onConfirmClick() {
                            notificationBeanDao.deleteAll()
                            binding.emptyView.visibility = View.VISIBLE
                            binding.notificationView.visibility = View.GONE
                        }
                    }).build().show()
            }
        })
    }


    override fun initOnCreate(savedInstanceState: Bundle?) {
        LogUtils.log(Log.DEBUG,kTag, "初始化活动")

        ActivityStackManager.addActivity(this)

        weakReferenceHandler = WeakReferenceHandler(this)
        LogUtils.log(Log.DEBUG,kTag, "创建 WeakReferenceHandler")

        dataBeans = queryNotificationRecord()
        LogUtils.log(Log.DEBUG,kTag, "查询通知记录，数量: ${dataBeans.size}")

        weakReferenceHandler.sendEmptyMessage(2022061901)
        LogUtils.log(Log.DEBUG,kTag, "发送消息以更新 UI")
    }


    override fun initEvent() {
        LogUtils.log(Log.DEBUG,kTag, "初始化事件监听")

        binding.refreshLayout.setOnRefreshListener { refreshLayout ->
            LogUtils.log(Log.DEBUG,kTag, "开始刷新数据")
            isRefresh = true
            object : CountDownTimer(1000, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    LogUtils.log(Log.DEBUG,kTag, "刷新中... 剩余时间: $millisUntilFinished")
                }

                override fun onFinish() {
                    LogUtils.log(Log.DEBUG,kTag, "刷新完成")
                    isRefresh = false
                    dataBeans.clear()
                    offset = 0
                    dataBeans = queryNotificationRecord()
                    refreshLayout.finishRefresh()
                    weakReferenceHandler.sendEmptyMessage(2022061901)
                    LogUtils.log(Log.DEBUG,kTag, "数据更新完成，更新 UI")
                }
            }.start()
        }

        binding.refreshLayout.setOnLoadMoreListener { refreshLayout ->
            LogUtils.log(Log.DEBUG,kTag, "开始加载更多数据")
            isLoadMore = true
            object : CountDownTimer(1000, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    LogUtils.log(Log.DEBUG,kTag, "加载中... 剩余时间: $millisUntilFinished")
                }

                override fun onFinish() {
                    LogUtils.log(Log.DEBUG,kTag, "加载更多完成")
                    isLoadMore = false
                    offset++
                    dataBeans.addAll(queryNotificationRecord())
                    refreshLayout.finishLoadMore()
                    weakReferenceHandler.sendEmptyMessage(2022061901)
                    LogUtils.log(Log.DEBUG,kTag, "数据加载完成，更新 UI")
                }
            }.start()
        }
    }


    override fun observeRequestState() {

    }

    override fun handleMessage(msg: Message): Boolean {
        LogUtils.log(Log.DEBUG,kTag, "处理消息: ${msg.what}")

        if (msg.what == 2022061901) {
            if (isRefresh || isLoadMore) {
                LogUtils.log(Log.DEBUG,kTag, "刷新或加载更多数据，通知适配器更新")
                noticeAdapter.notifyDataSetChanged()
            } else { // 首次加载数据
                LogUtils.log(Log.DEBUG,kTag, "首次加载数据")
                if (dataBeans.size == 0) {
                    LogUtils.log(Log.DEBUG,kTag, "数据为空，显示空视图")
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    LogUtils.log(Log.DEBUG,kTag, "数据存在，隐藏空视图")
                    binding.emptyView.visibility = View.GONE

                    noticeAdapter = object : NormalRecyclerAdapter<com.autodark.bean.NotificationBean>(
                        R.layout.item_notice_rv_l, dataBeans
                    ) {
                        override fun convertView(
                            viewHolder: ViewHolder, position: Int, item: com.autodark.bean.NotificationBean
                        ) {
                            viewHolder.setText(R.id.titleView, "标题：${item.notificationTitle}")
                                .setText(R.id.packageNameView, "包名：${item.packageName}")
                                .setText(R.id.messageView, "内容：${item.notificationMsg}")
                                .setText(R.id.postTimeView, item.postTime)
                        }
                    }

                    binding.notificationView.addItemDecoration(
                        RecyclerViewItemDivider(1, Color.LTGRAY)
                    )
                    binding.notificationView.adapter = noticeAdapter
                    LogUtils.log(Log.DEBUG,kTag, "适配器设置完成，数据数量: ${dataBeans.size}")
                }
            }
        }
        return true
    }

    private fun queryNotificationRecord(): MutableList<com.autodark.bean.NotificationBean> {
        LogUtils.log(Log.DEBUG,kTag, "查询通知记录，当前偏移量: $offset")

        val records = notificationBeanDao.queryBuilder()
            .orderDesc(com.autodark.greendao.NotificationBeanDao.Properties.PostTime)
            .offset(offset * 15)
            .limit(15)
            .list()

        LogUtils.log(Log.DEBUG,kTag, "查询到的记录数量: ${records.size}")
        return records
    }

}
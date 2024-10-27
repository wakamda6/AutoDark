package com.pengxh.autodingding.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import com.pengxh.autodingding.BaseApplication
import com.pengxh.autodingding.R
import com.pengxh.autodingding.bean.NotificationBean
import com.pengxh.autodingding.databinding.ActivityNoticeBinding
import com.pengxh.autodingding.extensions.initImmersionBar
import com.pengxh.autodingding.greendao.NotificationBeanDao
import com.pengxh.kt.lite.adapter.NormalRecyclerAdapter
import com.pengxh.kt.lite.adapter.ViewHolder
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.divider.RecyclerViewItemDivider
import com.pengxh.kt.lite.utils.ActivityStackManager
import com.pengxh.kt.lite.utils.WeakReferenceHandler
import com.pengxh.kt.lite.widget.TitleBarView

class NoticeRecordActivity : KotlinBaseActivity<ActivityNoticeBinding>(), Handler.Callback {

    private val notificationBeanDao by lazy { BaseApplication.get().daoSession.notificationBeanDao }
    private lateinit var weakReferenceHandler: WeakReferenceHandler
    private lateinit var noticeAdapter: NormalRecyclerAdapter<NotificationBean>
    private var dataBeans: MutableList<NotificationBean> = ArrayList()
    private var isRefresh = false
    private var isLoadMore = false
    private var offset = 0 // 本地数据库分页从0开始

    override fun initViewBinding(): ActivityNoticeBinding {
        return ActivityNoticeBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        Log.d("AuToDark.setupTopBarLayout", "设置顶部导航栏布局")

        // 初始化沉浸式状态栏
        binding.rootView.initImmersionBar(this, true, R.color.white)
        Log.d("AuToDark.setupTopBarLayout", "沉浸式状态栏已初始化，背景颜色设置为白色")

        // 设置标题栏点击事件
        binding.titleView.setOnClickListener(object : TitleBarView.OnClickListener {
            override fun onLeftClick() {
                Log.d("AuToDark.setupTopBarLayout", "左侧按钮点击，结束当前活动")
                finish()
            }

            override fun onRightClick() {
                Log.d("AuToDark.setupTopBarLayout", "右侧按钮点击，执行相应操作")
                // 此处可以添加右侧按钮的操作
            }
        })
    }


    override fun initOnCreate(savedInstanceState: Bundle?) {
        Log.d("AuToDark.initOnCreate", "初始化活动")

        ActivityStackManager.addActivity(this)

        weakReferenceHandler = WeakReferenceHandler(this)
        Log.d("AuToDark.initOnCreate", "创建 WeakReferenceHandler")

        dataBeans = queryNotificationRecord()
        Log.d("AuToDark.initOnCreate", "查询通知记录，数量: ${dataBeans.size}")

        weakReferenceHandler.sendEmptyMessage(2022061901)
        Log.d("AuToDark.initOnCreate", "发送消息以更新 UI")
    }


    override fun initEvent() {
        Log.d("AuToDark.initEvent", "初始化事件监听")

        binding.refreshLayout.setOnRefreshListener { refreshLayout ->
            Log.d("AuToDark.initEvent", "开始刷新数据")
            isRefresh = true
            object : CountDownTimer(1000, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    Log.d("AuToDark.initEvent", "刷新中... 剩余时间: $millisUntilFinished")
                }

                override fun onFinish() {
                    Log.d("AuToDark.initEvent", "刷新完成")
                    isRefresh = false
                    dataBeans.clear()
                    offset = 0
                    dataBeans = queryNotificationRecord()
                    refreshLayout.finishRefresh()
                    weakReferenceHandler.sendEmptyMessage(2022061901)
                    Log.d("AuToDark.initEvent", "数据更新完成，更新 UI")
                }
            }.start()
        }

        binding.refreshLayout.setOnLoadMoreListener { refreshLayout ->
            Log.d("AuToDark.initEvent", "开始加载更多数据")
            isLoadMore = true
            object : CountDownTimer(1000, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    Log.d("AuToDark.initEvent", "加载中... 剩余时间: $millisUntilFinished")
                }

                override fun onFinish() {
                    Log.d("AuToDark.initEvent", "加载更多完成")
                    isLoadMore = false
                    offset++
                    dataBeans.addAll(queryNotificationRecord())
                    refreshLayout.finishLoadMore()
                    weakReferenceHandler.sendEmptyMessage(2022061901)
                    Log.d("AuToDark.initEvent", "数据加载完成，更新 UI")
                }
            }.start()
        }
    }


    override fun observeRequestState() {

    }

    override fun handleMessage(msg: Message): Boolean {
        Log.d("AuToDark.handleMessage", "处理消息: ${msg.what}")

        if (msg.what == 2022061901) {
            if (isRefresh || isLoadMore) {
                Log.d("AuToDark.handleMessage", "刷新或加载更多数据，通知适配器更新")
                noticeAdapter.notifyDataSetChanged()
            } else { // 首次加载数据
                Log.d("AuToDark.handleMessage", "首次加载数据")
                if (dataBeans.size == 0) {
                    Log.d("AuToDark.handleMessage", "数据为空，显示空视图")
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    Log.d("AuToDark.handleMessage", "数据存在，隐藏空视图")
                    binding.emptyView.visibility = View.GONE

                    noticeAdapter = object : NormalRecyclerAdapter<NotificationBean>(
                        R.layout.item_notice_rv_l, dataBeans
                    ) {
                        override fun convertView(
                            viewHolder: ViewHolder, position: Int, item: NotificationBean
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
                    Log.d("AuToDark.handleMessage", "适配器设置完成，数据数量: ${dataBeans.size}")
                }
            }
        }
        return true
    }

    private fun queryNotificationRecord(): MutableList<NotificationBean> {
        Log.d("AuToDark.queryNotificationRecord", "查询通知记录，当前偏移量: $offset")

        val records = notificationBeanDao.queryBuilder()
            .orderDesc(NotificationBeanDao.Properties.PostTime)
            .offset(offset * 15)
            .limit(15)
            .list()

        Log.d("AuToDark.queryNotificationRecord", "查询到的记录数量: ${records.size}")
        return records
    }

}
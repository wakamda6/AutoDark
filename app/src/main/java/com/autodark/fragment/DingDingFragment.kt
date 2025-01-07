package com.autodark.fragment

import com.autodark.utils.LogUtils
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.autodark.R
import com.autodark.databinding.FragmentDingdingBinding
import com.autodark.extensions.convertToWeek
import com.autodark.extensions.initImmersionBar
import com.autodark.extensions.openApplication
import com.autodark.extensions.showDatePicker
import com.autodark.extensions.showDateTimePicker
import com.autodark.utils.Constant
import com.autodark.utils.OnDateSelectedCallback
import com.pengxh.kt.lite.base.KotlinBaseFragment
import com.pengxh.kt.lite.divider.RecyclerViewItemOffsets
import com.pengxh.kt.lite.extensions.appendZero
import com.pengxh.kt.lite.extensions.dp2px
import com.pengxh.kt.lite.widget.dialog.AlertControlDialog
import java.util.UUID
import android.util.Log
import com.autodark.BaseApplication
import com.autodark.adapter.DateTimeAdapter
import com.autodark.bean.DateTimeBean

class DingDingFragment : KotlinBaseFragment<FragmentDingdingBinding>() {

    private val kTag = "DingDingFragment"
    private val dateTimeBeanDao by lazy { BaseApplication.get().daoSession.dateTimeBeanDao }
    private val marginOffset by lazy { 10.dp2px(requireContext()) }
    private lateinit var dateTimeAdapter: DateTimeAdapter
    private var dataBeans: MutableList<DateTimeBean> = ArrayList()
    private var clickedPosition = 0

    override fun setupTopBarLayout() {
        // 初始化状态栏，设置沉浸式效果
        LogUtils.log(Log.DEBUG,kTag, "setupTopBarLayout: 初始化状态栏")
        binding.rootView.initImmersionBar(this, true, R.color.white)
    }

    override fun observeRequestState() {
        // 观察请求状态，可用于更新UI或处理数据
        LogUtils.log(Log.DEBUG,kTag, "observeRequestState: 观察请求状态")
    }

    override fun initViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentDingdingBinding {
        // 初始化视图绑定
        LogUtils.log(Log.DEBUG,kTag, "initViewBinding: 初始化视图绑定")
        return FragmentDingdingBinding.inflate(inflater, container, false)
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        // 在Fragment创建时获取自动钉钉任务
        LogUtils.log(Log.DEBUG,kTag, "initOnCreate: 获取自动钉钉任务")
        getAutoDingDingTasks(false)
    }

    private fun getAutoDingDingTasks(isRefresh: Boolean) {
        // 查询数据库中的打卡任务，并更新UI
        LogUtils.log(Log.DEBUG,kTag, "getAutoDingDingTasks: isRefresh = $isRefresh")
        val queryResult = dateTimeBeanDao.queryBuilder().orderDesc(
            com.autodark.greendao.DateTimeBeanDao.Properties.Date
        ).list()

        // 根据任务数量显示空视图或隐藏空视图
        if (queryResult.isEmpty()) {
            LogUtils.log(Log.DEBUG,kTag, "getAutoDingDingTasks: 任务列表为空")
            binding.emptyView.visibility = View.VISIBLE
        } else {
            LogUtils.log(Log.DEBUG,kTag, "getAutoDingDingTasks: 任务数量 = ${queryResult.size}")
            binding.emptyView.visibility = View.GONE
        }

        if (isRefresh) {
            // 刷新适配器数据
            LogUtils.log(Log.DEBUG,kTag, "getAutoDingDingTasks: 刷新适配器数据")
            dateTimeAdapter.setRefreshData(queryResult)
        } else {
            // 初始化适配器并设置RecyclerView
            dataBeans = queryResult
            dateTimeAdapter = DateTimeAdapter(requireContext(), dataBeans)
            binding.recyclerView.adapter = dateTimeAdapter
            binding.recyclerView.addItemDecoration(
                RecyclerViewItemOffsets(
                    marginOffset, marginOffset shr 1, marginOffset, marginOffset shr 1
                )
            )
            // 设置项点击监听
            dateTimeAdapter.setOnItemClickListener(object : DateTimeAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    // 处理打卡任务点击事件
                    LogUtils.log(Log.DEBUG,kTag, "onItemClick: 修改打卡任务，position = $position")
                    AlertControlDialog.Builder()
                        .setContext(requireContext())
                        .setTitle("修改打卡任务")
                        .setMessage("是否需要调整打卡时间？")
                        .setNegativeButton("取消")
                        .setPositiveButton("确定")
                        .setOnDialogButtonClickListener(object :
                            AlertControlDialog.OnDialogButtonClickListener {
                            override fun onConfirmClick() {
                                // 确认修改时间
                                val dateTimeBean = dataBeans[position]
                                requireActivity().showDateTimePicker(
                                    dateTimeBean, object : OnDateSelectedCallback {
                                        override fun onTimePicked(vararg args: String) {
                                            dateTimeBean.date = "${args[0]}-${args[1]}-${args[2]}"
                                            dateTimeBean.time =
                                                "${args[3]}:${args[4]}:${randomSeconds()}"
                                            dateTimeBean.weekDay = dateTimeBean.date.convertToWeek()

                                            dateTimeBeanDao.update(dateTimeBean)
                                            LogUtils.log(Log.DEBUG,kTag, "onTimePicked: 更新打卡任务成功，任务 = $dateTimeBean")
                                            // 刷新列表
                                            getAutoDingDingTasks(true)
                                        }
                                    })
                            }

                            override fun onCancelClick() {
                                // 处理取消事件，修改日期
                                LogUtils.log(Log.DEBUG,kTag, "onCancelClick: 取消修改打卡任务时间")
                                val dateTimeBean = dataBeans[position]
                                requireActivity().showDatePicker(
                                    dateTimeBean, object : OnDateSelectedCallback {
                                        override fun onTimePicked(vararg args: String) {
                                            dateTimeBean.date = "${args[0]}-${args[1]}-${args[2]}"
                                            dateTimeBean.weekDay = dateTimeBean.date.convertToWeek()

                                            dateTimeBeanDao.update(dateTimeBean)
                                            LogUtils.log(Log.DEBUG,kTag, "onTimePicked: 更新打卡任务日期成功，任务 = $dateTimeBean")
                                            // 刷新列表
                                            getAutoDingDingTasks(true)
                                        }
                                    })
                            }
                        }).build().show()
                }

                override fun onItemLongClick(position: Int) {
                    // 长按事件，标记被点击的item位置
                    clickedPosition = position
                    LogUtils.log(Log.DEBUG,kTag, "onItemLongClick: 删除任务，position = $position")
                    AlertControlDialog.Builder().setContext(requireContext()).setTitle("删除提示")
                        .setMessage("确定要删除这个任务吗").setNegativeButton("取消")
                        .setPositiveButton("确定").setOnDialogButtonClickListener(object :
                            AlertControlDialog.OnDialogButtonClickListener {
                            override fun onConfirmClick() {
                                // 删除选中的任务
                                deleteTask(dataBeans[position])
                            }

                            override fun onCancelClick() {
                                // 取消删除
                                LogUtils.log(Log.DEBUG,kTag, "onCancelClick: 取消删除任务")
                            }
                        }).build().show()
                }

                override fun onCountDownFinish() {
                    // 倒计时结束，打开钉钉应用
                    LogUtils.log(Log.DEBUG,kTag, "onCountDownFinish: 倒计时结束，打开钉钉应用")
                    requireContext().openApplication(Constant.DING_DING)
                }
            })
        }
    }

    private fun deleteTask(bean: com.autodark.bean.DateTimeBean) {
        // 删除指定任务并更新UI
        LogUtils.log(Log.DEBUG,kTag, "deleteTask: 删除任务 = $bean")
        dateTimeBeanDao.delete(bean)
        dataBeans.removeAt(clickedPosition)
        dateTimeAdapter.notifyItemRemoved(clickedPosition)
        dateTimeAdapter.notifyItemRangeChanged(
            clickedPosition, dataBeans.size - clickedPosition
        )
        dateTimeAdapter.stopCountDownTimer(bean)
        // 根据任务数量显示空视图或隐藏空视图
        if (dataBeans.isEmpty()) {
            LogUtils.log(Log.DEBUG,kTag, "deleteTask: 任务列表已为空")
            binding.emptyView.visibility = View.VISIBLE
        } else {
            LogUtils.log(Log.DEBUG,kTag, "deleteTask: 任务列表不为空，当前任务数量 = ${dataBeans.size}")
            binding.emptyView.visibility = View.GONE
        }
    }

    override fun initEvent() {
        // 初始化添加计时器按钮的点击事件
        binding.addTimerButton.setOnClickListener {
            LogUtils.log(Log.DEBUG,kTag, "initEvent: 添加计时器按钮被点击")
            requireActivity().showDateTimePicker(null, object : OnDateSelectedCallback {
                override fun onTimePicked(vararg args: String) {
                    // 创建新打卡任务
                    val bean = com.autodark.bean.DateTimeBean()
                    bean.uuid = UUID.randomUUID().toString()
                    bean.date = "${args[0]}-${args[1]}-${args[2]}"
                    bean.time = "${args[3]}:${args[4]}:${randomSeconds()}"
                    bean.weekDay = bean.date.convertToWeek()

                    dateTimeBeanDao.insert(bean)
                    LogUtils.log(Log.DEBUG,kTag, "onTimePicked: 新任务已创建，任务 = $bean")
                    // 刷新列表
                    getAutoDingDingTasks(true)
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 停止所有计时器
        LogUtils.log(Log.DEBUG,kTag, "onDestroyView: 停止所有计时器")
        dataBeans.forEach {
            dateTimeAdapter.stopCountDownTimer(it)
        }
    }

    /**
     * 产生随机秒数
     * */
    private fun randomSeconds(): String {
        val seconds = (0 until 60).random().appendZero()
        LogUtils.log(Log.DEBUG,kTag, "randomSeconds: 产生随机秒数 = $seconds")
        return seconds
    }
}

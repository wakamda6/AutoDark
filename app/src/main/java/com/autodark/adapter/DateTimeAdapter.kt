package com.autodark.adapter

import com.autodark.utils.LogUtils
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.autodark.R
import com.autodark.extensions.diffCurrentMillis
import com.autodark.extensions.isEarlierThenCurrent

class DateTimeAdapter(context: Context, private val dataBeans: MutableList<com.autodark.bean.DateTimeBean>) :
    RecyclerView.Adapter<com.autodark.adapter.DateTimeAdapter.ItemViewHolder>() {

    private val kTag = "DateTimeAdapter"
    private val countDownTimerHashMap by lazy { HashMap<String, CountDownTimer>() }
    private var layoutInflater = LayoutInflater.from(context)

    @SuppressLint("NotifyDataSetChanged")
    fun setRefreshData(dataRows: MutableList<com.autodark.bean.DateTimeBean>) {
        LogUtils.log(Log.DEBUG,kTag, "刷新数据，新的数据行数量: ${dataRows.size}")
        this.dataBeans.clear()
        this.dataBeans.addAll(dataRows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = dataBeans.size

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):ItemViewHolder {
        Log.d(kTag, "创建视图持有者，类型: $viewType")
        return ItemViewHolder(
            layoutInflater.inflate(R.layout.item_timer_rv_l, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ItemViewHolder,position: Int) {
        Log.d("AuToDark.onBindViewHolder", "绑定视图，位置: $position")

        val timeBean = dataBeans[position]
        holder.dateView.text = timeBean.date
        holder.timeView.text = timeBean.time
        holder.weekDayView.text = timeBean.weekDay

        holder.itemView.setOnClickListener {
            Log.d("AuToDark.onBindViewHolder", "单击项目，位置: $position")
            itemClickListener?.onItemClick(position)
        }

        // 长按监听
        holder.itemView.setOnLongClickListener {
            Log.d("AuToDark.onBindViewHolder", "长按项目，位置: $position")
            itemClickListener?.onItemLongClick(position)
            true
        }

        val time = "${timeBean.date} ${timeBean.time}"
        if (time.isEarlierThenCurrent()) {
            Log.d("AuToDark.onBindViewHolder", "任务已过期，时间: $time")
            holder.countDownTextView.text = "任务已过期"
            holder.countDownTextView.setTextColor(Color.RED)
        } else {
            val diffCurrentMillis = time.diffCurrentMillis()

            holder.countDownTextView.setTextColor(Color.BLUE)

            holder.countDownProgress.max = diffCurrentMillis.toInt()
            //刷新列表先停止之前的定时器，否则会出现重复计时问题
            stopCountDownTimer(timeBean)

            //重新计时
            val countDownTimer = object : CountDownTimer(diffCurrentMillis, 1) {
                override fun onTick(millisUntilFinished: Long) {
                    holder.countDownProgress.progress =
                        (diffCurrentMillis - millisUntilFinished).toInt()

                    holder.countDownTextView.text = "${millisUntilFinished / 1000}秒后执行定时任务"
                }

                override fun onFinish() {
                    Log.d("AuToDark.onBindViewHolder", "倒计时结束，位置: $position")
                    itemClickListener?.onCountDownFinish()
                    holder.countDownTextView.text = "任务已过期"
                    holder.countDownTextView.setTextColor(Color.RED)
                }
            }.start()
            countDownTimerHashMap[timeBean.uuid] = countDownTimer
        }
    }

    fun stopCountDownTimer(bean: com.autodark.bean.DateTimeBean) {
        val downTimer = countDownTimerHashMap[bean.uuid]
        if (downTimer != null) {
            downTimer.cancel()
            Log.d(kTag, "停止倒计时器: ${bean.weekDay} ${bean.date} ${bean.time}")
        }
    }

    private var itemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.itemClickListener = listener
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)

        fun onItemLongClick(position: Int)

        fun onCountDownFinish()
    }

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var timeView: TextView = itemView.findViewById(R.id.timeView)
        var dateView: TextView = itemView.findViewById(R.id.dateView)
        var weekDayView: TextView = itemView.findViewById(R.id.weekDayView)
        var countDownTextView: TextView = itemView.findViewById(R.id.countDownTextView)
        var countDownProgress: LinearProgressIndicator = itemView.findViewById(
            R.id.countDownProgress
        )
    }
}

package com.autodark.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.autodark.R
import com.autodark.databinding.ActivityQuestionAndAnswerBinding
import com.autodark.extensions.initImmersionBar
import com.pengxh.kt.lite.adapter.NormalRecyclerAdapter
import com.pengxh.kt.lite.adapter.ViewHolder
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.divider.RecyclerViewItemDivider
import com.pengxh.kt.lite.extensions.readAssetsFile
import com.pengxh.kt.lite.utils.ActivityStackManager
import com.pengxh.kt.lite.utils.HtmlRenderEngine
import com.pengxh.kt.lite.widget.TitleBarView
import com.autodark.utils.LogUtils

class QuestionAndAnswerActivity : KotlinBaseActivity<ActivityQuestionAndAnswerBinding>() {

    private val context = this
    private val gson by lazy { Gson() }

    override fun initEvent() {

    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "初始化活动")

        ActivityStackManager.addActivity(this)
        LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "活动添加到栈中")

        binding.marqueeView.requestFocus()
        LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "请求焦点到滚动视图")

        val assetsFile = readAssetsFile("QuestionAndAnswer.json")
        LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "读取资产文件: QuestionAndAnswer.json")

        val dataRows = gson.fromJson<MutableList<com.autodark.model.QuestionAnAnswerModel>>(
            assetsFile, object : TypeToken<MutableList<com.autodark.model.QuestionAnAnswerModel>>() {}.type
        )
        LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "解析 JSON 数据，数据行数量: ${dataRows.size}")

        binding.recyclerView.addItemDecoration(RecyclerViewItemDivider(1, Color.LTGRAY))
        LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "为 RecyclerView 添加分隔线")

        binding.recyclerView.adapter = object :
            NormalRecyclerAdapter<com.autodark.model.QuestionAnAnswerModel>(R.layout.item_q_a_rv_l, dataRows) {
            override fun convertView(
                viewHolder: ViewHolder, position: Int, item: com.autodark.model.QuestionAnAnswerModel
            ) {
                viewHolder.setText(R.id.questionView, item.question)
                val textView = viewHolder.getView<TextView>(R.id.answerView)
                HtmlRenderEngine.Builder()
                    .setContext(context)
                    .setHtmlContent(item.answer)
                    .setTargetView(textView)
                    .setOnGetImageSourceListener(object :
                        HtmlRenderEngine.OnGetImageSourceListener {
                        override fun imageSource(url: String) {
                            LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "获取图片源: $url")
                        }
                    }).build().load()
                LogUtils.log(Log.DEBUG,"AuToDark.initOnCreate", "设置问题和答案视图，问题: ${item.question}")
            }
        }
    }


    override fun initViewBinding(): ActivityQuestionAndAnswerBinding {
        return ActivityQuestionAndAnswerBinding.inflate(layoutInflater)
    }

    override fun observeRequestState() {

    }

    override fun setupTopBarLayout() {
        LogUtils.log(Log.DEBUG,"AuToDark.setupTopBarLayout", "设置顶部导航栏布局")

        // 初始化沉浸式状态栏
        binding.rootView.initImmersionBar(this, true, R.color.white)
        LogUtils.log(Log.DEBUG,"AuToDark.setupTopBarLayout", "沉浸式状态栏已初始化，背景颜色设置为白色")

        // 设置标题栏点击事件
        binding.titleView.setOnClickListener(object : TitleBarView.OnClickListener {
            override fun onLeftClick() {
                LogUtils.log(Log.DEBUG,"AuToDark.setupTopBarLayout", "左侧按钮点击，结束当前活动")
                finish()
            }

            override fun onRightClick() {
                LogUtils.log(Log.DEBUG,"AuToDark.setupTopBarLayout", "右侧按钮点击，执行相应操作")
                // 此处可以添加右侧按钮的操作
            }
        })
    }

}
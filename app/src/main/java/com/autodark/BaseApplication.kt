package com.autodark

import android.app.Application
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.tencent.bugly.crashreport.CrashReport
import kotlin.properties.Delegates

class BaseApplication : Application() {

    companion object {
        private var application: com.autodark.BaseApplication by Delegates.notNull()

        fun get() = com.autodark.BaseApplication.Companion.application
    }

    lateinit var daoSession: com.autodark.greendao.DaoSession

    override fun onCreate() {
        super.onCreate()
        com.autodark.BaseApplication.Companion.application = this
        SaveKeyValues.initSharedPreferences(this)
        CrashReport.initCrashReport(this, "ce38195468", false)
        initDataBase()
    }

    private fun initDataBase() {
        val helper = com.autodark.greendao.DaoMaster.DevOpenHelper(this, "DingRecord.db")
        val daoMaster = com.autodark.greendao.DaoMaster(helper.writableDatabase)
        daoSession = daoMaster.newSession()
    }
}
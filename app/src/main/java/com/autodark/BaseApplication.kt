package com.autodark

import android.app.Application
import android.provider.Settings
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.tencent.bugly.crashreport.CrashReport
import kotlin.properties.Delegates

class BaseApplication : Application() {

    var androidId: String = ""

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

        // 获取设备的 Android ID
        androidId = getUUID()
    }

    private fun initDataBase() {
        val helper = com.autodark.greendao.DaoMaster.DevOpenHelper(this, "DingRecord.db")
        val daoMaster = com.autodark.greendao.DaoMaster(helper.writableDatabase)
        daoSession = daoMaster.newSession()
    }

    //获取设备唯一ID
    private fun getUUID(): String {
        return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
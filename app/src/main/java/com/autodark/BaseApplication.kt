package com.autodark

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.provider.Settings
import com.pengxh.kt.lite.utils.SaveKeyValues
import kotlin.properties.Delegates

class BaseApplication : Application() {

    var androidId: String = ""
    var caTimes:String = ""

    //mqtt设置
    lateinit var mqttClientId: String
    private lateinit var user: String
    private lateinit var pwd: String
    lateinit var mqttTopicCheckAppAlive: String
    lateinit var mqttTopicCheckAppAliveResult: String
    lateinit var mqttTopicDark: String
    lateinit var mqttTopicDarkResult: String
    lateinit var mqttTopicLastWill: String

    companion object {
        private var application: BaseApplication by Delegates.notNull()

        fun get() = application
        const val PREF_NAME = "app_prefs"
        const val KEY_DOMAIN = "DOMAIN_ADDRESS"
    }

    // 动态获取或设置域名
    var domainAddress: String
        get() {
            val sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return sp.getString(KEY_DOMAIN, "") ?: ""
        }
        set(value) {
            val sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            sp.edit().putString(KEY_DOMAIN, value.trim()).apply()
        }

    lateinit var daoSession: com.autodark.greendao.DaoSession

    override fun onCreate() {
        super.onCreate()
        application = this
        SaveKeyValues.initSharedPreferences(this)
        initDataBase()

        // 获取设备的 Android ID
        androidId = getUUID()

        //获取订阅主题
        mqttClientId = androidId
        mqttTopicCheckAppAlive = "/topic/$androidId/checkAppAlive"
        mqttTopicCheckAppAliveResult = "/topic/$androidId/checkAppAliveResult"
        mqttTopicDark = "/topic/$androidId/dark"
        mqttTopicDarkResult = "/topic/$androidId/darkResult"
        mqttTopicLastWill = "/topic/$androidId/LastWill"
        user = androidId
        pwd = androidId
    }

    private fun initDataBase() {
        val helper = com.autodark.greendao.DaoMaster.DevOpenHelper(this, "DingRecord.db")
        val daoMaster = com.autodark.greendao.DaoMaster(helper.writableDatabase)
        daoSession = daoMaster.newSession()
    }

    //获取设备唯一ID
    @SuppressLint("HardwareIds")
    private fun getUUID(): String {
        return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
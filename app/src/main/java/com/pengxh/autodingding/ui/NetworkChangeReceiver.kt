package com.pengxh.autodingding.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pengxh.autodingding.utils.NetworkUtils


class NetworkChangeReceiver(private val activity: MainActivity) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AuToDark.NetworkChangeReceiver.onReceive", "接收到网络状态变化的广播")

        // 检查网络连接状态
        if (NetworkUtils.isNetworkAvailable(context)) {
            Log.d("AuToDark.NetworkChangeReceiver.onReceive", "网络连接可用")

            // 检查 MQTT 客户端是否已连接
            if (!activity.isMqttConnected() && !activity.isConnecting) {
                Log.d("AuToDark.NetworkChangeReceiver.onReceive", "MQTT 尚未连接，尝试连接")
                activity.connectToMqtt()
            } else {
                Log.d("AuToDark.NetworkChangeReceiver.onReceive", "MQTT 已连接")
            }
        } else {
            Log.d("AuToDark.NetworkChangeReceiver.onReceive", "网络不可用")
        }
    }


}

package com.pengxh.autodingding.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.pengxh.autodingding.utils.NetworkUtils


class NetworkChangeReceiver(private val activity: MainActivity) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("networkChangeReceiver","NetworkChangeReceiver.ononReceive")
        // 检查网络连接状态
        if (NetworkUtils.isNetworkAvailable(context)) {
            Log.d("NetworkChangeReceiver", "Network is available")

            // 检查 MQTT 客户端是否已连接
            if (!activity.isMqttConnected() && !activity.isConnecting) {
                Log.d("NetworkChangeReceiver", "MQTT is not connected, attempting to connect")
                activity.connectToMqtt()
            } else {
                Log.d("NetworkChangeReceiver", "MQTT is already connected")
            }
        } else {
            Log.d("NetworkChangeReceiver", "Network is not available")
        }
    }

}

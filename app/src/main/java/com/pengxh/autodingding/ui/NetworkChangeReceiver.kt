package com.pengxh.autodingding.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.pengxh.autodingding.utils.NetworkUtils


class NetworkChangeReceiver(private val activity: MainActivity) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 检查网络连接状态
        if (NetworkUtils.isNetworkAvailable(context)) {
            Log.d("NetworkChangeReceiver", "Network is available")
            Toast.makeText(context, "Network is available", Toast.LENGTH_SHORT).show()
            // 连接 MQTT
            activity.connectToMqtt()
        } else {
            Log.d("NetworkChangeReceiver", "Network is not available")
            Toast.makeText(context, "Network is not available", Toast.LENGTH_SHORT).show()
        }
    }

}

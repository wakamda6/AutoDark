package com.autodark.model

import androidx.lifecycle.MutableLiveData

enum class MqttConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

object MqttStateHolder {
    val mqttState = MutableLiveData<MqttConnectionState>().apply {
        value = MqttConnectionState.DISCONNECTED
    }
}


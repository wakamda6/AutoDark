package com.autodark.model

import androidx.lifecycle.MutableLiveData

sealed class MqttConnectionState {
    object CONNECTING : MqttConnectionState()
    object CONNECTED : MqttConnectionState()
    object DISCONNECTED: MqttConnectionState()
    data class ERROR(val message: String) : MqttConnectionState()
}

object MqttStateHolder {
    val mqttState = MutableLiveData<MqttConnectionState>().apply {
        value = MqttConnectionState.DISCONNECTED
    }
}

package com.autodark

import javax.net.ssl.SSLContext

object MqttConfigHolder {
    var mqttSslContext: SSLContext? = null

    var isconnected: Boolean = false
}

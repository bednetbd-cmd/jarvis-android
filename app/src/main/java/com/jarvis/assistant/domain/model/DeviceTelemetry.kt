package com.jarvis.assistant.domain.model

enum class NetworkTransport(val label: String) {
    WIFI("WIFI"),
    CELLULAR("CELLULAR"),
    ETHERNET("ETHERNET"),
    OFFLINE("OFFLINE")
}

data class DeviceTelemetry(
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val networkTransport: NetworkTransport = NetworkTransport.OFFLINE
) {
    val isOnline: Boolean
        get() = networkTransport != NetworkTransport.OFFLINE
}

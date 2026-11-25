package com.jarvis.assistant.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.NetworkTransport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface SystemTelemetryPort {
    val telemetry: Flow<DeviceTelemetry>
    fun currentSnapshot(): DeviceTelemetry
}

class AndroidSystemTelemetryProvider(
    context: Context
) : SystemTelemetryPort {

    private val appContext = context.applicationContext

    override fun currentSnapshot(): DeviceTelemetry {
        return DeviceTelemetry(
            batteryPercent = readBatteryPercent(),
            isCharging = readIsCharging(),
            networkTransport = readNetworkTransport()
        )
    }

    override val telemetry: Flow<DeviceTelemetry> = callbackFlow {
        fun emitLatest() {
            trySend(currentSnapshot())
        }

        emitLatest()

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emitLatest()
            }
        }
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        appContext.registerReceiver(batteryReceiver, batteryFilter)

        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitLatest()
            override fun onLost(network: Network) = emitLatest()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = emitLatest()
        }

        val registeredCallback = runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            true
        }.getOrDefault(false)

        awaitClose {
            runCatching { appContext.unregisterReceiver(batteryReceiver) }
            if (registeredCallback) {
                runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            }
        }
    }.distinctUntilChanged()

    private fun readBatteryPercent(): Int {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (capacity in 0..100) capacity else -1
    }

    private fun readIsCharging(): Boolean {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.isCharging == true
    }

    private fun readNetworkTransport(): NetworkTransport {
        val manager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkTransport.OFFLINE
        val network = manager.activeNetwork ?: return NetworkTransport.OFFLINE
        val caps = manager.getNetworkCapabilities(network) ?: return NetworkTransport.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OFFLINE
        }
    }
}

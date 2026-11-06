package com.s520j.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** 离线态网络可用性跟踪：通过 ConnectivityManager 暴露一个 StateFlow<Boolean>。 */
@Singleton
class NetworkStateTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { trySend(currentConnected()) }
            override fun onLost(network: android.net.Network) { trySend(false) }
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                capabilities: NetworkCapabilities
            ) { trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) }
        }
        cm.registerDefaultNetworkCallback(callback)
        trySend(currentConnected())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** 同步取当前连通状态，供 ViewModel 在非协程场景快速判断。 */
    fun currentConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isAvailable == true &&
                cm.activeNetworkInfo?.isConnected == true
        }
    }
}

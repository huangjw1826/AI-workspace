package com.airecorder.android.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 连接状态枚举
 */
sealed class ConnectivityStatus {
    /** 网络可用、服务器可达 */
    data object Online : ConnectivityStatus()

    /** 网络不可用 */
    data object Offline : ConnectivityStatus()

    /** 网络可用但服务器不可达 */
    data class ServerUnreachable(val reason: String = "") : ConnectivityStatus()
}

/**
 * 网络连接 + 服务器可达性监控
 *
 * 使用方式：
 * - 在 Application 中初始化 connectivityMonitor.initialize(context)
 * - 在 UI 层收集 connectivityMonitor.status
 * - 当 PC 离线时显示红色横幅
 */
class ConnectivityMonitor {

    private val _status = MutableStateFlow<ConnectivityStatus>(ConnectivityStatus.Online)
    val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var healthCheckJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = false

    /** 服务器健康检查 URL（由外部设置） */
    var healthUrl: String = ""

    /** 活跃状态探测间隔（毫秒）*/
    var activeProbeIntervalMs: Long = 30_000L

    /** 后台状态探测间隔（毫秒）*/
    var idleProbeIntervalMs: Long = 120_000L

    /** 当前是否在后台 */
    var isInBackground: Boolean = false
        set(value) {
            field = value
            restartHealthCheck()
        }

    /** 健康检查回调（用于自定义探测逻辑，替代 URL 请求）*/
    var healthCheck: (suspend () -> Boolean)? = null

    fun initialize(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 注册网络状态回调
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkAvailable = true
                _status.value = ConnectivityStatus.Online
                startHealthCheck()
            }

            override fun onLost(network: Network) {
                isNetworkAvailable = false
                _status.value = ConnectivityStatus.Offline
                stopHealthCheck()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                isNetworkAvailable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)

        // 初始状态检查
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager?.getNetworkCapabilities(it) }
        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (isNetworkAvailable) {
            startHealthCheck()
        } else {
            _status.value = ConnectivityStatus.Offline
        }
    }

    fun shutdown() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        networkCallback = null
        stopHealthCheck()
    }

    private fun startHealthCheck() {
        stopHealthCheck()
        healthCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(if (isInBackground) idleProbeIntervalMs else activeProbeIntervalMs)

                val checkFn = healthCheck
                if (checkFn != null) {
                    try {
                        val reachable = withTimeout(10_000L) { checkFn() }
                        _status.value = if (reachable) {
                            ConnectivityStatus.Online
                        } else {
                            ConnectivityStatus.ServerUnreachable("PC 后端无响应")
                        }
                    } catch (e: Exception) {
                        _status.value = ConnectivityStatus.ServerUnreachable(
                            e.message ?: "健康检查失败"
                        )
                    }
                }
            }
        }
    }

    private fun stopHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private fun restartHealthCheck() {
        if (isNetworkAvailable) {
            startHealthCheck()
        }
    }
}

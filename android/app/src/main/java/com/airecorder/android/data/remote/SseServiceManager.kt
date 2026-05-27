package com.airecorder.android.data.remote

import android.util.Log
import com.airecorder.android.data.local.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseServiceManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val okHttpClient: OkHttpClient
) {
    private val TAG = "SseServiceManager"
    
    private var sseClient: SseClient? = null
    private var connectionJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _taskEvents = MutableSharedFlow<TaskEvent>(replay = 0, extraBufferCapacity = 64)
    val taskEvents: SharedFlow<TaskEvent> = _taskEvents.asSharedFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var currentServerUrl: String? = null
    private var currentApiToken: String? = null
    
    init {
        observePreferences()
    }
    
    private fun observePreferences() {
        serviceScope.launch {
            combine(
                preferencesManager.serverUrl,
                preferencesManager.apiToken
            ) { url, token ->
                if (url.isNotEmpty() && token.isNotEmpty()) {
                    connect(url, token)
                } else {
                    disconnect()
                }
            }.collect()
        }
    }
    
    private suspend fun connect(serverUrl: String, apiToken: String) {
        if (serverUrl == currentServerUrl && apiToken == currentApiToken && 
            _connectionState.value is ConnectionState.CONNECTED) {
            return
        }
        
        // 断开现有连接
        disconnect()
        
        currentServerUrl = serverUrl
        currentApiToken = apiToken
        
        _connectionState.value = ConnectionState.CONNECTING
        
        try {
            sseClient = SseClient(serverUrl, apiToken, okHttpClient).also { client ->
                // 监听SSE事件
                serviceScope.launch {
                    client.events.collect { event ->
                        _taskEvents.emit(event)
                    }
                }
                
                // 连接
                client.connect()
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "SSE连接成功: $serverUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSE连接失败", e)
            _connectionState.value = ConnectionState.ERROR(e.message ?: "未知错误")
        }
    }
    
    fun disconnect() {
        sseClient?.disconnect()
        sseClient = null
        connectionJob?.cancel()
        connectionJob = null
        currentServerUrl = null
        currentApiToken = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "SSE连接已断开")
    }
    
    fun manualConnect() {
        serviceScope.launch {
            val url = preferencesManager.serverUrl.first()
            val token = preferencesManager.apiToken.first()
            if (url.isNotEmpty() && token.isNotEmpty()) {
                connect(url, token)
            }
        }
    }
    
    fun clear() {
        disconnect()
        serviceScope.cancel()
    }
}

sealed class ConnectionState {
    object DISCONNECTED : ConnectionState()
    object CONNECTING : ConnectionState()
    object CONNECTED : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()
}

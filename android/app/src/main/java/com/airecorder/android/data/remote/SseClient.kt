package com.airecorder.android.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// SSE 事件数据类
data class SseEvent(
    val event: String? = null,
    val data: String? = null,
    val id: String? = null,
    val retry: Long? = null
)

// 任务事件
sealed class TaskEvent {
    data class TaskStarted(val taskId: String, val recordingId: String, val type: String) : TaskEvent()
    data class TaskProcessing(val taskId: String, val recordingId: String, val progress: Int) : TaskEvent()
    data class TaskCompleted(val taskId: String, val recordingId: String, val result: String) : TaskEvent()
    data class TaskFailed(val taskId: String, val recordingId: String, val error: String) : TaskEvent()
    object Unknown : TaskEvent()
}

class SseClient(
    private val baseUrl: String,
    private val apiToken: String,
    private val okHttpClient: OkHttpClient
) {
    private var currentJob: Job? = null
    private val _events = MutableSharedFlow<TaskEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<TaskEvent> = _events.asSharedFlow()
    
    private val reconnectDelays = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L)
    private var reconnectIndex = 0
    
    fun connect() {
        if (currentJob?.isActive == true) return
        
        currentJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    connectInternal()
                } catch (e: Exception) {
                    // 处理连接错误
                }
                
                // 指数退避重连
                if (isActive) {
                    val delayMs = reconnectDelays[reconnectIndex.coerceAtMost(reconnectDelays.size - 1)]
                    reconnectIndex = (reconnectIndex + 1).coerceAtMost(reconnectDelays.size - 1)
                    delay(delayMs)
                }
            }
        }
    }
    
    fun disconnect() {
        currentJob?.cancel()
        currentJob = null
    }
    
    private suspend fun connectInternal() {
        val request = Request.Builder()
            .url("$baseUrl/api/events")
            .header("Authorization", "Bearer $apiToken")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
        
        val client = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("SSE连接失败: ${response.code}")
        }
        
        // 重置重连指数
        reconnectIndex = 0
        
        response.body?.source()?.use { source ->
            var eventBuilder = SseEventBuilder()
            
            while (true) {
                val line = source.readUtf8Line() ?: break
                
                when {
                    line.isEmpty() -> {
                        // 空行表示事件结束
                        val event = eventBuilder.build()
                        if (event != null) {
                            processEvent(event)
                        }
                        eventBuilder = SseEventBuilder()
                    }
                    line.startsWith(":") -> {
                        // 注释行，忽略
                    }
                    else -> {
                        val colonIndex = line.indexOf(':')
                        if (colonIndex > 0) {
                            val field = line.substring(0, colonIndex)
                            val value = if (colonIndex + 1 < line.length && line[colonIndex + 1] == ' ') {
                                line.substring(colonIndex + 2)
                            } else {
                                line.substring(colonIndex + 1)
                            }
                            eventBuilder.setField(field, value)
                        } else {
                            // 没有冒号，整行作为字段名
                            eventBuilder.setField(line, "")
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun processEvent(event: SseEvent) {
        val taskEvent = when (event.event) {
            "task.started" -> parseTaskStarted(event.data)
            "task.processing" -> parseTaskProcessing(event.data)
            "task.completed" -> parseTaskCompleted(event.data)
            "task.failed" -> parseTaskFailed(event.data)
            else -> TaskEvent.Unknown
        }
        
        _events.emit(taskEvent)
    }
    
    private fun parseTaskStarted(data: String?): TaskEvent {
        return try {
            val json = JSONObject(data ?: "{}")
            TaskEvent.TaskStarted(
                taskId = json.optString("task_id", ""),
                recordingId = json.optString("recording_id", ""),
                type = json.optString("type", "")
            )
        } catch (e: Exception) {
            TaskEvent.Unknown
        }
    }
    
    private fun parseTaskProcessing(data: String?): TaskEvent {
        return try {
            val json = JSONObject(data ?: "{}")
            TaskEvent.TaskProcessing(
                taskId = json.optString("task_id", ""),
                recordingId = json.optString("recording_id", ""),
                progress = json.optInt("progress", 0)
            )
        } catch (e: Exception) {
            TaskEvent.Unknown
        }
    }
    
    private fun parseTaskCompleted(data: String?): TaskEvent {
        return try {
            val json = JSONObject(data ?: "{}")
            TaskEvent.TaskCompleted(
                taskId = json.optString("task_id", ""),
                recordingId = json.optString("recording_id", ""),
                result = json.optString("result", "")
            )
        } catch (e: Exception) {
            TaskEvent.Unknown
        }
    }
    
    private fun parseTaskFailed(data: String?): TaskEvent {
        return try {
            val json = JSONObject(data ?: "{}")
            TaskEvent.TaskFailed(
                taskId = json.optString("task_id", ""),
                recordingId = json.optString("recording_id", ""),
                error = json.optString("error", "")
            )
        } catch (e: Exception) {
            TaskEvent.Unknown
        }
    }
}

private class SseEventBuilder {
    private var event: String? = null
    private var data: String? = null
    private var id: String? = null
    private var retry: Long? = null
    
    fun setField(field: String, value: String) {
        when (field) {
            "event" -> event = value
            "data" -> {
                if (data == null) {
                    data = value
                } else {
                    data += "\n$value"
                }
            }
            "id" -> id = value
            "retry" -> retry = value.toLongOrNull()
        }
    }
    
    fun build(): SseEvent? {
        if (event == null && data == null && id == null) return null
        return SseEvent(event, data, id, retry)
    }
}

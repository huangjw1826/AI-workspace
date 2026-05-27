package com.airecorder.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Recording(
    val id: String,
    val filename: String,
    @SerialName("original_path")
    val originalPath: String? = null,
    val format: String? = null,
    val status: String = "pending",
    @SerialName("duration_seconds")
    val durationSeconds: Double? = null,
    @SerialName("file_size_bytes")
    val fileSizeBytes: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val tags: String? = null,
    @SerialName("source_type")
    val sourceType: String? = null
) {
    val isCompleted: Boolean
        get() = status == "completed" || status == "done"
    
    val isProcessing: Boolean
        get() = status == "processing" || status == "transcribing" || status == "pending"
    
    val isError: Boolean
        get() = status == "error" || status == "failed"
}

@Serializable
data class TranscriptSegment(
    val id: String? = null,
    @SerialName("recording_id")
    val recordingId: String? = null,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null,
    val speaker: String? = null,
    val text: String,
    val sequence: Int? = null
)

@Serializable
data class Summary(
    val id: String? = null,
    @SerialName("recording_id")
    val recordingId: String? = null,
    val mode: String? = null,
    val content: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class Task(
    val id: String,
    @SerialName("recording_id")
    val recordingId: String? = null,
    val type: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val error: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class RecordingDetail(
    val recording: Recording,
    val segments: List<TranscriptSegment> = emptyList(),
    val summaries: List<Summary> = emptyList(),
    val tasks: List<Task> = emptyList()
)

@Serializable
data class SearchResult(
    val recordings: List<Recording> = emptyList(),
    @SerialName("match_previews")
    val matchPreviews: Map<String, List<String>> = emptyMap()
)

@Serializable
data class HealthResponse(
    val status: String,
    val python: String? = null,
    val ffmpeg: Boolean = false,
    val funasr: Boolean = false,
    @SerialName("data_dir")
    val dataDir: String? = null,
    @SerialName("model_dir")
    val modelDir: String? = null,
    @SerialName("asr_model")
    val asrModel: String? = null,
    @SerialName("llm_provider")
    val llmProvider: String? = null,
    @SerialName("llm_base_url")
    val llmBaseUrl: String? = null,
    @SerialName("llm_model")
    val llmModel: String? = null,
    @SerialName("llm_configured")
    val llmConfigured: Boolean = false,
    @SerialName("log_dir")
    val logDir: String? = null,
    @SerialName("recent_errors")
    val recentErrors: List<String> = emptyList(),
    val system: SystemInfo? = null,
    val tunnel: TunnelInfo? = null
)

@Serializable
data class SystemInfo(
    @SerialName("cpu_percent")
    val cpuPercent: Double = -1.0,
    val memory: MemoryInfo? = null,
    val disk: DiskInfo? = null,
    @SerialName("uptime_seconds")
    val uptimeSeconds: Double = 0.0
)

@Serializable
data class MemoryInfo(
    val total: Long = -1,
    val available: Long = -1,
    val used: Long = -1,
    val percent: Double = -1.0
)

@Serializable
data class DiskInfo(
    val total: Long = -1,
    val used: Long = -1,
    val free: Long = -1
)

@Serializable
data class TunnelInfo(
    val connected: Boolean = true
)

@Serializable
data class LLMSettings(
    val provider: String,
    @SerialName("base_url")
    val baseUrl: String,
    val model: String,
    val configured: Boolean,
    @SerialName("api_key_masked")
    val apiKeyMasked: String? = null,
    @SerialName("mimo_thinking")
    val mimoThinking: String? = null,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @Transient
    val providers: Map<String, Map<String, Any>>? = null
)

@Serializable
data class WatchSettings(
    val enabled: Boolean,
    @SerialName("watch_dir")
    val watchDir: String,
    val recursive: Boolean,
    @SerialName("interval_seconds")
    val intervalSeconds: Int,
    val exists: Boolean
)

@Serializable
data class StorageSettings(
    @SerialName("transcript_dir")
    val transcriptDir: String,
    @SerialName("summary_dir")
    val summaryDir: String,
    @SerialName("transcript_exists")
    val transcriptExists: Boolean,
    @SerialName("summary_exists")
    val summaryExists: Boolean
)

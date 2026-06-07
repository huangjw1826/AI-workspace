package com.airecorder.android.data.repository

import com.airecorder.android.data.local.AudioCacheManager
import com.airecorder.android.data.local.RecordingCache
import com.airecorder.android.data.model.*
import com.airecorder.android.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val apiService: ApiService,
    private val cacheManager: AudioCacheManager
) {
    
    fun isAudioCached(id: String): Boolean = cacheManager.isCached(id)
    
    fun getCachedAudioFile(id: String): File? = cacheManager.getCachedPath(id)

    fun downloadAudio(id: String, format: String, startByte: Long = 0L): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.Started)
        
        val range = if (startByte > 0) "bytes=$startByte-" else null
        val response = apiService.downloadAudio(id, range)
        if (!response.isSuccessful) {
            emit(DownloadStatus.Error("服务器返回错误: ${response.code()}"))
            return@flow
        }
        
        val body = response.body()
        if (body == null) {
            emit(DownloadStatus.Error("响应体为空"))
            return@flow
        }
        
        val contentLength = body.contentLength()
        val totalLength = if (startByte > 0) contentLength + startByte else contentLength
        val file = cacheManager.getCacheFile(id, format)
        val tempFile = File(file.absolutePath + ".tmp")
        
        try {
            body.byteStream().use { inputStream ->
                java.io.FileOutputStream(tempFile, startByte > 0).use { outputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = startByte
                    var lastEmitTime = 0L
                    
                    // Emit initial progress
                    emit(DownloadStatus.Progress(if (totalLength > 0) totalBytesRead.toFloat() / totalLength else 0f, totalBytesRead, totalLength))
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastEmitTime > 200) { // Throttle to 5Hz
                            val progress = if (totalLength > 0) totalBytesRead.toFloat() / totalLength else 0f
                            emit(DownloadStatus.Progress(progress, totalBytesRead, totalLength))
                            lastEmitTime = currentTime
                        }
                    }
                }
            }
            if (tempFile.renameTo(file)) {
                emit(DownloadStatus.Success(file))
            } else {
                emit(DownloadStatus.Error("保存文件失败"))
            }
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        emit(DownloadStatus.Error("下载异常: ${e.localizedMessage}"))
    }.flowOn(Dispatchers.IO)

    fun clearAudioCache(id: String) {
        cacheManager.clearCache(id)
    }
    
    fun getTempFileSize(id: String, format: String): Long {
        val file = cacheManager.getCacheFile(id, format)
        val tempFile = File(file.absolutePath + ".tmp")
        return if (tempFile.exists()) tempFile.length() else 0L
    }

    fun deleteTempFile(id: String, format: String) {
        val file = cacheManager.getCacheFile(id, format)
        val tempFile = File(file.absolutePath + ".tmp")
        if (tempFile.exists()) tempFile.delete()
    }

    sealed class DownloadStatus {
        data object Started : DownloadStatus()
        data class Progress(val progress: Float, val downloaded: Long, val total: Long) : DownloadStatus()
        data class Success(val file: File) : DownloadStatus()
        data class Error(val message: String) : DownloadStatus()
    }
    
    suspend fun getRecordings(query: String = ""): Result<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecordings(query)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    // 仅缓存无筛选条件的结果
                    if (query.isEmpty()) {
                        RecordingCache.putRecordings(result.recordings)
                    }
                    Result.success(result)
                } else {
                    Result.failure(Exception("Failed to load recordings: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 缓存优先的录音列表加载 */
    suspend fun getRecordingsCached(query: String = ""): Pair<List<Recording>?, Result<SearchResult>> {
        // 先尝试缓存
        val cached = if (query.isEmpty()) {
            RecordingCache.getRecordings()
        } else null

        // 网络请求
        val result = getRecordings(query)
        return Pair(cached, result)
    }

    suspend fun getRecording(id: String): Result<RecordingDetail> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecording(id)
                if (response.isSuccessful && response.body() != null) {
                    val detail = response.body()!!
                    RecordingCache.putRecordingDetail(id, detail)
                    Result.success(detail)
                } else {
                    Result.failure(Exception("Failed to load recording: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 缓存优先的录音详情加载 */
    suspend fun getRecordingCached(id: String): Pair<RecordingDetail?, Result<RecordingDetail>> {
        val cached = RecordingCache.getRecordingDetail(id)
        val result = getRecording(id)
        return Pair(cached, result)
    }

    /** 清除所有录音缓存 */
    suspend fun invalidateCache() {
        RecordingCache.invalidateRecordings()
    }
    
    suspend fun uploadRecording(file: File, fileName: String): Result<Recording> =
        withContext(Dispatchers.IO) {
            try {
                val requestFile = file.asRequestBody()
                val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
                val response = apiService.uploadRecording(body)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to upload recording: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun deleteRecording(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteRecording(id)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete recording: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun exportTranscript(id: String, format: String = "md"): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.exportTranscript(id, format)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to export transcript: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun transcribe(id: String): Result<Task> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.transcribe(id)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to start transcription: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun summarize(id: String, mode: String = "summary"): Result<Task> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.summarize(id, mode)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to start summary: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getTask(id: String): Result<Task> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTask(id)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get task: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getSummaryTemplates(): Result<List<Map<String, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getSummaryTemplates()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get summary templates: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

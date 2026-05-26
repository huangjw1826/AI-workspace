package com.airecorder.android.data.repository

import com.airecorder.android.data.model.*
import com.airecorder.android.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val apiService: ApiService
) {
    
    suspend fun getRecordings(query: String = "", tag: String = ""): Result<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecordings(query, tag)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load recordings: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getRecording(id: String): Result<RecordingDetail> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecording(id)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load recording: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
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

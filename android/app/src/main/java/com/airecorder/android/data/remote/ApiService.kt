package com.airecorder.android.data.remote

import com.airecorder.android.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @GET("/health")
    suspend fun getHealth(): Response<HealthResponse>
    
    @GET("/api/recordings")
    suspend fun getRecordings(
        @Query("query") query: String = ""
    ): Response<SearchResult>
    
    @GET("/api/recordings/{id}")
    suspend fun getRecording(@Path("id") id: String): Response<RecordingDetail>
    
    @Multipart
    @POST("/api/recordings")
    suspend fun uploadRecording(
        @Part file: MultipartBody.Part
    ): Response<Recording>
    
    @DELETE("/api/recordings/{id}")
    suspend fun deleteRecording(@Path("id") id: String): Response<Map<String, String>>
    
    @GET("/api/recordings/{id}/exports/transcript")
    suspend fun exportTranscript(
        @Path("id") id: String,
        @Query("format") format: String = "md"
    ): Response<String>
    
    @POST("/api/transcribe/{id}")
    suspend fun transcribe(@Path("id") id: String): Response<Task>
    
    @POST("/api/summary/{id}")
    suspend fun summarize(
        @Path("id") id: String,
        @Query("mode") mode: String = "summary"
    ): Response<Task>
    
    @GET("/api/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<Task>
    
    @GET("/api/summary/templates")
    suspend fun getSummaryTemplates(): Response<List<Map<String, String>>>
    
    @GET("/api/settings/llm")
    suspend fun getLLMSettings(): Response<LLMSettings>
    
    @GET("/api/settings/watch")
    suspend fun getWatchSettings(): Response<WatchSettings>
    
    @GET("/api/settings/storage")
    suspend fun getStorageSettings(): Response<StorageSettings>
    
    @Streaming
    @GET("/api/recordings/{id}/audio")
    suspend fun downloadAudio(
        @Path("id") id: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>
    
    @GET("/api/watch/events")
    suspend fun getWatchEvents(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<WatchEvent>>
}

package com.airecorder.android.data.repository

import com.airecorder.android.data.model.*
import com.airecorder.android.data.remote.ApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val apiService: ApiService
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    suspend fun getHealth(): Result<HealthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getHealth()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load health: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun testConnection(serverUrl: String, apiToken: String): Result<HealthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
                
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                
                val testOkHttpClient = OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor { chain ->
                        val originalRequest = chain.request()
                        val requestBuilder = originalRequest.newBuilder()
                        if (apiToken.isNotBlank()) {
                            requestBuilder.header("X-API-Token", apiToken)
                        }
                        chain.proceed(requestBuilder.build())
                    }
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
                
                val testRetrofit = Retrofit.Builder()
                    .client(testOkHttpClient)
                    .baseUrl(normalizedUrl)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                
                val testApiService = testRetrofit.create(ApiService::class.java)
                val response = testApiService.getHealth()
                
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to connect: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getLLMSettings(): Result<LLMSettings> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getLLMSettings()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load LLM settings: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getWatchSettings(): Result<WatchSettings> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getWatchSettings()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load watch settings: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun getStorageSettings(): Result<StorageSettings> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getStorageSettings()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load storage settings: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

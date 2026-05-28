package com.airecorder.android.data.repository

import com.airecorder.android.data.model.WatchEvent
import com.airecorder.android.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getWatchEvents(
        limit: Int = 100,
        offset: Int = 0
    ): Result<List<WatchEvent>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWatchEvents(limit, offset)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load watch events: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.airecorder.android.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioCacheManager(private val context: Context) {
    
    private val cacheDir = File(context.cacheDir, "audio").apply {
        if (!exists()) {
            mkdirs()
        }
    }
    
    private val maxCacheFiles = 5
    
    fun getCachedPath(recordingId: String, format: String? = null): File? {
        val files = cacheDir.listFiles() ?: return null
        return files.find { it.name.startsWith(recordingId) }
    }
    
    fun isCached(recordingId: String, format: String? = null): Boolean {
        return getCachedPath(recordingId, format) != null
    }
    
    fun getCacheFile(recordingId: String, format: String): File {
        return File(cacheDir, "${recordingId}.${format.lowercase()}")
    }
    
    fun clearOldCache() {
        val files = cacheDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > maxCacheFiles) {
            files.drop(maxCacheFiles).forEach { it.delete() }
        }
    }
    
    fun clearCache(recordingId: String) {
        getCachedPath(recordingId)?.delete()
    }
    
    fun clearAllCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
    
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    suspend fun saveAudioToCache(
        recordingId: String,
        format: String,
        inputStream: java.io.InputStream
    ): File = withContext(Dispatchers.IO) {
        val file = getCacheFile(recordingId, format)
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        clearOldCache()
        file
    }
}

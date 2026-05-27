package com.airecorder.android.data.local

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CacheHelper @Inject constructor() {
    // 内存缓存 - 最大使用应用内存的 1/8
    private val memoryCache: LruCache<String, Any> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        object : LruCache<String, Any>(cacheSize) {
            override fun sizeOf(key: String, value: Any): Int {
                return when (value) {
                    is Bitmap -> value.byteCount / 1024
                    is String -> (value.length * 2) / 1024
                    else -> 1
                }
            }
        }
    }

    // 临时数据缓存
    private val tempDataCache = mutableMapOf<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getFromCache(key: String): T? = withContext(Dispatchers.IO) {
        return@withContext memoryCache[key] as? T
    }

    suspend fun putToCache(key: String, value: Any, ttl: Long = DEFAULT_TTL) = withContext(Dispatchers.IO) {
        memoryCache.put(key, value)
        tempDataCache[key] = CacheEntry(value, System.currentTimeMillis() + ttl)
        cleanupExpired()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getOrFetch(
        key: String,
        ttl: Long = DEFAULT_TTL,
        fetch: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        val cached = tempDataCache[key]
        if (cached != null && !cached.isExpired()) {
            return@withContext cached.value as T
        }

        val fetched = fetch()
        putToCache(key, fetched, ttl)
        fetched
    }

    fun clear(key: String) {
        memoryCache.remove(key)
        tempDataCache.remove(key)
    }

    fun clearAll() {
        memoryCache.evictAll()
        tempDataCache.clear()
    }

    private fun cleanupExpired() {
        tempDataCache.entries.removeAll { it.value.isExpired() }
    }

    private data class CacheEntry(
        val value: Any,
        val expiresAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    companion object {
        const val DEFAULT_TTL = 5 * 60 * 1000L // 5分钟
        const val SHORT_TTL = 30 * 1000L // 30秒
        const val LONG_TTL = 30 * 60 * 1000L // 30分钟
    }
}

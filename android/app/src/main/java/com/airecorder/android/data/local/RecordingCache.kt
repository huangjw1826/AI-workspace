package com.airecorder.android.data.local

import com.airecorder.android.data.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 内存缓存层 — LRU 策略
 * 用于减少重复 API 请求，提升感知响应速度
 */
object RecordingCache {

    private const val MAX_LIST_CACHE_SIZE = 1
    private const val MAX_DETAIL_CACHE_SIZE = 20

    // TTL 配置（毫秒）
    private const val LIST_CACHE_TTL = 30_000L      // 列表：30 秒
    private const val DETAIL_CACHE_TTL = 300_000L    // 详情：5 分钟
    private const val HEALTH_CACHE_TTL = 60_000L     // 健康：60 秒

    private val mutex = Mutex()

    // 录音列表缓存
    private var listCache: CacheEntry<List<Recording>>? = null

    // 录音详情缓存 (recordingId -> detail)
    private val detailCache = object : LinkedHashMap<String, CacheEntry<RecordingDetail>>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<RecordingDetail>>?): Boolean {
            return size > MAX_DETAIL_CACHE_SIZE
        }
    }

    // 健康数据缓存
    private var healthCache: CacheEntry<HealthResponse>? = null

    // ========== 录音列表 ==========

    suspend fun getRecordings(): List<Recording>? {
        return mutex.withLock {
            listCache?.takeIf { !it.isExpired(LIST_CACHE_TTL) }?.data
        }
    }

    suspend fun putRecordings(recordings: List<Recording>) {
        mutex.withLock {
            listCache = CacheEntry(recordings)
        }
    }

    suspend fun invalidateRecordings() {
        mutex.withLock {
            listCache = null
        }
    }

    // ========== 录音详情 ==========

    suspend fun getRecordingDetail(recordingId: String): RecordingDetail? {
        return mutex.withLock {
            detailCache[recordingId]?.takeIf { !it.isExpired(DETAIL_CACHE_TTL) }?.data
        }
    }

    suspend fun putRecordingDetail(recordingId: String, detail: RecordingDetail) {
        mutex.withLock {
            detailCache[recordingId] = CacheEntry(detail)
        }
    }

    // ========== 健康数据 ==========

    suspend fun getHealth(): HealthResponse? {
        return mutex.withLock {
            healthCache?.takeIf { !it.isExpired(HEALTH_CACHE_TTL) }?.data
        }
    }

    suspend fun putHealth(data: HealthResponse) {
        mutex.withLock {
            healthCache = CacheEntry(data)
        }
    }

    // ========== 全部清除 ==========

    suspend fun clearAll() {
        mutex.withLock {
            listCache = null
            detailCache.clear()
            healthCache = null
        }
    }

    // ========== 内部类 ==========

    private class CacheEntry<T>(
        val data: T,
        private val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(ttl: Long): Boolean {
            return System.currentTimeMillis() - createdAt > ttl
        }
    }
}

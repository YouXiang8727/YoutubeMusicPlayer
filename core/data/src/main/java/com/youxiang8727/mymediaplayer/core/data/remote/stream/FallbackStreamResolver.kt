package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** 可注入的時鐘（TTL 快取用），單元測試以 fake 時鐘推進時間。 */
fun interface StreamClock {
    fun nowMs(): Long
}

/**
 * 多層 fallback 串流解析器。
 *
 * 策略：
 * 1. 先查快取（成功結果 TTL 內直接回傳，避免逐首解析重複網路成本）
 * 2. 未命中則依注入順序嘗試各 [AudioStreamSource]，任一成功即快取並回傳
 * 3. 全數失敗時，經 [StreamErrorClassifier] 分類並聚合各來源錯誤成一則可讀訊息
 *    （會顯示於媒體通知，bot 封鎖時附帶使用者可操作的建議）
 *
 * 可測性：來源清單、錯誤分類器、時鐘皆可注入；純 JVM 單元測試覆蓋順序／快取／聚合。
 */
@Singleton
class FallbackStreamResolver @Inject constructor(
    private val sources: List<@JvmSuppressWildcards AudioStreamSource>,
    private val classifier: StreamErrorClassifier,
    private val dispatchers: DispatcherProvider,
    private val clock: StreamClock
) {

    private data class CacheEntry(val url: String, val cachedAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * @param videoId 影片 ID
     * @param force 是否強制重新解析。預設 false（命中 TTL 快取直接回傳）；
     *   true 時**繞過快取**強制重跑所有來源——成功以新結果覆寫快取（換新鮮 URL），
     *   全數失敗則清除舊快取（舊 URL 已驗證失效，避免殘留被再命中）。
     *   用於 content URL 過期（HTTP 403）後重試同曲。
     */
    suspend fun resolve(videoId: String, force: Boolean = false): Result<String> = withContext(dispatchers.io) {
        if (!force) {
            cache[videoId]?.let { entry ->
                if (clock.nowMs() - entry.cachedAtMs < CACHE_TTL_MS) {
                    return@withContext Result.success(entry.url)
                }
                cache.remove(videoId, entry)
            }
        }

        val attempts = mutableListOf<Pair<String, Throwable>>()

        for (source in sources) {
            source.fetch(videoId)
                .onSuccess { url ->
                    // 成功時無論是否 force 都寫入快取（force 時覆寫舊值，換新鮮 URL）
                    cache[videoId] = CacheEntry(url, clock.nowMs())
                    return@withContext Result.success(url)
                }
                .onFailure { throwable ->
                    attempts += source.name to throwable
                }
        }

        // 全數失敗：force 模式下也不保留舊快取（舊 URL 已驗證失效）；清除避免再被命中。
        if (force) cache.remove(videoId)
        Result.failure(IOException(classifier.describe(attempts)))
    }

    companion object {
        /**
         * 快取 TTL：串流 URL 簽章約 6 小時後失效，但保險起見取較短值，
         * 避免暫停過久後恢復播放吃到死 URL（過期即重新解析）。
         */
        const val CACHE_TTL_MS: Long = 30L * 60 * 1000
    }
}

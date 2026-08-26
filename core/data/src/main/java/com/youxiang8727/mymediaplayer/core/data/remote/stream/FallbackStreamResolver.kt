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

    suspend fun resolve(videoId: String): Result<String> = withContext(dispatchers.io) {
        cache[videoId]?.let { entry ->
            if (clock.nowMs() - entry.cachedAtMs < CACHE_TTL_MS) {
                return@withContext Result.success(entry.url)
            }
            cache.remove(videoId, entry)
        }

        val attempts = mutableListOf<Pair<String, Throwable>>()
        var sawBotBlock = false

        for (source in sources) {
            source.fetch(videoId)
                .onSuccess { url ->
                    cache[videoId] = CacheEntry(url, clock.nowMs())
                    return@withContext Result.success(url)
                }
                .onFailure { throwable ->
                    attempts += source.name to throwable
                    if (classifier.classify(throwable.message) == StreamFailureKind.BOT_BLOCK) {
                        sawBotBlock = true
                    }
                }
        }

        Result.failure(IOException(classifier.describe(sawBotBlock, attempts)))
    }

    companion object {
        /**
         * 快取 TTL：串流 URL 簽章約 6 小時後失效，但保險起見取較短值，
         * 避免暫停過久後恢復播放吃到死 URL（過期即重新解析）。
         */
        const val CACHE_TTL_MS: Long = 30L * 60 * 1000
    }
}

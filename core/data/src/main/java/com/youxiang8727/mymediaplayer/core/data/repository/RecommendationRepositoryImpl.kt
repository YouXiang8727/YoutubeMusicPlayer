package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.remote.RelatedStreamsDataSource
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.RecommendationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * 「為你推薦」實作：跨種子共現評分。
 *
 * 流程：
 * 1. 對各 [seeds] **併發**抓取 related（[RelatedStreamsDataSource.related]）；
 *    單一 seed 失敗 → 該 seed 視同無相關歌曲（空清單），不影響其他 seed。
 * 2. 全部種子皆失敗 → [Result.failure]（前端顯示可重試錯誤）。
 * 3. 以 [rankRecommendations] 純函數評分：候選出現在幾個**不同** seed 的 related
 *    集合中（共現）→ 分數愈高愈前；同分以 title 字元序穩定排序；
 *    排除種子自身與「已在播放清單中」的歌曲（以 `observeRecentItems` 快照取得）；
 *    最後取 limit 筆。
 *
 * 已知 videoId 快照優先以 `PlaylistRepository.observeRecentItems` 取得（不另開 DAO 方法）。
 */
@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val relatedStreamsDataSource: RelatedStreamsDataSource,
    private val playlistRepository: PlaylistRepository
) : RecommendationRepository {

    override suspend fun recommendationsFor(
        seeds: List<PlaylistItem>,
        limit: Int
    ): Result<List<VideoResult>> = coroutineScope {
        if (seeds.isEmpty()) {
            return@coroutineScope Result.success(emptyList())
        }

        // 併發抓各 seed 的 related；先保留原始 Result 以判斷「全部失敗」
        val deferredBySeed = seeds.associate { seed ->
            seed.videoId to async { relatedStreamsDataSource.related(seed.videoId) }
        }
        val relatedResultBySeed = deferredBySeed.mapValues { (_, deferred) -> deferred.await() }

        val allFailed = relatedResultBySeed.values.all { it.isFailure }
        if (allFailed) {
            val cause = relatedResultBySeed.values
                .firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("全部種子抓取 related 失敗")
            return@coroutineScope Result.failure(cause)
        }

        val relatedBySeed = relatedResultBySeed.mapValues { (_, result) ->
            result.getOrDefault(emptyList())
        }
        val knownVideoIds = playlistRepository.observeRecentItems(KNOWN_VIDEO_IDS_LIMIT)
            .first()
            .map { it.videoId }
            .toSet()

        Result.success(rankRecommendations(seeds, relatedBySeed, knownVideoIds, limit))
    }

    companion object {
        /** 已知 videoId 快照筆數（排除「已在播放清單」的候選；1000 已遠超合理收藏量）。 */
        internal const val KNOWN_VIDEO_IDS_LIMIT = 1000
    }
}

/**
 * 跨種子共現評分（純函數，JVM 可測、不觸網、無 Android 依賴）。
 *
 * @param seeds 種子歌曲（其 videoId 會被排除在候選之外）。
 * @param relatedBySeed 各 seed 的 related 清單（失敗 seed 為空清單）。
 * @param knownVideoIds 已在播放清單中的 videoId（排除）。
 * @param limit 最多回傳筆數。
 * @return 依評分排序的候選清單；無候選回空清單。
 */
internal fun rankRecommendations(
    seeds: List<PlaylistItem>,
    relatedBySeed: Map<String, List<VideoResult>>,
    knownVideoIds: Set<String>,
    limit: Int
): List<VideoResult> {
    val excluded = knownVideoIds + seeds.mapTo(mutableSetOf()) { it.videoId }
    // videoId -> (候選, 共現數)。key 以 videoId 計，跨 seed 去重由 distinctBy 保證
    val scored = mutableMapOf<String, Pair<VideoResult, Int>>()
    for (related in relatedBySeed.values) {
        // 同一 seed 內重複的 videoId 只算一次（避免灌分）
        for (candidate in related.distinctBy { it.videoId }) {
            if (candidate.videoId in excluded) continue
            val entry = scored.getOrPut(candidate.videoId) { candidate to 0 }
            scored[candidate.videoId] = entry.first to (entry.second + 1)
        }
    }
    if (scored.isEmpty()) return emptyList()

    // 共現分數降序 → 同分以 title 字元序（comparator 鏈保持穩定排序）
    return scored.values
        .sortedWith(
            compareByDescending<Pair<VideoResult, Int>> { it.second }
                .thenBy { it.first.title }
        )
        .map { it.first }
        .take(limit)
}
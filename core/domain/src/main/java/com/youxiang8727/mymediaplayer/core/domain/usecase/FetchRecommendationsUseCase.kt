package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.RecommendationRepository
import javax.inject.Inject

/**
 * 抓取「為你推薦」（探索頁推薦區塊的資料契約）。
 *
 * - [invoke] 會把 [limit] clamp 到 `1..RECOMMENDATION_LIMIT`（避免呼叫端傳入
 *   不合理的筆數）；種子清單原樣透傳（種子數由呼叫端以 [SEED_LIMIT] 控制）。
 * - 錯誤以 [Result] 封裝回傳（失敗原因見 [RecommendationRepository]）。
 */
class FetchRecommendationsUseCase @Inject constructor(
    private val repository: RecommendationRepository
) {
    /**
     * @param seeds 推薦種子（通常為最近加入播放清單的歌曲，最多 [SEED_LIMIT] 首）。
     * @param limit 最多回傳的推薦筆數（內部 clamp 至 `1..RECOMMENDATION_LIMIT`）。
     */
    suspend operator fun invoke(
        seeds: List<PlaylistItem>,
        limit: Int = RECOMMENDATION_LIMIT
    ): Result<List<VideoResult>> =
        repository.recommendationsFor(seeds, limit.coerceIn(1, RECOMMENDATION_LIMIT))

    companion object {
        /** 推薦種子數上限（探索頁以最近加入的前 N 首為種子）。 */
        const val SEED_LIMIT = 5

        /** 單次顯示的推薦筆數上限。 */
        const val RECOMMENDATION_LIMIT = 10
    }
}
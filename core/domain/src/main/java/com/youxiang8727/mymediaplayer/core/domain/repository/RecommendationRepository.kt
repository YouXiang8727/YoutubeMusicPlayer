package com.youxiang8727.mymediaplayer.core.domain.repository

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult

/**
 * 「為你推薦」資料源（探索頁推薦區塊專用）。
 *
 * 契約：
 * - UI 只依賴此介面，不接觸任何資料層型別（Dao／Api／NewPipe）。
 * - 所有錯誤（除非空種子等無操作狀況）一律以 [Result.failure] 封裝回傳，
 *   成功時 [Result.success] 裝載排序後的最終推薦清單。
 * - [seeds] 為推薦種子：實作端會排除「已在播放清單中的 videoId」與種子自身，
 *   故回傳清單不會包含使用者已收藏或種子本身的歌曲。
 * - 種子為空 → 直接回傳空清單（不觸網）。
 *
 * 實作位於 core:data（`RecommendationRepositoryImpl`＋`RelatedStreamsDataSource`）。
 */
interface RecommendationRepository {

    /**
     * 以 [seeds] 為種子抓取「為你推薦」。
     *
     * @param seeds 種子歌曲（通常由 `PlaylistRepository.observeRecentItems` 供應，
     *              即最近加入播放清單的歌曲）。實作端會排除其中已在播放清單的歌曲。
     * @param limit 最多回傳的推薦筆數。
     * @return [Result.success]＝排序後的推薦清單（可能為空）；
     *         [Result.failure]＝全部種子抓取皆失敗（單一失敗種子視同無相關歌曲，不影響整體）。
     */
    suspend fun recommendationsFor(
        seeds: List<PlaylistItem>,
        limit: Int
    ): Result<List<VideoResult>>
}
package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 觀察「最近加入播放清單」的歌曲（跨全部播放清單、依 addedAt 最新在前、videoId 去重）。
 *
 * 薄轉發 [PlaylistRepository.observeRecentItems]，讓 ViewModel 只依賴 UseCase、
 * 不直接拿 Repository 介面。通常與 [FetchRecommendationsUseCase.SEED_LIMIT]
 * 搭配當作「為你推薦」的種子來源。
 */
class ObserveRecentPlaylistItemsUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    operator fun invoke(limit: Int): Flow<List<PlaylistItem>> =
        repository.observeRecentItems(limit)
}
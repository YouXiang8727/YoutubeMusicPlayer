package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchVideosUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    /**
     * 初次搜尋或載入下一頁。
     * @param continuationToken null = 初次搜尋；非 null = 以該 token 載入續頁。
     *                           token 為機密性字串，不做 trim、原樣傳遞。
     */
    suspend operator fun invoke(
        query: String,
        continuationToken: String? = null
    ): Result<VideoSearchPage> = repository.search(query.trim(), continuationToken)
}

class FetchTrendingSongsUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    /**
     * 抓取台灣官方熱門音樂榜單（YouTube Music Global Charts「台灣百大熱門音樂影片」，
     * 內部分頁聚合至整份，回傳完整清單）。
     * @param region 榜單區域（目前僅台灣）
     */
    suspend operator fun invoke(region: ChartRegion): Result<List<VideoResult>> =
        repository.fetchTrendingSongs(region)
}

class CreatePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(name: String) = repository.createPlaylist(name.trim())
}

class RenamePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long, name: String) = repository.renamePlaylist(id, name.trim())
}

class DeletePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long) = repository.deletePlaylist(id)
}

class ObservePlaylistsUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<Playlist>> = repository.observeAllPlaylists()
}

class ObservePlaylistItemsUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    operator fun invoke(playlistId: Long): Flow<List<PlaylistItem>> =
        repository.observePlaylistItems(playlistId)
}

class AddToPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, item: PlaylistItem) =
        repository.addItem(playlistId, item)
}

class RemoveFromPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, videoId: String) =
        repository.removeItem(playlistId, videoId)
}

class ClearPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long) = repository.clearPlaylist(playlistId)
}

class ShufflePlayPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long): PlaylistItem? =
        repository.getRandomItem(playlistId)
}
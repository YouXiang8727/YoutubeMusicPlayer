package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchVideosUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(query: String): Result<List<VideoResult>> =
        repository.search(query.trim())
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
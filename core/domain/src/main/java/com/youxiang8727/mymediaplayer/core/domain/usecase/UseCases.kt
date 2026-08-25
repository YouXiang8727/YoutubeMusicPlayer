package com.youxiang8727.mymediaplayer.core.domain.usecase

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

class ObservePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<PlaylistItem>> = repository.observeAll()
}

class AddToPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(item: PlaylistItem) = repository.add(item)
}

class RemoveFromPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(videoId: String) = repository.remove(videoId)
}

class ClearPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke() = repository.clear()
}

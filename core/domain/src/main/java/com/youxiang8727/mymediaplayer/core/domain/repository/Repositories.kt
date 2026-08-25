package com.youxiang8727.mymediaplayer.core.domain.repository

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    suspend fun search(query: String): Result<List<VideoResult>>
}

interface PlaylistRepository {
    fun observeAll(): Flow<List<PlaylistItem>>
    suspend fun add(item: PlaylistItem)
    suspend fun remove(videoId: String)
    suspend fun clear()
}

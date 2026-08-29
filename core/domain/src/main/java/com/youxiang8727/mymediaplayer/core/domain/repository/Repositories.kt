package com.youxiang8727.mymediaplayer.core.domain.repository

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    /**
     * 執行搜尋或載入下一頁。
     * @param continuationToken null = 初次搜尋；非 null = 以該 token 載入續頁
     */
    suspend fun search(query: String, continuationToken: String? = null): Result<VideoSearchPage>
}

interface PlaylistRepository {
    fun observeAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>>
    suspend fun addItem(playlistId: Long, item: PlaylistItem)
    suspend fun removeItem(playlistId: Long, videoId: String)
    suspend fun clearPlaylist(playlistId: Long)
    suspend fun getRandomItem(playlistId: Long): PlaylistItem?
}

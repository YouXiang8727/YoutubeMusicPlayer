package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistEntity
import com.youxiang8727.mymediaplayer.core.data.local.toDomain
import com.youxiang8727.mymediaplayer.core.data.local.toEntity
import com.youxiang8727.mymediaplayer.core.data.remote.YoutubeDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val dataSource: YoutubeDataSource
) : VideoRepository {
    override suspend fun search(query: String): Result<List<VideoResult>> =
        runCatching { dataSource.search(query) }
}

@Singleton
class AudioStreamRepositoryImpl @Inject constructor(
    private val fallbackResolver: FallbackStreamResolver
) : AudioStreamRepository {
    override suspend fun resolveAudioUrl(videoId: String): Result<String> =
        fallbackResolver.resolve(videoId)
}

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao
) : PlaylistRepository {

    // ── 播放清單 ──

    override fun observeAllPlaylists(): Flow<List<Playlist>> =
        dao.observeAllPlaylists().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun renamePlaylist(playlistId: Long, newName: String) =
        dao.updatePlaylist(playlistId, newName, System.currentTimeMillis())

    override suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylistWithItemsCascade(playlistId)
        dao.deletePlaylist(playlistId)
    }

    // ── 項目 ──

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
        dao.observePlaylistItems(playlistId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addItem(playlistId: Long, item: PlaylistItem) =
        dao.insertItem(item.copy(playlistId = playlistId).toEntity())

    override suspend fun removeItem(playlistId: Long, videoId: String) =
        dao.deleteItem(playlistId, videoId)

    override suspend fun clearPlaylist(playlistId: Long) =
        dao.clearPlaylist(playlistId)

    // ── 隨機 ──

    override suspend fun getRandomItem(playlistId: Long): PlaylistItem? =
        dao.getRandomItem(playlistId)?.toDomain()
}

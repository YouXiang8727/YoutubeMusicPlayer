package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.toDomain
import com.youxiang8727.mymediaplayer.core.data.local.toEntity
import com.youxiang8727.mymediaplayer.core.data.remote.YoutubeDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver
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

/**
 * 音訊串流解析的領域埠實作，供播放服務注入。
 * 內部交給多層 fallback 解析器（NewPipe → InnerTube → Piped），domain 不感知來源細節。
 */
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

    override fun observeAll(): Flow<List<PlaylistItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun add(item: PlaylistItem) = dao.insert(item.toEntity())

    override suspend fun remove(videoId: String) = dao.deleteById(videoId)

    override suspend fun clear() = dao.clearAll()
}

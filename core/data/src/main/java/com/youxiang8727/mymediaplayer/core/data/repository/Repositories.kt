package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistEntity
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistItemEntity
import com.youxiang8727.mymediaplayer.core.data.local.toDomain
import com.youxiang8727.mymediaplayer.core.data.local.toEntity
import com.youxiang8727.mymediaplayer.core.data.remote.TrendingPlaylistDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.YoutubeDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver
import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val dataSource: YoutubeDataSource,
    private val trendingDataSource: TrendingPlaylistDataSource
) : VideoRepository {
    override suspend fun search(query: String, continuationToken: String?): Result<VideoSearchPage> =
        runCatching { dataSource.search(query, continuationToken) }

    override suspend fun fetchTrendingSongs(region: ChartRegion): Result<List<VideoResult>> =
        runCatching { trendingDataSource.fetch(region).getOrThrow() }
}

@Singleton
class AudioStreamRepositoryImpl @Inject constructor(
    private val fallbackResolver: FallbackStreamResolver
) : AudioStreamRepository {
    override suspend fun resolveAudioUrl(videoId: String, force: Boolean): Result<String> =
        fallbackResolver.resolve(videoId, force)
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

    // ── 播放失敗標記 ──

    override suspend fun markStreamFailed(videoId: String, failedAt: Long) =
        dao.markStreamFailed(videoId, failedAt)

    override suspend fun clearStreamFailed(videoId: String) =
        dao.clearStreamFailed(videoId)

    // ── 最近加入（為你推薦種子 / 已知 videoId 快照）──

    override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> =
        dao.observeRecentItems(limit).map { entities ->
            // SQL 已依 addedAt DESC 排序並 LIMIT；此處依 videoId 去重保留最新一筆
            entities.map { it.toDomain() }.distinctBy { it.videoId }
        }

    // ── 隨機 ──

    override suspend fun getRandomItem(playlistId: Long): PlaylistItem? =
        dao.getRandomItem(playlistId)?.toDomain()

    // ── 匯出/匯入 ──

    override suspend fun exportPlaylistAsJson(playlistId: Long): String? {
        val playlistEntity = dao.observeAllPlaylists()
            .first()
            .find { it.id == playlistId } ?: return null

        val items = dao.getAllItems()
            .filter { it.playlistId == playlistId }
            .sortedBy { it.addedAt }

        val json = buildJsonObject {
            put("version", kotlinx.serialization.json.JsonPrimitive(1))
            put("exportedAt", kotlinx.serialization.json.JsonPrimitive(
                Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)
            ))
            put("playlist", buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive(playlistEntity.name))
                put("items", buildJsonArray {
                    items.forEach { item ->
                        add(buildJsonObject {
                            put("videoId", kotlinx.serialization.json.JsonPrimitive(item.videoId))
                            put("title", kotlinx.serialization.json.JsonPrimitive(item.title))
                            put("thumbnailUrl", kotlinx.serialization.json.JsonPrimitive(item.thumbnailUrl))
                            put("channel", kotlinx.serialization.json.JsonPrimitive(item.channel))
                            item.duration?.let { put("duration", kotlinx.serialization.json.JsonPrimitive(it)) }
                        })
                    }
                })
            })
        }
        return json.toString()
    }

    override suspend fun importPlaylistFromJson(json: String): Long? {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val playlistObj = root["playlist"]?.jsonObject ?: return null
            val name = playlistObj["name"]?.jsonPrimitive?.content ?: return null
            val itemsArray = playlistObj["items"]?.jsonArray ?: return null

            // Create playlist
            val playlistId = dao.insertPlaylist(PlaylistEntity(name = name))

            // Insert items
            itemsArray.forEach { element ->
                val item = element.jsonObject
                val videoId = item["videoId"]?.jsonPrimitive?.content ?: return@forEach
                val title = item["title"]?.jsonPrimitive?.content ?: return@forEach
                val thumbnailUrl = item["thumbnailUrl"]?.jsonPrimitive?.content ?: ""
                val channel = item["channel"]?.jsonPrimitive?.contentOrNull ?: ""
                val duration = item["duration"]?.jsonPrimitive?.contentOrNull

                dao.insertItem(
                    PlaylistItemEntity(
                        videoId = videoId,
                        title = title,
                        thumbnailUrl = thumbnailUrl,
                        channel = channel,
                        duration = duration,
                        playlistId = playlistId
                    )
                )
            }
            playlistId
        } catch (e: Exception) {
            null
        }
    }
}

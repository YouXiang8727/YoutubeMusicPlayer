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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 本版本 kotlinx-serialization 無內建 jsonObjectOrNull；沿用專案既有慣例（見 InnerTubeStreamSource）。 */
private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

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

        return buildJsonObject {
            put("version", JsonPrimitive(1))
            put("exportedAt", JsonPrimitive(nowIsoString()))
            put("playlist", buildJsonObject {
                put("name", JsonPrimitive(playlistEntity.name))
                put("items", buildJsonArray {
                    items.forEach { add(itemToJson(it)) }
                })
            })
        }.toString()
    }

    override suspend fun exportAllPlaylistsAsJson(): String? {
        val playlists = dao.observeAllPlaylists().first()
        if (playlists.isEmpty()) return null

        val allItems = dao.getAllItems()
        return buildJsonObject {
            put("version", JsonPrimitive(2))
            put("exportedAt", JsonPrimitive(nowIsoString()))
            put("playlists", buildJsonArray {
                playlists.forEach { playlistEntity ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(playlistEntity.name))
                        put("items", buildJsonArray {
                            allItems
                                .filter { it.playlistId == playlistEntity.id }
                                .sortedBy { it.addedAt }
                                .forEach { add(itemToJson(it)) }
                        })
                    })
                }
            })
        }.toString()
    }

    override suspend fun importPlaylistFromJson(json: String): Long? {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val single = root["playlist"]?.jsonObject
            if (single != null) {
                // v1：單一歌單
                return importSinglePlaylist(single).takeIf { it != SKIP_SENTINEL }
            }

            // v2：多歌單 bundle；逐筆跳過無效項目，回傳第一個成功建立的 ID
            val bundles = root["playlists"]?.jsonArray ?: return null
            var firstCreated: Long? = null
            bundles.forEach { element ->
                val id = importSinglePlaylist(element.jsonObjectOrNull() ?: return@forEach)
                if (id != SKIP_SENTINEL && firstCreated == null) {
                    firstCreated = id
                }
            }
            firstCreated
        } catch (e: Exception) {
            null
        }
    }

    /** 建立單一歌單（v1 / v2 共用）。name 缺失時回傳 [SKIP_SENTINEL] 表示跳過該筆。 */
    private suspend fun importSinglePlaylist(playlistObj: JsonObject): Long {
        val name = playlistObj["name"]?.jsonPrimitive?.content ?: return SKIP_SENTINEL
        val itemsArray = playlistObj["items"]?.jsonArray ?: emptyList()
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = name))

        itemsArray.forEach { element ->
            val item = element.jsonObjectOrNull() ?: return@forEach
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
        return playlistId
    }

    private fun itemToJson(item: PlaylistItemEntity): JsonObject =
        buildJsonObject {
            put("videoId", JsonPrimitive(item.videoId))
            put("title", JsonPrimitive(item.title))
            put("thumbnailUrl", JsonPrimitive(item.thumbnailUrl))
            put("channel", JsonPrimitive(item.channel))
            item.duration?.let { put("duration", JsonPrimitive(it)) }
        }

    private fun nowIsoString(): String =
        Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

    private companion object {
        /** importSinglePlaylist 的跳過哨兵：該筆（如缺 name）不匯入、不視為失敗。 */
        const val SKIP_SENTINEL = -1L
    }
}

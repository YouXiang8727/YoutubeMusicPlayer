package com.youxiang8727.mymediaplayer.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.youxiang8727.mymediaplayer.core.data.local.AppDatabase
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistEntity
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistItemEntity
import com.youxiang8727.mymediaplayer.core.data.local.toDomain
import com.youxiang8727.mymediaplayer.core.data.local.toEntity
import com.youxiang8727.mymediaplayer.core.data.remote.TrendingPlaylistDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.YoutubeDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver
import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
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
class PlaylistRepositoryImpl : PlaylistRepository {

    private val dao: PlaylistDao
    private val runInTransaction: suspend (block: suspend () -> Unit) -> Unit

    @Inject
    constructor(dao: PlaylistDao, database: AppDatabase) {
        this.dao = dao
        // Replace 決策需要交易性「刪除既有歌單＋重建」：以 Room withTransaction 確保
        // 中途失敗即整體 rollback，不留半套狀態。
        this.runInTransaction = { block -> database.withTransaction { block() } }
    }

    /**
     * 測試專用建構子（Fake Dao 情境）：Fake 無真實 SQL 交易，直接執行 block。
     * 正式路徑一律走上方 [@Inject] 建構子（注入 [AppDatabase] 提供 withTransaction）。
     */
    internal constructor(dao: PlaylistDao) {
        this.dao = dao
        this.runInTransaction = { block -> block() }
    }

    // ── 播放清單 ──

    override fun observeAllPlaylists(): Flow<List<Playlist>> =
        dao.observeAllPlaylists().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String): Long {
        val id = dao.insertPlaylist(PlaylistEntity(name = name))
        // playlists.name 唯一索引：衝突時 IGNORE 回傳 -1（與 SKIP_SENTINEL 同值）——
        // 語意化錯誤信號，交由 UI 辨識「重名」。
        if (id == SKIP_SENTINEL) throw PlaylistNameConflictException(name)
        return id
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        // 前置檢查：newName 已被「其他」歌單使用 → 直接拋語意化例外（改名為自己目前名稱屬合法）
        val existingId = dao.findPlaylistIdByName(newName)
        if (existingId != null && existingId != playlistId) {
            throw PlaylistNameConflictException(newName)
        }
        try {
            dao.updatePlaylist(playlistId, newName, System.currentTimeMillis())
        } catch (e: SQLiteConstraintException) {
            // 雙保險：前置檢查與實際寫入之間若發生 race，唯一索引仍會擋下重名，
            // 避免未包裝的底層例外直接炸出
            throw PlaylistNameConflictException(newName)
        }
    }

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

    override suspend fun importPlaylistFromJson(
        json: String,
        onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
    ): PlaylistImportResult? {
        // v1 = 單一 bundle；v2 = 多 bundle。結構無法辨識（無 playlist/playlists 鍵、
        // 非 JSON、parse 失敗）→ null（維持既有語意）。
        val bundles = parseBundles(json) ?: return null

        // 預掃：以「初始快照」計算衝突總數（bundle 內與既有歌單重名之筆數，逐筆計算）。
        // 之後實際依序處理時可能因 bundle 內互撞多出衝突（見邊角防護），conflictIndex 不受影響。
        val initialNames = dao.observeAllPlaylists().first().map { it.name }.toSet()
        val totalConflicts = bundles.count { bundle ->
            val name = bundle["name"]?.jsonPrimitive?.contentOrNull
            name != null && name in initialNames
        }

        var created = 0
        var replaced = 0
        var keptBoth = 0
        var cancelled = false
        var conflictIndex = 0

        for (bundle in bundles) {
            val name = bundle["name"]?.jsonPrimitive?.contentOrNull
                ?: continue // 缺 name：無效項目，靜默跳過（非衝突）
            val items = parseItems(bundle["items"]?.jsonArray ?: emptyList())

            // 衝突偵測以真實唯一索引為準：重名時 insert（IGNORE）回傳 -1。
            val insertedId = dao.insertPlaylist(PlaylistEntity(name = name))
            if (insertedId != SKIP_SENTINEL) {
                insertItems(insertedId, items)
                created++
                continue
            }

            // 衝突：交由 UI 決策（流程於此暫停等待 callback 回傳）
            conflictIndex++
            val decision = onConflict(
                ImportConflictInfo(
                    name = name,
                    conflictIndex = conflictIndex,
                    totalConflicts = totalConflicts
                )
            )
            when (decision) {
                ImportConflictDecision.Replace -> {
                    val existingId = dao.findPlaylistIdByName(name)
                    if (existingId == null) {
                        // 競態防護：既有歌單已被移除 → 視同一般建立
                        val id = dao.insertPlaylist(PlaylistEntity(name = name))
                        if (id != SKIP_SENTINEL) {
                            insertItems(id, items)
                            created++
                        }
                    } else {
                        replaced += replacePlaylist(existingId, name, items)
                    }
                }
                ImportConflictDecision.KeepBoth -> {
                    // 以「原名 (2)」「原名 (3)」...逐次尋找未使用名稱（查 DB 確保不撞既有/新名稱）
                    val candidate = findAvailableKeepBothName(name)
                    if (candidate != null) {
                        val id = dao.insertPlaylist(PlaylistEntity(name = candidate))
                        if (id != SKIP_SENTINEL) {
                            insertItems(id, items)
                            keptBoth++
                        }
                    }
                    // 後綴全滿（極端案例）：靜默跳過該筆，不回報錯誤
                }
                ImportConflictDecision.Cancel -> {
                    cancelled = true
                    break // 中止後續所有歌單處理，先前已匯入者保留
                }
            }
        }

        return PlaylistImportResult(
            created = created,
            replaced = replaced,
            keptBoth = keptBoth,
            cancelled = cancelled
        )
    }

    /** 解析 JSON 為 bundle 清單；結構無法辨識回傳 null（v1 視為單一 bundle）。 */
    private fun parseBundles(json: String): List<JsonObject>? {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val single = root["playlist"]?.jsonObject
            if (single != null) {
                listOf(single)
            } else {
                root["playlists"]?.jsonArray?.mapNotNull { it.jsonObjectOrNull() }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 解析 bundle 的 items 陣列：跳過缺 videoId/title 的無效項目。 */
    private fun parseItems(elements: List<JsonElement>): List<PlaylistItemEntity> =
        elements.mapNotNull { element ->
            val item = element.jsonObjectOrNull() ?: return@mapNotNull null
            val videoId = item["videoId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            PlaylistItemEntity(
                videoId = videoId,
                title = title,
                thumbnailUrl = item["thumbnailUrl"]?.jsonPrimitive?.content ?: "",
                channel = item["channel"]?.jsonPrimitive?.contentOrNull ?: "",
                duration = item["duration"]?.jsonPrimitive?.contentOrNull,
                playlistId = 0 // 佔位；建立歌單後以實際 id copy
            )
        }

    private suspend fun insertItems(playlistId: Long, items: List<PlaylistItemEntity>) {
        items.forEach { dao.insertItem(it.copy(playlistId = playlistId)) }
    }

    /**
     * Replace 決策執行：交易性「刪除既有歌單＋其 items（沿用級聯刪除）→ 原名重建 → 寫入 items」。
     * 中途任何失敗由 withTransaction 整體 rollback，不留半套狀態。
     * @return 1 = 成功取代；0 = 理論上不發生的重建失敗（不產生孤兒項目）。
     */
    private suspend fun replacePlaylist(
        existingId: Long,
        name: String,
        items: List<PlaylistItemEntity>
    ): Int {
        var newId = SKIP_SENTINEL
        runInTransaction {
            dao.deletePlaylistWithItemsCascade(existingId)
            dao.deletePlaylist(existingId)
            newId = dao.insertPlaylist(PlaylistEntity(name = name))
            if (newId != SKIP_SENTINEL) {
                insertItems(newId, items)
            }
        }
        return if (newId != SKIP_SENTINEL) 1 else 0
    }

    /**
     * KeepBoth 決策執行：尋找未使用的「原名 (n)」名稱（n 自 2 起）。
     * 上限 [KEEP_BOTH_MAX_SUFFIX]，全滿回傳 null（跳過該筆）。
     */
    private suspend fun findAvailableKeepBothName(base: String): String? {
        for (suffix in 2..KEEP_BOTH_MAX_SUFFIX) {
            val candidate = "$base ($suffix)"
            if (dao.findPlaylistIdByName(candidate) == null) return candidate
        }
        return null
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
        /**
         * insert 衝突哨兵：playlists.name 唯一索引在 insert（IGNORE）時回傳 -1——
         * 代表「該筆名稱已存在」（含 bundle 內互撞），由匯入流程轉為衝突決策。
         */
        const val SKIP_SENTINEL = -1L

        /** KeepBoth 名稱後綴尋找上限（`name (2)`..`name (1000)`）；全滿視為極端案例跳過該筆。 */
        const val KEEP_BOTH_MAX_SUFFIX = 1000
    }
}

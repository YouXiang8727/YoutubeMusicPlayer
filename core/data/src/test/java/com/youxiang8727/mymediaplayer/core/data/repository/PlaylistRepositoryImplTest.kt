package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistEntity
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistItemEntity
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 團隊規範要求：core:data Repository 對 Fake Dao 至少一組測試。
 * 以 in-memory StateFlow 模擬 Room 行為（不含 SQL），驗證 Entity ↔ Domain mapping 與 CRUD 委派。
 */
class PlaylistRepositoryImplTest {

    /** Fake Dao：以 MutableStateFlow 模擬 playlists + playlist_items 表。 */
    private class FakePlaylistDao : PlaylistDao {
        val playlistTable = MutableStateFlow<List<PlaylistEntity>>(emptyList())
        val itemTable = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())
        private var nextId = 1L

        override fun observeAllPlaylists(): Flow<List<PlaylistEntity>> = playlistTable.map { rows ->
            rows.sortedByDescending { it.createdAt }
        }

        override suspend fun insertPlaylist(entity: PlaylistEntity): Long {
            val id = nextId++
            playlistTable.value = playlistTable.value + entity.copy(id = id)
            return id
        }

        override suspend fun updatePlaylist(id: Long, name: String, updatedAt: Long) {
            playlistTable.value = playlistTable.value.map {
                if (it.id == id) it.copy(name = name, updatedAt = updatedAt) else it
            }
        }

        override suspend fun deletePlaylist(id: Long) {
            playlistTable.value = playlistTable.value.filterNot { it.id == id }
        }

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
            itemTable.map { rows ->
                rows.filter { it.playlistId == playlistId }.sortedByDescending { it.addedAt }
            }

        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItemEntity>> =
            itemTable.map { rows ->
                // 模擬 SQL：全表 addedAt DESC + LIMIT（去重在 Repository 層）
                rows.sortedByDescending { it.addedAt }.take(limit)
            }

        override suspend fun insertItem(entity: PlaylistItemEntity) {
            itemTable.value = itemTable.value.filterNot {
                it.videoId == entity.videoId && it.playlistId == entity.playlistId
            } + entity
        }

        override suspend fun deleteItem(playlistId: Long, videoId: String) {
            itemTable.value = itemTable.value.filterNot {
                it.playlistId == playlistId && it.videoId == videoId
            }
        }

        override suspend fun clearPlaylist(playlistId: Long) {
            itemTable.value = itemTable.value.filterNot { it.playlistId == playlistId }
        }

        override suspend fun getRandomItem(playlistId: Long): PlaylistItemEntity? =
            itemTable.value.filter { it.playlistId == playlistId }.randomOrNull()

        override suspend fun getAllItems(): List<PlaylistItemEntity> = itemTable.value

        override suspend fun deletePlaylistWithItemsCascade(playlistId: Long) {
            itemTable.value = itemTable.value.filterNot { it.playlistId == playlistId }
        }

        override suspend fun markStreamFailed(videoId: String, failedAt: Long) {
            itemTable.value = itemTable.value.map {
                if (it.videoId == videoId) it.copy(streamFailedAt = failedAt) else it
            }
        }

        override suspend fun clearStreamFailed(videoId: String) {
            itemTable.value = itemTable.value.map {
                if (it.videoId == videoId) it.copy(streamFailedAt = null) else it
            }
        }
    }

    private fun item(
        videoId: String,
        playlistId: Long = 1L,
        addedAt: Long = 0L,
        duration: String? = null
    ) = PlaylistItem(
        videoId = videoId,
        title = "Title $videoId",
        thumbnailUrl = "https://img/$videoId",
        channel = "Channel $videoId",
        duration = duration,
        addedAt = addedAt,
        playlistId = playlistId
    )

    @Test
    fun `observeAllPlaylists 將 Entity 映射為 Domain model`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        // 手動插入不同 createdAt 確保排序可預期（ createdAt DESC ）
        val now = 1_000_000L
        dao.playlistTable.value = listOf(
            PlaylistEntity(id = 1, name = "我的最愛", createdAt = now, updatedAt = now),
            PlaylistEntity(id = 2, name = "稍後再聽", createdAt = now + 1, updatedAt = now + 1)
        )

        val playlists = repository.observeAllPlaylists().first()
        assertEquals(2, playlists.size)
        assertEquals("稍後再聽", playlists.first().name) // createdAt DESC
    }

    @Test
    fun `observePlaylistItems 依 playlistId 過濾`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id1 = repository.createPlaylist("清單1")
        val id2 = repository.createPlaylist("清單2")
        repository.addItem(id1, item("v1", playlistId = id1, addedAt = 100L))
        repository.addItem(id1, item("v2", playlistId = id1, addedAt = 200L))
        repository.addItem(id2, item("v3", playlistId = id2, addedAt = 300L))

        val items1 = repository.observePlaylistItems(id1).first()
        assertEquals(listOf("v2", "v1"), items1.map { it.videoId }) // addedAt DESC

        val items2 = repository.observePlaylistItems(id2).first()
        assertEquals(listOf("v3"), items2.map { it.videoId })
    }

    @Test
    fun `renamePlaylist 更新名稱`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("舊名")
        repository.renamePlaylist(id, "新名")

        val playlists = repository.observeAllPlaylists().first()
        assertEquals("新名", playlists.first { it.id == id }.name)
    }

    @Test
    fun `deletePlaylist 級聯刪除項目`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("待刪")
        repository.addItem(id, item("v1", playlistId = id))
        repository.deletePlaylist(id)

        val playlists = repository.observeAllPlaylists().first()
        assertTrue(playlists.isEmpty())
        assertTrue(dao.itemTable.value.isEmpty())
    }

    @Test
    fun `removeItem 依 videoId 刪除`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("清單")
        repository.addItem(id, item("v1", playlistId = id))
        repository.addItem(id, item("v2", playlistId = id))

        repository.removeItem(id, "v1")

        val remaining = repository.observePlaylistItems(id).first()
        assertEquals(listOf("v2"), remaining.map { it.videoId })
    }

    @Test
    fun `clearPlaylist 清空指定清單`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("清單")
        repository.addItem(id, item("v1", playlistId = id))

        repository.clearPlaylist(id)

        assertTrue(repository.observePlaylistItems(id).first().isEmpty())
    }

    @Test
    fun `addItem 帶 duration 時 Entity 與 Domain mapping 皆保留`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("清單")
        repository.addItem(id, item("v1", playlistId = id, duration = "3:45"))

        val items = repository.observePlaylistItems(id).first()
        assertEquals("3:45", items.single().duration)
        // toEntity mapping 一併保留（Room 持久化面）
        assertEquals("3:45", dao.itemTable.value.single().duration)
    }

    @Test
    fun `getRandomItem 回傳隨機項目`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("清單")
        repository.addItem(id, item("v1", playlistId = id))

        val random = repository.getRandomItem(id)
        assertEquals("v1", random?.videoId)
    }

    @Test
    fun `getRandomItem 空清單回傳 null`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("空清單")

        assertNull(repository.getRandomItem(id))
    }

    @Test
    fun `markStreamFailed 標記失敗時間戳並反映在 observePlaylistItems`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("清單")
        repository.addItem(id, item("v1", playlistId = id))
        assertNull(repository.observePlaylistItems(id).first().single().streamFailedAt)

        repository.markStreamFailed("v1", 1234L)

        val items = repository.observePlaylistItems(id).first()
        assertEquals(1234L, items.single().streamFailedAt)
        // toEntity mapping 一併保留（Room 持久化面）
        assertEquals(1234L, dao.itemTable.value.single().streamFailedAt)
    }

    @Test
    fun `clearStreamFailed 清除失敗標記`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val id = repository.createPlaylist("清單")
        repository.addItem(id, item("v1", playlistId = id))
        repository.markStreamFailed("v1", 5678L)
        assertEquals(5678L, repository.observePlaylistItems(id).first().single().streamFailedAt)

        repository.clearStreamFailed("v1")

        assertNull(repository.observePlaylistItems(id).first().single().streamFailedAt)
        assertNull(dao.itemTable.value.single().streamFailedAt)
    }

    // ── observeRecentItems（為你推薦種子）──

    @Test
    fun `observeRecentItems 跨全部清單依 addedAt 降序`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)
        dao.itemTable.value = listOf(
            PlaylistItemEntity(
                videoId = "v1", title = "T1", thumbnailUrl = "", playlistId = 1L, addedAt = 100L
            ),
            PlaylistItemEntity(
                videoId = "v2", title = "T2", thumbnailUrl = "", playlistId = 2L, addedAt = 300L
            ),
            PlaylistItemEntity(
                videoId = "v3", title = "T3", thumbnailUrl = "", playlistId = 1L, addedAt = 200L
            )
        )

        val recent = repository.observeRecentItems(10).first()

        // 跨清單合併，addedAt DESC（v2=300 → v3=200 → v1=100）
        assertEquals(listOf("v2", "v3", "v1"), recent.map { it.videoId })
    }

    @Test
    fun `observeRecentItems 依 videoId 去重且保留最新一筆`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)
        dao.itemTable.value = listOf(
            PlaylistItemEntity(
                videoId = "v1", title = "舊名稱", thumbnailUrl = "", playlistId = 1L, addedAt = 100L
            ),
            // 同 videoId 在另一清單，addedAt 較新 → 應保留（新名稱）
            PlaylistItemEntity(
                videoId = "v1", title = "新名稱", thumbnailUrl = "", playlistId = 2L, addedAt = 400L
            ),
            PlaylistItemEntity(
                videoId = "v2", title = "T2", thumbnailUrl = "", playlistId = 1L, addedAt = 300L
            )
        )

        val recent = repository.observeRecentItems(10).first()

        assertEquals(listOf("v1", "v2"), recent.map { it.videoId }) // v1 只出現一次
        assertEquals("新名稱", recent.first().title) // 保留最新一筆（addedAt=400）
    }

    @Test
    fun `observeRecentItems 套用 limit 上限`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)
        dao.itemTable.value = listOf(
            PlaylistItemEntity(
                videoId = "v1", title = "T1", thumbnailUrl = "", playlistId = 1L, addedAt = 100L
            ),
            PlaylistItemEntity(
                videoId = "v2", title = "T2", thumbnailUrl = "", playlistId = 1L, addedAt = 200L
            ),
            PlaylistItemEntity(
                videoId = "v3", title = "T3", thumbnailUrl = "", playlistId = 1L, addedAt = 300L
            )
        )

        val recent = repository.observeRecentItems(2).first()

        assertEquals(listOf("v3", "v2"), recent.map { it.videoId }) // 只取 2 筆
    }

    @Test
    fun `observeRecentItems 空資料庫回傳空清單`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val recent = repository.observeRecentItems(5).first()

        assertTrue(recent.isEmpty())
    }

    // ── 匯出全部（v2 bundle）──

    private fun playlistNamesOf(root: JsonElement): List<String> =
        root.jsonObject["playlists"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

    private fun itemVideoIdsOf(playlistJson: JsonElement): List<String> =
        playlistJson.jsonObject["items"]!!.jsonArray.map { it.jsonObject["videoId"]!!.jsonPrimitive.content }

    @Test
    fun `exportAllPlaylistsAsJson 產出 v2 JSON 含全部歌單與依 addedAt 升序的項目`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)
        dao.playlistTable.value = listOf(
            PlaylistEntity(id = 1, name = "清單A", createdAt = 100L, updatedAt = 100L),
            PlaylistEntity(id = 2, name = "清單B", createdAt = 200L, updatedAt = 200L)
        )
        dao.itemTable.value = listOf(
            PlaylistItemEntity(
                videoId = "v1", title = "Title v1", thumbnailUrl = "https://img/v1",
                channel = "Channel v1", duration = "3:45", playlistId = 1L, addedAt = 200L
            ),
            PlaylistItemEntity(
                videoId = "v2", title = "Title v2", thumbnailUrl = "https://img/v2",
                channel = "Channel v2", playlistId = 1L, addedAt = 100L
            ),
            PlaylistItemEntity(
                videoId = "v3", title = "Title v3", thumbnailUrl = "", playlistId = 2L, addedAt = 50L
            )
        )

        val json = repository.exportAllPlaylistsAsJson()

        assertNotNull(json)
        val root = Json.parseToJsonElement(json!!).jsonObject
        assertEquals("2", root["version"]!!.jsonPrimitive.content)
        // ISO-8601 時間戳
        assertTrue(root.containsKey("exportedAt") && root["exportedAt"]!!.jsonPrimitive.content.isNotBlank())

        // 依觀察順序（observeAllPlaylists → createdAt DESC → 清單B 在前）
        assertEquals(listOf("清單B", "清單A"), playlistNamesOf(root))
        val playlists = root["playlists"]!!.jsonArray

        // B 的項目依 addedAt 升序（單筆）
        assertEquals(listOf("v3"), itemVideoIdsOf(playlists[0]))

        // A 的項目依 addedAt 升序（v2@100 → v1@200），且序列化欄位與 v1 相同
        assertEquals(listOf("v2", "v1"), itemVideoIdsOf(playlists[1]))
        val v1Json = playlists[1].jsonObject["items"]!!.jsonArray[1].jsonObject
        assertEquals("Title v1", v1Json["title"]!!.jsonPrimitive.content)
        assertEquals("https://img/v1", v1Json["thumbnailUrl"]!!.jsonPrimitive.content)
        assertEquals("Channel v1", v1Json["channel"]!!.jsonPrimitive.content)
        assertEquals("3:45", v1Json["duration"]!!.jsonPrimitive.content)
    }

    @Test
    fun `exportAllPlaylistsAsJson 無任何歌單時回傳 null`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        assertNull(repository.exportAllPlaylistsAsJson())
    }

    // ── 匯入 v2 bundle ──

    @Test
    fun `importPlaylistFromJson v2 bundle 建立多個歌單並跳過無效項目`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val json = """
            {
              "version": 2,
              "exportedAt": "2026-01-01T00:00:00Z",
              "playlists": [
                { "name": "匯入A", "items": [
                    { "videoId": "vA1", "title": "TA1", "thumbnailUrl": "https://img/A1", "channel": "歌手A" },
                    { "videoId": "vA2", "title": "TA2", "duration": "2:30" }
                ]},
                { "name": "匯入B", "items": [] },
                { "items": [] }
              ]
            }
        """.trimIndent()

        val firstId = repository.importPlaylistFromJson(json)

        assertNotNull(firstId)
        // 缺 name 的第三筆被跳過（未匯入）
        val playlists = repository.observeAllPlaylists().first()
        assertEquals(setOf("匯入A", "匯入B"), playlists.map { it.name }.toSet())
        assertEquals(2, playlists.size)

        // 第一個回傳 ID = 第一個成功建立的歌單（匯入A）
        val aPlaylistId = playlists.first { it.name == "匯入A" }.id
        assertEquals(aPlaylistId, firstId)

        val aItems = dao.itemTable.value.filter { it.playlistId == aPlaylistId }
        assertEquals(setOf("vA1", "vA2"), aItems.map { it.videoId }.toSet())
        assertEquals("TA1", aItems.first { it.videoId == "vA1" }.title)
        assertEquals("歌手A", aItems.first { it.videoId == "vA1" }.channel)
        // 缺 thumbnailUrl / channel → 回退空字串；duration 保留
        val vA2 = aItems.first { it.videoId == "vA2" }
        assertEquals("", vA2.thumbnailUrl)
        assertEquals("", vA2.channel)
        assertEquals("2:30", vA2.duration)
    }

    @Test
    fun `importPlaylistFromJson v2 全部項目無效時回傳 null`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val json = """
            {
              "version": 2,
              "exportedAt": "2026-01-01T00:00:00Z",
              "playlists": [
                { "items": [] },
                { "items": [] }
              ]
            }
        """.trimIndent()

        assertNull(repository.importPlaylistFromJson(json))
        assertTrue(dao.playlistTable.value.isEmpty())
    }

    @Test
    fun `importPlaylistFromJson 結構無效（無 playlist 也無 playlists）回傳 null`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        assertNull(repository.importPlaylistFromJson("""{"version":99}"""))
        assertTrue(dao.playlistTable.value.isEmpty())
    }

    // ── v1 匯入回歸 ──

    @Test
    fun `importPlaylistFromJson v1 單一格式仍可匯入（回歸）`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        val json = """
            {
              "version": 1,
              "exportedAt": "2026-01-01T00:00:00Z",
              "playlist": {
                "name": "舊版歌單",
                "items": [
                  { "videoId": "v1", "title": "T1", "thumbnailUrl": "https://img/1", "channel": "歌手1", "duration": "4:20" },
                  { "videoId": "v2", "title": "T2" }
                ]
              }
            }
        """.trimIndent()

        val id = repository.importPlaylistFromJson(json)

        assertNotNull(id)
        val playlists = repository.observeAllPlaylists().first()
        assertEquals(listOf("舊版歌單"), playlists.map { it.name })
        assertEquals(id, playlists.single().id)

        val items = dao.itemTable.value.filter { it.playlistId == id }
        assertEquals(setOf("v1", "v2"), items.map { it.videoId }.toSet())
        assertEquals("4:20", items.first { it.videoId == "v1" }.duration)
        // 缺 thumbnailUrl / channel → 回退空字串
        val v2 = items.first { it.videoId == "v2" }
        assertEquals("", v2.thumbnailUrl)
        assertEquals("", v2.channel)
    }
}

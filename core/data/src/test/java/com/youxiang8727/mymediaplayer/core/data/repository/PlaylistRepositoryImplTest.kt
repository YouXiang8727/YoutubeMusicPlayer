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
import org.junit.Assert.assertEquals
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

        override suspend fun deletePlaylistWithItemsCascade(playlistId: Long) {
            itemTable.value = itemTable.value.filterNot { it.playlistId == playlistId }
        }
    }

    private fun item(videoId: String, playlistId: Long = 1L, addedAt: Long = 0L) = PlaylistItem(
        videoId = videoId,
        title = "Title $videoId",
        thumbnailUrl = "https://img/$videoId",
        channel = "Channel $videoId",
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
}

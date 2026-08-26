package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistItemEntity
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 團隊規範要求：core:data Repository 對 Fake Dao 至少一組測試。
 * 以 in-memory StateFlow 模擬 Room 行為（不含 SQL），驗證 Entity ↔ Domain mapping 與 CRUD 委派。
 */
class PlaylistRepositoryImplTest {

    /** Fake Dao：以 MutableStateFlow 模擬 playlist 表。 */
    private class FakePlaylistDao : PlaylistDao {
        val table = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())

        override fun observeAll(): Flow<List<PlaylistItemEntity>> = table.map { rows ->
            rows.sortedByDescending { it.addedAt }
        }

        override suspend fun findById(videoId: String): PlaylistItemEntity? =
            table.value.firstOrNull { it.videoId == videoId }

        override suspend fun insert(item: PlaylistItemEntity) {
            table.value = table.value.filterNot { it.videoId == item.videoId } + item
        }

        override suspend fun deleteById(videoId: String) {
            table.value = table.value.filterNot { it.videoId == videoId }
        }

        override suspend fun clearAll() {
            table.value = emptyList()
        }
    }

    private fun item(videoId: String, addedAt: Long = 0L) = PlaylistItem(
        videoId = videoId,
        title = "Title $videoId",
        thumbnailUrl = "https://img/$videoId",
        channel = "Channel $videoId",
        addedAt = addedAt
    )

    @Test
    fun `observeAll 將 Entity 映射為 Domain model`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)

        repository.add(item("v1", addedAt = 100L))
        repository.add(item("v2", addedAt = 200L))

        val items = repository.observeAll().first()
        assertEquals(listOf("v2", "v1"), items.map { it.videoId }) // addedAt DESC
        assertEquals("Title v2", items.first().title)
        assertEquals("Channel v2", items.first().channel)
    }

    @Test
    fun `remove 依 videoId 刪除`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)
        repository.add(item("v1"))
        repository.add(item("v2"))

        repository.remove("v1")

        val remaining = repository.observeAll().first()
        assertEquals(listOf("v2"), remaining.map { it.videoId })
    }

    @Test
    fun `clear 清空全部`() = runTest {
        val dao = FakePlaylistDao()
        val repository = PlaylistRepositoryImpl(dao)
        repository.add(item("v1"))

        repository.clear()

        assertTrue(repository.observeAll().first().isEmpty())
    }
}

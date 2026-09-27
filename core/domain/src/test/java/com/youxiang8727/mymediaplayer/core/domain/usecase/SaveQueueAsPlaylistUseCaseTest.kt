package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 [SaveQueueAsPlaylistUseCase] 的領域規則：
 * 去重（保留首次出現）＋ `addedAt` 反向遞增以保序（讀取端 `addedAt DESC`）、
 * 空佇列失敗、名稱衝突分支、其他失敗分支。
 */
class SaveQueueAsPlaylistUseCaseTest {

    private class FakePlaylistRepository(
        private val newPlaylistId: Long = 42L,
        private val throwable: Throwable? = null
    ) : PlaylistRepository {
        val receivedNames = mutableListOf<String>()
        val receivedItems = mutableListOf<List<PlaylistItem>>()

        override fun observeAllPlaylists(): Flow<List<Playlist>> =
            MutableStateFlow(emptyList())

        override suspend fun createPlaylist(name: String): Long = 1L

        override suspend fun renamePlaylist(playlistId: Long, newName: String) = Unit

        override suspend fun deletePlaylist(playlistId: Long) = Unit

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
            MutableStateFlow(emptyList())

        override suspend fun addItem(playlistId: Long, item: PlaylistItem) = Unit

        override suspend fun removeItem(playlistId: Long, videoId: String) = Unit

        override suspend fun clearPlaylist(playlistId: Long) = Unit

        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null

        override suspend fun createPlaylistWithItems(name: String, items: List<PlaylistItem>): Long {
            receivedNames += name
            receivedItems += items
            throwable?.let { throw it }
            return newPlaylistId
        }

        override suspend fun markStreamFailed(videoId: String, failedAt: Long) = Unit

        override suspend fun clearStreamFailed(videoId: String) = Unit

        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> =
            MutableStateFlow(emptyList())

        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = null

        override suspend fun exportAllPlaylistsAsJson(): String? = null

        override suspend fun importPlaylistFromJson(
            json: String,
            onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
        ): PlaylistImportResult? = null
    }

    private fun queue(vararg videoIds: String) =
        videoIds.map { PlayQueueItem(videoId = it, title = "Title $it") }

    /** 讀取端 `ORDER BY addedAt DESC`：依 addedAt 降序排列。 */
    private fun readOrder(items: List<PlaylistItem>) =
        items.sortedByDescending { it.addedAt }.map { it.videoId }

    @Test
    fun `正常佇列建立成功且讀出順序等於佇列順序`() = runTest {
        val repo = FakePlaylistRepository(newPlaylistId = 7L)
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val result = useCase("我的佇列", queue("v1", "v2", "v3"))

        val success = result as SaveQueueAsPlaylistResult.Success
        assertEquals(7L, success.playlistId)
        assertEquals("我的佇列", success.playlistName)
        assertEquals(3, success.itemCount)
        assertEquals(0, success.mergedCount)

        val items = repo.receivedItems.single()
        assertEquals("我的佇列", repo.receivedNames.single())
        // 寫入順序 = 佇列順序
        assertEquals(listOf("v1", "v2", "v3"), items.map { it.videoId })
        // 核心（裁決 B）：addedAt 反向遞減 → 以 addedAt DESC 讀出時順序還原為佇列順序
        assertEquals(listOf("v1", "v2", "v3"), readOrder(items))
        assertTrue(items[0].addedAt > items[1].addedAt)
        assertTrue(items[1].addedAt > items[2].addedAt)
    }

    @Test
    fun `佇列有重複時合併且只寫入一筆並保留首次出現`() = runTest {
        val repo = FakePlaylistRepository()
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val result = useCase("佇列", queue("v1", "v2", "v1", "v3", "v2"))

        val success = result as SaveQueueAsPlaylistResult.Success
        assertEquals(3, success.itemCount)
        assertEquals(2, success.mergedCount)

        val items = repo.receivedItems.single()
        assertEquals(listOf("v1", "v2", "v3"), items.map { it.videoId }) // 去重後唯一一列
        // 保留首次出現（佇列中較前的那筆）：title 仍為首次的
        assertEquals(listOf("Title v1", "Title v2", "Title v3"), items.map { it.title })
        assertEquals(listOf("v1", "v2", "v3"), readOrder(items))
    }

    @Test
    fun `佇列縮圖固定空字串且 playlistId 交由 Repository 決定`() = runTest {
        val repo = FakePlaylistRepository()
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        useCase("佇列", queue("v1"))

        val item = repo.receivedItems.single().single()
        assertEquals("", item.thumbnailUrl)
        assertEquals(0L, item.playlistId)
    }

    @Test
    fun `空佇列回傳失敗且不建立任何歌單`() = runTest {
        val repo = FakePlaylistRepository()
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val result = useCase("佇列", emptyList())

        val failure = result as SaveQueueAsPlaylistResult.Failure
        assertTrue(failure.message.isNotBlank())
        assertTrue(repo.receivedNames.isEmpty())
    }

    @Test
    fun `名稱重複回傳 NameConflict 並沿用既有可顯示訊息`() = runTest {
        val repo = FakePlaylistRepository(throwable = PlaylistNameConflictException("我的最愛"))
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val result = useCase("我的最愛", queue("v1"))

        val conflict = result as SaveQueueAsPlaylistResult.NameConflict
        assertEquals("我的最愛", conflict.name)
        assertEquals("已存在同名歌單「我的最愛」", conflict.message)
    }

    @Test
    fun `Repository 丟出其他例外時回傳 Failure 並帶失敗原因`() = runTest {
        val repo = FakePlaylistRepository(throwable = IllegalStateException("資料庫忙碌中"))
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val result = useCase("佇列", queue("v1"))

        val failure = result as SaveQueueAsPlaylistResult.Failure
        assertEquals("資料庫忙碌中", failure.message)
    }

    @Test
    fun `Repository 拋出 CancellationException 時取消訊號原樣穿透（不得轉為 Failure）`() = runTest {
        // CancellationException 繼承 IllegalStateException → RuntimeException → Exception，
        // 若 UseCase 少了 rethrow 分支，這裡就會拿到 Failure（exceptionOrNull() == null）而非往外拋。
        val repo = FakePlaylistRepository(throwable = CancellationException("已取消"))
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val thrown = runCatching { useCase("佇列", queue("v1")) }.exceptionOrNull()

        assertTrue("應讓 CancellationException 往外拋，但實際得到：$thrown", thrown is CancellationException)
        assertEquals("已取消", thrown?.message)
    }

    @Test
    fun `歌單名稱 trim 後才送出 Repository`() = runTest {
        val repo = FakePlaylistRepository()
        val useCase = SaveQueueAsPlaylistUseCase(repo)

        val result = useCase("  我的佇列  ", queue("v1"))

        assertEquals("我的佇列", repo.receivedNames.single())
        assertEquals("我的佇列", (result as SaveQueueAsPlaylistResult.Success).playlistName)
    }
}

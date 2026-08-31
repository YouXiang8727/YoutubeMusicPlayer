package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 涵蓋 [CreatePlaylistUseCase]、[RenamePlaylistUseCase]、[DeletePlaylistUseCase]、
 * [ObservePlaylistsUseCase]、[ObservePlaylistItemsUseCase]、[AddToPlaylistUseCase]、
 * [RemoveFromPlaylistUseCase]、[ClearPlaylistUseCase]、[ShufflePlayPlaylistUseCase] 的純 JVM 單元測試。
 * 使用 [FakePlaylistRepository]（Fake Repository）驗證參數透傳、trim 與回傳透傳。
 */
class PlaylistUseCasesTest {

    private lateinit var repository: FakePlaylistRepository

    @Before
    fun setUp() {
        repository = FakePlaylistRepository()
    }

    // ---- CreatePlaylistUseCase ----

    @Test
    fun `create 會移除名稱前後空白並回傳新 id`() = runTest {
        val useCase = CreatePlaylistUseCase(repository)

        val newId = useCase("  我的歌單  ")

        assertEquals(1L, newId)
        assertEquals("我的歌單", repository.lastCreateName)
    }

    // ---- RenamePlaylistUseCase ----

    @Test
    fun `rename 會移除新名稱前後空白並透傳 id`() = runTest {
        val useCase = RenamePlaylistUseCase(repository)

        useCase(10L, "  改過的名稱  ")

        assertEquals(10L, repository.lastRenameId)
        assertEquals("改過的名稱", repository.lastRenameName)
    }

    // ---- DeletePlaylistUseCase ----

    @Test
    fun `delete 會透傳 playlistId`() = runTest {
        val useCase = DeletePlaylistUseCase(repository)

        useCase(7L)

        assertEquals(7L, repository.deletedId)
    }

    // ---- ObservePlaylistsUseCase ----

    @Test
    fun `observePlaylists 回傳 repository 的串流`() = runTest {
        val playlists = listOf(Playlist(id = 1L, name = "A"), Playlist(id = 2L, name = "B"))
        repository.emitPlaylists(playlists)
        val useCase = ObservePlaylistsUseCase(repository)

        val result = useCase().first()

        assertEquals(playlists, result)
    }

    // ---- ObservePlaylistItemsUseCase ----

    @Test
    fun `observePlaylistItems 回傳該 playlist 的串流且透傳 playlistId`() = runTest {
        val items = listOf(
            PlaylistItem(videoId = "v1", title = "T1", thumbnailUrl = "", playlistId = 5L)
        )
        repository.emitPlaylistItems(5L, items)
        val useCase = ObservePlaylistItemsUseCase(repository)

        val result = useCase(5L).first()

        assertEquals(5L, repository.lastObservedItemsPlaylistId)
        assertEquals(items, result)
    }

    // ---- AddToPlaylistUseCase ----

    @Test
    fun `addItem 透傳 playlistId 與 item`() = runTest {
        val item = PlaylistItem(videoId = "v9", title = "T9", thumbnailUrl = "", playlistId = 3L)
        val useCase = AddToPlaylistUseCase(repository)

        useCase(3L, item)

        assertEquals(3L, repository.lastAddedPlaylistId)
        assertSame(item, repository.lastAddedItem)
    }

    // ---- RemoveFromPlaylistUseCase ----

    @Test
    fun `removeItem 透傳 playlistId 與 videoId`() = runTest {
        val useCase = RemoveFromPlaylistUseCase(repository)

        useCase(4L, "videoToRemove")

        assertEquals(4L, repository.lastRemovedPlaylistId)
        assertEquals("videoToRemove", repository.lastRemovedVideoId)
    }

    // ---- ClearPlaylistUseCase ----

    @Test
    fun `clearPlaylist 透傳 playlistId`() = runTest {
        val useCase = ClearPlaylistUseCase(repository)

        useCase(6L)

        assertEquals(6L, repository.clearedPlaylistId)
    }

    // ---- ShufflePlayPlaylistUseCase ----

    @Test
    fun `shufflePlay 回傳隨機取曲的結果`() = runTest {
        val randomItem = PlaylistItem(videoId = "r1", title = "R1", thumbnailUrl = "", playlistId = 2L)
        repository.randomItemToReturn = randomItem
        val useCase = ShufflePlayPlaylistUseCase(repository)

        val result = useCase(2L)

        assertEquals(2L, repository.lastRandomPlaylistId)
        assertSame(randomItem, result)
    }

    @Test
    fun `shufflePlay 清單為空時回傳 null`() = runTest {
        repository.randomItemToReturn = null
        val useCase = ShufflePlayPlaylistUseCase(repository)

        val result = useCase(2L)

        assertNull(result)
        assertEquals(2L, repository.lastRandomPlaylistId)
        assertTrue(repository.getRandomCalled)
    }

    // ---- Fake 實作 ----

    private class FakePlaylistRepository : PlaylistRepository {
        val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
        val itemsFlow = MutableStateFlow<List<PlaylistItem>>(emptyList())

        var lastCreateName: String? = null
        var nextPlaylistId: Long = 1L

        var lastRenameId: Long = -1L
        var lastRenameName: String? = null

        var deletedId: Long = -1L

        var lastObservedItemsPlaylistId: Long = -1L

        var lastAddedPlaylistId: Long = -1L
        var lastAddedItem: PlaylistItem? = null

        var lastRemovedPlaylistId: Long = -1L
        var lastRemovedVideoId: String? = null

        var clearedPlaylistId: Long = -1L

        var lastRandomPlaylistId: Long = -1L
        var randomItemToReturn: PlaylistItem? = null
        var getRandomCalled: Boolean = false

        fun emitPlaylists(value: List<Playlist>) {
            playlistsFlow.value = value
        }

        fun emitPlaylistItems(playlistId: Long, value: List<PlaylistItem>) {
            itemsFlow.value = value
        }

        override fun observeAllPlaylists(): Flow<List<Playlist>> = playlistsFlow

        override suspend fun createPlaylist(name: String): Long {
            lastCreateName = name
            return nextPlaylistId
        }

        override suspend fun renamePlaylist(playlistId: Long, newName: String) {
            lastRenameId = playlistId
            lastRenameName = newName
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            deletedId = playlistId
        }

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
            lastObservedItemsPlaylistId = playlistId
            return itemsFlow
        }

        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {
            lastAddedPlaylistId = playlistId
            lastAddedItem = item
        }

        override suspend fun removeItem(playlistId: Long, videoId: String) {
            lastRemovedPlaylistId = playlistId
            lastRemovedVideoId = videoId
        }

        override suspend fun clearPlaylist(playlistId: Long) {
            clearedPlaylistId = playlistId
        }

        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? {
            lastRandomPlaylistId = playlistId
            getRandomCalled = true
            return randomItemToReturn
        }
    }
}

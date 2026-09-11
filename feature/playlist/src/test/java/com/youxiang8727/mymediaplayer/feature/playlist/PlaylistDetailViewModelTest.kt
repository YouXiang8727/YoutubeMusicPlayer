package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.lifecycle.SavedStateHandle
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.ClearPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistItemsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.RemoveFromPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ShufflePlayPlaylistUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val v1 = PlaylistItem(
        videoId = "id1",
        title = "晴天",
        thumbnailUrl = "",
        channel = "Jay Chou",
        playlistId = 1L
    )
    private val v2 = PlaylistItem(
        videoId = "id2",
        title = "夜曲",
        thumbnailUrl = "",
        channel = "Official",
        playlistId = 1L
    )
    private val v3 = PlaylistItem(
        videoId = "id3",
        title = "七里香",
        thumbnailUrl = "",
        channel = "Jay Chou",
        playlistId = 1L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** ViewModel 只依賴 interface，此 fake 只供應觀察 flow；其餘操作不觸發。 */
    private class FakePlaylistRepository(
        val itemsFlow: Flow<List<PlaylistItem>> = emptyFlow()
    ) : PlaylistRepository {
        override fun observeAllPlaylists(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun createPlaylist(name: String): Long = 1L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = itemsFlow
        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {}
        override suspend fun removeItem(playlistId: Long, videoId: String) {}
        override suspend fun clearPlaylist(playlistId: Long) {}
        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null
        override suspend fun markStreamFailed(videoId: String, failedAt: Long) {}
        override suspend fun clearStreamFailed(videoId: String) {}
        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> = flowOf(emptyList())
        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = null
        override suspend fun importPlaylistFromJson(json: String): Long? = null
    }

    /** 記錄 playQueue（暫時性佇列）與 play（Room 路徑）的呼叫供斷言。 */
    private class FakePlayerController : PlayerController {
        val playQueueCalls = mutableListOf<Pair<List<PlayQueueItem>, Int>>()
        val playCalls = mutableListOf<Pair<String, String>>()
        override val playback = MutableStateFlow(PlaybackSnapshot())

        override fun play(videoId: String, title: String) {
            playCalls += videoId to title
        }

        override fun playQueue(items: List<PlayQueueItem>, startIndex: Int) {
            playQueueCalls += items to startIndex
        }

        override fun togglePlayPause() {}
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun seekTo(positionMs: Long) {}
        override fun toggleShuffle() {}
        override fun cycleRepeatMode() {}
        override fun stop() {}
    }

    private class Harness(
        val vm: PlaylistDetailViewModel,
        val player: FakePlayerController,
        val messages: MutableList<String>
    )

    private fun buildHarness(
        repo: FakePlaylistRepository,
        playlistId: Long = 1L,
        player: FakePlayerController = FakePlayerController()
    ): Harness {
        val vm = PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to playlistId)),
            observePlaylistItems = ObservePlaylistItemsUseCase(repo),
            removeFromPlaylist = RemoveFromPlaylistUseCase(repo),
            clearPlaylist = ClearPlaylistUseCase(repo),
            shufflePlayPlaylist = ShufflePlayPlaylistUseCase(repo),
            playerController = player
        )
        val messages = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        return Harness(vm, player, messages)
    }

    /** 以顯示順序建出預期的 PlayQueueItem list。 */
    private fun queueOf(vararg items: PlaylistItem): List<PlayQueueItem> =
        items.map { PlayQueueItem(videoId = it.videoId, title = it.title) }

    @Test
    fun `Play 以 items 顯示順序建 PlayQueueItem 佇列並從點擊曲 index 起播`() {
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1, v2, v3))))
        dispatcher.scheduler.advanceUntilIdle() // init flow 載入 items

        h.vm.onIntent(PlaylistDetailIntent.Play(v2))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, h.player.playQueueCalls.size)
        val (queueItems, startIndex) = h.player.playQueueCalls.single()
        assertEquals(queueOf(v1, v2, v3), queueItems)
        assertEquals(1, startIndex)
        // 不走 Room play 路徑（避免播放清單選擇錯亂）
        assertTrue(h.player.playCalls.isEmpty())
    }

    @Test
    fun `Play 第一首時 startIndex 為 0`() {
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1, v2, v3))))
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(PlaylistDetailIntent.Play(v1))
        dispatcher.scheduler.advanceUntilIdle()

        val (queueItems, startIndex) = h.player.playQueueCalls.single()
        assertEquals(queueOf(v1, v2, v3), queueItems)
        assertEquals(0, startIndex)
    }

    @Test
    fun `items 為空時 Play 不觸發 playQueue 且發出提示`() {
        // playlistId = 0 → init 不 subscribe DB flow，items 保持空
        val h = buildHarness(FakePlaylistRepository(), playlistId = 0L)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(h.vm.state.value.items.isEmpty())

        h.vm.onIntent(PlaylistDetailIntent.Play(v1))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(h.player.playQueueCalls.isEmpty())
        assertTrue(h.player.playCalls.isEmpty())
        assertEquals("清單為空，無法播放", h.messages.last())
    }

    @Test
    fun `failedCount 正確反映含 streamFailedAt 的項目數`() {
        val v1Failed = v1.copy(streamFailedAt = 1725000000000L)
        val v2Failed = v2.copy(streamFailedAt = 1725000100000L)
        // v1 失敗、v2 失敗、v3 正常 → failedCount = 2
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1Failed, v2Failed, v3))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, h.vm.state.value.items.size)
        assertEquals(2, h.vm.state.value.failedCount)
    }

    @Test
    fun `failedCount 為零時無失敗項目`() {
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1, v2, v3))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, h.vm.state.value.items.size)
        assertEquals(0, h.vm.state.value.failedCount)
    }
}
package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.lifecycle.SavedStateHandle
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
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
        val itemsFlow: Flow<List<PlaylistItem>> = emptyFlow(),
        /** [getRandomItem] 的回傳值；預設 null 代表「抽不到」＝歌單為空。 */
        private val randomItem: PlaylistItem? = null
    ) : PlaylistRepository {
        override fun observeAllPlaylists(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun createPlaylist(name: String): Long = 1L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = itemsFlow
        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {}
        override suspend fun removeItem(playlistId: Long, videoId: String) {}
        override suspend fun clearPlaylist(playlistId: Long) {}
        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = randomItem
        override suspend fun createPlaylistWithItems(name: String, items: List<PlaylistItem>): Long = 1L
        override suspend fun markStreamFailed(videoId: String, failedAt: Long) {}
        override suspend fun clearStreamFailed(videoId: String) {}
        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> = flowOf(emptyList())
        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = null
        override suspend fun exportAllPlaylistsAsJson(): String? = null
        override suspend fun importPlaylistFromJson(
            json: String,
            onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
        ): PlaylistImportResult? = null
    }

    /** 記錄 playQueue（暫時性佇列）與 play（Room 路徑）的呼叫供斷言。 */
    private class FakePlayerController : PlayerController {
        val playQueueCalls = mutableListOf<Pair<List<PlayQueueItem>, Int>>()
        val playCalls = mutableListOf<Pair<String, String>>()
        override val playback = MutableStateFlow(PlaybackSnapshot())
        override val queue = MutableStateFlow<List<PlayQueueItem>>(emptyList())

        override fun play(videoId: String, title: String) {
            playCalls += videoId to title
        }

        override fun playQueue(items: List<PlayQueueItem>, startIndex: Int) {
            playQueueCalls += items to startIndex
        }

        override fun addToQueue(videoId: String, title: String) {}

        override fun togglePlayPause() {}
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun seekTo(positionMs: Long) {}
        override fun toggleShuffle() {}
        override fun cycleRepeatMode() {}
        override fun stop() {}
        override fun seekToIndex(index: Int) {}
        override fun removeFromQueue(index: Int) {}
        override fun clearQueue() {}
    }

    private class Harness(
        val vm: PlaylistDetailViewModel,
        val player: FakePlayerController,
        val messages: MutableList<String>
    )

    private fun buildHarness(
        repo: FakePlaylistRepository,
        playlistId: Long = 1L,
        player: FakePlayerController = FakePlayerController(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("playlistId" to playlistId))
    ): Harness {
        val vm = PlaylistDetailViewModel(
            savedStateHandle = savedStateHandle,
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

    // ── 以下為歌名關鍵字篩選（query / visibleItems）──────────────────────────
    // visibleItems 是純函式派生屬性，故直接斷言 UiState，不需 coroutine。

    @Test
    fun `query 為空字串時 visibleItems 等於 items 且順序不變`() {
        val items = listOf(v1, v2, v3)
        val state = PlaylistDetailUiState(items = items, query = "")

        assertEquals(items, state.visibleItems)
    }

    @Test
    fun `query 為純空白時 visibleItems 等於 items（視為不篩選）`() {
        val items = listOf(v1, v2, v3)
        val state = PlaylistDetailUiState(items = items, query = "   ")

        assertEquals(items, state.visibleItems)
    }

    @Test
    fun `query 命中者保留、未命中者排除且維持原順序`() {
        val items = listOf(v1, v2, v3) // 晴天 / 夜曲 / 七里香
        val state = PlaylistDetailUiState(items = items, query = "夜")

        assertEquals(listOf(v2), state.visibleItems)
    }

    @Test
    fun `query 比對大小寫不敏感`() {
        // query 全小寫 jay，標題是「Jay Chou ...」大寫開頭
        val jayLive = v2.copy(title = "Jay Chou 演唱會現場")
        val items = listOf(v1, jayLive, v3)
        val state = PlaylistDetailUiState(items = items, query = "jay")

        assertEquals(listOf(jayLive), state.visibleItems)
    }

    @Test
    fun `query 前後空白被 trim 後才比對`() {
        val items = listOf(v1, v2, v3) // 晴天 / 夜曲 / 七里香
        val state = PlaylistDetailUiState(items = items, query = "  晴天  ")

        assertEquals(listOf(v1), state.visibleItems)
    }

    @Test
    fun `query 無命中時 visibleItems 為空`() {
        val items = listOf(v1, v2, v3)
        val state = PlaylistDetailUiState(items = items, query = "不存在")

        assertTrue(state.visibleItems.isEmpty())
    }

    @Test
    fun `query 只比對歌名不比頻道`() {
        // v1/v3 的 channel 都是 Jay Chou，但歌名不含 jay → 應全被排除
        val items = listOf(v1, v2, v3)
        val state = PlaylistDetailUiState(items = items, query = "jay")

        assertTrue(state.visibleItems.isEmpty())
    }

    @Test
    fun `SetQuery 更新 state query 並同步寫入 SavedStateHandle`() {
        val handle = SavedStateHandle(mapOf("playlistId" to 1L))
        val h = buildHarness(FakePlaylistRepository(), savedStateHandle = handle)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(PlaylistDetailIntent.SetQuery("  Jay "))

        // 存的是原始值（不 trim）：畫面上的字串與輸入框內容必須一致
        assertEquals("  Jay ", h.vm.state.value.query)
        assertEquals("  Jay ", handle.get<String>("playlistQuery"))
    }

    @Test
    fun `SetQuery 空白時 visibleItems 恢復全部項目`() {
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1, v2, v3))))
        dispatcher.scheduler.advanceUntilIdle()
        h.vm.onIntent(PlaylistDetailIntent.SetQuery("晴天"))
        assertEquals(listOf(v1), h.vm.state.value.visibleItems)

        h.vm.onIntent(PlaylistDetailIntent.SetQuery(""))

        assertEquals("", h.vm.state.value.query)
        assertEquals(listOf(v1, v2, v3), h.vm.state.value.visibleItems)
    }

    @Test
    fun `建構時從 SavedStateHandle 還原 query`() {
        val handle = SavedStateHandle(
            mapOf("playlistId" to 1L, "playlistQuery" to "夜曲")
        )
        val h = buildHarness(FakePlaylistRepository(), savedStateHandle = handle)

        assertEquals("夜曲", h.vm.state.value.query)
    }

    @Test
    fun `ShufflePlay 於篩選生效時以可見項目為播放佇列並從其中隨機起播`() {
        // 只有 v2 的歌名命中「夜曲」；Fake 的 getRandomItem 恆回 null，
        // 故若誤走未篩選路徑（UseCase）會得到「清單為空」而非播放。
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1, v2, v3))))
        dispatcher.scheduler.advanceUntilIdle()
        h.vm.onIntent(PlaylistDetailIntent.SetQuery("夜曲"))

        val expectedVisible = h.vm.state.value.visibleItems
        assertEquals(listOf(v2), expectedVisible)

        h.vm.onIntent(PlaylistDetailIntent.ShufflePlay)
        dispatcher.scheduler.advanceUntilIdle()

        // 佇列**恰為**可見項目（順序與 visibleItems 一致）
        assertEquals(1, h.player.playQueueCalls.size)
        val (queueItems, startIndex) = h.player.playQueueCalls.single()
        assertEquals(queueOf(v2), queueItems)
        // 只斷言範圍：startIndex 是隨機的，斷言固定值會 flaky
        assertTrue("startIndex=$startIndex 應落在 0..size-1", startIndex in expectedVisible.indices)
        // 不可走 Room play 路徑（那會讓 MusicService 以整份歌單為佇列，數字說謊）
        assertTrue(h.player.playCalls.isEmpty())
        assertEquals("隨機播放：夜曲", h.messages.last())
    }

    @Test
    fun `ShufflePlay 於篩選生效時佇列只含全部可見項目而非整份歌單`() {
        // query「Jay」命中 v1/v3 的**頻道**而非歌名 → 空；但「夜」只命中 v2。
        // 此處用能命中多首的 query 驗證佇列長度 == 可見數量（而非整份歌單 3 首）。
        val night1 = v1.copy(videoId = "n1", title = "夜曲 Live")
        val night2 = v2.copy(videoId = "n2", title = "夜曲 鋼琴版")
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(night1, v2, night2))))
        dispatcher.scheduler.advanceUntilIdle()
        h.vm.onIntent(PlaylistDetailIntent.SetQuery("夜曲"))

        val expectedVisible = h.vm.state.value.visibleItems
        assertEquals(3, expectedVisible.size)

        h.vm.onIntent(PlaylistDetailIntent.ShufflePlay)
        dispatcher.scheduler.advanceUntilIdle()

        val (queueItems, startIndex) = h.player.playQueueCalls.single()
        assertEquals(queueOf(night1, v2, night2), queueItems)
        assertEquals(expectedVisible.size, queueItems.size)
        assertTrue(startIndex in expectedVisible.indices)
        assertEquals(
            "隨機播放：${expectedVisible[startIndex].title}",
            h.messages.last()
        )
    }

    @Test
    fun `ShufflePlay 於篩選無結果時不播放且不建佇列並提示清單為空`() {
        val h = buildHarness(FakePlaylistRepository(flowOf(listOf(v1, v2, v3))))
        dispatcher.scheduler.advanceUntilIdle()
        h.vm.onIntent(PlaylistDetailIntent.SetQuery("不存在"))

        h.vm.onIntent(PlaylistDetailIntent.ShufflePlay)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(h.player.playQueueCalls.isEmpty())
        assertTrue(h.player.playCalls.isEmpty())
        assertEquals("清單為空，無法隨機播放", h.messages.last())
    }

    @Test
    fun `ShufflePlay 未篩選時仍走 play 路徑且不建 playQueue 佇列`() {
        // 回歸保護：防止有人「順手統一」兩條路徑，把未篩選也改成 playQueue。
        // 未篩選的既有語意是「整份歌單為佇列、隨機起播位置」，由 UseCase + play 達成。
        val repo = FakePlaylistRepository(
            itemsFlow = flowOf(listOf(v1, v2, v3)),
            randomItem = v3
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", h.vm.state.value.query)

        h.vm.onIntent(PlaylistDetailIntent.ShufflePlay)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, h.player.playCalls.size)
        assertEquals("id3" to "七里香", h.player.playCalls.single())
        assertTrue(h.player.playQueueCalls.isEmpty())
        assertEquals("隨機播放：七里香", h.messages.last())
    }
}
package com.youxiang8727.mymediaplayer.feature.player

import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
import com.youxiang8727.mymediaplayer.core.domain.usecase.SaveQueueAsPlaylistUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PlayerViewModel] 的意圖分送契約與「存為播放清單」訊息文案測試。
 *
 * onPlaybackIntent 的播放相關分支是同步純轉送（不碰 dispatcher、不做 coroutine suspend），
 * 故以 FakePlayerController 記錄呼叫即可斷言；「存為播放清單」分支涉及 suspend
 * 與 messages，故以 kotlinx-coroutines-test 的 TestDispatcher 推進（需 setMain，
 * 因為 viewModelScope 固定走 Dispatchers.Main）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 記錄 addToQueue / play / playQueue / seekToIndex / clearQueue 的呼叫供斷言；其餘空實作。 */
    private class FakePlayerController : PlayerController {
        var addToQueueCall: Pair<String, String>? = null
        var playCall: Pair<String, String>? = null
        var playQueueCall: Pair<List<PlayQueueItem>, Int>? = null
        var seekToIndexCall: Int? = null
        var clearQueueCalled: Boolean = false

        override val playback = MutableStateFlow(PlaybackSnapshot())
        override val queue = MutableStateFlow<List<PlayQueueItem>>(emptyList())

        override fun addToQueue(videoId: String, title: String) {
            addToQueueCall = videoId to title
        }

        override fun play(videoId: String, title: String) {
            playCall = videoId to title
        }

        override fun playQueue(items: List<PlayQueueItem>, startIndex: Int) {
            playQueueCall = items to startIndex
        }

        override fun seekToIndex(index: Int) {
            seekToIndexCall = index
        }

        override fun clearQueue() {
            clearQueueCalled = true
        }

        override fun togglePlayPause() {}
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun seekTo(positionMs: Long) {}
        override fun toggleShuffle() {}
        override fun cycleRepeatMode() {}
        override fun stop() {}
        override fun removeFromQueue(index: Int) {}
    }

    /**
     * 播放清單 Fake（沿用 SearchViewModelTest 的風格）：
     * - [createError] 模擬寫入失敗；若是 [PlaylistNameConflictException] 則走
     *   domain 的重名分支，[NameConflict] 會被驗證到。
     * - [existingPlaylists] 供 observeAllPlaylists 觀察，驗證 playlistNames 形狀。
     */
    private class FakePlaylistRepository(
        val existingPlaylists: List<Playlist> = emptyList(),
        var createError: Exception? = null
    ) : PlaylistRepository {
        val createdPlaylists = mutableListOf<String>()
        val createdItems = mutableListOf<List<PlaylistItem>>()

        override fun observeAllPlaylists(): Flow<List<Playlist>> =
            MutableStateFlow(existingPlaylists)

        override suspend fun createPlaylist(name: String): Long = 1L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {}
        override suspend fun removeItem(playlistId: Long, videoId: String) {}
        override suspend fun clearPlaylist(playlistId: Long) {}
        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null

        override suspend fun createPlaylistWithItems(name: String, items: List<PlaylistItem>): Long {
            createError?.let { throw it }
            createdPlaylists += name
            createdItems += items
            return 1L
        }

        override suspend fun markStreamFailed(videoId: String, failedAt: Long) {}
        override suspend fun clearStreamFailed(videoId: String) {}
        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = null
        override suspend fun exportAllPlaylistsAsJson(): String? = null
        override suspend fun importPlaylistFromJson(
            json: String,
            onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
        ): PlaylistImportResult? = null
    }

    private class Harness(
        val vm: PlayerViewModel,
        val player: FakePlayerController,
        val playlistRepo: FakePlaylistRepository,
        val messages: MutableList<String>
    )

    private fun buildHarness(
        playlistRepo: FakePlaylistRepository = FakePlaylistRepository(),
        queue: List<PlayQueueItem> = emptyList()
    ): Harness {
        val player = FakePlayerController()
        player.queue.value = queue
        val vm = PlayerViewModel(
            playerController = player,
            saveQueueAsPlaylist = SaveQueueAsPlaylistUseCase(playlistRepo),
            observePlaylists = ObservePlaylistsUseCase(playlistRepo)
        )
        val messages = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        return Harness(vm, player, playlistRepo, messages)
    }

    // ── AddToQueue（新增「加入佇列」路徑）──

    @Test
    fun `AddToQueue 轉送 videoId 與 title 給 addToQueue`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.AddToQueue("id1", "title1"))

        assertEquals("id1" to "title1", h.player.addToQueueCall)
    }

    @Test
    fun `AddToQueue 不觸發 play（加入佇列不中斷目前播放）`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.AddToQueue("id1", "title1"))

        assertNull(h.player.playCall)
        assertNull(h.player.playQueueCall)
    }

    // ── 既有 intent 回歸測底 ──

    @Test
    fun `Play 轉送 videoId 與 title 給 play`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.Play("id9", "t9"))

        assertEquals("id9" to "t9", h.player.playCall)
        assertNull(h.player.addToQueueCall)
    }

    @Test
    fun `PlayList 轉送 entries 與 startIndex 給 playQueue`() {
        val h = buildHarness()
        val entries = listOf(PlayQueueItem("e1", "第一"), PlayQueueItem("e2", "第二"))

        h.vm.onPlaybackIntent(PlaybackIntent.PlayList(entries, startIndex = 1))

        assertEquals(entries to 1, h.player.playQueueCall)
        assertNull(h.player.playCall)
    }

    @Test
    fun `ClearQueue 轉送給 clearQueue`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.ClearQueue)

        assertTrue(h.player.clearQueueCalled)
    }

    @Test
    fun `SeekToIndex 轉送 index 給 seekToIndex`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.SeekToIndex(2))

        assertEquals(2, h.player.seekToIndexCall)
    }

    // ── 存為播放清單（SaveQueueAsPlaylist）──

    private fun Harness.saveAs(name: String) {
        vm.onPlaybackIntent(PlaybackIntent.SaveQueueAsPlaylist(name))
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `存為播放清單成功且無重複時提示已建立`() {
        val h = buildHarness(
            queue = listOf(PlayQueueItem("id1", "第一"), PlayQueueItem("id2", "第二"))
        )

        h.saveAs("我的最愛")

        assertEquals(listOf("我的最愛"), h.playlistRepo.createdPlaylists)
        // 去重後 2 筆：順序由 addedAt 反向遞減還原佇列順序（此處只驗筆數與 videoId 順序）
        assertEquals(listOf("id1", "id2"), h.playlistRepo.createdItems.single().map { it.videoId })
        assertEquals("已建立「我的最愛」", h.messages.last())
    }

    @Test
    fun `存為播放清單有重複曲目時文案如實告知合併數`() {
        // 佇列刻意允許重複（append-only），但歌單 (playlistId, videoId) 複合主鍵只能一列
        val h = buildHarness(
            queue = listOf(
                PlayQueueItem("id1", "第一"),
                PlayQueueItem("id2", "第二"),
                PlayQueueItem("id1", "第一"),
                PlayQueueItem("id2", "第二"),
                PlayQueueItem("id3", "第三")
            )
        )

        h.saveAs("我的最愛")

        // 實際寫入 3 筆（去重後），但必須告知使用者有 2 首被合併
        assertEquals(3, h.playlistRepo.createdItems.single().size)
        assertTrue(
            "文案必須包含合併數，實際：${h.messages.last()}",
            h.messages.last().contains("2 首重複曲目已合併")
        )
        assertEquals("已建立「我的最愛」，其中 2 首重複曲目已合併", h.messages.last())
    }

    @Test
    fun `歌單重名時直接顯示 domain 的 message`() {
        val h = buildHarness(
            queue = listOf(PlayQueueItem("id1", "第一")),
            playlistRepo = FakePlaylistRepository(
                createError = PlaylistNameConflictException("我的最愛")
            )
        )

        h.saveAs("我的最愛")

        assertEquals(listOf("已存在同名歌單「我的最愛」"), h.messages)
        // 重名不得寫入任何項目
        assertTrue(h.playlistRepo.createdPlaylists.isEmpty())
    }

    @Test
    fun `寫入失敗時直接顯示 domain 的 message`() {
        val h = buildHarness(
            queue = listOf(PlayQueueItem("id1", "第一")),
            playlistRepo = FakePlaylistRepository(createError = RuntimeException("磁碟寫滿"))
        )

        h.saveAs("我的最愛")

        assertEquals(listOf("磁碟寫滿"), h.messages)
    }

    @Test
    fun `佇列為空時顯示 domain 的失敗文案且不建立歌單`() {
        val h = buildHarness(queue = emptyList())

        h.saveAs("空清單")

        assertTrue(h.playlistRepo.createdPlaylists.isEmpty())
        assertEquals(listOf("播放佇列為空，沒有可儲存的曲目"), h.messages)
    }

    // ── playlistNames（Dialog 防重名）──

    @Test
    fun `playlistNames 反映觀察到的既有歌單名稱`() {
        val h = buildHarness(
            playlistRepo = FakePlaylistRepository(
                existingPlaylists = listOf(
                    Playlist(id = 1L, name = "我的最愛"),
                    Playlist(id = 2L, name = "工作播放清單")
                )
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("我的最愛", "工作播放清單"), h.vm.playlistNames.value)
    }

    @Test
    fun `playlistNames 初始為空集合`() {
        val h = buildHarness()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptySet<String>(), h.vm.playlistNames.value)
    }
}

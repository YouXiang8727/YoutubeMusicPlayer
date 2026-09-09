package com.youxiang8727.mymediaplayer.feature.discover

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchTrendingSongsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val v1 = VideoResult("id1", "晴天", "", "Jay Chou")
    private val v2 = VideoResult("id2", "夜曲", "", "Official")
    private val v3 = VideoResult("id3", "七里香", "", "Jay Chou")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 熱門榜單：預設所有 region 回傳同一 [trendingResult]；可用 [trendingResultsByRegion]
     * 針對單一 region 覆寫結果（區域獨立性測試用）。記錄收到的 region 順序供
     * 「依 DISPLAY_ORDER 依序抓取」斷言。search stub：Discover 不觸發搜尋，回空頁即可。
     */
    private class FakeVideoRepository(
        var trendingResult: Result<List<VideoResult>> = Result.success(emptyList()),
        var trendingResultsByRegion: Map<ChartRegion, Result<List<VideoResult>>> = emptyMap()
    ) : VideoRepository {
        var trendingCalls = 0
        val receivedTrendingRegions = mutableListOf<ChartRegion>()

        override suspend fun search(query: String, continuationToken: String?): Result<VideoSearchPage> =
            Result.success(VideoSearchPage(emptyList()))

        override suspend fun fetchTrendingSongs(region: ChartRegion): Result<List<VideoResult>> {
            trendingCalls++
            receivedTrendingRegions += region
            return trendingResultsByRegion[region] ?: trendingResult
        }
    }

    /**
     * 播放清單 Fake：可覆寫 [addItemError] 模擬加入失敗；記錄建立與加入呼叫供斷言。
     * playlists 以 [MutableStateFlow] 暴露，測試可直接更新模擬 observe 推送。
     */
    private class FakePlaylistRepository(
        initialPlaylists: List<Playlist> = emptyList(),
        var addItemError: Boolean = false
    ) : PlaylistRepository {
        val playlistsFlow = MutableStateFlow(initialPlaylists)
        val createdPlaylists = mutableListOf<String>()
        val addedItems = mutableListOf<Pair<Long, PlaylistItem>>()

        override fun observeAllPlaylists(): Flow<List<Playlist>> = playlistsFlow

        override suspend fun createPlaylist(name: String): Long {
            createdPlaylists += name
            return 1L
        }

        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}

        override suspend fun deletePlaylist(playlistId: Long) {}

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = emptyFlow()

        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {
            if (addItemError) throw RuntimeException("add failed")
            addedItems += (playlistId to item)
        }

        override suspend fun removeItem(playlistId: Long, videoId: String) {}

        override suspend fun clearPlaylist(playlistId: Long) {}

        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null
    }

    private class Harness(
        val vm: DiscoverViewModel,
        val repo: FakeVideoRepository,
        val playlistRepo: FakePlaylistRepository,
        val messages: MutableList<String>
    )

    private fun buildHarness(
        repo: FakeVideoRepository,
        playlistRepo: FakePlaylistRepository = FakePlaylistRepository()
    ): Harness {
        val vm = DiscoverViewModel(
            FetchTrendingSongsUseCase(repo),
            AddToPlaylistUseCase(playlistRepo),
            CreatePlaylistUseCase(playlistRepo),
            ObservePlaylistsUseCase(playlistRepo)
        )
        val messages = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        return Harness(vm, repo, playlistRepo, messages)
    }

    @Test
    fun `init 依 DISPLAY_ORDER 依序抓取所有區域各一次`() {
        val repo = FakeVideoRepository()
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()

        // fake 記錄收到的 region：應恰好依 DISPLAY_ORDER 順序、各一次
        assertEquals(ChartRegion.DISPLAY_ORDER, h.repo.receivedTrendingRegions)
        assertEquals(ChartRegion.DISPLAY_ORDER.size, h.repo.trendingCalls)
    }

    @Test
    fun `init 各區域成功寫入 items 且無 error`() {
        val repo = FakeVideoRepository(
            trendingResult = Result.success(listOf(v1, v2, v3))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()

        // 所有區域都應有結果（FakeRepository 對任何 region 回傳相同結果）
        for (region in ChartRegion.DISPLAY_ORDER) {
            assertEquals(
                listOf(v1, v2, v3),
                h.vm.state.value.trendingByRegion[region]?.items
            )
            assertNull(h.vm.state.value.trendingByRegion[region]?.error)
            assertTrue(!(h.vm.state.value.trendingByRegion[region]?.loading ?: true))
        }
    }

    @Test
    fun `init 全區域失敗寫入各區域 error 且 items 為空`() {
        val repo = FakeVideoRepository(
            trendingResult = Result.failure(RuntimeException("charts down"))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()

        for (region in ChartRegion.DISPLAY_ORDER) {
            assertEquals("charts down", h.vm.state.value.trendingByRegion[region]?.error)
            assertEquals(emptyList<VideoResult>(), h.vm.state.value.trendingByRegion[region]?.items)
        }
    }

    @Test
    fun `單一區域失敗不影響其他區域的榜單內容`() {
        // 僅 JAPAN 失敗，其餘區域成功：驗證區域獨立性
        val repo = FakeVideoRepository(
            trendingResult = Result.success(listOf(v1, v2)),
            trendingResultsByRegion = mapOf(
                ChartRegion.JAPAN to Result.failure(RuntimeException("jp down"))
            )
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()

        // 失敗區域：error 寫入、items 為空、loading 結束
        assertEquals("jp down", h.vm.state.value.trendingByRegion[ChartRegion.JAPAN]?.error)
        assertEquals(emptyList<VideoResult>(), h.vm.state.value.trendingByRegion[ChartRegion.JAPAN]?.items)
        // 其餘區域不受影響：items 完好、無 error
        for (region in ChartRegion.DISPLAY_ORDER.filter { it != ChartRegion.JAPAN }) {
            assertEquals(listOf(v1, v2), h.vm.state.value.trendingByRegion[region]?.items)
            assertNull(h.vm.state.value.trendingByRegion[region]?.error)
        }
        // 失敗與成功區域皆被抓取
        assertEquals(ChartRegion.DISPLAY_ORDER.toSet(), h.repo.receivedTrendingRegions.toSet())
    }

    @Test
    fun `TrendingRetry 重試成功清除該區域 error 並寫入榜單`() {
        // 初始全區域失敗
        val repo = FakeVideoRepository(
            trendingResult = Result.failure(RuntimeException("charts down"))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()
        for (region in ChartRegion.DISPLAY_ORDER) {
            assertEquals("charts down", h.vm.state.value.trendingByRegion[region]?.error)
        }
        assertEquals(ChartRegion.DISPLAY_ORDER.size, h.repo.trendingCalls)

        // 模擬後端恢復：換成成功結果後重試
        repo.trendingResult = Result.success(listOf(v1, v2))
        h.vm.onIntent(DiscoverIntent.TrendingRetry)
        dispatcher.scheduler.advanceUntilIdle()

        for (region in ChartRegion.DISPLAY_ORDER) {
            assertEquals(listOf(v1, v2), h.vm.state.value.trendingByRegion[region]?.items)
            assertNull(h.vm.state.value.trendingByRegion[region]?.error)
        }
        assertEquals(ChartRegion.DISPLAY_ORDER.size * 2, h.repo.trendingCalls)
    }

    @Test
    fun `TrendingRetry 不重複抓取仍在載入中的區域`() {
        val repo = FakeVideoRepository(
            trendingResult = Result.success(listOf(v1))
        )
        val h = buildHarness(repo)
        // 不 advance：init 的 fetch 仍在進行中（loading = true）
        assertEquals(0, h.repo.trendingCalls)
        assertTrue(
            ChartRegion.DISPLAY_ORDER.all {
                h.vm.state.value.trendingByRegion[it]?.loading == true
            }
        )

        // 載入進行中觸發 retry：重入 guard 應擋下，不重複發起抓取
        h.vm.onIntent(DiscoverIntent.TrendingRetry)
        dispatcher.scheduler.advanceUntilIdle()

        // 只有 init 的 4 次抓取，retry 未新增呼叫
        assertEquals(ChartRegion.DISPLAY_ORDER.size, h.repo.trendingCalls)
        for (region in ChartRegion.DISPLAY_ORDER) {
            assertEquals(listOf(v1), h.vm.state.value.trendingByRegion[region]?.items)
            assertNull(h.vm.state.value.trendingByRegion[region]?.error)
        }
    }

    @Test
    fun `observePlaylists 推送更新 playlists 狀態`() {
        val repo = FakeVideoRepository()
        // Playlist 帶 default 時脈時間戳：initial 與斷言共用同一 instance，
        // 避免兩次重建（跨毫秒）造成時間戳不等 → flaky（CI 時脈下必現）
        val initial = Playlist(id = 1, name = "我的最愛")
        val playlistRepo = FakePlaylistRepository(
            initialPlaylists = listOf(initial)
        )
        val h = buildHarness(repo, playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(initial), h.vm.playlists.value)

        // 新增一筆後推送：VM 應立即反映（updated 內含 initial 同一 instance）
        val updated = listOf(
            initial,
            Playlist(id = 2, name = "工作播放清單")
        )
        playlistRepo.playlistsFlow.value = updated
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(updated, h.vm.playlists.value)
    }

    @Test
    fun `AddToPlaylist 成功寫入 repository 並發出成功訊息`() {
        val repo = FakeVideoRepository()
        val playlistRepo = FakePlaylistRepository()
        val h = buildHarness(repo, playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(DiscoverIntent.AddToPlaylist(v1, 7L))
        dispatcher.scheduler.advanceUntilIdle()

        // addedAt 為 System.currentTimeMillis()，只比對關鍵欄位（避免毫秒差異造成 flaky）
        assertEquals(1, playlistRepo.addedItems.size)
        assertEquals(7L, playlistRepo.addedItems.single().first)
        assertEquals(v1.videoId, playlistRepo.addedItems.single().second.videoId)
        assertEquals(7L, playlistRepo.addedItems.single().second.playlistId)
        assertTrue(h.messages.contains("已加入播放清單"))
    }

    @Test
    fun `AddToPlaylist 失敗發出失敗訊息`() {
        val repo = FakeVideoRepository()
        val playlistRepo = FakePlaylistRepository(addItemError = true)
        val h = buildHarness(repo, playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(DiscoverIntent.AddToPlaylist(v1, 7L))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(h.messages.contains("加入失敗：add failed"))
        assertTrue(playlistRepo.addedItems.isEmpty())
    }

    @Test
    fun `createPlaylistAndAdd 建立新清單並加入歌曲發出成功訊息`() {
        val repo = FakeVideoRepository()
        val playlistRepo = FakePlaylistRepository()
        val h = buildHarness(repo, playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.createPlaylistAndAdd("我的最愛", v1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("我的最愛"), playlistRepo.createdPlaylists)
        // addedAt 為 System.currentTimeMillis()，只比對關鍵欄位（避免毫秒差異造成 flaky）
        assertEquals(1L, playlistRepo.addedItems.single().first)
        assertEquals(v1.videoId, playlistRepo.addedItems.single().second.videoId)
        assertEquals(1L, playlistRepo.addedItems.single().second.playlistId)
        assertTrue(h.messages.contains("已建立「我的最愛」並加入歌曲"))
    }

    @Test
    fun `createPlaylistAndAdd 失敗發出失敗訊息`() {
        val repo = FakeVideoRepository()
        val playlistRepo = FakePlaylistRepository(addItemError = true)
        val h = buildHarness(repo, playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.createPlaylistAndAdd("我的最愛", v1)
        dispatcher.scheduler.advanceUntilIdle()

        // createPlaylist 已在加入前建立；加入失敗 → 失敗訊息
        assertEquals(listOf("我的最愛"), playlistRepo.createdPlaylists)
        assertTrue(h.messages.contains("建立失敗：add failed"))
    }
}
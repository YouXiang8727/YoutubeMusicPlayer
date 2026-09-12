package com.youxiang8727.mymediaplayer.feature.discover

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.RecommendationRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchRecommendationsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchTrendingSongsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObserveRecentPlaylistItemsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
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
import org.junit.Assert.assertFalse
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
     * playlists 以 [MutableStateFlow] 暴露，測試可直接更新模擬 observe 推送；
     * recentItemsFlow 同理模擬 `observeRecentItems`（「為你推薦」種子來源）推送。
     */
    private class FakePlaylistRepository(
        initialPlaylists: List<Playlist> = emptyList(),
        initialRecentItems: List<PlaylistItem> = emptyList(),
        var addItemError: Boolean = false,
        var createError: Exception? = null
    ) : PlaylistRepository {
        val playlistsFlow = MutableStateFlow(initialPlaylists)
        val recentItemsFlow = MutableStateFlow(initialRecentItems)
        val createdPlaylists = mutableListOf<String>()
        val addedItems = mutableListOf<Pair<Long, PlaylistItem>>()

        override fun observeAllPlaylists(): Flow<List<Playlist>> = playlistsFlow

        override suspend fun createPlaylist(name: String): Long {
            createError?.let { throw it }
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
        override suspend fun markStreamFailed(videoId: String, failedAt: Long) {}
        override suspend fun clearStreamFailed(videoId: String) {}

        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> = recentItemsFlow

        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = null
        override suspend fun exportAllPlaylistsAsJson(): String? = null
        override suspend fun importPlaylistFromJson(
            json: String,
            onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
        ): PlaylistImportResult? = null
    }

    /** 「為你推薦」資料源 Fake：記錄收到的種子與 limit 供斷言，結果可覆寫。 */
    private class FakeRecommendationRepository(
        var result: Result<List<VideoResult>> = Result.success(emptyList())
    ) : RecommendationRepository {
        var calls = 0
        val receivedSeeds = mutableListOf<List<PlaylistItem>>()
        var receivedLimit: Int? = null

        override suspend fun recommendationsFor(
            seeds: List<PlaylistItem>,
            limit: Int
        ): Result<List<VideoResult>> {
            calls++
            receivedSeeds += seeds
            receivedLimit = limit
            return result
        }
    }

    private class Harness(
        val vm: DiscoverViewModel,
        val repo: FakeVideoRepository,
        val playlistRepo: FakePlaylistRepository,
        val recRepo: FakeRecommendationRepository,
        val messages: MutableList<String>
    )

    private fun buildHarness(
        repo: FakeVideoRepository,
        playlistRepo: FakePlaylistRepository = FakePlaylistRepository(),
        recRepo: FakeRecommendationRepository = FakeRecommendationRepository()
    ): Harness {
        val vm = DiscoverViewModel(
            FetchTrendingSongsUseCase(repo),
            AddToPlaylistUseCase(playlistRepo),
            CreatePlaylistUseCase(playlistRepo),
            ObservePlaylistsUseCase(playlistRepo),
            FetchRecommendationsUseCase(recRepo),
            ObserveRecentPlaylistItemsUseCase(playlistRepo)
        )
        val messages = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        return Harness(vm, repo, playlistRepo, recRepo, messages)
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

    @Test
    fun `createPlaylistAndAdd 遇同名歌單發出衝突訊息且不加入任何歌曲`() {
        // create 拋 PlaylistNameConflictException → 直接提示重名，addToPlaylist 不被呼叫
        val repo = FakeVideoRepository()
        val playlistRepo = FakePlaylistRepository(
            createError = PlaylistNameConflictException("我的最愛")
        )
        val h = buildHarness(repo, playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.createPlaylistAndAdd("我的最愛", v1)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(h.messages.contains("已存在同名歌單「我的最愛」"))
        assertTrue("addToPlaylist 不應被呼叫", playlistRepo.addedItems.isEmpty())
    }

    // --- 「為你推薦」 ---

    /** 產生種子用 PlaylistItem（跟既有測試慣例一致，只比對關鍵欄位）。 */
    private fun seedItem(videoId: String) = PlaylistItem(
        videoId = videoId,
        title = "seed $videoId",
        thumbnailUrl = "",
        playlistId = 1L
    )

    @Test
    fun `init 種子非空抓取推薦成功寫入 items`() {
        val seeds = listOf(seedItem("s1"), seedItem("s2"))
        val recRepo = FakeRecommendationRepository(Result.success(listOf(v1, v2)))
        val h = buildHarness(
            repo = FakeVideoRepository(),
            playlistRepo = FakePlaylistRepository(initialRecentItems = seeds),
            recRepo = recRepo
        )
        dispatcher.scheduler.advanceUntilIdle()

        val recommendation = h.vm.state.value.recommendation
        assertEquals(listOf(v1, v2), recommendation.items)
        assertNull(recommendation.error)
        assertFalse(recommendation.loading)
        assertFalse(recommendation.seedEmpty)
        // 種子原樣透傳、limit 用 domain 常數
        assertEquals(1, recRepo.calls)
        assertEquals(seeds, recRepo.receivedSeeds.single())
        assertEquals(FetchRecommendationsUseCase.RECOMMENDATION_LIMIT, recRepo.receivedLimit)
    }

    @Test
    fun `init 種子空 seedEmpty=true 且不觸發抓取`() {
        val recRepo = FakeRecommendationRepository()
        val h = buildHarness(repo = FakeVideoRepository(), recRepo = recRepo)
        dispatcher.scheduler.advanceUntilIdle()

        val recommendation = h.vm.state.value.recommendation
        assertTrue(recommendation.seedEmpty)
        assertEquals(emptyList<VideoResult>(), recommendation.items)
        assertNull(recommendation.error)
        assertEquals(0, recRepo.calls)
    }

    @Test
    fun `抓取失敗寫入 error 且 items 為空`() {
        val recRepo = FakeRecommendationRepository(
            Result.failure(RuntimeException("related down"))
        )
        val h = buildHarness(
            repo = FakeVideoRepository(),
            playlistRepo = FakePlaylistRepository(
                initialRecentItems = listOf(seedItem("s1"))
            ),
            recRepo = recRepo
        )
        dispatcher.scheduler.advanceUntilIdle()

        val recommendation = h.vm.state.value.recommendation
        assertEquals("related down", recommendation.error)
        assertEquals(emptyList<VideoResult>(), recommendation.items)
        assertFalse(recommendation.seedEmpty)
        assertFalse(recommendation.loading)
    }

    @Test
    fun `RecommendationRefresh 以相同種子重新抓取成功`() {
        val seeds = listOf(seedItem("s1"), seedItem("s2"))
        // 初始失敗 → error 可重試
        val recRepo = FakeRecommendationRepository(
            Result.failure(RuntimeException("down"))
        )
        val h = buildHarness(
            repo = FakeVideoRepository(),
            playlistRepo = FakePlaylistRepository(initialRecentItems = seeds),
            recRepo = recRepo
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("down", h.vm.state.value.recommendation.error)
        assertEquals(1, recRepo.calls)

        // 後端恢復後「換一批」：同一組種子重新抓取、成功寫入並清除 error
        recRepo.result = Result.success(listOf(v1))
        h.vm.onIntent(DiscoverIntent.RecommendationRefresh)
        dispatcher.scheduler.advanceUntilIdle()

        val recommendation = h.vm.state.value.recommendation
        assertEquals(listOf(v1), recommendation.items)
        assertNull(recommendation.error)
        assertFalse(recommendation.loading)
        assertEquals(2, recRepo.calls)
        assertEquals(seeds, recRepo.receivedSeeds[0])
        assertEquals(seeds, recRepo.receivedSeeds[1])
    }

    @Test
    fun `RecommendationRefresh 種子為空時為 no-op`() {
        val recRepo = FakeRecommendationRepository()
        val h = buildHarness(repo = FakeVideoRepository(), recRepo = recRepo)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(h.vm.state.value.recommendation.seedEmpty)

        h.vm.onIntent(DiscoverIntent.RecommendationRefresh)
        dispatcher.scheduler.advanceUntilIdle()

        // seedEmpty 空狀態下沒有種子可換：不觸發抓取
        assertEquals(0, recRepo.calls)
        assertTrue(h.vm.state.value.recommendation.seedEmpty)
    }

    @Test
    fun `observeRecentItems 推新種子自動重新抓取`() {
        // 共用同一 instance：addedAt 為 default 時脈，跨呼叫重建會造成毫秒差異 → flaky
        val seed1 = seedItem("s1")
        val seed2 = seedItem("s2")
        val playlistRepo = FakePlaylistRepository(initialRecentItems = listOf(seed1))
        val recRepo = FakeRecommendationRepository(Result.success(listOf(v1)))
        val h = buildHarness(
            repo = FakeVideoRepository(),
            playlistRepo = playlistRepo,
            recRepo = recRepo
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, recRepo.calls)
        assertEquals(listOf(seed1), recRepo.receivedSeeds.single())

        // 加入新歌 → observeRecentItems 推送新種子 → 自動重新產生推薦（非 Refresh intent）
        playlistRepo.recentItemsFlow.value = listOf(seed2)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, recRepo.calls)
        assertEquals(listOf(seed2), recRepo.receivedSeeds.last())
        assertEquals(listOf(v1), h.vm.state.value.recommendation.items)
    }
}
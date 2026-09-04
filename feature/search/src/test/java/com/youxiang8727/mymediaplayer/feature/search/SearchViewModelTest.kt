package com.youxiang8727.mymediaplayer.feature.search

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchTrendingSongsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchVideosUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class SearchViewModelTest {

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

    /** 依 token 區分初次搜尋與載入更多回傳；記錄呼叫次數與收到的 token。 */
    private class FakeVideoRepository(
        var firstPageResult: Result<VideoSearchPage> = Result.success(VideoSearchPage(emptyList())),
        var loadMoreResult: Result<VideoSearchPage> = Result.success(VideoSearchPage(emptyList())),
        var trendingResult: Result<List<VideoResult>> = Result.success(emptyList())
    ) : VideoRepository {
        var searchCalls = 0
        val receivedTokens = mutableListOf<String?>()
        var trendingCalls = 0

        override suspend fun search(query: String, continuationToken: String?): Result<VideoSearchPage> {
            searchCalls++
            receivedTokens += continuationToken
            return if (continuationToken == null) firstPageResult else loadMoreResult
        }

        override suspend fun fetchTrendingSongs(region: ChartRegion): Result<List<VideoResult>> {
            trendingCalls++
            return trendingResult
        }
    }

    private object EmptyPlaylistRepository : PlaylistRepository {
        override fun observeAllPlaylists(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun createPlaylist(name: String): Long = 1L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {}
        override suspend fun removeItem(playlistId: Long, videoId: String) {}
        override suspend fun clearPlaylist(playlistId: Long) {}
        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null
    }

    private class Harness(
        val vm: SearchViewModel,
        val repo: FakeVideoRepository,
        val messages: MutableList<String>
    )

    private fun buildHarness(repo: FakeVideoRepository): Harness {
        val vm = SearchViewModel(
            SearchVideosUseCase(repo),
            AddToPlaylistUseCase(EmptyPlaylistRepository),
            CreatePlaylistUseCase(EmptyPlaylistRepository),
            ObservePlaylistsUseCase(EmptyPlaylistRepository),
            FetchTrendingSongsUseCase(repo)
        )
        val messages = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        return Harness(vm, repo, messages)
    }

    private fun Harness.doSearch(query: String) {
        vm.onIntent(SearchIntent.QueryChanged(query))
        vm.onIntent(SearchIntent.Search)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun Harness.triggerLoadMore() {
        vm.onIntent(SearchIntent.LoadMore)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `初次搜尋成功時 results 被替換且 nextPageToken 更新`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")

        assertEquals(listOf(v1), h.vm.state.value.results)
        assertEquals("TOKEN_A", h.vm.state.value.nextPageToken)
        assertTrue(!h.vm.state.value.isLoading)
        assertTrue(!h.vm.state.value.isLoadingMore)
    }

    @Test
    fun `初次搜尋失敗清空 nextPageToken 且 error 有值`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.failure(RuntimeException("boom"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")

        assertNull(h.vm.state.value.nextPageToken)
        assertEquals("boom", h.vm.state.value.error)
        assertEquals(emptyList<VideoResult>(), h.vm.state.value.results)
        assertEquals("搜尋失敗：boom", h.messages.last())
    }

    @Test
    fun `loadMore 成功時 append 結果並更新 token`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A")),
            loadMoreResult = Result.success(VideoSearchPage(listOf(v2), "TOKEN_B"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        h.triggerLoadMore()

        assertEquals(listOf(v1, v2), h.vm.state.value.results)
        assertEquals("TOKEN_B", h.vm.state.value.nextPageToken)
        assertTrue(!h.vm.state.value.isLoadingMore)
        // 載入更多以既有 token 呼叫
        assertEquals(listOf<String?>(null, "TOKEN_A"), h.repo.receivedTokens)
    }

    @Test
    fun `loadMore 回空頁視為到底 token 清空並提示已無更多`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A")),
            loadMoreResult = Result.success(VideoSearchPage(emptyList()))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        h.triggerLoadMore()

        assertNull(h.vm.state.value.nextPageToken)
        assertEquals(listOf(v1), h.vm.state.value.results) // 既有結果不受影響
        assertTrue(h.messages.contains("已無更多結果"))
    }

    @Test
    fun `loadMore 進行中時再次觸發不重複呼叫 UseCase`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        assertEquals(1, h.repo.searchCalls)

        // 第一次 LoadMore：同步把 isLoadingMore 設為 true（尚未 advance，coroutine 排隊中）
        h.vm.onIntent(SearchIntent.LoadMore)
        // 尚未 advance 即再次觸發 → 重入 guard 應擋下
        h.vm.onIntent(SearchIntent.LoadMore)
        dispatcher.scheduler.advanceUntilIdle()

        // 只有一次額外 loadMore 呼叫（初次搜尋 1 次 + 載入更多 1 次）
        assertEquals(2, h.repo.searchCalls)
        assertEquals("TOKEN_A", h.repo.receivedTokens.last())
    }

    @Test
    fun `loadMore 失敗不破壞結果且保留 token 供重試`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A")),
            loadMoreResult = Result.failure(RuntimeException("token expired"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        assertEquals("TOKEN_A", h.vm.state.value.nextPageToken)
        h.triggerLoadMore()

        assertEquals(listOf(v1), h.vm.state.value.results) // 既有結果不破壞
        assertEquals("TOKEN_A", h.vm.state.value.nextPageToken) // 保留供重試
        assertEquals("token expired", h.vm.state.value.error)
        assertTrue(!h.vm.state.value.isLoadingMore)
        assertEquals("載入更多失敗：token expired", h.messages.last())
    }

    @Test
    fun `空白查詢搜尋被忽略不呼叫 UseCase`() {
        val repo = FakeVideoRepository()
        val h = buildHarness(repo)
        h.vm.onIntent(SearchIntent.QueryChanged("   "))
        h.vm.onIntent(SearchIntent.Search)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, h.repo.searchCalls)
        assertTrue(!h.vm.state.value.searched)
    }

    @Test
    fun `深頁重疊時 append 去重且順序不變不崩潰`() {
        // 第一頁尾 2 筆（v1、v2）在第二頁重複出現，模擬 deep page 5~28% 重疊
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v2, v1), "TOKEN_A")),
            // 第二頁含第一頁尾 2 筆（v1、v2）重疊 + 1 筆新結果 v3
            loadMoreResult = Result.success(VideoSearchPage(listOf(v1, v2, v3), "TOKEN_B"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        h.triggerLoadMore()

        // 去重後：保留首次出現（v2、v1 順序），新增 v3，不崩潰、無視覺重複
        assertEquals(listOf(v2, v1, v3), h.vm.state.value.results)
        // token 正常前進
        assertEquals("TOKEN_B", h.vm.state.value.nextPageToken)
        assertTrue(!h.vm.state.value.isLoadingMore)
    }

    @Test
    fun `token 未推進視為到底 token 清空並提示已無更多`() {
        // 回聲：data 層回傳的 nextPageToken 與本次 sent token 相同
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1, v2), "TOKEN_A")),
            loadMoreResult = Result.success(VideoSearchPage(listOf(v3), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        assertEquals("TOKEN_A", h.vm.state.value.nextPageToken)
        h.triggerLoadMore()

        // 視為已到底：nextPageToken 清空（中斷潛在輪迴）、結果仍 append、提示已無更多
        assertNull(h.vm.state.value.nextPageToken)
        assertEquals(listOf(v1, v2, v3), h.vm.state.value.results)
        assertTrue(h.messages.contains("已無更多結果"))
    }

    @Test
    fun `init 自動載入台灣熱門榜單成功寫入 trendingItems`() {
        val repo = FakeVideoRepository(
            trendingResult = Result.success(listOf(v1, v2, v3))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(v1, v2, v3), h.vm.state.value.trendingItems)
        assertTrue(!h.vm.state.value.trendingLoading)
        assertNull(h.vm.state.value.trendingError)
        // init 載入不干擾搜尋狀態（仍為空狀態）
        assertTrue(!h.vm.state.value.searched)
        assertEquals(1, h.repo.trendingCalls)
    }

    @Test
    fun `init 熱門榜單載入失敗寫入 trendingError 且 items 為空`() {
        val repo = FakeVideoRepository(
            trendingResult = Result.failure(RuntimeException("charts down"))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("charts down", h.vm.state.value.trendingError)
        assertEquals(emptyList<VideoResult>(), h.vm.state.value.trendingItems)
        assertTrue(!h.vm.state.value.trendingLoading)
        assertNull(h.vm.state.value.error) // 不污染搜尋錯誤欄位
    }

    @Test
    fun `TrendingRetry 重試成功清除錯誤並寫入榜單`() {
        val repo = FakeVideoRepository(
            trendingResult = Result.failure(RuntimeException("charts down"))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("charts down", h.vm.state.value.trendingError)
        assertEquals(1, h.repo.trendingCalls)

        // 模擬後端恢復：換成成功結果後重試
        repo.trendingResult = Result.success(listOf(v1, v2))
        h.vm.onIntent(SearchIntent.TrendingRetry)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(v1, v2), h.vm.state.value.trendingItems)
        assertNull(h.vm.state.value.trendingError)
        assertTrue(!h.vm.state.value.trendingLoading)
        assertEquals(2, h.repo.trendingCalls)
    }

    @Test
    fun `熱門榜單與搜尋狀態互不干擾`() {
        // 搜尋成功不影響已載入的 trending
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A")),
            trendingResult = Result.success(listOf(v2, v3))
        )
        val h = buildHarness(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(v2, v3), h.vm.state.value.trendingItems)

        h.doSearch("晴天")
        assertEquals(listOf(v1), h.vm.state.value.results)
        assertTrue(h.vm.state.value.searched)
        assertEquals(listOf(v2, v3), h.vm.state.value.trendingItems)
        assertNull(h.vm.state.value.trendingError)

        // 搜尋失敗也不影響已載入的 trending
        val repo2 = FakeVideoRepository(
            firstPageResult = Result.failure(RuntimeException("boom")),
            trendingResult = Result.success(listOf(v2, v3))
        )
        val h2 = buildHarness(repo2)
        dispatcher.scheduler.advanceUntilIdle()
        h2.doSearch("晴天")
        assertEquals("boom", h2.vm.state.value.error)
        assertEquals(listOf(v2, v3), h2.vm.state.value.trendingItems)
        assertNull(h2.vm.state.value.trendingError)
    }
}

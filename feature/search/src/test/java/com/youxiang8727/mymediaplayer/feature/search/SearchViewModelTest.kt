package com.youxiang8727.mymediaplayer.feature.search

import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.SearchHistoryRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.SearchSuggestionRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import com.youxiang8727.mymediaplayer.core.domain.model.SearchSort
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddSearchHistoryUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ClearSearchHistoryUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObserveSearchHistoryUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchSuggestionsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchVideosUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
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

    /**
     * 依 token 區分初次搜尋與載入更多回傳；記錄呼叫次數與收到的 token／sort。
     */
    private class FakeVideoRepository(
        var firstPageResult: Result<VideoSearchPage> = Result.success(VideoSearchPage(emptyList())),
        var loadMoreResult: Result<VideoSearchPage> = Result.success(VideoSearchPage(emptyList()))
    ) : VideoRepository {
        var searchCalls = 0
        val receivedTokens = mutableListOf<String?>()
        /** 每次呼叫收到的 sort（供排序功能斷言；續頁應永遠是預設 RELEVANCE）。 */
        val receivedSorts = mutableListOf<SearchSort>()

        override suspend fun search(
            query: String,
            continuationToken: String?,
            sort: SearchSort
        ): Result<VideoSearchPage> {
            searchCalls++
            receivedTokens += continuationToken
            receivedSorts += sort
            return if (continuationToken == null) firstPageResult else loadMoreResult
        }

        override suspend fun fetchTrendingSongs(region: com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion): Result<List<VideoResult>> =
            Result.success(emptyList())
    }

    /**
     * 播放清單 Fake：可覆寫 [createError] 模擬建立重名衝突；記錄建立與加入呼叫供斷言。
     */
    private class FakePlaylistRepository(
        var createError: Exception? = null
    ) : PlaylistRepository {
        val createdPlaylists = mutableListOf<String>()
        val addedItems = mutableListOf<Pair<Long, PlaylistItem>>()

        override fun observeAllPlaylists(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun createPlaylist(name: String): Long {
            createError?.let { throw it }
            createdPlaylists += name
            return 1L
        }
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {
            addedItems += (playlistId to item)
        }
        override suspend fun removeItem(playlistId: Long, videoId: String) {}
        override suspend fun clearPlaylist(playlistId: Long) {}
        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null
        override suspend fun createPlaylistWithItems(name: String, items: List<PlaylistItem>): Long = 1L
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

    /**
     * 搜尋建議 Fake：記錄呼叫次數與收到的查詢；預設回空清單，
     * 可覆寫 [resultByQuery]（依查詢回對應建議）與 [forcedFailure]（模擬網路失敗）。
     */
    private class FakeSuggestionRepository(
        var resultByQuery: Map<String, List<String>> = emptyMap(),
        var forcedFailure: Boolean = false
    ) : SearchSuggestionRepository {
        var suggestionCalls = 0
        val receivedQueries = mutableListOf<String>()

        override suspend fun suggestions(query: String): List<String> {
            suggestionCalls++
            receivedQueries += query
            if (forcedFailure) return emptyList()
            return resultByQuery[query] ?: emptyList()
        }
    }

    /**
     * 搜尋紀錄 Fake：in-memory 實作 [SearchHistoryRepository]。
     * 語意與 core:data 實作一致：trim、空白忽略、去重置頂、上限 10。
     */
    private class FakeSearchHistoryRepository : SearchHistoryRepository {
        private val _history = MutableStateFlow<List<String>>(emptyList())

        var addCalls = 0
        val addedQueries = mutableListOf<String>()
        var clearCalls = 0

        override suspend fun add(query: String) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return
            addCalls++
            addedQueries += trimmed
            _history.update { current ->
                val deduped = current.filter { it != trimmed }
                (listOf(trimmed) + deduped).take(10)
            }
        }

        override fun observeAll(): Flow<List<String>> = _history

        override suspend fun clear() {
            clearCalls++
            _history.value = emptyList()
        }

        /** 測試輔助：直接設定歷史資料（模擬外部變更）。 */
        fun seedHistory(items: List<String>) {
            _history.value = items
        }
    }

    private class Harness(
        val vm: SearchViewModel,
        val repo: FakeVideoRepository,
        val suggestionRepo: FakeSuggestionRepository,
        val historyRepo: FakeSearchHistoryRepository,
        val messages: MutableList<String>
    )

    private fun buildHarness(
        repo: FakeVideoRepository = FakeVideoRepository(),
        suggestionRepo: FakeSuggestionRepository = FakeSuggestionRepository(),
        historyRepo: FakeSearchHistoryRepository = FakeSearchHistoryRepository(),
        playlistRepo: FakePlaylistRepository = FakePlaylistRepository()
    ): Harness {
        val vm = SearchViewModel(
            searchVideos = SearchVideosUseCase(repo),
            addToPlaylist = AddToPlaylistUseCase(playlistRepo),
            createPlaylist = CreatePlaylistUseCase(playlistRepo),
            observePlaylists = ObservePlaylistsUseCase(playlistRepo),
            searchSuggestions = SearchSuggestionsUseCase(suggestionRepo),
            observeSearchHistory = ObserveSearchHistoryUseCase(historyRepo),
            addSearchHistory = AddSearchHistoryUseCase(historyRepo),
            clearSearchHistory = ClearSearchHistoryUseCase(historyRepo)
        )
        val messages = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        return Harness(vm, repo, suggestionRepo, historyRepo, messages)
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

    private fun Harness.changeSort(sort: SearchSort) {
        vm.onIntent(SearchIntent.ChangeSort(sort))
        dispatcher.scheduler.advanceUntilIdle()
    }

    // ==================== 搜尋 & 分頁 ====================

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

    /**
     * 空結果回饋只由 UI 空狀態承載，ViewModel 不再發「查無結果」snackbar
     * （避免短暫浮動與持續顯示的文字同時出現）。
     */
    @Test
    fun `搜尋無結果時不發出 messages 由空狀態承載回饋`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(emptyList()))
        )
        val h = buildHarness(repo)
        h.doSearch("這是不存在關鍵字zzz")

        // 搜尋本身成功：狀態正確進入「已搜尋但無結果」，空狀態據此顯示「查無結果」
        assertTrue(h.vm.state.value.searched)
        assertTrue(h.vm.state.value.results.isEmpty())
        assertNull(h.vm.state.value.error)
        assertTrue(!h.vm.state.value.isLoading)
        // 不再發出重複的 snackbar
        assertEquals(emptyList<String>(), h.messages)
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
    fun `空白 query 重置搜尋狀態回空狀態`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)

        // 先執行搜尋，進入已搜尋狀態
        h.doSearch("晴天")
        assertTrue(h.vm.state.value.searched)
        assertEquals(listOf(v1), h.vm.state.value.results)

        // 收到空白 QueryChanged → 重置回空狀態（清除搜尋）
        h.vm.onIntent(SearchIntent.QueryChanged(""))
        val st = h.vm.state.value
        assertTrue(!st.searched)
        assertEquals(emptyList<VideoResult>(), st.results)
        assertNull(st.nextPageToken)
        assertTrue(!st.isLoading)
        assertTrue(!st.isLoadingMore)
        assertNull(st.error)
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

    // ==================== 搜尋紀錄（history） ====================

    @Test
    fun `init 觀察歷史 repo 有資料則 state history 對應`() {
        val historyRepo = FakeSearchHistoryRepository()
        historyRepo.seedHistory(listOf("晴天", "七里香"))
        val h = buildHarness(historyRepo = historyRepo)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("晴天", "七里香"), h.vm.state.value.history)
    }

    @Test
    fun `repo 推送更新後 state history 同步更新`() {
        val historyRepo = FakeSearchHistoryRepository()
        val h = buildHarness(historyRepo = historyRepo)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(h.vm.state.value.history.isEmpty())

        // 模擬外部變更（例如其他畫面觸發搜尋）
        historyRepo.seedHistory(listOf("夜曲"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("夜曲"), h.vm.state.value.history)
    }

    @Test
    fun `Search 提交後 repo 記錄該 query`() {
        val historyRepo = FakeSearchHistoryRepository()
        val h = buildHarness(historyRepo = historyRepo)
        h.doSearch("晴天")

        assertEquals(1, h.historyRepo.addCalls)
        assertEquals("晴天", h.historyRepo.addedQueries.last())
        // state 也同步（因為 repo 會推送）
        assertEquals(listOf("晴天"), h.vm.state.value.history)
    }

    @Test
    fun `SelectSuggestion 提交後 repo 記錄該 query`() {
        val historyRepo = FakeSearchHistoryRepository()
        val h = buildHarness(historyRepo = historyRepo)
        h.vm.onIntent(SearchIntent.SelectSuggestion("周杰倫"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, h.historyRepo.addCalls)
        assertEquals("周杰倫", h.historyRepo.addedQueries.last())
        assertEquals(listOf("周杰倫"), h.vm.state.value.history)
    }

    @Test
    fun `重複搜尋同 query 只存一筆且置頂`() {
        val historyRepo = FakeSearchHistoryRepository()
        val h = buildHarness(historyRepo = historyRepo)

        h.doSearch("晴天")
        h.doSearch("夜曲")
        h.doSearch("晴天") // 重複
        dispatcher.scheduler.advanceUntilIdle()

        // 去重置頂：晴天在最前，夜曲在後
        assertEquals(listOf("晴天", "夜曲"), h.vm.state.value.history)
        // add 被呼叫 3 次
        assertEquals(3, h.historyRepo.addCalls)
    }

    @Test
    fun `ClearHistory 清空 repo 且 state history 為空`() {
        val historyRepo = FakeSearchHistoryRepository()
        historyRepo.seedHistory(listOf("晴天", "夜曲"))
        val h = buildHarness(historyRepo = historyRepo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("晴天", "夜曲"), h.vm.state.value.history)

        h.vm.onIntent(SearchIntent.ClearHistory)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, h.historyRepo.clearCalls)
        assertTrue(h.vm.state.value.history.isEmpty())
    }

    @Test
    fun `空白 query 的 Search 不記錄歷史`() {
        val historyRepo = FakeSearchHistoryRepository()
        val h = buildHarness(historyRepo = historyRepo)
        h.vm.onIntent(SearchIntent.QueryChanged("   "))
        h.vm.onIntent(SearchIntent.Search)
        dispatcher.scheduler.advanceUntilIdle()

        // 空白 query 被忽略（doSearch 內部 guard），不呼叫 add
        assertEquals(0, h.historyRepo.addCalls)
        assertTrue(h.historyRepo.addedQueries.isEmpty())
    }

    // ==================== 搜尋建議（autocomplete）流程 ====================

    @Test
    fun `輸入非空白查詢會 debounce 後抓取建議寫入 state`() {
        val suggestionRepo = FakeSuggestionRepository(
            resultByQuery = mapOf(
                "周" to listOf("周杰倫", "周星馳"),
                "周杰" to listOf("周杰倫", "周杰倫 晴天")
            )
        )
        val h = buildHarness(FakeVideoRepository(), suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle() // 消化 init 的建議收集 coroutine

        // 快速連續輸入：只應觸發一次（最後一筆「周杰」經 debounce 後抓取）
        h.vm.onIntent(SearchIntent.QueryChanged("周"))
        h.vm.onIntent(SearchIntent.QueryChanged("周杰"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("周杰倫", "周杰倫 晴天"), h.vm.state.value.suggestions)
        assertEquals(listOf("周杰"), h.suggestionRepo.receivedQueries)
        assertEquals(1, h.suggestionRepo.suggestionCalls)
    }

    @Test
    fun `debounce 期間尚未到期時不觸發抓取`() {
        val suggestionRepo = FakeSuggestionRepository(
            resultByQuery = mapOf("周杰倫" to listOf("周杰倫 晴天"))
        )
        val h = buildHarness(FakeVideoRepository(), suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(SearchIntent.QueryChanged("周杰倫"))
        // advance 299ms（< debounce 300ms）：應尚未觸發
        dispatcher.scheduler.advanceTimeBy(SearchViewModel.SUGGESTION_DEBOUNCE_MS - 1)
        assertEquals(0, h.suggestionRepo.suggestionCalls)
        assertEquals(emptyList<String>(), h.vm.state.value.suggestions)

        // 推進跨越 debounce 門檻後才觸發
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, h.suggestionRepo.suggestionCalls)
        assertEquals(listOf("周杰倫 晴天"), h.vm.state.value.suggestions)
    }

    @Test
    fun `空白或清除輸入不觸發抓取且建議清空`() {
        val suggestionRepo = FakeSuggestionRepository(
            resultByQuery = mapOf("周" to listOf("周杰倫"))
        )
        val h = buildHarness(FakeVideoRepository(), suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle()

        // 空白輸入：不觸發抓取
        h.vm.onIntent(SearchIntent.QueryChanged("   "))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, h.suggestionRepo.suggestionCalls)
        assertTrue(h.vm.state.value.suggestions.isEmpty())

        // 輸入非空白取得建議後清除（✕按鈕送空白）：清空建議
        h.vm.onIntent(SearchIntent.QueryChanged("周"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("周杰倫"), h.vm.state.value.suggestions)
        h.vm.onIntent(SearchIntent.QueryChanged(""))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(h.vm.state.value.suggestions.isEmpty())
        assertTrue(!h.vm.state.value.searched)
    }

    @Test
    fun `點擊建議填回搜尋框並觸發搜尋且隱藏建議`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val suggestionRepo = FakeSuggestionRepository(
            resultByQuery = mapOf("周" to listOf("周杰倫"))
        )
        val h = buildHarness(repo, suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle()

        // 先取得建議
        h.vm.onIntent(SearchIntent.QueryChanged("周"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("周杰倫"), h.vm.state.value.suggestions)

        // 點擊建議
        h.vm.onIntent(SearchIntent.SelectSuggestion("周杰倫"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("周杰倫", h.vm.state.value.query)
        assertEquals(1, h.repo.searchCalls)
        assertTrue(h.vm.state.value.searched)
        assertTrue(h.vm.state.value.suggestions.isEmpty())
        assertEquals(listOf(v1), h.vm.state.value.results)
    }

    @Test
    fun `明確搜尋按鈕隱藏建議但不影響既有抓取行為`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val suggestionRepo = FakeSuggestionRepository(
            resultByQuery = mapOf("周" to listOf("周杰倫"))
        )
        val h = buildHarness(repo, suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(SearchIntent.QueryChanged("周"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("周杰倫"), h.vm.state.value.suggestions)

        h.vm.onIntent(SearchIntent.Search)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, h.repo.searchCalls)
        assertTrue(h.vm.state.value.suggestions.isEmpty())
        assertEquals(listOf(v1), h.vm.state.value.results)
    }

    @Test
    fun `建議抓取失敗優雅降級為空清單不崩潰`() {
        // Repository 失敗回空清單（不丟例外），UI 應顯示空建議、不崩潰、不影響別的欄位
        val suggestionRepo = FakeSuggestionRepository(forcedFailure = true)
        val h = buildHarness(FakeVideoRepository(), suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.onIntent(SearchIntent.QueryChanged("周杰倫"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(h.vm.state.value.suggestions.isEmpty())
        assertNull(h.vm.state.value.error) // 建議失敗不污染搜尋錯誤欄位
        assertTrue(!h.vm.state.value.searched)
    }

    @Test
    fun `搜尋後遲到的建議回應不覆蓋搜尋後狀態`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val suggestionRepo = FakeSuggestionRepository(
            resultByQuery = mapOf("周" to listOf("周杰倫"))
        )
        val h = buildHarness(repo, suggestionRepo)
        dispatcher.scheduler.advanceUntilIdle()

        // 輸入非空白，debounce 尚未跨過門檻即執行明確搜尋
        h.vm.onIntent(SearchIntent.QueryChanged("周"))
        h.vm.onIntent(SearchIntent.Search)
        dispatcher.scheduler.advanceUntilIdle()

        // 搜尋後建議應為空（未套用遲到的建議回應）
        assertTrue(h.vm.state.value.suggestions.isEmpty())
        assertEquals(listOf(v1), h.vm.state.value.results)
        assertTrue(h.vm.state.value.searched)
    }

    // ==================== 排序切換（sort） ====================

    /**
     * 零行為變更守門測試：排序功能導入前後，預設狀態必須完全相同
     * （RELEVANCE ＝ 平台預設相關性排序），既有搜尋流程不受影響。
     */
    @Test
    fun `sort 預設為 RELEVANCE 且既有搜尋以預設排序送出`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)

        assertEquals(SearchSort.RELEVANCE, h.vm.state.value.sort)

        h.doSearch("晴天")

        assertEquals(SearchSort.RELEVANCE, h.vm.state.value.sort)
        assertEquals(listOf(SearchSort.RELEVANCE), h.repo.receivedSorts)
    }

    @Test
    fun `切換排序立即清空既有結果與 nextPageToken`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        assertEquals(listOf(v1), h.vm.state.value.results)
        assertEquals("TOKEN_A", h.vm.state.value.nextPageToken)

        // 刻意不 advance：觀察「發出 intent 後、重搜 coroutine 尚未執行」的瞬間狀態
        h.vm.onIntent(SearchIntent.ChangeSort(SearchSort.LATEST))
        val cleared = h.vm.state.value

        assertEquals(SearchSort.LATEST, cleared.sort)
        assertEquals(emptyList<VideoResult>(), cleared.results)
        assertNull(cleared.nextPageToken)
        // 仍處於「已搜尋」狀態（不退回空狀態頁）
        assertTrue(cleared.searched)
    }

    @Test
    fun `切換排序以新 sort 重新搜尋並取代結果`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        assertEquals(1, h.repo.searchCalls)

        // 換成「最新」後，data 層回傳的是另一組結果與另一枚 token
        repo.firstPageResult = Result.success(VideoSearchPage(listOf(v2), "TOKEN_B"))
        h.changeSort(SearchSort.LATEST)

        // 自動重搜（不需再按搜尋鈕），且以新 sort、不帶 continuation token
        assertEquals(2, h.repo.searchCalls)
        assertEquals(SearchSort.LATEST, h.repo.receivedSorts.last())
        assertNull(h.repo.receivedTokens.last())
        // 舊結果（v1）已被取代為新排序的結果（v2），不是 append
        assertEquals(listOf(v2), h.vm.state.value.results)
        assertEquals("TOKEN_B", h.vm.state.value.nextPageToken)
        assertTrue(!h.vm.state.value.isLoading)
    }

    @Test
    fun `切換到熱門排序同樣觸發重搜且帶 POPULAR`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        h.changeSort(SearchSort.POPULAR)

        assertEquals(SearchSort.POPULAR, h.vm.state.value.sort)
        assertEquals(SearchSort.POPULAR, h.repo.receivedSorts.last())
    }

    @Test
    fun `未搜尋過時切換排序不觸發搜尋但保留選項`() {
        val repo = FakeVideoRepository()
        val h = buildHarness(repo)

        h.changeSort(SearchSort.LATEST)

        assertEquals(0, h.repo.searchCalls)
        assertTrue(!h.vm.state.value.searched)
        assertEquals(SearchSort.LATEST, h.vm.state.value.sort)
    }

    @Test
    fun `未搜尋過所選的排序會沿用到下一次搜尋`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)

        // 空狀態先選好排序（UI 此時不顯示切換器，屬防禦性行為）
        h.changeSort(SearchSort.POPULAR)
        assertEquals(0, h.repo.searchCalls)

        h.doSearch("晴天")

        assertEquals(1, h.repo.searchCalls)
        assertEquals(SearchSort.POPULAR, h.repo.receivedSorts.last())
    }

    @Test
    fun `空白 query 切換排序不觸發搜尋不崩潰`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)

        h.vm.onIntent(SearchIntent.QueryChanged("   "))
        dispatcher.scheduler.advanceUntilIdle()
        h.changeSort(SearchSort.LATEST)

        assertEquals(0, h.repo.searchCalls)
        assertEquals(SearchSort.LATEST, h.vm.state.value.sort)
    }

    @Test
    fun `切換排序不寫入搜尋紀錄`() {
        val historyRepo = FakeSearchHistoryRepository()
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo, historyRepo = historyRepo)
        h.doSearch("晴天")
        assertEquals(1, historyRepo.addCalls)

        h.changeSort(SearchSort.LATEST)
        h.changeSort(SearchSort.POPULAR)

        // 切換排序不是新的搜尋意圖，紀錄仍只記 1 筆
        assertEquals(1, historyRepo.addCalls)
        assertEquals(listOf("晴天"), historyRepo.addedQueries)
    }

    @Test
    fun `切換到當前相同排序不重搜`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")
        assertEquals(1, h.repo.searchCalls)

        h.changeSort(SearchSort.RELEVANCE)

        assertEquals(1, h.repo.searchCalls)
        assertEquals(listOf(v1), h.vm.state.value.results)
        assertEquals("TOKEN_A", h.vm.state.value.nextPageToken)
    }

    @Test
    fun `LoadMore 不帶 sort 由 continuation token 承載排序`() {
        val repo = FakeVideoRepository(
            firstPageResult = Result.success(VideoSearchPage(listOf(v1), "TOKEN_A")),
            loadMoreResult = Result.success(VideoSearchPage(listOf(v2), "TOKEN_B"))
        )
        val h = buildHarness(repo)
        h.doSearch("晴天")                      // 初次：RELEVANCE
        h.changeSort(SearchSort.LATEST)        // 初次：LATEST
        h.triggerLoadMore()                    // 續頁：沿用預設，不傳 sort

        assertEquals(
            listOf(SearchSort.RELEVANCE, SearchSort.LATEST, SearchSort.RELEVANCE),
            h.repo.receivedSorts
        )
        assertEquals(listOf<String?>(null, null, "TOKEN_A"), h.repo.receivedTokens)
    }

    // ==================== createPlaylistAndAdd（重名防呆） ====================

    @Test
    fun `createPlaylistAndAdd 遇同名歌單發出衝突訊息且不加入任何歌曲`() {
        // create 拋 PlaylistNameConflictException → 直接提示重名，addToPlaylist 不被呼叫
        val playlistRepo = FakePlaylistRepository(
            createError = PlaylistNameConflictException("我的最愛")
        )
        val h = buildHarness(playlistRepo = playlistRepo)
        dispatcher.scheduler.advanceUntilIdle()

        h.vm.createPlaylistAndAdd("我的最愛", v1)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(h.messages.contains("已存在同名歌單「我的最愛」"))
        assertTrue("addToPlaylist 不應被呼叫", playlistRepo.addedItems.isEmpty())
    }
}

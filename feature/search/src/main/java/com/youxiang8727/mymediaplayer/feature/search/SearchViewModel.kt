package com.youxiang8727.mymediaplayer.feature.search

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddSearchHistoryUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ClearSearchHistoryUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObserveSearchHistoryUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchSuggestionsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchVideosUseCase
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<VideoResult> = emptyList(),
    val nextPageToken: String? = null,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
    // 搜尋建議（autocomplete）：輸入過程 debounce 後載入，空白/清除/搜尋後清空
    val suggestions: List<String> = emptyList(),
    // 搜尋紀錄（最新在前，最多 10 筆，空白/清除時顯示空狀態提示）
    val history: List<String> = emptyList()
)

sealed interface SearchIntent {
    data class QueryChanged(val value: String) : SearchIntent
    data object Search : SearchIntent
    data class SelectSuggestion(val value: String) : SearchIntent
    data object LoadMore : SearchIntent
    data class AddToPlaylist(val video: VideoResult, val playlistId: Long) : SearchIntent
    data object ClearHistory : SearchIntent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVideos: SearchVideosUseCase,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    observePlaylists: ObservePlaylistsUseCase,
    private val searchSuggestions: SearchSuggestionsUseCase,
    observeSearchHistory: ObserveSearchHistoryUseCase,
    private val addSearchHistory: AddSearchHistoryUseCase,
    private val clearSearchHistory: ClearSearchHistoryUseCase
) : ViewModel() {

    companion object {
        /** 觸發建議查詢的 Input debounce 毫秒數（避免每鍵打網）。 */
        const val SUGGESTION_DEBOUNCE_MS = 300L
    }

    /** 內部資料：debounce 後的建議查詢（文字與事件紀元，用於無效化已過期的結果）。 */
    private data class SuggestionQuery(val text: String, val epoch: Long)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // 輸入過程的 debounce 來源；DROP_OLDEST：連續快速輸入時只保留最新一筆基準。
    // 每筆事件帶有 epoch：當使用者執行明確搜尋或清除時 epoch++，使仍處於 debounce 管道
    // 中的前一輪結果在取得網路回應後發現 epoch 不符而被拋棄，不覆蓋搜尋/清除後的狀態。
    private var suggestionEpoch: Long = 0L

    private val _suggestionQuery = MutableSharedFlow<SuggestionQuery>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        observePlaylists()
            .onEach { list -> _playlists.value = list }
            .launchIn(viewModelScope)
        observeSearchHistory()
            .onEach { list -> _state.update { it.copy(history = list) } }
            .launchIn(viewModelScope)
        collectSuggestions()
    }

    /**
     * debounce 輸入後抓取搜尋建議。`collectLatest` 使前一個未完成的抓取
     * （慢網回應）在下一筆輸入到來時被取消，避免過時回應覆蓋新狀態。
     * [SuggestionQuery.epoch] 讓搜尋/清除後的遲到結果自動作廢。
     *
     * 空/過短查詢由 [SearchSuggestionsUseCase] 內部防禦回空清單（不觸網）；
     * Repository 失敗回空清單（不丟例外），故 UI 不需額外兜底即可優雅降級。
     */
    @OptIn(FlowPreview::class)
    private fun collectSuggestions() {
        viewModelScope.launch {
            _suggestionQuery
                .debounce(SUGGESTION_DEBOUNCE_MS)
                .collectLatest { item ->
                    val result = searchSuggestions(item.text)
                    // 僅在「事件紀元仍為最新（期間未發生搜尋/清除）且查詢相符」時套用，
                    // 避免搜尋後的遲到回應重新覆蓋建議清單（reset suggestions = 空）。
                    if (item.epoch == suggestionEpoch &&
                        _state.value.query.trim() == item.text
                    ) {
                        _state.update { it.copy(suggestions = result) }
                    }
                }
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                _state.update { it.copy(query = intent.value) }
                // 空白查詢：重置搜尋狀態回空狀態（最近搜尋/提示文字）。
                // 不觸碰 history（observeAll 持續推送）；隱藏建議並無效化
                // debounce 管道中的前一輪事件（suggestionEpoch++）。
                if (intent.value.isBlank()) {
                    suggestionEpoch++
                    _state.update {
                        it.copy(
                            searched = false,
                            results = emptyList(),
                            nextPageToken = null,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                            suggestions = emptyList()
                        )
                    }
                } else {
                    // 非空白：進 debounce 鏈抓建議（epoch 維持，debounce 到期時匹配）
                    _suggestionQuery.tryEmit(SuggestionQuery(intent.value.trim(), suggestionEpoch))
                }
            }
            SearchIntent.Search -> {
                // 執行明確搜尋：隱藏建議並無效化 debounce 管道中仍在等待的前一輪事件
                suggestionEpoch++
                _state.update { it.copy(suggestions = emptyList()) }
                val query = _state.value.query.trim()
                if (query.isNotEmpty()) {
                    viewModelScope.launch { addSearchHistory(query) }
                }
                doSearch()
            }
            // 點擊建議：填回搜尋框並直接觸發搜尋；同 Search 清空建議並無效化管道
            is SearchIntent.SelectSuggestion -> {
                suggestionEpoch++
                _state.update { it.copy(query = intent.value, suggestions = emptyList()) }
                viewModelScope.launch { addSearchHistory(intent.value.trim()) }
                doSearch()
            }
            SearchIntent.LoadMore -> loadMore()
            is SearchIntent.AddToPlaylist -> addVideoToPlaylist(
                intent.video.toPlaylistItem(intent.playlistId),
                intent.playlistId
            )
            SearchIntent.ClearHistory -> {
                viewModelScope.launch { clearSearchHistory() }
            }
        }
    }

    private fun doSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null, searched = true) }
        viewModelScope.launch {
            searchVideos(query)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            results = page.results,
                            nextPageToken = page.nextPageToken,
                            error = null
                        )
                    }
                    if (page.results.isEmpty()) _messages.tryEmit("查無結果")
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            nextPageToken = null,
                            error = e.message
                        )
                    }
                    _messages.tryEmit("搜尋失敗：${e.message ?: "未知錯誤"}")
                }
        }
    }

    private fun loadMore() {
        val current = _state.value
        // 重入 guard：初次搜尋中或已在載入更多時忽略
        if (current.isLoading || current.isLoadingMore) return
        val token = current.nextPageToken ?: return
        val query = current.query.trim()
        if (query.isEmpty()) return

        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            searchVideos(query, token)
                .onSuccess { page ->
                    // append 去重：保留首次出現、維持既有順序。
                    // 深頁（結果池枯竭）可能跨頁重複 videoId，若不去重會讓
                    // LazyColumn(key = { it.videoId }) 因 duplicate key 拋
                    // IllegalArgumentException 而崩潰；去重同時避免視覺重複。
                    val existing = _state.value.results
                    val existingIds = existing.mapTo(HashSet()) { it.videoId }
                    var appended = 0
                    var duplicated = 0
                    val merged = buildList {
                        addAll(existing)
                        for (video in page.results) {
                            if (existingIds.add(video.videoId)) {
                                add(video)
                                appended++
                            } else {
                                duplicated++
                            }
                        }
                    }

                    // 「token 未推進 → 視為到底」guard：若 data 層回傳的下一頁
                    // token 與本次 sent 的 token 相同（YT 回聲續頁），表示沒有真正
                    // 前進，中斷潛在輪迴並視為已到底，避免 UI 死循環。
                    val reachedEnd = page.nextPageToken != null &&
                        page.nextPageToken == token

                    val nextToken = if (reachedEnd) null else page.nextPageToken

                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            results = merged,
                            nextPageToken = nextToken
                        )
                    }

                    Log.i(
                        "SearchPaging",
                        "APPEND q=$query fetched=${page.results.size} " +
                            "appended=$appended dup=$duplicated " +
                            "next=${nextToken?.take(12) ?: ">"}"
                    )

                    // 載入更多回空頁 / token 未推進：視為已到底，UI 呈現「已無更多」
                    if (page.results.isEmpty() || reachedEnd) {
                        _messages.tryEmit("已無更多結果")
                    }
                }
                .onFailure { e ->
                    // 失敗不破壞既有結果，保留現有 nextPageToken 供使用者重試
                    _state.update { it.copy(isLoadingMore = false, error = e.message) }
                    _messages.tryEmit("載入更多失敗：${e.message ?: "未知錯誤"}")
                }
        }
    }

    private fun addVideoToPlaylist(item: PlaylistItem, playlistId: Long) {
        viewModelScope.launch {
            runCatching { addToPlaylist(playlistId, item) }
                .onSuccess { _messages.tryEmit("已加入播放清單") }
                .onFailure { _messages.tryEmit("加入失敗：${it.message}") }
        }
    }

    fun createPlaylistAndAdd(name: String, video: VideoResult) {
        viewModelScope.launch {
            runCatching {
                val newId = createPlaylist(name)
                addToPlaylist(newId, video.toPlaylistItem(newId))
            }
                .onSuccess { _messages.tryEmit("已建立「$name」並加入歌曲") }
                .onFailure { _messages.tryEmit("建立失敗：${it.message}") }
        }
    }
}

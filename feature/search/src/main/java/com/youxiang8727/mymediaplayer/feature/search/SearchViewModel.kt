package com.youxiang8727.mymediaplayer.feature.search

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchVideosUseCase
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val searched: Boolean = false
)

sealed interface SearchIntent {
    data class QueryChanged(val value: String) : SearchIntent
    data object Search : SearchIntent
    data object LoadMore : SearchIntent
    data class AddToPlaylist(val video: VideoResult, val playlistId: Long) : SearchIntent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVideos: SearchVideosUseCase,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    observePlaylists: ObservePlaylistsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        observePlaylists()
            .onEach { list -> _playlists.value = list }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> _state.update { it.copy(query = intent.value) }
            SearchIntent.Search -> doSearch()
            SearchIntent.LoadMore -> loadMore()
            is SearchIntent.AddToPlaylist -> addVideoToPlaylist(
                intent.video.toPlaylistItem(intent.playlistId),
                intent.playlistId
            )
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

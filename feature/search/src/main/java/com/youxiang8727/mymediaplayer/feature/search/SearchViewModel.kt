package com.youxiang8727.mymediaplayer.feature.search

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SearchVideosUseCase
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
    val error: String? = null,
    val searched: Boolean = false
)

sealed interface SearchIntent {
    data class QueryChanged(val value: String) : SearchIntent
    data object Search : SearchIntent
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
            is SearchIntent.AddToPlaylist -> addVideoToPlaylist(
                intent.video.toPlaylistItem(intent.playlistId),
                intent.playlistId
            )
        }
    }

    private fun doSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(isLoading = true, error = null, searched = true) }
        viewModelScope.launch {
            searchVideos(query)
                .onSuccess { list ->
                    _state.update {
                        it.copy(isLoading = false, results = list)
                    }
                    if (list.isEmpty()) _messages.tryEmit("查無結果")
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    _messages.tryEmit("搜尋失敗：${e.message ?: "未知錯誤"}")
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

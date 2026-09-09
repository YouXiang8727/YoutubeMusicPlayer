package com.youxiang8727.mymediaplayer.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchTrendingSongsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
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

/** 單一區域的熱門榜單狀態。 */
data class TrendingState(
    val items: List<VideoResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class DiscoverUiState(
    val trendingByRegion: Map<ChartRegion, TrendingState> = emptyMap()
)

sealed interface DiscoverIntent {
    data object TrendingRetry : DiscoverIntent
    data class AddToPlaylist(val video: VideoResult, val playlistId: Long) : DiscoverIntent
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val fetchTrendingSongs: FetchTrendingSongsUseCase,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    observePlaylists: ObservePlaylistsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        observePlaylists()
            .onEach { list -> _playlists.value = list }
            .launchIn(viewModelScope)
        fetchTrending()
    }

    fun onIntent(intent: DiscoverIntent) {
        when (intent) {
            DiscoverIntent.TrendingRetry -> fetchTrending()
            is DiscoverIntent.AddToPlaylist -> addVideoToPlaylist(
                intent.video.toPlaylistItem(intent.playlistId),
                intent.playlistId
            )
        }
    }

    /**
     * 抓取各區域熱門音樂榜單；各區域獨立載入，失敗僅寫入對應 [TrendingState.error]。
     *
     * 重入策略：各區域自己的 loading 旗標即重入 guard——本次呼叫只對未載入中
     * （`loading == false`，含失敗待重試）的區域發起抓取，已載入中的區域不重複發起。
     */
    private fun fetchTrending() {
        val current = _state.value.trendingByRegion
        val toFetch = ChartRegion.DISPLAY_ORDER.filter { region ->
            current[region]?.loading != true
        }
        if (toFetch.isEmpty()) return
        _state.update { st ->
            st.copy(
                trendingByRegion = st.trendingByRegion +
                    toFetch.associateWith { TrendingState(loading = true) }
            )
        }
        viewModelScope.launch {
            for (region in toFetch) {
                launch {
                    fetchTrendingSongs(region)
                        .onSuccess { list ->
                            _state.update { st ->
                                st.copy(
                                    trendingByRegion = st.trendingByRegion +
                                        (region to TrendingState(items = list))
                                )
                            }
                        }
                        .onFailure { e ->
                            _state.update { st ->
                                st.copy(
                                    trendingByRegion = st.trendingByRegion +
                                        (region to TrendingState(error = e.message))
                                )
                            }
                        }
                }
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

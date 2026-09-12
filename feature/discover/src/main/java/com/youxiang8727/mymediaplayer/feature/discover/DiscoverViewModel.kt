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
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchRecommendationsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.FetchTrendingSongsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObserveRecentPlaylistItemsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** 「為你推薦」區塊狀態（種子＝最近加入播放清單的歌曲）。 */
data class RecommendationState(
    val items: List<VideoResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val seedEmpty: Boolean = false
)

data class DiscoverUiState(
    val trendingByRegion: Map<ChartRegion, TrendingState> = emptyMap(),
    val recommendation: RecommendationState = RecommendationState()
)

sealed interface DiscoverIntent {
    data object TrendingRetry : DiscoverIntent
    data class AddToPlaylist(val video: VideoResult, val playlistId: Long) : DiscoverIntent
    data object RecommendationRefresh : DiscoverIntent
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val fetchTrendingSongs: FetchTrendingSongsUseCase,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    observePlaylists: ObservePlaylistsUseCase,
    private val fetchRecommendations: FetchRecommendationsUseCase,
    private val observeRecentItems: ObserveRecentPlaylistItemsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** 最近一次使用的推薦種子（「換一批」以其為基礎重新抓取）。 */
    private var seeds: List<PlaylistItem> = emptyList()

    init {
        observePlaylists()
            .onEach { list -> _playlists.value = list }
            .launchIn(viewModelScope)
        observeRecentSeeds()
        fetchTrending()
    }

    fun onIntent(intent: DiscoverIntent) {
        when (intent) {
            DiscoverIntent.TrendingRetry -> fetchTrending()
            is DiscoverIntent.AddToPlaylist -> addVideoToPlaylist(
                intent.video.toPlaylistItem(intent.playlistId),
                intent.playlistId
            )
            DiscoverIntent.RecommendationRefresh -> refreshRecommendations()
        }
    }

    /**
     * 觀察「最近加入播放清單」的歌曲作為「為你推薦」種子（最多 [FetchRecommendationsUseCase.SEED_LIMIT] 首）。
     *
     * 種子變化（[distinctUntilChanged] 過濾同一清單重複 emit）即自動重新產生推薦；
     * 種子為空（播放清單還沒有歌曲）時只更新 `seedEmpty` 空狀態，不觸發網路抓取。
     */
    private fun observeRecentSeeds() {
        observeRecentItems(FetchRecommendationsUseCase.SEED_LIMIT)
            .distinctUntilChanged()
            .onEach { newSeeds -> onSeedsChanged(newSeeds) }
            .launchIn(viewModelScope)
    }

    private fun onSeedsChanged(newSeeds: List<PlaylistItem>) {
        seeds = newSeeds
        if (newSeeds.isEmpty()) {
            _state.update { st ->
                st.copy(recommendation = RecommendationState(seedEmpty = true))
            }
        } else {
            fetchRecommendations(newSeeds)
        }
    }

    /**
     * 以 [seeds] 抓取「為你推薦」並寫入 [DiscoverUiState.recommendation]。
     *
     * 重入策略：整體 loading 旗標即重入 guard——loading 中不重複發起
     * （含「換一批」手動觸發與種子自動更新），避免併發覆寫結果。
     */
    private fun fetchRecommendations(seeds: List<PlaylistItem>) {
        if (_state.value.recommendation.loading) return
        _state.update { st ->
            st.copy(recommendation = RecommendationState(loading = true, seedEmpty = false))
        }
        viewModelScope.launch {
            fetchRecommendations(seeds, FetchRecommendationsUseCase.RECOMMENDATION_LIMIT)
                .onSuccess { list ->
                    _state.update { st ->
                        st.copy(recommendation = RecommendationState(items = list))
                    }
                }
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(recommendation = RecommendationState(error = e.message))
                    }
                }
        }
    }

    /**
     * 「換一批」：以最近一次種子（[seeds]）重新抓取。種子為空（`seedEmpty`
     * 空狀態下 UI 不顯示該按鈕）時為 no-op；loading 中由重入 guard 擋下。
     */
    private fun refreshRecommendations() {
        if (seeds.isEmpty()) return
        fetchRecommendations(seeds)
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
                .onFailure { e ->
                    if (e is PlaylistNameConflictException) {
                        // 重名為資料層可預期之結果：直接提示（例外在 addToPlaylist 前拋出，不會以 -1 加入）
                        _messages.tryEmit(e.message ?: "已存在同名歌單")
                    } else {
                        _messages.tryEmit("建立失敗：${e.message}")
                    }
                }
        }
    }
}

package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.ClearPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistItemsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.RemoveFromPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ShufflePlayPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
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
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlistId: Long = 0L,
    val playlistName: String = "",
    val items: List<PlaylistItem> = emptyList(),
    /** 歌名關鍵字篩選字串；空／空白代表不篩選。 */
    val query: String = "",
    val isLoading: Boolean = true,
    val failedCount: Int = 0
) {
    /**
     * 依 [query] 過濾後的清單；query 空白時為全部（維持原順序）。
     *
     * **只比對歌名**（[PlaylistItem.title]）：不比頻道、不比時長、不比播放失敗狀態。
     * 比對規則 = `title.contains(query.trim(), ignoreCase = true)`：
     * - `trim()` 讓使用者手打的前後空白不影響結果；
     * - `ignoreCase` 讓中文以外（英文歌名／頻道式標題）的大小寫不影響結果。
     *
     * 刻意做成**純函式派生屬性**（不進 constructor、不存進 StateFlow）：
     * 篩選結果可由 [items] 與 [query] 完全推導，無額外真實來源，
     * 因此單元測試直接斷言此屬性即可，不需 coroutine / dispatcher。
     */
    val visibleItems: List<PlaylistItem>
        get() = if (query.isBlank()) {
            items
        } else {
            items.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
}

sealed interface PlaylistDetailIntent {
    data class Remove(val videoId: String) : PlaylistDetailIntent
    data class Play(val item: PlaylistItem) : PlaylistDetailIntent
    data object ClearAll : PlaylistDetailIntent
    data object ShufflePlay : PlaylistDetailIntent
    /** 更新歌名關鍵字篩選字串（由輸入框每次變動發出）。 */
    data class SetQuery(val value: String) : PlaylistDetailIntent
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observePlaylistItems: ObservePlaylistItemsUseCase,
    private val removeFromPlaylist: RemoveFromPlaylistUseCase,
    private val clearPlaylist: ClearPlaylistUseCase,
    private val shufflePlayPlaylist: ShufflePlayPlaylistUseCase,
    private val playerController: PlayerController
) : ViewModel() {

    private val playlistId: Long =
        savedStateHandle.get<Long>("playlistId") ?: 0L
    private val playlistName: String =
        savedStateHandle.get<String>("name").orEmpty()

    private val _state = MutableStateFlow(
        PlaylistDetailUiState(
            playlistId = playlistId,
            playlistName = playlistName,
            // 旋轉／重建 ViewModel 後還原先前的篩選字串
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty()
        )
    )
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        if (playlistId > 0L) {
            observePlaylistItems(playlistId)
                .onEach { items ->
                    _state.value = _state.value.copy(
                        items = items,
                        isLoading = false,
                        failedCount = items.count { it.streamFailedAt != null }
                    )
                }
                .launchIn(viewModelScope)
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun onIntent(intent: PlaylistDetailIntent) {
        when (intent) {
            is PlaylistDetailIntent.SetQuery -> {
                // 純狀態寫入，不涉及 IO，因此刻意不 launch coroutine。
                // 寫入 SavedStateHandle 的是**原始值**（不 trim）：
                // trim 只在 visibleItems 比對與文案顯示時處理，避免把使用者
                // 正在輸入的字串（含尾端空白）改寫成另一個值而與畫面游標脫節。
                savedStateHandle[KEY_QUERY] = intent.value
                _state.value = _state.value.copy(query = intent.value)
            }

            is PlaylistDetailIntent.Remove -> viewModelScope.launch {
                removeFromPlaylist(playlistId, intent.videoId)
                _messages.tryEmit("已從播放清單移除")
            }

            // 點擊歌曲：以整份歌單為暫時性佇列，從點擊曲起播（不進播放頁）
            is PlaylistDetailIntent.Play -> {
                val items = _state.value.items
                if (items.isEmpty()) {
                    _messages.tryEmit("清單為空，無法播放")
                } else {
                    val startIndex = items
                        .indexOfFirst { it.videoId == intent.item.videoId }
                        .coerceAtLeast(0)
                    playerController.playQueue(
                        items = items.map { PlayQueueItem(videoId = it.videoId, title = it.title) },
                        startIndex = startIndex
                    )
                }
            }

            PlaylistDetailIntent.ClearAll -> viewModelScope.launch {
                clearPlaylist(playlistId)
                _messages.tryEmit("播放清單已清空")
            }

            PlaylistDetailIntent.ShufflePlay -> viewModelScope.launch {
                val current = _state.value
                if (current.query.isNotBlank()) {
                    // 篩選生效時**以可見項目作為播放佇列**，並從其中隨機一比起播。
                    // 理由是語意要誠實：按鈕標示「隨機播放（N 首符合篩選）」，
                    // 畫面上看到的就是那 N 首，實際播放的佇列也必須正好是那 N 首，
                    // 三者（數字／畫面／佇列長度）才一致。
                    //
                    // ⚠️ 不可用 playerController.play(videoId, title)：那條路徑會發
                    // ACTION_PLAY 給 MusicService，PlaybackQueueBuilder 會把
                    // 「整份歌單」當成佇列、只從該曲起播 → 按鈕寫 3 首但佇列 10 首，
                    // 正是要避免的數字／行為不一致。
                    val visible = current.visibleItems
                    if (visible.isEmpty()) {
                        _messages.tryEmit("清單為空，無法隨機播放")
                    } else {
                        val startIndex = visible.indices.random()
                        playerController.playQueue(
                            items = visible.map {
                                PlayQueueItem(videoId = it.videoId, title = it.title)
                            },
                            startIndex = startIndex
                        )
                        _messages.tryEmit("隨機播放：${visible[startIndex].title}")
                    }
                } else {
                    // 未篩選：維持既有路徑（整份歌單為佇列、隨機起播位置），不動。
                    shufflePlayPlaylist(playlistId)
                        ?.let { item ->
                            playerController.play(item.videoId, item.title)
                            _messages.tryEmit("隨機播放：${item.title}")
                        }
                        ?: _messages.tryEmit("清單為空，無法隨機播放")
                }
            }
        }
    }

    private companion object {
        /** [androidx.lifecycle.SavedStateHandle] 中存放篩選字串的 key。 */
        const val KEY_QUERY = "playlistQuery"
    }
}

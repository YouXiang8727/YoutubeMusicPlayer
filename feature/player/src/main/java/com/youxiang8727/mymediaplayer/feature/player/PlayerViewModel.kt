package com.youxiang8727.mymediaplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.SaveQueueAsPlaylistResult
import com.youxiang8727.mymediaplayer.core.domain.usecase.SaveQueueAsPlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 迷你播放列共用的播放意圖。[Play] 另供外部列表（如搜尋結果）直接起播。 */
sealed interface PlaybackIntent {
    data object TogglePlayPause : PlaybackIntent
    data object Next : PlaybackIntent
    data object Previous : PlaybackIntent
    data object ToggleShuffle : PlaybackIntent
    data object CycleRepeat : PlaybackIntent
    data class Seek(val positionMs: Long) : PlaybackIntent

    /** 跳轉至佇列指定索引播放（MiniPlayerBar 展開佇列點擊）。 */
    data class SeekToIndex(val index: Int) : PlaybackIntent

    /** 從佇列移除指定索引曲目（MiniPlayerBar 展開佇列）。 */
    data class RemoveFromQueue(val index: Int) : PlaybackIntent

    /** 清空目前播放佇列。 */
    data object ClearQueue : PlaybackIntent

    /**
     * 直接起播指定影片，不導航至播放頁（本專案已無全螢幕播放頁）。
     * 由 activity scope 的 PlayerViewModel（app 容器層）轉 call PlayerController.play，
     * 供外部列表（如搜尋結果）直接起播、不導航至播放頁。
     */
    data class Play(val videoId: String, val title: String) : PlaybackIntent

    /**
     * 播放一組暫時性佇列（熱門榜單等非 Room 清單），從 startIndex 起播整份清單。
     * 由 activity scope 的 PlayerViewModel（app 容器層）轉 call PlayerController.playQueue。
     */
    data class PlayList(val entries: List<PlayQueueItem>, val startIndex: Int) : PlaybackIntent

    /**
     * 將單曲加入目前播放佇列尾端（append-only，不中斷目前播放）。
     * 由 activity scope 的 PlayerViewModel（app 容器層）轉 call PlayerController.addToQueue，
     * 供外部列表（搜尋結果、熱門榜單卡片）的「加入佇列」按鈕使用。
     */
    data class AddToQueue(val videoId: String, val title: String) : PlaybackIntent

    /**
     * 把目前的播放佇列存成一個新的播放清單（名稱由使用者於 Dialog 輸入）。
     *
     * 佇列內容不由此參數攜入：ViewModel 已有 [PlayerViewModel.queue]，
     * 由 VM 自取快照可避免 UI 與實際佇列之間的時序落差（Dialog 開著時使用者
     * 仍可能清空佇列），也省去整份佇列的跨層拷貝。
     */
    data class SaveQueueAsPlaylist(val name: String) : PlaybackIntent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val saveQueueAsPlaylist: SaveQueueAsPlaylistUseCase,
    observePlaylists: ObservePlaylistsUseCase
) : ViewModel() {

    /** 來自 MediaSession 的即時播放狀態（MiniPlayerBar 與通知共用同一狀態源）。 */
    val playback: StateFlow<com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot> =
        playerController.playback

    /** 目前播放佇列（供 MiniPlayerBar 展開顯示）。 */
    val queue: StateFlow<List<PlayQueueItem>> = playerController.queue

    /**
     * 一次性使用者提示（snackbar 文案），由 UI 層（app 容器層）收集並顯示。
     *
     * 慣例與 SearchViewModel / DiscoverViewModel / PlaylistListViewModel 一致：
     * `extraBufferCapacity = 1` + replay 0，UI 須在觸發動作前就開始收集。
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * 既有歌單名稱集合，供 `CreatePlaylistDialog` 的 `existingNames` 做即時防重名。
     *
     * 這是**防呆**層，不是唯一防線：Dialog 關閉到建立之間存在時間差，且其他裝置
     * （匯入）也可能新增同名歌單，故仍以 domain 回的
     * [SaveQueueAsPlaylistResult.NameConflict] 為最終裁決。
     */
    private val _playlistNames = MutableStateFlow<Set<String>>(emptySet())
    val playlistNames: StateFlow<Set<String>> = _playlistNames.asStateFlow()

    init {
        observePlaylists()
            .map { list -> list.mapTo(HashSet()) { it.name } }
            .onEach { names -> _playlistNames.value = names }
            .launchIn(viewModelScope)
    }

    /** 給 MiniPlayerBar（activity scope）使用；[PlaybackIntent.Play] 供外部列表直接起播。 */
    fun onPlaybackIntent(intent: PlaybackIntent) {
        when (intent) {
            is PlaybackIntent.TogglePlayPause -> playerController.togglePlayPause()
            is PlaybackIntent.Next -> playerController.seekToNext()
            is PlaybackIntent.Previous -> playerController.seekToPrevious()
            is PlaybackIntent.ToggleShuffle -> playerController.toggleShuffle()
            is PlaybackIntent.CycleRepeat -> playerController.cycleRepeatMode()
            is PlaybackIntent.Seek -> playerController.seekTo(intent.positionMs)
            is PlaybackIntent.SeekToIndex -> playerController.seekToIndex(intent.index)
            is PlaybackIntent.RemoveFromQueue -> playerController.removeFromQueue(intent.index)
            is PlaybackIntent.ClearQueue -> playerController.clearQueue()
            is PlaybackIntent.Play -> playerController.play(intent.videoId, intent.title)
            is PlaybackIntent.PlayList -> playerController.playQueue(intent.entries, intent.startIndex)
            is PlaybackIntent.AddToQueue -> playerController.addToQueue(intent.videoId, intent.title)
            is PlaybackIntent.SaveQueueAsPlaylist -> saveQueueToPlaylist(intent.name)
        }
    }

    /**
     * 將目前播放佇列存成新歌單，結果以 [messages] 告知使用者。
     *
     * 採用 `try/catch` 並在 `catch (e: CancellationException) { throw e }` 先行穿透，
     * **不使用** `runCatching { }.onFailure { }`：後者的 `onFailure` 對
     * `CancellationException` 同樣會被觸發，於是 ViewModel scope 被取消（使用者已
     * 離開頁面）時反而會在一個已取消的 scope 中 emit 失敗 snackbar。
     * 先行 rethrow 也讓結構化並發維持正確：取消是「訊號」而非可顯示的錯誤。
     *
     * 佇列為空時由 domain 回 [SaveQueueAsPlaylistResult.Failure]（可預期邊界條件），
     * UI 端入口另有 `enabled = queue.isNotEmpty()` 把關，此處不重複判斷。
     */
    private fun saveQueueToPlaylist(name: String) {
        val queueSnapshot = queue.value
        viewModelScope.launch {
            try {
                when (val result = saveQueueAsPlaylist(name, queueSnapshot)) {
                    is SaveQueueAsPlaylistResult.Success -> {
                        // 佇列刻意允許重複（append-only 語意），歌單則因 (playlistId, videoId)
                        // 複合主鍵只能一列。這個落差必須如實告知，否則使用者存了 10 首
                        // 只得到 7 首卻被告知「已建立」，是誤導而非簡潔。
                        _messages.tryEmit(
                            if (result.mergedCount > 0) {
                                "已建立「${result.playlistName}」，其中 ${result.mergedCount} 首重複曲目已合併"
                            } else {
                                "已建立「${result.playlistName}」"
                            }
                        )
                    }
                    // 重名與失敗的 message 皆為 domain 層已領域化的可顯示文案，直接透傳，
                    // UI 層不二次加工（避免同一件事在兩處有兩種說法）。
                    is SaveQueueAsPlaylistResult.NameConflict -> _messages.tryEmit(result.message)
                    is SaveQueueAsPlaylistResult.Failure -> _messages.tryEmit(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("存為播放清單失敗：${e.message ?: "未知錯誤"}")
            }
        }
    }
}

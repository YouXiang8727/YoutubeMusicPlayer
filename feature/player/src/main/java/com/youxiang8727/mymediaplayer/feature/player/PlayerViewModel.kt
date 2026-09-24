package com.youxiang8727.mymediaplayer.feature.player

import androidx.lifecycle.ViewModel
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

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
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {

    /** 來自 MediaSession 的即時播放狀態（MiniPlayerBar 與通知共用同一狀態源）。 */
    val playback: StateFlow<com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot> =
        playerController.playback

    /** 目前播放佇列（供 MiniPlayerBar 展開顯示）。 */
    val queue: StateFlow<List<PlayQueueItem>> = playerController.queue

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
        }
    }
}

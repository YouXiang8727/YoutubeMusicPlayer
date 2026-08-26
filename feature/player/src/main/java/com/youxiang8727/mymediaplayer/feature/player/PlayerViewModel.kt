package com.youxiang8727.mymediaplayer.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.feature.player.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val videoId: String = "",
    val title: String = ""
)

/** 迷你播放列 / 播放頁共用的播放意圖。 */
sealed interface PlaybackIntent {
    data object TogglePlayPause : PlaybackIntent
    data object Next : PlaybackIntent
    data object Previous : PlaybackIntent
    data object ToggleShuffle : PlaybackIntent
    data object CycleRepeat : PlaybackIntent
    data class Seek(val positionMs: Long) : PlaybackIntent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val playerController: PlayerController
) : ViewModel() {

    val state: PlayerUiState = PlayerUiState(
        videoId = savedStateHandle.get<String>("videoId").orEmpty(),
        title = savedStateHandle.get<String>("title").orEmpty()
    )

    /** 來自 MediaSession 的即時播放狀態（MiniPlayerBar 與通知共用同一狀態源）。 */
    val playback: StateFlow<com.youxiang8727.mymediaplayer.feature.player.playback.PlaybackSnapshot> =
        playerController.playback

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun startBackgroundPlayback() {
        playerController.play(state.videoId, state.title.ifBlank { state.videoId })
        _messages.tryEmit("已啟動背景播放")
    }

    fun stopBackgroundPlayback() {
        playerController.stop()
        _messages.tryEmit("已停止背景播放")
    }

    /** 給 MiniPlayerBar（activity scope）使用：以目前播放中的曲目為準。 */
    fun onPlaybackIntent(intent: PlaybackIntent) {
        when (intent) {
            is PlaybackIntent.TogglePlayPause -> playerController.togglePlayPause()
            is PlaybackIntent.Next -> playerController.seekToNext()
            is PlaybackIntent.Previous -> playerController.seekToPrevious()
            is PlaybackIntent.ToggleShuffle -> playerController.toggleShuffle()
            is PlaybackIntent.CycleRepeat -> playerController.cycleRepeatMode()
            is PlaybackIntent.Seek -> playerController.seekTo(intent.positionMs)
        }
    }

    fun onAddToPlaylist(item: PlaylistItem) {
        viewModelScope.launch {
            runCatching { addToPlaylist(item) }
                .onSuccess { _messages.tryEmit("已加入播放清單") }
                .onFailure { _messages.tryEmit("加入失敗：${it.message}") }
        }
    }
}

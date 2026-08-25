package com.youxiang8727.mymediaplayer.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.AddToPlaylistUseCase
import com.youxiang8727.mymediaplayer.feature.player.service.MusicService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val videoId: String = "",
    val title: String = ""
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val musicServiceController: MusicServiceController
) : ViewModel() {

    val state: PlayerUiState = PlayerUiState(
        videoId = savedStateHandle.get<String>("videoId").orEmpty(),
        title = savedStateHandle.get<String>("title").orEmpty()
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun startBackgroundPlayback() {
        musicServiceController.start(state.videoId, state.title.ifBlank { state.videoId })
        _messages.tryEmit("已啟動背景播放")
    }

    fun stopBackgroundPlayback() {
        musicServiceController.stop()
        _messages.tryEmit("已停止背景播放")
    }

    fun onAddToPlaylist(item: PlaylistItem) {
        viewModelScope.launch {
            runCatching { addToPlaylist(item) }
                .onSuccess { _messages.tryEmit("已加入播放清單") }
                .onFailure { _messages.tryEmit("加入失敗：${it.message}") }
        }
    }
}

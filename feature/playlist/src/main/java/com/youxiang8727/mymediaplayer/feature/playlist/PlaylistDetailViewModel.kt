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
    val isLoading: Boolean = true,
    val failedCount: Int = 0
)

sealed interface PlaylistDetailIntent {
    data class Remove(val videoId: String) : PlaylistDetailIntent
    data class Play(val item: PlaylistItem) : PlaylistDetailIntent
    data object ClearAll : PlaylistDetailIntent
    data object ShufflePlay : PlaylistDetailIntent
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
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
            playlistName = playlistName
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

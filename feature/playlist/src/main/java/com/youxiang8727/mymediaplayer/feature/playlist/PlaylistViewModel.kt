package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.usecase.ClearPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.RemoveFromPlaylistUseCase
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

data class PlaylistUiState(
    val items: List<PlaylistItem> = emptyList(),
    val isLoading: Boolean = true
)

sealed interface PlaylistIntent {
    data class Remove(val videoId: String) : PlaylistIntent
    data object ClearAll : PlaylistIntent
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    observePlaylist: ObservePlaylistUseCase,
    private val removeFromPlaylist: RemoveFromPlaylistUseCase,
    private val clearPlaylist: ClearPlaylistUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        observePlaylist()
            .onEach { items -> _state.value = PlaylistUiState(items = items, isLoading = false) }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: PlaylistIntent) {
        when (intent) {
            is PlaylistIntent.Remove -> viewModelScope.launch {
                removeFromPlaylist(intent.videoId)
                _messages.tryEmit("已從播放清單移除")
            }
            PlaylistIntent.ClearAll -> viewModelScope.launch {
                clearPlaylist()
                _messages.tryEmit("播放清單已清空")
            }
        }
    }
}

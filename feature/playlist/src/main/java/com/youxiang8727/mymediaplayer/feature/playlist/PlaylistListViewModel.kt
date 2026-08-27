package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.DeletePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.RenamePlaylistUseCase
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

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true
)

sealed interface PlaylistListIntent {
    data class Create(val name: String) : PlaylistListIntent
    data class Delete(val id: Long) : PlaylistListIntent
    data class Rename(val id: Long, val name: String) : PlaylistListIntent
}

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    observePlaylists: ObservePlaylistsUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    private val deletePlaylist: DeletePlaylistUseCase,
    private val renamePlaylist: RenamePlaylistUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistListUiState())
    val state: StateFlow<PlaylistListUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        observePlaylists()
            .onEach { playlists ->
                _state.update {
                    it.copy(playlists = playlists, isLoading = false)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: PlaylistListIntent) {
        when (intent) {
            is PlaylistListIntent.Create -> viewModelScope.launch {
                createPlaylist(intent.name)
                _messages.tryEmit("已建立「${intent.name}」")
            }

            is PlaylistListIntent.Delete -> viewModelScope.launch {
                deletePlaylist(intent.id)
                _messages.tryEmit("已刪除播放清單")
            }

            is PlaylistListIntent.Rename -> viewModelScope.launch {
                renamePlaylist(intent.id, intent.name)
                _messages.tryEmit("已重新命名為「${intent.name}」")
            }
        }
    }
}

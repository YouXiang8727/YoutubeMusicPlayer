package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.DeletePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ExportAllPlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ExportPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ImportPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
import com.youxiang8727.mymediaplayer.core.domain.usecase.RenamePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
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
    data class Export(val id: Long) : PlaylistListIntent
    data object ExportAll : PlaylistListIntent
    data class Import(val json: String) : PlaylistListIntent
}

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    observePlaylists: ObservePlaylistsUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    private val deletePlaylist: DeletePlaylistUseCase,
    private val renamePlaylist: RenamePlaylistUseCase,
    private val exportPlaylist: ExportPlaylistUseCase,
    private val exportAllPlaylists: ExportAllPlaylistsUseCase,
    private val importPlaylist: ImportPlaylistUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistListUiState())
    val state: StateFlow<PlaylistListUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 匯出產生的 JSON 字串（僅 Export intent 會 emit；供 UI 透過 SAF 存檔）。 */
    private val _exportResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportResult: SharedFlow<String> = _exportResult.asSharedFlow()

    /** 目前等待使用者決策的匯入名稱衝突；null = 無彈窗。 */
    private val _importConflict = MutableStateFlow<ImportConflictInfo?>(null)
    val importConflict: StateFlow<ImportConflictInfo?> = _importConflict.asStateFlow()

    /** 等待中衝突決策的 deferred（onConflict 暫停於 await，UI 決策後 complete）。 */
    private var pendingDecision: CompletableDeferred<ImportConflictDecision>? = null

    /** 「全部套用」快取：上次勾選的決策，之後衝突直接沿用不再彈窗；每次 Import 開始重設。 */
    private var applyAllDecision: ImportConflictDecision? = null

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
                runCatching { createPlaylist(intent.name) }
                    .onSuccess { _messages.tryEmit("已建立「${intent.name}」") }
                    .onFailure { e ->
                        if (e is PlaylistNameConflictException) {
                            _messages.tryEmit(e.message ?: "已存在同名歌單")
                        } else {
                            _messages.tryEmit("建立失敗：${e.message}")
                        }
                    }
            }

            is PlaylistListIntent.Delete -> viewModelScope.launch {
                deletePlaylist(intent.id)
                _messages.tryEmit("已刪除播放清單")
            }

            is PlaylistListIntent.Rename -> viewModelScope.launch {
                runCatching { renamePlaylist(intent.id, intent.name) }
                    .onSuccess { _messages.tryEmit("已重新命名為「${intent.name}」") }
                    .onFailure { e ->
                        if (e is PlaylistNameConflictException) {
                            _messages.tryEmit(e.message ?: "已存在同名歌單")
                        } else {
                            _messages.tryEmit("重新命名失敗：${e.message}")
                        }
                    }
            }

            is PlaylistListIntent.Export -> viewModelScope.launch {
                val json = exportPlaylist(intent.id)
                json?.let { _exportResult.tryEmit(it) } ?: _messages.tryEmit("找不到該歌單")
            }

            is PlaylistListIntent.ExportAll -> viewModelScope.launch {
                val json = exportAllPlaylists()
                if (json != null) _exportResult.tryEmit(json)
                else _messages.tryEmit("尚無任何歌單可匯出")
            }

            is PlaylistListIntent.Import -> viewModelScope.launch {
                applyAllDecision = null
                pendingDecision = null
                try {
                    val result = importPlaylist(intent.json) { info ->
                        // 上次勾選「全部套用」→ 直接沿用同一決策，不再彈窗詢問
                        applyAllDecision?.let { return@importPlaylist it }
                        _importConflict.value = info
                        val deferred = CompletableDeferred<ImportConflictDecision>()
                        pendingDecision = deferred
                        deferred.await()
                    }
                    _messages.tryEmit(
                        result?.let { formatImportResultMessage(it) }
                            ?: "匯入失敗：JSON 格式無法辨識"
                    )
                } finally {
                    _importConflict.value = null
                    pendingDecision = null
                    applyAllDecision = null
                }
            }
        }
    }

    /**
     * 使用者在匯入衝突彈窗做出決策。
     * @param applyToAll true = 本次匯入後續衝突一律沿用同一決策，不再逐個詢問。
     */
    fun onImportConflictDecision(decision: ImportConflictDecision, applyToAll: Boolean) {
        if (applyToAll) applyAllDecision = decision
        pendingDecision?.complete(decision)
    }

    /** 把匯入結果彙整為摘要 Snackbar 訊息（只列出非零計數項）。 */
    private fun formatImportResultMessage(result: PlaylistImportResult): String {
        val summary = buildList {
            if (result.created > 0) add("新增 ${result.created}")
            if (result.replaced > 0) add("取代 ${result.replaced}")
            if (result.keptBoth > 0) add("保留兩者 ${result.keptBoth}")
        }.joinToString("、")
        return when {
            result.cancelled && summary.isEmpty() -> "已取消匯入"
            result.cancelled -> "匯入已中斷：$summary"
            summary.isEmpty() -> "沒有任何歌單可匯入"
            else -> "已匯入：$summary"
        }
    }
}

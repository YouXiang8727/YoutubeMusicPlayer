package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 播放清單列表頁（無狀態） */
@Composable
fun PlaylistListScreen(
    state: PlaylistListUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (PlaylistListIntent) -> Unit,
    onExport: (playlistId: Long) -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit,
    onOpenPlaylist: (playlistId: Long, name: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增播放清單")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放清單（${state.playlists.size}）",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onExportAll) { Text("匯出全部") }
                    TextButton(onClick = onImport) { Text("匯入歌單") }
                }
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.playlists.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "尚無播放清單，點擊右下角 + 建立",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistListItem(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id, playlist.name) },
                            onRename = { showRenameDialog = playlist },
                            onDelete = { onIntent(PlaylistListIntent.Delete(playlist.id)) },
                            onExport = { onExport(playlist.id) }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) } // FAB space
                }
            }
        }

        // 建立新播放清單 Dialog
        if (showCreateDialog) {
            CreatePlaylistDialog(
                onConfirm = { name -> onIntent(PlaylistListIntent.Create(name)) },
                onDismiss = { showCreateDialog = false }
            )
        }

        // 重新命名 Dialog
        showRenameDialog?.let { playlist ->
            RenamePlaylistDialog(
                currentName = playlist.name,
                onConfirm = { newName ->
                    onIntent(PlaylistListIntent.Rename(playlist.id, newName))
                    showRenameDialog = null
                },
                onDismiss = { showRenameDialog = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val localeList = configuration.locales
    val locale = if (localeList.size() > 0) localeList[0] else Locale.getDefault()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", locale)
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "建立於 ${dateFormat.format(Date(playlist.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("匯出 JSON") },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重新命名") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("刪除") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新命名播放清單") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名稱") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()); onDismiss() },
                enabled = name.isNotBlank() && name.trim() != currentName
            ) { Text("確認") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 播放清單列表頁 Hilt 容器 */
@Composable
fun PlaylistListRoute(
    viewModel: PlaylistListViewModel = hiltViewModel(),
    onOpenPlaylist: (playlistId: Long, name: String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 匯出：拿到 JSON 後記住，等 SAF 存檔對話框回傳時寫入
    var pendingExportName by remember { mutableStateOf<String?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    // 匯入：挑選 .json 檔，讀取內容後送 VM
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (json != null) {
                viewModel.onIntent(PlaylistListIntent.Import(json))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // 匯出成功（JSON 就緒）→ 開 SAF 存檔對話框
    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { json ->
            pendingJson = json
            exportLauncher.launch(
                "MyMediaPlayer_${sanitizeFileName(pendingExportName.orEmpty())}.json"
            )
        }
    }

    PlaylistListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onExport = { id ->
            pendingExportName = state.playlists.firstOrNull { it.id == id }?.name
            viewModel.onIntent(PlaylistListIntent.Export(id))
        },
        onExportAll = {
            // 全部匯出統一使用備份檔名，不沿用單一歌單名稱
            val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            pendingExportName = "Backup_$date"
            viewModel.onIntent(PlaylistListIntent.ExportAll)
        },
        onImport = { importLauncher.launch("application/json") },
        onOpenPlaylist = onOpenPlaylist
    )
}

/** 過濾檔案名稱非法字元（Windows/Android 通用），空白名稱兜底為 playlist。 */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "playlist" }

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistList - Empty - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistList - Empty - Light"
)
@Composable
private fun PlaylistListScreenEmptyPreview() {
    MyMediaPlayerTheme {
        PlaylistListScreen(
            state = PlaylistListUiState(playlists = emptyList(), isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onExport = {},
            onExportAll = {},
            onImport = {},
            onOpenPlaylist = { _, _ -> }
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistList - Items - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistList - Items - Light"
)
@Composable
private fun PlaylistListScreenItemsPreview() {
    MyMediaPlayerTheme {
        PlaylistListScreen(
            state = PlaylistListUiState(
                isLoading = false,
                playlists = listOf(
                    Playlist(id = 1, name = "我的最愛", createdAt = 1690000000000L),
                    Playlist(id = 2, name = "工作播放清單", createdAt = 1690100000000L),
                    Playlist(id = 3, name = "運動音樂", createdAt = 1690200000000L)
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onExport = {},
            onExportAll = {},
            onImport = {},
            onOpenPlaylist = { _, _ -> }
        )
    }
}
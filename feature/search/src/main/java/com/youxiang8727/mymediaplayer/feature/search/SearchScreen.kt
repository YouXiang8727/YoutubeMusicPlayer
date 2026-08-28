package com.youxiang8727.mymediaplayer.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import com.youxiang8727.mymediaplayer.feature.playlist.CreatePlaylistDialog
import com.youxiang8727.mymediaplayer.feature.playlist.PlaylistPickerSheet

/** 無狀態 UI：狀態提升，便於 Preview 與測試。 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    playlists: List<Playlist>,
    snackbarHostState: SnackbarHostState,
    onIntent: (SearchIntent) -> Unit,
    onPlayVideo: (VideoResult) -> Unit,
    onCreatePlaylistAndAdd: (name: String, video: VideoResult) -> Unit
) {
    // 顯示播放清單選擇 BottomSheet（帶影片資料）
    var showPickerVideo by remember { mutableStateOf<VideoResult?>(null) }
    // 顯示建立新播放清單 Dialog（獨立於 BottomSheet 生命週期）
    var showCreateDialog by remember { mutableStateOf(false) }
    // 待建立清單後加入的影片（BottomSheet 關閉後仍需保留）
    var pendingCreateVideo by remember { mutableStateOf<VideoResult?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(SearchIntent.QueryChanged(it)) },
                label = { Text("搜尋影片") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onIntent(SearchIntent.Search) },
                enabled = !state.isLoading && state.query.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.isLoading) "搜尋中…" else "搜尋") }

            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.results.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.searched) "查無結果" else "輸入關鍵字開始搜尋",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.results, key = { it.videoId }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onPlayVideo(video) },
                            onAdd = { showPickerVideo = video }
                        )
                    }
                    item {
                        LoadMoreFooter(
                            nextPageToken = state.nextPageToken,
                            isLoadingMore = state.isLoadingMore,
                            onLoadMore = { onIntent(SearchIntent.LoadMore) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // 播放清單選擇 BottomSheet（僅控制 BottomSheet 顯示）
    showPickerVideo?.let { video ->
        PlaylistPickerSheet(
            playlists = playlists,
            onPlaylistSelected = { playlistId ->
                onIntent(SearchIntent.AddToPlaylist(video, playlistId))
                showPickerVideo = null
            },
            onCreateNew = {
                // 先保存影片，再關閉 BottomSheet
                pendingCreateVideo = video
                showPickerVideo = null
                showCreateDialog = true
            },
            onDismiss = { showPickerVideo = null }
        )
    }

    // 建立新播放清單 Dialog（獨立於 BottomSheet，生命週期不受影響）
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                pendingCreateVideo?.let { video ->
                    onCreatePlaylistAndAdd(name, video)
                }
                pendingCreateVideo = null
                showCreateDialog = false
            },
            onDismiss = {
                pendingCreateVideo = null
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun VideoCard(
    video: VideoResult,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp, 54.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp, 54.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.LightGray)
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.channel.isNotBlank()) {
                    Text(
                        text = video.channel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "加入播放清單")
            }
        }
    }
}

/**
 * LazyColumn 底部的「載入更多」footer。
 * 僅在仍有下一頁（nextPageToken != null）時顯示；載入中時 disabled 並顯示小型進度。
 */
@Composable
internal fun LoadMoreFooter(
    nextPageToken: String?,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (nextPageToken == null) {
        // 已到底：留出底部空間給 Spacer
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onLoadMore,
            enabled = !isLoadingMore
        ) {
            if (isLoadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(if (isLoadingMore) "載入中…" else "載入更多")
        }
    }
}

/** Hilt 容器：收集狀態與一次性訊息。 */
@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlayVideo: (VideoResult) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    SearchScreen(
        state = state,
        playlists = playlists,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onPlayVideo = onPlayVideo,
        onCreatePlaylistAndAdd = viewModel::createPlaylistAndAdd
    )
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Empty - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Empty - Light"
)
@Composable
private fun SearchScreenEmptyPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(),
            playlists = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Results - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Results - Light"
)
@Composable
private fun SearchScreenResultsPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(
                query = "周杰倫",
                results = listOf(
                    VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou"),
                    VideoResult("abc12345678", "夜曲 Live", "", "Official")
                ),
                searched = true
            ),
            playlists = listOf(
                Playlist(id = 1, name = "我的最愛"),
                Playlist(id = 2, name = "工作播放清單")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Results with LoadMore - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Results with LoadMore - Light"
)
@Composable
private fun SearchScreenResultsLoadMorePreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(
                query = "周杰倫",
                results = listOf(
                    VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou"),
                    VideoResult("abc12345678", "夜曲 Live", "", "Official")
                ),
                nextPageToken = "continuation-token-1",
                searched = true
            ),
            playlists = listOf(
                Playlist(id = 1, name = "我的最愛")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Results LoadingMore - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Results LoadingMore - Light"
)
@Composable
private fun SearchScreenResultsLoadingMorePreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(
                query = "周杰倫",
                results = listOf(
                    VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou"),
                    VideoResult("abc12345678", "夜曲 Live", "", "Official")
                ),
                nextPageToken = "continuation-token-1",
                isLoadingMore = true,
                searched = true
            ),
            playlists = listOf(
                Playlist(id = 1, name = "我的最愛")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

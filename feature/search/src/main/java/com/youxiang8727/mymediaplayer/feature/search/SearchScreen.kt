package com.youxiang8727.mymediaplayer.feature.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.ui.component.VideoThumbnailWithBadge
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

    // 已搜尋時攔截系統返回鍵為「清除搜尋」回到空狀態（最近搜尋）；未搜尋（searched == false）
    // 時 enabled = false 保持系統預設行為（退出 App）。
    BackHandler(enabled = state.searched) {
        onIntent(SearchIntent.QueryChanged(""))
    }

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
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { onIntent(SearchIntent.QueryChanged("")) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清除搜尋"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            // 搜尋建議（autocomplete）：輸入過程 debounce 載入，非空時顯示在搜尋框下方。
            if (state.suggestions.isNotEmpty()) {
                SuggestionList(
                    suggestions = state.suggestions,
                    onSelect = { onIntent(SearchIntent.SelectSuggestion(it)) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onIntent(SearchIntent.Search) },
                enabled = !state.isLoading && state.query.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.isLoading) "搜尋中…" else "搜尋") }

            Spacer(Modifier.height(12.dp))

            when {
                // 空狀態（尚未搜尋）：顯示最近搜尋紀錄；無紀錄時顯示提示文字
                !state.searched -> {
                    if (state.history.isNotEmpty()) {
                        // 標題列：「最近搜尋」＋清除全部
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "最近搜尋",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { onIntent(SearchIntent.ClearHistory) }
                            ) { Text("清除全部") }
                        }
                        // 每筆紀錄一列：icon + query（與 SuggestionList 視覺一致），
                        // 點擊行為等同點 autocomplete 建議：填回搜尋框並直接搜尋
                        state.history.forEach { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onIntent(SearchIntent.SelectSuggestion(query)) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            "輸入關鍵字開始搜尋",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.results.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "查無結果",
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

/**
 * 搜尋建議下拉清單（autocomplete）。唯讀呈現 [suggestions]，點擊任一項以 [onSelect] 回報
 * （由 ViewModel 填回搜尋框並觸發搜尋）。
 */
@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
            VideoThumbnailWithBadge(
                url = video.thumbnailUrl,
                duration = video.duration,
                modifier = Modifier.size(96.dp, 54.dp)
            )
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
    name = "SearchScreen - Suggestions - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Suggestions - Light"
)
@Composable
private fun SearchScreenSuggestionsPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(
                query = "周杰",
                suggestions = listOf(
                    "周杰倫",
                    "周杰倫 晴天",
                    "周杰倫 最新歌曲",
                    "周杰倫 演唱會"
                )
            ),
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
                    VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou", "4:30"),
                    VideoResult("abc12345678", "夜曲 Live", "", "Official", "3:12")
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
                    VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou", "4:30"),
                    VideoResult("abc12345678", "夜曲 Live", "", "Official", "3:12")
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
                    VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou", "4:30"),
                    VideoResult("abc12345678", "夜曲 Live", "", "Official", "3:12")
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

/*** 產生「最近搜尋」Preview 用假資料。 */
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - History - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - History - Light"
)
@Composable
private fun SearchScreenHistoryPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(
                history = listOf("周杰倫 晴天", "五月天", "IU 新歌", "YOASOBI")
            ),
            playlists = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

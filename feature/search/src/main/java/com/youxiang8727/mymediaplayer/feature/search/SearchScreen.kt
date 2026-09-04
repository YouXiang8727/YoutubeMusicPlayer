package com.youxiang8727.mymediaplayer.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.toPlayQueueItem
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
    onCreatePlaylistAndAdd: (name: String, video: VideoResult) -> Unit,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit = { _, _ -> }
) {
    // 顯示播放清單選擇 BottomSheet（帶影片資料）
    var showPickerVideo by remember { mutableStateOf<VideoResult?>(null) }
    // 顯示建立新播放清單 Dialog（獨立於 BottomSheet 生命週期）
    var showCreateDialog by remember { mutableStateOf(false) }
    // 待建立清單後加入的影片（BottomSheet 關閉後仍需保留）
    var pendingCreateVideo by remember { mutableStateOf<VideoResult?>(null) }
    // 熱門榜單 rail ⇄ 完整清單切換（同 feature:search 內以 state 切換，不新增 nav route）
    var showFullChart by remember { mutableStateOf(false) }

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
                // 空狀態（尚未搜尋）：熱門音樂榜單區塊（失敗不擋搜尋，搜尋框仍在上方可用）
                !state.searched -> {
                    Text(
                        "輸入關鍵字開始搜尋",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TrendingSection(
                        state = state,
                        showFullChart = showFullChart,
                        onShowFullChart = { showFullChart = true },
                        onBackToRail = { showFullChart = false },
                        onRetry = { onIntent(SearchIntent.TrendingRetry) },
                        onPlayChartQueue = onPlayChartQueue
                    )
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

/** 熱門榜單 rail 顯示筆數上限（詳情完整清單不截斷）。 */
private const val TRENDING_RAIL_LIMIT = 10

/**
 * 熱門音樂榜單區塊（僅在空狀態 `searched == false` 顯示）。
 * rail（前 [TRENDING_RAIL_LIMIT] 筆）⇄ 完整清單兩態以 [showFullChart] 切換；
 * 載入中／失敗（重試）／空榜單三態都有對應 UI。
 */
@Composable
private fun TrendingSection(
    state: SearchUiState,
    showFullChart: Boolean,
    onShowFullChart: () -> Unit,
    onBackToRail: () -> Unit,
    onRetry: () -> Unit,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit
) {
    when {
        state.trendingLoading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showFullChart) {
                    IconButton(onClick = onBackToRail) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回熱門列表")
                    }
                    Text(
                        "台灣熱門音樂",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        "台灣熱門音樂",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onShowFullChart) { Text("查看完整榜單") }
                }
            }

            when {
                state.trendingError != null -> TrendingError(
                    message = state.trendingError,
                    onRetry = onRetry
                )

                state.trendingItems.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暫無熱門歌曲",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                showFullChart -> ChartFullList(
                    items = state.trendingItems,
                    onPlayChartQueue = onPlayChartQueue
                )

                else -> ChartRail(
                    items = state.trendingItems,
                    onPlayChartQueue = onPlayChartQueue
                )
            }
        }
    }
}

/** 熱門榜單載入失敗（內嵌重試，不擋搜尋）。 */
@Composable
private fun TrendingError(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "熱門榜單載入失敗",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("重試") }
    }
}

/** rail（橫向捲動）：前 [TRENDING_RAIL_LIMIT] 筆，名次＋縮圖＋歌名＋歌手。 */
@Composable
private fun ChartRail(
    items: List<VideoResult>,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(items.take(TRENDING_RAIL_LIMIT), key = { _, v -> v.videoId }) { index, video ->
            ChartRailItem(
                rank = index + 1,
                video = video,
                onClick = {
                    // 以「整份榜單」為佇列從該曲起播（rail 只顯示前 N 筆，佇列仍是完整清單）
                    onPlayChartQueue(items.map { it.toPlayQueueItem() }, index)
                }
            )
        }
    }
}

/** 完整榜單（可捲動，顯示方式同播放清單詳情：名次＋縮圖＋歌名＋歌手）。 */
@Composable
private fun ChartFullList(
    items: List<VideoResult>,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(items, key = { _, v -> v.videoId }) { index, video ->
            ChartDetailRow(
                rank = index + 1,
                video = video,
                onClick = { onPlayChartQueue(items.map { it.toPlayQueueItem() }, index) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChartRailItem(
    rank: Int,
    video: VideoResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Box {
            ChartThumbnail(
                url = video.thumbnailUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
            // 名次徽章（疊於縮圖左上角）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(4.dp))
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
}

@Composable
private fun ChartDetailRow(
    rank: Int,
    video: VideoResult,
    onClick: () -> Unit
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
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp)
            )
            ChartThumbnail(
                url = video.thumbnailUrl,
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
        }
    }
}

/** 榜單縮圖（空白 URL 以主題色塊替代，placeholder/error 同播放清單慣例）。 */
@Composable
private fun ChartThumbnail(url: String, modifier: Modifier = Modifier) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.small),
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
    onPlayVideo: (VideoResult) -> Unit,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit = { _, _ -> }
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
        onCreatePlaylistAndAdd = viewModel::createPlaylistAndAdd,
        onPlayChartQueue = onPlayChartQueue
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

/** 產生榜單 Preview 用假資料（縮圖留空以主題色塊呈現，與既有 Preview 慣例一致）。 */
private fun trendingPreviewItems(count: Int): List<VideoResult> =
    List(count) { i -> VideoResult("trending-$i", "台灣熱門音樂 Top ${i + 1}", "", "歌手 $i") }

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Trending Loading - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Trending Loading - Light"
)
@Composable
private fun SearchScreenTrendingLoadingPreview() {
    MyMediaPlayerTheme {
        TrendingSection(
            state = SearchUiState(trendingLoading = true),
            showFullChart = false,
            onShowFullChart = {},
            onBackToRail = {},
            onRetry = {},
            onPlayChartQueue = { _, _ -> }
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
    name = "SearchScreen - Trending Rail - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Trending Rail - Light"
)
@Composable
private fun SearchScreenTrendingRailPreview() {
    MyMediaPlayerTheme {
        // 12 筆 > rail 上限 10，驗證截斷只顯示前 10 筆
        TrendingSection(
            state = SearchUiState(trendingItems = trendingPreviewItems(12)),
            showFullChart = false,
            onShowFullChart = {},
            onBackToRail = {},
            onRetry = {},
            onPlayChartQueue = { _, _ -> }
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
    name = "SearchScreen - Trending Full Chart - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Trending Full Chart - Light"
)
@Composable
private fun SearchScreenTrendingFullChartPreview() {
    MyMediaPlayerTheme {
        TrendingSection(
            state = SearchUiState(trendingItems = trendingPreviewItems(50)),
            showFullChart = true,
            onShowFullChart = {},
            onBackToRail = {},
            onRetry = {},
            onPlayChartQueue = { _, _ -> }
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
    name = "SearchScreen - Trending Error - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Trending Error - Light"
)
@Composable
private fun SearchScreenTrendingErrorPreview() {
    MyMediaPlayerTheme {
        TrendingSection(
            state = SearchUiState(trendingError = "charts.youtube.com (HTTP 403)"),
            showFullChart = false,
            onShowFullChart = {},
            onBackToRail = {},
            onRetry = {},
            onPlayChartQueue = { _, _ -> }
        )
    }
}

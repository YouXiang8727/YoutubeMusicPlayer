package com.youxiang8727.mymediaplayer.feature.discover

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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.toPlayQueueItem
import com.youxiang8727.mymediaplayer.core.ui.component.DurationBadge
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import com.youxiang8727.mymediaplayer.feature.playlist.CreatePlaylistDialog
import com.youxiang8727.mymediaplayer.feature.playlist.PlaylistPickerSheet

/** 無狀態 UI：狀態提升，便於 Preview 與測試。 */
@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    playlists: List<Playlist>,
    snackbarHostState: SnackbarHostState,
    onIntent: (DiscoverIntent) -> Unit,
    onCreatePlaylistAndAdd: (name: String, video: VideoResult) -> Unit,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit
) {
    // 顯示播放清單選擇 BottomSheet（帶影片資料）
    var showPickerVideo by remember { mutableStateOf<VideoResult?>(null) }
    // 顯示建立新播放清單 Dialog（獨立於 BottomSheet 生命週期）
    var showCreateDialog by remember { mutableStateOf(false) }
    // 待建立清單後加入的影片（BottomSheet 關閉後仍需保留）
    var pendingCreateVideo by remember { mutableStateOf<VideoResult?>(null) }
    // 展開完整榜單的區域（null = 全部收合成 rail 並排）（同 feature:search 內以 state 切換，不新增 nav route）
    var fullChartRegion by remember { mutableStateOf<ChartRegion?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            val expandedRegion = fullChartRegion
            if (expandedRegion != null) {
                // 單一區域完整榜單：TrendingSection 內部的 ChartFullList LazyColumn
                // 直接掛在普通 Column 下（無垂直 LazyColumn 巢狀）
                TrendingSection(
                    title = expandedRegion.displayTitle(),
                    trending = state.trendingByRegion[expandedRegion] ?: TrendingState(),
                    showFullChart = true,
                    onShowFullChart = {},
                    onBackToRail = { fullChartRegion = null },
                    onRetry = { onIntent(DiscoverIntent.TrendingRetry) },
                    onPlayChartQueue = onPlayChartQueue,
                    onAdd = { showPickerVideo = it }
                )
            } else {
                // 四區域 rail 並排：垂直 LazyColumn 承載，避免 4 條 rail 超出螢幕高度
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(ChartRegion.DISPLAY_ORDER) { region ->
                        TrendingSection(
                            title = region.displayTitle(),
                            trending = state.trendingByRegion[region] ?: TrendingState(),
                            showFullChart = false,
                            onShowFullChart = { fullChartRegion = region },
                            onBackToRail = {},
                            onRetry = { onIntent(DiscoverIntent.TrendingRetry) },
                            onPlayChartQueue = onPlayChartQueue,
                            onAdd = { showPickerVideo = it }
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
                onIntent(DiscoverIntent.AddToPlaylist(video, playlistId))
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

/** 各區域榜單的顯示標題。 */
private fun ChartRegion.displayTitle(): String = when (this) {
    ChartRegion.TAIWAN -> "台灣熱門音樂"
    ChartRegion.WESTERN -> "西洋熱門音樂"
    ChartRegion.JAPAN -> "日本熱門音樂"
    ChartRegion.KOREA -> "韓國熱門音樂"
}

/**
 * 單一區域的熱門音樂榜單區塊。
 * rail（前 [TRENDING_RAIL_LIMIT] 筆）⇄ 完整清單兩態以 [showFullChart] 切換；
 * 載入中／失敗（重試）／空榜單三態都有對應 UI。
 */
@Composable
private fun TrendingSection(
    title: String,
    trending: TrendingState,
    showFullChart: Boolean,
    onShowFullChart: () -> Unit,
    onBackToRail: () -> Unit,
    onRetry: () -> Unit,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit,
    onAdd: (VideoResult) -> Unit
) {
    when {
        trending.loading -> Box(
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
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onShowFullChart) { Text("查看完整榜單") }
                }
            }

            when {
                trending.error != null -> TrendingError(
                    message = trending.error,
                    onRetry = onRetry
                )

                trending.items.isEmpty() -> Box(
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
                    items = trending.items,
                    onPlayChartQueue = onPlayChartQueue,
                    onAdd = onAdd
                )

                else -> ChartRail(
                    items = trending.items,
                    onPlayChartQueue = onPlayChartQueue,
                    onAdd = onAdd
                )
            }
        }
    }
}

/** 熱門榜單載入失敗（內嵌重試）。 */
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
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit,
    onAdd: (VideoResult) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(items.take(TRENDING_RAIL_LIMIT), key = { _, v -> v.videoId }) { index, video ->
            ChartRailItem(
                video = video,
                onClick = {
                    // 以「整份榜單」為佇列從該曲起播（rail 只顯示前 N 筆，佇列仍是完整清單）
                    onPlayChartQueue(items.map { it.toPlayQueueItem() }, index)
                },
                onAdd = { onAdd(video) }
            )
        }
    }
}

/** 完整榜單（可捲動，顯示方式同播放清單詳情：縮圖＋歌名＋歌手）。 */
@Composable
private fun ChartFullList(
    items: List<VideoResult>,
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit,
    onAdd: (VideoResult) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(items, key = { _, v -> v.videoId }) { index, video ->
            ChartDetailRow(
                video = video,
                onClick = { onPlayChartQueue(items.map { it.toPlayQueueItem() }, index) },
                onAdd = { onAdd(video) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChartRailItem(
    video: VideoResult,
    onClick: () -> Unit,
    onAdd: () -> Unit
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
            // 右下角：時長 badge 與「加入播放清單」並排（Row 避免兩者重疊）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DurationBadge(duration = video.duration)
                // 獨立可點擊區域（在整卡 onClick 之前攔截），疊於縮圖右下角。
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "加入播放清單",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
            // 縮圖＋時長 badge（右下角）
            Box {
                ChartThumbnail(
                    url = video.thumbnailUrl,
                    modifier = Modifier.size(96.dp, 54.dp)
                )
                DurationBadge(
                    duration = video.duration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
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

/** Hilt 容器：收集狀態與一次性訊息。 */
@Composable
fun DiscoverRoute(
    viewModel: DiscoverViewModel = hiltViewModel(),
    onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    DiscoverScreen(
        state = state,
        playlists = playlists,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onCreatePlaylistAndAdd = viewModel::createPlaylistAndAdd,
        onPlayChartQueue = onPlayChartQueue
    )
}

/** 產生榜單 Preview 用假資料（縮圖留空以主題色塊呈現，與既有 Preview 慣例一致）。 */
private fun trendingPreviewItems(region: ChartRegion, count: Int): List<VideoResult> =
    List(count) { i ->
        VideoResult(
            "trending-${region.name}-$i",
            "${region.displayTitle()} Top ${i + 1}",
            "",
            "歌手 $i",
            "3:4${i % 10}"
        )
    }

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-discover",
    name = "DiscoverScreen - Loading - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-discover",
    name = "DiscoverScreen - Loading - Light"
)
@Composable
private fun DiscoverScreenLoadingPreview() {
    MyMediaPlayerTheme {
        // 初始載入：全部區域皆在載入中，驗證各區域獨立 spinner
        DiscoverScreen(
            state = DiscoverUiState(
                trendingByRegion = ChartRegion.DISPLAY_ORDER.associateWith {
                    TrendingState(loading = true)
                }
            ),
            playlists = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onCreatePlaylistAndAdd = { _, _ -> },
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
    group = "feature-discover",
    name = "DiscoverScreen - Rail - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-discover",
    name = "DiscoverScreen - Rail - Light"
)
@Composable
private fun DiscoverScreenRailPreview() {
    MyMediaPlayerTheme {
        // 台灣 12 筆 > rail 上限 10，驗證截斷只顯示前 10 筆；西洋 5 筆並排驗證第二條 rail
        DiscoverScreen(
            state = DiscoverUiState(
                trendingByRegion = mapOf(
                    ChartRegion.TAIWAN to TrendingState(
                        items = trendingPreviewItems(ChartRegion.TAIWAN, 12)
                    ),
                    ChartRegion.WESTERN to TrendingState(
                        items = trendingPreviewItems(ChartRegion.WESTERN, 5)
                    )
                )
            ),
            playlists = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onCreatePlaylistAndAdd = { _, _ -> },
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
    group = "feature-discover",
    name = "DiscoverScreen - Full Chart - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-discover",
    name = "DiscoverScreen - Full Chart - Light"
)
@Composable
private fun DiscoverScreenFullChartPreview() {
    MyMediaPlayerTheme {
        // 單一區域完整榜單（50 筆 > rail 上限，驗證完整清單不截斷）
        TrendingSection(
            title = ChartRegion.TAIWAN.displayTitle(),
            trending = TrendingState(items = trendingPreviewItems(ChartRegion.TAIWAN, 50)),
            showFullChart = true,
            onShowFullChart = {},
            onBackToRail = {},
            onRetry = {},
            onPlayChartQueue = { _, _ -> },
            onAdd = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-discover",
    name = "DiscoverScreen - Error - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-discover",
    name = "DiscoverScreen - Error - Light"
)
@Composable
private fun DiscoverScreenErrorPreview() {
    MyMediaPlayerTheme {
        // 台灣載入失敗（顯示重試）、西洋正常載入：驗證單一區域失敗不影響其他區域
        DiscoverScreen(
            state = DiscoverUiState(
                trendingByRegion = mapOf(
                    ChartRegion.TAIWAN to TrendingState(error = "charts.youtube.com (HTTP 403)"),
                    ChartRegion.WESTERN to TrendingState(
                        items = trendingPreviewItems(ChartRegion.WESTERN, 5)
                    )
                )
            ),
            playlists = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onCreatePlaylistAndAdd = { _, _ -> },
            onPlayChartQueue = { _, _ -> }
        )
    }
}
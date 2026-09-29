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
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.SearchSort
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
    onAddToQueue: (VideoResult) -> Unit,
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

            // 排序切換器：僅在已搜尋時顯示（空狀態頁只有「最近搜尋」，無結果可排序）。
            // 放在搜尋鈕與結果列表之間，使用者一搜完就能在清單正上方切換。
            if (state.searched) {
                SearchSortSelector(
                    selected = state.sort,
                    // 搜尋中鎖住：避免連點造成多次重搜。**doSearch 本身沒有 isLoading
                    // 重入 guard**（只有 loadMore 有），所以此處的 enabled 與搜尋鈕的
                    // enabled 就是防止連點發出多次請求的**唯一**防線——移除任一個，
                    // 連點就會真的重搜。
                    enabled = !state.isLoading,
                    onSelect = { onIntent(SearchIntent.ChangeSort(it)) }
                )
                Spacer(Modifier.height(12.dp))
            }

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

                // 搜尋失敗：必須排在「查無結果」**之前**。兩者 results 都是空的，
                // 若順序顛倒，網路失敗會被誤呈為「查無結果」——那是誤導（使用者會
                // 以為搜尋成功但沒結果）。訊息由 ViewModel 依失敗原因分類（SearchError），
                // 本層不判斷原因、只呈現。
                state.error != null -> SearchErrorState(message = state.error.message)

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
            onDismiss = { showPickerVideo = null },
            onAddToQueue = {
                onAddToQueue(video)
                showPickerVideo = null
            }
        )
    }

    // 建立新播放清單 Dialog（獨立於 BottomSheet，生命週期不受影響）
    if (showCreateDialog) {
        CreatePlaylistDialog(
            existingNames = playlists.map { it.name }.toSet(),
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
 * 搜尋失敗的錯誤狀態：取代結果列表與「查無結果」空狀態。
 *
 * 唯讀呈現 [message]（由 ViewModel 的 [SearchError] 依失敗原因分類後給出），本函式
 * **不判斷原因**——分類邏輯在 ViewModel 保持可測。
 *
 * ### 為什麼沒有「重試」按鈕（設計判斷）
 *
 * 搜尋鈕就在本狀態正上方且**永遠可用**（`enabled = !isLoading && query.isNotBlank()`，
 * 失敗後 `isLoading` 已回 false）；排序 chip 也會觸發重搜且同樣可用。兩者都在同一個
 * 視窗內、距本狀態僅數十 dp。再放一顆「重試」會是**第三個執行同一動作**的控制項，
 * 而且必須與全寬的「搜尋」鈕區隔（否則就是同一個動作兩個名字）——這正是
 * `docs/TEAM.md` §7 排序切換器決策中指出的「層級訊息被抹掉」問題。
 * `docs/qa/smoke-checklist.md` [O2] 亦已記錄「搜尋鈕仍可點，使用者能自行再次搜尋，
 * 不需另加重試按鈕」。若日後產品 wants 標準空狀態重試鈕，在此加一顆即可。
 *
 * ### 長訊息不破版
 *
 * [message] 的長度不由本層控制（文案會改、會翻譯），且系統字級可被使用者放大，
 * 因此**不設** `maxLines`、不設 `overflow`，讓文字自然換行；左右留 24dp 避免貼邊。
 * 視覺回歸見 `SearchErrorState` 的長訊息 Preview。
 */
@Composable
internal fun SearchErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 搜尋結果排序切換器。唯讀呈現 [SearchUiState.sort]，點擊任一選項以 [onSelect] 回報。
 *
 * 選項文案是 UI 語意（domain 的 [SearchSort] 只描述「要什麼」），故對應表留在本層。
 * 排序僅影響初次搜尋；續頁由 continuation token 承載，UI 不需額外處理。
 */
@Composable
internal fun SearchSortSelector(
    selected: SearchSort,
    enabled: Boolean,
    onSelect: (SearchSort) -> Unit
) {
    val options = SearchSort.entries
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSelect(sort) },
                label = { Text(text = sort.label) },
                enabled = enabled,
                // FilterChip 預設高度 32dp，低於 Material 最小觸控目標，故拉高至 40dp。
                // 用 requiredHeightIn 而非 height：避免被元件內部固定高度順序覆蓋。
                modifier = Modifier.requiredHeightIn(min = 40.dp)
            )
        }
    }
}

/** [SearchSort] 的使用者可見文案。 */
private val SearchSort.label: String
    get() = when (this) {
        SearchSort.RELEVANCE -> "相關性"
        SearchSort.LATEST -> "最新"
        SearchSort.POPULAR -> "熱門"
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
            // 右側單顆「＋」：開啟選單（加入當前播放佇列／選擇播放清單）
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "加入")
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
    onAddToQueue: (VideoResult) -> Unit
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
        onAddToQueue = onAddToQueue,
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
            onAddToQueue = {},
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
            onAddToQueue = {},
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
            onAddToQueue = {},
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
            onAddToQueue = {},
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
            onAddToQueue = {},
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
            onAddToQueue = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

// ==================== 排序切換（視覺回歸：僅 sort 不同，三張圖可直接對比） ====================

/**
 * 排序切換 Preview 共用狀態：**只有 [sort] 不同**，其餘（query／results／nextPageToken）
 * 完全一致，讓三張 Preview 的差異只來自排序切換器的選中狀態。
 */
private fun sortPreviewState(sort: SearchSort) = SearchUiState(
    query = "周杰倫",
    results = listOf(
        VideoResult("dQw4w9WgXcQ", "晴天", "", "Jay Chou", "4:30"),
        VideoResult("abc12345678", "夜曲 Live", "", "Official", "3:12")
    ),
    nextPageToken = "continuation-token-1",
    searched = true,
    sort = sort
)

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Sort Relevance - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Sort Relevance - Light"
)
@Composable
private fun SearchScreenSortRelevancePreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = sortPreviewState(SearchSort.RELEVANCE),
            playlists = listOf(Playlist(id = 1, name = "我的最愛")),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onAddToQueue = {},
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
    name = "SearchScreen - Sort Latest - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Sort Latest - Light"
)
@Composable
private fun SearchScreenSortLatestPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = sortPreviewState(SearchSort.LATEST),
            playlists = listOf(Playlist(id = 1, name = "我的最愛")),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onAddToQueue = {},
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
    name = "SearchScreen - Sort Popular - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Sort Popular - Light"
)
@Composable
private fun SearchScreenSortPopularPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = sortPreviewState(SearchSort.POPULAR),
            playlists = listOf(Playlist(id = 1, name = "我的最愛")),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onAddToQueue = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

// ==================== 錯誤狀態（視覺回歸） ====================

/**
 * 錯誤狀態於完整搜尋頁的呈現。
 *
 * 刻意挑 [SearchError.NETWORK]：其文案是四類中最長（「無法連線到網路，請檢查網路後重試」），
 * 完整頁面（搜尋框＋搜尋鈕＋排序切換器＋錯誤狀態）可一併檢查錯誤狀態不會把
 * 上方控制項擠掉、或與「查無結果」空狀態難以區分。
 */
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Error Network - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchScreen - Error Network - Light"
)
@Composable
private fun SearchScreenErrorNetworkPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(
                query = "周杰倫 晴天",
                searched = true,
                error = SearchError.NETWORK
            ),
            playlists = listOf(Playlist(id = 1, name = "我的最愛")),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {},
            onAddToQueue = {},
            onCreatePlaylistAndAdd = { _, _ -> }
        )
    }
}

/**
 * 錯誤狀態的**長訊息壓力測試**（`fontScale = 1.8f` ＋ 超長文案）。
 *
 * 錯誤訊息的長度不由 UI 層控制（文案會改、會翻譯），且系統字級可被使用者放大。
 * 此 Preview 鎖住兩件事：① 文案自然換行、**不**溢出或被截斷（`SearchErrorState`
 * 刻意不設 `maxLines`／`overflow`）② 換行後仍置中且左右留白不貼邊。
 *
 * 獨立於 `SearchScreen` 預覽：長文案無法由 [SearchUiState.error] 產生（那是封閉
 * enum），故直接對 [SearchErrorState] 取參數，專門鎖這個元件的換行行為。
 */
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.8f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchErrorState - Long Message Large Font - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.8f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-search",
    name = "SearchErrorState - Long Message Large Font - Light"
)
@Composable
private fun SearchErrorStateLongMessagePreview() {
    MyMediaPlayerTheme {
        Surface {
            SearchErrorState(
                message = "搜尋結果無法顯示，請稍後再試一次；若持續發生，可能是服務暫時" +
                    "維護或回應格式變更，請稍候再試。"
            )
        }
    }
}

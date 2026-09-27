package com.youxiang8727.mymediaplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import com.youxiang8727.mymediaplayer.feature.discover.DiscoverRoute
import com.youxiang8727.mymediaplayer.feature.player.MiniPlayerBar
import com.youxiang8727.mymediaplayer.feature.player.PlaybackIntent
import com.youxiang8727.mymediaplayer.feature.player.PlayerViewModel
import com.youxiang8727.mymediaplayer.feature.playlist.CreatePlaylistDialog
import com.youxiang8727.mymediaplayer.feature.playlist.PlaylistDetailRoute
import com.youxiang8727.mymediaplayer.feature.playlist.PlaylistListRoute
import com.youxiang8727.mymediaplayer.feature.search.SearchRoute
import dagger.hilt.android.AndroidEntryPoint

object Routes {
    const val SEARCH = "search"
    const val DISCOVER = "discover"
    const val PLAYLIST_LIST = "playlist_list"
    const val PLAYLIST_DETAIL = "playlist_detail/{playlistId}?name={name}"

    fun playlistDetail(playlistId: Long, name: String = "") =
        "playlist_detail/$playlistId?name=${android.net.Uri.encode(name)}"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒絕不影響前景播放 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            MyMediaPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyApp()
                }
            }
        }
    }

    /** Android 13+ 需動態請求通知權限，否則背景播放通知不出現。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun MyApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // activity-scoped：MiniPlayerBar 與各頁面共用同一狀態源（MediaSession）
    val playerViewModel: PlayerViewModel = viewModel()
    val playback by playerViewModel.playback.collectAsState()
    val queue by playerViewModel.queue.collectAsState()

    // MiniPlayerBar 展開狀態（50% 螢幕高度）
    var isMiniPlayerExpanded by remember { mutableStateOf(false) }

    // 底部 overlay 高度測量（px → Dp）：
    //  - navigationBarHeight：底部導覽列高度（MiniPlayerBar overlay 需墊在其上方）
    //  - miniPlayerOverlayHeight：MiniPlayerBar 自身佔用高度（NavHost 內容底部需預留，避免被蓋住）
    val density = LocalDensity.current
    var navigationBarHeight by remember { mutableStateOf(0.dp) }
    var miniPlayerOverlayHeight by remember { mutableStateOf(0.dp) }

    // 播放失敗的 App 內回饋（P1）：errorMessage 由 ExoPlayer 保留至下次 prepare()，
    // 此處做 one-shot 顯示；錯誤清除（null）時重置去重鍵，同曲重試失敗仍會再次提示。
    val snackbarHostState = remember { SnackbarHostState() }
    var shownError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playback.errorMessage) {
        when (val message = playback.errorMessage) {
            null -> shownError = null
            else -> if (message != shownError) {
                shownError = message
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // 「存為播放清單」：由 MiniPlayerBar 展開面板的按鈕觸發，於容器層開 Dialog。
    // Dialog 掛在 Box 層級（與 MiniPlayerBar 同級、非 Scaffold 內）——面板展開時
    // MiniPlayerBar 為 zIndex(2f) 且其 scrim 為 zIndex(1f)，Dialog 若置於面板內
    // 會被面板的層級與 scrim 影響顯示。
    // 開啟前先收面板：兩者都是全螢幕層級，同時可見會互相遮蔽。
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // PlayerViewModel.messages 為 replay=0 的 SharedFlow，**必須在畫面層常駐收集**：
    // 若改成在 onSaveAsPlaylist 的 lambda 裡臨時 collect，會漏掉自己剛發出的訊息
    // （emit 早於 collect 上線）。與 search/discover/playlist 的接線方式相同。
    val playlistNames by playerViewModel.playlistNames.collectAsState()
    LaunchedEffect(Unit) {
        playerViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // 頂層目的地（搜尋／探索／播放清單）顯示底部導航列；播放清單詳情頁保留（階層 nav）
    val showBottomBar = currentRoute == Routes.SEARCH ||
            currentRoute == Routes.DISCOVER ||
            currentRoute == Routes.PLAYLIST_LIST ||
            currentRoute?.startsWith("playlist_detail") == true

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // SnackbarHost 已移出至本 Box 末段（zIndex 3f）——留空，
            // 原因見該處註解：置於 Scaffold 內會被 MiniPlayerBar 遮蔽。
            snackbarHost = {},
            bottomBar = {
                // 底部導覽列留在 Scaffold：展開 MiniPlayerBar 時會被 scrim 蓋住（modal 行為）
                if (showBottomBar) {
                    NavigationBar(
                        modifier = Modifier.onSizeChanged {
                            navigationBarHeight = with(density) { it.height.toDp() }
                        }
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == Routes.SEARCH,
                            onClick = {
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(Routes.SEARCH) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text("搜尋") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.DISCOVER,
                            onClick = {
                                navController.navigate(Routes.DISCOVER) {
                                    popUpTo(Routes.SEARCH) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            label = { Text("探索") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.PLAYLIST_LIST ||
                                    currentRoute?.startsWith("playlist_detail") == true,
                            onClick = {
                                navController.navigate(Routes.PLAYLIST_LIST) {
                                    popUpTo(Routes.SEARCH) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            label = { Text("播放清單") }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.SEARCH,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // 內容底部預留 MiniPlayerBar 佔用高度（收起態 96dp；展開態 50% 螢幕）
                    .padding(bottom = miniPlayerOverlayHeight),
                builder = {
                    composable(Routes.SEARCH) {
                        SearchRoute(
                            onPlayVideo = { video ->
                                playerViewModel.onPlaybackIntent(
                                    PlaybackIntent.Play(video.videoId, video.title)
                                )
                            },
                            // 加入播放佇列尾端（append-only，不中斷目前播放）
                            onAddToQueue = { video ->
                                playerViewModel.onPlaybackIntent(
                                    PlaybackIntent.AddToQueue(video.videoId, video.title)
                                )
                            }
                        )
                    }
                    composable(Routes.DISCOVER) {
                        DiscoverRoute(
                            // 熱門榜單：整份清單一併送入暫時性播放佇列，從點擊的歌曲起播
                            onPlayChartQueue = { entries, startIndex ->
                                playerViewModel.onPlaybackIntent(
                                    PlaybackIntent.PlayList(entries, startIndex)
                                )
                            },
                            // 單曲加入播放佇列尾端（append-only，不中斷目前播放）
                            onAddToQueue = { video ->
                                playerViewModel.onPlaybackIntent(
                                    PlaybackIntent.AddToQueue(video.videoId, video.title)
                                )
                            }
                        )
                    }
                    composable(Routes.PLAYLIST_LIST) {
                        PlaylistListRoute(
                            onOpenPlaylist = { id, name ->
                                navController.navigate(Routes.playlistDetail(id, name))
                            }
                        )
                    }
                    composable(
                        route = Routes.PLAYLIST_DETAIL,
                        arguments = listOf(
                            navArgument("playlistId") { type = NavType.LongType },
                            navArgument("name") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) {
                        PlaylistDetailRoute(
                            onBack = { navController.popBackStack() }
                        )
                    }
                },
                contentAlignment = androidx.compose.ui.Alignment.TopStart
            )
        }

        // MiniPlayerBar：最高層（zIndex 2，高於 scrim）。
        // 展開時面板必須在 scrim 之上才可操作（佇列點選／移除／拖曳／清空／存為播放清單）；
        // 收起態墊於底部導覽列上方。onSizeChanged 量測到的是 MiniPlayerBar 自身高度
        // （padding 在 it 外層，不會把導覽列高度算進去），用於 NavHost 底部預留空間。
        AnimatedVisibility(
            visible = playback.hasCurrent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navigationBarHeight)
                .zIndex(2f)
                .onSizeChanged { miniPlayerOverlayHeight = with(density) { it.height.toDp() } }
        ) {
            MiniPlayerBar(
                snapshot = playback,
                queue = queue,
                isExpanded = isMiniPlayerExpanded,
                onToggleExpand = { isMiniPlayerExpanded = !isMiniPlayerExpanded },
                onTogglePlayPause = {
                    playerViewModel.onPlaybackIntent(PlaybackIntent.TogglePlayPause)
                },
                onNext = { playerViewModel.onPlaybackIntent(PlaybackIntent.Next) },
                onPrevious = { playerViewModel.onPlaybackIntent(PlaybackIntent.Previous) },
                onToggleShuffle = { playerViewModel.onPlaybackIntent(PlaybackIntent.ToggleShuffle) },
                onCycleRepeat = { playerViewModel.onPlaybackIntent(PlaybackIntent.CycleRepeat) },
                onSeek = { positionMs ->
                    playerViewModel.onPlaybackIntent(PlaybackIntent.Seek(positionMs))
                },
                onSeekToIndex = { index ->
                    playerViewModel.onPlaybackIntent(PlaybackIntent.SeekToIndex(index))
                },
                onRemoveFromQueue = { index ->
                    playerViewModel.onPlaybackIntent(PlaybackIntent.RemoveFromQueue(index))
                },
                onClearQueue = {
                    playerViewModel.onPlaybackIntent(PlaybackIntent.ClearQueue)
                },
                onSaveAsPlaylist = {
                    // 雙重把關：按鈕本身已 enabled = queue.isNotEmpty()，此處再確認一次
                    // 以防某些路徑（旋轉、佇列被清）在觸發時已空——那時開 Dialog 只會讓
                    // 使用者輸完名字才得到「播放佇列為空」。空佇列的失敗文案由 domain 層負責。
                    if (queue.isNotEmpty()) {
                        isMiniPlayerExpanded = false
                        showCreatePlaylistDialog = true
                    }
                }
            )
        }

        // MiniPlayerBar 本身以 AnimatedVisibility(visible = playback.hasCurrent) 控制顯示；
        // 若 hasCurrent 因「清空佇列」而變 false，面板會整個被移除，此時必須同步把
        // isMiniPlayerExpanded 收回 false，否則 scrim 條件仍為 true → 全螢幕殘留灰色遮罩
        // （且點擊會去「收起」一個已不可見的面板，狀態也不乾淨）。
        // 這裡負責清狀態（下一個 frame 生效），下方 scrim 條件另有 hasCurrent 連動做同步防護。
        LaunchedEffect(playback.hasCurrent) {
            if (!playback.hasCurrent) {
                isMiniPlayerExpanded = false
            }
        }

        // scrim：中間層（zIndex 1，低於 MiniPlayerBar、高於 Scaffold），
        // 全屏覆蓋內容與底部導覽列，點擊任一處收起展開面板。
        // 條件必須連動 playback.hasCurrent：hasCurrent 為 false 時 MiniPlayerBar 已不可見，
        // 此時顯示 scrim 等於留下一個無主的全屏灰色遮罩。
        if (isMiniPlayerExpanded && playback.hasCurrent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isMiniPlayerExpanded = false }
                    .zIndex(1f)
            )
        }

        // 建立播放清單 Dialog（feature:playlist 的共用元件；feature:player 已依賴它）。
        // 置於 Box 層級且在 AnimatedVisibility(scrim/MiniPlayerBar) **之外**：Dialog 是
        // 自己的全螢幕視窗層，若被包進面板會被面板的 zIndex 與 scrim 影響顯示。
        // existingNames 取自 PlayerViewModel.playlistNames，提供與其他頁面一致的重名即時防呆
        // （domain 層仍有 PlaylistNameConflictException 作為第二道防線）。
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                existingNames = playlistNames,
                onConfirm = { name ->
                    playerViewModel.onPlaybackIntent(PlaybackIntent.SaveQueueAsPlaylist(name))
                },
                onDismiss = { showCreatePlaylistDialog = false }
            )
        }

        // SnackbarHost：最高層（zIndex 3f，**必須在 Scaffold 之外**）。
        // 2026-09-26 實機驗證發現：原本放在 Scaffold(snackbarHost = ...) 內時
        // 「存為播放清單」建立成功卻完全看不到提示。原因是層級 + 位置雙重因素——
        // ① MiniPlayerBar 為 zIndex(2f)、Scaffold 為 0，後者（含其內的 SnackbarHost）
        //    繪製在 MiniPlayerBar **之下**；
        // ② Material3 Scaffold 將 snackbar 排在 bottomBar 之上（≈ navigationBarHeight），
        //    而 MiniPlayerBar 亦為 BottomCenter + padding(bottom = navigationBarHeight)
        //    ——兩者垂直位置重疊，故 snackbar 被整個蓋住。
        // 影響範圍不限本功能：同一 snackbarHostState 也承載 playback.errorMessage
        // （播放失敗提示），推測同樣被遮蔽而長期未察覺，本次一併修正。
        // padding 手動施加（原本由 Scaffold 依 bottomBar 高度自動施加）：
        // 播放中疊在 MiniPlayerBar 上方、不遮蔽它；未播放時貼在導覽列上方。
        val snackbarBottomPadding =
            if (playback.hasCurrent) navigationBarHeight + miniPlayerOverlayHeight
            else navigationBarHeight
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomPadding)
                .zIndex(3f)
        )
    }
}

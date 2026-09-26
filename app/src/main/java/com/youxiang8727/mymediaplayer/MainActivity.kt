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

    // 頂層目的地（搜尋／探索／播放清單）顯示底部導航列；播放清單詳情頁保留（階層 nav）
    val showBottomBar = currentRoute == Routes.SEARCH ||
            currentRoute == Routes.DISCOVER ||
            currentRoute == Routes.PLAYLIST_LIST ||
            currentRoute?.startsWith("playlist_detail") == true

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    // TODO: 存為播放清單（S2 工作項實作）
                }
            )
        }

        // scrim：中間層（zIndex 1，低於 MiniPlayerBar、高於 Scaffold），
        // 全屏覆蓋內容與底部導覽列，點擊任一處收起展開面板。
        if (isMiniPlayerExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isMiniPlayerExpanded = false }
                    .zIndex(1f)
            )
        }
    }
}

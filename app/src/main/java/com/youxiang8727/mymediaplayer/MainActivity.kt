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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import com.youxiang8727.mymediaplayer.feature.player.MiniPlayerBar
import com.youxiang8727.mymediaplayer.feature.player.PlaybackIntent
import com.youxiang8727.mymediaplayer.feature.player.PlayerRoute
import com.youxiang8727.mymediaplayer.feature.player.PlayerViewModel
import com.youxiang8727.mymediaplayer.feature.playlist.PlaylistRoute
import com.youxiang8727.mymediaplayer.feature.search.SearchRoute
import dagger.hilt.android.AndroidEntryPoint

object Routes {
    const val SEARCH = "search"
    const val PLAYLIST = "playlist"
    const val PLAYER = "player/{videoId}?title={title}"

    fun player(videoId: String, title: String = "") =
        "player/$videoId?title=${android.net.Uri.encode(title)}"
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

    val showBottomBar = currentRoute == Routes.SEARCH || currentRoute == Routes.PLAYLIST

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(visible = playback.hasCurrent) {
                    MiniPlayerBar(
                        snapshot = playback,
                        onTogglePlayPause = {
                            playerViewModel.onPlaybackIntent(PlaybackIntent.TogglePlayPause)
                        },
                        onNext = { playerViewModel.onPlaybackIntent(PlaybackIntent.Next) },
                        onPrevious = { playerViewModel.onPlaybackIntent(PlaybackIntent.Previous) },
                        onToggleShuffle = { playerViewModel.onPlaybackIntent(PlaybackIntent.ToggleShuffle) },
                        onCycleRepeat = { playerViewModel.onPlaybackIntent(PlaybackIntent.CycleRepeat) },
                        onSeek = { positionMs ->
                            playerViewModel.onPlaybackIntent(PlaybackIntent.Seek(positionMs))
                        }
                    )
                }
                if (showBottomBar) {
                    NavigationBar {
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
                            selected = currentRoute == Routes.PLAYLIST,
                            onClick = {
                                navController.navigate(Routes.PLAYLIST) {
                                    popUpTo(Routes.SEARCH) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Filled.List, contentDescription = null) },
                            label = { Text("播放清單") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SEARCH,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            builder = {
                composable(Routes.SEARCH) {
                    // 點擊搜尋結果直接起播（不導航）：轉發給 activity-scoped PlayerViewModel，
                    // 與 MiniPlayerBar 共用同一 MediaSession；App 退背景由 MusicService 前景服務接手。
                    SearchRoute(
                        onPlayVideo = { video ->
                            playerViewModel.onPlaybackIntent(
                                PlaybackIntent.Play(video.videoId, video.title)
                            )
                        }
                    )
                }
                composable(Routes.PLAYLIST) {
                    PlaylistRoute(
                        onOpenVideo = { item ->
                            navController.navigate(Routes.player(item.videoId, item.title))
                        }
                    )
                }
                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("title") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) {
                    PlayerRoute(onBack = { navController.popBackStack() })
                }
            },
            contentAlignment = androidx.compose.ui.Alignment.TopStart
        )
    }
}

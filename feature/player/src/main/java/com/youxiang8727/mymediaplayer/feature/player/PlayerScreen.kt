package com.youxiang8727.mymediaplayer.feature.player

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartBackground: () -> Unit,
    onStopBackground: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 頂部列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = state.title.ifBlank { state.videoId },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Filled.Add, contentDescription = "加入播放清單")
                }
            }

            // 影片區（WebView 嵌入 YouTube 播放器）
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        loadUrl("https://www.youtube.com/embed/${state.videoId}?autoplay=1")
                    }
                },
                update = { webView ->
                    webView.loadUrl("https://www.youtube.com/embed/${state.videoId}?autoplay=1")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color.Black)
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartBackground,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("背景播放音訊") }
                OutlinedButton(
                    onClick = onStopBackground,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("停止背景播放") }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "提示：背景服務已就緒；實際串流 URL 解析接入後即可在鎖屏/離開 App 時持續播放。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val state = viewModel.state
    PlayerScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onStartBackground = viewModel::startBackgroundPlayback,
        onStopBackground = viewModel::stopBackgroundPlayback,
        onAddToPlaylist = {
            viewModel.onAddToPlaylist(
                PlaylistItem(
                    videoId = state.videoId,
                    title = state.title.ifBlank { state.videoId },
                    thumbnailUrl = ""
                )
            )
        }
    )
}

@Preview(showBackground = true, name = "Player")
@Composable
private fun PlayerScreenPreview() {
    MyMediaPlayerTheme {
        PlayerScreen(
            state = PlayerUiState(videoId = "dQw4w9WgXcQ", title = "晴天"),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onStartBackground = {},
            onStopBackground = {},
            onAddToPlaylist = {}
        )
    }
}

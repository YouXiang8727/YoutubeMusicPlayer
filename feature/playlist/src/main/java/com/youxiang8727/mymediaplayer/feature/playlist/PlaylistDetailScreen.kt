package com.youxiang8727.mymediaplayer.feature.playlist

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/** 播放清單詳情頁（無狀態） */
@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (PlaylistDetailIntent) -> Unit,
    onOpenVideo: (PlaylistItem) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 頂部列：返回 + 標題
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = state.playlistName.ifBlank { "播放清單" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (state.items.isNotEmpty()) {
                    TextButton(onClick = { onIntent(PlaylistDetailIntent.ClearAll) }) {
                        Text("全部清除")
                    }
                }
            }

            // 隨機播放按鈕
            if (state.items.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { onIntent(PlaylistDetailIntent.ShufflePlay) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "隨機播放（${state.items.size} 首）",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "清單是空的，去搜尋頁加幾首歌吧！",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { it.videoId }) { item ->
                        PlaylistDetailCard(
                            item = item,
                            onClick = { onOpenVideo(item) },
                            onRemove = { onIntent(PlaylistDetailIntent.Remove(item.videoId)) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
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
            if (item.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
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
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.channel.isNotBlank()) {
                    Text(
                        text = item.channel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "移除")
            }
        }
    }
}

/** 播放清單詳情頁 Hilt 容器 */
@Composable
fun PlaylistDetailRoute(
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onOpenVideo: (PlaylistItem) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    PlaylistDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onOpenVideo = onOpenVideo,
        onBack = onBack
    )
}

@Preview(showBackground = true, name = "PlaylistDetail - Empty")
@Composable
private fun PlaylistDetailScreenEmptyPreview() {
    MyMediaPlayerTheme {
        PlaylistDetailScreen(
            state = PlaylistDetailUiState(
                playlistId = 1,
                playlistName = "我的最愛",
                items = emptyList(),
                isLoading = false
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onOpenVideo = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "PlaylistDetail - Items")
@Composable
private fun PlaylistDetailScreenItemsPreview() {
    MyMediaPlayerTheme {
        PlaylistDetailScreen(
            state = PlaylistDetailUiState(
                playlistId = 1,
                playlistName = "我的最愛",
                isLoading = false,
                items = listOf(
                    PlaylistItem(
                        videoId = "dQw4w9WgXcQ",
                        title = "晴天",
                        thumbnailUrl = "",
                        channel = "Jay Chou",
                        playlistId = 1
                    ),
                    PlaylistItem(
                        videoId = "abc12345678",
                        title = "夜曲 Live",
                        thumbnailUrl = "",
                        channel = "Official",
                        playlistId = 1
                    )
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onOpenVideo = {},
            onBack = {}
        )
    }
}

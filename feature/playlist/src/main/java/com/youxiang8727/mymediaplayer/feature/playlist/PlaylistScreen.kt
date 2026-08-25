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
import androidx.compose.material.icons.filled.Close
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

@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (PlaylistIntent) -> Unit,
    onOpenVideo: (PlaylistItem) -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放清單（${state.items.size}）",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (state.items.isNotEmpty()) {
                    TextButton(onClick = { onIntent(PlaylistIntent.ClearAll) }) {
                        Text("全部清除")
                    }
                }
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
                        PlaylistCard(
                            item = item,
                            onClick = { onOpenVideo(item) },
                            onRemove = { onIntent(PlaylistIntent.Remove(item.videoId)) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
fun PlaylistRoute(
    viewModel: PlaylistViewModel = hiltViewModel(),
    onOpenVideo: (PlaylistItem) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    PlaylistScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onOpenVideo = onOpenVideo
    )
}

@Preview(showBackground = true, name = "Playlist - Empty")
@Composable
private fun PlaylistScreenEmptyPreview() {
    MyMediaPlayerTheme {
        PlaylistScreen(
            state = PlaylistUiState(items = emptyList(), isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onOpenVideo = {}
        )
    }
}

@Preview(showBackground = true, name = "Playlist - Items")
@Composable
private fun PlaylistScreenItemsPreview() {
    MyMediaPlayerTheme {
        PlaylistScreen(
            state = PlaylistUiState(
                isLoading = false,
                items = listOf(
                    PlaylistItem("dQw4w9WgXcQ", "晴天", "", "Jay Chou"),
                    PlaylistItem("abc12345678", "夜曲 Live", "", "Official")
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onOpenVideo = {}
        )
    }
}

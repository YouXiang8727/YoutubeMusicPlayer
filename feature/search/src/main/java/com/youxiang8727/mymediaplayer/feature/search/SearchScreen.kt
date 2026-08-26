package com.youxiang8727.mymediaplayer.feature.search

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
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/** 無狀態 UI：狀態提升，便於 Preview 與測試。 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (SearchIntent) -> Unit,
    onPlayVideo: (VideoResult) -> Unit
) {
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
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.results.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.searched) "查無結果" else "輸入關鍵字開始搜尋",
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
                            onAdd = { onIntent(SearchIntent.AddToPlaylist(video)) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
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
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** Hilt 容器：收集狀態與一次性訊息。 */
@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlayVideo: (VideoResult) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    SearchScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onPlayVideo = onPlayVideo
    )
}

@Preview(showBackground = true, name = "Search - Empty")
@Composable
private fun SearchScreenEmptyPreview() {
    MyMediaPlayerTheme {
        SearchScreen(
            state = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {}
        )
    }
}

@Preview(showBackground = true, name = "Search - Results")
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
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onPlayVideo = {}
        )
    }
}

package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 播放清單選擇 BottomSheet（search / player 共用）。
 * 點擊清單項目 → [onPlaylistSelected]；點擊「建立新清單」→ [onCreateNew]。
 *
 * [onAddToQueue] 為選擇性參數：非 null 時在標題下方多出一列「加入當前播放佇列」
 * （null 時維持原本只有播放清單的外觀，既有呼叫端不受影響）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    onPlaylistSelected: (playlistId: Long) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    onAddToQueue: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "選擇播放清單",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 加入當前播放佇列（選單化的第二個意圖，圖示沿用原「加入佇列」按鈕）
            if (onAddToQueue != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAddToQueue()
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "加入當前播放佇列",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                HorizontalDivider()
            }

            if (playlists.isEmpty()) {
                Text(
                    text = "尚無播放清單",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPlaylistSelected(playlist.id)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            // 建立新播放清單選項
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onCreateNew()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "建立新播放清單",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - With Items - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - With Items - Light"
)
@Composable
private fun PlaylistPickerSheetPreview() {
    MyMediaPlayerTheme {
        PlaylistPickerSheet(
            playlists = listOf(
                Playlist(id = 1, name = "我的最愛"),
                Playlist(id = 2, name = "工作播放清單"),
                Playlist(id = 3, name = "運動音樂")
            ),
            onPlaylistSelected = {},
            onCreateNew = {},
            onDismiss = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - Empty - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - Empty - Light"
)
@Composable
private fun PlaylistPickerSheetEmptyPreview() {
    MyMediaPlayerTheme {
        PlaylistPickerSheet(
            playlists = emptyList(),
            onPlaylistSelected = {},
            onCreateNew = {},
            onDismiss = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - With Queue Option - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - With Queue Option - Light"
)
@Composable
private fun PlaylistPickerSheetWithQueueOptionPreview() {
    MyMediaPlayerTheme {
        // onAddToQueue 非 null：標題下方多出「加入當前播放佇列」一列 + 分隔線
        PlaylistPickerSheet(
            playlists = listOf(
                Playlist(id = 1, name = "我的最愛"),
                Playlist(id = 2, name = "工作播放清單"),
                Playlist(id = 3, name = "運動音樂")
            ),
            onPlaylistSelected = {},
            onCreateNew = {},
            onDismiss = {},
            onAddToQueue = {}
        )
    }
}

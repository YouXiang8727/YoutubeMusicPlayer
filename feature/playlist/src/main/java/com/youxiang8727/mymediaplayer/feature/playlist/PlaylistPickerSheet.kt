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
 * [onAddToQueue] 為選擇性參數：非 null 時 sheet 改為**雙區塊**結構——
 * 區塊 1「加入當前播放佇列」放加入佇列的動作列，區塊 2「選擇播放清單」放歌單列表，
 * 兩者以 section header + 分隔線區隔（避免動作列被誤讀成一個名為「加入當前播放佇列」的歌單）。
 * 為 null 時維持原本只有播放清單的外觀（單一總標題 + 列表），既有呼叫端不受影響。
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
            if (onAddToQueue != null) {
                // ── 區塊 1：加入當前播放佇列（動作）──────────────────────
                SheetSectionHeader(text = "加入當前播放佇列")
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = "加入佇列尾端",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "不中斷目前播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── 區塊 2：選擇播放清單（目標清單）──────────────────────
                SheetSectionHeader(text = "選擇播放清單")
            } else {
                Text(
                    text = "選擇播放清單",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
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

/**
 * Sheet 內的區塊標題（section header）樣式：次要層級，用來區隔同層不同性質的區塊。
 * 刻意不用 titleMedium 總標題，避免與「動作列」或「歌單項目」同層級造成誤讀。
 */
@Composable
private fun SheetSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
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
        // onAddToQueue 非 null：雙區塊結構（區塊 1 加入佇列動作 / 區塊 2 歌單列表）
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

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - With Queue Option & Empty - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "PlaylistPickerSheet - With Queue Option & Empty - Light"
)
@Composable
private fun PlaylistPickerSheetWithQueueOptionEmptyPreview() {
    MyMediaPlayerTheme {
        // 雙區塊 + 零歌單：兩個區塊標題仍須正確，「尚無播放清單」落在區塊 2 標題之下
        PlaylistPickerSheet(
            playlists = emptyList(),
            onPlaylistSelected = {},
            onCreateNew = {},
            onDismiss = {},
            onAddToQueue = {}
        )
    }
}

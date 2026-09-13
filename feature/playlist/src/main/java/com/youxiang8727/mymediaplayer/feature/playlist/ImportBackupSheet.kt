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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import java.util.Locale

/**
 * 匯入歌單 BottomSheet：列出 `Download/MyMediaPlayer/` 下的 `.json` 備份檔。
 * 點擊某檔或「匯入」按鈕 → [onFileSelected]；「刪除」按鈕 → [onDelete]（sheet 維持開啟，由呼叫方負責確認彈窗）；
 * 點「重新掃描」→ [onRescan]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBackupSheet(
    backups: List<PlaylistBackupFile>,
    onFileSelected: (PlaylistBackupFile) -> Unit,
    onDelete: (PlaylistBackupFile) -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
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
            // 標題列
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "匯入歌單",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRescan) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "重新掃描"
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (backups.isEmpty()) {
                // 空狀態
                Text(
                    text = "Download/MyMediaPlayer/ 沒有備份檔，請先執行匯出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(backups, key = { it.uri.toString() }) { backup ->
                        ImportBackupItem(
                            backup = backup,
                            onClick = {
                                onFileSelected(backup)
                                onDismiss()
                            },
                            onDelete = { onDelete(backup) }
                        )
                        HorizontalDivider()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportBackupItem(
    backup: PlaylistBackupFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatBackupModifiedDate(backup.modifiedAt, Locale.getDefault())}　${formatBackupFileSize(backup.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 明確的「匯入」按鈕（與整列點擊同行為：選檔後關閉 sheet）
        TextButton(onClick = onClick) {
            Text("匯入")
        }
        // 刪除按鈕：交由呼叫方彈確認對話框，sheet 維持開啟
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "刪除備份")
        }
    }
}

// ── Preview ──────────────────────────────────────────────────

private val sampleBackups = listOf(
    PlaylistBackupFile(
        uri = android.net.Uri.parse("content://media/external/1"),
        displayName = "MyMediaPlayer_我的最愛.json",
        sizeBytes = 12_345,
        modifiedAt = 1693500000000L
    ),
    PlaylistBackupFile(
        uri = android.net.Uri.parse("content://media/external/2"),
        displayName = "MyMediaPlayer_Backup_20260901.json",
        sizeBytes = 85_432,
        modifiedAt = 1693586400000L
    ),
    PlaylistBackupFile(
        uri = android.net.Uri.parse("content://media/external/3"),
        displayName = "MyMediaPlayer_Backup_20260815.json",
        sizeBytes = 1_048_576,
        modifiedAt = 1692057600000L
    )
)

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - With Items - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - With Items - Light"
)
@Composable
private fun ImportBackupSheetPreview() {
    MyMediaPlayerTheme {
        ImportBackupSheet(
            backups = sampleBackups,
            onFileSelected = {},
            onDelete = {},
            onRescan = {},
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
    name = "ImportBackupSheet - Empty - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - Empty - Light"
)
@Composable
private fun ImportBackupSheetEmptyPreview() {
    MyMediaPlayerTheme {
        ImportBackupSheet(
            backups = emptyList(),
            onFileSelected = {},
            onDelete = {},
            onRescan = {},
            onDismiss = {}
        )
    }
}

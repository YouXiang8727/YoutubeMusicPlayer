package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
 * 點「重新掃描」→ [onRescan]；[ImportBackupPanelState.PermissionRequired] 時顯示阻擋訊息與
 * 「前往權限設定」→ [onOpenSettings]。
 *
 * [panelState] 由純函式 [importBackupPanelState] 產生，四種情境（未授權／掃描中／查詢失敗／有清單）
 * 在此分派，Sheet 本身不判斷權限也不做 IO。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBackupSheet(
    panelState: ImportBackupPanelState,
    onFileSelected: (PlaylistBackupFile) -> Unit,
    onDelete: (PlaylistBackupFile) -> Unit,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
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

            when (panelState) {
                is ImportBackupPanelState.PermissionRequired ->
                    PermissionRequiredContent(onOpenSettings = onOpenSettings)

                is ImportBackupPanelState.Scanning -> Text(
                    text = "正在掃描 Download/MyMediaPlayer/…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                is ImportBackupPanelState.QueryFailed -> Column(
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "無法讀取 Download/MyMediaPlayer/ 的備份清單",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // 查詢失敗 ≠ 沒有備份：不可引導使用者去重新匯出（那不是解決辦法）
                    Text(
                        text = panelState.reason
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "錯誤：$it" }
                            ?: "可能是存取權限在讀取過程中被關閉，請開啟權限後再試一次。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                is ImportBackupPanelState.Ready ->
                    if (panelState.backups.isEmpty()) {
                        // 空狀態：查詢成功且確實沒有備份
                        Text(
                            text = "Download/MyMediaPlayer/ 沒有備份檔，請先執行匯出",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(400.dp)) {
                            items(panelState.backups, key = { it.uri.toString() }) { backup ->
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
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 未取得「所有檔案存取」權限時的阻擋內容。
 *
 * 這裡**不能**顯示「沒有備份檔」——舊備份可能真的在，只是 MediaStore 不把非本 App 擁有的
 * 檔案列出來（解除安裝→重裝的典型情境）。
 */
@Composable
private fun PermissionRequiredContent(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "需要「所有檔案存取」權限才能讀取 Download/MyMediaPlayer/ 的備份檔。",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "未開啟時，系統只會列出本 App 自己建立的檔案，" +
                "重新安裝前匯出的舊備份會看不到（檔案其實還在）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onOpenSettings) { Text("前往權限設定") }
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
            panelState = ImportBackupPanelState.Ready(sampleBackups),
            onFileSelected = {},
            onDelete = {},
            onRescan = {},
            onOpenSettings = {},
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
            panelState = ImportBackupPanelState.Ready(emptyList()),
            onFileSelected = {},
            onDelete = {},
            onRescan = {},
            onOpenSettings = {},
            onDismiss = {}
        )
    }
}

// 未授權是本次修正的核心情境（原本會誤顯示「沒有備份檔」），固定做視覺回歸
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - PermissionRequired - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - PermissionRequired - Light"
)
@Composable
private fun ImportBackupSheetPermissionRequiredPreview() {
    MyMediaPlayerTheme {
        ImportBackupSheet(
            panelState = ImportBackupPanelState.PermissionRequired,
            onFileSelected = {},
            onDelete = {},
            onRescan = {},
            onOpenSettings = {},
            onDismiss = {}
        )
    }
}

// 查詢失敗：必須與「沒有備份」視覺可區分，否則等於沒修
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - QueryFailed - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportBackupSheet - QueryFailed - Light"
)
@Composable
private fun ImportBackupSheetQueryFailedPreview() {
    MyMediaPlayerTheme {
        ImportBackupSheet(
            panelState = ImportBackupPanelState.QueryFailed("SecurityException: 權限已被關閉"),
            onFileSelected = {},
            onDelete = {},
            onRescan = {},
            onOpenSettings = {},
            onDismiss = {}
        )
    }
}

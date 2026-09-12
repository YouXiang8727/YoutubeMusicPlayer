package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 建立新播放清單的 Dialog。
 * [onConfirm] 回傳使用者輸入的名稱（trim 後）；[onDismiss] 關閉。
 * [existingNames] 為既有歌單名稱（已 trim）集合——輸入名稱與其重複時
 * 確認鈕 disabled 並以 isError＋supportingText「此名稱已存在」即時提示。
 */
@Composable
fun CreatePlaylistDialog(
    existingNames: Set<String>,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val nameExists = trimmed.isNotBlank() && trimmed in existingNames
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立新播放清單") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("清單名稱") },
                singleLine = true,
                isError = nameExists,
                supportingText = if (nameExists) {
                    { Text("此名稱已存在") }
                } else {
                    null
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed); onDismiss() },
                enabled = !nameExists && name.isNotBlank()
            ) { Text("建立") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "CreatePlaylistDialog - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "CreatePlaylistDialog - Light"
)
@Composable
private fun CreatePlaylistDialogPreview() {
    MyMediaPlayerTheme {
        CreatePlaylistDialog(
            existingNames = setOf("我的最愛", "工作播放清單"),
            onConfirm = {},
            onDismiss = {}
        )
    }
}

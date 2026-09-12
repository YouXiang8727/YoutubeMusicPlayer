package com.youxiang8727.mymediaplayer.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 匯入衝突彈窗：告知「已存在同名歌單」，由使用者決定
 * 取代／兩者皆保留／取消；後面還有衝突時可勾選「全部套用」。
 */
@Composable
fun ImportConflictDialog(
    info: ImportConflictInfo,
    onDecision: (decision: ImportConflictDecision, applyToAll: Boolean) -> Unit
) {
    // 後面還有衝突才顯示「第 X/Y 個衝突」與「全部套用」（最後一個衝突無需套用到後續）
    val hasMoreConflicts = info.conflictIndex < info.totalConflicts
    var applyAllChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDecision(ImportConflictDecision.Cancel, false) },
        title = { Text("名稱衝突") },
        text = {
            Column {
                Text("已存在同名歌單「${info.name}」")
                if (hasMoreConflicts) {
                    Text(
                        text = "第 ${info.conflictIndex}/${info.totalConflicts} 個衝突",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Checkbox(
                            checked = applyAllChecked,
                            onCheckedChange = { applyAllChecked = it }
                        )
                        Text("全部套用")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onDecision(ImportConflictDecision.KeepBoth, applyAllChecked) }
                ) { Text("兩者皆保留") }
                TextButton(
                    onClick = { onDecision(ImportConflictDecision.Replace, applyAllChecked) }
                ) { Text("取代") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDecision(ImportConflictDecision.Cancel, applyAllChecked) }
            ) { Text("取消") }
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
    name = "ImportConflictDialog - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-playlist",
    name = "ImportConflictDialog - Light"
)
@Composable
private fun ImportConflictDialogPreview() {
    MyMediaPlayerTheme {
        ImportConflictDialog(
            info = ImportConflictInfo(name = "我的最愛", conflictIndex = 1, totalConflicts = 3),
            onDecision = { _, _ -> }
        )
    }
}
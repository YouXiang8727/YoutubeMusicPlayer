package com.youxiang8727.mymediaplayer.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 影片時長 badge（疊於縮圖角落，YouTube 慣例）。
 * `duration == null` 或空白時不顯示。
 */
@Composable
fun DurationBadge(duration: String?, modifier: Modifier = Modifier) {
    if (duration.isNullOrBlank()) return
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 一筆有值、一筆 null（不顯示）並排，驗證兩種輸入的視覺表現。 */
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "DurationBadge - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "DurationBadge - Light"
)
@Composable
private fun DurationBadgePreview() {
    MyMediaPlayerTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            DurationBadge(duration = "4:30")
            DurationBadge(duration = null)
        }
    }
}

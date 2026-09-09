package com.youxiang8727.mymediaplayer.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 影片縮圖 + 右下角 [DurationBadge] 組合元件。
 *
 * [VideoThumbnail] 的薄包裝：內部透過 [VideoThumbnail.overlayContent] slot
 * 將 [DurationBadge] 疊加於縮圖右下角。尺寸由呼叫方透過 [modifier] 控制。
 */
@Composable
fun VideoThumbnailWithBadge(
    url: String,
    duration: String?,
    modifier: Modifier = Modifier
) {
    VideoThumbnail(
        url = url,
        modifier = modifier
    ) {
        DurationBadge(
            duration = duration,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        )
    }
}

// region Preview

private const val THUMBNAIL_URL = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "VideoThumbnailWithBadge - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "VideoThumbnailWithBadge - Light"
)
@Composable
private fun VideoThumbnailWithBadgePreview() {
    MyMediaPlayerTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            VideoThumbnailWithBadge(
                url = THUMBNAIL_URL,
                duration = "4:30",
                modifier = Modifier.size(96.dp, 54.dp)
            )
            VideoThumbnailWithBadge(
                url = THUMBNAIL_URL,
                duration = null,
                modifier = Modifier.size(96.dp, 54.dp)
            )
        }
    }
}

// endregion

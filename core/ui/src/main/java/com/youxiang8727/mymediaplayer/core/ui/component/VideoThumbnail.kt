package com.youxiang8727.mymediaplayer.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 無狀態影片縮圖元件。
 *
 * - `url` 非空白：以 Coil [AsyncImage] 載入，[ContentScale.Crop] 裁切，
 *   placeholder / error 皆以主題色 `surfaceVariant` 塊呈現。
 * - `url` 為空白：顯示主題色 `surfaceVariant` 佔位塊。
 *
 * 尺寸由呼叫方透過 [modifier] 控制（含 [clip]）。
 *
 * @param overlayContent 疊加於縮圖之上的 slot（[BoxScope]），
 *   呼叫方可自行 align 到任意角落（例：右下角 DurationBadge）。
 *   預設為空白——不疊加任何內容。
 */
@Composable
fun VideoThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.small),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        overlayContent()
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
    name = "VideoThumbnail - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "VideoThumbnail - Light"
)
@Composable
private fun VideoThumbnailPreview() {
    MyMediaPlayerTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            VideoThumbnail(
                url = THUMBNAIL_URL,
                modifier = Modifier.size(96.dp, 54.dp)
            )
            VideoThumbnail(
                url = "",
                modifier = Modifier.size(96.dp, 54.dp)
            )
            VideoThumbnail(
                url = THUMBNAIL_URL,
                modifier = Modifier.size(96.dp, 54.dp)
            ) {
                DurationBadge(
                    duration = "3:45",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }
        }
    }
}

// endregion

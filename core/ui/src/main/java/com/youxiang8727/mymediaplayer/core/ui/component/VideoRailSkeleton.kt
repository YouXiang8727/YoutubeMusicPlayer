package com.youxiang8727.mymediaplayer.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme

/**
 * 影片橫向 rail 載入骨架（skeleton placeholder）。
 *
 * 模擬 rail 載入完成後的外觀——標題列 + 橫向卡片，讓使用者預知「這裡將出現什麼」，
 * 並以整層 pulse 透明度動畫提示載入中。卡片輪廓（寬 140dp、縮圖高 80dp、LazyRow 間距
 * 12dp）與 feature:discover 的 `ChartRailItem`／`ChartRail` 對齊，減少 loading → loaded
 * 佈局跳動。首個使用點為 feature:discover 熱門榜單 rail；命名採通用語意，未來搜尋結果
 * 等橫向 rail 載入處可複用。
 *
 * @param modifier 套用於整體（pulse 的 `graphicsLayer` 疊加在其後）
 * @param itemCount placeholder 卡片數量（預設 5，對應 rail 首屏約 2.5 卡＋可捲動提示）
 * @param showTitle 是否顯示標題列 placeholder（預設 true，對應 rail 版面）
 */
@Composable
fun VideoRailSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
    showTitle: Boolean = true
) {
    val pulseAlpha by rememberInfiniteTransition(label = "skeletonPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Column(modifier = modifier.graphicsLayer { alpha = pulseAlpha }) {
        if (showTitle) {
            // 標題列 placeholder：寬度 120dp、高度 20dp，與 titleMedium 行高（24sp）相近
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(itemCount) {
                VideoRailSkeletonCard()
            }
        }
    }
}

/** 單張 skeleton 卡片：輪廓與 feature:discover `ChartRailItem` 對齊（寬 140dp、縮圖高 80dp）。 */
@Composable
private fun VideoRailSkeletonCard() {
    Column(modifier = Modifier.width(140.dp)) {
        // 縮圖佔位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(4.dp))
        // 歌名佔位（寬 70%、高 12dp）
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(6.dp))
        // 歌手佔位（寬 50%、高 10dp）
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

// region Preview

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "VideoRailSkeleton - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "core-ui",
    name = "VideoRailSkeleton - Light"
)
@Composable
private fun VideoRailSkeletonPreview() {
    MyMediaPlayerTheme {
        VideoRailSkeleton(modifier = Modifier.fillMaxWidth())
    }
}

// endregion
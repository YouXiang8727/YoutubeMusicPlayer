package com.youxiang8727.mymediaplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode
import kotlinx.coroutines.launch

/**
 * 常駐底部的迷你播放控制列（App 前景時）。
 * 無狀態 Composable；狀態由 ViewModel 的 PlaybackSnapshot 驅動。
 *
 * 支援向上拖曳展開至螢幕 50% 高度，顯示播放佇列。
 */
@Composable
fun MiniPlayerBar(
    snapshot: PlaybackSnapshot,
    queue: List<PlayQueueItem>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onSeekToIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val expandedHeight = (screenHeightDp * 0.5f).dp
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var toastRef by remember { mutableStateOf<Toast?>(null) }

    fun showToast(message: String) {
        toastRef?.cancel()
        toastRef = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    // 目前播放的索引（用 videoId 比對）
    val currentIndex = queue.indexOfFirst { it.videoId == snapshot.videoId }.takeIf { it >= 0 }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isExpanded) expandedHeight else 96.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // 拖曳結束：依位移決定展開/收起（負位移 = 上滑 = 展開）
                        if (dragY.value < -50f && !isExpanded) onToggleExpand()
                        else if (dragY.value > 50f && isExpanded) onToggleExpand()
                        scope.launch { dragY.snapTo(0f) }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { dragY.snapTo(dragY.value + dragAmount.y) }
                }
            },
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (isExpanded) {
                // 展開狀態：頂部 drag handle
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "播放佇列",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = onClearQueue) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "清空佇列",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 佇列列表
                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "佇列為空",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(queue, key = { _, item -> item.videoId }) { index, item ->
                            QueueRow(
                                item = item,
                                isCurrent = index == currentIndex,
                                onClick = {
                                    if (index != currentIndex) onSeekToIndex(index)
                                },
                                onRemove = { onRemoveFromQueue(index) }
                            )
                        }
                    }
                }

                // 底部操作列
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    TextButton(
                        onClick = onSaveAsPlaylist,
                        enabled = queue.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("存為播放清單")
                    }
                }
            } else {
                // 收起狀態：既有控制列
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "展開播放佇列",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = snapshot.title.ifBlank { "未在播放" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                val willEnable = !snapshot.shuffleEnabled
                                onToggleShuffle()
                                showToast(if (willEnable) "隨機播放：開啟" else "隨機播放：關閉")
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (snapshot.shuffleEnabled) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle
                                ),
                                contentDescription = if (snapshot.shuffleEnabled) "關閉隨機播放" else "開啟隨機播放",
                                tint = if (snapshot.shuffleEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onPrevious, enabled = snapshot.hasCurrent, modifier = Modifier.size(40.dp)) {
                            Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一首")
                        }

                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            enabled = snapshot.hasCurrent,
                            modifier = Modifier
                                .size(44.dp)
                                .semantics { contentDescription = if (snapshot.isPlaying) "暫停" else "播放" }
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (snapshot.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                                ),
                                contentDescription = null
                            )
                        }

                        IconButton(onClick = onNext, enabled = snapshot.hasCurrent, modifier = Modifier.size(40.dp)) {
                            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一首")
                        }

                        IconButton(
                            onClick = {
                                val nextMode = snapshot.repeatMode.next()
                                onCycleRepeat()
                                val label = if (nextMode == RepeatMode.ONE) "單曲循環" else "清單循環"
                                showToast("循環模式：$label")
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            val repeatOne = snapshot.repeatMode == RepeatMode.ONE
                            Icon(
                                painter = painterResource(
                                    if (repeatOne) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                                ),
                                contentDescription =
                                    if (repeatOne) "切換為清單循環" else "切換為單曲循環",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var dragPosition by remember(snapshot.videoId) { mutableFloatStateOf(Float.NaN) }
                        var isDragging by remember { mutableStateOf(false) }

                        val sliderValue = when {
                            isDragging && !dragPosition.isNaN() -> dragPosition
                            else -> snapshot.positionMs.toFloat()
                        }
                        Slider(
                            value = sliderValue.coerceIn(0f, snapshot.durationMs.coerceAtLeast(1L).toFloat()),
                            onValueChange = {
                                isDragging = true
                                dragPosition = it
                            },
                            onValueChangeFinished = {
                                if (!dragPosition.isNaN()) onSeek(dragPosition.toLong())
                                isDragging = false
                                dragPosition = Float.NaN
                            },
                            valueRange = 0f..snapshot.durationMs.coerceAtLeast(1L).toFloat(),
                            enabled = snapshot.hasCurrent && snapshot.durationMs > 0,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${formatTime(snapshot.positionMs)} / ${formatTime(snapshot.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 佇列單行：歌名 + 目前播放高亮 + 移除按鈕。 */
@Composable
private fun QueueRow(
    item: PlayQueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除 ${item.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** mm:ss（超過 1 小時顯示 h:mm:ss）。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

// region Previews

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-player",
    name = "MiniPlayerBar - Playing - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-player",
    name = "MiniPlayerBar - Playing - Light"
)
@Composable
private fun MiniPlayerBarPlayingPreview() {
    MyMediaPlayerTheme {
        MiniPlayerBar(
            snapshot = PlaybackSnapshot(
                hasCurrent = true,
                videoId = "dQw4w9WgXcQ",
                title = "晴天",
                isPlaying = true,
                positionMs = 83_000,
                durationMs = 269_000,
                shuffleEnabled = false,
                repeatMode = RepeatMode.ALL
            ),
            queue = listOf(
                PlayQueueItem("dQw4w9WgXcQ", "晴天"),
                PlayQueueItem("abc12345678", "夜曲 Live")
            ),
            isExpanded = false,
            onToggleExpand = {},
            onTogglePlayPause = {}, onNext = {}, onPrevious = {},
            onToggleShuffle = {}, onCycleRepeat = {}, onSeek = {},
            onSeekToIndex = {}, onRemoveFromQueue = {}, onClearQueue = {},
            onSaveAsPlaylist = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-player",
    name = "MiniPlayerBar - Expanded - Dark"
)
@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
    locale = "zh_TW",
    fontScale = 1.0f,
    device = Devices.PIXEL_7_PRO,
    group = "feature-player",
    name = "MiniPlayerBar - Expanded - Light"
)
@Composable
private fun MiniPlayerBarExpandedPreview() {
    MyMediaPlayerTheme {
        MiniPlayerBar(
            snapshot = PlaybackSnapshot(
                hasCurrent = true,
                videoId = "bbb",
                title = "一首非常非常長的歌名會被省略號截斷嗎",
                isPlaying = false,
                positionMs = 12_000,
                durationMs = 3_721_000,
                shuffleEnabled = true,
                repeatMode = RepeatMode.ONE
            ),
            queue = listOf(
                PlayQueueItem("dQw4w9WgXcQ", "晴天"),
                PlayQueueItem("abc12345678", "夜曲 Live"),
                PlayQueueItem("xyz98765432", "青花瓷"),
                PlayQueueItem("def45678901", "稻香"),
                PlayQueueItem("bbb", "一首非常非常長的歌名會被省略號截斷嗎")
            ),
            isExpanded = true,
            onToggleExpand = {},
            onTogglePlayPause = {}, onNext = {}, onPrevious = {},
            onToggleShuffle = {}, onCycleRepeat = {}, onSeek = {},
            onSeekToIndex = {}, onRemoveFromQueue = {}, onClearQueue = {},
            onSaveAsPlaylist = {}
        )
    }
}

// endregion
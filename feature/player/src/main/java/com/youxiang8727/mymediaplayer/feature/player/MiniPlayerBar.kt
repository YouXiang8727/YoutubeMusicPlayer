package com.youxiang8727.mymediaplayer.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.youxiang8727.mymediaplayer.core.ui.theme.MyMediaPlayerTheme
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode

/**
 * 常駐底部的迷你播放控制列（App 前景時）。
 * 無狀態 Composable；狀態由 ViewModel 的 PlaybackSnapshot 驅動。
 */
@Composable
fun MiniPlayerBar(
    snapshot: PlaybackSnapshot,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var toastRef by remember { mutableStateOf<Toast?>(null) }

    fun showToast(message: String) {
        toastRef?.cancel()
        toastRef = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
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

@Preview(showBackground = true, name = "MiniPlayer - 播放中")
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
            onTogglePlayPause = {}, onNext = {}, onPrevious = {},
            onToggleShuffle = {}, onCycleRepeat = {}, onSeek = {}
        )
    }
}

@Preview(showBackground = true, name = "MiniPlayer - 單曲循環＋隨機＋暫停")
@Composable
private fun MiniPlayerBarPausedPreview() {
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
            onTogglePlayPause = {}, onNext = {}, onPrevious = {},
            onToggleShuffle = {}, onCycleRepeat = {}, onSeek = {}
        )
    }
}

// endregion

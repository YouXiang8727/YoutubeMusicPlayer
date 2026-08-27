package com.youxiang8727.mymediaplayer.feature.player.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode
import com.youxiang8727.mymediaplayer.feature.player.service.MusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * 以 [MediaController] 操作 MusicService 的 MediaSession，
 * 並把 Player 事件 + 定時取樣折疊成 [PlaybackSnapshot] StateFlow。
 */
@Singleton
class MediaControllerPlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controllerFlow = MutableStateFlow<MediaController?>(null)

    private val _playback = MutableStateFlow(PlaybackSnapshot())
    override val playback: StateFlow<PlaybackSnapshot> = _playback.asStateFlow()

    init {
        // SessionToken 解析依賴 manifest 的 MediaSessionService intent-filter；
        // 失敗時降級為「未連線」（playback 停留空狀態），不可炸掉 composition。
        runCatching { connectToSession() }
            .onFailure { android.util.Log.e(TAG, "MediaController 連線初始化失敗", it) }

        scope.launch {
            controllerFlow.collect { controller ->
                if (controller == null) {
                    _playback.value = PlaybackSnapshot()
                } else {
                    observeSnapshot(controller)
                }
            }
        }
    }

    private fun connectToSession() {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { controllerFlow.value = future.get() }
                    .onFailure { android.util.Log.e(TAG, "MediaController 連線失敗", it) }
            },
            { command -> command.run() } // direct executor：主執行緒回呼
        )
    }

    /** 監聽 Player 事件並定時取樣 position，折疊成快照流。 */
    private suspend fun observeSnapshot(controller: MediaController) {
        val events = callbackFlow {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { trySend(Unit) }
                override fun onPlaybackStateChanged(playbackState: Int) { trySend(Unit) }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { trySend(Unit) }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { trySend(Unit) }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { trySend(Unit) }
                override fun onShuffleModeEnabledChanged(enabled: Boolean) { trySend(Unit) }
                override fun onRepeatModeChanged(repeatMode: Int) { trySend(Unit) }
                override fun onPlayerError(error: PlaybackException) { trySend(Unit) }
            }
            controller.addListener(listener)
            awaitClose { controller.removeListener(listener) }
        }

        // position 是連續值、listener 只有事件驅動 → 以 250ms ticker 取樣進度
        val ticker = kotlinx.coroutines.flow.flow {
            while (true) {
                emit(Unit)
                delay(PROGRESS_INTERVAL_MS)
            }
        }

        combine(events, ticker) { _, _ -> controller.toSnapshot() }
            .onStart { emit(controller.toSnapshot()) }
            .collect { _playback.value = it }
    }

    private fun MediaController.toSnapshot(): PlaybackSnapshot {
        val hasCurrent = currentMediaItem != null && playbackState != Player.STATE_IDLE
        // playerError 由 ExoPlayer 保留到下次 prepare() 自動清除，無需手動管理生命週期
        val errorMessage = playerError?.let { PlaybackErrorDescriber.describe(it.errorCodeName, it.causeChainMessages()) }
        return PlaybackSnapshot(
            hasCurrent = hasCurrent,
            videoId = currentMediaItem?.mediaId.orEmpty(),
            title = currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
            isPlaying = isPlaying,
            positionMs = contentPosition.coerceAtLeast(0L),
            durationMs = if (contentDuration == C.TIME_UNSET) 0L else contentDuration,
            shuffleEnabled = shuffleModeEnabled,
            repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) RepeatMode.ONE else RepeatMode.ALL,
            errorMessage = errorMessage
        )
    }

    /**
     * 從 PlaybackException 取值的薄介面卡：收集 cause chain 各層 message（最外層→最深）。
     * 限深以防異常的循環 cause chain；映射邏輯在純 Kotlin 的 [PlaybackErrorDescriber]。
     */
    private fun Throwable.causeChainMessages(): List<String?> =
        generateSequence(this) { it.cause }
            .take(MAX_CAUSE_CHAIN_DEPTH)
            .drop(1) // 不含 PlaybackException 自身的泛用訊息，無 cause 時走 errorCodeName 兜底
            .map { it.message }
            .toList()

    private inline fun withController(block: (MediaController) -> Unit) {
        controllerFlow.value?.let(block)
    }

    override fun play(videoId: String, title: String) {
        // startForegroundService 啟動服務並帶入 PLAY 指令；
        // MediaController 的 bind 不會觸發 onStartCommand，兩者相輔相成。
        val intent = Intent(context, MusicService::class.java)
            .setAction(MusicService.ACTION_PLAY)
            .putExtra(MusicService.EXTRA_VIDEO_ID, videoId)
            .putExtra(MusicService.EXTRA_TITLE, title)
        context.startForegroundService(intent)
    }

    override fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

    override fun seekToNext() = withController { it.seekToNext() }

    override fun seekToPrevious() = withController { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    override fun cycleRepeatMode() = withController {
        it.repeatMode =
            if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
    }

    override fun stop() {
        context.startService(Intent(context, MusicService::class.java).setAction(MusicService.ACTION_STOP))
    }

    companion object {
        private const val TAG = "PlayerController"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val MAX_CAUSE_CHAIN_DEPTH = 16
    }
}

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * 以 [MediaController] 操作 MusicService 的 MediaSession，
 * 並把 Player 事件 + 定時取樣折疊成 [PlaybackSnapshot] StateFlow。
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest 在 coroutines 1.9.0 仍標 experimental
class MediaControllerPlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controllerFlow = MutableStateFlow<MediaController?>(null)

    /** 避免初次 play() 與 init 的連線重疊觸發兩顆 MediaController（會漏綁第一顆）。 */
    private var connectInFlight = false

    private val _playback = MutableStateFlow(PlaybackSnapshot())
    override val playback: StateFlow<PlaybackSnapshot> = _playback.asStateFlow()

    init {
        // SessionToken 解析依賴 manifest 的 MediaSessionService intent-filter；
        // 失敗時降級為「未連線」（playback 停留空狀態），不可炸掉 composition。
        runCatching { connectToSession() }
            .onFailure { android.util.Log.e(TAG, "MediaController 連線初始化失敗", it) }

        // flatMapLatest：controller 換人（release → null，或 reconnect → 新 controller）時，
        // 自動取消上一顆的觀察迴圈並切到新的；避免舊 controller 一直把資料塞進 _playback。
        scope.launch {
            controllerFlow
                .flatMapLatest { controller ->
                    if (controller == null) {
                        flowOf(PlaybackSnapshot())
                    } else {
                        observeSnapshot(controller)
                    }
                }
                .collect { _playback.value = it }
        }
    }

    private fun connectToSession() {
        if (connectInFlight) return
        connectInFlight = true
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connectInFlight = false
                runCatching { controllerFlow.value = future.get() }
                    .onFailure { android.util.Log.e(TAG, "MediaController 連線失敗", it) }
            },
            { command -> command.run() } // direct executor：主執行緒回呼
        )
    }

    /**
     * 釋放目前連線的 MediaController（供 App 容器層在滑掉任務時呼叫）。
     * 釋放後 controllerFlow = null → 快照切回空狀態；下次任何需要 controller 的動作
     * 會經 [ensureConnected] 重新建立連線，不需重建本 instance。
     */
    fun release() {
        controllerFlow.value?.release()
        controllerFlow.value = null
    }

    /** 需要 controller 的動作前排程連線：null（滑掉任務後）→ 重新 buildAsync。 */
    private fun ensureConnected() {
        if (controllerFlow.value == null) {
            runCatching { connectToSession() }
                .onFailure { android.util.Log.e(TAG, "MediaController 重連失敗", it) }
        }
    }

    /** 監聽 Player 事件並定時取樣 position，折疊成快照流。 */
    private fun observeSnapshot(controller: MediaController): Flow<PlaybackSnapshot> {
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

        return combine(events, ticker) { _, _ -> controller.toSnapshot() }
            .onStart { emit(controller.toSnapshot()) }
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
        // 滑掉任務後 controller 已被 release；先確保連線，再帶 PLAY 指令啟動服務。
        // MediaController 的 bind 不會觸發 onStartCommand，兩者相輔相成；
        // service 重建後 session 才存在，buildAsync 連線為異步，快照短暫停留空狀態屬預期。
        ensureConnected()
        val intent = Intent(context, MusicService::class.java)
            .setAction(MusicService.ACTION_PLAY)
            .putExtra(MusicService.EXTRA_VIDEO_ID, videoId)
            .putExtra(MusicService.EXTRA_TITLE, title)
        context.startForegroundService(intent)
    }

    override fun togglePlayPause() { ensureConnected(); withController { if (it.isPlaying) it.pause() else it.play() } }

    override fun seekToNext() { ensureConnected(); withController { it.seekToNext() } }

    override fun seekToPrevious() { ensureConnected(); withController { it.seekToPrevious() } }

    override fun seekTo(positionMs: Long) { ensureConnected(); withController { it.seekTo(positionMs) } }

    override fun toggleShuffle() { ensureConnected(); withController { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    override fun cycleRepeatMode() { ensureConnected(); withController {
        it.repeatMode =
            if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
    } }

    override fun stop() {
        context.startService(Intent(context, MusicService::class.java).setAction(MusicService.ACTION_STOP))
    }

    companion object {
        private const val TAG = "PlayerController"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val MAX_CAUSE_CHAIN_DEPTH = 16
    }
}

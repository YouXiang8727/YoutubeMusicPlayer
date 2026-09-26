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
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
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

    private val _queue = MutableStateFlow<List<PlayQueueItem>>(emptyList())
    override val queue: StateFlow<List<PlayQueueItem>> = _queue.asStateFlow()

    init {
        // SessionToken 解析依賴 manifest 的 MediaSessionService intent-filter；
        // 失敗時降級為「未連線」（playback 停留空狀態），不可炸掉 composition。
        runCatching { connectToSession() }
            .onFailure { android.util.Log.e(TAG, "MediaController 連線初始化失敗", it) }

        scope.launch {
            controllerFlow.collect { controller ->
                if (controller == null) {
                    _playback.value = PlaybackSnapshot()
                    _queue.value = emptyList()
                } else {
                    // Bug A 修復：observeSnapshot 內部對無窮 combine 流 collect、永不返回，
                    // 順序 invoke 會卡死導致 observeQueue 永不執行；改為並行子 job，
                    // collect block 立即返回，兩條觀察流同時運行。
                    launch { observeSnapshot(controller) }
                    launch { observeQueue(controller) }
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

    /** 監聽佇列變更（播放清單 metadata 改變、歌曲切換），折疊成 PlayQueueItem 列表流。 */
    private suspend fun observeQueue(controller: MediaController) {
        val events = callbackFlow {
            val listener = object : Player.Listener {
                override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) { trySend(Unit) }
                // Bug B 修復：setMediaItems() 換置佇列時的主力事件是 onTimelineChanged
                //（reason = TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED），缺此事件佇列變更不會重讀。
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { trySend(Unit) }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { trySend(Unit) }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { trySend(Unit) }
            }
            controller.addListener(listener)
            awaitClose { controller.removeListener(listener) }
        }

        events
            .onStart { emit(Unit) }
            .collect { _queue.value = controller.toQueue() }
    }

    private fun MediaController.toQueue(): List<PlayQueueItem> {
        val count = mediaItemCount
        if (count == 0) return emptyList()
        return (0 until count).map { index ->
            val item = getMediaItemAt(index)
            PlayQueueItem(
                videoId = item.mediaId,
                title = item.mediaMetadata?.title?.toString() ?: item.mediaId
            )
        }
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

    override fun playQueue(items: List<PlayQueueItem>, startIndex: Int) {
        // 空佇列無意義：不起服務、不觸發播放（no-op）
        if (items.isEmpty()) return
        val videoIds = ArrayList<String>()
        val titles = ArrayList<String>()
        items.forEach { videoIds += it.videoId; titles += it.title }
        val intent = Intent(context, MusicService::class.java)
            .setAction(MusicService.ACTION_PLAY_QUEUE)
            .putStringArrayListExtra(MusicService.EXTRA_QUEUE_VIDEO_IDS, videoIds)
            .putStringArrayListExtra(MusicService.EXTRA_QUEUE_TITLES, titles)
            .putExtra(MusicService.EXTRA_QUEUE_START_INDEX, startIndex.coerceAtLeast(0))
        context.startForegroundService(intent)
    }

    override fun addToQueue(videoId: String, title: String) {
        // 佇列 append 統一由 MusicService 端處理（與 play/playQueue 同一哲學）：
        // Controller 只做 Intent 轉送，佇列操作集中在服務端單一入口。
        val intent = Intent(context, MusicService::class.java)
            .setAction(MusicService.ACTION_ADD_TO_QUEUE)
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

    override fun seekToIndex(index: Int) = withController { it.seekTo(index, 0L) }

    override fun removeFromQueue(index: Int) = withController {
        val currentIndex = it.currentMediaItemIndex
        it.removeMediaItem(index)
        // 移除的是目前播放項之前，則 currentMediaItemIndex 會位移，重指回原曲
        if (index < currentIndex) it.seekTo(currentIndex - 1, 0L)
    }

    override fun clearQueue() = withController { it.clearMediaItems() }

    companion object {
        private const val TAG = "PlayerController"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val MAX_CAUSE_CHAIN_DEPTH = 16
    }
}

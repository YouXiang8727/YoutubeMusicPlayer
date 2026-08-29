package com.youxiang8727.mymediaplayer.feature.player.service

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.feature.player.playback.MediaControllerPlayerController
import com.youxiang8727.mymediaplayer.feature.player.playback.PlaybackQueueBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前景媒體服務（Media3 MediaSessionService）。
 *
 * - 播放佇列：點播的歌在播放清單中 → 整份清單從該曲起播；否則單曲。
 * - 串流 URL 以 ResolvingDataSource 於載入當下逐首解析（NewPipe），loader thread 內同步等待。
 * - 隨機/循環由 ExoPlayer 原生支援，系統通知、鎖屏、藍牙耳機鍵皆可用。
 * - 通知列採用自訂 [PlayerMediaNotificationProvider]（RemoteViews）：每顆按鈕獨立點擊反饋、
 *   root 設 content intent 返回 App、保留進度條；隨機/循環 icon 隨播放模式切換。
 */
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject lateinit var streamResolver: AudioStreamRepository
    @Inject lateinit var playlistRepository: PlaylistRepository

    /**
     * App 側的 MediaController（MediaControllerPlayerController）綁住本 service，會讓
     * service 在滑掉任務後無法真正 destroy（onDestroy 不觸發 ⇒ 通知殘留）。此處注入以便
     * [onTaskRemoved] 時釋放其綁定，讓 service 得以終止；釋放後下次 play() 仍需重連。
     */
    @Inject lateinit var playerController: MediaControllerPlayerController

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressTickerJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()

        val newPlayer = ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(resolvingDataSourceFactory())
            )
            .build()
        newPlayer.repeatMode = Player.REPEAT_MODE_ALL
        player = newPlayer
        mediaSession = MediaSession.Builder(this, newPlayer)
            .setCallback(sessionCallback)
            .build()

        setMediaNotificationProvider(
            PlayerMediaNotificationProvider(this)
        )

        // 監聽播放模式變更，重新繪製通知列（icon 隨狀態切換）
        newPlayer.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                triggerNotificationUpdate()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                triggerNotificationUpdate()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 播放／暫停切換時重繪（play/pause icon），並維持進度 ticker 運作
                triggerNotificationUpdate()
            }
        })

        startProgressTicker()
    }

    /**
     * 自訂 Provider 不會像 DefaultMediaNotificationProvider 定期重繪進度：
     * 以 service scope 每秒喚醒一次，播放中觸發 [triggerNotificationUpdate] 重建 RemoteViews。
     */
    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = serviceScope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    triggerNotificationUpdate()
                    delay(PROGRESS_TICK_INTERVAL_MS)
                } else {
                    // 未播放時不需每秒重繪；略等後再檢查（避免 busy loop）
                    delay(IDLE_TICK_INTERVAL_MS)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE) ?: videoId
                if (videoId.isBlank()) stopSelf() else handlePlay(videoId, title)
            }
            ACTION_STOP -> {
                player?.stop()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** 載入佇列並從點播的曲目開始播放。 */
    private fun handlePlay(videoId: String, title: String) {
        serviceScope.launch {
            // 找出包含該 videoId 的播放清單，以其項目建構佇列
            val playlists = playlistRepository.observeAllPlaylists().first()
            var playlistItems = emptyList<com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem>()
            for (playlist in playlists) {
                val items = playlistRepository.observePlaylistItems(playlist.id).first()
                if (items.any { it.videoId == videoId }) {
                    playlistItems = items
                    break
                }
            }
            val queue = PlaybackQueueBuilder.build(playlistItems, videoId, title)
            player?.apply {
                // Save current playback modes before setting new items
                val currentShuffleMode = shuffleModeEnabled
                val currentRepeatMode = repeatMode

                setMediaItems(queue.entries.map { it.toMediaItem() })
                seekTo(queue.startIndex, 0L)

                // Re-apply playback modes after setting new items
                shuffleModeEnabled = currentShuffleMode
                repeatMode = currentRepeatMode

                prepare()
                playWhenReady = true
            }
        }
    }

    // region MediaSession callback（通知列上的隨機 / 循環按鈕）

    private val sessionCallback = object : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_CYCLE_REPEAT, Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_TOGGLE_SHUFFLE ->
                    player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                COMMAND_CYCLE_REPEAT ->
                    player?.let {
                        it.repeatMode =
                            if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL
                            else Player.REPEAT_MODE_ONE
                    }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    // endregion

    /**
     * 逐首解析串流 URL：MediaItem.uri 指向 watch 頁，開流時攔截換成真實音訊 URL。
     * Resolver 於 ExoPlayer loader thread 呼叫，可同步做網路工作。
     */
    private fun resolvingDataSourceFactory(): DataSource.Factory {
        val resolvedUrls = ConcurrentHashMap<String, String>()
        val resolver = ResolvingDataSource.Resolver { dataSpec ->
            val videoId = dataSpec.uri.getQueryParameter("v")
                ?: dataSpec.uri.lastPathSegment
                ?: throw IOException("無法從 URI 取得 videoId：${dataSpec.uri}")
            val url = resolvedUrls.getOrPut(videoId) {
                runBlockingResolve(videoId)
            }
            dataSpec.buildUpon().setUri(url).build()
        }
        return ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            resolver
        )
    }

    private fun runBlockingResolve(videoId: String): String =
        kotlinx.coroutines.runBlocking {
            streamResolver.resolveAudioUrl(videoId)
                .getOrElse { throw IOException("解析串流失敗：${it.message}", it) }
        }

    private fun PlaybackQueueBuilder.QueueEntry.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(videoId)
            .setUri("https://www.youtube.com/watch?v=$videoId") // 由 ResolvingDataSource 替換
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    /** 使用者從最近使用清單劃掉 App：一律關閉 Service 與播放（不做「沒在播才停」的條件判斷）。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // pauseAllPlayersAndStopSelf() 會 pause 播放、把 Service 移出 foreground 並 stopSelf()，
        // 是唯一能在「播放中」安全終止 foreground service 的途徑（單純 stopSelf 會被系統重建）。
        // 但 service 因 App 側 MediaController（playerController）仍 binding 而無法真正 destroy
        // （onDestroy 不觸發），導致 session 仍在 ⇒ MediaNotificationManager 不會撤下通知。
        // Media3 官方對「有外部 controller 綁住 service」的建議（MediaSessionService.onTaskRemoved
        // javadoc）：release session 再 stopSelf，讓所有 controller 斷線、通知撤下、service 得以
        // 真正 destroy。故先停播，再釋放 session 與 App 側 controller（釋放後 ensureConnected()
        // 仍會於下次 play() 重新連線，不影響從 recents 重開）。
        pauseAllPlayersAndStopSelf()

        mediaSession?.release()
        mediaSession = null

        playerController.release()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.youxiang8727.mymediaplayer.action.PLAY"
        const val ACTION_STOP = "com.youxiang8727.mymediaplayer.action.STOP"
        const val COMMAND_TOGGLE_SHUFFLE = "com.youxiang8727.mymediaplayer.command.TOGGLE_SHUFFLE"
        const val COMMAND_CYCLE_REPEAT = "com.youxiang8727.mymediaplayer.command.CYCLE_REPEAT"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"

        /** 播放中進度重繪間隔（毫秒）。 */
        private const val PROGRESS_TICK_INTERVAL_MS = 1000L
        /** 未播放時輪詢間隔（毫秒），避免 busy loop。 */
        private const val IDLE_TICK_INTERVAL_MS = 250L
    }
}

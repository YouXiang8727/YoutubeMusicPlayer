package com.youxiang8727.mymediaplayer.feature.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.feature.player.R
import com.youxiang8727.mymediaplayer.feature.player.playback.PlaybackQueueBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 前景媒體服務（Media3 MediaSessionService）。
 *
 * - 播放佇列：點播的歌在播放清單中 → 整份清單從該曲起播；否則單曲。
 * - 串流 URL 以 ResolvingDataSource 於載入當下逐首解析（NewPipe），loader thread 內同步等待。
 * - 隨機/循環由 ExoPlayer 原生支援，系統通知、鎖屏、藍牙耳機鍵皆可用。
 */
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject lateinit var streamResolver: AudioStreamRepository
    @Inject lateinit var playlistRepository: PlaylistRepository

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.feature_player_channel_playback)
                .build()
                .apply { setSmallIcon(R.drawable.ic_music_notification) }
        )

        // 監聽播放模式變更，重新設定通知列 custom layout（icon 隨狀態切換）
        newPlayer.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                refreshNotificationCustomLayout()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                refreshNotificationCustomLayout()
            }
        })
    }

    /** 重新設定通知列 custom layout，使 icon 反映最新播放模式。 */
    private fun refreshNotificationCustomLayout() {
        val session = mediaSession ?: return
        // 取目前所有已連線的 controller，逐一重新設定
        for (i in 0 until session.connectedControllers.size) {
            val controller = session.connectedControllers[i]
            session.setCustomLayout(controller, buildCustomLayout(session))
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

        /** 通知列上的隨機／循環按鈕（custom layout 於連線後掛載）。 */
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            session.setCustomLayout(controller, buildCustomLayout(session))
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

    /**
     * 依目前播放模式動態選 icon 的 custom layout：
     * - 隨機：已啟用 → ic_shuffle_active / 未啟用 → ic_shuffle
     * - 循環：ALL → ic_repeat / ONE → ic_repeat_one
     */
    private fun buildCustomLayout(session: MediaSession): ImmutableList<CommandButton> {
        val p = player
        val shuffleIcon = if (p?.shuffleModeEnabled == true) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle
        val repeatIcon = when (p?.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat
        }
        return ImmutableList.of(
            CommandButton.Builder()
                .setDisplayName("隨機播放")
                .setIconResId(shuffleIcon)
                .setSessionCommand(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build(),
            CommandButton.Builder()
                .setDisplayName("循環模式（清單／單曲）")
                .setIconResId(repeatIcon)
                .setSessionCommand(SessionCommand(COMMAND_CYCLE_REPEAT, Bundle.EMPTY))
                .build()
        )
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

    /** 使用者從最近使用清單劃掉 App：沒在播就收掉服務，避免孤兒前景通知。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 && p.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        // Channel 由 DefaultMediaNotificationProvider 依 CHANNEL_ID 建立，此處不需手動建。
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
    }
}

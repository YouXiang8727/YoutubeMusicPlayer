package com.youxiang8727.mymediaplayer.feature.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(resolvingDataSourceFactory())

        val newPlayer = ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(
                mediaSourceFactory
            ).build()
        newPlayer.repeatMode = Player.REPEAT_MODE_ALL
        player = newPlayer

        // 點通知本體（非按鈕）時把 App 帶回前景；Media3 藉 sessionActivity 設為通知 contentIntent。
        // 以 setClassName 字串指向 app 的 MainActivity，避免 feature:player 對 :app 產生 compile 依賴。
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent().setClassName(
                packageName,
                "com.youxiang8727.mymediaplayer.MainActivity"
            ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, newPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(contentIntent)
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

    // region MediaSession callback（通知列 custom layout 按鈕：常駐上一首/下一首 + 隨機/循環）

    private val sessionCallback = object : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_CYCLE_REPEAT, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_PREVIOUS, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_NEXT, Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        /** 常駐上一首／下一首與隨機／循環按鈕（custom layout 於連線後掛載）。 */
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
                // 常駐「上一首／下一首」：通知列按鈕不受 player command 可用性影響
                COMMAND_PREVIOUS ->
                    player?.let { p ->
                        if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem() else p.seekToDefaultPosition()
                    }
                COMMAND_NEXT ->
                    player?.let { p ->
                        if (p.hasNextMediaItem()) p.seekToNextMediaItem() else p.seekToDefaultPosition()
                    }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * 依目前播放模式動態選 Media3 官方 icon 的 custom layout：
     * - 上一首／下一首：常駐按鈕（SLOT_BACK / SLOT_FORWARD），不受「曲目位置影響」——
     *   系統 prev/next 依 `hasPreviousMediaItem()` / `hasNextMediaItem()` 在清單邊界或單曲時會消失，
     *   故改用自訂 session command 的 CommandButton 佔固定 slot，維持 compact 排版為 [上一首, 播放/暫停, 下一首]；
     *   落點由 onCustomCommand 處理：無上/下一首時重播目前曲目開頭。
     * - 隨機：已啟用 → ICON_SHUFFLE_ON / 未啟用 → ICON_SHUFFLE_OFF（官方 disabled 色）
     * - 循環：ALL → ICON_REPEAT_ALL / ONE → ICON_REPEAT_ONE
     * （隨機／循環維持既有邏輯，僅放入展開區）
     */
    private fun buildCustomLayout(session: MediaSession): ImmutableList<CommandButton> {
        val p = player
        return ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName("上一首")
                .setSessionCommand(SessionCommand(COMMAND_PREVIOUS, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("下一首")
                .setSessionCommand(SessionCommand(COMMAND_NEXT, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(
                if (p?.shuffleModeEnabled == true) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
            )
                .setDisplayName("隨機播放")
                .setSessionCommand(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build(),
            CommandButton.Builder(
                if (p?.repeatMode == Player.REPEAT_MODE_ONE) CommandButton.ICON_REPEAT_ONE else CommandButton.ICON_REPEAT_ALL
            )
                .setDisplayName("循環模式（清單／單曲）")
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
        const val COMMAND_PREVIOUS = "com.youxiang8727.mymediaplayer.command.PREVIOUS"
        const val COMMAND_NEXT = "com.youxiang8727.mymediaplayer.command.NEXT"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
    }
}

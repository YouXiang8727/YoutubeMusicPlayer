package com.youxiang8727.mymediaplayer.feature.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.feature.player.R
import com.youxiang8727.mymediaplayer.feature.player.playback.PlaybackQueueBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 前景媒體服務（Media3 MediaSessionService）。
 *
 * - 播放佇列（Room 路徑）：點播的歌在播放清單中 → 整份清單從該曲起播；否則單曲。
 * - 暫時性佇列（ACTION_PLAY_QUEUE）：熱門榜單等非 Room 清單，以 Intent 平行陣列
 *   （videoIds / titles）直接建佇列起播，**不查 Room**。
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

    /**
     * session 級 memoize：每 videoId 已解析的音訊 URL，避免重複解析。
     * 由 [onPlayerError] 在 content URL 403 失效時針對該 videoId 失效（移除）並強制重解析。
     */
    private val resolvedUrls = ConcurrentHashMap<String, String>()

    /** 403 處理中的 videoId 集合（reentrancy guard）：同一 videoId 不重複觸發重試。 */
    private val pending403Handling = ConcurrentHashMap.newKeySet<String>()

    /** 同一 videoId 的連續 403 自動重試次數（bounded retry，防無限迴圈）。 */
    private val retryCounts = ConcurrentHashMap<String, Int>()

    /** onPlayerError 尚未落地的 markStreamFailed job（key = videoId）；
     *  READY 時可取消，避免 mark/clear 交錯。 */
    private val pendingFailureMarks = ConcurrentHashMap<String, Job>()

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

        // 自訂 MediaNotificationProvider：覆寫 getMediaButtons 回傳固定按鈕序列，
        // 避免父類別在 custom layout 外再補系統 prev/next 造成通知列重複 icon。
        setMediaNotificationProvider(
            CustomMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.feature_player_channel_playback
            ).apply { setSmallIcon(R.drawable.ic_music_notification) }
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

        // 播放錯誤攔截：所有播放錯誤都持久化失敗標記（歌單詳情頁紅框提示）；
        // content URL 403 額外嘗試自動恢復（重新解析同曲或切歌）。
        newPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // 所有播放錯誤：標記該曲失敗（fire-and-forget，僅 Room 播放清單有對應 row）
                val videoId = newPlayer.currentMediaItem?.mediaId
                if (videoId != null) {
                    val markJob = serviceScope.launch {
                        playlistRepository.markStreamFailed(videoId, System.currentTimeMillis())
                    }
                    pendingFailureMarks[videoId] = markJob
                    markJob.invokeOnCompletion { pendingFailureMarks.remove(videoId) }
                }
                // 403 額外處理：失效 memoize + 強制重解析同曲（仍失敗則切歌）
                if (isContentUrl403(error)) {
                    onContentUrl403(error)
                }
                // 非 403 錯誤不攔截：維持既有 snapshot/error 顯示機制，由 UI 呈現。
            }

            // 曲目成功進入播放中（READY）→ 清除失敗標記：
            //   mark（onPlayerError 提交）必然先於 clear（本處提交）送進 Room 單線程 transaction
            //   executor；取消 pending job 是為了防止 cancel 與 DB write 之間的交錯。
            //   暫時性佇列（非 Room 清單）的 clearStreamFailed 為 no-op，不影響。
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    newPlayer.currentMediaItem?.mediaId?.let { mediaId ->
                        retryCounts.remove(mediaId)
                        pendingFailureMarks.remove(mediaId)?.cancel()
                        serviceScope.launch {
                            playlistRepository.clearStreamFailed(mediaId)
                        }
                    }
                }
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
            ACTION_PLAY_QUEUE -> {
                val videoIds = intent.getStringArrayListExtra(EXTRA_QUEUE_VIDEO_IDS).orEmpty()
                val titles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES).orEmpty()
                val startIndex = intent.getIntExtra(EXTRA_QUEUE_START_INDEX, 0)
                // 平行陣列 zip 回 PlayQueueItem（titles 缺項以 "" 兜底，由 builder 的 ifBlank 處理）
                val entries = videoIds.mapIndexed { index, videoId ->
                    PlayQueueItem(videoId = videoId, title = titles.getOrElse(index) { "" })
                }
                if (entries.isEmpty()) stopSelf() else handlePlayQueue(entries, startIndex)
            }
            ACTION_STOP -> {
                player?.stop()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** 載入佇列並從點播的曲目開始播放（Room 路徑：查播放清單）。 */
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
            loadQueueAndPlay(queue)
        }
    }

    /** 暫時性佇列（熱門榜單等非 Room 清單）：直接以傳入清單建佇列起播，不查 Room。 */
    private fun handlePlayQueue(entries: List<PlayQueueItem>, startIndex: Int) {
        val queue = PlaybackQueueBuilder.buildFromEntries(entries, startIndex)
        loadQueueAndPlay(queue)
    }

    /** 兩條路徑共用的佇列載入：setMediaItems + seekTo(start,0) + 保留 shuffle/repeat + prepare + play。 */
    private fun loadQueueAndPlay(queue: PlaybackQueueBuilder.Queue) {
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
            // 連線後同時設定該 controller 的 custom layout 與「廣播級」media button preferences。
            // 兩者用同一份 buildCustomLayout(session)（含 SLOT_BACK / SLOT_FORWARD）：
            // Media3 的 MediaSessionLegacyStub（line 1923-1930）須在「mediaButtonPreferences 非空」
            // 且 custom layout 含 SLOT_BACK/FORWARD 按鈕時，才會從 PlaybackState.actions 移除
            // ACTION_SKIP_TO_PREVIOUS/NEXT，令 SystemUI 的 MediaStyle media surface 不再渲染系統
            // prev/seekbar/next，解決展開通知重複「上一首/下一首」icon。
            session.setCustomLayout(controller, buildCustomLayout(session))
            session.setMediaButtonPreferences(buildCustomLayout(session))
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
        val resolver = ResolvingDataSource.Resolver { dataSpec ->
            val videoId = dataSpec.uri.getQueryParameter("v")
                ?: dataSpec.uri.lastPathSegment
                ?: throw IOException("無法從 URI 取得 videoId：${dataSpec.uri}")
            val url = resolvedUrls.getOrPut(videoId) {
                runBlockingResolve(videoId, force = false)
            }
            dataSpec.buildUpon().setUri(url).build()
        }
        return ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            resolver
        )
    }

    private fun runBlockingResolve(videoId: String, force: Boolean): String =
        kotlinx.coroutines.runBlocking {
            streamResolver.resolveAudioUrl(videoId, force)
                .getOrElse { throw IOException("解析串流失敗：${it.message}", it) }
        }

    // ── 播放錯誤：content URL 403 自動恢復（需求 2 + 3）──

    /**
     * 判斷播放錯誤是否為「內容 URL 403」：
     * 沿 cause chain 找 Media3 [HttpDataSource.InvalidResponseCodeException] 且 responseCode == 403
     * （即已解析的 googlevideo 簽名 URL 過期／被撤銷，播放當下被 DefaultHttpDataSource 以 403 浮出）。
     */
    private fun isContentUrl403(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    /**
     * 處理 content URL 403：
     * 1. 失效該 videoId 的 memoized URL，強制重新解析同曲（需求 2）；成功 → 重試同一首。
     * 2. 重新解析仍失敗 → 依目前 repeatMode 切歌（需求 3）：
     *    - REPEAT_MODE_ONE：重播同一首。
     *    - REPEAT_MODE_ALL / OFF：seekToNextMediaItem（無下一首則重播或自然停，依播放模式）。
     * 併發防護：
     * - pending403Handling 防同一 videoId 併發重入；
     * - retryCounts 限制同一 videoId 連續 403 自動重試次數（[MAX_403_RETRY_PER_VIDEO]），
     *   超過即停止自動處理、讓錯誤交由既有 snapshot/error 機制顯示，避免無限重試迴圈。
     */
    private fun onContentUrl403(error: PlaybackException) {
        val p = player ?: return
        val videoId = p.currentMediaItem?.mediaId ?: return
        if (!pending403Handling.add(videoId)) return // 已在處理中，忽略重入

        val retry = (retryCounts[videoId] ?: 0) + 1
        if (retry > MAX_403_RETRY_PER_VIDEO) {
            // 超出上限：不再自動重試，讓錯誤浮現給 UI 顯示（同時清除 guard 讓後續手動重播可用）
            retryCounts.remove(videoId)
            pending403Handling.remove(videoId)
            return
        }
        retryCounts[videoId] = retry

        serviceScope.launch {
            try {
                // 需求 2：失效 memoize + 強制重解析
                resolvedUrls.remove(videoId)
                val newUrl = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    streamResolver.resolveAudioUrl(videoId, force = true)
                        .getOrElse { throw IOException("重新解析失敗：${it.message}", it) }
                }
                resolvedUrls[videoId] = newUrl
                // 重試同一首：回到原 position
                val position = p.currentPosition.coerceAtLeast(0L)
                p.prepare()
                if (position > 0L) p.seekTo(position)
                p.playWhenReady = true
            } catch (e: Exception) {
                // 重新解析仍失敗 → 需求 3：依播放模式切歌
                advanceOn403(p)
            } finally {
                pending403Handling.remove(videoId)
            }
        }
    }

    /** 重新解析仍失敗時，依 repeatMode 切歌（失敗標記已於 onPlayerError 統一持久化）。 */
    private fun advanceOn403(p: ExoPlayer) {
        when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                // 單曲循環：重播同一首（prepare 會重新載入、重新解析）
                p.prepare()
                p.playWhenReady = true
            }
            else -> {
                // ALL / OFF：優先下一首
                if (p.hasNextMediaItem()) {
                    p.prepare()
                    p.seekToNextMediaItem()
                    p.playWhenReady = true
                } else {
                    // 無下一首：REPEAT_MODE_ALL 會由 ExoPlayer 自動回繞第一首；
                    // REPEAT_MODE_OFF 則準備目前曲目（重試，最終仍失敗時由 UI 顯示錯誤）。
                    p.prepare()
                    p.playWhenReady = true
                }
            }
        }
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
        const val ACTION_PLAY_QUEUE = "com.youxiang8727.mymediaplayer.action.PLAY_QUEUE"
        const val ACTION_STOP = "com.youxiang8727.mymediaplayer.action.STOP"
        const val COMMAND_TOGGLE_SHUFFLE = "com.youxiang8727.mymediaplayer.command.TOGGLE_SHUFFLE"
        const val COMMAND_CYCLE_REPEAT = "com.youxiang8727.mymediaplayer.command.CYCLE_REPEAT"
        const val COMMAND_PREVIOUS = "com.youxiang8727.mymediaplayer.command.PREVIOUS"
        const val COMMAND_NEXT = "com.youxiang8727.mymediaplayer.command.NEXT"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_QUEUE_VIDEO_IDS = "extra_queue_video_ids"
        const val EXTRA_QUEUE_TITLES = "extra_queue_titles"
        const val EXTRA_QUEUE_START_INDEX = "extra_queue_start_index"

        /**
         * 同一 videoId 連續 403 自動重試的上限。超出即停止自動處理、讓錯誤交由 UI 顯示，
         * 防止網路/URL 持續給 403 時無限重試迴圈。成功重解析或切至新曲時 [retryCounts] 會被重設。
         */
        const val MAX_403_RETRY_PER_VIDEO = 3
    }
}

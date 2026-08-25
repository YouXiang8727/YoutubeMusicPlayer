package com.youxiang8727.mymediaplayer.feature.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.feature.player.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前景服務：背景音訊播放。
 * 流程：收到 PLAY → 立刻 startForeground（顯示「解析中」）
 *      → AudioStreamRepository 取得真實串流 URL → ExoPlayer 播放 → 更新通知。
 * 與播放清單無關：直接從搜尋結果點開的影片也能背景播放。
 */
@AndroidEntryPoint
class MusicService : Service() {

    @Inject lateinit var streamResolver: AudioStreamRepository

    private var player: ExoPlayer? = null

    // ExoPlayer 規定只能在主執行緒存取；
    // StreamResolver 內部會自行切換到 IO 做網路工作，回來後仍在主執行緒。
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE) ?: videoId
                if (videoId.isBlank()) {
                    stopSelf()
                } else {
                    handlePlay(videoId, title)
                }
            }
            ACTION_TOGGLE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_STOP -> shutdownAndStop()
        }
        return START_NOT_STICKY
    }

    private fun handlePlay(videoId: String, title: String) {
        // 必須在 startForegroundService 後盡快呼叫 startForeground
        startForeground(NOTIFICATION_ID, buildNotification("解析中… $title"))

        ensurePlayer()

        // 若前一個影片還在解析，取消它
        playJob?.cancel()
        playJob = serviceScope.launch {
            streamResolver.resolveAudioUrl(videoId)
                .onSuccess { url ->
                    if (!isActive) return@launch
                    val item = MediaItem.Builder()
                        .setMediaId(videoId)
                        .setUri(url) // ← 真實串流位址
                        .setMediaMetadata(
                            MediaMetadata.Builder().setTitle(title).build()
                        )
                        .build()
                    player?.apply {
                        setMediaItem(item)
                        prepare()
                        playWhenReady = true
                    }
                    updateNotification(title)
                }
                .onFailure { e ->
                    updateNotification("無法播放：${e.message?.take(48) ?: "未知錯誤"}")
                    delay(2_500)
                    shutdownAndStop()
                }
        }
    }

    private fun ensurePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(applicationContext).build()
        }
    }

    private fun buildNotification(text: String): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_music_notification)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "播放/暫停", actionIntent(ACTION_TOGGLE, requestCode = 1))
            .addAction(0, "停止", actionIntent(ACTION_STOP, requestCode = 2))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    /** 開啟 App 的 Launcher Activity（以 package 反查，避免 feature 依賴 app 層）。 */
    private fun contentIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
        return PendingIntent.getActivity(
            this, 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "背景播放",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun shutdownAndStop() {
        playJob?.cancel()
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.youxiang8727.mymediaplayer.action.PLAY"
        const val ACTION_TOGGLE = "com.youxiang8727.mymediaplayer.action.TOGGLE"
        const val ACTION_STOP = "com.youxiang8727.mymediaplayer.action.STOP"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, videoId: String, title: String) {
            context.startForegroundService(
                Intent(context, MusicService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
                    .putExtra(EXTRA_TITLE, title)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MusicService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

package com.youxiang8727.mymediaplayer.feature.player.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.CustomCommandPendingIntentBuilder
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.PlaybackPendingIntentBuilder
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.youxiang8727.mymediaplayer.feature.player.R

/**
 * 自訂的媒體通知 Provider（RemoteViews 全自訂佈局）。
 *
 * 相較於 [androidx.media3.session.DefaultMediaNotificationProvider] 的 MediaStyle 客製按鈕：
 * - 每一顆按鈕獨立 [android.widget.RemoteViews.setOnClickPendingIntent] → 點擊反饋只落在單一按鈕。
 * - Root view 設 content intent → 點通知返回 App（以 launch intent 重建，不依賴 :app 的 MainActivity class）。
 * - 保留進度條（position / duration）與 seek 能力。
 *
 * 介面簽名依 Media3 1.11.0 的 [MediaNotification.Provider]：
 * 只有 `createNotification` / `handleCustomCommand` / `getNotificationChannelInfo` 三個方法
 * （並無 1.11.0 之前的 handleCommand / dismissNotification / onDestroy）。
 */
class PlayerMediaNotificationProvider(
    private val context: android.content.Context
) : MediaNotification.Provider {

    private val packageManager = context.packageManager

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = mediaSession.player

        val views = android.widget.RemoteViews(
            context.packageName,
            R.layout.notification_player
        )

        // --- 曲目資訊 ---
        val metadata = if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
            player.mediaMetadata
        } else {
            androidx.media3.common.MediaMetadata.EMPTY
        }
        val title = metadata.title?.toString().orEmpty()
        val subtitle = metadata.artist?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: title
        views.setTextViewText(R.id.notification_player_title, title)
        views.setTextViewText(R.id.notification_player_subtitle, subtitle)

        // --- content intent：點通知返回 App ---
        // Media3 的 DecoratedMediaCustomViewStyle 於 API24+ 會委託平台
        // Notification.DecoratedMediaCustomViewStyle 把自訂 RemoteViews 包進「系統裝飾容器」，
        // 其 makeContentView()/makeBigContentView() 回傳 null；點「通知卡片」走的是
        // notification.contentIntent（由 NotificationCompat.Builder.setContentIntent 設定），
        // 而非自訂 RemoteViews root 的 setOnClickPendingIntent（那只套用於容器內部）。
        // 故必須在 builder 層級設定 contentIntent，欄位才不會是 null、點卡片才能返回 App。
        // （root 層的 click 保留作為容器內部點擊的兜底，與 contentIntent 指向同一 PendingIntent。）
        views.setOnClickPendingIntent(R.id.notification_player_root, buildContentIntent())

        // --- 播放／暫停按鈕 ---
        val isPlaying = player.isPlaying
        views.setImageViewResource(
            R.id.notification_player_play,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        views.setOnClickPendingIntent(
            R.id.notification_player_play,
            playbackPendingIntent(Player.COMMAND_PLAY_PAUSE, startForeground = !isPlaying)
        )

        // --- 上一首 / 下一首 ---
        views.setOnClickPendingIntent(
            R.id.notification_player_prev,
            playbackPendingIntent(Player.COMMAND_SEEK_TO_PREVIOUS, startForeground = false)
        )
        views.setOnClickPendingIntent(
            R.id.notification_player_next,
            playbackPendingIntent(Player.COMMAND_SEEK_TO_NEXT, startForeground = false)
        )

        // --- 隨機 / 循環：icon 依播放模式切換（沿用既有 shuffle/repeat 狀態邏輯） ---
        val shuffleIcon =
            if (player.shuffleModeEnabled) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat
        }
        views.setImageViewResource(R.id.notification_player_shuffle, shuffleIcon)
        views.setImageViewResource(R.id.notification_player_repeat, repeatIcon)
        views.setOnClickPendingIntent(
            R.id.notification_player_shuffle,
            customCommandPendingIntent(MusicService.COMMAND_TOGGLE_SHUFFLE)
        )
        views.setOnClickPendingIntent(
            R.id.notification_player_repeat,
            customCommandPendingIntent(MusicService.COMMAND_CYCLE_REPEAT)
        )

        // --- 進度條與時間（position / duration） ---
        bindProgress(views, player)

        val notification = NotificationCompat.Builder(context, MusicService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_notification)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            // 點「通知卡片」返回 App：setContentIntent 是 notification.contentIntent 的唯一來源。
            // 自訂 RemoteViews 在 DecoratedMediaCustomViewStyle 下包進系統裝飾容器，
            // 卡片點擊由 contentIntent 驅動，root 的 setOnClickPendingIntent 無法取代它。
            .setContentIntent(buildContentIntent())
            // Media3 1.11.0 的官方做法：用 DecoratedMediaCustomViewStyle 同時拿到
            // 「系統認得這是媒體通知」(平台 media session token + EXTRA_MEDIA3_SESSION)
            // 與「保留自訂 RemoteViews」(API 24+ 會沿用 setCustomContentView/BigContentView)。
            // MediaSession 在 1.11.0 沒有公開的 sessionCompat 欄位，只有 getPlatformToken()，
            // 此 style 內部會自己解析 token，故不需手動取 token。
            .setStyle(
                MediaStyleNotificationHelper.DecoratedMediaCustomViewStyle(mediaSession)
            )
            .build()

        return MediaNotification(MusicService.NOTIFICATION_ID, notification)
    }

    /**
     * 自訂 command 一律轉送給 session（讓 [androidx.media3.session.MediaSession.Callback.onCustomCommand]
     * 處理，沿用既有的 shuffle / repeat 切換邏輯），故回傳 false。
     */
    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            MusicService.CHANNEL_ID,
            context.getString(R.string.feature_player_channel_playback)
        )

    // --- 內部工具 ---

    private fun bindProgress(
        views: android.widget.RemoteViews,
        player: Player
    ) {
        val durationMs = player.duration
        // currentPosition 對 ExoPlayer 永遠可取（無 media 時回傳 0）
        val positionMs = player.currentPosition.coerceAtLeast(0L)

        if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
            views.setInt(R.id.notification_player_progress, "setProgress", 0)
        } else {
            val pct =
                ((positionMs.toDouble() / durationMs.toDouble()) * PROGRESS_MAX).toInt()
                    .coerceIn(0, PROGRESS_MAX)
            views.setProgressBar(
                R.id.notification_player_progress,
                PROGRESS_MAX,
                pct,
                /* indeterminate= */ false
            )
        }
        views.setTextViewText(R.id.notification_player_position, formatTime(positionMs))
        views.setTextViewText(
            R.id.notification_player_duration,
            if (durationMs > 0L) formatTime(durationMs) else ""
        )
    }

    private fun playbackPendingIntent(command: Int, startForeground: Boolean): PendingIntent =
        PlaybackPendingIntentBuilder(context, command, MusicService::class.java)
            .setStartAsForegroundService(startForeground)
            .build()

    private fun customCommandPendingIntent(action: String): PendingIntent =
        CustomCommandPendingIntentBuilder(
            context,
            MusicService::class.java,
            SessionCommand(action, Bundle.EMPTY)
        ).build()

    private fun buildContentIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        // launch 為 null 時 fallback 為空 Intent，避免 PendingIntent.getActivity 炸掉。
        return PendingIntent.getActivity(
            context,
            0,
            launch ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val PROGRESS_MAX = 1000
    }
}

package com.youxiang8727.mymediaplayer.feature.player.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList

/**
 * 自訂通知列按鈕的 [DefaultMediaNotificationProvider]。
 *
 * 背景：父類別 [DefaultMediaNotificationProvider.getMediaButtons] 會先把
 * `setCustomLayout` 的按鈕（SLOT_BACK / SLOT_FORWARD）放進通知，
 * 再對找不到對應 slot 的按鈕走「系統 prev/next」else-if 分支補上
 * `SEEK_TO_PREVIOUS` / `SEEK_TO_NEXT` —— 兩者同時存在時通知列會重複
 * [跳轉到上一首, 跳轉到下一首]（實機 dumpsys 驗證 7 個 action）。
 *
 * 解法：直接覆寫 [getMediaButtons] 回傳**固定序列** [上一首, 播放/暫停, 下一首,
 * 隨機, 循環]，完全不呼叫父類別那組會補系統 prev/next 的邏輯。上一首／下一首走
 * custom session command（落點由 `MusicService` 的 `onCustomCommand` 處理），
 * 不受 player command 可用性影響、永遠常駐、不重複。
 */
@UnstableApi
class CustomMediaNotificationProvider(
    private val context: Context,
    notificationIdProvider: NotificationIdProvider,
    channelId: String,
    channelNameResourceId: Int,
) : DefaultMediaNotificationProvider(
    context,
    notificationIdProvider,
    channelId,
    channelNameResourceId,
) {

    /**
     * 通知列按鈕：固定為 [上一首, 播放/暫停, 下一首, 隨機, 循環]。
     *
     * - 上一首／下一首：`SLOT_BACK` / `SLOT_FORWARD`（compact view index 0 / 2）
     * - 播放/暫停：不設 slots，走 Media3 預設 `SLOT_CENTRAL`（compact view index 1）；
     *   `showPauseButton == true`（播放中）→ 暫停 icon，反之 → 播放 icon
     * - 隨機/循環：不設 slots（走 overflow／展開區），icon 依播放模式狀態切換
     *
     * `mediaButtonPreferences` 無視（固定序列，不需按偏好重排）。
     */
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val player = session.player

        val playPauseButton =
            if (showPauseButton) {
                CommandButton.Builder(CommandButton.ICON_PAUSE)
                    .setDisplayName(context.getString(androidx.media3.session.R.string.media3_controls_pause_description))
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .build()
            } else {
                CommandButton.Builder(CommandButton.ICON_PLAY)
                    .setDisplayName(context.getString(androidx.media3.session.R.string.media3_controls_play_description))
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .build()
            }

        val shuffleButton =
            CommandButton.Builder(
                if (player?.shuffleModeEnabled == true) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
            )
                .setDisplayName("隨機播放")
                .setSessionCommand(SessionCommand(MusicService.COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()

        val repeatButton =
            CommandButton.Builder(
                if (player?.repeatMode == Player.REPEAT_MODE_ONE) CommandButton.ICON_REPEAT_ONE else CommandButton.ICON_REPEAT_ALL
            )
                .setDisplayName("循環模式（清單／單曲）")
                .setSessionCommand(SessionCommand(MusicService.COMMAND_CYCLE_REPEAT, Bundle.EMPTY))
                .build()

        return ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName("上一首")
                .setSessionCommand(SessionCommand(MusicService.COMMAND_PREVIOUS, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            playPauseButton,
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("下一首")
                .setSessionCommand(SessionCommand(MusicService.COMMAND_NEXT, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            shuffleButton,
            repeatButton,
        )
    }
}
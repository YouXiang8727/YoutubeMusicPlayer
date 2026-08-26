package com.youxiang8727.mymediaplayer.feature.player.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放控制的 feature 內部介面。
 * ViewModel 只依賴此介面（不碰 MediaController/ExoPlayer/Service），便於測試替換；
 * 實作以 MediaController 連線至 MusicService 的 MediaSession。
 */
interface PlayerController {

    /** 播放狀態快照（含每 ~250ms 更新一次的進度）。 */
    val playback: StateFlow<PlaybackSnapshot>

    /** 播放指定影片：在播放清單中則以整份清單為佇列，否則單曲。 */
    fun play(videoId: String, title: String)

    fun togglePlayPause()

    fun seekToNext()

    fun seekToPrevious()

    /** 拖動進度條。 */
    fun seekTo(positionMs: Long)

    /** 隨機播放開關。 */
    fun toggleShuffle()

    /** 循環模式切換：清單循環 ↔ 單曲循環。 */
    fun cycleRepeatMode()

    fun stop()
}

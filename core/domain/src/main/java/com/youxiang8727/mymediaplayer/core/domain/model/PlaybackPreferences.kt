package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 播放偏好（隨機／循環）的持久化快照。
 *
 * 由 MusicService 於建立時讀取還原、於播放模式切換時寫入；
 * 缺省值（shuffle 關閉、清單循環）與 MusicService 既有的硬編碼行為一致。
 */
data class PlaybackPreferences(
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL
)
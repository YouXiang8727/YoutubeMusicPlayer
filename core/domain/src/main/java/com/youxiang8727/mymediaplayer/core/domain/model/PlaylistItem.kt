package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 播放清單項目（純領域模型）。
 * 持久化細節由 core:data 的 PlaylistItemEntity 承擔，兩者以 mapper 互轉。
 */
data class PlaylistItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

package com.youxiang8727.mymediaplayer.core.domain.model

data class PlaylistItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val duration: String? = null,  // 顯示用長度字串；null = 舊資料未記錄
    val addedAt: Long = System.currentTimeMillis(),
    val playlistId: Long
)

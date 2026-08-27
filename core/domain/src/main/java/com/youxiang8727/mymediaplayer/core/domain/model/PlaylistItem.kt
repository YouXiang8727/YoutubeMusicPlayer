package com.youxiang8727.mymediaplayer.core.domain.model

data class PlaylistItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val playlistId: Long
)

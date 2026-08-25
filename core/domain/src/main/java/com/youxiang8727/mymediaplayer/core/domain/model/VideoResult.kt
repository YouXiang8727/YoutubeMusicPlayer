package com.youxiang8727.mymediaplayer.core.domain.model

/** 搜尋結果（純領域模型，不含任何持久化或序列化標註）。 */
data class VideoResult(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = ""
)

fun VideoResult.toPlaylistItem() = PlaylistItem(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel
)

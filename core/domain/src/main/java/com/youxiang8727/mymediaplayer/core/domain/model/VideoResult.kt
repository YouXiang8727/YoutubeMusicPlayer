package com.youxiang8727.mymediaplayer.core.domain.model

/** 搜尋結果（純領域模型，不含任何持久化或序列化標註）。 */
data class VideoResult(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val duration: String? = null  // 顯示用長度字串（例 "3:45"、"1:02:03"）；null = 未知/直播
)

fun VideoResult.toPlaylistItem(playlistId: Long = 0L) = PlaylistItem(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    duration = duration,
    playlistId = playlistId
)

package com.youxiang8727.mymediaplayer.feature.player.playback

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem

/**
 * 依「點播的歌 + 目前的播放清單」建構播放佇列（純函數，便於單元測試）。
 *
 * 語意：
 * - 點播的歌在清單中 → 以整份清單為佇列，從該曲開始播放。
 * - 點播的歌不在清單中（或清單為空）→ 佇列僅含該曲（單曲播放）。
 */
object PlaybackQueueBuilder {

    data class QueueEntry(val videoId: String, val title: String)

    data class Queue(val entries: List<QueueEntry>, val startIndex: Int)

    fun build(
        playlist: List<PlaylistItem>,
        requestedVideoId: String,
        requestedTitle: String
    ): Queue {
        val indexInPlaylist = playlist.indexOfFirst { it.videoId == requestedVideoId }
        return if (playlist.isEmpty() || indexInPlaylist < 0) {
            Queue(
                entries = listOf(QueueEntry(requestedVideoId, requestedTitle.ifBlank { requestedVideoId })),
                startIndex = 0
            )
        } else {
            Queue(
                entries = playlist.map { QueueEntry(it.videoId, it.title.ifBlank { it.videoId }) },
                startIndex = indexInPlaylist
            )
        }
    }
}

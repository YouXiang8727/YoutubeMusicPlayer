package com.youxiang8727.mymediaplayer.feature.player.playback

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem

/**
 * 播放佇列建構（純函數，便於單元測試）。兩條路徑：
 *
 * **Room 路徑 [build]**（常駐播放清單）：
 * - 點播的歌在清單中 → 以整份清單為佇列，從該曲開始播放。
 * - 點播的歌不在清單中（或清單為空）→ 佇列僅含該曲（單曲播放）。
 *
 * **暫時性佇列路徑 [buildFromEntries]**（熱門榜單等非 Room 清單）：
 * - 直接以呼叫端提供的 [PlayQueueItem] 清單為佇列，從 [startIndex] 起播，**不查 Room**。
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

    /**
     * 以呼叫端提供的暫時性佇列直接建構播放佇列（不查 Room）。
     * @param entries 依顯示順序的佇列項目
     * @param startIndex 0-based 起播位置；clamp 至 [0, size-1]，空清單回 size=0 的 Queue
     */
    fun buildFromEntries(entries: List<PlayQueueItem>, startIndex: Int): Queue {
        if (entries.isEmpty()) return Queue(entries = emptyList(), startIndex = 0)
        val clamped = startIndex.coerceIn(0, entries.size - 1)
        return Queue(
            entries = entries.map { QueueEntry(it.videoId, it.title.ifBlank { it.videoId }) },
            startIndex = clamped
        )
    }
}

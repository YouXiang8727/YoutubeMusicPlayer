package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 播放佇列項目（純領域模型，不含任何持久化或序列化標註）。
 *
 * 用於**暫時性佇列**——例如熱門榜單等非 Room 播放清單的整份列表起播。
 * 與 [PlaylistItem]（Room 持久化）不同，[PlayQueueItem] 只描述「要播什麼」，
 * 由上層呼叫端直接提供，不查 Room、不落庫。
 *
 * 傳遞方式：由 feature:player 的 controller 層以 parallel arrays
 * （videoIds / titles，見 MusicService ACTION_PLAY_QUEUE extras）過 Intent，
 * 故本 model 不實作任何序列化標註（維持 domain 零序列化依賴）。
 */
data class PlayQueueItem(
    val videoId: String,
    val title: String
)

fun VideoResult.toPlayQueueItem() = PlayQueueItem(videoId = videoId, title = title)
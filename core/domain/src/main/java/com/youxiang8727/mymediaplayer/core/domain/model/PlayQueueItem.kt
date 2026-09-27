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

/**
 * 播放佇列項目 → 播放清單項目（持久化面）的欄位對應。
 *
 * **分工**：本 mapper 只做「一個欄位到另一個欄位」的映射；**順序規則不屬於此處**。
 * `playlist_items` 以 `addedAt DESC` 讀取（最新在前），與佇列的播放順序相反，
 * 故「如何排列 addedAt 才能還原佇列順序」是**領域規則**，由呼叫端
 * （[com.youxiang8727.mymediaplayer.core.domain.usecase.SaveQueueAsPlaylistUseCase]）
 * 計算後經 [addedAt] 參數傳入，避免映射邏輯與排序策略糾結。
 *
 * 縮圖固定為空字串：[PlayQueueItem] 不帶縮圖資訊（MediaItem 層已丟棄 artwork），
 * UI 對空 url 以色塊佔位顯示。
 */
fun PlayQueueItem.toPlaylistItem(
    playlistId: Long = 0L,
    addedAt: Long = System.currentTimeMillis()
) = PlaylistItem(
    videoId = videoId,
    title = title,
    thumbnailUrl = "",
    playlistId = playlistId,
    addedAt = addedAt
)
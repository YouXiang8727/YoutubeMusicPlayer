package com.youxiang8727.mymediaplayer.core.domain.model

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放控制的跨 feature 共用介面。
 * ViewModel 只依賴此介面（不碰 MediaController/ExoPlayer/Service），便於測試替換；
 * 實作以 MediaController 連線至 MusicService 的 MediaSession。
 */
interface PlayerController {

    /** 播放狀態快照（含每 ~250ms 更新一次的進度）。 */
    val playback: StateFlow<PlaybackSnapshot>

    /** 目前播放佇列（含暫時性佇列與 Room 播放清單），依播放順序排序。 */
    val queue: StateFlow<List<PlayQueueItem>>

    /** 播放指定影片：在播放清單中則以整份清單為佇列，否則單曲。 */
    fun play(videoId: String, title: String)

    /**
     * 播放一組**暫時性佇列**（非 Room 播放清單，例如熱門榜單）。
     *
     * 與 [play] 的差異：佇列內容由呼叫端提供，MusicService 不查 Room，
     * 直接以傳入的清單作為播放佇列，並從 [startIndex] 起播。
     * 播放模式（清單循環／單曲循環／隨機）與常駐佇列共用同一套機制。
     *
     * @param items 佇列項目（依顯示順序）
     * @param startIndex 起播位置（0-based）
     */
    fun playQueue(items: List<PlayQueueItem>, startIndex: Int)

    fun togglePlayPause()

    fun seekToNext()

    fun seekToPrevious()

    /** 拖動進度條。 */
    fun seekTo(positionMs: Long)

    /** 隨機播放開關。 */
    fun toggleShuffle()

    /** 循環模式切換：清單循環 ↔ 單曲循環。 */
    fun cycleRepeatMode()

    fun stop()

    /** 跳轉至佇列指定索引位置播放。 */
    fun seekToIndex(index: Int)

    /** 從佇列移除指定索引曲目。 */
    fun removeFromQueue(index: Int)

    /** 清空目前播放佇列。 */
    fun clearQueue()
}

package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 播放模式（需求：單曲循環 / 清單循環兩種，不含「不循環」）。
 */
enum class RepeatMode { ALL, ONE;

    /** 切換到下一個模式：ALL ↔ ONE。 */
    fun next(): RepeatMode = when (this) {
        ALL -> ONE
        ONE -> ALL
    }
}

/**
 * 迷你播放列 / 通知共用的播放狀態快照。
 * 由 [PlayerController] 從 MediaSession 的 ExoPlayer 投影而來。
 */
data class PlaybackSnapshot(
    /** 目前是否持有可顯示的曲目（決定 MiniPlayerBar 是否顯示）。 */
    val hasCurrent: Boolean = false,
    val videoId: String = "",
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /**
     * 目前播放項在播放佇列（timeline）中的索引；無目前播放項時為 [NO_CURRENT_INDEX]（-1）。
     *
     * 為何需要它：佇列是 append-only 的**有序且允許重複**的清單，同一首歌可合法排入多筆
     * （見 `TEAM.md` §7），故 `videoId` **不唯一**、不能拿來定位「是哪一筆正在播」。
     * UI 若以 `queue.indexOfFirst { it.videoId == snapshot.videoId }` 反推索引，
     * 目前播第二筆時會誤判為第一筆（高亮錯列）。本欄位直接提供播放端的真實索引。
     *
     * 語意與 Media3 `Player.currentMediaItemIndex` 一致（無效時 -1），
     * `hasCurrent` 為 false 時本欄位必定為 [NO_CURRENT_INDEX]（兩者不獨立，勿各自信其是）。
     *
     * **呼叫端責任**：本欄位與 `PlayerController.queue` 同源於同一條 timeline，
     * 但兩者由不同事件觸發更新，可能相差一幀。UI 以本欄位比對 index 為主，
     * 並建議以 `videoId` 作二次確認（不一致時寧可不高亮，也不要高亮錯列）。
     */
    val currentMediaItemIndex: Int = NO_CURRENT_INDEX,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    /**
     * 最近一次播放錯誤的可讀訊息（如「播放失敗：所有解析來源皆失敗[…]」）；
     * null 表示目前無錯誤。供容器層顯示 snackbar／未來 UI 呈現。
     * 錯誤由 ExoPlayer 保留至下次 prepare() 自動清除，此欄位隨之回到 null。
     */
    val errorMessage: String? = null
) {
    companion object {
        /** 無目前播放項時 [currentMediaItemIndex] 的值（對齊 Media3 `Player.INDEX_UNSET`）。 */
        const val NO_CURRENT_INDEX: Int = -1
    }
}

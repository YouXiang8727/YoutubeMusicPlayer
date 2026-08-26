package com.youxiang8727.mymediaplayer.feature.player.playback

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
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    /**
     * 最近一次播放錯誤的可讀訊息（如「播放失敗：所有解析來源皆失敗[…]」）；
     * null 表示目前無錯誤。供容器層顯示 snackbar／未來 UI 呈現。
     * 錯誤由 ExoPlayer 保留至下次 prepare() 自動清除，此欄位隨之回到 null。
     */
    val errorMessage: String? = null
)

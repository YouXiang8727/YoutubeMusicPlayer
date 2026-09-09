package com.youxiang8727.mymediaplayer.core.domain.model

data class PlaylistItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val duration: String? = null,  // 顯示用長度字串；null = 舊資料未記錄
    val addedAt: Long = System.currentTimeMillis(),
    val playlistId: Long,
    /**
     * 最近一次「播放清單內播放失敗」的時間戳；null = 無失敗紀錄。
     *
     * 用途：播放清單 UI 據以對該曲顯示播放錯誤標記（如紅框），讓使用者得知哪幾首
     * 在播放時遇到過問題（例：串流 URL 403 導致自動跳歌）。
     *
     * 清除時機：該曲**下一次成功播放**（進入 ExoPlayer READY 狀態）時由
     * MusicService 清除為 null。此為「上次播放結果」的持久化紀錄，僅針對 Room
     * 播放清單（暫時性佇列無 Room 表，不適用）。
     */
    val streamFailedAt: Long? = null
)

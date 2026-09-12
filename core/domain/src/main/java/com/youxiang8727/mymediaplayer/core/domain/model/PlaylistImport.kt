package com.youxiang8727.mymediaplayer.core.domain.model

/** 匯入時遇同名歌單的處理決定。 */
enum class ImportConflictDecision { Replace, KeepBoth, Cancel }

/** 單一衝突的資訊；conflictIndex 為 1-based、totalConflicts 為本次匯入預掃到的衝突總數。 */
data class ImportConflictInfo(
    val name: String,
    val conflictIndex: Int,
    val totalConflicts: Int
)

/** 匯入結果統計。cancelled = 使用者選擇取消（後續歌單未匯入，先前已匯入的保留）。 */
data class PlaylistImportResult(
    val created: Int,
    val replaced: Int,
    val keptBoth: Int,
    val cancelled: Boolean
)
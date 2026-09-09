package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 搜尋紀錄 Room Entity：query 為 PK，重複搜尋以 REPLACE 覆寫（更新 [searchedAt]）達成去重置頂。
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
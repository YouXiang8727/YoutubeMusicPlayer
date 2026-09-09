package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /**
     * 觀察搜尋紀錄（最新在前）。SQL 層 `LIMIT 10` 即上限——repository 的 [add]
     * 另以 [trimToLimit] 實際刪除超出筆數；此處 10 需與
     * `SearchHistoryRepositoryImpl.MAX_HISTORY_SIZE` 保持一致。
     */
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 10")
    fun observeAll(): Flow<List<SearchHistoryEntity>>

    /** 寫入或覆寫一筆紀錄（同 query 以 REPLACE 更新 searchedAt，達成去重置頂）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    /** 刪除超出最新 [limit] 筆以外的舊紀錄。 */
    @Query(
        "DELETE FROM search_history WHERE query NOT IN " +
            "(SELECT query FROM search_history ORDER BY searchedAt DESC LIMIT :limit)"
    )
    suspend fun trimToLimit(limit: Int)

    /** 清除全部搜尋紀錄。 */
    @Query("DELETE FROM search_history")
    suspend fun clear()
}
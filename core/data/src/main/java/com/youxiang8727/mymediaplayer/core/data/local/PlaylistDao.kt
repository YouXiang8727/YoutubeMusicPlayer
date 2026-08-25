package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist WHERE videoId = :videoId LIMIT 1")
    suspend fun findById(videoId: String): PlaylistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("DELETE FROM playlist")
    suspend fun clearAll()
}

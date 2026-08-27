package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // ── 播放清單 ──
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaylist(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // ── 播放清單項目 ──
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(entity: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteItem(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    // ── 隨機取一首 ──
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomItem(playlistId: Long): PlaylistItemEntity?

    // ── 級聯刪除 ──
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistWithItemsCascade(playlistId: Long)
}

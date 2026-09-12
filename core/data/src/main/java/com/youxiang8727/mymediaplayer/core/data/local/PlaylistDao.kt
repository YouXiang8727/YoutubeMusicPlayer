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

    /**
     * 依名稱查詢歌單 id（name 具唯一索引後最多一筆；供重新命名前置檢查）。
     * 回傳 null = 無任何歌單使用該名稱。
     */
    @Query("SELECT id FROM playlists WHERE name = :name LIMIT 1")
    suspend fun findPlaylistIdByName(name: String): Long?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // ── 播放清單項目 ──
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    /**
     * 觀察「最近加入」的歌曲（跨全部播放清單，addedAt 最新在前，最多 limit 筆）。
     * 供「為你推薦」種子與已知 videoId 快照使用；無需新表、不 bump DB version。
     * 去重（videoId）在 Repository 層以 distinctBy 處理（SQL 全表含跨清單重複列）。
     */
    @Query("SELECT * FROM playlist_items ORDER BY addedAt DESC LIMIT :limit")
    fun observeRecentItems(limit: Int): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(entity: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteItem(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    // ── 隨機取一首 ──
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomItem(playlistId: Long): PlaylistItemEntity?

    // ── 匯出用 ──
    @Query("SELECT * FROM playlist_items")
    suspend fun getAllItems(): List<PlaylistItemEntity>

    // ── 播放失敗標記 ──
    @Query("UPDATE playlist_items SET streamFailedAt = :failedAt WHERE videoId = :videoId")
    suspend fun markStreamFailed(videoId: String, failedAt: Long)

    @Query("UPDATE playlist_items SET streamFailedAt = NULL WHERE videoId = :videoId")
    suspend fun clearStreamFailed(videoId: String)

    // ── 級聯刪除 ──
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistWithItemsCascade(playlistId: Long)
}

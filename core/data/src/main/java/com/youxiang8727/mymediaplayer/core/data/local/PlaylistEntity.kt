package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist

/**
 * 歌單表。name 具唯一索引（DB v6 起）——資料層最終防線：
 * insert 衝突（IGNORE）時 Room 回傳 -1，由 Repository 轉為語意化例外。
 */
@Entity(tableName = "playlists", indices = [Index(value = ["name"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun PlaylistEntity.toDomain() = Playlist(id, name, createdAt, updatedAt)
fun Playlist.toEntity() = PlaylistEntity(id, name, createdAt, updatedAt)

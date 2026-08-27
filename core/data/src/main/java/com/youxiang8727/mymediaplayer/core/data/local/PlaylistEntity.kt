package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun PlaylistEntity.toDomain() = Playlist(id, name, createdAt, updatedAt)
fun Playlist.toEntity() = PlaylistEntity(id, name, createdAt, updatedAt)

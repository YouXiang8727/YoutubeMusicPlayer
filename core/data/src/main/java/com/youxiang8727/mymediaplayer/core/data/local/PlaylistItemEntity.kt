package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem

@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId")]
)
data class PlaylistItemEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "playlistId") val playlistId: Long
)

fun PlaylistItemEntity.toDomain() = PlaylistItem(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    addedAt = addedAt,
    playlistId = playlistId
)

fun PlaylistItem.toEntity() = PlaylistItemEntity(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    addedAt = addedAt,
    playlistId = playlistId
)

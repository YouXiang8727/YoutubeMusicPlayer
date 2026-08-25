package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem

/**
 * 播放清單 Room Entity。
 * 僅存在於 core:data 內部；對外一律以 domain 的 PlaylistItem 溝通。
 */
@Entity(tableName = "playlist")
data class PlaylistItemEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

fun PlaylistItemEntity.toDomain() = PlaylistItem(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    addedAt = addedAt
)

fun PlaylistItem.toEntity() = PlaylistItemEntity(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    addedAt = addedAt
)

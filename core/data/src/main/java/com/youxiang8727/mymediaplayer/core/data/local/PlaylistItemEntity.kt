package com.youxiang8727.mymediaplayer.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem

/**
 * 播放清單項目 Room Entity。
 *
 * 主鍵為複合鍵 (playlistId, videoId)（DB v7 起）：同一影片可同時存在於多個歌單，
 * `insertItem(REPLACE)` 以 (playlistId, videoId) 為衝突單位。
 * 修前為單一 videoId 全域 PK——KeepBoth 匯入同曲目到新歌單時 REPLACE 覆蓋舊歌單
 * 同 videoId 的資料列（「兩者皆保留」變成移花接木）。
 */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index("playlistId")]
)
data class PlaylistItemEntity(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val duration: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "playlistId") val playlistId: Long,
    val streamFailedAt: Long? = null
)

fun PlaylistItemEntity.toDomain() = PlaylistItem(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    duration = duration,
    addedAt = addedAt,
    playlistId = playlistId,
    streamFailedAt = streamFailedAt
)

fun PlaylistItem.toEntity() = PlaylistItemEntity(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    channel = channel,
    duration = duration,
    addedAt = addedAt,
    playlistId = playlistId,
    streamFailedAt = streamFailedAt
)

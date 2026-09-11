package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * JSON 匯出/匯入格式的頂層結構。
 * 純 Kotlin，無 Android 或序列化框架依賴。
 */
data class PlaylistExportFormat(
    val version: Int = 1,
    val exportedAt: String, // ISO-8601
    val playlist: ExportedPlaylist
)

data class ExportedPlaylist(
    val name: String,
    val items: List<ExportedPlaylistItem>
)

data class ExportedPlaylistItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channel: String = "",
    val duration: String? = null
)

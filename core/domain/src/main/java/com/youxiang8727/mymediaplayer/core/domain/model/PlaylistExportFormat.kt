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

/**
 * v2：多歌單（匯出全部）格式。與 v1 的差異在於以 `playlist`（單一）換成 `playlists`（陣列）。
 * [ExportedPlaylist] / [ExportedPlaylistItem] 與 v1 共用。
 */
data class PlaylistExportBundleFormat(
    val version: Int = 2,
    val exportedAt: String, // ISO-8601
    val playlists: List<ExportedPlaylist>
)

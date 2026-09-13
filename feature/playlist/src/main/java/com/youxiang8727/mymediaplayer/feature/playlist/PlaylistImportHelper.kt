package com.youxiang8727.mymediaplayer.feature.playlist

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌單備份檔的 MediaStore 摘要資訊。
 */
data class PlaylistBackupFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedAt: Long
)

/**
 * 掃描 `Download/MyMediaPlayer/` 下的 `.json` 備份檔，依修改時間降序回傳。
 * 查詢失敗或查無結果回空清單（不 throw）。
 */
fun Context.queryPlaylistBackups(): List<PlaylistBackupFile> {
    val relativePath = Environment.DIRECTORY_DOWNLOADS + "/MyMediaPlayer/"
    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
        "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf(relativePath, "%.json")

    return runCatching {
        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            ),
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000L // seconds → millis
                    val uri = Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    add(PlaylistBackupFile(uri, name, size, date))
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())
}

/**
 * 讀取指定 URI 的備份 JSON 內容，失敗回 null。
 */
fun Context.readPlaylistBackup(uri: Uri): String? =
    runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        }
    }.getOrNull()

// ── 純函式（方便 JVM 單元測試） ──────────────────────────────

/**
 * 將 Unix 毫秒時間戳格式化為 `yyyy-MM-dd HH:mm`。
 * @param locale 用於 SimpleDateFormat，Preview/測試時可注入固定 locale。
 * @param timeZone 顯示時區，預設系統時區；測試時可注入固定時區確保可重現。
 */
fun formatBackupModifiedDate(
    millis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", locale).apply {
        this.timeZone = timeZone
    }.format(Date(millis))

/**
 * 將 byte 數格式化為可讀大小字串（B / KB / MB）。
 */
fun formatBackupFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> {
        val mb = bytes.toDouble() / (1024L * 1024L)
        String.format(Locale.US, "%.1f MB", mb)
    }
}

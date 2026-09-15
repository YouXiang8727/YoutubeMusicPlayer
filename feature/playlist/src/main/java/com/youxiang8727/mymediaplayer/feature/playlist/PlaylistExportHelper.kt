package com.youxiang8727.mymediaplayer.feature.playlist

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * 以 MediaStore 直寫歌單備份 JSON 至固定資料夾 `Download/MyMediaPlayer/`。
 *
 * - [MediaStore.MediaColumns.RELATIVE_PATH] 指向 `Download/MyMediaPlayer/`
 *   （minSdk 33 > RELATIVE_PATH 所需 API 29，全裝置支援，不需 fallback）。
 * - 先以 `IS_PENDING = 1` 插入（檔案未寫完整前不讓其他 App／媒體掃描看到），
 *   寫入 [json] bytes 後更新為 0；任一步失敗則刪除該 uri 並回傳 failure，不留半成品。
 * - 檔名與既有檔案衝突時 MediaProvider 會自動加「 (N)」後綴（例 `xxx (1).json`），
 *   不需手動處理。
 *
 * 呼叫端請包 `withContext(Dispatchers.IO)`（ContentResolver 寫入為 blocking I/O）。
 */
suspend fun Context.writePlaylistBackup(json: String, fileName: String): Result<Uri> =
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/MyMediaPlayer/"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert 失敗")

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream 失敗")

            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            uri
        } catch (e: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw e
        }
    }

/**
 * 查回 MediaStore 實際寫入的檔名（檔名衝突被自動加「 (N)」後綴時回傳真正落盤的名字）。
 * 查詢失敗或欄位不存在時回傳 null，由呼叫端以建議檔名兜底。
 */
fun Context.queryStoredDisplayName(uri: Uri): String? =
    runCatching {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
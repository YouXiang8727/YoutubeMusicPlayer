package com.youxiang8727.mymediaplayer.feature.playlist

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
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

// ── 所有檔案存取權限（MANAGE_EXTERNAL_STORAGE） ───────────────

/**
 * 是否已取得 `MANAGE_EXTERNAL_STORAGE`（所有檔案存取權限）。
 *
 * 為什麼匯入**需要**這個權限：Android 11+ 的 `MediaStore.Downloads` 查詢**只列出 App 自己擁有
 * （owner）的檔案**。解除安裝時 MediaProvider 會撤掉舊安裝在 shared storage 建立檔案的 ownership，
 * 重裝後那些 row 對新安裝等同「別人的檔」→ 查不到，但檔案其實還在。
 * 開啟所有檔案存取權限後才列得出舊安裝的備份。
 *
 * 為什麼匯出**不需要**：寫入走 `MediaStore.insert` + `IS_PENDING`，寫自己的檔案不需此權限。
 *
 * 此權限沒有普通 runtime permission 的三態（系統不記錄「永久拒絕」），只是設定頁的一個
 * toggle，因此**不存在**「第一次提示、第二次才開設定頁」的分流邏輯，統一走：
 * 未授權 → 顯示阻擋訊息 → 跳設定頁 → 返回後重新檢查。
 *
 * `isExternalStorageManager()` 需 API 30+，本專案 minSdk 33，不需版本 guard。
 */
fun Context.hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

/**
 * 跳至「所有檔案存取權限」設定頁。
 *
 * 優先跳本 App 的權限細項頁；部分 ROM／裁切版沒有該 Activity（`ActivityNotFoundException`），
 * fallback 到全域清單頁。兩個頁都開不起來時回 `false`，由呼叫端如實回報失敗——**不假成功**。
 */
fun Context.openAllFilesAccessSettings(): Boolean {
    val appSpecific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", packageName, null)
    )
    return try {
        startActivity(appSpecific)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

// ── 備份檔列舉 ──────────────────────────────────────────────

/**
 * 掃描 `Download/MyMediaPlayer/` 下的 `.json` 備份檔，依修改時間降序回傳。
 *
 * 回 `Result` 而非「失敗就回空清單」：**查詢失敗與「確實沒有備份」必須可分辨**，
 * 否則權限在查詢瞬間被撤銷（或任何 `SecurityException`）都會被呈現成
 * 「Download/MyMediaPlayer/ 沒有備份檔」——那是誤導使用者去重新匯出。
 */
fun Context.queryPlaylistBackups(): Result<List<PlaylistBackupFile>> {
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
    }
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

/**
 * 刪除指定 URI 的備份檔（MediaStore）。
 * `contentResolver.delete` 回傳 > 0 視為成功；exception 或回傳 0 皆回 false，不 throw。
 */
fun Context.deletePlaylistBackup(uri: Uri): Boolean =
    runCatching { contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

// ── 純函式（方便 JVM 單元測試） ──────────────────────────────

/**
 * 匯入面板的顯示狀態（封閉型別，四種互斥情境）。
 *
 * 用 sealed interface 而非多個 nullable 欄位，讓「沒有備份」與「查不到」在**型別層**就分開——
 * 兩者都對應「畫面上看不到檔案」，但意義相反：前者要引導使用者去匯出，後者要如實說明失敗。
 */
sealed interface ImportBackupPanelState {

    /** 未取得「所有檔案存取」權限 → 阻擋，並引導至權限設定頁。 */
    data object PermissionRequired : ImportBackupPanelState

    /** 已授權、查詢進行中（或尚未查詢）。 */
    data object Scanning : ImportBackupPanelState

    /** 已授權但查詢失敗 → 如實回報失敗，**不可偽裝成「沒有備份」**。 */
    data class QueryFailed(val reason: String?) : ImportBackupPanelState

    /** 已授權且查詢成功。[PlaylistBackupFile] 為空 = 確實沒有備份檔。 */
    data class Ready(val backups: List<PlaylistBackupFile>) : ImportBackupPanelState
}

/**
 * 「權限狀態 + 查詢結果 → 面板顯示狀態」的純對應（可 JVM 單元測試）。
 *
 * @param hasAllFilesAccess 目前是否持有 `MANAGE_EXTERNAL_STORAGE`。
 * @param queryResult 最近一次查詢結果；`null` = 尚未查詢／查詢中。
 *        未授權時直接忽略（此時查詢本來就查不到東西，跑了只會製造假的「沒有備份」）。
 */
fun importBackupPanelState(
    hasAllFilesAccess: Boolean,
    queryResult: Result<List<PlaylistBackupFile>>?
): ImportBackupPanelState = when {
    !hasAllFilesAccess -> ImportBackupPanelState.PermissionRequired
    queryResult == null -> ImportBackupPanelState.Scanning
    else -> queryResult.fold(
        onSuccess = { ImportBackupPanelState.Ready(it) },
        onFailure = { ImportBackupPanelState.QueryFailed(it.message) }
    )
}

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

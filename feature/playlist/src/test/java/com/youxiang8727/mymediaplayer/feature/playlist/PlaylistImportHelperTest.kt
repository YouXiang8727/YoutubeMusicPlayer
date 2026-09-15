package com.youxiang8727.mymediaplayer.feature.playlist

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 備份檔格式化純函式測試（JVM）。
 * MediaStore query/read 屬 ContentResolver 真機行為，不在此測。
 */
class PlaylistImportHelperTest {

    private val taipeiTz = TimeZone.getTimeZone("Asia/Taipei")

    @Test
    fun `formatBackupModifiedDate 格式化為 yyyy-MM-dd HH_mm`() {
        // 2023-09-01 12:34:56 (Asia/Taipei, UTC+8) = 1693542896000 ms
        val formatted = formatBackupModifiedDate(1693542896000L, Locale.TAIWAN, taipeiTz)
        assertEquals("2023-09-01 12:34", formatted)
    }

    @Test
    fun `formatBackupModifiedDate 時間戳為 0 時格式化為 1970-01-01 08_00`() {
        // epoch 0 在 UTC+8 為 1970-01-01 08:00
        assertEquals("1970-01-01 08:00", formatBackupModifiedDate(0L, Locale.TAIWAN, taipeiTz))
    }

    @Test
    fun `formatBackupFileSize 小於 1KB 顯示 B`() {
        assertEquals("512 B", formatBackupFileSize(512L))
    }

    @Test
    fun `formatBackupFileSize 1KB 以上以 KB 顯示`() {
        assertEquals("1 KB", formatBackupFileSize(1024L))
        assertEquals("85 KB", formatBackupFileSize(85 * 1024L))
    }

    @Test
    fun `formatBackupFileSize 1MB 以上以 MB 一位小數顯示`() {
        assertEquals("1.0 MB", formatBackupFileSize(1_048_576L))
        assertEquals("2.5 MB", formatBackupFileSize(2_621_440L))
    }

    @Test
    fun `formatBackupFileSize 0 byte 顯示 0 B`() {
        assertEquals("0 B", formatBackupFileSize(0L))
    }
}
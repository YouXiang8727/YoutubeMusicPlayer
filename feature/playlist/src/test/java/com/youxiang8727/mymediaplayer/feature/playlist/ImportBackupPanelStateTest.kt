package com.youxiang8727.mymediaplayer.feature.playlist

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「權限狀態 + 查詢結果 → 匯入面板顯示狀態」純函式測試（JVM）。
 *
 * 針對 2026-09 的實機事故：解除安裝重裝後，`MediaStore.Downloads` 查不到舊備份
 * （MediaProvider 撤掉了舊安裝的 ownership），面板卻顯示「沒有備份檔」——誤導使用者去重新匯出。
 *
 * 註：`PlaylistBackupFile` 含 `android.net.Uri`，純 JVM 單元測試無法建構實例，
 * 因此「已授權且查詢到檔案」一列不在此測（該路徑由 Preview 做視覺回歸）。
 */
class ImportBackupPanelStateTest {

    @Test
    fun `未授權時即使查詢成功也顯示 PermissionRequired`() {
        val state = importBackupPanelState(
            hasAllFilesAccess = false,
            queryResult = Result.success(emptyList<PlaylistBackupFile>())
        )
        assertEquals(ImportBackupPanelState.PermissionRequired, state)
    }

    @Test
    fun `未授權時即使查詢失敗也顯示 PermissionRequired`() {
        val state = importBackupPanelState(
            hasAllFilesAccess = false,
            queryResult = Result.failure(SecurityException("權限在查詢瞬間被撤銷"))
        )
        assertEquals(ImportBackupPanelState.PermissionRequired, state)
    }

    @Test
    fun `未授權時忽略查詢結果而非回退成沒有備份`() {
        // 未授權 → 查詢本來就會回空；把它呈現成「沒有備份檔」正是本次要修掉的誤導
        val state = importBackupPanelState(false, Result.success(emptyList()))
        assertTrue(state !is ImportBackupPanelState.Ready)
        assertTrue(state !is ImportBackupPanelState.QueryFailed)
    }

    @Test
    fun `已授權但尚未查詢時顯示 Scanning 而非沒有備份`() {
        val state = importBackupPanelState(hasAllFilesAccess = true, queryResult = null)
        assertEquals(ImportBackupPanelState.Scanning, state)
    }

    @Test
    fun `已授權且查詢成功為空清單時顯示 Ready 空清單`() {
        // 唯一可以說「沒有備份檔」的情境：查詢成功 + 確實沒有
        val state = importBackupPanelState(
            hasAllFilesAccess = true,
            queryResult = Result.success(emptyList())
        )
        assertTrue(state is ImportBackupPanelState.Ready)
        assertEquals(emptyList<PlaylistBackupFile>(), (state as ImportBackupPanelState.Ready).backups)
    }

    @Test
    fun `已授權但查詢失敗時顯示 QueryFailed 並帶上原因`() {
        val state = importBackupPanelState(
            hasAllFilesAccess = true,
            queryResult = Result.failure(SecurityException("Permission Denial: query MediaStore"))
        )
        assertTrue(state is ImportBackupPanelState.QueryFailed)
        assertEquals(
            "Permission Denial: query MediaStore",
            (state as ImportBackupPanelState.QueryFailed).reason
        )
    }

    @Test
    fun `已授權但查詢失敗時絕不可偽裝成 Ready 空清單`() {
        val state = importBackupPanelState(
            hasAllFilesAccess = true,
            queryResult = Result.failure(IOException("provider died"))
        )
        assertTrue(state !is ImportBackupPanelState.Ready)
    }

    @Test
    fun `查詢失敗且例外無訊息時仍為 QueryFailed 且 reason 為 null`() {
        val state = importBackupPanelState(
            hasAllFilesAccess = true,
            queryResult = Result.failure(SecurityException())
        )
        assertTrue(state is ImportBackupPanelState.QueryFailed)
        assertNull((state as ImportBackupPanelState.QueryFailed).reason)
    }

    @Test
    fun `六種輸入涵蓋到四種狀態且沒有第五種`() {
        // UI 以 when 分派四種狀態；若未來新增分支，這裡會立刻抓到未預期的映射結果
        val rendered = listOf(
            importBackupPanelState(false, null),
            importBackupPanelState(false, Result.success(emptyList())),
            importBackupPanelState(false, Result.failure(SecurityException())),
            importBackupPanelState(true, null),
            importBackupPanelState(true, Result.success(emptyList())),
            importBackupPanelState(true, Result.failure(SecurityException()))
        ).map { it::class }.toSet()

        assertEquals(
            setOf(
                ImportBackupPanelState.PermissionRequired::class,
                ImportBackupPanelState.Scanning::class,
                ImportBackupPanelState.Ready::class,
                ImportBackupPanelState.QueryFailed::class
            ),
            rendered
        )
    }
}

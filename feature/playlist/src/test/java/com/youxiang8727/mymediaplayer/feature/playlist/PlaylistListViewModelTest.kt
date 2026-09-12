package com.youxiang8727.mymediaplayer.feature.playlist

import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.DeletePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ExportAllPlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ExportPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ImportPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException
import com.youxiang8727.mymediaplayer.core.domain.usecase.RenamePlaylistUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * ViewModel 只依賴 interface，此 fake 供應 export/import 結果並記錄 import 的 json。
     * [conflicts] 模擬匯入時依序遇到的衝突——對每個衝突呼叫 [onConflict]（流程暫停等待決策），
     * 收到 Cancel 即中止後續（比照真實 repository 語意）。
     */
    private class FakePlaylistRepository(
        var exportResult: String? = null,
        var exportAllResult: String? = null,
        var importResult: PlaylistImportResult? = null,
        val importCalls: MutableList<String> = mutableListOf(),
        val conflicts: List<ImportConflictInfo> = emptyList(),
        val conflictCalls: MutableList<ImportConflictInfo> = mutableListOf(),
        val conflictDecisions: MutableList<ImportConflictDecision> = mutableListOf(),
        var createError: Exception? = null,
        var renameError: Exception? = null,
        val createCalls: MutableList<String> = mutableListOf(),
        val renameCalls: MutableList<Pair<Long, String>> = mutableListOf()
    ) : PlaylistRepository {
        override fun observeAllPlaylists(): Flow<List<Playlist>> = flowOf(emptyList())
        override suspend fun createPlaylist(name: String): Long {
            createCalls += name
            createError?.let { throw it }
            return 1L
        }
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {
            renameCalls += (playlistId to newName)
            renameError?.let { throw it }
        }
        override suspend fun deletePlaylist(playlistId: Long) {}
        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun addItem(playlistId: Long, item: PlaylistItem) {}
        override suspend fun removeItem(playlistId: Long, videoId: String) {}
        override suspend fun clearPlaylist(playlistId: Long) {}
        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null
        override suspend fun markStreamFailed(videoId: String, failedAt: Long) {}
        override suspend fun clearStreamFailed(videoId: String) {}
        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> = flowOf(emptyList())
        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = exportResult
        override suspend fun exportAllPlaylistsAsJson(): String? = exportAllResult
        override suspend fun importPlaylistFromJson(
            json: String,
            onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
        ): PlaylistImportResult? {
            importCalls += json
            for (info in conflicts) {
                conflictCalls += info
                val decision = onConflict(info)
                conflictDecisions += decision
                if (decision == ImportConflictDecision.Cancel) break
            }
            return importResult
        }
    }

    private class Harness(
        val vm: PlaylistListViewModel,
        val messages: MutableList<String>,
        val exports: MutableList<String>
    )

    private fun buildHarness(repo: FakePlaylistRepository): Harness {
        val vm = PlaylistListViewModel(
            observePlaylists = ObservePlaylistsUseCase(repo),
            createPlaylist = CreatePlaylistUseCase(repo),
            deletePlaylist = DeletePlaylistUseCase(repo),
            renamePlaylist = RenamePlaylistUseCase(repo),
            exportPlaylist = ExportPlaylistUseCase(repo),
            exportAllPlaylists = ExportAllPlaylistsUseCase(repo),
            importPlaylist = ImportPlaylistUseCase(repo)
        )
        val messages = mutableListOf<String>()
        val exports = mutableListOf<String>()
        // 先於任何 VM 動作前訂閱 messages / exportResult，確保 SharedFlow（replay=0）不會漏接。
        CoroutineScope(dispatcher).launch { vm.messages.collect { messages.add(it) } }
        CoroutineScope(dispatcher).launch { vm.exportResult.collect { exports.add(it) } }
        return Harness(vm, messages, exports)
    }

    @Test
    fun `Export 成功時 emit JSON 至 exportResult`() {
        val json = """{"version":1,"playlist":{"name":"我的最愛"}}"""
        val h = buildHarness(FakePlaylistRepository(exportResult = json))

        h.vm.onIntent(PlaylistListIntent.Export(1L))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(json), h.exports)
        assertTrue("不應有 snackbar 訊息", h.messages.isEmpty())
    }

    @Test
    fun `Export 找不到歌單時發出 snackbar 訊息且不 emit exportResult`() {
        val h = buildHarness(FakePlaylistRepository(exportResult = null))

        h.vm.onIntent(PlaylistListIntent.Export(999L))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("不應 emit exportResult", h.exports.isEmpty())
        assertEquals("找不到該歌單", h.messages.last())
    }

    @Test
    fun `ExportAll 有歌單時 emit JSON 至 exportResult`() {
        val json = """{"version":2,"playlists":[]}"""
        val h = buildHarness(FakePlaylistRepository(exportAllResult = json))

        h.vm.onIntent(PlaylistListIntent.ExportAll)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(json), h.exports)
        assertTrue("不應有 snackbar 訊息", h.messages.isEmpty())
    }

    @Test
    fun `ExportAll 無歌單時發出 snackbar 訊息且不 emit exportResult`() {
        val h = buildHarness(FakePlaylistRepository(exportAllResult = null))

        h.vm.onIntent(PlaylistListIntent.ExportAll)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("不應 emit exportResult", h.exports.isEmpty())
        assertEquals("尚無任何歌單可匯出", h.messages.last())
    }

    @Test
    fun `Import 無衝突時發出「已匯入」摘要且 json 正確傳遞、importConflict 保持 null`() {
        val repo = FakePlaylistRepository(
            importResult = PlaylistImportResult(created = 2, replaced = 0, keptBoth = 0, cancelled = false)
        )
        val h = buildHarness(repo)
        val json = """{"version":1,"playlist":{"name":"匯入歌單","items":[]}}"""

        h.vm.onIntent(PlaylistListIntent.Import(json))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(json), repo.importCalls)
        assertTrue("無衝突不應呼叫 onConflict", repo.conflictCalls.isEmpty())
        assertEquals("已匯入：新增 2", h.messages.last())
        assertEquals("import 結束後 importConflict 應回到 null", null, h.vm.importConflict.value)
        assertTrue("Import 不應 emit exportResult", h.exports.isEmpty())
    }

    @Test
    fun `Import 單一衝突選 Replace 時訊息含取代 1 且不彈窗後 importConflict 回到 null`() {
        val info = ImportConflictInfo(name = "我的最愛", conflictIndex = 1, totalConflicts = 1)
        val repo = FakePlaylistRepository(
            importResult = PlaylistImportResult(created = 0, replaced = 1, keptBoth = 0, cancelled = false),
            conflicts = listOf(info)
        )
        val h = buildHarness(repo)

        h.vm.onIntent(PlaylistListIntent.Import("""{"version":2,"playlists":[]}"""))
        dispatcher.scheduler.advanceUntilIdle()

        // 匯入流程暫停在衝突詢問 → 彈窗資訊正確
        assertEquals(info, h.vm.importConflict.value)
        assertEquals(listOf(info), repo.conflictCalls)

        h.vm.onImportConflictDecision(ImportConflictDecision.Replace, applyToAll = false)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(ImportConflictDecision.Replace), repo.conflictDecisions)
        assertEquals("已匯入：取代 1", h.messages.last())
        assertEquals("import 結束後 importConflict 應回到 null", null, h.vm.importConflict.value)
    }

    @Test
    fun `Import 單一衝突選 KeepBoth 時訊息含保留兩者 1`() {
        val info = ImportConflictInfo(name = "我的最愛", conflictIndex = 1, totalConflicts = 1)
        val repo = FakePlaylistRepository(
            importResult = PlaylistImportResult(created = 0, replaced = 0, keptBoth = 1, cancelled = false),
            conflicts = listOf(info)
        )
        val h = buildHarness(repo)

        h.vm.onIntent(PlaylistListIntent.Import("""{"version":2,"playlists":[]}"""))
        dispatcher.scheduler.advanceUntilIdle()
        h.vm.onImportConflictDecision(ImportConflictDecision.KeepBoth, applyToAll = false)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(ImportConflictDecision.KeepBoth), repo.conflictDecisions)
        assertEquals("已匯入：保留兩者 1", h.messages.last())
        assertEquals("import 結束後 importConflict 應回到 null", null, h.vm.importConflict.value)
    }

    @Test
    fun `Import 單一衝突選 Cancel 時訊息已取消匯入且中止後續衝突詢問`() {
        val first = ImportConflictInfo(name = "我的最愛", conflictIndex = 1, totalConflicts = 2)
        val second = ImportConflictInfo(name = "工作", conflictIndex = 2, totalConflicts = 2)
        val repo = FakePlaylistRepository(
            importResult = PlaylistImportResult(created = 0, replaced = 0, keptBoth = 0, cancelled = true),
            conflicts = listOf(first, second)
        )
        val h = buildHarness(repo)

        h.vm.onIntent(PlaylistListIntent.Import("""{"version":2,"playlists":[]}"""))
        dispatcher.scheduler.advanceUntilIdle()
        h.vm.onImportConflictDecision(ImportConflictDecision.Cancel, applyToAll = false)
        dispatcher.scheduler.advanceUntilIdle()

        // 只詢問第一個衝突，第二個不再被問（流程中止）
        assertEquals(listOf(first), repo.conflictCalls)
        assertEquals(listOf(ImportConflictDecision.Cancel), repo.conflictDecisions)
        assertEquals("已取消匯入", h.messages.last())
        assertEquals("import 結束後 importConflict 應回到 null", null, h.vm.importConflict.value)
    }

    @Test
    fun `Import 多衝突勾選全部套用時只詢問一次、後續自動沿用同一決策`() {
        val conflicts = (1..3).map { ImportConflictInfo(name = "我的最愛", conflictIndex = it, totalConflicts = 3) }
        val repo = FakePlaylistRepository(
            importResult = PlaylistImportResult(created = 0, replaced = 3, keptBoth = 0, cancelled = false),
            conflicts = conflicts
        )
        val h = buildHarness(repo)

        h.vm.onIntent(PlaylistListIntent.Import("""{"version":2,"playlists":[]}"""))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(conflicts[0], h.vm.importConflict.value)

        // 只做一次決策 + 全部套用，之後完全不需再詢問
        h.vm.onImportConflictDecision(ImportConflictDecision.Replace, applyToAll = true)
        dispatcher.scheduler.advanceUntilIdle()

        // 三個衝突都被處理，且決策全部一致（第 2、3 個走快取無需 UI）
        assertEquals(conflicts, repo.conflictCalls)
        assertEquals(
            listOf(
                ImportConflictDecision.Replace,
                ImportConflictDecision.Replace,
                ImportConflictDecision.Replace
            ),
            repo.conflictDecisions
        )
        assertEquals("已匯入：取代 3", h.messages.last())
        assertEquals("import 結束後 importConflict 應回到 null", null, h.vm.importConflict.value)
    }

    @Test
    fun `Import 多衝突未勾選全部套用時逐個詢問且每次可給不同決策`() {
        val conflicts = (1..3).map { ImportConflictInfo(name = "我的最愛", conflictIndex = it, totalConflicts = 3) }
        val repo = FakePlaylistRepository(
            importResult = PlaylistImportResult(created = 0, replaced = 1, keptBoth = 1, cancelled = true),
            conflicts = conflicts
        )
        val h = buildHarness(repo)

        h.vm.onIntent(PlaylistListIntent.Import("""{"version":2,"playlists":[]}"""))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(conflicts[0], h.vm.importConflict.value)

        h.vm.onImportConflictDecision(ImportConflictDecision.KeepBoth, applyToAll = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("下一個衝突應再次彈窗", conflicts[1], h.vm.importConflict.value)

        h.vm.onImportConflictDecision(ImportConflictDecision.Replace, applyToAll = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("下一個衝突應再次彈窗", conflicts[2], h.vm.importConflict.value)

        h.vm.onImportConflictDecision(ImportConflictDecision.Cancel, applyToAll = false)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                ImportConflictDecision.KeepBoth,
                ImportConflictDecision.Replace,
                ImportConflictDecision.Cancel
            ),
            repo.conflictDecisions
        )
        assertEquals("匯入已中斷：取代 1、保留兩者 1", h.messages.last())
        assertEquals("import 結束後 importConflict 應回到 null", null, h.vm.importConflict.value)
    }

    @Test
    fun `Import JSON 無法辨識時發出失敗訊息且 importConflict 為 null`() {
        val h = buildHarness(FakePlaylistRepository(importResult = null))

        h.vm.onIntent(PlaylistListIntent.Import("not-json"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("匯入失敗：JSON 格式無法辨識", h.messages.last())
        assertEquals(null, h.vm.importConflict.value)
    }

    // ==================== 新建 / 重新命名重名防呆 ====================

    @Test
    fun `Create 遇同名歌單發出衝突訊息`() {
        // fake repo 拋 PlaylistNameConflictException → 直接提示重名（不帶「建立失敗：」前綴）
        val h = buildHarness(
            FakePlaylistRepository(createError = PlaylistNameConflictException("我的最愛"))
        )

        h.vm.onIntent(PlaylistListIntent.Create("我的最愛"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("已存在同名歌單「我的最愛」", h.messages.last())
    }

    @Test
    fun `Rename 遇同名歌單發出衝突訊息`() {
        val h = buildHarness(
            FakePlaylistRepository(renameError = PlaylistNameConflictException("我的最愛"))
        )

        h.vm.onIntent(PlaylistListIntent.Rename(1L, "我的最愛"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("已存在同名歌單「我的最愛」", h.messages.last())
    }

    @Test
    fun `Rename 改成自己目前名稱視為成功`() {
        // 改名為自己目前名稱對資料層合法（不會拋衝突例外）→ 維持成功訊息
        val repo = FakePlaylistRepository()
        val h = buildHarness(repo)

        h.vm.onIntent(PlaylistListIntent.Rename(1L, "我的最愛"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L to "我的最愛"), repo.renameCalls)
        assertEquals("已重新命名為「我的最愛」", h.messages.last())
    }
}
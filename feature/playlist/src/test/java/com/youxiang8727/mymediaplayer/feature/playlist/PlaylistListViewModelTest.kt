package com.youxiang8727.mymediaplayer.feature.playlist

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.usecase.CreatePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.DeletePlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ExportPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ImportPlaylistUseCase
import com.youxiang8727.mymediaplayer.core.domain.usecase.ObservePlaylistsUseCase
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

    /** ViewModel 只依賴 interface，此 fake 供應 export/import 結果並記錄 import 的 json。 */
    private class FakePlaylistRepository(
        var exportResult: String? = null,
        var importResult: Long? = null,
        val importCalls: MutableList<String> = mutableListOf()
    ) : PlaylistRepository {
        override fun observeAllPlaylists(): Flow<List<Playlist>> = flowOf(emptyList())
        override suspend fun createPlaylist(name: String): Long = 1L
        override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
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
        override suspend fun importPlaylistFromJson(json: String): Long? {
            importCalls += json
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
    fun `Import 成功時發出成功訊息且傳遞正確 json 至 repository`() {
        val repo = FakePlaylistRepository(importResult = 42L)
        val h = buildHarness(repo)
        val json = """{"version":1,"playlist":{"name":"匯入歌單","items":[]}}"""

        h.vm.onIntent(PlaylistListIntent.Import(json))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(json), repo.importCalls)
        assertEquals("已匯入歌單", h.messages.last())
        assertTrue("Import 不應 emit exportResult", h.exports.isEmpty())
    }

    @Test
    fun `Import JSON 無效時發出失敗訊息`() {
        val h = buildHarness(FakePlaylistRepository(importResult = null))

        h.vm.onIntent(PlaylistListIntent.Import("not-json"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("匯入失敗：JSON 格式無法辨識", h.messages.last())
    }
}
package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 驗證 [ImportPlaylistUseCase] 為 [PlaylistRepository.importPlaylistFromJson]
 * 的薄轉發：json 與 onConflict callback 透傳、結果原樣、不額外處理。
 */
class ImportPlaylistUseCaseTest {

    private class FakePlaylistRepository(
        private val importResult: PlaylistImportResult? = null
    ) : PlaylistRepository {
        val receivedJsons = mutableListOf<String>()
        var receivedOnConflict: (suspend (ImportConflictInfo) -> ImportConflictDecision)? = null

        override fun observeAllPlaylists(): Flow<List<Playlist>> =
            MutableStateFlow(emptyList())

        override suspend fun createPlaylist(name: String): Long = 1L

        override suspend fun renamePlaylist(playlistId: Long, newName: String) = Unit

        override suspend fun deletePlaylist(playlistId: Long) = Unit

        override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> =
            MutableStateFlow(emptyList())

        override suspend fun addItem(playlistId: Long, item: PlaylistItem) = Unit

        override suspend fun removeItem(playlistId: Long, videoId: String) = Unit

        override suspend fun clearPlaylist(playlistId: Long) = Unit

        override suspend fun getRandomItem(playlistId: Long): PlaylistItem? = null

        override suspend fun markStreamFailed(videoId: String, failedAt: Long) = Unit

        override suspend fun clearStreamFailed(videoId: String) = Unit

        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> =
            MutableStateFlow(emptyList())

        override suspend fun exportPlaylistAsJson(playlistId: Long): String? = null

        override suspend fun exportAllPlaylistsAsJson(): String? = null

        override suspend fun importPlaylistFromJson(
            json: String,
            onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
        ): PlaylistImportResult? {
            receivedJsons += json
            receivedOnConflict = onConflict
            return importResult
        }
    }

    @Test
    fun `JSON 與 onConflict callback 透傳且匯入結果原樣回傳`() = runTest {
        val json = """{"name":"Imported","items":[]}"""
        val result = PlaylistImportResult(created = 2, replaced = 1, keptBoth = 1, cancelled = true)
        val repo = FakePlaylistRepository(importResult = result)
        val useCase = ImportPlaylistUseCase(repo)
        val callback: suspend (ImportConflictInfo) -> ImportConflictDecision = { ImportConflictDecision.Replace }

        val returned = useCase(json, callback)

        assertEquals(json, repo.receivedJsons.single())
        // 同一 callback 實例原樣透傳（非複製、非包裝）
        assertSame(callback, repo.receivedOnConflict)
        assertEquals(result, returned)
    }

    @Test
    fun `無效 JSON 結構回傳 null`() = runTest {
        val repo = FakePlaylistRepository(importResult = null)
        val useCase = ImportPlaylistUseCase(repo)

        val result = useCase("invalid json") { ImportConflictDecision.Cancel }

        assertEquals("invalid json", repo.receivedJsons.single())
        assertNull(result)
    }
}
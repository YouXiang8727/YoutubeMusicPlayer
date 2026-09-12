package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 驗證 [ExportAllPlaylistsUseCase] 為 [PlaylistRepository.exportAllPlaylistsAsJson]
 * 的薄轉發：呼叫次數原樣、結果原樣（null 或 JSON 字串）、不額外處理。
 */
class ExportAllPlaylistsUseCaseTest {

    private class FakePlaylistRepository(
        private val exportAllResult: String? = null
    ) : PlaylistRepository {
        var exportAllCalls = 0

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

        override suspend fun exportAllPlaylistsAsJson(): String? {
            exportAllCalls++
            return exportAllResult
        }

        override suspend fun importPlaylistFromJson(json: String): Long? = null
    }

    @Test
    fun `無歌單時回傳 null`() = runTest {
        val repo = FakePlaylistRepository(exportAllResult = null)
        val useCase = ExportAllPlaylistsUseCase(repo)

        val result = useCase()

        assertEquals(1, repo.exportAllCalls)
        assertNull(result)
    }

    @Test
    fun `JSON 字串原樣回傳`() = runTest {
        val json = """{"version":2,"playlists":[]}"""
        val repo = FakePlaylistRepository(exportAllResult = json)
        val useCase = ExportAllPlaylistsUseCase(repo)

        val result = useCase()

        assertEquals(1, repo.exportAllCalls)
        assertEquals(json, result)
    }
}
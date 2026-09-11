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
 * 驗證 [ExportPlaylistUseCase] 為 [PlaylistRepository.exportPlaylistAsJson]
 * 的薄轉發：playlistId 透傳、結果原樣、不額外處理。
 */
class ExportPlaylistUseCaseTest {

    private class FakePlaylistRepository(
        private val exportResult: String? = null
    ) : PlaylistRepository {
        val receivedPlaylistIds = mutableListOf<Long>()

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

        override suspend fun exportPlaylistAsJson(playlistId: Long): String? {
            receivedPlaylistIds += playlistId
            return exportResult
        }

        override suspend fun importPlaylistFromJson(json: String): Long? = null
    }

    @Test
    fun `playlistId 透傳且 JSON 原樣回傳`() = runTest {
        val json = """{"id":1,"name":"My Playlist","items":[]}"""
        val repo = FakePlaylistRepository(exportResult = json)
        val useCase = ExportPlaylistUseCase(repo)

        val result = useCase(42L)

        assertEquals(42L, repo.receivedPlaylistIds.single())
        assertEquals(json, result)
    }

    @Test
    fun `清單不存在時回傳 null`() = runTest {
        val repo = FakePlaylistRepository(exportResult = null)
        val useCase = ExportPlaylistUseCase(repo)

        val result = useCase(999L)

        assertEquals(999L, repo.receivedPlaylistIds.single())
        assertNull(result)
    }
}

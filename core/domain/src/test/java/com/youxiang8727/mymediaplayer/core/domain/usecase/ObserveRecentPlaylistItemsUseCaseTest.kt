package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 驗證 [ObserveRecentPlaylistItemsUseCase] 為 [PlaylistRepository.observeRecentItems]
 * 的薄轉發：limit 透傳、內容原樣、不額外處理。
 */
class ObserveRecentPlaylistItemsUseCaseTest {

    private class FakePlaylistRepository(
        var recentItems: List<PlaylistItem> = emptyList()
    ) : PlaylistRepository {
        val receivedLimits = mutableListOf<Int>()

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

        override fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>> {
            receivedLimits += limit
            return MutableStateFlow(recentItems)
        }
    }

    private fun item(videoId: String, playlistId: Long = 1L) = PlaylistItem(
        videoId = videoId,
        title = "Title $videoId",
        thumbnailUrl = "https://img/$videoId",
        channel = "Channel",
        playlistId = playlistId
    )

    @Test
    fun `limit 透傳且內容原樣回傳`() = runTest {
        val repo = FakePlaylistRepository(
            recentItems = listOf(item("v2"), item("v1"))
        )
        val useCase = ObserveRecentPlaylistItemsUseCase(repo)

        val result = useCase(5).first()

        assertEquals(5, repo.receivedLimits.single())
        assertEquals(listOf("v2", "v1"), result.map { it.videoId })
    }

    @Test
    fun `空清單原樣透傳`() = runTest {
        val repo = FakePlaylistRepository(recentItems = emptyList())
        val useCase = ObserveRecentPlaylistItemsUseCase(repo)

        val result = useCase(3).first()

        assertEquals(3, repo.receivedLimits.single())
        assertEquals(emptyList<PlaylistItem>(), result)
    }
}
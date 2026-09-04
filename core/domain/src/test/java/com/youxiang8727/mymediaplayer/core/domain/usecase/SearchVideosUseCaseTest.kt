package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchVideosUseCaseTest {

    private class FakeVideoRepository(
        private val page: VideoSearchPage = VideoSearchPage(listOf(VideoResult("id1", "晴天", "", "Jay Chou")))
    ) : VideoRepository {
        var receivedQuery: String? = null
        var receivedToken: String? = null

        override suspend fun search(query: String, continuationToken: String?): Result<VideoSearchPage> {
            receivedQuery = query
            receivedToken = continuationToken
            return Result.success(page)
        }

        override suspend fun fetchTrendingSongs(region: ChartRegion): Result<List<VideoResult>> =
            Result.success(emptyList())
    }

    @Test
    fun `invoke 會移除查詢前後空白`() = runTest {
        val repo = FakeVideoRepository()
        val useCase = SearchVideosUseCase(repo)

        val result = useCase("  晴天  ")

        assertEquals("晴天", repo.receivedQuery)
        assertNull(repo.receivedToken)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().results.size)
        assertEquals("id1", result.getOrThrow().results.first().videoId)
    }

    @Test
    fun `帶 continuationToken 會傳遞給 repository`() = runTest {
        val repo = FakeVideoRepository()
        val useCase = SearchVideosUseCase(repo)

        val result = useCase("晴天", "TOKEN_PAGE_2")

        assertTrue(result.isSuccess)
        assertEquals("TOKEN_PAGE_2", repo.receivedToken)
        assertEquals("晴天", repo.receivedQuery)
    }

    @Test
    fun `repository 回傳的 nextPageToken 正確透出`() = runTest {
        val repo = FakeVideoRepository(
            page = VideoSearchPage(
                results = listOf(VideoResult("id1", "晴天", "", "Jay Chou")),
                nextPageToken = "TOKEN_NEXT"
            )
        )
        val useCase = SearchVideosUseCase(repo)

        val result = useCase("晴天")

        assertTrue(result.isSuccess)
        assertEquals("TOKEN_NEXT", result.getOrThrow().nextPageToken)
    }

    @Test
    fun `已到底時 nextPageToken 為 null`() = runTest {
        val repo = FakeVideoRepository(
            page = VideoSearchPage(results = listOf(VideoResult("id1", "晴天", "", "Jay Chou")))
        )
        val useCase = SearchVideosUseCase(repo)

        val result = useCase("晴天")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().nextPageToken)
    }
}
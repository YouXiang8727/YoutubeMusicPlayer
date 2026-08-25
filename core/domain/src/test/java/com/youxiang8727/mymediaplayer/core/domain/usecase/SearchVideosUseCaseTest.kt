package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchVideosUseCaseTest {

    private class FakeVideoRepository : VideoRepository {
        var receivedQuery: String? = null
        override suspend fun search(query: String): Result<List<VideoResult>> {
            receivedQuery = query
            return Result.success(listOf(VideoResult("id1", "晴天", "", "Jay Chou")))
        }
    }

    @Test
    fun `invoke 會移除查詢前後空白`() = runTest {
        val repo = FakeVideoRepository()
        val useCase = SearchVideosUseCase(repo)

        val result = useCase("  晴天  ")

        assertEquals("晴天", repo.receivedQuery)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("id1", result.getOrThrow().first().videoId)
    }
}

package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchTrendingSongsUseCaseTest {

    private class FakeVideoRepository(
        private val chart: List<VideoResult> = listOf(VideoResult("id1", "晴天", "", "周杰倫"))
    ) : VideoRepository {
        var receivedRegion: ChartRegion? = null

        override suspend fun search(query: String, continuationToken: String?): Result<VideoSearchPage> =
            Result.success(VideoSearchPage(results = emptyList()))

        override suspend fun fetchTrendingSongs(region: ChartRegion): Result<List<VideoResult>> {
            receivedRegion = region
            return Result.success(chart)
        }
    }

    @Test
    fun `invoke 會把區域傳遞給 repository`() = runTest {
        val repo = FakeVideoRepository()
        val useCase = FetchTrendingSongsUseCase(repo)

        val result = useCase(ChartRegion.TAIWAN)

        assertEquals(ChartRegion.TAIWAN, repo.receivedRegion)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("id1", result.getOrThrow().first().videoId)
    }

    @Test
    fun `repository 成功回傳的榜單正確透出`() = runTest {
        val charts = listOf(
            VideoResult("id1", "第一", "", "歌手A"),
            VideoResult("id2", "第二", "", "歌手B")
        )
        val useCase = FetchTrendingSongsUseCase(FakeVideoRepository(charts))

        val result = useCase(ChartRegion.TAIWAN)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        assertEquals("第一", result.getOrThrow()[0].title)
    }
}
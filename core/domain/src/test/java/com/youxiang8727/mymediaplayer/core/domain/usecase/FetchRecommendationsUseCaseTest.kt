package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.RecommendationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 [FetchRecommendationsUseCase] 的委派行為：
 * - repository 回傳結果原樣透傳（success ／ failure）
 * - 空種子 → 空清單（repository 端處理，UseCase 不攔截）
 * - seeds 原樣透傳（UseCase 不修改種子）
 * - limit 被 clamp 到 1..RECOMMENDATION_LIMIT
 */
class FetchRecommendationsUseCaseTest {

    private class FakeRecommendationRepository(
        var result: Result<List<VideoResult>> = Result.success(emptyList())
    ) : RecommendationRepository {
        var receivedSeeds: List<PlaylistItem>? = null
        var receivedLimit: Int = -1

        override suspend fun recommendationsFor(
            seeds: List<PlaylistItem>,
            limit: Int
        ): Result<List<VideoResult>> {
            receivedSeeds = seeds
            receivedLimit = limit
            return result
        }
    }

    private fun seed(videoId: String) = PlaylistItem(
        videoId = videoId,
        title = "Title $videoId",
        thumbnailUrl = "https://img/$videoId",
        channel = "Channel",
        playlistId = 1L
    )

    private val sampleResult = listOf(VideoResult("r1", "推薦一", "https://img/r1", "歌手"))

    @Test
    fun `success 結果原樣透傳`() = runTest {
        val repo = FakeRecommendationRepository(Result.success(sampleResult))
        val useCase = FetchRecommendationsUseCase(repo)

        val result = useCase(listOf(seed("s1")))

        assertTrue(result.isSuccess)
        assertEquals(sampleResult, result.getOrThrow())
    }

    @Test
    fun `空種子時 repository 回傳空清單並透傳`() = runTest {
        val repo = FakeRecommendationRepository(Result.success(emptyList()))
        val useCase = FetchRecommendationsUseCase(repo)

        val result = useCase(emptyList())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(emptyList<PlaylistItem>(), repo.receivedSeeds)
    }

    @Test
    fun `failure 結果原樣透傳`() = runTest {
        val error = RuntimeException("network down")
        val repo = FakeRecommendationRepository(Result.failure(error))
        val useCase = FetchRecommendationsUseCase(repo)

        val result = useCase(listOf(seed("s1")))

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `種子清單原樣傳給 repository`() = runTest {
        val repo = FakeRecommendationRepository()
        val useCase = FetchRecommendationsUseCase(repo)
        val seeds = listOf(seed("s1"), seed("s2"), seed("s3"))

        useCase(seeds)

        assertEquals(seeds, repo.receivedSeeds)
    }

    @Test
    fun `limit 預設為 RECOMMENDATION_LIMIT`() = runTest {
        val repo = FakeRecommendationRepository()
        val useCase = FetchRecommendationsUseCase(repo)

        useCase(listOf(seed("s1")))

        assertEquals(FetchRecommendationsUseCase.RECOMMENDATION_LIMIT, repo.receivedLimit)
    }

    @Test
    fun `超過上限的 limit 被 clamp 到 RECOMMENDATION_LIMIT`() = runTest {
        val repo = FakeRecommendationRepository()
        val useCase = FetchRecommendationsUseCase(repo)

        useCase(listOf(seed("s1")), limit = 999)

        assertEquals(FetchRecommendationsUseCase.RECOMMENDATION_LIMIT, repo.receivedLimit)
    }

    @Test
    fun `小於 1 的 limit 被 clamp 到 1`() = runTest {
        val repo = FakeRecommendationRepository()
        val useCase = FetchRecommendationsUseCase(repo)

        useCase(listOf(seed("s1")), limit = 0)

        assertEquals(1, repo.receivedLimit)
    }
}
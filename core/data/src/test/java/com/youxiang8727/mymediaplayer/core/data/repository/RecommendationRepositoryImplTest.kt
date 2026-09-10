package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.common.DefaultDispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.remote.RelatedStreamsDataSource
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.RecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 [RecommendationRepositoryImpl] 的跨種子共現評分流程（Fake 資料源，純 JVM）：
 * - 多 seed 共現候選分數較高且排序在前
 * - 排除已在播放清單中的歌曲（knownVideoIds）
 * - 排除種子自身
 * - 單一 seed 失敗 → 視同無相關歌曲，不影響其他 seed
 * - 全部 seed 皆失敗 → Result.failure
 * - limit 截斷
 */
class RecommendationRepositoryImplTest {

    /** Fake RelatedStreamsDataSource：依 videoId 回傳預排結果（success／failure）。
     * 只覆寫 related()；父類 constructor 的 client／dispatcher 僅供初始化（測試不觸網）。 */
    private class FakeRelatedStreamsDataSource(
        private val results: Map<String, Result<List<VideoResult>>>
    ) : RelatedStreamsDataSource(
        okHttpClient = OkHttpClient.Builder().build(),
        dispatchers = DefaultDispatcherProvider()
    ) {
        override suspend fun related(videoId: String): Result<List<VideoResult>> =
            results[videoId] ?: Result.success(emptyList())
    }

    /** Fake PlaylistRepository：僅供應 observeRecentItems 快照（已知 videoId）。 */
    private class FakePlaylistRepository(
        private val knownItems: List<PlaylistItem> = emptyList()
    ) : PlaylistRepository {

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
            MutableStateFlow(knownItems)
    }

    private fun seed(videoId: String) = PlaylistItem(
        videoId = videoId,
        title = "Seed $videoId",
        thumbnailUrl = "",
        channel = "",
        playlistId = 1L
    )

    private fun video(videoId: String, title: String = "Title $videoId") =
        VideoResult(videoId, title, "https://img/$videoId", "歌手")

    private fun success(vararg items: VideoResult) = Result.success(items.toList())

    // ── 排序：共現計分 ──

    @Test
    fun `共現候選（出現在多個 seed 的 related）排序在前`() = runTest {
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf(
                // 候選 c1 同時出現在 seed1 與 seed2 的 related → 分數 2
                "seed1" to success(video("c1"), video("d1")),
                "seed2" to success(video("c1"), video("e1")),
                // seed - d1/e1 只出現一次 → 分數 1，排在 c1 之後
                "seed3" to success(video("f1"))
            )
        )
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(
            seeds = listOf(seed("seed1"), seed("seed2"), seed("seed3")),
            limit = 10
        )

        val ids = result.getOrThrow().map { it.videoId }
        assertEquals(listOf("c1", "d1", "e1", "f1"), ids)
    }

    @Test
    fun `同分候選依 title 字元序穩定排序`() = runTest {
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf(
                // b 與 a 同分（各只出現一次），title 字元序 a 在前
                "seed1" to success(video("b", "Banana")),
                "seed2" to success(video("a", "Apple"))
            )
        )
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(
            seeds = listOf(seed("seed1"), seed("seed2")),
            limit = 10
        )

        val ids = result.getOrThrow().map { it.videoId }
        assertEquals(listOf("a", "b"), ids)
    }

    // ── 排除規則 ──

    @Test
    fun `排除已在播放清單中的歌曲`() = runTest {
        // known = c1（已收藏）→ 不應出現在推薦
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf("seed1" to success(video("c1"), video("d1")))
        )
        val repository: RecommendationRepository = RecommendationRepositoryImpl(
            dataSource,
            FakePlaylistRepository(knownItems = listOf(seed("c1")))
        )

        val result = repository.recommendationsFor(listOf(seed("seed1")), limit = 10)

        val ids = result.getOrThrow().map { it.videoId }
        assertEquals(listOf("d1"), ids)
    }

    @Test
    fun `排除種子自身的 videoId`() = runTest {
        // 種子 seed1 出現在自己的 related → 排除
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf("seed1" to success(video("seed1"), video("d1")))
        )
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(listOf(seed("seed1")), limit = 10)

        val ids = result.getOrThrow().map { it.videoId }
        assertEquals(listOf("d1"), ids)
    }

    // ── 失敗降級 ──

    @Test
    fun `單一 seed 失敗視同無相關歌曲不影響其他 seed`() = runTest {
        val error = RuntimeException("extractor failed")
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf(
                "seed1" to Result.failure(error),
                "seed2" to success(video("d1"))
            )
        )
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(
            seeds = listOf(seed("seed1"), seed("seed2")),
            limit = 10
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("d1"), result.getOrThrow().map { it.videoId })
    }

    @Test
    fun `全部種子皆失敗時回傳 failure`() = runTest {
        val error = RuntimeException("extractor failed")
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf(
                "seed1" to Result.failure(error),
                "seed2" to Result.failure(error)
            )
        )
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(
            seeds = listOf(seed("seed1"), seed("seed2")),
            limit = 10
        )

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `空種子直接回傳空清單且不觸網`() = runTest {
        val dataSource = FakeRelatedStreamsDataSource(emptyMap()) // 任何呼叫都會缺 key 回空
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(emptyList(), limit = 10)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    // ── limit ──

    @Test
    fun `候選超過 limit 時只回傳前 limit 筆`() = runTest {
        val dataSource = FakeRelatedStreamsDataSource(
            mapOf("seed1" to success(video("c1"), video("c2"), video("c3"), video("c4")))
        )
        val repository: RecommendationRepository =
            RecommendationRepositoryImpl(dataSource, FakePlaylistRepository())

        val result = repository.recommendationsFor(listOf(seed("seed1")), limit = 2)

        val ids = result.getOrThrow().map { it.videoId }
        assertEquals(listOf("c1", "c2"), ids)
    }

    // ── rankRecommendations 純函數（直接驗證邊界）──

    @Test
    fun `rankRecommendations 同 seed 內重複候選不灌分`() {
        // seed1 的 related 中 c1 出現兩次 → 共現數仍為 1（只算一次）
        val seeds = listOf(seed("seed1"))
        val relatedBySeed = mapOf(
            "seed1" to listOf(video("c1"), video("c1"), video("d1"))
        )
        val ranked = rankRecommendations(seeds, relatedBySeed, emptySet(), limit = 10)

        val ids = ranked.map { it.videoId }
        assertEquals(listOf("c1", "d1"), ids)
    }

    @Test
    fun `rankRecommendations 無候選回空清單`() {
        val seeds = listOf(seed("seed1"))
        val relatedBySeed = mapOf("seed1" to emptyList<VideoResult>())

        val ranked = rankRecommendations(seeds, relatedBySeed, emptySet(), limit = 10)

        assertTrue(ranked.isEmpty())
    }
}
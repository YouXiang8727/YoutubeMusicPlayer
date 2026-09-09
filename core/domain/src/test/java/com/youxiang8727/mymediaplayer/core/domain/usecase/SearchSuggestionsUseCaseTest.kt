package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.repository.SearchSuggestionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 SearchSuggestionsUseCase 的輸入防禦與委派：
 * trim、過短輸入不觸網（回空清單）、正常輸入透傳給 repository。
 */
class SearchSuggestionsUseCaseTest {

    private class FakeSearchSuggestionRepository : SearchSuggestionRepository {
        var receivedQueries = mutableListOf<String>()
        var result: List<String> = emptyList()

        override suspend fun suggestions(query: String): List<String> {
            receivedQueries += query
            return result
        }
    }

    @Test
    fun `trim 後的關鍵字透傳給 repository`() = runTest {
        val repo = FakeSearchSuggestionRepository().apply {
            result = listOf("晴天", "晴天 周杰倫")
        }
        val useCase = SearchSuggestionsUseCase(repo)

        val result = useCase("  晴天  ")

        assertEquals(listOf("晴天"), repo.receivedQueries)
        assertEquals(listOf("晴天", "晴天 周杰倫"), result)
    }

    @Test
    fun `純空白輸入回空清單且不觸網`() = runTest {
        val repo = FakeSearchSuggestionRepository()
        val useCase = SearchSuggestionsUseCase(repo)

        val result = useCase("   ")

        assertTrue(result.isEmpty())
        assertTrue(repo.receivedQueries.isEmpty())
    }

    @Test
    fun `空字串輸入回空清單且不觸網`() = runTest {
        val repo = FakeSearchSuggestionRepository()
        val useCase = SearchSuggestionsUseCase(repo)

        val result = useCase("")

        assertTrue(result.isEmpty())
        assertTrue(repo.receivedQueries.isEmpty())
    }

    @Test
    fun `單一字元輸入仍會觸發查詢`() = runTest {
        val repo = FakeSearchSuggestionRepository().apply {
            result = listOf("a", "abc")
        }
        val useCase = SearchSuggestionsUseCase(repo)

        val result = useCase("a")

        assertEquals(listOf("a"), repo.receivedQueries)
        assertEquals(listOf("a", "abc"), result)
    }

    @Test
    fun `repository 回空清單時透出空清單`() = runTest {
        val repo = FakeSearchSuggestionRepository() // result 預設空
        val useCase = SearchSuggestionsUseCase(repo)

        val result = useCase("查無此歌")

        assertTrue(result.isEmpty())
        assertEquals(listOf("查無此歌"), repo.receivedQueries)
    }
}
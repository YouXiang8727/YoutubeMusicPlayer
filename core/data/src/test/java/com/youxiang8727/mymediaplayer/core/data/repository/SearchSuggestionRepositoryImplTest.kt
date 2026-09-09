package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.remote.SearchSuggestionDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 SearchSuggestionRepositoryImpl 對資料源的委派與輸入防禦：
 * trim 透傳、空白不觸網、資料源結果原樣透出。
 */
class SearchSuggestionRepositoryImplTest {

    private class FakeSearchSuggestionDataSource : SearchSuggestionDataSource {
        val receivedQueries = mutableListOf<String>()
        var result: List<String> = emptyList()

        override suspend fun suggestions(query: String): List<String> {
            receivedQueries += query
            return result
        }
    }

    @Test
    fun `trim 後的關鍵字透傳給資料源`() = runTest {
        val dataSource = FakeSearchSuggestionDataSource().apply {
            result = listOf("周杰倫", "周杰倫 晴天")
        }
        val repository = SearchSuggestionRepositoryImpl(dataSource)

        val result = repository.suggestions("  周杰倫  ")

        assertEquals(listOf("周杰倫"), dataSource.receivedQueries)
        assertEquals(listOf("周杰倫", "周杰倫 晴天"), result)
    }

    @Test
    fun `純空白輸入回空清單且不觸網`() = runTest {
        val dataSource = FakeSearchSuggestionDataSource()
        val repository = SearchSuggestionRepositoryImpl(dataSource)

        val result = repository.suggestions("   ")

        assertTrue(result.isEmpty())
        assertTrue(dataSource.receivedQueries.isEmpty())
    }

    @Test
    fun `空字串輸入回空清單且不觸網`() = runTest {
        val dataSource = FakeSearchSuggestionDataSource()
        val repository = SearchSuggestionRepositoryImpl(dataSource)

        val result = repository.suggestions("")

        assertTrue(result.isEmpty())
        assertTrue(dataSource.receivedQueries.isEmpty())
    }

    @Test
    fun `資料源回空清單時透出空清單`() = runTest {
        val dataSource = FakeSearchSuggestionDataSource()
        val repository = SearchSuggestionRepositoryImpl(dataSource)

        val result = repository.suggestions("查無此歌")

        assertTrue(result.isEmpty())
        assertEquals(listOf("查無此歌"), dataSource.receivedQueries)
    }
}
package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證三個搜尋紀錄 UseCase 的委派行為（Fake in-memory repository）：
 * - Add：query 原樣轉發（trim／空白防禦在 repository 實作層）
 * - Observe：repo 的 Flow 原樣傳遞
 * - Clear：呼叫 repo.clear
 */
class SearchHistoryUseCasesTest {

    private class FakeSearchHistoryRepository : SearchHistoryRepository {
        val history = MutableStateFlow<List<String>>(emptyList())
        var clearCount = 0

        override suspend fun add(query: String) {
            history.value = history.value.filterNot { it == query } + query
        }

        override fun observeAll(): Flow<List<String>> = history

        override suspend fun clear() {
            clearCount++
            history.value = emptyList()
        }
    }

    @Test
    fun `Add 將 query 原樣轉發給 repository`() = runTest {
        val repo = FakeSearchHistoryRepository()
        val useCase = AddSearchHistoryUseCase(repo)

        useCase("晴天")

        assertEquals(listOf("晴天"), repo.history.value)
    }

    @Test
    fun `Observe 原樣透傳 repository 的 Flow`() = runTest {
        val repo = FakeSearchHistoryRepository().apply {
            history.value = listOf("晴天", "周杰倫")
        }
        val useCase = ObserveSearchHistoryUseCase(repo)

        val result = useCase().first()

        assertEquals(listOf("晴天", "周杰倫"), result)
    }

    @Test
    fun `Clear 呼叫 repository clear`() = runTest {
        val repo = FakeSearchHistoryRepository().apply {
            history.value = listOf("晴天")
        }
        val useCase = ClearSearchHistoryUseCase(repo)

        useCase()

        assertTrue(repo.history.value.isEmpty())
        assertEquals(1, repo.clearCount)
    }
}
package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.SearchHistoryDao
import com.youxiang8727.mymediaplayer.core.data.local.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 團隊規範要求：core:data Repository 對 Fake Dao 至少一組測試。
 * 以 in-memory StateFlow 模擬 Room 行為（含 SQL `LIMIT 10` 排序與 trim 語意），
 * 驗證 add 的 trim／空白忽略、重複 query 置頂、排序（最新在前）、上限汰除與 clear。
 */
class SearchHistoryRepositoryTest {

    /** Fake Dao：以 MutableStateFlow 模擬 search_history 表（REPLACE／排序／LIMIT／trim 語意）。 */
    private class FakeSearchHistoryDao : SearchHistoryDao {
        val table = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<SearchHistoryEntity>> =
            table.map { rows -> rows.sortedByDescending { it.searchedAt }.take(10) }

        override suspend fun upsert(entity: SearchHistoryEntity) {
            // REPLACE 語意：同 query 移除舊列後插入新列
            table.value = table.value.filterNot { it.query == entity.query } + entity
        }

        override suspend fun trimToLimit(limit: Int) {
            table.value = table.value.sortedByDescending { it.searchedAt }.take(limit)
        }

        override suspend fun clear() {
            table.value = emptyList()
        }
    }

    private fun seededDao(vararg entries: SearchHistoryEntity): FakeSearchHistoryDao =
        FakeSearchHistoryDao().apply { table.value = entries.toList() }

    private fun entity(query: String, searchedAt: Long) = SearchHistoryEntity(query, searchedAt)

    @Test
    fun `add 空白 query 忽略不寫入`() = runTest {
        val dao = FakeSearchHistoryDao()
        val repository = SearchHistoryRepositoryImpl(dao)

        repository.add("   ")
        repository.add("")

        assertTrue(repository.observeAll().first().isEmpty())
        assertTrue(dao.table.value.isEmpty())
    }

    @Test
    fun `add 會 trim 前後空白`() = runTest {
        val dao = FakeSearchHistoryDao()
        val repository = SearchHistoryRepositoryImpl(dao)

        repository.add("  晴天  ")

        assertEquals(listOf("晴天"), repository.observeAll().first())
        assertEquals("晴天", dao.table.value.single().query)
    }

    @Test
    fun `add 重複 query 更新時間戳置頂`() = runTest {
        val dao = seededDao(entity("晴天", 100L), entity("周杰倫", 200L))
        val repository = SearchHistoryRepositoryImpl(dao)

        repository.add("晴天") // 以目前時間戳覆寫 → 應置頂

        val history = repository.observeAll().first()
        assertEquals(listOf("晴天", "周杰倫"), history)
    }

    @Test
    fun `observeAll 依 searchedAt 最新在前`() = runTest {
        val dao = seededDao(entity("舊", 100L), entity("中", 200L), entity("新", 300L))
        val repository = SearchHistoryRepositoryImpl(dao)

        assertEquals(listOf("新", "中", "舊"), repository.observeAll().first())
    }

    @Test
    fun `clear 清空全部`() = runTest {
        val dao = seededDao(entity("晴天", 100L), entity("周杰倫", 200L))
        val repository = SearchHistoryRepositoryImpl(dao)

        repository.clear()

        assertTrue(repository.observeAll().first().isEmpty())
        assertTrue(dao.table.value.isEmpty())
    }

    @Test
    fun `超過 10 筆汰除最舊`() = runTest {
        val dao = FakeSearchHistoryDao()
        val repository = SearchHistoryRepositoryImpl(dao)

        // 依序加入 12 筆；sleep 2ms 確保時間戳遞增（System.currentTimeMillis 解析度），
        // 使「汰除最舊兩筆」斷言確定性成立。
        repeat(12) { i ->
            repository.add("query$i")
            Thread.sleep(2)
        }

        val history = repository.observeAll().first()
        assertEquals(10, history.size)
        assertEquals("query11", history.first()) // 最新在前
        assertEquals("query2", history.last())   // 最舊 query0、query1 被汰除
        // DAO 表內實際刪除（非僅查詢 LIMIT）
        assertTrue(dao.table.value.none { it.query == "query0" || it.query == "query1" })
    }
}
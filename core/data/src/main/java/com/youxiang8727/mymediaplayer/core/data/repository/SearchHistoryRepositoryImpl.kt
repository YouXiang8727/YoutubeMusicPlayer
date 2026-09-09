package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.local.SearchHistoryDao
import com.youxiang8727.mymediaplayer.core.data.local.SearchHistoryEntity
import com.youxiang8727.mymediaplayer.core.domain.repository.SearchHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val dao: SearchHistoryDao
) : SearchHistoryRepository {

    override suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(SearchHistoryEntity(query = trimmed))
        dao.trimToLimit(MAX_HISTORY_SIZE)
    }

    override fun observeAll(): Flow<List<String>> =
        dao.observeAll().map { entities -> entities.map { it.query } }

    override suspend fun clear() = dao.clear()

    companion object {
        /** 搜尋紀錄保留上限（與 [SearchHistoryDao.observeAll] 的 SQL `LIMIT 10` 同步）。 */
        const val MAX_HISTORY_SIZE = 10
    }
}
package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.repository.SearchHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 記錄一次搜尋進搜尋紀錄。
 *
 * 本 UseCase 為薄轉發層：trim／空白忽略／去重置頂／上限汰除等防禦
 * 統一由 core:data 的 repository 實作負責（見 [SearchHistoryRepository.add]）。
 */
class AddSearchHistoryUseCase @Inject constructor(
    private val repository: SearchHistoryRepository
) {
    suspend operator fun invoke(query: String) = repository.add(query)
}

/**
 * 觀察全部搜尋紀錄（最新在前，最多 10 筆）。
 */
class ObserveSearchHistoryUseCase @Inject constructor(
    private val repository: SearchHistoryRepository
) {
    operator fun invoke(): Flow<List<String>> = repository.observeAll()
}

/**
 * 清除全部搜尋紀錄。
 */
class ClearSearchHistoryUseCase @Inject constructor(
    private val repository: SearchHistoryRepository
) {
    suspend operator fun invoke() = repository.clear()
}
package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.repository.SearchSuggestionRepository
import javax.inject.Inject

/**
 * 取得 YouTube 搜尋建議（autocomplete）。
 *
 * 於實作前先做輕量防禦：trim 關鍵字、空白與過短輸入直接回空清單（不觸網），
 * 避免對每鍵輸入都發送無意義網路請求。
 */
class SearchSuggestionsUseCase @Inject constructor(
    private val repository: SearchSuggestionRepository
) {
    /**
     * @param query 使用者目前輸入的關鍵字（未 trim 亦可，內部會處理）。
     *              trim 後為空白或長度 < [MIN_QUERY_LENGTH] 時回空清單。
     */
    suspend operator fun invoke(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        return repository.suggestions(trimmed)
    }

    companion object {
        /** 觸發建議查詢的最小關鍵字長度（低於此回空清單，不發請求）。 */
        const val MIN_QUERY_LENGTH = 1
    }
}

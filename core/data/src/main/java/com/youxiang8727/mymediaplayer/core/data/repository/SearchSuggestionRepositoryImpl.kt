package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.data.remote.SearchSuggestionDataSource
import com.youxiang8727.mymediaplayer.core.domain.repository.SearchSuggestionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchSuggestionRepositoryImpl @Inject constructor(
    private val dataSource: SearchSuggestionDataSource
) : SearchSuggestionRepository {

    override suspend fun suggestions(query: String): List<String> =
        // 防禦：過短（含純空白 trim 後）直接回空，不觸網。
        if (query.trim().isEmpty()) emptyList() else dataSource.suggestions(query.trim())
}

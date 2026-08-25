package com.youxiang8727.mymediaplayer.core.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * YouTube 行動版搜尋頁 API（無需 API Key）。
 * 回傳原始 HTML，由 YoutubeDataSource 解析內嵌的 ytInitialData。
 */
interface YoutubeSearchApi {

    @GET("results")
    suspend fun searchHtml(
        @Query("search_query") query: String
    ): String
}

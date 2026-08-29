package com.youxiang8727.mymediaplayer.core.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * YouTube 行動版搜尋 API（無需 API Key）。
 *
 * 分頁機制（2026-08 真實多頁實測結論）：
 * - **初次搜尋**：`GET results`（不帶 continuation），回傳完整搜尋頁 JSON（`ytInitialData`）。
 *   結果 renderer 為 `videoRenderer`，續頁 token 位於
 *   `continuationItemRenderer.continuationEndpoint.continuationCommand.token`。
 * - **續頁**：**必須走 innerTube `POST youtubei/v1/search`**（[searchContinuation]），
 *   把上一頁 token 放進 body 的 `continuation` 欄位。
 *   實測證實：若改用 `GET results?continuation=`，YouTube 會把**整頁重新排序回傳**
 *   （與首頁結果重疊率高達 55~100%），造成「載入更多變成輪迴 / 結果重複」。
 *   而 POST 回傳的是 **append-only 續頁 chunk**
 *   （`onResponseReceivedCommands[].appendContinuationItemsAction.continuationItems[]`），
 *   結果 renderer 為 `videoWithContextRenderer`，實測與先前頁重疊率 0%（深頁才漸增）。
 */
interface YoutubeSearchApi {

    /** 初次搜尋（不帶 continuation）。回傳完整 ytInitialData HTML/JSON。 */
    @GET("results")
    suspend fun searchHtml(
        @Query("search_query") query: String,
        @Query("continuation") continuationToken: String? = null
    ): String

    /**
     * 續頁：innerTube POST 抓取 append-only 續頁 chunk。
     * baseUrl 為 `https://m.youtube.com/`，故實際請求 `https://m.youtube.com/youtubei/v1/search`。
     * body（[RequestBody]，application/json）由 [YoutubeDataSource] 建構，
     * 內含 `context.client` 與 `continuation` token。
     */
    @POST("youtubei/v1/search")
    suspend fun searchContinuation(
        @Header("X-Youtube-Client-Name") clientName: String,
        @Header("X-Youtube-Client-Version") clientVersion: String,
        @Body body: RequestBody
    ): String
}

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
 * - **初次搜尋（預設相關性）**：`GET results`（不帶 continuation），回傳完整搜尋頁 JSON（`ytInitialData`）。
 *   結果 renderer 為 `videoRenderer`，續頁 token 位於
 *   `continuationItemRenderer.continuationEndpoint.continuationCommand.token`。
 * - **初次搜尋（指定排序）**：改走 innerTube POST（[searchInnerTube]）並在 body 帶 `params`。
 *   實測 `GET results?params=` **不生效**（結果只是非確定性雜訊、並未依日期排序），
 *   故排序無法走 GET 路由。POST 首頁結果 renderer 為 `videoWithContextRenderer`
 *   （與續頁同構），續頁 token 位置與 GET 路徑相同。
 * - **續頁**：**必須走 innerTube `POST youtubei/v1/search`**（[searchInnerTube]），
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
     * innerTube `POST youtubei/v1/search`：同時服務「指定排序的初次搜尋」與「續頁」。
     * baseUrl 為 `https://m.youtube.com/`，故實際請求 `https://m.youtube.com/youtubei/v1/search`。
     * body（[RequestBody]，application/json）由 [YoutubeDataSource] 建構：
     * 續頁含 `context.client` 與 `continuation` token；排序首頁含 `context.client`、
     * `query` 與 `params`。
     */
    @POST("youtubei/v1/search")
    suspend fun searchInnerTube(
        @Header("X-Youtube-Client-Name") clientName: String,
        @Header("X-Youtube-Client-Version") clientVersion: String,
        @Body body: RequestBody
    ): String
}

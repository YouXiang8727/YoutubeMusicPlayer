package com.youxiang8727.mymediaplayer.core.data.remote

import android.util.Log
import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 不使用 YouTube Data API Key：
 * 透過 Retrofit(OkHttp) 請求（初次搜尋 GET / 續頁 innerTube POST），
 * 再以 kotlinx.serialization 解析內嵌的 ytInitialData JSON。
 *
 * 分頁（2026-08 真實多頁實測結論，根因已修正）：
 * - **初次搜尋**：`GET results`，解析 `videoRenderer`（現有 [parseYtInitialData] 路徑），
 *   續頁 token 取自 `continuationItemRenderer.continuationEndpoint.continuationCommand.token`。
 * - **續頁**：**innerTube POST**（[api.searchContinuation]），解析 append-only 續頁 chunk
 *   中的 `videoWithContextRenderer`（[parseContinuationChunk]）。
 *   修正前舊行為是 `GET results?continuation=`，實測回傳的是**整頁重新排序**
 *   （與首頁重疊 55~100%），導致「載入更多變成輪迴 / 結果重複」；POST 續頁實測重疊 0%。
 *
 * 每抓完一頁會以 `android.util.Log` 輸出 [TAG] 的摘要＋明細行（供使用者對照 logcat
 * 驗證續頁是否重複）；token 未推進時印 `WARN token not advanced` 警示。
 *
 * [extractYtInitialData]、[parseYtInitialData]、[parseContinuationChunk]、
 * [collectVideoRenderers]、[collectContinuationVideoRenderers]、[extractContinuationToken]
 * 為 internal 純函數（不觸網），供 JVM 單元測試直接驗證。
 */
@Singleton
class YoutubeDataSource @Inject constructor(
    private val api: YoutubeSearchApi,
    private val dispatchers: DispatcherProvider
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 初次搜尋（[continuationToken] null）或載入續頁（非 null）。
     *
     * @param previousVideoIds 已載入之全部 videoId（供 log 重疊率計算；由呼叫端傳入）。
     *                         僅為本資料層可觀測性用途，不影響回傳之 [VideoSearchPage]。
     * @param pageNumber 目前頁號（供 log）；null 時以「首頁 → 1、續頁 → cont」近似。
     */
    suspend fun search(
        query: String,
        continuationToken: String? = null,
        previousVideoIds: Set<String> = emptySet(),
        pageNumber: Int? = null
    ): VideoSearchPage = withContext(dispatchers.io) {
        if (continuationToken == null) {
            val html = api.searchHtml(query, null)
            val page = extractYtInitialData(html)
                ?.let { parseYtInitialData(json, it) }
                ?: VideoSearchPage(results = emptyList())
            logPage(query, pageNumber, null, page.nextPageToken, page, previousVideoIds)
            page
        } else {
            val body = buildContinuationBody(continuationToken)
            val rawJson = api.searchContinuation(MWEB_CLIENT_HEADER, MWEB_CLIENT_VERSION, body)
            val page = parseContinuationChunk(json, rawJson)
            logPage(query, pageNumber, continuationToken, page.nextPageToken, page, previousVideoIds)
            page
        }
    }

    // region ── 續頁 request body（innerTube POST）──

    private companion object {
        const val MWEB_CLIENT_HEADER = "2"
        const val MWEB_CLIENT_VERSION = "2.20240820.00.00"
        const val JSON_MEDIA_TYPE = "application/json"
    }

    /** 以固定 innerTube MWEB context 包裝 continuation token 為 JSON RequestBody。 */
    private fun buildContinuationBody(token: String): RequestBody {
        // token 為 base64url 字串（A-Za-z0-9+/=），不含 JSON 需跳脫字元，直接內嵌安全。
        val payload = "{\"context\":{\"client\":{" +
            "\"clientName\":\"MWEB\",\"clientVersion\":\"$MWEB_CLIENT_VERSION\"," +
            "\"hl\":\"zh-TW\",\"gl\":\"TW\"}},\"continuation\":\"$token\"}"
        return payload.toRequestBody(JSON_MEDIA_TYPE.toMediaType())
    }

    // endregion

    // region ── 可觀測性 log（SearchPaging）──

    private fun logPage(
        query: String,
        pageNumber: Int?,
        sentToken: String?,
        nextToken: String?,
        page: VideoSearchPage,
        previousVideoIds: Set<String>
    ) {
        val q = query.ifBlank { "?" }
        val pageLabel = when {
            pageNumber != null -> pageNumber.toString()
            sentToken == null -> "1"
            else -> "cont"
        }
        val sentHead = sentToken?.take(12) ?: "-"
        val nextHead = nextToken?.take(12) ?: "-"
        val count = page.results.size
        val overlap = if (previousVideoIds.isEmpty()) {
            "?" // 呼叫端未提供先前面結果時無法計算；由 C/A 配合串接 previousVideoIds 後可得實數
        } else {
            page.results.count { it.videoId in previousVideoIds }.toString()
        }

        Log.i(TAG, "SUMMARY q=$q page=$pageLabel sent=$sentHead next=$nextHead " +
            "count=$count overlapPrior=$overlap")
        val detail = page.results.joinToString(" | ") { "${it.videoId}:${it.title.take(30)}" }
        Log.i(TAG, "DETAIL  q=$q page=$pageLabel videos=[$detail]")

        if (sentToken != null && nextToken != null && sentToken == nextToken) {
            Log.w(TAG, "WARN token not advanced: sent=$sentHead next=$nextHead " +
                "-> 續頁可能輪迴（同 token 重複）")
        }
    }

    // endregion
}

// region ── 可測試的內部分析函數（純 JVM，不觸網）──

/** 以大括號配對方式擷取 ytInitialData 的完整 JSON 字串。 */
internal fun extractYtInitialData(html: String): String? {
    val marker = "ytInitialData"
    val startIdx = html.indexOf(marker).takeIf { it >= 0 } ?: return null
    val braceStart = html.indexOf('{', startIdx).takeIf { it >= 0 } ?: return null

    var depth = 0
    var inString = false
    var escaped = false
    for (i in braceStart until html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return html.substring(braceStart, i + 1)
            }
        }
    }
    return null
}

/**
 * 解析**初次搜尋** ytInitialData：取該頁全部 `videoRenderer` 轉為 [VideoResult]，
 * 並嘗試抽取續頁 token。JSON 不合法時回傳空頁。
 */
internal fun parseYtInitialData(json: Json, rawJson: String): VideoSearchPage {
    val root: JsonElement = runCatching { json.parseToJsonElement(rawJson) }
        .getOrElse { return VideoSearchPage(results = emptyList()) }

    val renderers = mutableListOf<JsonObject>()
    collectVideoRenderers(root, renderers)

    val results = renderers.mapNotNull { it.toVideoResult() }
        .distinctBy { it.videoId }
    return VideoSearchPage(
        results = results,
        nextPageToken = extractContinuationToken(root)
    )
}

/**
 * 解析**續頁（innerTube POST）** chunk：
 * 取 append-only 結構中的全部 `videoWithContextRenderer` 轉為 [VideoResult]。
 * 續頁 chunk key 路徑與首頁不同（實測）：
 * `onResponseReceivedCommands[].appendContinuationItemsAction.continuationItems[].itemSectionRenderer.contents[]`。
 * token 仍取自最後的 `continuationItemRenderer`（[extractContinuationToken] 全樹走訪可涵蓋）。
 * JSON 不合法時回傳空頁。
 */
internal fun parseContinuationChunk(json: Json, rawJson: String): VideoSearchPage {
    val root: JsonElement = runCatching { json.parseToJsonElement(rawJson) }
        .getOrElse { return VideoSearchPage(results = emptyList()) }

    val renderers = mutableListOf<JsonObject>()
    collectContinuationVideoRenderers(root, renderers)

    val results = renderers.mapNotNull { it.toContinuationVideoResult() }
        .distinctBy { it.videoId }
    return VideoSearchPage(
        results = results,
        nextPageToken = extractContinuationToken(root)
    )
}

/**
 * 遞迴走訪 JSON 樹，蒐集所有 **videoRenderer** 節點（初次搜尋頁用）。
 * 維持全樹收集（與續頁同構考慮版型微調）。
 */
internal fun collectVideoRenderers(element: JsonElement, out: MutableList<JsonObject>) {
    when (element) {
        is JsonObject -> {
            element["videoRenderer"]?.let { renderer ->
                if (renderer is JsonObject) out += renderer
            }
            element.values.forEach { collectVideoRenderers(it, out) }
        }
        is JsonArray -> element.forEach { collectVideoRenderers(it, out) }
        else -> Unit
    }
}

/**
 * 遞迴走訪 JSON 樹，蒐集所有 **videoWithContextRenderer** 節點（innerTube 續頁 chunk 用）。
 */
internal fun collectContinuationVideoRenderers(element: JsonElement, out: MutableList<JsonObject>) {
    when (element) {
        is JsonObject -> {
            element["videoWithContextRenderer"]?.let { renderer ->
                if (renderer is JsonObject) out += renderer
            }
            element.values.forEach { collectContinuationVideoRenderers(it, out) }
        }
        is JsonArray -> element.forEach { collectContinuationVideoRenderers(it, out) }
        else -> Unit
    }
}

/**
 * 從 JSON 樹抽取續頁 token。
 *
 * 只認 `continuationItemRenderer.continuationEndpoint.continuationCommand.token`
 * 這條路徑（2026-08 實測 m.youtube.com 主搜尋續頁符號）。filter chips
 * （`chipCloudChipRenderer.navigationEndpoint.continuationCommand`）不在此路徑上，不會誤取。
 *
 * 實測連續多頁只會出現**一顆** search token（`request == "CONTINUATION_REQUEST_TYPE_SEARCH"`），
 * 故 `firstOrNull` 與 rightmost 等價；此處維持優先取 search token、否則退回第一顆。
 */
internal fun extractContinuationToken(root: JsonElement): String? {
    val searchTokens = mutableListOf<String>()
    val otherTokens = mutableListOf<String>()

    fun visit(node: JsonElement) {
        when (node) {
            is JsonObject -> {
                (node["continuationItemRenderer"] as? JsonObject)?.let { item ->
                    val command = (item["continuationEndpoint"] as? JsonObject)
                        ?.get("continuationCommand") as? JsonObject
                    val token = (command?.get("token") as? JsonPrimitive)
                        ?.takeIf { it.isString && it.content.isNotBlank() }
                        ?.content
                    if (token != null) {
                        val request = (command["request"] as? JsonPrimitive)?.content
                        if (request == "CONTINUATION_REQUEST_TYPE_SEARCH") {
                            searchTokens += token
                        } else {
                            otherTokens += token
                        }
                    }
                }
                node.values.forEach { visit(it) }
            }
            is JsonArray -> node.forEach { visit(it) }
            else -> Unit
        }
    }

    visit(root)
    return searchTokens.firstOrNull() ?: otherTokens.firstOrNull()
}

// endregion

// region ── JSON 小工具（維持既有手動走訪風格）──

private fun JsonObject.toVideoResult(): VideoResult? {
    val videoId = str("videoId") ?: return null
    val title = path("title", "runs")?.jsonArrayFirstText()
        ?: primitiveText(path("title", "simpleText"))
        ?: ""
    val channel = path("ownerText", "runs")?.jsonArrayFirstText()
        ?: path("longBylineText", "runs")?.jsonArrayFirstText()
        ?: primitiveText(path("ownerText", "simpleText"))
        ?: ""
    val thumb = thumbnails().firstOrNull { it.isNotBlank() } ?: ""

    return VideoResult(
        videoId = videoId,
        title = title.ifBlank { videoId },
        thumbnailUrl = thumb,
        channel = channel
    )
}

/** innerTube 續頁 chunk 的 `videoWithContextRenderer` 欄位對應（實測 2026-08）： */
private fun JsonObject.toContinuationVideoResult(): VideoResult? {
    val videoId = (path("navigationEndpoint", "watchEndpoint") as? JsonObject)?.str("videoId")
        ?: return null
    val title = path("headline", "runs")?.jsonArrayFirstText()
        ?: primitiveText(path("headline", "simpleText"))
        ?: ""
    val channel = path("shortBylineText", "runs")?.jsonArrayFirstText()
        ?: ""
    val thumb = thumbnails().firstOrNull { it.isNotBlank() } ?: ""

    return VideoResult(
        videoId = videoId,
        title = title.ifBlank { videoId },
        thumbnailUrl = thumb,
        channel = channel
    )
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotBlank() }?.content

private fun primitiveText(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.content

private fun JsonObject.path(vararg keys: String): JsonElement? {
    var cur: JsonElement = this
    for (k in keys) {
        cur = (cur as? JsonObject)?.get(k) ?: return null
    }
    return cur
}

private fun JsonElement.jsonArrayFirstText(): String? {
    val arr = this as? JsonArray ?: return null
    val first = arr.firstOrNull() as? JsonObject ?: return null
    return (first["text"] as? JsonPrimitive)?.content
}

private fun JsonObject.thumbnails(): List<String> {
    val arr = path("thumbnail", "thumbnails") as? JsonArray ?: return emptyList()
    return arr.mapNotNull { (it as? JsonObject)?.get("url")?.jsonPrimitive?.content }
}

// endregion

// region ── log tag ──

/** 搜尋分頁可觀測性 log tag。 */
const val TAG = "SearchPaging"

// endregion

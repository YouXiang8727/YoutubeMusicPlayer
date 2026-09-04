package com.youxiang8727.mymediaplayer.core.data.remote

import android.util.Log
import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpRequest
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpResponse
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpTransport
import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 台灣熱門音樂榜單資料源（官方 YouTube Music playlist「台灣百大熱門音樂影片」）。
 *
 * 背景：舊 charts 資料鏈（`WEB_MUSIC_ANALYTICS` client ＋
 * `FEmusic_analytics_charts_home` browse）已於 2026-09 被 YouTube 汰除（恆 HTTP 400，
 * 且 charts.youtube.com 官方 `LAUNCHED_CHART_COUNTRIES` 不含 TW）。改走官方 YT Music
 * playlist，owner = YouTube Music Global Charts 官方頻道，100 首：
 * - playback list id：`PL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU`
 * - browseId = `VL` + id
 * - client = **ANDROID_VR**（免 poToken，與專案串流鏈同家族；版本易腐需一起監控）
 *
 * 依 A 實證規格，ANDROID_VR 的 UA / headers / `context.client` **直接在此 hardcode**
 * （見 [TrendingPlaylistDataSource.Companion]），**不共享** `InnerTubeClientProfiles`
 * 常數物件（避免跨檔變更風險）。ANDROID_VR 版本易腐，若失效需與串流鏈版本互相對照更新。
 *
 * 不走 Retrofit：直接以 [StreamHttpTransport]（串流鏈現有抽象）POST innerTube browse，
 * clean client 身份由 body + headers 自帶。**分頁聚合至整份**：
 * 抓頁 → 解析 `playlistVideoRenderer` → 累加去重 → 取續頁 token → 續頁，直到 token
 * 為 null 或達 [MAX_PAGES] 上限。HTTP 非 200 / 非合法 JSON / 重複 token 皆回 [Result.failure]。
 *
 * 錯誤殼（bot wall）：回應只有 `messageRenderer`/`pageHeaderRenderer` 空殼而無任何
 * `playlistVideoRenderer` → 視同「無內容」回傳空清單＋可觀測性 log。
 *
 * [parsePlaylistPage]、[collectPlaylistVideos]、[extractPlaylistContinuationToken]
 * 為 internal 純函數（不觸網），供 JVM 單元測試直接驗證。
 */
@Singleton
class TrendingPlaylistDataSource @Inject constructor(
    private val transport: StreamHttpTransport,
    private val dispatchers: DispatcherProvider
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 抓取指定區域的熱門音樂，**分頁聚合至整份**。
     * @param region 目前僅 [ChartRegion.TAIWAN]（無其他官方來源區域）。
     */
    suspend fun fetch(region: ChartRegion): Result<List<VideoResult>> = withContext(dispatchers.io) {
        runCatching {
            when (region) {
                ChartRegion.TAIWAN -> fetchTaiwanPlaylist()
            }
        }
    }

    /** 抓取台灣官方 playlist，分頁迴圈聚合至完整 100 首。 */
    private suspend fun fetchTaiwanPlaylist(): List<VideoResult> {
        val aggregated = mutableListOf<VideoResult>()
        val seenIds = mutableSetOf<String>()
        var token: String? = null
        var page = 1

        while (true) {
            val body = if (token == null) buildFirstPageBody() else buildContinuationBody(token)
            val rawJson = postJson(body)

            // 回應非合法 JSON → 直接 failure（前端可顯示錯誤，不與「空殼無內容」混淆）
            if (runCatching { json.parseToJsonElement(rawJson) }.isFailure) {
                throw IOException("回應非合法 JSON")
            }
            val parsed = parsePlaylistPage(json, rawJson)
            if (parsed.items.isEmpty()) {
                Log.i(TAG, "page=$page count=0 -> 錯誤殼或空頁，視同無內容停止")
                break
            }

            // 累加（distinctBy videoId，維持原順序）
            var newCount = 0
            for (item in parsed.items) {
                if (seenIds.add(item.videoId)) {
                    aggregated += item
                    newCount++
                }
            }
            Log.i(
                TAG,
                "page=$page count=${parsed.items.size} new=$newCount " +
                    "accum=${aggregated.size} next=${parsed.nextToken?.take(12) ?: "-"}"
            )

            val nextToken = parsed.nextToken
            // 重複 token：前進未推進 → 視為防禦停止（避免無限迴圈）
            if (nextToken != null && nextToken == token) {
                throw IOException("續頁 token 未推進（${nextToken.take(12)}），停止分頁")
            }
            if (nextToken == null || page >= MAX_PAGES) break

            token = nextToken
            page++
        }
        return aggregated
    }

    /** 以 StreamHttpTransport POST browse（回傳 raw JSON body）。非 200 拋錯。 */
    private suspend fun postJson(body: String): String {
        val response: StreamHttpResponse = transport.execute(
            StreamHttpRequest(
                url = BROWSE_ENDPOINT,
                method = "POST",
                headers = HEADERS,
                body = body
            )
        )
        if (response.code != 200) throw IOException("HTTP ${response.code}")
        return response.body ?: throw IOException("回應為空內容")
    }

    /** 首頁 body：ANDROID_VR context ＋ `browseId="VL" + PLAYLIST_ID`。 */
    private fun buildFirstPageBody(): String =
        "{\"context\":{\"client\":$ANDROID_VR_CLIENT_CONTEXT}," +
            "\"browseId\":\"VL$PLAYLIST_ID\"}"

    /** 續頁 body：同 context，但只有 continuation（**沒有** browseId）。 */
    private fun buildContinuationBody(token: String): String =
        "{\"context\":{\"client\":$ANDROID_VR_CLIENT_CONTEXT}," +
            "\"continuation\":\"$token\"}"

    companion object {
        private const val TAG = "Trending"
        private const val BROWSE_ENDPOINT = "https://www.youtube.com/youtubei/v1/browse"
        private const val PLAYLIST_ID = "PL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU"

        /** ANDROID_VR client `context.client`（A 實證規格；免 poToken，與串流鏈同家族，
         * 版本易腐需一起監控）。依規格在此 hardcode，不共享 `InnerTubeClientProfiles`
         * （避免跨檔變更風險）。 */
        private const val ANDROID_VR_CLIENT_CONTEXT =
            "{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.71.26\"," +
                "\"deviceMake\":\"Oculus\",\"deviceModel\":\"Quest 3\"," +
                "\"osName\":\"Android\",\"osVersion\":\"12\",\"androidSdkVersion\":32," +
                "\"hl\":\"zh-TW\",\"gl\":\"TW\"}"

        private const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.71.26 " +
                "(Linux; U; Android 12; US; Quest 3 Build/SQ3A.220605.009.A1) gzip"

        /** 依 A 實證規格 hardcode 的 headers（clean client 身份）。 */
        private val HEADERS: Map<String, String> = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to ANDROID_VR_USER_AGENT,
            "X-YouTube-Client-Name" to "28",
            "X-YouTube-Client-Version" to "1.71.26"
        )

        /** 分頁上限（100 首 = 5 頁；+1 容錯）。防壞回應無限迴圈。 */
        internal const val MAX_PAGES = 6
    }
}

/** 單頁解析結果：歌曲清單 ＋ 續頁 token（null = 已到底）。 */
internal data class PlaylistPage(
    val items: List<VideoResult>,
    val nextToken: String?
)

/**
 * 解析單頁 playlist browse response：全樹收集 `playlistVideoRenderer` 轉為 [VideoResult]，
 * 續頁 token 取自 `playlistVideoListRenderer.continuations[0].nextContinuationData.continuation`
 * （A 實測。**不是** `continuationItemRenderer`）。
 *
 * 空殼（僅 messageRenderer/pageHeaderRenderer 而無任何 playlistVideoRenderer）→ 回空清單。
 * JSON 非法 → 回空清單（由資料源迴圈視同無內容，或由測試端斷言）。
 */
internal fun parsePlaylistPage(json: Json, rawJson: String): PlaylistPage {
    val root: JsonElement = runCatching { json.parseToJsonElement(rawJson) }
        .getOrElse { return PlaylistPage(items = emptyList(), nextToken = null) }

    val renderers = mutableListOf<JsonObject>()
    collectPlaylistVideos(root, renderers)

    val items = renderers.mapNotNull { it.toPlaylistVideoResult() }
        .distinctBy { it.videoId }
    return PlaylistPage(
        items = items,
        nextToken = extractPlaylistContinuationToken(root)
    )
}

/** 遞迴走訪 JSON 樹，收集所有 `playlistVideoRenderer` 節點（維持全樹收集防版型微調）。 */
internal fun collectPlaylistVideos(element: JsonElement, out: MutableList<JsonObject>) {
    when (element) {
        is JsonObject -> {
            element["playlistVideoRenderer"]?.let { renderer ->
                if (renderer is JsonObject) out += renderer
            }
            element.values.forEach { collectPlaylistVideos(it, out) }
        }
        is JsonArray -> element.forEach { collectPlaylistVideos(it, out) }
        else -> Unit
    }
}

/**
 * 抽取續頁 token：找全樹第一個
 * `playlistVideoListRenderer.continuations[0].nextContinuationData.continuation`
 * （A 實測精確路徑；`playlistVideoListRenderer` 在樹中位置不定，故全樹走訪）。
 * token 含 `%3D` 結尾 URL 編碼字元，在 JSON body 內原樣嵌入安全。
 * 無此結構回 null（已到底）。
 */
internal fun extractPlaylistContinuationToken(root: JsonElement): String? {
    fun visit(node: JsonElement): String? {
        if (node is JsonObject) {
            (node["playlistVideoListRenderer"] as? JsonObject)?.let { list ->
                val continuation = (list["continuations"] as? JsonArray)
                    ?.firstOrNull() as? JsonObject
                continuation?.let { c ->
                    val token = (c["nextContinuationData"] as? JsonObject)
                        ?.get("continuation")
                    if (token is JsonPrimitive && token.isString && token.content.isNotBlank()) {
                        return token.content
                    }
                }
            }
            val found = node.values.mapNotNull { visit(it) }.firstOrNull()
            if (found != null) return found
        } else if (node is JsonArray) {
            val found = node.mapNotNull { visit(it) }.firstOrNull()
            if (found != null) return found
        }
        return null
    }
    return visit(root)
}

// region ── JSON 小工具（比照 YoutubeDataSource 的手動走訪風格）──

/** `playlistVideoRenderer` 欄位對應（A 實測結構）：
 * - `videoId`（直接字串）
 * - `title.runs[0].text`（歌名）
 * - `shortBylineText.runs[0].text`（歌手）
 * - `thumbnail.thumbnails[].url`（取第一個非空） */
private fun JsonObject.toPlaylistVideoResult(): VideoResult? {
    val videoId = str("videoId") ?: return null
    val title = path("title", "runs")?.runsFirstText() ?: ""
    val channel = path("shortBylineText", "runs")?.runsFirstText() ?: ""
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

private fun JsonObject.path(vararg keys: String): JsonElement? {
    var cur: JsonElement = this
    for (k in keys) {
        cur = (cur as? JsonObject)?.get(k) ?: return null
    }
    return cur
}

/** `runs[0].text`（innerTube 慣用結構）。 */
private fun JsonElement?.runsFirstText(): String? {
    val arr = this as? JsonArray ?: return null
    val first = arr.firstOrNull() as? JsonObject ?: return null
    return (first["text"] as? JsonPrimitive)?.content
}

private fun JsonObject.thumbnails(): List<String> {
    val arr = path("thumbnail", "thumbnails") as? JsonArray ?: return emptyList()
    return arr.mapNotNull { (it as? JsonObject)?.get("url")?.jsonPrimitive?.content }
}

// endregion

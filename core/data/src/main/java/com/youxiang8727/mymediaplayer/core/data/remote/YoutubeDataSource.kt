package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
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
 * 不使用 YouTube Data API Key：
 * 透過 Retrofit(OkHttp) 請求行動版搜尋頁，
 * 再以 kotlinx.serialization 解析內嵌的 ytInitialData JSON。
 */
@Singleton
class YoutubeDataSource @Inject constructor(
    private val api: YoutubeSearchApi,
    private val dispatchers: DispatcherProvider
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<VideoResult> = withContext(dispatchers.io) {
        val html = api.searchHtml(query)

        val rawJson = extractYtInitialData(html) ?: return@withContext emptyList()
        val root: JsonElement = runCatching { json.parseToJsonElement(rawJson) }
            .getOrElse { return@withContext emptyList() }

        val renderers = mutableListOf<JsonObject>()
        collectVideoRenderers(root, renderers)

        renderers.mapNotNull { it.toVideoResult() }
            .distinctBy { it.videoId }
            .take(30)
    }

    /** 以大括號配對方式擷取 ytInitialData 的完整 JSON 字串。 */
    private fun extractYtInitialData(html: String): String? {
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

    /** 遞迴走訪 JSON 樹，蒐集所有 videoRenderer 節點。 */
    private fun collectVideoRenderers(element: JsonElement, out: MutableList<JsonObject>) {
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
}

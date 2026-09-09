package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.di.SuggestionsProfile
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google suggestqueries 建議端點實作（無需 API Key）。
 *
 * 端點：`https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&hl=zh-TW&gl=TW&q=<query>`
 * 回傳 JSONP 包裝：`window.google.ac.h(["query", [["suggestion",0,[...]], ...], {...}])`。
 * 每個建議項目為 `[title, type, [terms...]]`，title 取子陣列 index 0。
 *
 * 使用乾淨的 `@SuggestionsProfile` OkHttpClient（短逾時 5s），
 * 不需瀏覽器 header；解析拆成 internal 純函數 [parseCompletionSuggestions] 供 JVM 單元測試。
 */
@Singleton
class YoutubeSearchSuggestionDataSource @Inject constructor(
    @SuggestionsProfile private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : SearchSuggestionDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun suggestions(query: String): List<String> = withContext(dispatchers.io) {
        runCatching {
            val url = buildUrl(query)
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val body = response.body?.string() ?: return@use emptyList<String>()
                parseCompletionSuggestions(json, body)
            }
        }.getOrElse { emptyList() }
    }

    /** 建構建議端點 URL；query 以 UTF-8 URL-encode。 */
    private fun buildUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://suggestqueries.google.com/complete/search" +
            "?client=youtube&ds=yt&hl=zh-TW&gl=TW&q=$encoded"
    }
}

/**
 * 解析 suggestqueries JSONP 回應為建議字串清單（純 JVM，可測試）。
 *
 * 輸入形如：`window.google.ac.h(["q",[["a",0,[512]],["b",0,[512]]],{"k":1}])`
 * 不具 name（key 前空白/引號變體）亦相容：`google.search.CompletionSearch([...])`。
 * 取首個 `(` 與最後 `)` 之間的字串解析為 JSON 陣列，再收集每個建議子陣列的 index 0 字串。
 * 解析失敗回空清單。
 */
internal fun parseCompletionSuggestions(json: Json, raw: String): List<String> {
    val start = raw.indexOf('(')
    val end = raw.lastIndexOf(')')
    if (start < 0 || end <= start) return emptyList()

    val inner = raw.substring(start + 1, end)
    val root: JsonElement = runCatching { json.parseToJsonElement(inner) }
        .getOrElse { return emptyList() }

    // root = [query, [ [title, type, terms], ... ], metadata]
    val suggestionsArray = (root as? JsonArray)?.getOrNull(1) as? JsonArray
        ?: return emptyList()

    return suggestionsArray.mapNotNull { item ->
        (item as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
    }.filter { it.isNotBlank() }.distinct()
}

private val JsonPrimitive.contentOrNull: String?
    get() = if (isString) content else null

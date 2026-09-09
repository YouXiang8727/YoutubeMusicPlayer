package com.youxiang8727.mymediaplayer.core.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 suggestqueries JSONP 回應解析（[parseCompletionSuggestions]）：
 * 涵蓋真實 `window.google.ac.h(...)` 形狀、CompletionSearch 變體與各種異常輸入。
 */
class YoutubeSearchSuggestionDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `解析標準 window google ac h 回應`() {
        val raw = """window.google.ac.h(["晴天",[["晴天",0,[512]],["晴天 周杰倫",0,[512,433]],["晴天的午後",0,[512]]],{"k":1}])"""

        val result = parseCompletionSuggestions(json, raw)

        assertEquals(listOf("晴天", "晴天 周杰倫", "晴天的午後"), result)
    }

    @Test
    fun `解析 CompletionSearch 變體`() {
        val raw = """google.search.CompletionSearch(["avicii",[["avicii",0],["avicii the nights",0],["avicii levels",0]],{}])"""

        val result = parseCompletionSuggestions(json, raw)

        assertEquals(listOf("avicii", "avicii the nights", "avicii levels"), result)
    }

    @Test
    fun `去重且過濾空白建議`() {
        val raw = """window.google.ac.h(["a",[["a",0,[512]],["a",0,[512]],["  ",0,[512]]],{}])"""

        val result = parseCompletionSuggestions(json, raw)

        assertEquals(listOf("a"), result)
    }

    @Test
    fun `無建議時回空清單`() {
        val raw = """window.google.ac.h(["zzzzz",[],{}])"""

        val result = parseCompletionSuggestions(json, raw)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `非 JSONP 輸入回空清單`() {
        assertTrue(parseCompletionSuggestions(json, "<html>consent wall</html>").isEmpty())
        assertTrue(parseCompletionSuggestions(json, "not jsonp").isEmpty())
    }

    @Test
    fun `JSON 不合法時回空清單`() {
        val raw = """window.google.ac.h([broken json])"""

        val result = parseCompletionSuggestions(json, raw)

        assertTrue(result.isEmpty())
    }
}
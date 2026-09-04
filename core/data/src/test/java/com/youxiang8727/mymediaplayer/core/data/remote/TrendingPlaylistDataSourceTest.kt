package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpRequest
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpResponse
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpTransport
import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 TrendingPlaylistDataSource：fake StreamHttpTransport 回傳 fixture JSON（不觸網）。
 * 確認 ① 分頁聚合（頁1+頁2 → 4 首、順序維持、去重） ② 續頁 body 走 continuation 非 browseId
 * ③ 首頁 body 含 browseId 與 ANDROID_VR context ④ headers 含 X-YouTube-Client-Name: 28
 * ⑤ 空殼 → 空清單 success ⑥ HTTP 400 → failure ⑦ 壞 JSON → failure ⑧ 重複 token → failure
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendingPlaylistDataSourceTest {

    private class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }

    /** 假傳輸層：依序回傳預排回應，並記錄每個請求供斷言。 */
    private class FakeTransport : StreamHttpTransport {
        val requests = mutableListOf<StreamHttpRequest>()
        val responses = ArrayDeque<Pair<Int, String>>()

        override suspend fun execute(request: StreamHttpRequest): StreamHttpResponse {
            requests += request
            val (code, body) = responses.removeFirst()
            return StreamHttpResponse(code, body)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun videoRenderer(id: String, title: String, channel: String): String = """
        {
          "videoId": "$id",
          "title": { "runs": [ { "text": "$title" } ] },
          "shortBylineText": { "runs": [ { "text": "$channel" } ] },
          "thumbnail": { "thumbnails": [ { "url": "https://thumb/$id" }, { "url": "https://thumb2/$id" } ] }
        }
    """.trimIndent()

    /** 首頁 fixture：2 首 + playlistVideoListRenderer.continuations[0].nextContinuationData.continuation */
    private fun page1Json(token: String = "TOKEN%3DABC") = """
        {
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "tabs": [ {
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "musicPlaylistShelfRenderer": {
                            "contents": [
                              { "playlistVideoRenderer": ${videoRenderer("vid1", "晴天", "周杰倫")} },
                              { "playlistVideoRenderer": ${videoRenderer("vid2", "七里香", "五月天")} }
                            ]
                          }
                        },
                        {
                          "playlistVideoListRenderer": {
                            "continuations": [
                              { "nextContinuationData": { "continuation": "$token" } }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              } ]
            }
          }
        }
    """.trimIndent()

    /** 續頁 fixture：2 首 + 無 continuation（已到底）。 */
    private fun page2Json() = """
        {
          "onResponseReceivedCommands": [ {
            "appendContinuationItemsAction": {
              "continuationItems": [
                { "playlistVideoRenderer": ${videoRenderer("vid3", "夜曲", "周杰倫")} },
                { "playlistVideoRenderer": ${videoRenderer("vid4", "稻香", "周杰倫")} }
              ]
            }
          } ]
        }
    """.trimIndent()

    /** 空殼（bot wall）fixture：僅 messageRenderer/pageHeaderRenderer，無 playlistVideoRenderer。 */
    private val shellJson = """
        {
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "tabs": [ {
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [
                        { "messageRenderer": { "text": { "runs": [ { "text": "Sign in to continue" } ] } } },
                        { "pageHeaderRenderer": { "pageTitle": "blocked" } }
                      ]
                    }
                  }
                }
              } ]
            }
          }
        }
    """.trimIndent()

    // ── parsePlaylistPage / collectPlaylistVideos / extractPlaylistContinuationToken 純函數 ──

    @Test
    fun `parsePlaylistPage 解析首頁：2 首 + 續頁 token`() {
        val page = parsePlaylistPage(json, page1Json("TOKEN%3DABC"))

        assertEquals(listOf("vid1", "vid2"), page.items.map { it.videoId })
        assertEquals("晴天", page.items[0].title)
        assertEquals("周杰倫", page.items[0].channel)
        assertEquals("https://thumb/vid1", page.items[0].thumbnailUrl)
        assertEquals("TOKEN%3DABC", page.nextToken)
    }

    @Test
    fun `parsePlaylistPage 空殼回傳空清單且無 token`() {
        val page = parsePlaylistPage(json, shellJson)

        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextToken)
    }

    @Test
    fun `parsePlaylistPage 壞 JSON 回傳空清單`() {
        val page = parsePlaylistPage(json, "{ not valid json")

        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextToken)
    }

    @Test
    fun `extractPlaylistContinuationToken 精確路徑`() {
        val root = json.parseToJsonElement(page1Json("TOKEN_X")).jsonObject
        assertEquals("TOKEN_X", extractPlaylistContinuationToken(root))
    }

    @Test
    fun `extractPlaylistContinuationToken 無 continuation 回 null`() {
        val root = json.parseToJsonElement(page2Json()).jsonObject
        assertEquals(null, extractPlaylistContinuationToken(root))
    }

    @Test
    fun `collectPlaylistVideos 全樹收集`() {
        val root = json.parseToJsonElement(page2Json()).jsonObject
        val out = mutableListOf<kotlinx.serialization.json.JsonObject>()
        collectPlaylistVideos(root, out)
        assertEquals(2, out.size)
    }

    // ── fetch()：分頁聚合 ──

    @Test
    fun `fetch 聚合跨頁 4 首、維持順序、續頁走 continuation`() = runTest {
        val transport = FakeTransport().apply {
            responses += 200 to page1Json("TOKEN%3DA")
            responses += 200 to page2Json()
        }
        val dataSource = TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val result = dataSource.fetch(ChartRegion.TAIWAN)

        assertTrue(result.isSuccess)
        assertEquals(listOf("vid1", "vid2", "vid3", "vid4"), result.getOrThrow().map { it.videoId })

        assertEquals(2, transport.requests.size)
        // 首頁 body：含 browseId 與 ANDROID_VR context
        val first = transport.requests[0]
        assertTrue(first.body.orEmpty().contains("VLPL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU"))
        assertTrue(first.body.orEmpty().contains("\"clientName\":\"ANDROID_VR\""))
        // 續頁 body：含 continuation 而非 browseId
        val second = transport.requests[1]
        assertTrue(second.body.orEmpty().contains("\"continuation\":\"TOKEN%3DA\""))
        assertTrue(!second.body.orEmpty().contains("browseId"))
    }

    @Test
    fun `首頁請求 headers 含 X-YouTube-Client-Name 28`() = runTest {
        val transport = FakeTransport().apply {
            responses += 200 to page1Json()
            responses += 200 to page2Json()
        }
        TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))
            .fetch(ChartRegion.TAIWAN)

        val first = transport.requests[0]
        assertEquals("28", first.headers["X-YouTube-Client-Name"])
        assertEquals("1.71.26", first.headers["X-YouTube-Client-Version"])
        assertTrue(first.headers.getValue("User-Agent").contains("com.google.android.apps.youtube.vr.oculus"))
    }

    @Test
    fun `跨頁重複 videoId 被去重且維持順序`() = runTest {
        // 頁1: vid1, vid2；頁2: vid2(重複), vid3
        val page2WithDup = """
            {
              "onResponseReceivedCommands": [ {
                "appendContinuationItemsAction": {
                  "continuationItems": [
                    { "playlistVideoRenderer": ${videoRenderer("vid2", "七里香", "五月天")} },
                    { "playlistVideoRenderer": ${videoRenderer("vid3", "夜曲", "周杰倫")} }
                  ]
                }
              } ]
            }
        """.trimIndent()
        val transport = FakeTransport().apply {
            responses += 200 to page1Json("TOKEN%3DA")
            responses += 200 to page2WithDup
        }
        val dataSource = TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val result = dataSource.fetch(ChartRegion.TAIWAN)

        assertTrue(result.isSuccess)
        assertEquals(listOf("vid1", "vid2", "vid3"), result.getOrThrow().map { it.videoId })
    }

    @Test
    fun `空殼回應回傳空清單 success`() = runTest {
        val transport = FakeTransport().apply { responses += 200 to shellJson }
        val dataSource = TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val result = dataSource.fetch(ChartRegion.TAIWAN)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `HTTP 非 200 回傳 failure`() = runTest {
        val transport = FakeTransport().apply { responses += 400 to "<html>blocked</html>" }
        val dataSource = TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val result = dataSource.fetch(ChartRegion.TAIWAN)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("400"))
    }

    @Test
    fun `壞 JSON 回傳 failure`() = runTest {
        val transport = FakeTransport().apply { responses += 200 to "{ not valid json" }
        val dataSource = TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val result = dataSource.fetch(ChartRegion.TAIWAN)

        assertTrue(result.isFailure)
    }

    @Test
    fun `重複 continuation token 停止並回傳 failure`() = runTest {
        // 頁1 給 token "TOKEN%3DA"；頁2 亦回傳相同的 token → 下一步續頁會是重複 token
        // （fetch 以 true 前進判斷：token 未變 → 防禦失敗）
        val sameToken = "TOKEN%3DREPEAT"
        val page2SameToken = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "musicPlaylistShelfRenderer": {
                        "contents": [
                          { "playlistVideoRenderer": ${videoRenderer("vid3", "夜曲", "周杰倫")} }
                        ]
                      }
                    },
                    {
                      "playlistVideoListRenderer": {
                        "continuations": [
                          { "nextContinuationData": { "continuation": "$sameToken" } }
                        ]
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val transport = FakeTransport().apply {
            responses += 200 to page1Json(sameToken)
            responses += 200 to page2SameToken
        }
        val dataSource = TrendingPlaylistDataSource(transport, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val result = dataSource.fetch(ChartRegion.TAIWAN)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("未推進"))
    }
}

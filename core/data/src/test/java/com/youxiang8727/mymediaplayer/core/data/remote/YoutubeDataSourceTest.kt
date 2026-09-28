package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.domain.model.SearchSort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 YoutubeDataSource 對 API 的委派與「續頁 token 回傳」round-trip：
 * fake YoutubeSearchApi 依收到的 continuation token 回傳對應 HTML/JSON（不觸網），
 * 確認初次搜尋走 GET、續頁走 innerTube POST、續頁結果與新 token 正確回傳。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YoutubeDataSourceTest {

    private class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }

    private class FakeYoutubeSearchApi : YoutubeSearchApi {
        data class GetCall(val query: String, val continuationToken: String?)
        data class PostCall(val clientName: String, val clientVersion: String, val body: String)

        val getCalls = mutableListOf<GetCall>()
        val postCalls = mutableListOf<PostCall>()
        var initialHtml: String = ""
        var continuationJson: String = ""

        override suspend fun searchHtml(query: String, continuationToken: String?): String {
            getCalls += GetCall(query, continuationToken)
            return if (continuationToken == null) initialHtml else continuationJson
        }

        override suspend fun searchInnerTube(
            clientName: String,
            clientVersion: String,
            body: RequestBody
        ): String {
            // 透過 okio Buffer 讀出 RequestBody 的實際 JSON 字串，供斷言續頁 token 有進 body
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            val bodyText = buffer.readUtf8()
            postCalls += PostCall(clientName, clientVersion, bodyText)
            return continuationJson
        }
    }

    private fun htmlAround(rawJson: String) =
        "<html><script>var ytInitialData = $rawJson;</script></html>"

    private val initialPageJson = """
        {
          "contents": {
            "twoColumnSearchResultsRenderer": {
              "primaryContents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "itemSectionRenderer": {
                        "contents": [
                          {
                            "videoRenderer": {
                              "videoId": "id1",
                              "title": { "simpleText": "影片 A" },
                              "ownerText": { "simpleText": "頻道 A" },
                              "thumbnail": { "thumbnails": [{ "url": "https://img/1" }] },
                              "lengthText": { "simpleText": "3:45" }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "continuationItemRenderer": {
                        "continuationEndpoint": {
                          "continuationCommand": {
                            "token": "TOKEN_PAGE_1",
                            "request": "CONTINUATION_REQUEST_TYPE_SEARCH"
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    /** innerTube POST 續頁 chunk：videoWithContextRenderer + 尾端 continuationItemRenderer。 */
    private val continuationChunkJson = """
        {
          "onResponseReceivedCommands": [
            {
              "appendContinuationItemsAction": {
                "continuationItems": [
                  {
                    "itemSectionRenderer": {
                      "contents": [
                        {
                          "videoWithContextRenderer": {
                            "navigationEndpoint": { "watchEndpoint": { "videoId": "id2" } },
                            "headline": { "runs": [{ "text": "影片 B" }] },
                            "shortBylineText": { "runs": [{ "text": "頻道 B" }] },
                            "thumbnail": { "thumbnails": [{ "url": "https://img/2" }] },
                            "thumbnailOverlayTimeStatusRenderer": { "text": { "simpleText": "2:22" } }
                          }
                        }
                      ]
                    }
                  },
                  {
                    "continuationItemRenderer": {
                      "continuationEndpoint": {
                        "continuationCommand": {
                          "token": "TOKEN_PAGE_2==",
                          "request": "CONTINUATION_REQUEST_TYPE_SEARCH"
                        }
                      }
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    /** 續頁回聲同一 token（不推進）的 chunk fixture：無新結果、token 與 sent 相同。 */
    private val echoChunkJson = """
        {
          "onResponseReceivedCommands": [
            {
              "appendContinuationItemsAction": {
                "continuationItems": [
                  {
                    "continuationItemRenderer": {
                      "continuationEndpoint": {
                        "continuationCommand": {
                          "token": "TOKEN_PAGE_1",
                          "request": "CONTINUATION_REQUEST_TYPE_SEARCH"
                        }
                      }
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `初次搜尋走 GET、不帶 token，回傳首頁結果與續頁 token`() = runTest {
        val api = FakeYoutubeSearchApi().apply { initialHtml = htmlAround(initialPageJson) }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page = dataSource.search("晴天")

        assertEquals(listOf("id1"), page.results.map { it.videoId })
        assertEquals("3:45", page.results[0].duration)
        assertEquals("TOKEN_PAGE_1", page.nextPageToken)
        assertEquals(FakeYoutubeSearchApi.GetCall("晴天", null), api.getCalls.single())
        assertTrue(api.postCalls.isEmpty())
    }

    @Test
    fun `續頁走 innerTube POST、回傳新結果與新 token`() = runTest {
        val api = FakeYoutubeSearchApi().apply {
            initialHtml = htmlAround(initialPageJson)
            continuationJson = continuationChunkJson
        }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page2 = dataSource.search("晴天", continuationToken = "TOKEN_PAGE_1")

        assertEquals(listOf("id2"), page2.results.map { it.videoId })
        assertEquals("2:22", page2.results[0].duration)
        assertEquals("TOKEN_PAGE_2==", page2.nextPageToken)
        // 續頁不應走 GET 續頁
        assertTrue(api.getCalls.none { it.continuationToken != null })
        // 續頁走 POST，body 內含續頁 token
        assertEquals(1, api.postCalls.size)
        val post = api.postCalls.single()
        assertEquals("2", post.clientName)
        assertTrue(post.body.contains("TOKEN_PAGE_1"))
        assertTrue(post.body.contains("\"continuation\":"))
        assertTrue(post.body.contains("\"clientName\":\"MWEB\""))
    }

    @Test
    fun `續頁回聲同 token（不推進）時 parse 對應出無新結果`() = runTest {
        val api = FakeYoutubeSearchApi().apply {
            continuationJson = echoChunkJson
        }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page = dataSource.search("晴天", continuationToken = "TOKEN_PAGE_1")

        // 回聲 chunk 無任何 videoWithContextRenderer：結果為空、token 仍回傳同一個（供上層偵測輪迴）
        assertTrue(page.results.isEmpty())
        assertEquals("TOKEN_PAGE_1", page.nextPageToken)
    }

    @Test
    fun `HTML 無 ytInitialData 時回傳空頁且無 token`() = runTest {
        val api = FakeYoutubeSearchApi().apply { initialHtml = "<html>consent wall</html>" }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page = dataSource.search("晴天")

        assertTrue(page.results.isEmpty())
        assertNull(page.nextPageToken)
    }

    // region ── 排序首頁的改版防禦（fallback）──

    /** 改版情境：innerTube 排序首頁改吐 GET 版 `videoRenderer`（無 videoWithContextRenderer）。 */
    private val shapeChangedToVideoRendererJson = """
        {
          "contents": {
            "twoColumnSearchResultsRenderer": {
              "primaryContents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "itemSectionRenderer": {
                        "contents": [
                          {
                            "videoRenderer": {
                              "videoId": "id9",
                              "title": { "simpleText": "改版後影片 I" },
                              "ownerText": { "simpleText": "頻道 I" },
                              "thumbnail": { "thumbnails": [{ "url": "https://img/9" }] },
                              "lengthText": { "simpleText": "4:04" }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "continuationItemRenderer": {
                        "continuationEndpoint": {
                          "continuationCommand": {
                            "token": "TOKEN_PAGE_1",
                            "request": "CONTINUATION_REQUEST_TYPE_SEARCH"
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `排序首頁改吐 videoRenderer 時 fallback 仍解析出結果與續頁 token`() = runTest {
        val api = FakeYoutubeSearchApi().apply { continuationJson = shapeChangedToVideoRendererJson }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page = dataSource.search("晴天", sort = SearchSort.LATEST)

        // 關鍵：沒有靜默回傳空結果——renderer 換 key 後仍能出結果
        assertEquals(listOf("id9"), page.results.map { it.videoId })
        assertEquals("改版後影片 I", page.results[0].title)
        assertEquals("頻道 I", page.results[0].channel)
        assertEquals("4:04", page.results[0].duration)
        // token 抽取不受 renderer 類型影響
        assertEquals("TOKEN_PAGE_1", page.nextPageToken)
    }

    @Test
    fun `兩種 renderer 同時存在時優先走 videoWithContextRenderer 不觸發 fallback`() = runTest {
        // 正常 chunk（id2）再混入一個 videoRenderer（id9）：應只取 videoWithContextRenderer
        val mixed = """
            {
              "onResponseReceivedCommands": [
                {
                  "appendContinuationItemsAction": {
                    "continuationItems": [
                      {
                        "itemSectionRenderer": {
                          "contents": [
                            {
                              "videoWithContextRenderer": {
                                "navigationEndpoint": { "watchEndpoint": { "videoId": "id2" } },
                                "headline": { "runs": [{ "text": "影片 B" }] },
                                "shortBylineText": { "runs": [{ "text": "頻道 B" }] },
                                "thumbnail": { "thumbnails": [{ "url": "https://img/2" }] },
                                "thumbnailOverlayTimeStatusRenderer": { "text": { "simpleText": "2:22" } }
                              }
                            },
                            {
                              "videoRenderer": {
                                "videoId": "id9",
                                "title": { "simpleText": "不該被取用" },
                                "ownerText": { "simpleText": "頻道 I" }
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val api = FakeYoutubeSearchApi().apply { continuationJson = mixed }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page = dataSource.search("晴天", sort = SearchSort.LATEST)

        assertEquals(listOf("id2"), page.results.map { it.videoId })
    }

    // endregion

    // region ── 搜尋排序（SearchSort → params）──

    @Test
    fun `預設 RELEVANCE 走既有 GET 路徑、不發任何 innerTube POST`() = runTest {
        val api = FakeYoutubeSearchApi().apply { initialHtml = htmlAround(initialPageJson) }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        dataSource.search("晴天")

        assertEquals(FakeYoutubeSearchApi.GetCall("晴天", null), api.getCalls.single())
        assertTrue(api.postCalls.isEmpty())
    }

    @Test
    fun `明確指定 RELEVANCE 與預設走同一條 GET 路徑（不帶 params）`() = runTest {
        val api = FakeYoutubeSearchApi().apply { initialHtml = htmlAround(initialPageJson) }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        dataSource.search("晴天", sort = SearchSort.RELEVANCE)

        assertEquals(FakeYoutubeSearchApi.GetCall("晴天", null), api.getCalls.single())
        assertTrue(api.postCalls.isEmpty())
    }

    @Test
    fun `LATEST 初次搜尋改走 innerTube POST 且 body 帶 EgQIBRAB 與 query`() = runTest {
        val api = FakeYoutubeSearchApi().apply { continuationJson = continuationChunkJson }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        val page = dataSource.search("晴天", sort = SearchSort.LATEST)

        // GET 路由不支援 params：排序時不得走 GET
        assertTrue(api.getCalls.isEmpty())
        val post = api.postCalls.single()
        assertTrue(post.body.contains("\"params\":\"EgQIBRAB\""))
        assertTrue(post.body.contains("\"query\":\"晴天\""))
        assertTrue(post.body.contains("\"clientName\":\"MWEB\""))
        // 排序首頁結果 renderer 為 videoWithContextRenderer，以 parseContinuationChunk 解析
        assertEquals(listOf("id2"), page.results.map { it.videoId })
        assertEquals("TOKEN_PAGE_2==", page.nextPageToken)
    }

    @Test
    fun `POPULAR 初次搜尋改走 innerTube POST 且 body 帶 EgQICRAB`() = runTest {
        val api = FakeYoutubeSearchApi().apply { continuationJson = continuationChunkJson }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        dataSource.search("晴天", sort = SearchSort.POPULAR)

        assertTrue(api.getCalls.isEmpty())
        assertTrue(api.postCalls.single().body.contains("\"params\":\"EgQICRAB\""))
    }

    @Test
    fun `排序初次搜尋的 body 不含 continuation 欄位`() = runTest {
        val api = FakeYoutubeSearchApi().apply { continuationJson = continuationChunkJson }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        dataSource.search("晴天", sort = SearchSort.LATEST)

        assertFalse(api.postCalls.single().body.contains("\"continuation\":"))
    }

    @Test
    fun `續頁即使指定 LATEST 仍不帶 params（排序由 continuation token 承載）`() = runTest {
        val api = FakeYoutubeSearchApi().apply { continuationJson = continuationChunkJson }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        dataSource.search("晴天", continuationToken = "TOKEN_PAGE_1", sort = SearchSort.LATEST)

        val post = api.postCalls.single()
        assertFalse(post.body.contains("\"params\":"))
        assertTrue(post.body.contains("\"continuation\":\"TOKEN_PAGE_1\""))
        assertTrue(post.body.contains("TOKEN_PAGE_1"))
    }

    @Test
    fun `排序初次搜尋時 query 含引號會被跳脫，body 仍為合法 JSON`() = runTest {
        val api = FakeYoutubeSearchApi().apply { continuationJson = continuationChunkJson }
        val dataSource = YoutubeDataSource(api, TestDispatcherProvider(UnconfinedTestDispatcher()))

        dataSource.search("say \"hi\"", sort = SearchSort.LATEST)

        val body = api.postCalls.single().body
        assertTrue(body.contains("\"query\":\"say \\\"hi\\\"\""))
        // 合法 JSON：不會因未跳脫而解析失敗
        assertEquals("EgQIBRAB", Json.parseToJsonElement(body).jsonObject["params"]?.jsonPrimitive?.content)
    }

    // endregion
}

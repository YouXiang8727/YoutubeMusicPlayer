package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
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

        override suspend fun searchContinuation(
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
                              "thumbnail": { "thumbnails": [{ "url": "https://img/1" }] }
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
                            "thumbnail": { "thumbnails": [{ "url": "https://img/2" }] }
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
}

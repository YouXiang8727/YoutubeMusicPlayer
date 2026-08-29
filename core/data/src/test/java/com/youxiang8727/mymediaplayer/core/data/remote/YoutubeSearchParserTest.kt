package com.youxiang8727.mymediaplayer.core.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驗證 YoutubeDataSource 的解析純函數（extractYtInitialData / parseYtInitialData /
 * extractContinuationToken）：以 fake HTML/JSON fixture 驗證 token 擷取與續頁邏輯，
 * 不觸網。fixture 形狀對照 2026-08 實測的 m.youtube.com 搜尋頁：
 * - 主續頁 token 位於 sectionListRenderer.contents 尾部的 continuationItemRenderer，
 *   其 continuationCommand.request == "CONTINUATION_REQUEST_TYPE_SEARCH"。
 * - filter chips 有各自的 continuationCommand，但位於 chipCloudChipRenderer 底下，
 *   不在 continuationItemRenderer 路徑上，不得誤取。
 */
class YoutubeSearchParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    // region fixtures

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
                              "title": { "runs": [{ "text": "影片 A" }] },
                              "ownerText": { "runs": [{ "text": "頻道 A" }] },
                              "thumbnail": { "thumbnails": [{ "url": "https://img/1" }] }
                            }
                          },
                          {
                            "videoRenderer": {
                              "videoId": "id2",
                              "title": { "simpleText": "影片 B" },
                              "longBylineText": { "runs": [{ "text": "頻道 B" }] },
                              "thumbnail": { "thumbnails": [{ "url": "https://img/2" }] }
                            }
                          },
                          {
                            "channelRenderer": {
                              "channelId": "UC_FAKE",
                              "title": { "simpleText": "某頻道" }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "continuationItemRenderer": {
                        "trigger": "CONTINUATION_TRIGGER_ON_ITEM_SHOWN",
                        "continuationEndpoint": {
                          "commandMetadata": {
                            "webCommandMetadata": { "sendPost": true, "apiUrl": "/youtubei/v1/search" }
                          },
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
          },
          "header": {
            "searchHeaderRenderer": {
              "chipBar": {
                "chipCloudRenderer": {
                  "chips": [
                    {
                      "chipCloudChipRenderer": { "text": { "simpleText": "All" }, "isSelected": true }
                    },
                    {
                      "chipCloudChipRenderer": {
                        "text": { "simpleText": "Shorts" },
                        "navigationEndpoint": {
                          "commandMetadata": {
                            "webCommandMetadata": { "sendPost": true, "apiUrl": "/youtubei/v1/search" }
                          },
                          "continuationCommand": {
                            "token": "CHIP_TOKEN_SHOULD_NOT_BE_USED"
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

    private val continuationPageJson = """
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
                              "videoId": "id3",
                              "title": { "runs": [{ "text": "影片 C" }] },
                              "ownerText": { "runs": [{ "text": "頻道 C" }] },
                              "thumbnail": { "thumbnails": [{ "url": "https://img/3" }] }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "continuationItemRenderer": {
                        "trigger": "CONTINUATION_TRIGGER_ON_ITEM_SHOWN",
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
            }
          }
        }
    """.trimIndent()

    private val lastPageJson = """
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
                              "title": { "runs": [{ "text": "最後一頁" }] },
                              "ownerText": { "runs": [{ "text": "頻道 Z" }] },
                              "thumbnail": { "thumbnails": [{ "url": "https://img/9" }] }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * innerTube POST 續頁 chunk fixture：`onResponseReceivedCommands[].appendContinuationItemsAction
     * .continuationItems[].itemSectionRenderer.contents[].videoWithContextRenderer`。
     * 欄位對應 2026-08 實測：
     * - videoId 位於 `navigationEndpoint.watchEndpoint.videoId`（非 renderer 根部）
     * - title 位於 `headline.runs[0].text`
     * - channel 位於 `shortBylineText.runs[0].text`
     */
    private val continuationChunkJson = """
        {
          "responseContext": { "serviceTrackingParams": [] },
          "onResponseReceivedCommands": [
            {
              "appendContinuationItemsAction": {
                "targetId": "search-feed",
                "continuationItems": [
                  {
                    "itemSectionRenderer": {
                      "contents": [
                        {
                          "videoWithContextRenderer": {
                            "navigationEndpoint": { "watchEndpoint": { "videoId": "id3" } },
                            "headline": { "runs": [{ "text": "續頁影片 C" }] },
                            "shortBylineText": { "runs": [{ "text": "續頁頻道 C" }] },
                            "thumbnail": { "thumbnails": [{ "url": "https://img/3" }] }
                          }
                        },
                        {
                          "videoWithContextRenderer": {
                            "navigationEndpoint": { "watchEndpoint": { "videoId": "id4" } },
                            "headline": { "simpleText": "續頁影片 D" },
                            "shortBylineText": { "runs": [{ "text": "續頁頻道 D" }] },
                            "thumbnail": { "thumbnails": [{ "url": "https://img/4" }] }
                          }
                        },
                        {
                          "gridPlaylistRenderer": { "playlistId": "PL_FAKE", "title": { "simpleText": "某播放清單" } }
                        }
                      ]
                    }
                  },
                  {
                    "continuationItemRenderer": {
                      "trigger": "CONTINUATION_TRIGGER_ON_ITEM_SHOWN",
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

    /** 續頁回聲同一 token（不推進）：chunk 內無任何 video renderer，token 與 sent 相同。 */
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

    // endregion

    // ── extractYtInitialData ──

    @Test
    fun `extractYtInitialData 從 HTML 抽出完整 JSON`() {
        val html = "<html><body><script>var ytInitialData = " +
            initialPageJson +
            ";</script></body></html>"

        val extracted = extractYtInitialData(html)

        assertEquals(initialPageJson, extracted)
    }

    @Test
    fun `extractYtInitialData 無 marker 回傳 null`() {
        assertNull(extractYtInitialData("<html>no data here</html>"))
    }

    // ── parseYtInitialData ──

    @Test
    fun `首頁解析取出全部 videoRenderer 與續頁 token`() {
        val page = parseYtInitialData(json, initialPageJson)

        // 每頁取全部 videoRenderer（channelRenderer 不計），無 take(30) 上限
        assertEquals(listOf("id1", "id2"), page.results.map { it.videoId })
        assertEquals("影片 A", page.results[0].title)
        assertEquals("頻道 A", page.results[0].channel)
        assertEquals("影片 B", page.results[1].title)
        assertEquals("頻道 B", page.results[1].channel)
        // 續頁 token 只認 continuationItemRenderer 路徑，filter chip 的 token 不會誤取
        assertEquals("TOKEN_PAGE_1", page.nextPageToken)
    }

    @Test
    fun `續頁解析取出該頁結果與新 token`() {
        val page = parseYtInitialData(json, continuationPageJson)

        assertEquals(listOf("id3"), page.results.map { it.videoId })
        assertEquals("TOKEN_PAGE_2==", page.nextPageToken)
    }

    @Test
    fun `最後一頁無 continuation 時 nextPageToken 為 null`() {
        val page = parseYtInitialData(json, lastPageJson)

        assertEquals(listOf("id9"), page.results.map { it.videoId })
        assertNull(page.nextPageToken)
    }

    @Test
    fun `非法 JSON 回傳空頁`() {
        val page = parseYtInitialData(json, "{ not valid json !!!")

        assertTrue(page.results.isEmpty())
        assertNull(page.nextPageToken)
    }

    // ── parseContinuationChunk（innerTube POST 續頁 chunk）──

    @Test
    fun `續頁 chunk 解析 videoWithContextRenderer 與新 token`() {
        val page = parseContinuationChunk(json, continuationChunkJson)

        // videoWithContextRenderer 對應為 VideoResult：videoId 取自 watchEndpoint、title 取自 headline
        assertEquals(listOf("id3", "id4"), page.results.map { it.videoId })
        assertEquals("續頁影片 C", page.results[0].title)
        assertEquals("續頁頻道 C", page.results[0].channel)
        assertEquals("續頁影片 D", page.results[1].title)
        assertEquals("TOKEN_PAGE_2==", page.nextPageToken)
    }

    @Test
    fun `續頁 chunk 無 videoWithContextRenderer 時結果為空但仍可取 token`() {
        val page = parseContinuationChunk(json, lastPageJson)

        // 首頁結構的 videoRenderer 不應被續頁解析器誤取
        assertTrue(page.results.isEmpty())
        assertNull(page.nextPageToken)
    }

    @Test
    fun `續頁回聲同 token 不推進時回傳同 token 且無新結果`() {
        val page = parseContinuationChunk(json, echoChunkJson)

        // 回聲 chunk：無 video renderer + token 原樣回傳（與 sent 相同），供上層偵測輪迴
        assertTrue(page.results.isEmpty())
        assertEquals("TOKEN_PAGE_1", page.nextPageToken)
    }

    @Test
    fun `續頁 chunk 非法 JSON 回傳空頁`() {
        val page = parseContinuationChunk(json, "not json")

        assertTrue(page.results.isEmpty())
        assertNull(page.nextPageToken)
    }

    @Test
    fun `續頁欄位對應與首頁不同（videoId 在 watchEndpoint、title 在 headline）`() {
        val onlyContinuationShape = """
            {
              "onResponseReceivedCommands": [
                { "appendContinuationItemsAction": { "continuationItems": [
                  { "itemSectionRenderer": { "contents": [
                    {
                      "videoWithContextRenderer": {
                        "navigationEndpoint": { "watchEndpoint": { "videoId": "c1" } },
                        "headline": { "runs": [{ "text": "Chunk 標題" }] },
                        "shortBylineText": { "runs": [{ "text": "Chunk 頻道" }] },
                        "thumbnail": { "thumbnails": [{ "url": "https://img/c1" }] }
                      }
                    }
                  ] } }
                ] } }
              ]
            }
        """.trimIndent()

        val page = parseContinuationChunk(json, onlyContinuationShape)

        assertEquals(listOf("c1"), page.results.map { it.videoId })
        assertEquals("Chunk 標題", page.results[0].title)
        assertEquals("Chunk 頻道", page.results[0].channel)
        assertEquals("https://img/c1", page.results[0].thumbnailUrl)
    }

    // ── extractContinuationToken ──

    @Test
    fun `同頁多個 continuationItemRenderer 時優先取 SEARCH_REQUEST`() {
        val root = buildJsonObject {
            put("contents", buildJsonObject {
                put("first", continuationItem("OTHER_TOKEN", "CONTINUATION_REQUEST_TYPE_BROWSE"))
                put("second", continuationItem("SEARCH_TOKEN", "CONTINUATION_REQUEST_TYPE_SEARCH"))
            })
        }

        assertEquals("SEARCH_TOKEN", extractContinuationToken(root))
    }

    @Test
    fun `多顆 search token 時選取第一顆（實測單頁僅一顆，first 與 rightmost 等價）`() {
        // 實測 m.youtube.com 搜尋每頁只會出現一顆 search token；此處以兩顆 fixture
        // 固定目前行為：優先取 SEARCH 類且取第一顆（非內部 echo／舊 token 的情形）。
        val root = buildJsonObject {
            put("contents", buildJsonObject {
                put("a", continuationItem("SEARCH_TOKEN_FIRST", "CONTINUATION_REQUEST_TYPE_SEARCH"))
                put("b", continuationItem("SEARCH_TOKEN_SECOND", "CONTINUATION_REQUEST_TYPE_SEARCH"))
            })
        }

        assertEquals("SEARCH_TOKEN_FIRST", extractContinuationToken(root))
    }

    @Test
    fun `只有非 SEARCH 的 continuationItemRenderer 時退回第一個 token`() {
        val root = buildJsonObject {
            put("contents", continuationItem("FALLBACK_TOKEN", null))
        }

        assertEquals("FALLBACK_TOKEN", extractContinuationToken(root))
    }

    @Test
    fun `完全沒有 continuationItemRenderer 時回傳 null`() {
        val root = buildJsonObject {
            put("contents", buildJsonObject {
                put("itemSectionRenderer", buildJsonObject { })
            })
        }

        assertNull(extractContinuationToken(root))
    }

    private fun continuationItem(token: String, request: String?): JsonObject = buildJsonObject {
        put("continuationItemRenderer", buildJsonObject {
            put("continuationEndpoint", buildJsonObject {
                put("continuationCommand", buildJsonObject {
                    put("token", token)
                    request?.let { put("request", it) }
                })
            })
        })
    }
}
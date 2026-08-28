package com.youxiang8727.mymediaplayer.core.data.remote.stream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeStreamSourceTest {

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

    private fun okPlayerResponse() = """
        {
          "playabilityStatus": {"status": "OK"},
          "streamingData": {
            "adaptiveFormats": [
              {"mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 70000, "url": "https://gvs/audio-low.m4a"},
              {"mimeType": "video/mp4; codecs=\"avc1.640028\"", "bitrate": 4000000, "url": "https://gvs/video.mp4"},
              {"mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "url": "https://gvs/audio-high.m4a"},
              {"mimeType": "audio/webm; codecs=\"opus\"", "bitrate": 160000, "url": "https://gvs/audio.webm"}
            ]
          }
        }
    """.trimIndent()

    private fun loginRequiredResponse() = """
        {
          "playabilityStatus": {
            "status": "LOGIN_REQUIRED",
            "reason": "Sign in to confirm that you're not a bot"
          }
        }
    """.trimIndent()

    @Test
    fun `IOS client 成功時取最高位元率的 m4a URL`() = runTest {
        val transport = FakeTransport().apply { responses += 200 to okPlayerResponse() }
        val fetcher = FakeVisitorDataFetcher("TEST_VD")

        val result = InnerTubeStreamSource(transport, fetcher).fetch("abc123")

        assertEquals("https://gvs/audio-high.m4a", result.getOrThrow())
        val request = transport.requests.single()
        assertTrue(request.url.contains("/youtubei/v1/player"))
        assertEquals("POST", request.method)
        assertTrue(request.headers.getValue("User-Agent").contains("com.google.ios.youtube/21.26.4"))
        assertTrue(request.body.orEmpty().contains("\"videoId\":\"abc123\""))
        // video/webm 與 audio/webm 不應入選；只挑 audio/mp4 中 bitrate 最高者
    }

    @Test
    fun `IOS 遭 LOGIN_REQUIRED 時改試 ANDROID_VR client`() = runTest {
        val transport = FakeTransport().apply {
            responses += 200 to loginRequiredResponse()
            responses += 200 to okPlayerResponse()
        }
        val fetcher = FakeVisitorDataFetcher("TEST_VD")

        val result = InnerTubeStreamSource(transport, fetcher).fetch("abc123")

        assertEquals("https://gvs/audio-high.m4a", result.getOrThrow())
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[1].body.orEmpty().contains("ANDROID_VR"))
    }

    @Test
    fun `所有 client 皆 LOGIN_REQUIRED 時失敗訊息含狀態碼`() = runTest {
        val transport = FakeTransport().apply {
            responses += 200 to loginRequiredResponse()
            responses += 200 to loginRequiredResponse()
        }
        val fetcher = FakeVisitorDataFetcher("TEST_VD")

        val result = InnerTubeStreamSource(transport, fetcher).fetch("abc123")

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("LOGIN_REQUIRED"))
        assertTrue(message.contains("IOS"))
        assertTrue(message.contains("ANDROID_VR"))
    }

    @Test
    fun `非 JSON 回應視為失敗`() = runTest {
        val transport = FakeTransport().apply { responses += 200 to "<html>blocked</html>" }
        val fetcher = FakeVisitorDataFetcher("TEST_VD")

        val result = InnerTubeStreamSource(transport, fetcher).fetch("abc123")

        assertTrue(result.isFailure)
    }

    @Test
    fun `IOS client 版本號與 UA 已更新至 21_26_4`() = runTest {
        val transport = FakeTransport().apply { responses += 200 to okPlayerResponse() }
        val fetcher = FakeVisitorDataFetcher("TEST_VD")

        InnerTubeStreamSource(transport, fetcher).fetch("test")

        val request = transport.requests.single()
        assertTrue(request.headers.getValue("User-Agent").contains("21.26.4"))
        assertTrue(request.headers.getValue("User-Agent").contains("iPhone16,2"))
        assertTrue(request.headers.getValue("X-YouTube-Client-Version") == "21.26.4")
        // body 中 context.client.clientVersion 也應為 21.26.4
        assertTrue(request.body.orEmpty().contains("\"clientVersion\":\"21.26.4\""))
        assertTrue(request.body.orEmpty().contains("\"deviceModel\":\"iPhone16,2\""))
    }

    @Test
    fun `ANDROID_VR client 版本號與 UA 已更新至 1_65_10`() = runTest {
        val transport = FakeTransport().apply {
            responses += 200 to loginRequiredResponse()  // IOS 失敗
            responses += 200 to okPlayerResponse()       // ANDROID_VR 成功
        }
        val fetcher = FakeVisitorDataFetcher("TEST_VD")

        InnerTubeStreamSource(transport, fetcher).fetch("test")

        val vrRequest = transport.requests[1]
        assertTrue(vrRequest.headers.getValue("User-Agent").contains("1.65.10"))
        assertTrue(vrRequest.headers.getValue("User-Agent").contains("Android 12L"))
        assertTrue(vrRequest.headers.getValue("X-YouTube-Client-Version") == "1.65.10")
        assertTrue(vrRequest.body.orEmpty().contains("\"clientVersion\":\"1.65.10\""))
        assertTrue(vrRequest.body.orEmpty().contains("\"osVersion\":\"12L\""))
    }

    @Test
    fun `請求 body 含 context_user_visitorData 當 visitorData 可用時`() = runTest {
        // 此測試驗證程式碼結構包含 visitorData 注入邏輯
        // 實際網路行為由整合測試覆蓋；這裡只確認 body 結構可序列化
        val transport = FakeTransport().apply { responses += 200 to okPlayerResponse() }
        val fetcher = FakeVisitorDataFetcher("TEST_VISITOR_DATA_VALUE")

        InnerTubeStreamSource(transport, fetcher).fetch("test")

        val request = transport.requests.single()
        val body = request.body.orEmpty()
        // 當 visitorData 非空時，應包含 user.visitorData 欄位
        assertTrue(body.contains("\"videoId\":\"test\""))
        assertTrue(body.contains("\"contentCheckOk\":true"))
        assertTrue(body.contains("\"racyCheckOk\":true"))
        assertTrue(body.contains("\"visitorData\":\"TEST_VISITOR_DATA_VALUE\""))
    }
}

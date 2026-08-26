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

        val result = InnerTubeStreamSource(transport).fetch("abc123")

        assertEquals("https://gvs/audio-high.m4a", result.getOrThrow())
        val request = transport.requests.single()
        assertTrue(request.url.contains("/youtubei/v1/player"))
        assertEquals("POST", request.method)
        assertTrue(request.headers.getValue("User-Agent").contains("com.google.ios.youtube"))
        assertTrue(request.body.orEmpty().contains("\"videoId\":\"abc123\""))
        // video/webm 與 audio/webm 不應入選；只挑 audio/mp4 中 bitrate 最高者
    }

    @Test
    fun `IOS 遭 LOGIN_REQUIRED 時改試 ANDROID_VR client`() = runTest {
        val transport = FakeTransport().apply {
            responses += 200 to loginRequiredResponse()
            responses += 200 to okPlayerResponse()
        }

        val result = InnerTubeStreamSource(transport).fetch("abc123")

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

        val result = InnerTubeStreamSource(transport).fetch("abc123")

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("LOGIN_REQUIRED"))
        assertTrue(message.contains("IOS"))
        assertTrue(message.contains("ANDROID_VR"))
    }

    @Test
    fun `非 JSON 回應視為失敗`() = runTest {
        val transport = FakeTransport().apply { responses += 200 to "<html>blocked</html>" }

        val result = InnerTubeStreamSource(transport).fetch("abc123")

        assertTrue(result.isFailure)
    }
}

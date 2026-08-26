package com.youxiang8727.mymediaplayer.core.data.remote.stream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipedStreamSourceTest {

    private class FakeTransport : StreamHttpTransport {
        var code: Int = 200
        var body: String? = null
        lateinit var lastRequest: StreamHttpRequest

        override suspend fun execute(request: StreamHttpRequest): StreamHttpResponse {
            lastRequest = request
            return StreamHttpResponse(code, body)
        }
    }

    @Test
    fun `解析 audioStreams 取最高位元率 m4a`() = runTest {
        val transport = FakeTransport().apply {
            body = """
                {
                  "title": "test",
                  "audioStreams": [
                    {"url": "https://proxy/low.m4a", "format": "M4A", "quality": "64 kbps", "mimeType": "audio/mp4", "bitrate": 64000},
                    {"url": "https://proxy/high.m4a", "format": "M4A", "quality": "128 kbps", "mimeType": "audio/mp4", "bitrate": 128000},
                    {"url": "https://proxy/audio.webm", "format": "WEBMA_OPUS", "quality": "160 kbps", "mimeType": "audio/webm", "bitrate": 160000}
                  ]
                }
            """.trimIndent()
        }

        val result = PipedStreamSource(transport).fetch("abc123")

        assertEquals("https://proxy/high.m4a", result.getOrThrow())
        assertTrue(transport.lastRequest.url.endsWith("/streams/abc123"))
    }

    @Test
    fun `HTTP 5xx 時失敗且訊息含狀態碼`() = runTest {
        val transport = FakeTransport().apply { code = 503 }

        val result = PipedStreamSource(transport).fetch("abc123")

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 503"))
    }

    @Test
    fun `回應缺 audioStreams 時視為失敗`() = runTest {
        val transport = FakeTransport().apply { body = """{"title":"no streams"}""" }

        val result = PipedStreamSource(transport).fetch("abc123")

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("audioStreams"))
    }
}

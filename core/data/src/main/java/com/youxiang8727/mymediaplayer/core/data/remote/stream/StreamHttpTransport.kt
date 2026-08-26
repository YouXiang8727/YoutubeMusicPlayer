package com.youxiang8727.mymediaplayer.core.data.remote.stream

/**
 * 可注入的 HTTP 傳輸層抽象。
 *
 * 存在目的：讓 InnerTube／Piped 的「請求建構＋JSON 解析」邏輯可以在純 JVM 單元測試中，
 * 以 Fake transport 模擬回應，不需真網路。
 */
interface StreamHttpTransport {
    suspend fun execute(request: StreamHttpRequest): StreamHttpResponse
}

data class StreamHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class StreamHttpResponse(
    val code: Int,
    val body: String?
)

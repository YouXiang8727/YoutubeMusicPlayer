package com.youxiang8727.mymediaplayer.core.data.remote.stream

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 最後一層 fallback：Piped 公開實例 API（`GET /streams/{videoId}`）。
 *
 * 已知限制（2026 現況）：公開實例的 IP 常遭 YouTube 封鎖、過載頻繁，
 * 「works until it doesn't」——只當 NewPipe 與 InnerTube 都失效時的最後手段，
 * 其串流 URL 亦經實例代理（頻寬走第三方）。實例失效時更換 [DEFAULT_API_BASE]。
 */
@Singleton
class PipedStreamSource @Inject constructor(
    private val transport: StreamHttpTransport
) : AudioStreamSource {

    override val name: String = "Piped"

    override suspend fun fetch(videoId: String): Result<String> = runCatching {
        val response = transport.execute(
            StreamHttpRequest(url = "$DEFAULT_API_BASE/streams/$videoId")
        )
        if (response.code != 200) throw IOException("HTTP ${response.code}（實例可能過載或被封鎖）")
        parseAudioUrl(response.body ?: throw IOException("回應為空內容"))
    }

    internal fun parseAudioUrl(body: String): String {
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw IOException("回應非合法 JSON")

        val streams = root["audioStreams"] as? kotlinx.serialization.json.JsonArray
            ?: throw IOException("回應缺少 audioStreams")

        val best = streams.asSequence()
            .mapNotNull { it as? JsonObject }
            .filter { (it["mimeType"] as? JsonPrimitive)?.content?.contains("audio/mp4") == true }
            .maxByOrNull { entry ->
                (entry["bitrate"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            } ?: throw IOException("audioStreams 無可用之 audio/mp4 串流")

        return (best["url"] as? JsonPrimitive)?.takeIf { it.content.isNotBlank() }?.content
            ?: throw IOException("音訊串流缺 URL")
    }

    companion object {
        /** Piped 官方主要 API 實例；失效時改用 TeamPiped/Piped wiki Instances 清單上的健康實例。 */
        const val DEFAULT_API_BASE = "https://pipedapi.kavin.rocks"
    }
}

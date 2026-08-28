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
 *
 * 2026-08 更新：優先選擇有 CDN、支援 audio/mp4、亞洲/台灣延遲低的實例。
 * 參考：TeamPiped/Piped Wiki Instances、piped.status 監控頁。
 */
@Singleton
class PipedStreamSource @Inject constructor(
    private val transport: StreamHttpTransport
) : AudioStreamSource {

    override val name: String = "Piped"

    override suspend fun fetch(videoId: String): Result<String> = runCatching {
        // 嘗試主實例，失效時輪換備援
        val instances = listOf(DEFAULT_API_BASE, FALLBACK_INSTANCE_1, FALLBACK_INSTANCE_2)
        var lastError: Throwable? = null
        for (base in instances) {
            try {
                val response = transport.execute(
                    StreamHttpRequest(url = "$base/streams/$videoId")
                )
                if (response.code == 200) {
                    return@runCatching parseAudioUrl(response.body ?: throw IOException("回應為空內容"))
                }
                lastError = IOException("HTTP ${response.code}（實例可能過載或被封鎖）")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("所有 Piped 實例皆失敗")
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
        /** Piped 官方主要 API 實例（有 CDN、多地區含台灣/亞洲）；失效時改用備援。 */
        const val DEFAULT_API_BASE = "https://pipedapi.kavin.rocks"
        /** 備援 1：syncpundit.io（有 CDN、US/India/UK/Japan 節點、支援 audio/mp4） */
        const val FALLBACK_INSTANCE_1 = "https://pipedapi.syncpundit.io"
        /** 備援 2：mha.fi（有 CDN、Finland 節點、支援 audio/mp4） */
        const val FALLBACK_INSTANCE_2 = "https://api-piped.mha.fi"
    }
}

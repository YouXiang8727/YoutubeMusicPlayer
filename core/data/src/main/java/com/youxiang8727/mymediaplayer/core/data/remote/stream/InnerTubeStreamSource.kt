package com.youxiang8727.mymediaplayer.core.data.remote.stream

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 直接呼叫 YouTube InnerTube player API（`POST /youtubei/v1/player`）取得音訊串流 URL。
 *
 * 背景：YouTube 對匿名 IP 的 bot 偵測封鎖（LOGIN_REQUIRED）主要卡在 WEB 系 client 的
 * po_token 政策；截至 2026 年中，IOS 與 ANDROID_VR client 仍可免 poToken 取得
 * plain（未加密）的 adaptiveFormats URL。client 版本號屬「易腐常數」，
 * 失效時優先懷疑版本被 YouTube 淘汰 → 更新 [IOS]／[ANDROID_VR] 的版本與 UA。
 *
 * 嘗試順序：IOS → ANDROID_VR（ANDROID client 已於 2026 年初被 YouTube 淘汰，不採用）。
 */
@Singleton
class InnerTubeStreamSource @Inject constructor(
    private val transport: StreamHttpTransport
) : AudioStreamSource {

    override val name: String = "InnerTube"

    private data class ClientProfile(
        val id: String,
        val userAgent: String,
        val clientIndexHeader: String,
        val contextClient: JsonObject
    )

    private val profiles = listOf(IOS, ANDROID_VR)

    override suspend fun fetch(videoId: String): Result<String> {
        val failures = mutableListOf<String>()
        for (profile in profiles) {
            fetchWith(profile, videoId)
                .onSuccess { return Result.success(it) }
                .onFailure { failures += "${profile.id}: ${it.message}" }
        }
        return Result.failure(IOException("所有 InnerTube client 皆失敗（${failures.joinToString("; ")}）"))
    }

    private suspend fun fetchWith(profile: ClientProfile, videoId: String): Result<String> =
        runCatching {
            val response = transport.execute(
                StreamHttpRequest(
                    url = PLAYER_ENDPOINT,
                    method = "POST",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "User-Agent" to profile.userAgent,
                        "X-YouTube-Client-Name" to profile.clientIndexHeader,
                        "X-YouTube-Client-Version" to (profile.contextClient["clientVersion"] as JsonPrimitive).content
                    ),
                    body = buildJsonObject {
                        put("context", buildJsonObject { put("client", profile.contextClient) })
                        put("videoId", videoId)
                        put("contentCheckOk", true)
                        put("racyCheckOk", true)
                    }.toString()
                )
            )
            if (response.code != 200) throw IOException("HTTP ${response.code}")
            response.body?.takeIf { it.isNotBlank() } ?: throw IOException("回應為空內容")
        }.mapCatching { body -> parseAudioUrl(body) }

    /** 解析 player 回應：playabilityStatus 需 OK，取 audio/mp4 中位元率最高者的 plain URL。 */
    internal fun parseAudioUrl(body: String): String {
        val root = runCatching { Json.parseToJsonElement(body).jsonObjectOrNull() }
            .getOrNull() ?: throw IOException("回應非合法 JSON")

        val status = root.primitive("playabilityStatus", "status")
        val reason = root.primitive("playabilityStatus", "reason")
        if (status != "OK") {
            throw IOException(
                "playabilityStatus=${status ?: "UNKNOWN"}${reason?.let { "（$it）" } ?: ""}"
            )
        }

        val formats = root["streamingData"].jsonObjectOrNull()
            ?.get("adaptiveFormats").jsonArrayOrNull()
            ?: throw IOException("回應缺少 streamingData.adaptiveFormats")

        // 只取自帶 plain url 且為 audio/mp4（m4a 相容性最佳）的項目；
        // 帶 signatureCipher 而無 url 的項目需 JS 簽章解密，直接略過。
        val best = formats.asSequence()
            .mapNotNull { it.jsonObjectOrNull() }
            .filter { it.primitive("mimeType")?.contains("audio/mp4") == true }
            .filter { !it.primitive("url").isNullOrBlank() }
            .maxByOrNull { it.longOrZero("bitrate") }
            ?: throw IOException("adaptiveFormats 無可用之未加密音訊串流")

        return best.primitive("url") ?: throw IOException("音訊串流缺 URL")
    }

    // region JSON 小工具（比照 YoutubeDataSource 的手動走訪風格）

    private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonObject.primitive(vararg path: String): String? {
        var cur: JsonObject = this
        for (i in 0 until path.size - 1) {
            cur = cur[path[i]] as? JsonObject ?: return null
        }
        return (cur[path.last()] as? JsonPrimitive)?.takeIf { it.content.isNotBlank() }?.content
    }

    private fun JsonObject.longOrZero(key: String): Long =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    // endregion

    private companion object {
        const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player"

        /**
         * IOS client：免 poToken、免 API key（以 UA + clientVersion 驗證）。
         * 版本易腐：失效時對照 yt-dlp / NewPipeExtractor 最新使用的 iOS 版本號更新。
         */
        val IOS = ClientProfile(
            id = "IOS",
            userAgent = "com.google.ios.youtube/20.49.6 (iPhone17,2; U; CPU iOS 18_4_1 like Mac OS X)",
            clientIndexHeader = "5",
            contextClient = buildJsonObject {
                put("clientName", "IOS")
                put("clientVersion", "20.49.6")
                put("deviceMake", "Apple")
                put("deviceModel", "iPhone17,2")
                put("osName", "iOS")
                put("osVersion", "18.4.1.22E219")
                put("hl", "zh-TW")
                put("gl", "TW")
            }
        )

        /** ANDROID_VR client：免 poToken、免 JS 簽章、plain URL。 */
        val ANDROID_VR = ClientProfile(
            id = "ANDROID_VR",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.71.26 " +
                "(Linux; U; Android 12; US; Quest 3 Build/SQ3A.220605.009.A1) gzip",
            clientIndexHeader = "28",
            contextClient = buildJsonObject {
                put("clientName", "ANDROID_VR")
                put("clientVersion", "1.71.26")
                put("deviceMake", "Oculus")
                put("deviceModel", "Quest 3")
                put("osName", "Android")
                put("osVersion", "12")
                put("androidSdkVersion", 32)
                put("hl", "zh-TW")
                put("gl", "TW")
            }
        )
    }
}

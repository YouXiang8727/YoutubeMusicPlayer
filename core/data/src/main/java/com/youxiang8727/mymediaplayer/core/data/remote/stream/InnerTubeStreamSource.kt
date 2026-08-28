package com.youxiang8727.mymediaplayer.core.data.remote.stream

import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * 2026-08 新增：所有 client（含 IOS/ANDROID_VR）在高風險 IP 需送 visitorData。
 * 實作：GET www.youtube.com 解析 ytcfg.set('VISITOR_DATA', '...')，快取 24 小時，
 * 注入 context.user.visitorData 並帶上 Cookie VISITOR_DATA/VISITOR_INFO1_LIVE。
 * 失敗時只記 warning 不阻斷主流程。
 *
 * 嘗試順序：IOS → ANDROID_VR（ANDROID client 已於 2026 年初被 YouTube 淘汰，不採用）。
 */
@Singleton
class InnerTubeStreamSource @Inject constructor(
    private val transport: StreamHttpTransport,
    private val visitorDataFetcher: VisitorDataFetcher
) : AudioStreamSource {

    override val name: String = "InnerTube"

    private data class ClientProfile(
        val id: String,
        val userAgent: String,
        val clientIndexHeader: String,
        val contextClient: JsonObject
    )

    private val profiles = listOf(IOS, ANDROID_VR)

    /** visitorData 快取：key = clientProfile.id，value = Pair(visitorData, cookieHeader) */
    private val visitorDataCache = ConcurrentHashMap<String, Pair<String, String>>()

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
            val visitorData = visitorDataFetcher.fetchVisitorData(profile.userAgent, profile.id)
            val (cookieHeader, body) = buildRequestBody(profile, videoId, visitorData)

            val response = transport.execute(
                StreamHttpRequest(
                    url = PLAYER_ENDPOINT,
                    method = "POST",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "User-Agent" to profile.userAgent,
                        "X-YouTube-Client-Name" to profile.clientIndexHeader,
                        "X-YouTube-Client-Version" to (profile.contextClient["clientVersion"] as JsonPrimitive).content,
                        "Cookie" to cookieHeader
                    ),
                    body = body
                )
            )
            if (response.code != 200) throw IOException("HTTP ${response.code}")
            response.body?.takeIf { it.isNotBlank() } ?: throw IOException("回應為空內容")
        }.mapCatching { body -> parseAudioUrl(body) }

    /** 建構請求 body 並注入 visitorData；回傳 Pair(cookieHeader, jsonBody) */
    private fun buildRequestBody(profile: ClientProfile, videoId: String, visitorData: String): Pair<String, String> {
        val clientJson = buildJsonObject {
            // 手動複製 profile.contextClient 的所有屬性（kotlinx.serialization 無 putAll）
            profile.contextClient.forEach { (key, value) -> put(key, value) }
            if (visitorData.isNotBlank()) {
                put("visitorData", visitorData)
            }
        }
        val body = buildJsonObject {
            put("context", buildJsonObject {
                put("client", clientJson)
                if (visitorData.isNotBlank()) {
                    put("user", buildJsonObject { put("visitorData", visitorData) })
                }
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()
        val cookieHeader = visitorDataCache[profile.id]?.second ?: ""
        return cookieHeader to body
    }

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
         * 2026-08-19 同步 yt-dlp 2026.08.19：clientVersion 21.26.4, iPhone16,2, iOS 18.3.2
         */
        val IOS = ClientProfile(
            id = "IOS",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            clientIndexHeader = "5",
            contextClient = buildJsonObject {
                put("clientName", "IOS")
                put("clientVersion", "21.26.4")
                put("deviceMake", "Apple")
                put("deviceModel", "iPhone16,2")
                put("osName", "iPhone")
                put("osVersion", "18.3.2.22D82")
                put("hl", "zh-TW")
                put("gl", "TW")
            }
        )

        /** ANDROID_VR client：免 poToken、免 JS 簽章、plain URL。
         *  2026-08-19 同步 yt-dlp 2026.08.19：clientVersion 1.65.10, Quest 3, Android 12L
         *  注意：yt-dlp 2026.08.17 後 ALL formats 含 HLS 都需 PO token，
         *  但我們只取 adaptiveFormats 中的 plain URL (audio/mp4)，暫時仍可用。
         */
        val ANDROID_VR = ClientProfile(
            id = "ANDROID_VR",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            clientIndexHeader = "28",
            contextClient = buildJsonObject {
                put("clientName", "ANDROID_VR")
                put("clientVersion", "1.65.10")
                put("deviceMake", "Oculus")
                put("deviceModel", "Quest 3")
                put("osName", "Android")
                put("osVersion", "12L")
                put("androidSdkVersion", 32)
                put("hl", "zh-TW")
                put("gl", "TW")
            }
        )
    }
}

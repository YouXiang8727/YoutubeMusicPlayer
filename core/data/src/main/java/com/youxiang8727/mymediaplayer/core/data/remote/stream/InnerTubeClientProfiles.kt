package com.youxiang8727.mymediaplayer.core.data.remote.stream

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 免 poToken 的 InnerTube client 身分 profile（易腐常數集中管理）。
 *
 * 供串流解析鏈（[InnerTubeStreamSource]）使用，避免同一份 client 版本/UA/context 常數
 * 在串流來源間漂移。熱門榜單資料源（TrendingPlaylistDataSource）依 A 實證規格在自身
 * hardcode own ANDROID_VR profile，**不共用**本常數（避免跨檔變更風險）——需監控時
 * 兩處版本常數互相對照更新。
 *
 * 使用背景：YouTube 對匿名 IP 的 bot 封鎖主要卡在 WEB 系 client 的 po_token 政策；
 * IOS 與 ANDROID_VR client 仍可免 poToken。版本號屬**易腐常數**，失效時優先懷疑被
 * YouTube 淘汰 → 更新對應 profile 的版本與 UA。
 *
 * @property id client 邏輯名稱（供錯誤訊息／測試引用）
 * @property userAgent HTTP `User-Agent` header
 * @property clientIndexHeader HTTP `X-YouTube-Client-Name` header
 * @property contextClient request body `context.client` JsonObject
 */
internal data class ClientProfile(
    val id: String,
    val userAgent: String,
    val clientIndexHeader: String,
    val contextClient: JsonObject
)

/**
 * 共用 innerTube client 身分常數。
 */
internal object InnerTubeClientProfiles {

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

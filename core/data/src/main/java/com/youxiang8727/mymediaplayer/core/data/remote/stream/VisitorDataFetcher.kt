package com.youxiang8727.mymediaplayer.core.data.remote.stream

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 抓取 YouTube visitorData 的介面。
 *
 * 實作：GET www.youtube.com 解析 ytcfg.set('VISITOR_DATA', '...')，
 * 並提取 Set-Cookie 中的 VISITOR_DATA / VISITOR_INFO1_LIVE。
 * 失敗時回傳空字串（不拋例外，由呼叫端決定是否阻斷）。
 *
 * 為了可測性，定義為 interface；生產環境用 [OkHttpVisitorDataFetcher]，
 * 測試用 [FakeVisitorDataFetcher]。
 */
interface VisitorDataFetcher {
    /**
     * 取得指定 client profile 的 visitorData。
     *
     * @param userAgent 用於請求的 User-Agent（需與後續 InnerTube 請求一致）
     * @param profileId 快取 key（通常為 client profile id，如 "IOS"、"ANDROID_VR"）
     * @return visitorData 字串；失敗或解析不到時回傳空字串
     */
    suspend fun fetchVisitorData(userAgent: String, profileId: String): String
}

/** 預設實作：使用 OkHttp 抓取 YouTube 首頁並解析 visitorData。 */
class OkHttpVisitorDataFetcher @Inject constructor(
    private val dispatcher: CoroutineDispatcher,
    private val okHttpClient: okhttp3.OkHttpClient
) : VisitorDataFetcher {

    private val YOUTUBE_HOME = "https://www.youtube.com"
    private val TAG = "OkHttpVisitorDataFetcher"
    private val VISITOR_DATA_TTL_MS = 24 * 60 * 60 * 1000L // 24 小時
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    override suspend fun fetchVisitorData(userAgent: String, profileId: String): String {
        val now = System.currentTimeMillis()
        cache[profileId]?.let { (data, expiresAt) ->
            if (now < expiresAt) return data
        }
        return withContext(dispatcher) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(YOUTUBE_HOME)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    val html = resp.body?.string() ?: return@withContext ""
                    val visitorData = parseVisitorData(html)
                    if (visitorData.isNotBlank()) {
                        val cookieHeader = extractVisitorCookies(resp)
                        cache[profileId] = visitorData to (now + VISITOR_DATA_TTL_MS)
                        android.util.Log.d(TAG, "Fetched visitorData for $profileId")
                    }
                    visitorData
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to fetch visitorData for $profileId: ${e.message}")
                ""
            }
        }
    }

    private fun parseVisitorData(html: String): String {
        val patterns = listOf(
            """ytcfg\.set\s*\(\s*['"]VISITOR_DATA['"]\s*,\s*['"]([^'"]+)['"]""".toRegex(),
            """['"]VISITOR_DATA['"]\s*:\s*['"]([^'"]+)['"]""".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return ""
    }

    private fun extractVisitorCookies(response: okhttp3.Response): String {
        val cookies = mutableListOf<String>()
        response.headers("Set-Cookie").forEach { cookie ->
            if (cookie.startsWith("VISITOR_DATA=") || cookie.startsWith("VISITOR_INFO1_LIVE=")) {
                cookies += cookie.split(";")[0]
            }
        }
        return cookies.joinToString("; ")
    }
}

/** 測試用假實作：直接回傳預設值。 */
class FakeVisitorDataFetcher(
    private val visitorData: String = "TEST_VISITOR_DATA"
) : VisitorDataFetcher {
    override suspend fun fetchVisitorData(userAgent: String, profileId: String): String = visitorData
}
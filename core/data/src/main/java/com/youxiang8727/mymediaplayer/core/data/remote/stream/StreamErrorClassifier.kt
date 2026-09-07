package com.youxiang8727.mymediaplayer.core.data.remote.stream

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析失敗的錯誤映射器：把各來源拋出的例外訊息分類成 [StreamFailureKind]，
 * 並負責把整條 fallback 鏈的嘗試結果聚合成一則使用者可讀的錯誤描述
 * （該描述會顯示在媒體通知上，見 MusicService.runBlockingResolve）。
 *
 * 抽成可注入結構：FallbackStreamResolver 測試可直接以真實實例驗證映射規則。
 *
 * 403 歸類決策：raw HTTP 403 可能來自「bot 封鎖被 WAF 以 403 擋下」、「簽名 URL 過期／
 * 被 YouTube 撤銷」或「WAF 拒絕」。這些本質上都**可重試**（換來源、清除快取強制重解析）
 * 或可透過更換網路/稍後改善，**不應**歸為 [StreamFailureKind.PERMANENT]（永久結構性失敗）。
 * 因 403 最常見於內容 URL 過期／WAF 拒絕（跟「暫時性」高度重疊），故歸 [TRANSIENT]；
 * 若同時出現 bot 封鎖關鍵字（如 playabilityStatus=LOGIN_REQUIRED）則優先歸 [BOT_BLOCK]。
 */
@Singleton
class StreamErrorClassifier @Inject constructor() {

    fun classify(errorMessage: String?): StreamFailureKind {
        if (errorMessage == null) return StreamFailureKind.TRANSIENT
        val lower = errorMessage.lowercase()
        return when {
            BOT_KEYWORDS.any { lower.contains(it) } -> StreamFailureKind.BOT_BLOCK
            // raw HTTP 403（InnerTube/Piped 的非 200 統一以 "HTTP <code>" 拋出，
            // 或 content URL 播放當下被 DefaultHttpDataSource 以 "HTTP 403" InvalidResponseCodeException 浮出）。
            // 403 本質可重試／換網路，不屬永久失敗，歸 TRANSIENT（見類別註解決策）。
            HTTP_403_REGEX.containsMatchIn(lower) -> StreamFailureKind.TRANSIENT
            NETWORK_KEYWORDS.any { lower.contains(it) } -> StreamFailureKind.TRANSIENT
            else -> StreamFailureKind.PERMANENT
        }
    }

    /**
     * 聚合所有來源的失敗結果，依分類給出不同的使用者可讀提示。
     * @param attempts 各來源（名稱, 例外）的嘗試紀錄，依嘗試順序
     */
    fun describe(attempts: List<Pair<String, Throwable>>): String {
        val sawBotBlock = attempts.any { classify(it.second.message) == StreamFailureKind.BOT_BLOCK }
        val sawExpired = attempts.any { classify(it.second.message) == StreamFailureKind.TRANSIENT && is403(it.second.message) }
        val allTransient = attempts.isNotEmpty() &&
            attempts.all { classify(it.second.message) == StreamFailureKind.TRANSIENT }
        val headline = when {
            attempts.isEmpty() -> "串流解析未嘗試任何來源"
            sawBotBlock -> "所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路）"
            sawExpired -> "播放連結已失效，已嘗試重新取得（仍失敗，可稍後重試）"
            allTransient -> "所有解析來源皆失敗（網路不穩，可稍後重試）"
            else -> "所有解析來源皆失敗"
        }
        val detail = attempts.joinToString("；") { "${it.first}: ${it.second.message}" }
        return if (detail.isBlank()) headline else "$headline [$detail]"
    }

    /** 該 message 是否包含 403（連結失效／WAF 拒絕的訊號）。 */
    private fun is403(message: String?): Boolean =
        message != null && HTTP_403_REGEX.containsMatchIn(message.lowercase())

    private companion object {
        // YouTube 匿名存取被封鎖時的典型訊息（playabilityStatus=LOGIN_REQUIRED /
        // "Sign in to confirm that you're not a bot"）。小寫比對。
        val BOT_KEYWORDS = listOf(
            "login_required",
            "sign in to confirm",
            "not a bot",
            "please sign in"
        )

        // raw HTTP 403（InnerTube 非 200 → "HTTP 403"；content URL 播放當下 → "HTTP 403"）。
        // 以 \b 字元邊界匹配獨立 "403"，避免誤傷 "4030" ／ "1403" 等數字尾碼。
        val HTTP_403_REGEX = Regex("\\b403\\b")

        val NETWORK_KEYWORDS = listOf(
            "timeout",
            "timed out",
            "unable to resolve",
            "connection",
            "econnreset",
            "http 5"
        )
    }
}

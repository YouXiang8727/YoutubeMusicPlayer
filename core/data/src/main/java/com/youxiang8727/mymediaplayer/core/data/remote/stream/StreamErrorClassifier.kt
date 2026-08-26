package com.youxiang8727.mymediaplayer.core.data.remote.stream

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析失敗的錯誤映射器：把各來源拋出的例外訊息分類成 [StreamFailureKind]，
 * 並負責把整條 fallback 鏈的嘗試結果聚合成一則使用者可讀的錯誤描述
 * （該描述會顯示在媒體通知上，見 MusicService.runBlockingResolve）。
 *
 * 抽成可注入結構：FallbackStreamResolver 測試可直接以真實實例驗證映射規則。
 */
@Singleton
class StreamErrorClassifier @Inject constructor() {

    fun classify(errorMessage: String?): StreamFailureKind {
        if (errorMessage == null) return StreamFailureKind.TRANSIENT
        val lower = errorMessage.lowercase()
        return when {
            BOT_KEYWORDS.any { lower.contains(it) } -> StreamFailureKind.BOT_BLOCK
            NETWORK_KEYWORDS.any { lower.contains(it) } -> StreamFailureKind.TRANSIENT
            else -> StreamFailureKind.PERMANENT
        }
    }

    /**
     * 聚合所有來源的失敗結果。
     * @param sawBotBlock 過程中是否有任一來源被判斷為 bot 封鎖
     * @param attempts 各來源（名稱, 例外）的嘗試紀錄，依嘗試順序
     */
    fun describe(sawBotBlock: Boolean, attempts: List<Pair<String, Throwable>>): String {
        val headline =
            if (attempts.isEmpty()) "串流解析未嘗試任何來源"
            else "所有解析來源皆失敗" + if (sawBotBlock) "（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路）" else ""
        val detail = attempts.joinToString("；") { "${it.first}: ${it.second.message}" }
        return if (detail.isBlank()) headline else "$headline [$detail]"
    }

    private companion object {
        // YouTube 匿名存取被封鎖時的典型訊息（playabilityStatus=LOGIN_REQUIRED /
        // "Sign in to confirm that you're not a bot"）。小寫比對。
        val BOT_KEYWORDS = listOf(
            "login_required",
            "sign in to confirm",
            "not a bot",
            "please sign in"
        )

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

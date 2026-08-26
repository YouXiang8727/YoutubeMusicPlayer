package com.youxiang8727.mymediaplayer.feature.player.playback

/**
 * 把播放錯誤的 errorCodeName 與 cause chain 訊息映射成單一可讀字串。
 *
 * 純 Kotlin（零 Android 依賴）以便單元測試；
 * 從 [androidx.media3.common.PlaybackException] 取值的薄介面卡留在
 * [MediaControllerPlayerController]，此處只吃已抽出的原始值。
 */
object PlaybackErrorDescriber {

    private const val PREFIX = "播放失敗："
    private const val ERROR_CODE_PREFIX = "ERROR_CODE_"
    private const val UNKNOWN_REASON = "未知錯誤"

    /**
     * 產生形如「播放失敗：<訊息>」的錯誤描述。規則：
     * 1. [causeChainMessages] 中**最深層**的非空訊息優先——
     *    串流解析聚合的中文訊息（「所有解析來源皆失敗[…]」）位於 cause chain 最深層；
     * 2. 全部為 null／空白時，以 [errorCodeName] 人類可讀化兜底。
     *
     * @param errorCodeName 例如 "ERROR_CODE_IO_UNSPECIFIED"
     * @param causeChainMessages 各層 exception 的 message，由最外層往最深走（可含 null／空白）
     */
    fun describe(errorCodeName: String, causeChainMessages: List<String?>): String {
        val deepestMessage = causeChainMessages
            .lastOrNull { !it.isNullOrBlank() }
            ?.trim()
        val reason = deepestMessage ?: humanizeErrorCodeName(errorCodeName)
        return "$PREFIX$reason"
    }

    /** "ERROR_CODE_IO_UNSPECIFIED" → "IO Unspecified"（IO／DRM 等短縮寫維持原樣）。 */
    private fun humanizeErrorCodeName(errorCodeName: String): String {
        val body = errorCodeName.removePrefix(ERROR_CODE_PREFIX)
        if (body.isBlank()) return UNKNOWN_REASON
        return body.split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                if (token.length <= 3) token
                else token.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}

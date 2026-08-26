package com.youxiang8727.mymediaplayer.core.data.remote.stream

/**
 * 單一音訊串流解析來源的抽象（data 層內部型別，不外洩至 domain/UI）。
 *
 * FallbackStreamResolver 依注入順序逐一嘗試；實作應：
 * - 只做「解析出一個可播放的音訊 URL」，不做重試策略（策略集中在 resolver）
 * - 失敗時以例外訊息描述原因（會被 StreamErrorClassifier 分類並聚合進最終錯誤）
 */
interface AudioStreamSource {

    /** 來源識別名稱，用於錯誤訊息聚合與測試斷言。 */
    val name: String

    suspend fun fetch(videoId: String): Result<String>
}

/** 解析失敗的分類，用於錯誤映射與使用者可讀的提示。 */
enum class StreamFailureKind {
    /** YouTube bot 偵測封鎖（LOGIN_REQUIRED / Sign in to confirm…），換網路或稍後重試可能改善。 */
    BOT_BLOCK,

    /** 網路逾時、5xx 等暫時性失敗。 */
    TRANSIENT,

    /** 影片不存在、無音訊串流等結構性失敗。 */
    PERMANENT
}

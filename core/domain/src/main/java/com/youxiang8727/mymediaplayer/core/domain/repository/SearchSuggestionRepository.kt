package com.youxiang8727.mymediaplayer.core.domain.repository

/**
 * 搜尋建議（autocomplete / search suggestion）領域埠。
 *
 * 實作位於 core:data（Google suggestqueries 建議端點），UI 僅依賴此介面，
 * 不洩漏任何資料層型別。
 */
interface SearchSuggestionRepository {

    /**
     * 依關鍵字前綴取得 YouTube 搜尋建議清單。
     *
     * @param query 使用者目前輸入的關鍵字（會先 trim；空白／過短回空清單）。
     * @return 建議字串清單（依 YouTube 排序），網路失敗或無建議時為空清單，
     *         此函式**不丟例外**（錯誤封在實作內，對呼叫端僅回空清單）。
     */
    suspend fun suggestions(query: String): List<String>
}

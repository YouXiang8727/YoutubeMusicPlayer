package com.youxiang8727.mymediaplayer.core.data.remote

/**
 * 搜尋建議資料源抽象（data 層內部型別，不外洩）。
 *
 * 實作負責執行網路呼叫並回傳已解析的建議字串清單。
 * 對 Repository 而言，此型別為可替換的資料來源（單元測試以 Fake 注入）。
 */
interface SearchSuggestionDataSource {

    /**
     * 依關鍵字前綴取得 YouTube 搜尋建議。
     *
     * @return 建議字串清單；網路失敗、回應無法解析、或無建議時回空清單
     *         （實作不拋例外，錯誤封在內部）。
     */
    suspend fun suggestions(query: String): List<String>
}

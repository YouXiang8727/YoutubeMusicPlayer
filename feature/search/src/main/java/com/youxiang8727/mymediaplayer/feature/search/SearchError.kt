package com.youxiang8727.mymediaplayer.feature.search

import java.io.IOException

/**
 * 搜尋失敗的原因分類。**由 ViewModel 依失敗原因決定，UI 層只負責呈現 [message]。**
 *
 * ### 為什麼是封閉 enum，而不是 `String?`
 *
 * 本欄位過去直接存 `e.message`，導致英文／內部細節（`token expired` 等）被外顯到
 * 畫面上。改為封閉型別後，「原始例外訊息外顯」在**型別層就不可能發生**：
 * `feature:search` 只能拿到 enum 寫入，沒有任何路徑能把任意字串塞進來。
 * 這與專案「物理強制、不靠自覺」的原則一致（例：佇列 `key` 納入 `index` 修正崩潰）。
 *
 * ### 分類依據（`core:data` 搜尋鏈實查結果）
 *
 * 呼叫鏈為 `SearchViewModel.doSearch` → `SearchVideosUseCase` → `VideoRepositoryImpl`
 * （`runCatching` 包裹）→ `YoutubeDataSource` → Retrofit（`searchHtml` GET 首次搜尋／
 * `searchInnerTube` POST 排序搜尋與續頁）→ `kotlinx.serialization` 解析。實際會逃逸到
 * ViewModel 的例外就三類，其餘落 [UNKNOWN]：
 *
 * | 分類 | 觸發來源 | 判斷依據 |
 * |------|----------|----------|
 * | [NETWORK] | 網路不可達／連線中斷 | `java.io.IOException` 及其子類（`UnknownHostException` DNS 失敗、`SocketTimeoutException` 逾時、`ConnectException` 拒絕連線） |
 * | [SERVER] | HTTP 非 2xx（`retrofit2.HttpException`） | 例外全名比對，見 [HTTP_ERROR_CLASS_NAMES] |
 * | [PARSE] | 回應格式無法解析（`kotlinx.serialization`） | 例外全名比對，見 [PARSE_ERROR_CLASS_NAMES] |
 * | [UNKNOWN] | 以上皆非（兜底，保留「搜尋失敗」大類語意） | — |
 *
 * ⚠️ **已知限制（不是疏忽）**：[PARSE] 目前在現行資料鏈**幾乎不會發生**——
 * `YoutubeDataSource.parseYtInitialData`／`parseContinuationChunk` 對 JSON 解析失敗是
 * `runCatching` **吞掉並回空頁**，不會往上拋。故解析失敗目前會走 [UNKNOWN] 並顯示
 * 「查無結果」（此為上游靜默降級的既有取捨，見 `docs/qa/smoke-checklist.md` [O2]）。
 * 保留 [PARSE] 分類是為了：① 資料層改為向上拋解析失敗時 UI 已就緒 ② 讓
 * 「解析失敗不該引導使用者去查網路」這個判斷有明確落點。
 */
enum class SearchError(val message: String) {

    /**
     * 網路不可達／連線中斷。使用者**有事可做**（開網路、換網路、稍後重試），
     * 故文案直接給出行動指令。
     */
    NETWORK("無法連線到網路，請檢查網路後重試"),

    /**
     * 伺服器回應非 2xx（限流、伺服器故障等）。使用者無從處理，
     * **不可**引導去檢查網路（那會誤導使用者懷疑自己的網路），只請稍後再試。
     */
    SERVER("伺服器暫時無法回應，請稍後再試一次"),

    /**
     * 回應格式無法解析。同 [SERVER]：這是服務端的問題，**不引導使用者檢查網路**。
     */
    PARSE("搜尋結果無法顯示，請稍後再試一次"),

    /**
     * 無法歸類的兜底。刻意保留「搜尋失敗」四字作為大類語意，
     * 讓未知失敗仍明確被讀成「這次搜尋沒成功」而非「查無結果」。
     */
    UNKNOWN("搜尋失敗，請稍後再試一次")
}

/**
 * `retrofit2.HttpException` 的類別全名。
 *
 * ⚠️ **為什麼用「類別全名比對」而不是 `is retrofit2.HttpException`**：
 * Retrofit（`retrofit2.*`）與 kotlinx.serialization 都在 `core:data` 的 classpath 上，
 * `feature:search` **看不到它們**（依賴方向由 Gradle 物理強制，`feature:*` 不可依賴
 * `core:data`），而為了錯誤文案去新增該依賴屬越界。故只能以全名比對。
 *
 * 取捨：全名比對是字串耦合，但兩者都是第三方套件的**公開型別全名**，跨版本穩定；
 * 且萬一真的變了，結果只是退回 [SearchError.UNKNOWN]（顯示「搜尋失敗，請稍後再試一次」）
 * 而**不會崩潰**，屬可接受的降級。此處刻意不為此新增 domain 層錯誤型別（跨層變更，
 * 需 Tech Lead 裁決）。
 */
private val HTTP_ERROR_CLASS_NAMES = setOf("retrofit2.HttpException")

/** 解析類例外的類別全名。取得理由同 [HTTP_ERROR_CLASS_NAMES]。 */
private val PARSE_ERROR_CLASS_NAMES = setOf("kotlinx.serialization.SerializationException")

/**
 * `cause` 鏈走訪上限：避免病態的自參照鏈無限循環，同時足以涵蓋真實的包裝深度
 * （Retrofit → OkHttp → JDK 最多 3～4 層）。
 */
private const val CAUSE_CHAIN_LIMIT = 8

/**
 * 把上層（`core:data`）丟出的例外歸類為 [SearchError]。
 *
 * **走訪 `cause` 鏈而非只比對最外層**：底層的 [IOException] 可能被包裝成
 * `RuntimeException`（Retrofit 在部分路徑如此），只看最外層會把網路錯誤
 * 誤判成 [SearchError.UNKNOWN]——那正好是最常見的一種失敗，誤判成本最高。
 */
internal fun Throwable.toSearchError(): SearchError {
    val chain = generateSequence(this) { it.cause }
        .take(CAUSE_CHAIN_LIMIT)
        .toList()
    // IOException 是 JDK 型別，feature:search 直接可見，用型別判斷（能涵蓋所有子類）。
    // SERVER／PARSE 需比對第三方型別全名，拆成純函式 [classifyByClassName] 以便
    // 不依賴那些類別也能測試（見下）。
    return if (chain.any { it is IOException }) {
        SearchError.NETWORK
    } else {
        classifyByClassName(chain.map { it.javaClass.name })
    }
}

/**
 * 以例外鏈的**類別全名**分類（[SearchError.NETWORK] 的 [IOException] 判斷不在此）。
 *
 * 刻意拆成獨立的純函式：Retrofit／serialization 的型別不在 `feature:search` 的
 * classpath 上（見 [HTTP_ERROR_CLASS_NAMES]），故測試無法直接 new 出那些例外來驅動
 * [toSearchError]；拆開後可用真實的全名字串斷言分類表本身正確。
 */
internal fun classifyByClassName(classNames: List<String>): SearchError = when {
    classNames.any { it in HTTP_ERROR_CLASS_NAMES } -> SearchError.SERVER
    classNames.any { it in PARSE_ERROR_CLASS_NAMES } -> SearchError.PARSE
    else -> SearchError.UNKNOWN
}

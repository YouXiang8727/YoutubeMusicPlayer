package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.domain.model.SearchSort

/**
 * [SearchSort] → innerTube `params` 查詢參數的映射。
 *
 * ## 為什麼這層知識在 core:data 而不在 core:domain
 *
 * `params` 的值是 YouTube 端點的 **base64 編碼 protobuf**（`SearchFilter`），
 * 屬於平台的私有 wire format：YouTube 隨時可能改版，值也可能整批失效。
 * 領域模型不該知道「排序要變成 base64」這件事，故映射收在此處
 * （與 `MusicService` 內 `RepeatMode.toExoRepeatMode()` 同一原則：
 * 平台／框架特定映射不進 domain）。
 *
 * ## 三個常數的 protobuf 結構
 *
 * 解碼後皆為 `12 02 08 <sort> 10 01`：
 * - `08 <sort>` = 欄位 1（filter）內的排序子欄位：`02` = 上傳日期、`03` = 觀看次數。
 * - `10 01` = 結果型別 `type=video`（只要影片，與本專案既有行為一致）。
 *
 * ## ⚠️ 網路上幾乎所有文章都會踩的坑
 *
 * 常見教學叫你對 **web 版** 搜尋加 `sp=CAISAhAB`。那是 **web 版的 protobuf 編碼**，
 * 在本專案使用的 innerTube `POST youtubei/v1/search` 上**無效**（實測結果等同基線）。
 * innerTube 用的是另一套編碼（`EgQ?A?RAB`）。**不要**把兩者互換或「順手統一」。
 *
 * ## 路由限制（實測決定，不可憑印象改動）
 *
 * `params` **只在 innerTube POST 端點有效**。2026-09 實測 `GET results?params=...`：
 * 結果確實「與基線不同」，但那是**非確定性的雜訊、不是排序**——同一 query 重複請求
 * 會得到不同順序，且最新排序的第 1 名永遠是 4 年前的老片（日期並未遞減）。
 * 故 [YoutubeDataSource] 僅在需要排序時改走 innerTube POST，`RELEVANCE` 維持 GET 原路徑。
 *
 * @see SearchSort
 */
internal fun SearchSort.toSearchParams(): String? = when (this) {
    // RELEVANCE 回傳 null（而非 sort=0 的常數）：**不帶 params 欄位**，
    // 讓預設路徑與「YouTube 預設行為」走同一條程式路徑。若改帶 sort=0 常數，
    // 結果雖實測相同，但走不同程式分支——萬一 params 機制整體劣化，
    // 預設路徑會被無謂牽連。
    SearchSort.RELEVANCE -> null
    SearchSort.LATEST -> "EgQIBRAB" // 依上傳日期由新到舊
    SearchSort.POPULAR -> "EgQICRAB" // 依觀看次數由多到少
}

package com.youxiang8727.mymediaplayer.core.data.repository

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem

/**
 * 由 YouTube [videoId] 推導縮圖 URL 的 wire 規則。
 *
 * ## 為什麼這層知識在 core:data 而不在 core:domain
 *
 * `i.ytimg.com/vi/{id}/...` 是 YouTube 平台的**私有 wire format**：主機名、檔名、
 * 尺寸代號（`mqdefault`）皆屬平台約定，YouTube 改版即可能失效。領域模型不該知道
 * 「videoId 拼成 URL」這件事，故收在此處，與 `remote/InnerTubeSearchParams.kt`
 * （`params` 的 base64 protobuf）同一原則：**平台／框架特定映射不進 domain**。
 *
 * 為何另開檔而非放進 `local/PlaylistItemEntity.kt`（mapper 同層）：
 * 該檔目前是「Entity ↔ Domain 的機械式映射」，零平台知識；把 YouTube URL 模板
 * 混進去會讓 mapper 檔同時承擔兩種責任。獨立小檔讓 wire 規則可被單獨閱讀、
 * 單獨測試——與專案既有把平台常數獨立成檔的慣例一致
 * （`InnerTubeSearchParams.kt`、`stream/InnerTubeClientProfiles.kt`）。
 */

/** YouTube videoId 形態：固定 11 字元 base64url（`A-Za-z0-9_-`）。 */
private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * ⚠️ **尺寸代號選 `mqdefault`（320×180、16:9）而非 `hqdefault`（480×360、4:3）——
 * 不要基於「畫質較高」改回去。**
 *
 * 選 `mqdefault` 的理由是**與歌單詳情頁縮圖槽位比例一致**（實測槽位 286×160 px
 * ≈ 16:9），`core:ui` `VideoThumbnail` 以 `ContentScale.Crop` 渲染，故 16:9 對 16:9
 * **不產生裁切損失**；而 320×180 仍略高於槽位像素（286×160），足夠不糊。
 *
 * 若改用 4:3 的 `hqdefault`，`Crop` 會因比例不符而**上下各裁掉約 25%**
 * （480×360 裁成 16:9），放大檢視可見縮圖內容的文字被水平切斷——是實測已否決的
 * 方向（2026-09 實機量測，見 `docs/qa/reports/2026-09-28-playlist-thumbnail.md`）。
 * **此處是「畫質」與「不裁切內容」的取捨，裁切內容已證實有害，故取不裁切。**
 *
 * 另與搜尋結果存入的縮圖外觀一致：兩種來源最終都餵給同一個 `VideoThumbnail`
 * ＋ 同一個 `ContentScale.Crop` 渲染路徑，且同屬 `i.ytimg.com/vi/{id}/` 這條 URL 規則。
 */
private const val THUMBNAIL_URL_TEMPLATE = "https://i.ytimg.com/vi/%s/mqdefault.jpg"

/**
 * 推導 [videoId] 的 YouTube 縮圖 URL；**格式不符 YouTube 規格時回傳空字串**
 * （而非拼出無效 URL），讓 UI 的縮圖元件維持「無圖」語意而非走錯誤 fallback。
 */
internal fun deriveYoutubeThumbnailUrl(videoId: String): String =
    if (YOUTUBE_VIDEO_ID.matches(videoId)) {
        THUMBNAIL_URL_TEMPLATE.format(videoId)
    } else {
        ""
    }

/**
 * 寫入時的縮圖正規化：**只補空白**（`isBlank()`，涵蓋空字串與純空白字元），
 * 已有值一律原樣保留——不覆寫，才能讓未來其他寫入路徑帶入的真實縮圖
 * （如 JSON 匯入、或搜尋結果自帶的 API 縮圖）不被推導值蓋掉。
 *
 * 補上的推導值是「不顯示灰色色塊」的體驗底線，**非呼叫端精選的 crop**；
 * 尺寸代號取 `mqdefault` 的理由見 [THUMBNAIL_URL_TEMPLATE]。
 */
internal fun PlaylistItem.withResolvedThumbnail(): PlaylistItem =
    if (thumbnailUrl.isBlank()) {
        copy(thumbnailUrl = deriveYoutubeThumbnailUrl(videoId))
    } else {
        this
    }

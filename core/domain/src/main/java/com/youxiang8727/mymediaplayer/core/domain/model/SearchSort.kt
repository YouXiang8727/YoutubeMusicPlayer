package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 搜尋結果排序方式。
 *
 * **這是「要什麼」的表達，不是「怎麼拿到」**：本列舉刻意只描述語意，
 * 不含任何平台特定實作（base64 protobuf 參數等）——那些是 YouTube 端點的
 * 私有 wire format，會隨 YouTube 改版而變動，屬於 `core:data` 的知識。
 * `core:data` 負責把本列舉映射成實際請求參數。
 *
 * 三種排序的實際語意（2026-09 於 `m.youtube.com` 實測確認）：
 * - [RELEVANCE]：依相關性排序（YouTube 預設）。
 * - [LATEST]：依上傳日期由新到舊。
 * - [POPULAR]：依觀看次數由多到少。
 *
 * 續頁（continuation）**不支援**排序切換：續頁 token 本身已承載首次搜尋的
 * 排序狀態，詳見 `core:data` 的 `InnerTubeSearchParams` 說明。
 */
enum class SearchSort {
    RELEVANCE,
    LATEST,
    POPULAR
}

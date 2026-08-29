package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 搜尋結果的一頁（純領域模型，不含任何持久化或序列化標註）。
 *
 * @param results 該頁的全部影片結果（分頁由 token 控制，不再有每頁上限）
 * @param nextPageToken 下一頁的 continuation token；null 表示已到底、無更多結果
 */
data class VideoSearchPage(
    val results: List<VideoResult>,
    val nextPageToken: String? = null
)
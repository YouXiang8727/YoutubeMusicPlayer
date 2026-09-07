package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 熱門音樂榜單區域。
 *
 * 每個值對應 YouTube Music Global Charts 官方頻道的一個 playlist（100 首、每週更新）。
 * [playlistId] 為 YouTube playlist ID，[countryCode] 保留給 innerTube browse 的
 * 國家參數（`gl` / `hl`）。
 *
 * @property playlistId YouTube Music Global Charts 官方 playlist ID（`PL` 開頭）。
 * @property countryCode ISO 3166-1 alpha-2 國家碼（大寫）。
 */
enum class ChartRegion(val countryCode: String, val playlistId: String) {
    /** 台灣百大熱門音樂影片 */
    TAIWAN("TW", "PL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU"),
    /** Top 100 Songs United States（西洋熱門音樂） */
    WESTERN("US", "PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI"),
    /** Top 100 Songs Japan（日本熱門音樂） */
    JAPAN("JP", "PL4fGSI1pDJn4-UIb6RKHdxam-oAUULIGB"),
    /** Top 100 Songs South Korea（韓國熱門音樂） */
    KOREA("KR", "PL4fGSI1pDJn6jXS_Tv_N9B8Z0HTRVJE0m");

    companion object {
        /** 搜尋頁空狀態顯示的 region 順序。 */
        val DISPLAY_ORDER: List<ChartRegion> = listOf(TAIWAN, WESTERN, JAPAN, KOREA)
    }
}

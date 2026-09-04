package com.youxiang8727.mymediaplayer.core.domain.model

/**
 * 熱門音樂榜單區域。
 *
 * [countryCode] 保留給 innerTube browse 的國家參數（`gl` / `hl`），
 * 並對應官方 YouTube Music Global Charts playlist 的地區來源。
 *
 * 只保留 [TAIWAN]：台灣官方熱門音樂 playlist 為「台灣百大熱門音樂影片」
 * （owner = YouTube Music Global Charts 官方頻道，100 首）。舊 charts.youtube.com
 * 的 `LAUNCHED_CHART_COUNTRIES` 不含 TW，GLOBAL 亦無官方來源／產品使用點，
 * 故一律移除（避免死路徑誤導）。
 */
enum class ChartRegion(val countryCode: String) {
    TAIWAN("TW")
}

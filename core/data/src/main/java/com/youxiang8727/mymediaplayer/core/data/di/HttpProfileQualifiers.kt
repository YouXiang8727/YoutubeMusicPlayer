package com.youxiang8727.mymediaplayer.core.data.di

import javax.inject.Qualifier

/**
 * OkHttpClient 的 profile 區分（提供處見 `remote.NetworkModule`）。
 *
 * 歷史教訓：全域單一 client 曾把瀏覽器 header 攔截器（UA／Referer／Cookie）
 * 套到所有請求上，破壞 InnerTube IOS/ANDROID_VR client 身份與 NewPipe extractor
 * 自帶 UA，導致串流解析三層 fallback 全數被 YouTube 回 LOGIN_REQUIRED（IP 本身清白）。
 * 因此 client 一律依用途綁定 profile；禁止再新增無差別的全域 header 覆蓋。
 */

/** 瀏覽器 profile：含 YoutubeHeaderInterceptor，僅供 Retrofit 行動版搜尋頁 HTML 抓取使用。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BrowserProfile

/** 串流 profile：乾淨 client（僅逾時設定、無任何攔截器），供串流解析鏈（NewPipe／InnerTube／Piped）使用。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StreamProfile

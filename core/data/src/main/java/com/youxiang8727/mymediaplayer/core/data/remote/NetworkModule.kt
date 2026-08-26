package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.data.di.BrowserProfile
import com.youxiang8727.mymediaplayer.core.data.di.StreamProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * HTTP client 依用途拆成兩個 profile（qualifier 定義見 `di.HttpProfileQualifiers`）：
 * - [BrowserProfile]：掛 [YoutubeHeaderInterceptor]（瀏覽器 UA／語系／Consent Cookie），
 *   僅供 Retrofit 抓行動版搜尋頁 HTML——該頁面需要偽裝瀏覽器才回傳完整內容。
 * - [StreamProfile]：乾淨 client（僅逾時設定、無任何攔截器），串流解析鏈專用。
 *   瀏覽器 header 會破壞 InnerTube IOS/ANDROID_VR 直連的 client 身份，並覆蓋
 *   NewPipe extractor 自帶的 UA，兩者都會被 YouTube 回 LOGIN_REQUIRED，
 *   故串流鏈一律走本 profile。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** 瀏覽器 profile：模擬行動版瀏覽器，僅供搜尋頁 HTML 抓取。 */
    @Provides
    @Singleton
    @BrowserProfile
    fun provideBrowserOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(YoutubeHeaderInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    /** 串流 profile：無攔截器的乾淨 client，串流解析鏈（NewPipe／InnerTube／Piped）專用。 */
    @Provides
    @Singleton
    @StreamProfile
    fun provideStreamOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(@BrowserProfile client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://m.youtube.com/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideYoutubeSearchApi(retrofit: Retrofit): YoutubeSearchApi =
        retrofit.create(YoutubeSearchApi::class.java)
}

/** 統一注入瀏覽器 UA / 語系 / Consent Cookie，模擬瀏覽器行為。僅掛於 [BrowserProfile] client。 */
object YoutubeHeaderInterceptor : Interceptor {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://m.youtube.com/")
            .header("Cookie", "CONSENT=YES+cb; SOCS=CAI")
            .build()
        return chain.proceed(request)
    }
}

package com.youxiang8727.mymediaplayer.core.data.remote

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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(YoutubeHeaderInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
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

/** 統一注入瀏覽器 UA / 語系 / Consent Cookie，模擬瀏覽器行為。 */
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

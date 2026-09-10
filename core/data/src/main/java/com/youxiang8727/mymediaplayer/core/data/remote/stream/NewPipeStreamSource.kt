package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.di.StreamProfile
import com.youxiang8727.mymediaplayer.core.data.remote.OkHttpDownloader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * 主路徑：NewPipe Extractor 解析（原 StreamResolver 邏輯遷入）。
 *
 * 使用 [StreamProfile] 乾淨 client（無攔截器），讓 extractor 自帶的 UA 生效——
 * 瀏覽器 header 覆蓋會導致 YouTube 回 LOGIN_REQUIRED。
 *
 * NewPipe 初始化以 [ensureExtractorInitialized] 延遲執行（與「為你推薦」的
 * [RelatedStreamsDataSource] 共用同一初始化，避免行為分歧）。
 *
 * 注意：YouTube 對匿名 IP 啟用 bot 偵測（LOGIN_REQUIRED）時此路徑仍可能失敗——
 * v0.26.5（現行最新版）尚未內建繞道，由 FallbackStreamResolver 接手。
 */
@Singleton
class NewPipeStreamSource @Inject constructor(
    @StreamProfile okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : AudioStreamSource {

    override val name: String = "NewPipe"

    private val downloader = OkHttpDownloader(okHttpClient)

    override suspend fun fetch(videoId: String): Result<String> = withContext(dispatchers.io) {
        runCatching {
            ensureExtractorInitialized(downloader)
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )
            val streams = info.audioStreams

            // 優先選最高音質的 m4a（相容性最佳），否則退而求其次
            streams.filter { it.format == MediaFormat.M4A }
                .maxByOrNull { it.averageBitrate }
                ?: streams.maxByOrNull { it.averageBitrate }
        }.mapCatching { stream ->
            requireNotNull(stream) { "此影片沒有可用的音訊串流" }.content
        }
    }
}

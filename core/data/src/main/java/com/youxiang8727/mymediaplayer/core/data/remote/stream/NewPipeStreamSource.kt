package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.remote.OkHttpDownloader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo

@Volatile
private var extractorInitialized = false

/**
 * 主路徑：NewPipe Extractor 解析（原 StreamResolver 邏輯遷入）。
 *
 * 注意：YouTube 對匿名 IP 啟用 bot 偵測（LOGIN_REQUIRED）時此路徑會失敗——
 * v0.26.5（現行最新版）尚未內建繞道，由 FallbackStreamResolver 接手。
 */
@Singleton
class NewPipeStreamSource @Inject constructor(
    okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : AudioStreamSource {

    override val name: String = "NewPipe"

    private val downloader = OkHttpDownloader(okHttpClient)

    private fun ensureInitialized() {
        if (!extractorInitialized) {
            synchronized(this) {
                if (!extractorInitialized) {
                    NewPipe.init(downloader, Localization("zh", "TW"))
                    extractorInitialized = true
                }
            }
        }
    }

    override suspend fun fetch(videoId: String): Result<String> = withContext(dispatchers.io) {
        runCatching {
            ensureInitialized()
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

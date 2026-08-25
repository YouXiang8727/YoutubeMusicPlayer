package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
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
 * 使用 NewPipe Extractor 解析影片的可播放音訊串流 URL。
 * 與播放清單無關：任何 videoId（搜尋結果或清單中的影片）皆可解析。
 */
@Singleton
class StreamResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) {

    private fun ensureInitialized() {
        if (!extractorInitialized) {
            synchronized(this) {
                if (!extractorInitialized) {
                    NewPipe.init(
                        OkHttpDownloader(okHttpClient),
                        Localization("zh", "TW")
                    )
                    extractorInitialized = true
                }
            }
        }
    }

    suspend fun resolveAudioUrl(videoId: String): Result<String> =
        withContext(dispatchers.io) {
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

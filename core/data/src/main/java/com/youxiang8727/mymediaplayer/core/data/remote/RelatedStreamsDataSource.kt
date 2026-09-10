package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.di.StreamProfile
import com.youxiang8727.mymediaplayer.core.data.remote.stream.ensureExtractorInitialized
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * 「相關歌曲」資料源：以 NewPipe Extractor 的 watch page **Related Songs** 為推薦候選。
 *
 * - 使用 [StreamProfile] 乾淨 client → [OkHttpDownloader]，與 [NewPipeStreamSource]
 *   同一乾淨身份（瀏覽器 header 會破壞 extractor 自帶 UA，見 `di.HttpProfileQualifiers`）。
 * - NewPipe 初始化以 [ensureExtractorInitialized] 延遲執行（與串流解析共用，避免分歧）。
 * - `getRelatedItems()` 於 extractor v0.26.5 已確認存在（回傳 `List<InfoItem>`，
 *   成員以 [StreamInfoItem] 居多，其餘型別一律過濾）。
 * - 解析映射拆 internal 純函數 [relatedItemToVideoResult]（JVM 可測，不觸網）。
 *
 * 已知風險（與串流解析同源的 bot 封鎖）：watch page 暴露於 YouTube 匿名 bot 偵測，
 * 失效時由「為你推薦」區塊降級為錯誤＋重試，不連坐熱門榜單。
 */
@Singleton
open class RelatedStreamsDataSource @Inject constructor(
    @StreamProfile okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider
) {
    private val downloader = OkHttpDownloader(okHttpClient)

    /**
     * 抓取指定影片的相關歌曲清單。
     * @param videoId 種子影片 ID（watch page）
     * @return 成功＝映射後的 [VideoResult] 清單（可能為空）；失敗＝解析/網路錯誤。
     */
    open suspend fun related(videoId: String): Result<List<VideoResult>> =
        withContext(dispatchers.io) {
            runCatching {
                ensureExtractorInitialized(downloader)
                val info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=$videoId"
                )
                info.relatedItems.mapNotNull { relatedItemToVideoResult(it) }
            }
        }
}

/**
 * 將 NewPipe 的 [InfoItem] 映射為領域模型 [VideoResult]。
 *
 * 只接受 [StreamInfoItem]（影片）；其他型別（頻道、播放清單等）一律回 null。
 * 防禦：URL 無法萃取出 videoId、影片實際無縮圖時，以可用的預設值（videoId 作為 title、
 * 空字串縮圖）回傳，不丟棄整筆候選。
 */
internal fun relatedItemToVideoResult(item: InfoItem?): VideoResult? {
    if (item !is StreamInfoItem) return null
    val videoId = extractVideoIdFromUrl(item.url) ?: return null
    val thumb = item.thumbnails
        ?.firstOrNull { it.url.isNotBlank() }
        ?.url
        .orEmpty()
    return VideoResult(
        videoId = videoId,
        title = item.name.trim().ifBlank { videoId },
        thumbnailUrl = thumb,
        channel = item.uploaderName.orEmpty(),
        duration = formatDurationSeconds(item.duration)
    )
}

/** 從 NewPipe 的影片 URL 萃取出 11 字元 videoId（支援 watch?v=、youtu.be/、shorts/）。 */
internal fun extractVideoIdFromUrl(url: String?): String? {
    if (url == null) return null
    return VIDEO_ID_PATTERN.find(url)?.groupValues?.get(1)
}

/** 秒數轉顯示用長度字串（"3:45"／"1:02:03"）；秒數 <= 0（未知/直播）回 null。 */
internal fun formatDurationSeconds(seconds: Long): String? {
    if (seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:$ss"
    } else {
        "$m:$ss"
    }
}

/** watch 頁常見 URL 形態的 videoId 萃取（11 字元 base64url）。 */
private val VIDEO_ID_PATTERN = Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/embed/)([\\w-]{11})")
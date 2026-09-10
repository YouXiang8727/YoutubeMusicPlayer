package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.data.remote.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

@Volatile
private var extractorInitialized = false

private val initializationLock = Any()

/**
 * NewPipe Extractor 的**全程序唯一**延遲初始化入口。
 *
 * 由 [NewPipeStreamSource]（串流解析主路徑）與 `RelatedStreamsDataSource`
 * （「為你推薦」相關歌曲）共用，避免兩處各自 `NewPipe.init` 造成行為分歧。
 *
 * 注意：`NewPipe.init` 只能以**乾淨 client**（`@StreamProfile` 的 [OkHttpDownloader]）
 * 初始化——瀏覽器 header 覆蓋會破壞 extractor 自帶的 UA，導致 YouTube 回
 * LOGIN_REQUIRED（歷史教訓，見 `di.HttpProfileQualifiers`）。
 */
internal fun ensureExtractorInitialized(downloader: OkHttpDownloader) {
    if (!extractorInitialized) {
        synchronized(initializationLock) {
            if (!extractorInitialized) {
                NewPipe.init(downloader, Localization("zh", "TW"))
                extractorInitialized = true
            }
        }
    }
}
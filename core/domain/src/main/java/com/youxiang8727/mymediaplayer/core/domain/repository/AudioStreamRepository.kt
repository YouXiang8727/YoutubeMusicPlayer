package com.youxiang8727.mymediaplayer.core.domain.repository

/**
 * 音訊串流解析的領域埠（Port）。
 * 實作位於 core:data（NewPipe Extractor），播放服務僅依賴此介面。
 */
interface AudioStreamRepository {
    suspend fun resolveAudioUrl(videoId: String): Result<String>
}

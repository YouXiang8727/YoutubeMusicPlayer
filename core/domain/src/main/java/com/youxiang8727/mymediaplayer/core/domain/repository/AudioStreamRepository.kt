package com.youxiang8727.mymediaplayer.core.domain.repository

/**
 * 音訊串流解析的領域埠（Port）。
 * 實作位於 core:data（NewPipe Extractor），播放服務僅依賴此介面。
 */
interface AudioStreamRepository {

    /**
     * 解析指定影片的音訊串流 URL。
     *
     * @param videoId 影片 ID
     * @param force 是否強制重新解析。預設 false（命中 TTL 快取直接回傳）；
     *   設為 true 時跳過快取強制重跑解析，用於 content URL 過期（HTTP 403）後重試同曲。
     *   實作細節（快取 TTL 等）封在 core:data，此介面只暴露語意。
     */
    suspend fun resolveAudioUrl(videoId: String, force: Boolean = false): Result<String>
}

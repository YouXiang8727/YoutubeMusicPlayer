package com.youxiang8727.mymediaplayer.core.domain.repository

import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackPreferences

/**
 * 播放偏好（隨機／循環）持久化領域埠。
 *
 * 實作位於 core:data（DataStore Preferences），UI 不直接觸及；
 * MusicService 僅依賴此介面，Hilt 於 app 圖層解析實作。
 * 無儲存資料時 [get] 回傳預設（shuffle=false、repeatMode=ALL）。
 */
interface PlaybackPreferencesRepository {

    /** 讀取上次播放模式；無儲存資料時回傳預設值（shuffle=false、repeatMode=ALL）。 */
    suspend fun get(): PlaybackPreferences

    /** 覆寫保存目前播放模式（供下次播放還原）。 */
    suspend fun save(preferences: PlaybackPreferences)
}
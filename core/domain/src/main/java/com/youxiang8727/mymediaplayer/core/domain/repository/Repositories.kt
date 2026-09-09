package com.youxiang8727.mymediaplayer.core.domain.repository

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import com.youxiang8727.mymediaplayer.core.domain.model.VideoSearchPage
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    /**
     * 執行搜尋或載入下一頁。
     * @param continuationToken null = 初次搜尋；非 null = 以該 token 載入續頁
     */
    suspend fun search(query: String, continuationToken: String? = null): Result<VideoSearchPage>

    /**
     * 抓取指定區域的熱門音樂 playlist（YouTube Music Global Charts 官方頻道，
     * 100 首、每週更新）。此資料源採**分頁聚合至整份**——內部迴圈抓各頁並累加去重，
     * 回傳為聚合後的完整清單（~100 首），非單頁。
     * @param region 榜單區域（[ChartRegion.DISPLAY_ORDER] 定義顯示順序）
     */
    suspend fun fetchTrendingSongs(region: ChartRegion): Result<List<VideoResult>>
}

interface PlaylistRepository {
    fun observeAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>>
    suspend fun addItem(playlistId: Long, item: PlaylistItem)
    suspend fun removeItem(playlistId: Long, videoId: String)
    suspend fun clearPlaylist(playlistId: Long)
    suspend fun getRandomItem(playlistId: Long): PlaylistItem?

    /**
     * 標記某曲「播放失敗」。記錄時間戳至 [PlaylistItem.streamFailedAt]，
     * 供播放清單 UI 顯示錯誤標記。videoId 為 playlist_items 全域主鍵，不需 playlistId。
     */
    suspend fun markStreamFailed(videoId: String, failedAt: Long)

    /**
     * 清除某曲的失敗標記（設回 null）。於該曲成功播放（READY）時呼叫。
     */
    suspend fun clearStreamFailed(videoId: String)
}

/**
 * 搜尋紀錄（本機儲存，最新在前，去重置頂，最多保留 10 筆）。
 *
 * 實作位於 core:data（Room），UI 僅依賴此介面，不洩漏任何資料層型別。
 */
interface SearchHistoryRepository {

    /**
     * 記錄一次搜尋。
     *
     * 實作端會先 trim，trim 後為空白的 query 直接忽略（不寫入）；重複 query
     * 以更新時間戳方式置頂；超出上限時淘汰最舊一筆。
     *
     * @param query 使用者執行的搜尋字串（可含前後空白，實作端會處理）
     */
    suspend fun add(query: String)

    /** 觀察全部搜尋紀錄（最新在前，最多 10 筆）。 */
    fun observeAll(): Flow<List<String>>

    /** 清除全部搜尋紀錄。 */
    suspend fun clear()
}

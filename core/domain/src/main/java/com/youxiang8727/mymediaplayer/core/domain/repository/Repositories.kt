package com.youxiang8727.mymediaplayer.core.domain.repository

import com.youxiang8727.mymediaplayer.core.domain.model.ChartRegion
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictDecision
import com.youxiang8727.mymediaplayer.core.domain.model.ImportConflictInfo
import com.youxiang8727.mymediaplayer.core.domain.model.Playlist
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistImportResult
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

    /**
     * 觀察「最近加入」的歌曲：跨**全部**播放清單依 [PlaylistItem.addedAt] 最新在前、
     * 依 videoId 去重（保留最新一筆）、最多 [limit] 筆。
     *
     * 用途：供應「為你推薦」的推薦種子（配合
     * `FetchRecommendationsUseCase.SEED_LIMIT` 等常數決定種子數）。
     */
    fun observeRecentItems(limit: Int): Flow<List<PlaylistItem>>

    /** Export a single playlist to JSON string. */
    suspend fun exportPlaylistAsJson(playlistId: Long): String?

    /** Export ALL playlists to a single JSON string (v2 format). null if no playlists exist. */
    suspend fun exportAllPlaylistsAsJson(): String?

    /**
     * Import playlist(s) from JSON string. Accepts both formats：
     * - v1（單一）：root 含 `playlist` 鍵 → 匯入一個歌單
     * - v2（bundle）：root 含 `playlists` 鍵（陣列）→ 依序匯入多個歌單
     *
     * 匯入流程採**衝突詢問制**：當某筆歌單名稱與既有歌單重名時，流程**暫停等待**
     * [onConflict] 回傳決策（[ImportConflictDecision]）：
     * - Replace：刪除既有歌單（含其項目）並以原名交易性重建
     * - KeepBoth：以「原名 (2)」「原名 (3)」...尋找未使用名稱建立
     * - Cancel：中止後續所有歌單處理，先前已匯入者保留
     *
     * [onConflict] 只在該筆歌單名稱與既有歌單衝突時被呼叫；呼叫時 import 流程
     * 暫停等待決策。未勾選「全部套用」時 UI 應逐個詢問；[ImportConflictInfo.conflictIndex]
     * 為 1-based 依處理順序遞增、[ImportConflictInfo.totalConflicts] 為匯入前預掃到的衝突總數。
     *
     * @param onConflict 衝突決策 callback（suspend，流程暫停等待回傳）
     * @return null = JSON 結構無法辨識；否則回傳匯入結果統計（即使中途取消亦非 null）。
     */
    suspend fun importPlaylistFromJson(
        json: String,
        onConflict: suspend (info: ImportConflictInfo) -> ImportConflictDecision
    ): PlaylistImportResult?
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

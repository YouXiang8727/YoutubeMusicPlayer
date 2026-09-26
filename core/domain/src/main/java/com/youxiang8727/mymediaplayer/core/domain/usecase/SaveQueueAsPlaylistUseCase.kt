package com.youxiang8727.mymediaplayer.core.domain.usecase

import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.toPlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * 「把目前的播放佇列存成一個新的播放清單」結果。
 *
 * 三種情形對應三種 UI 動作（見 `docs/ARCHITECTURE.md` §3）：
 * - [Success]：snackbar 顯示「已建立「X」」（[Success.mergedCount] > 0 時追加
 *   「，其中 N 首重複曲目已合併」）。
 * - [NameConflict]：沿用既有重名提示（直接顯示 [NameConflict.message]）。
 * - [Failure]：顯示失敗原因（[Failure.message]）。
 */
sealed interface SaveQueueAsPlaylistResult {

    /**
     * 建立成功。
     *
     * @param itemCount 實際寫入歌單的**去重後**曲目數（= [Success.mergedCount] 的母體）。
     * @param mergedCount 因佇列內重複而**被折疊**的曲目筆數。
     *        歌單語意是「收藏」——同一首歌收藏兩次沒有意義，且 `playlist_items`
     *        主鍵為 (playlistId, videoId) 本身也無法存放同一首歌兩列。
     *        但**不靜默**：此數值必須如實告知使用者，否則使用者會困惑歌單為何變短。
     */
    data class Success(
        val playlistId: Long,
        val playlistName: String,
        val itemCount: Int,
        val mergedCount: Int
    ) : SaveQueueAsPlaylistResult

    /** 歌單名稱已存在（[com.youxiang8727.mymediaplayer.core.domain.usecase.PlaylistNameConflictException] 的領域化呈現）。 */
    data class NameConflict(val name: String, val message: String) : SaveQueueAsPlaylistResult

    /** 佇列為空或寫入失敗。[message] 為可直接顯示的失敗原因。 */
    data class Failure(val message: String) : SaveQueueAsPlaylistResult
}

/**
 * 把目前播放佇列（[PlayQueueItem] 列表）存成一個新的播放清單。
 *
 * 領域規則（不放在 Repository / Dao，因為它們只是儲存機制）：
 * 1. **去重保留首次出現**：`distinctBy { videoId }`，即保留佇列中較前的那筆。
 * 2. **`addedAt` 反向遞增以保序**：讀取端 `observePlaylistItems` 為
 *    `ORDER BY addedAt DESC`（最新在前），與佇列的播放順序相反，故存入時令
 *    `addedAt = base - index`，使讀出順序**正好等於佇列順序**。
 *    `base` 於本 UseCase 取**一次**（若在迴圈內逐次取，同毫秒內順序不確定）。
 *    後續使用者手動加歌時 `addedAt` 為當下時間，自然排最前面，符合既有 DESC 語意。
 * 3. **空佇列視為失敗**：沒有東西可存，不建立空歌單。以 [SaveQueueAsPlaylistResult.Failure]
 *    回傳而非拋例外——這是可預期的邊界條件（UI 應在佇列為空時隱藏入口），
 *    不是異常；用回傳值表達可讓 UI 以單一 when 窮舉分支，不必額外 try/catch。
 */
class SaveQueueAsPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    /**
     * @param name 新歌單名稱（內部 trim，空白原樣傳遞由 Repository 決定語意）。
     * @param queue 目前播放佇列（順序 = 播放順序；允許重複）。
     */
    suspend operator fun invoke(
        name: String,
        queue: List<PlayQueueItem>
    ): SaveQueueAsPlaylistResult {
        if (queue.isEmpty()) return SaveQueueAsPlaylistResult.Failure(EMPTY_QUEUE_MESSAGE)

        val trimmedName = name.trim()
        val deduped = queue.distinctBy { it.videoId }
        val mergedCount = queue.size - deduped.size
        val base = System.currentTimeMillis()
        val items: List<PlaylistItem> = deduped.mapIndexed { index, queueItem ->
            queueItem.toPlaylistItem(addedAt = base - index)
        }

        return try {
            val playlistId = repository.createPlaylistWithItems(trimmedName, items)
            SaveQueueAsPlaylistResult.Success(
                playlistId = playlistId,
                playlistName = trimmedName,
                itemCount = items.size,
                mergedCount = mergedCount
            )
        } catch (e: PlaylistNameConflictException) {
            // 重名為資料層可預期之結果（非故障），領域化為專屬分支，
            // UI 得以 when 窮舉取代 catch 型別判斷；message 直接沿用既有可顯示文案。
            SaveQueueAsPlaylistResult.NameConflict(name = e.name, message = e.message.orEmpty())
        } catch (e: CancellationException) {
            // 取消是結構化並發的「訊號」而非可顯示的錯誤：必須原樣往上穿透。
            // CancellationException 繼承自 IllegalStateException → RuntimeException → Exception，
            // 若無此分支，下面的 catch (e: Exception) 必定會吞掉它，導致呼叫端（如 viewModelScope）
            // 以為自己還活著而繼續執行後續程式碼——使用者已離開頁面卻收到失敗 snackbar。
            // 此分支看似冗餘，但刪掉即破壞結構化並發，勿與下方合併。
            throw e
        } catch (e: Exception) {
            SaveQueueAsPlaylistResult.Failure(e.message ?: "未知錯誤：${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val EMPTY_QUEUE_MESSAGE = "播放佇列為空，沒有可儲存的曲目"
    }
}

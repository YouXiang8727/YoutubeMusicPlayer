# MyMediaPlayer 架構文件

> Stack：Kotlin 2.2 + Compose (BOM) + Hilt + Room + Retrofit/OkHttp + Media3 ExoPlayer/MediaSession + NewPipeExtractor
> Build：Gradle 9.4 / AGP 9.2（內建 Kotlin）/ JDK 21

---

## 1. 模組總覽

```
:app                 # 容器：Application、MainActivity、Navigation 圖、Manifest 聚合
├─ feature:search    # 搜尋頁（Screen + ViewModel + 搜尋紀錄 UI）→ 依賴 feature:playlist（PlaylistPickerSheet）
├─ feature:discover  # 探索頁（熱門音樂榜單，Screen + ViewModel；2026-09 由 search 遷出獨立）→ 依賴 feature:playlist
├─ feature:playlist  # 播放清單（列表頁 + 詳情頁 + 共用 BottomSheet/Dialog）
├─ feature:player    # 無全螢幕播放頁；提供播放佇列/控制器（PlayerViewModel + PlaybackIntent）、MiniPlayerBar、MusicService（前景服務、背景音訊）→ 依賴 feature:playlist
├─ core:ui           # Material Theme、共用樣式（DurationBadge／VideoThumbnail／VideoThumbnailWithBadge 等共用 Composable）
├─ core:domain       # 純 Kotlin：Model、Repository interface、UseCase、PlayerController 介面（零 Android 依賴）
├─ core:data         # Room、Retrofit/OkHttp、NewPipe 解析、Repository 實作、Hilt DataModule
└─ core:common       # DispatcherProvider 等跨層小工具（純 Kotlin）
```

## 2. 依賴規則（單向，物理強制）

```
:app ──▶ :feature:search  ──▶ :feature:playlist
    └──▶ :feature:discover ──▶ :feature:playlist
    └──▶ :feature:player  ──▶ :feature:playlist     :feature:* ──▶ :core:ui
    └──▶ :feature:playlist                          :feature:* ──▶ :core:domain
                                                     :core:data  ──▶ :core:common
             :core:domain ◀──────────────────────── :core:data
             （data 實作 domain 的 interface）
```

| 規則 | 違反後果 |
|------|---------|
| feature 不得依賴 core:data | build.gradle.kts 沒有此依賴 → import 直接編譯失敗 |
| core:domain 不得 import android.* | 純 Kotlin module，無 Android classpath |
| UI 層不得 new Dao / Api / ExoPlayer | 只能注入 UseCase（Hilt graph 保證） |

## 3. 各層職責與關鍵類別

### core:domain（純 Kotlin）
- `core.domain.model.VideoResult` / `Playlist` / `PlaylistItem`：領域模型（無 Room/序列化標註）。`VideoResult.duration: String?` 與 `PlaylistItem.duration: String?` 為**顯示用長度字串**（例 "3:45"、"1:02:03";null = 未知/直播/舊資料未記錄），由資料層解析 InnerTube `lengthText` 填值，`VideoResult.toPlaylistItem()` 會帶入。`PlaylistItem.streamFailedAt: Long?`（null=無失敗）為**最近一次播放失敗時間戳**（MusicService 於**任何**播放錯誤（403/IO 等）時寫入，不只是 403），供播放清單 UI 顯示錯誤標記；該曲**任一次播放成功**（ExoPlayer READY，含手動點播、自動續播、403 重解析成功）時由 MusicService 清除為 null。僅針對 Room 播放清單（暫時性佇列無 Room 表不適用）
- `core.domain.model.VideoSearchPage`：搜尋結果一頁（`results` + `nextPageToken`，null = 已到底）；分頁由 continuation token 控制，不再有每頁上限
- `core.domain.model.PlayerController`：播放控制介面（跨 feature 共用合約），ViewModel 只注入此介面；`play(videoId, title)` 為 Room 播放清單導向（點播曲在清單中→整份清單起播，否則單曲），`playQueue(items, startIndex)` 為**暫時性佇列**（熱門榜單等非 Room 清單直接以傳入清單起播，播放模式機制共用）
- `core.domain.model.PlayQueueItem`：暫時性佇列項目（videoId + title 純 data class，零序列化標註；提供 `VideoResult.toPlayQueueItem()` 轉換）。傳遞採 controller 層平行 arrays（videoIds/titles）過 Intent，維持 domain 零序列化依賴
- `core.domain.model.PlaybackSnapshot` / `RepeatMode`：播放狀態快照與循環模式枚舉
- `core.domain.repository.VideoRepository` / `PlaylistRepository`：interface（`VideoRepository.search(query, continuationToken: String? = null): Result<VideoSearchPage>`）。`PlaylistRepository` 含 `observePlaylistItems` / `addItem` / `removeItem` / `clearPlaylist` / `getRandomItem` 及播放失敗標記 `markStreamFailed(videoId, failedAt)` / `clearStreamFailed(videoId)`（videoId 為 playlist_items 全域主鍵，不需 playlistId；供 MusicService 於**任一播放錯誤**時標記、**成功播放（READY）**時清除）
- `core.domain.repository.AudioStreamRepository`：音訊串流解析領域埠（interface）：`resolveAudioUrl(videoId: String, force: Boolean = false): Result<String>`。`force=true` 表示跳過 TTL 快取強制重解析（用於 content URL 403 過期後重試同曲）；快取細節封在 core:data，此介面只暴露語意
- `core.domain.repository.SearchSuggestionRepository`：搜尋建議領域埠（interface）：`suggestions(query: String): List<String>`（回建議字串清單；空白/過短回空、不丟例外，錯誤封在實作）
- `core.domain.repository.SearchHistoryRepository`：搜尋紀錄領域埠（interface）：`add(query)` 記錄一次搜尋（實作端 trim、空白忽略、重複 query 更新時間戳置頂、上限 10 筆汰最舊）、`observeAll(): Flow<List<String>>`（最新在前）、`clear()`。介面 doc 註明防禦由實作層負責
- `core.domain.usecase.*`：SearchVideos（支援續頁 token 透傳）、FetchTrendingSongs（熱門音樂榜單，`region` 參數）、SearchSuggestions（autocomplete；trim＋`MIN_QUERY_LENGTH=1` 防禦，過短不觸網）、CreatePlaylist、RenamePlaylist、DeletePlaylist、ObservePlaylists、ObservePlaylistItems、AddToPlaylist、RemoveFromPlaylist、ClearPlaylist、ShufflePlayPlaylist、AddSearchHistory（薄轉發 repo.add，防禦在實作層）、ObserveSearchHistory（透傳 Flow）、ClearSearchHistory（呼叫 repo.clear）
- 測試：`src/test/` 純 JVM 單元測試（Fake Repository）

### core:data
- `local.PlaylistEntity` / `PlaylistItemEntity`：Room Entity（持久化細節，不外洩）；與 Domain Model 互轉的 mapper 在同檔（`PlaylistItemEntity` 含 `duration` 可空 TEXT 欄位與 `streamFailedAt` 可空 INTEGER 欄位；DB version 5，`MIGRATION_2_3` = `ALTER TABLE playlist_items ADD COLUMN duration TEXT`、`MIGRATION_4_5` = `ALTER TABLE playlist_items ADD COLUMN streamFailedAt INTEGER`）
- `local.SearchHistoryEntity` / `SearchHistoryDao`：搜尋紀錄表（`search_history`，query PK + searchedAt）；DAO 提供 `upsert`（REPLACE 達成去重置頂）、`observeAll`（`ORDER BY searchedAt DESC LIMIT 10` 最新在前）、`trimToLimit`（刪除超出上限的舊列，與 repo 的 `MAX_HISTORY_SIZE=10` 同步）、`clear`；`MIGRATION_3_4` = `CREATE TABLE IF NOT EXISTS search_history (...)`，**不清空既有播放清單資料**
- `local.AppDatabase` / `PlaylistDao`：Room（DB version 5；`PlaylistDao` 含播放清單 CRUD、項目觀察、隨機取曲、播放失敗標記 `markStreamFailed`/`clearStreamFailed`、級聯刪除；`searchHistoryDao()` 提供搜尋紀錄存取）
- `remote.YoutubeSearchApi`：Retrofit（行動版搜尋頁）；`searchHtml(query)` 初次以 GET `results` 抓取；`searchContinuation(clientName, clientVersion, body)` 續頁以 innerTube `POST youtubei/v1/search` 抓取 append-only chunk（baseUrl `https://m.youtube.com/`）
- `remote.YoutubeDataSource`：解析 `ytInitialData` → `VideoSearchPage`。初次搜尋解析 `videoRenderer`（[parseYtInitialData]）；續頁（innerTube POST）解析 `videoWithContextRenderer`（[parseContinuationChunk]，欄位對應不同：videoId 於 `watchEndpoint`、title 於 `headline`）。**duration 解析**：`toVideoResult()` 取 `lengthText.simpleText`（fallback `thumbnailOverlayTimeStatusRenderer.text.simpleText`），`toContinuationVideoResult()` 優先 `thumbnailOverlayTimeStatusRenderer`（fallback lengthText）；共用 `JsonObject.lengthText()`＋`normalizeDuration()`（處理 `\u202F`/`\u00A0`，空白→null）。token 擷取（`continuationItemRenderer.continuationEndpoint.continuationCommand.token`，優先 `CONTINUATION_REQUEST_TYPE_SEARCH`）與解析函式皆 internal 純函數（`extractYtInitialData` / `parseYtInitialData` / `parseContinuationChunk` / `collectVideoRenderers` / `collectContinuationVideoRenderers` / `extractContinuationToken`）供 JVM 測試；每頁輸出 `SearchPaging` log（SUMMARY/DETAIL 全量 videoId:title/WARN token 未推進）
- `remote.TrendingPlaylistDataSource`：熱門音樂榜單（**支援多區域**：台灣／西洋／日本／韓國，各對應 YouTube Music Global Charts 官方頻道 100 首 playlist；`playlistId` 由 `ChartRegion` 提供，例 TAIWAN =「台灣百大熱門音樂影片」）。不走 Retrofit，直接注入 `stream.StreamHttpTransport`（串流鏈現有抽象）POST innerTube browse（baseUrl `https://www.youtube.com/youtubei/v1/browse`，**不需新增 Retrofit**，clean client 身份由 body + headers 自帶）。client 用 **ANDROID_VR**（免 poToken，與串流鏈同家族，版本易腐一起監控；依 A 實證規格其 UA/headers/context 直接 hardcode 於本資料源，**不共享** `InnerTubeClientProfiles`，避免跨檔變更風險），首頁 body 帶 `browseId="VL" + ChartRegion.playlistId`（例 TW：`VLPL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU`）；**分頁聚合至整份**（約 100 首）：抓頁 → 全樹收 `playlistVideoRenderer`（videoId / title.runs[0] / shortBylineText.runs[0] / 首張縮圖）→ 累加去重 → 續頁 token 取 `playlistVideoListRenderer.continuations[0].nextContinuationData.continuation`（**非** continuationItemRenderer）。**duration 解析**：`toPlaylistVideoResult()` 取 `lengthText.simpleText`（fallback `thumbnailOverlayTimeStatusRenderer.text.simpleText`），套 `normalizeDuration()` 正規化。內部純函數 `parsePlaylistPage` / `collectPlaylistVideos` / `extractPlaylistContinuationToken` 供 JVM 測試；分頁上限 `MAX_PAGES=6` 防壞回應無限迴圈。HTTP 非 200 / 非合法 JSON / 重複 token → `Result.failure`（前端可顯示）。舊 charts 鏈（`ChartsApi`/`ChartsDataSource`、`WEB_MUSIC_ANALYTICS`＋`FEmusic_analytics_charts_home`）已於 2026-09 被汰除（恆 400、無 TW），已刪除
- `remote.SearchSuggestionDataSource`＋`YoutubeSearchSuggestionDataSource`：搜尋建議資料源（**無需 API Key**）。端點 `https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&hl=zh-TW&gl=TW&q=<query>`，回傳 JSONP `window.google.ac.h([...])`；走乾淨 `@SuggestionsProfile` OkHttpClient（`di.HttpProfileQualifiers` 新增、`NetworkModule.provideSuggestionsOkHttpClient` 提供，connect/read 皆 5s 短逾時；不需瀏覽器 header）。解析拆成 internal 純函數 `parseCompletionSuggestions`（JSONP 剝殼 → kotlinx.serialization 解析 → 收集每個建議子陣列 index 0 + 去重 + 過濾空白）供 JVM 測試；非 2xx / 解析失敗 → 空清單（不丟例外）
- `remote.stream.AudioStreamSource`：串流解析來源抽象（data 層內部型別），三個實作依優先序組成 fallback 鏈：
  - `NewPipeStreamSource`（主路徑）：NewPipe Extractor
  - `InnerTubeStreamSource`：直連 InnerTube player API（IOS → ANDROID_VR client，免 poToken；client 版本號為易腐常數，集中於共享 `stream.InnerTubeClientProfiles`）
  - `PipedStreamSource`：Piped 公開實例 `/streams/{id}`（最後手段）
- `remote.stream.FallbackStreamResolver`：依序嘗試來源、成功結果 TTL 快取、經 `StreamErrorClassifier` 分類錯誤並聚合可讀訊息；`resolve(videoId, force=false)` 支援 `force=true` 繞過 TTL 快取強制重解析（成功覆寫快取；全失敗清除舊快取，避免死 URL 殘留被再命中）
- `remote.stream.StreamErrorClassifier`：把例外訊息分類成 `StreamFailureKind`（BOT_BLOCK / TRANSIENT / PERMANENT）。raw HTTP 403（`HTTP 403`、`Response code: 403` 等，以 `\b403\b` 字元邊界匹配）歸 **TRANSIENT**（403 本質可重試／換網路，不屬永久結構性失敗；若同時含 bot 封鎖關鍵字則優先歸 BOT_BLOCK）；`describe(attempts)` 依分類聚合出不同使用者可讀提示（bot 封鎖／連結失效 403／網路不穩／一般錯誤）
- `remote.stream.InnerTubeClientProfiles`：免 poToken 的 innerTube client 身分常數（IOS／ANDROID_VR 的 UA、`X-YouTube-Client-Name`、context JsonObject）集中管理，供串流解析鏈 `InnerTubeStreamSource` 使用（熱門榜單資料源依規格 hardcode own ANDROID_VR profile、不共用本常數；易腐版本常數需互相對照更新）
- `remote.NetworkModule`：HTTP client 依用途拆雙 profile（Hilt qualifier 定義於 `di.HttpProfileQualifiers`）：
  - `@BrowserProfile`：掛瀏覽器 UA／Referer／Cookie 攔截器（`YoutubeHeaderInterceptor`），僅供 Retrofit `YoutubeSearchApi` 抓行動版搜尋頁 HTML
  - `@StreamProfile`：乾淨 client（僅逾時設定、無任何攔截器），串流解析鏈專用——NewPipe extractor 的 `remote.OkHttpDownloader`、InnerTube/Piped 的 `stream.OkHttpStreamHttpTransport` 都掛本 profile（熱門榜單走同抽象 `StreamHttpTransport`，不需 Retrofit）
  - `@SuggestionsProfile`：乾淨 client（connect/read 皆 5s 短逾時），搜尋建議端點（Google suggestqueries）專用——不需瀏覽器 header，避免與乾淨 client 混用
  - 教訓：瀏覽器 header 一旦覆蓋 InnerTube client 身份 UA 或 extractor 自帶 UA，串流解析即遭 LOGIN_REQUIRED，故兩 profile 嚴禁混用
- `repository.*Impl`：實作 domain interface（Entity ↔ Domain mapping）；`SearchSuggestionRepositoryImpl` 委派 `SearchSuggestionDataSource`（trim＋空白防禦，不觸網）；`SearchHistoryRepositoryImpl` 委派 `SearchHistoryDao`（add：trim＋空白忽略＋REPLACE upsert＋trimToLimit 上限汰除；observeAll：Entity → query 字串 list，最新在前）
- `di.DataModule`：Database / Dispatcher / Repository 三組綁定（含 `SearchSuggestionRepository`、`SearchSuggestionDataSource`、`SearchHistoryRepository` 的 @Binds；`DatabaseModule` 註冊 `MIGRATION_2_3`＋`MIGRATION_3_4`＋`MIGRATION_4_5` 並提供 `SearchHistoryDao`）

### core:ui
- `core.ui.theme.MyMediaPlayerTheme` / Color / Type
- `core.ui.component.DurationBadge`：影片時長 badge（疊於縮圖右下角，YouTube 慣例；`duration` 為 null/空白時不顯示）——共用元件第二使用點條款而升級（feature:search 搜尋結果／熱門榜單、feature:playlist 歌單詳情共用，見 §5 慣例）
- `core.ui.component.VideoThumbnail`：無狀態影片縮圖元件——統一縮圖 URL 載入／佔位慣例（`url` 非空白走 Coil AsyncImage，placeholder/error 皆以主題色 surfaceVariant；空白 URL 顯示主題色 surfaceVariant 佔位塊）。含 `overlayContent` slot（BoxScope，呼叫方可疊自訂內容到任意角落，預設空）——共用元件第三使用點條款而升級（feature:search / feature:playlist / feature:discover 三處統一，見 §5 慣例）
- `core.ui.component.VideoThumbnailWithBadge`：VideoThumbnail 薄包（縮圖＋右下角 DurationBadge），透過 overlayContent slot 疊加 badge，尺寸由呼叫方 modifier 控制
- `core.ui.component.VideoRailSkeleton`：影片橫向 rail 載入骨架（skeleton placeholder——標題列 placeholder + 橫向卡片，整層 pulse 透明度動畫由 `animation-core` 提供，未新增依賴）；卡片輪廓與 feature:discover `ChartRailItem`／`ChartRail` 對齊（寬 140dp、縮圖高 80dp、LazyRow 間距 12dp），減少 loading→loaded 佈局跳動。首用點 feature:discover 熱門榜單 rail；命名採通用語意，未來搜尋結果等橫向 rail 載入處可複用

### feature:search | discover | playlist | player
- `*Route`（Hilt 容器）→ `*Screen`（無狀態 Composable）＋ `*ViewModel`（StateFlow + Intent）
- `feature.search` 搜尋頁（Screen + ViewModel）：搜尋框＋「搜尋」按鈕＋搜尋結果（`VideoCard`＋`LoadMoreFooter`，append 去重＋token 未推進視為到底防禦）。**搜尋建議（autocomplete）**：`SearchViewModel` 注入 `SearchSuggestionsUseCase`，輸入非空白查詢經 `SearchIntent.QueryChanged` 送 `MutableSharedFlow` → `debounce(300ms)`（`SUGGESTION_DEBOUNCE_MS`）＋`collectLatest` 抓建議，寫入 `SearchUiState.suggestions: List<String>`；「epoch 無效化」機制（搜尋/清除/點建議時 `suggestionEpoch++`）使 debounce 管道中仍在等的遲到回應自動作廢，不覆蓋搜尋後狀態。`SearchScreen` 於搜尋框下方以 `SuggestionList`（Surface）顯示建議（非空時），點建議（`SearchIntent.SelectSuggestion`）填回搜尋框並直接觸發搜尋、隱藏建議；明確「搜尋」按鈕與空白清除同為隱藏建議。空/過短查詢與失敗由 UseCase/Repository 防禦回空清單（不觸網、不崩潰）。**搜尋紀錄（recent searches，2026-09）**：空狀態（`searched == false`）顯示「最近搜尋」區塊——`SearchUiState.history: List<String>` 由 `ObserveSearchHistoryUseCase` 觀察 Room `search_history` 表（最新在前、最多 10 筆，trim／空白忽略／去重置頂／上限汰除由 core:data `SearchHistoryRepositoryImpl` 負責）；點擊任一紀錄＝填回搜尋框並直接搜尋（行為等同 `SelectSuggestion`，同時更新時間戳置頂）；「清除全部」`TextButton` → `SearchIntent.ClearHistory`；`Search`／`SelectSuggestion` 提交時以 `AddSearchHistoryUseCase` 記錄（空白由 repository 擋）。無紀錄時顯示「輸入關鍵字開始搜尋」提示。**清除搜尋回空狀態**：搜尋框空白時顯示「✕」清除鈕、已搜尋時系統返回鍵被 `BackHandler(enabled=searched)` 攔截為清除搜尋（回到空狀態顯示最近搜尋）；`searched=false` 時維持系統預設退出。**加入播放清單**：結果卡片「＋」→ `showPickerVideo`→`PlaylistPickerSheet`（共用 feature:playlist）。所有縮圖以共用 `core:ui VideoThumbnailWithBadge`（縮圖＋DurationBadge）顯示影片時長
- `feature.discover` 探索頁＝熱門音樂榜單（2026-09 由 feature:search 遷出獨立成頁，搜尋頁專注搜尋＋紀錄）：四區域（台灣／西洋／日本／韓國，依 `ChartRegion.DISPLAY_ORDER`）rail（前 `TRENDING_RAIL_LIMIT=10` 筆，縮圖＋歌名＋歌手，不顯示名次）＋「查看完整榜單」切換完整清單（module 內 state `fullChartRegion: ChartRegion?` 切換，**不新增 nav route**；非 null 僅顯示該區完整清單，null 時四區 rail 以垂直 LazyColumn 並排，無巢狀）。`DiscoverViewModel` 注入 `FetchTrendingSongsUseCase`，init 依 `DISPLAY_ORDER` 自動載入各區域，狀態 `trendingByRegion: Map<ChartRegion, TrendingState>`（`TrendingState(items/loading/error)`，各區域獨立載入、單一區域失敗僅寫入該區域 `error` 不影響其他）；載入中顯示 region-level `core:ui VideoRailSkeleton`（標題列＋rail 卡片骨架 pulse 動畫，取代早期 4 區並排的大間距 CircularProgressIndicator；loading 僅發生於 rail 模式，完整榜單展開無 loading 狀態，故不需要 full-chart skeleton 變體）；`DiscoverIntent.TrendingRetry` 內嵌重試（各區域 `loading` 旗標即重入 guard）。點任一歌曲以**整份榜單**為暫時性佇列起播，經 `DiscoverRoute(onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit)`（型別只用 core:domain `PlayQueueItem`，由 app 容器層接線至 `PlaybackIntent.PlayList`，feature:discover 不依賴 feature:player）。「＋」加入播放清單（`ChartRailItem` 縮圖右下角獨立 24dp 區塊不觸發整卡播放、`ChartDetailRow` 行尾 `IconButton(Add)`）共用 `PlaylistPickerSheet` 流程。縮圖（rail／完整榜單）以共用 `core:ui VideoThumbnail`（rail 透過 overlayContent slot 疊 DurationBadge+Add 按鈕）
- `feature.player.PlayerViewModel.PlaybackIntent.PlayList(entries, startIndex)`：暫時性佇列播放意圖（熱門榜單等非 Room 清單），由 activity scope 的 PlayerViewModel 轉 call `PlayerController.playQueue(entries, startIndex)`（與 MusicService `ACTION_PLAY_QUEUE` 鏈路對接，見 §3 core:data 播放佇列）
- `feature:playlist` 提供共用 `PlaylistPickerSheet`（BottomSheet）與 `CreatePlaylistDialog`，供 search/player 共用（故 search/player 依賴 playlist）
- `feature:playlist` 歌單詳情頁（`PlaylistDetailScreen`/`PlaylistDetailCard`）：縮圖以共用 `core:ui VideoThumbnailWithBadge`（縮圖＋DurationBadge）顯示 `item.duration`（舊資料 null 不顯示）。**播放失敗標記**：`PlaylistDetailUiState` 以 derived `failedCount`（items 中 `streamFailedAt != null` 計數）驅動——卡片對應 `streamFailedAt != null` 的歌曲加 `MaterialTheme.colorScheme.error` 紅色邊框＋「播放失敗」label，列表頂部（LazyColumn 第一個 item）顯示「有 N 首歌曲在播放時曾發生問題」error 色提示（N=0 時不顯示）
- `feature.player.MiniPlayerBar`：前景常駐迷你播放列（隨機／前後曲／循環／歌名／進度條），由 app 層掛載於底部
- `feature.player.service.MusicService`：Media3 `MediaSessionService`
  - 播放佇列：點播曲目在播放清單中 → 整份清單從該曲起播；否則單曲（`playback.PlaybackQueueBuilder` 純函數，有單元測試）
  - 暫時性佇列（熱門榜單）：經 `PlayerController.playQueue` → `ACTION_PLAY_QUEUE`（平行陣列 videoIds/titles 過 Intent）直接起播，**不查 Room**；`PlaybackQueueBuilder.buildFromEntries` 純函數（startIndex clamp、空清單回 size 0），有單元測試
  - 串流 URL 以 `ResolvingDataSource` 於載入當下逐首解析（NewPipe）
  - **播放失敗標記（任一錯誤）**：`onPlayerError` 攔截 Media3 `Player.Listener`，**所有播放錯誤**（不限 403）都以 fire-and-forget 呼叫 `PlaylistRepository.markStreamFailed(currentVideoId, now)` 持久化該曲失敗標記（`pendingFailureMarks` 追蹤未落地 job，供 READY 時取消）。**content URL 403 額外自動恢復**：辨識 cause chain 中的 `HttpDataSource.InvalidResponseCodeException`（responseCode == 403）後分層處理——① 失效該 videoId session 級 memoize + `resolveAudioUrl(videoId, force=true)` 強制重解析同曲，成功則回到原 position 重試同一首；② 重新解析仍失敗 → 依 `repeatMode` 切歌（`REPEAT_MODE_ONE` 重播同一首；`REPEAT_MODE_ALL/OFF` 走 `seekToNextMediaItem()`，無下一首則回繞或自然停）。非 403 錯誤不攔截，維持既有 snapshot/error 顯示。**失敗標記清除**：`onPlaybackStateChanged(READY)`（該曲播放成功，無論手動點播/自動續播/403 重解析成功）時對現行 mediaId 取消 pending mark job 並 `clearStreamFailed`（mark 於 error 時提交、clear 於 READY 時提交，前者必然先入 Room 單線程 transaction executor；取消 pending job 防交錯。暫時性佇列對 Room UPDATE 為 no-op，自然只影響 Room 清單）。併發防護：`pending403Handling` 防同 videoId 重入，`retryCounts` 上限 `MAX_403_RETRY_PER_VIDEO=3` 防無限重試（READY 成功播放時重設該曲計數）
  - 通知由自訂 `service.CustomMediaNotificationProvider`（`DefaultMediaNotificationProvider` 子類別）產生：覆寫 `getMediaButtons` 回傳**固定按鈕序列** [上一首, 播放/暫停, 下一首, 隨機, 循環]，不呼叫父類別那組會補系統 prev/next 的邏輯——上一首／下一首為常駐按鈕（`SLOT_BACK` / `SLOT_FORWARD`，custom session command，不受 `hasPreviousMediaItem()` / `hasNextMediaItem()` 過濾影響，清單邊界或單曲時仍固定顯示；無上/下一首時落點為重播目前曲目開頭）；播放/暫停不設 slots（走 Media3 預設 `SLOT_CENTRAL`，play/pause icon 依播放狀態切換）；隨機與循環走 `SLOT_OVERFLOW`（展開區，`CommandButton.ICON_*` 依播放模式切換：`ICON_SHUFFLE_ON/OFF`、`ICON_REPEAT_ALL/ONE`，`ICON_SHUFFLE_OFF` 為官方 disabled 色）。compact view 依 slots 判定固定為 [上一首, 播放/暫停, 下一首]，**不會重複**系統 prev/next；通知本體點擊經 `MediaSession.setSessionActivity`（contentIntent）將 App 帶回前景。
    - **重複 icon 雙層解**：方案 A（`CustomMediaNotificationProvider` 覆寫 `getMediaButtons`）處理「notification 自訂 actions」層；A2（`MusicService.sessionCallback.onPostConnect` 呼叫**廣播級** `mediaSession.setMediaButtonPreferences(buildCustomLayout(session))`，與 `setCustomLayout` 共用同一份含 SLOT_BACK/FORWARD 的按鈕清單）處理「SystemUI MediaStyle media surface」層——Media3 `MediaSessionLegacyStub`（line 1923-1930）在 media button preferences 非空且 custom layout 含 SLOT_BACK/FORWARD 時，會從 `PlaybackState.actions` 移除 `ACTION_SKIP_TO_PREVIOUS/NEXT`，使 SystemUI 不再渲染系統 prev/seekbar/next（是否生效需實機 `dumpsys media_session` 驗證）
  - Service 的 Manifest 宣告在 feature 模組內（manifest merging 併入 app）；POST_NOTIFICATIONS 由 app 於啟動時動態請求

### :app
- `Routes` + NavHost；bottom bar（搜尋 / 探索 / 播放清單）——`Routes.SEARCH` 為 start destination，三個頂層目的地共用 `popUpTo(SEARCH){saveState}`＋`launchSingleTop`＋`restoreState` 的 tab 切換模式
- 播放清單導航：`playlist_list`（列表頁）→ `playlist_detail/{playlistId}`（詳情頁，含隨機播放）
- 播放接線：`SearchRoute(onPlayVideo)` → `PlaybackIntent.Play`；`DiscoverRoute(onPlayChartQueue)` → `PlaybackIntent.PlayList`（暫時性佇列）
- 權限宣告、Application (`@HiltAndroidApp`)

## 4. 資料流

```
UI Intent ──▶ ViewModel.onIntent ──▶ UseCase ──▶ Repository(interface)
                                                    │
UiState ◀── StateFlow ◀── ViewModel ◀── Flow ◀──┤
                                                     ├─▶ Room（playlist 表 / search_history 搜尋紀錄表）
                                                     └─▶ Retrofit/NewPipe（YouTube）
播放：PlayerViewModel ──▶ PlayerController(core:domain) ──▶ MediaController ──▶ MusicService(MediaSession) ──▶ ExoPlayer
      （PlayerController 介面在 core:domain，實作 MediaControllerPlayerController 在 feature:player）
      （前景 MiniPlayerBar 與背景通知共用同一 MediaSession 狀態源）
```

## 5. 新功能落地路徑（SOP）

1. **新頁面**：建 `feature:xxx` 模組（複製任一 feature 的 build.gradle.kts）→ `settings.gradle.kts` include（A 操作）→ app NavHost 加 route。
2. **新資料來源**：remote 加 DataSource → repository impl + domain interface → DataModule 綁定 → UseCase 包裝。
3. **新資料表**：Entity + Dao 於 `core/data/local` → AppDatabase version++ → Entity↔Domain mapper。
4. **共用 UI 元件**：先放所屬 feature；第二個地方要用時才升級到 `core:ui`（Rule of Three 從寬）。

## 6. 建置指令

```bash
./gradlew assembleDebug          # 全模組編譯
./gradlew test                   # 所有單元測試（含 core:domain 純 JVM 測試）
./gradlew :app:assembleDebug     # 只編 app 及其依賴
./gradlew :core:domain:test      # 只跑 domain 測試
```

本機若 PATH 的 java 是 8：以 JDK 21 執行（Android Studio JBR 或 ~/.jdks）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
```

## 7. 風險登記簿

| 風險 | 影響 | 對策 | Owner |
|------|------|------|-------|
| YouTube 改版使 ytInitialData / NewPipe 失效；匿名 IP 遭 bot 偵測封鎖（LOGIN_REQUIRED「Sign in to confirm you're not a bot」） | 搜尋、播放全掛 | StreamResolver 改多層 fallback：NewPipe → InnerTube 直連（IOS／ANDROID_VR client，免 poToken）→ Piped 實例，成功結果 TTL 快取；錯誤分類聚合顯示於通知。extractor 升級由 A 走統一 PR；poToken/BotGuard WebView 方案成本高暫緩（見 TEAM.md §7） | B |
| Foreground Service 政策（API 34+） | 上架審查 / 背景 被殺 | 已宣告 `foregroundServiceType=mediaPlayback`；未來接 MediaSessionService | B |
| 串流 URL 有時效性 | 暫停過久後恢復失敗 | 失敗時重新 resolve（MusicService 已有 job cancel/re-run 機制） | B |
| content URL（googlevideo.com）播放當下 403 | 死 URL 卡在 session 內不自動重解析、不切歌，顯示 Source error | 403 由 `StreamErrorClassifier` 歸 TRANSIENT；MusicService `onPlayerError` 在**任一播放錯誤**時即 `markStreamFailed` 持久化失敗標記（`streamFailedAt`，歌單 UI 紅框提示）；403 額外走 失效 memoize＋`resolveAudioUrl(force=true)` 重解析同曲，仍失敗則依 repeatMode 切歌；該曲**成功播放（READY）**時取消 pending mark 並 `clearStreamFailed`。bounded retry（`MAX_403_RETRY_PER_VIDEO=3`）+ `pending403Handling` 防無限重試。NewPipe/InnerTube 解析改動需實機煙霧測試驗證 | B |
| WebView 播放器與音訊服務同時發聲 | 使用者困惑 | Roadmap：以 ExoPlayer 畫面取代 WebView | C+B |
| 通知權限（Android 13+）未授予 | 背景播放時通知不出現（音訊不受影響） | App 啟動時動態請求 POST_NOTIFICATIONS；拒絕僅影響通知與鎖屏控制 | C |
| 逐首解析串流 URL 的切歌延遲 | 下一首開始前有解析等待（NewPipe 網路往返） | ResolvingDataSource 快取已解析結果；buffering 狀態由系統 UI 呈現；必要時改預先解析下一首 | B |
| 搜尋續頁走 innerTube `POST youtubei/v1/search`（MWEB client context，易腐路徑） | YouTube 改版可能使續頁失效 | 2026-08 多頁實測定案：GET `results?continuation=` 會**整頁重新排序回傳**（與前頁重疊 55~100%，造成「載入更多輪迴」），續頁必須走 POST（append-only，實測重疊 0%、深頁才因結果池枯竭漸增到 5~28%）。續頁 chunk 解析為 internal 純函數，失效時可直接改寫對應函式；ViewModel 已做 append 去重＋token 未推進視為到底的防禦 | B |
| charts innerTube 已死（`WEB_MUSIC_ANALYTICS`＋`FEmusic_analytics_charts_home` 於 2026-09 遭汰除，恆 HTTP 400；charts.youtube.com 官方 `LAUNCHED_CHART_COUNTRIES` 不含 TW） | 熱門榜單失效／空白 | 改官方 YT Music playlist（**多區域**，`playlistId` 定義於 `ChartRegion`：TAIWAN「台灣百大熱門音樂影片」/ WESTERN / JAPAN / KOREA，各 100 首；browseId `VL` + `playlistId`，例 TW `VLPL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU`）。client 用 **ANDROID_VR**（免 poToken，與串流鏈同家族）。ANDROID_VR 版本屬易腐路徑，需與串流鏈一起監控——熱門榜單依規格 hardcode own ANDROID_VR profile（不共享 `InnerTubeClientProfiles`），改版時兩處版本常數互相對照更新；解析為 internal 純函數可快速改寫 | B |
| 影片時長解析依賴 InnerTube `lengthText`/`thumbnailOverlayTimeStatusRenderer` 欄位（易腐路徑，2026-09 實機驗證形狀為 `simpleText`） | 時長 badge 消失（不影響播放） | 解析集中於兩資料源共用純函式（`lengthText()`＋`normalizeDuration()`），改版時直接改寫對應函式；badge 為漸進增強，欄位失效僅 badge 不顯示、不擋其他功能 | B |
| 既有播放清單資料（DB v2 時期加入）無 `duration` 欄位值 | 舊歌單詳情頁不顯示時長（新加入的歌正常） | `MIGRATION_2_3` 以可空 TEXT 加欄位（不 wipe 資料）；舊資料 duration = null，badge 自動隱藏；若要補齊需對舊曲逐一解析（超出目前需求範圍，暫列技術債） | B |
| Google suggestqueries 建議端點（`suggestqueries.google.com/complete/search`）為第三方輕量端點，非官方 API | 端點行為變更／停用時 autocomplete 失效（不影響主搜尋與播放） | 端點極穩定（Google 自家產品共用）；解析為 internal 純函數（`parseCompletionSuggestions`），改版時可直接改寫；失敗回空清單（UI 顯示空建議，不阻斷輸入） | B |

## 8. 歷史決策

- 2026-08：由 single-module `:app` 遷移至 8-module 架構；遷移順序 domain → data → ui → features → app 瘦身，每步 CI 綠燈。

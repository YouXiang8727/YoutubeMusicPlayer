# Changelog（異動紀錄）

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)；
版本語意參考 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

> **維護規則**：所有 merge 進 `master` 的 PR，必須在 `[Unreleased]` 區塊加一筆，
> 類別對應 Conventional Commits（`feat` → Added、`fix` → Fixed、`refactor`/`perf` → Changed、移除 → Removed、文件 → Docs）。
> 發版時由 Tech Lead 把 `[Unreleased]` 改為版本號 + 日期。

## [Unreleased]

### Added
- **播放失敗標記**（歌單內歌曲，播放失敗時可辨識）：`PlaylistItem` 新增 `streamFailedAt: Long?`（最近一次播放失敗時間戳，null=無失敗）；MusicService 於 **任一播放錯誤**（403/IO 等，`onPlayerError`）時 `markStreamFailed` 持久化標記（fire-and-forget，`pendingFailureMarks` 追蹤未落地 job）、該曲**成功播放（ExoPlayer READY，無論手動點播/自動續播/403 重解析成功）**時取消 pending job 並 `clearStreamFailed` 清除（mark 先於 clear 入 Room 單線程 transaction executor，順序保證；暫時性佇列對 Room 為 no-op，僅適用 Room 播放清單）。`core:data` Room `playlist_items` 新增可空 `streamFailedAt` 欄位（DB v4→v5，`MIGRATION_4_5` = `ALTER TABLE ... ADD COLUMN`，不清空既有清單），`PlaylistRepository` 新增 `markStreamFailed`/`clearStreamFailed`，`PlaylistDao` 對應新增兩個 @Query。UI：`PlaylistDetailScreen` 對失敗歌曲加 error 紅框＋「播放失敗」label，列表頂部（LazyColumn 第一個 item）當 `failedCount>0` 顯示「有 N 首歌曲在播放時曾發生問題」提示；`PlaylistDetailUiState` 新增 derived `failedCount`。同步修補 feature/search、playlist、discover 三模組測試 fake 的 interface 空實作。新增 PlaylistRepositoryImplTest（mark/clear）與 PlaylistDetailViewModelTest（failedCount）測試及失敗狀態 Preview

### Fixed
- `DiscoverViewModelTest.observePlaylists` 測試對 `Playlist` 的時脈預設時間戳（`createdAt`/`updatedAt`）於斷言處**重建比較**造成 flaky（初始值與斷言各 new 一次，跨毫秒即產生不同 timestamp → 同毫秒偶過、跨毫秒必掛；CI 全新執行 289 tasks 時現形）：改為 initial 與斷言共用同一 instance

### Added
- 影片縮圖抽為 `core:ui` 共用元件 `VideoThumbnail` / `VideoThumbnailWithBadge`（Rule of Three 第三使用點升級）：`VideoThumbnail` 提供 `overlayContent` slot 提升可組合性（discover rail 自訂右下角 badge+按鈕、detail row 改用 `VideoThumbnailWithBadge`）；`feature:search` / `feature:playlist` / `feature:discover` 三處內聯縮圖實作統一改用共用元件（Coil API 隨之收斂至 `core:ui`，三個 feature 移除自身 coil 宣告）。修正 search 縮圖佔位色由硬編碼 `Color.LightGray` 改為主題色 `surfaceVariant`，並補上 placeholder/error（與 playlist/discover 一致）
- 熱門榜單遷移至獨立**探索頁**（`feature:discover` 新模組）：trending UI 與狀態（`DiscoverScreen`/`DiscoverViewModel`、`TrendingState`、`fetchTrending()` per-region 邏輯、`TrendingRetry`）自 `feature:search` 完整遷出；搜尋頁專注搜尋＋紀錄。`MainActivity` 底部導覽列由 2 tab 改為 **3 tab（搜尋／探索／播放清單）**，`Routes.DISCOVER` 串接 `DiscoverRoute(onPlayChartQueue)` → `PlaybackIntent.PlayList`（暫時性佇列起播方式不變）；探索 tab 以 `Icons.Filled.Star` 圖示（core icons 集內無 Explore 圖示，未引入 extended icons 依賴）。新增 `DiscoverViewModelTest`（11 案例：init 依 DISPLAY_ORDER 載入、per-region 失敗隔離、retry 防重入、playlist 流程）與 4 組 `DiscoverScreen` Preview（deep/light）。`SearchViewModel`/`SearchScreen` 移除全部 trending 程式碼（`trendingByRegion`/`TrendingState`/`TrendingRetry`/trending composables/trending previews 與 `SearchRoute(onPlayChartQueue)` 參數）
- 搜尋頁**最近搜尋** UI（資料鏈見下一條目）：`SearchUiState` 新增 `history: List<String>`（`ObserveSearchHistoryUseCase` 觀察，最新在前）；空狀態（`searched == false`）顯示「最近搜尋」區塊——點擊紀錄＝填回搜尋框並直接搜尋（等同 `SelectSuggestion`，同時更新時間戳置頂）、「清除全部」→ `SearchIntent.ClearHistory`、無紀錄時顯示「輸入關鍵字開始搜尋」提示；`Search`／`SelectSuggestion` 提交時以 `AddSearchHistoryUseCase` 記錄。`BackHandler` 清除搜尋後回到空狀態（最近搜尋）。新增 7 個 ViewModel 歷史測試案例（init 觀察／外部推送同步／Search 記錄／SelectSuggestion 記錄／重複去重置頂／ClearHistory 清空／空白不記錄）與 `SearchScreen - History` Compose Preview（deep/light）
- 搜尋紀錄資料鏈：`core:domain` 新增 `SearchHistoryRepository`（`add` 記錄搜尋——實作端 trim＋空白忽略、重複 query 更新時間戳置頂、最多保留 10 筆汰最舊；`observeAll(): Flow<List<String>>` 最新在前；`clear()`）與 `AddSearchHistoryUseCase`／`ObserveSearchHistoryUseCase`／`ClearSearchHistoryUseCase`（薄轉發，防禦在實作層）；`core:data` 新增 `SearchHistoryEntity`（query PK + searchedAt）＋`SearchHistoryDao`（`upsert` REPLACE／`observeAll` `ORDER BY searchedAt DESC LIMIT 10`／`trimToLimit`／`clear`）＋`SearchHistoryRepositoryImpl`；AppDatabase 升 **v3→v4**（`MIGRATION_3_4` = 新增 `search_history` 表，**不清空既有播放清單**）；Hilt `RepositoryModule` 補 `SearchHistoryRepository` @Binds、`DatabaseModule` 補 `SearchHistoryDao` @Provides。新增 `SearchHistoryUseCasesTest`（3 案例，Fake repository）與 `SearchHistoryRepositoryTest`（6 案例，Fake Dao 含 LIMIT/trim/REPLACE 語意）。UI（搜尋頁歷史紀錄串接）由 A 後續派工 C
- 搜尋建議（autocomplete）UI：`feature:search` `SearchScreen`/`SearchViewModel` 接入 `SearchSuggestionsUseCase`——輸入非空白查詢時 debounce 300ms 後抓建議（`SUGGESTION_DEBOUNCE_MS`，`collectLatest`＋epoch 無效化機制防止遲到回應覆蓋），建議以 `SuggestionList` 顯示於搜尋框下方；點建議填回搜尋框並直接觸發搜尋、隱藏建議；明確「搜尋」按鈕與空白清除輸入皆隱藏並無效化管道（`SearchIntent.SelectSuggestion` 新增）。`SearchUiState` 新增 `suggestions: List<String>`；空/過短查詢與失敗由 UseCase/Repository 防禦回空清單（不觸網、優雅降級）。新增 7 個 ViewModel 單元測試案例（debounce/逾期/空白/點擊/明確搜尋/失敗/遲到回應）與 `SearchScreen - Suggestions` Compose Preview（深/淺色）。資料鏈部分見下方既有 `SearchSuggestionRepository`/`SearchSuggestionsUseCase` 條目
- 搜尋建議（autocomplete）資料鏈：`core:domain` 新增 `SearchSuggestionRepository`（`suggestions(query): List<String>`，空白/過短回空、不丟例外）與 `SearchSuggestionsUseCase`（trim＋`MIN_QUERY_LENGTH=1` 防禦，過短不觸網）；`core:data` 新增 `SearchSuggestionDataSource`＋`YoutubeSearchSuggestionDataSource`（Google suggestqueries 端點 `suggestqueries.google.com/complete/search?client=youtube&ds=yt`，無需 API Key；走新增 `@SuggestionsProfile` 乾淨 OkHttpClient，connect/read 皆 5s 短逾時；JSONP 解析拆成 internal 純函數 `parseCompletionSuggestions`：剝殼 → 收集子陣列 index 0 → 去重過濾，非 2xx/解析失敗回空清單）＋`SearchSuggestionRepositoryImpl`；Hilt `RepositoryModule` 補兩個 `@Binds`。新增 `SearchSuggestionsUseCaseTest`（5 案例）與 `SearchSuggestionRepositoryImplTest`（4 案例）、`YoutubeSearchSuggestionDataSourceTest`（6 案例，純 JVM 解析測試）。UI（ViewModel/Screen）由 A 後續派工 C
- 搜尋結果／熱門榜單／歌單詳情顯示影片時長：`VideoResult` 與 `PlaylistItem` 新增 `duration: String?`（顯示用字串如 "3:45"，null = 未知/直播/舊資料）；資料層解析 InnerTube `lengthText`（fallback `thumbnailOverlayTimeStatusRenderer.text`，`YoutubeDataSource` 兩個 renderer ＋ `TrendingPlaylistDataSource` 共用 `lengthText()`/`normalizeDuration()` 純函式，處理 `\u202F`/`\u00A0`）。Room `playlist_items` 新增可空 `duration` 欄位（DB v2→v3，`MIGRATION_2_3` = `ALTER TABLE ... ADD COLUMN`，不清空既有清單）。UI 以新共用元件 `core:ui DurationBadge` 疊於搜尋結果、熱門 rail、熱門完整榜單、歌單詳情頁的縮圖右下角（badge 第二使用點即升級至 core:ui，符合 Rule of Three 慣例）
- 熱門榜單（推薦歌曲）可加入播放清單：`ChartRailItem` 縮圖右下角「＋」（獨立可點擊 24dp 區塊，不誤觸整卡播放）、`ChartDetailRow` 行尾 `IconButton(Add)`；與搜尋結果共用 `PlaylistPickerSheet`＋`CreatePlaylistDialog` 流程（可加入現有清單或新建）
- 搜尋後可返回「推薦歌曲」：`SearchViewModel` 收到空白 `QueryChanged` 即重置搜尋狀態回熱門榜單（`trendingByRegion` 快取保留直接顯示）；搜尋框空白時顯示「✕」清除鈕；已搜尋時系統返回鍵被 `BackHandler(enabled=searched)` 攔截為清除搜尋（未搜尋時維持系統預設退出）。新增 `SearchViewModelTest` 驗證重置行為與快取保留
- 熱門音樂榜單資料鏈（備案，取代 charts）：`core:data` 新增 `TrendingPlaylistDataSource`（官方 YouTube Music playlist「台灣百大熱門音樂影片」，owner = YouTube Music Global Charts，browseId `VLPL4fGSI1pDJn4eKyK8APGwl0S0wgyHvQyU`；client 用 ANDROID_VR，免 poToken、與串流鏈同家族）。不走 Retrofit，直接注入 `stream.StreamHttpTransport` POST innerTube browse；**分頁聚合至整份**（~100 首）：全樹收 `playlistVideoRenderer`（videoId / title.runs[0] / shortBylineText.runs[0] / 首張縮圖）→ 累加去重 → 續頁 token 取 `playlistVideoListRenderer.continuations[0].nextContinuationData.continuation`；`MAX_PAGES=6` 防壞回應無限迴圈，HTTP 非 200 / 非合法 JSON / 重複 token → `Result.failure`。internal 純函數供 JVM 測試。`VideoRepository.fetchTrendingSongs(region)` 改走本資料源；舊 charts 鏈（`ChartsApi`/`ChartsDataSource`、`WEB_MUSIC_ANALYTICS`＋`FEmusic_analytics_charts_home`）已於 2026-09 被 YT 汰除（恆 HTTP 400、charts 官方國家清單不含 TW），已刪除
- 暫時性播放佇列：`PlayerController.playQueue(items, startIndex)` 實作完成（`MediaControllerPlayerController` → `ACTION_PLAY_QUEUE` 平行陣列過 Intent）；`MusicService.handlePlayQueue` 以 `PlaybackQueueBuilder.buildFromEntries`（startIndex clamp、空清單回 size 0）直接起播、不查 Room
- `feature:search` 熱門音樂榜單（T4/T5）：搜尋空狀態（`searched == false`）顯示「台灣熱門音樂」區塊——rail（前 10 筆，名次＋縮圖＋歌名＋歌手）＋「查看完整榜單」切換完整清單（兩態皆在 module 內以 state 切換，**不新增 nav route**；資料鏈由舊 charts（~50 單頁）改為官方 playlist 分頁聚合後顯示整份 ~100 首，名次由清單索引推導）；`SearchViewModel` 注入 `FetchTrendingSongsUseCase`（init 自動載入 `ChartRegion.TAIWAN`，`trendingItems/trendingLoading/trendingError` 三欄位，新增 `SearchIntent.TrendingRetry` 內嵌重試，失敗不擋搜尋）；點任一歌曲以**整份榜單**為暫時性佇列從該曲起播，經 `SearchRoute(onPlayChartQueue: (List<PlayQueueItem>, Int) -> Unit)`（預設 no-op 保持 feature:search 不依賴 feature:player）；app 容器層 `MainActivity` 已接線（`onPlayChartQueue` → activity scope `PlayerViewModel` → `PlaybackIntent.PlayList` → `PlayerController.playQueue`）；新增 4 個 ViewModel 測試案例與 4 組榜單 Preview（載入中／rail／完整清單／失敗，各深淺色）
- 熱門音樂榜單支援**多區域**：`ChartRegion` 擴充 WESTERN／JAPAN／KOREA，各區域自帶 `playlistId`（`DISPLAY_ORDER` 定義顯示順序）；`TrendingPlaylistDataSource` 移除 hardcode 的台灣 playlistId，`fetch(region)` 統一走 `fetchPlaylist(region.playlistId)`；`SearchViewModel` 熱門狀態由 `trendingItems/trendingLoading/trendingError` 三欄位移至 `trendingByRegion: Map<ChartRegion, TrendingState>`（各區域獨立載入，單一區域失敗不影響其他區域；各區域自己的 `loading` 旗標即重入 guard，`TrendingRetry` 重抓非載入中區域）；`SearchScreen` 空狀態改為四區域並排（台灣／西洋／日本／韓國，依 `DISPLAY_ORDER`），`fullChartRegion: ChartRegion?` 控制單區展開完整榜單（rail 並排以垂直 LazyColumn 承載，無巢狀 LazyColumn）；測試補充「DISPLAY_ORDER 各區域皆被抓取」與「區域獨立性」案例
- 新增 GitHub Actions CI（`.github/workflows/ci.yml`）：`CI / build-and-test` job 於 ubuntu-latest 跑 `./gradlew test assembleDebug`（JDK 21＋temurin、Gradle cache、失敗上傳 test reports），作為 Branch Protection 的 required status check（TEAM.md §3）
- `feature:player` 新增 `PlaybackIntent.PlayList(entries, startIndex)`（T4）：暫時性佇列播放意圖（熱門榜單等非 Room 清單），由 activity scope 的 `PlayerViewModel.onPlaybackIntent` 轉 call `PlayerController.playQueue`
- 通知本體點擊回前景：`MusicService` 以 `MediaSession.setSessionActivity` 設定 contentIntent（`PendingIntent` 指向 `MainActivity`，`SINGLE_TOP`＋`IMMUTABLE`），點通知本體（非按鈕）即把 App 帶回前景
- 通知列隨機/循環按鈕改用 Media3 官方 `CommandButton.ICON_*`（`ICON_SHUFFLE_ON/OFF`、`ICON_REPEAT_ALL/ONE`），移除 deprecated `setIconResId` 與自訂 `ic_shuffle_on` drawable；官方 `ICON_SHUFFLE_OFF` 以 disabled 色呈現，隨機開/關一眼可辨
- 搜尋結果「載入更多」：`feature:search` 新增 `SearchIntent.LoadMore`、`LoadMoreFooter`（僅在 `nextPageToken != null` 顯示，載入中 disabled＋小進度）；ViewModel 以 continuation token 併頁 append（去重）、空頁視為「已無更多」、token 過期失敗保留原結果可重試；新增 `SearchViewModelTest`（9 案例，純 JVM）
- 搜尋分頁資料鏈：新增 `VideoSearchPage(results, nextPageToken)` 領域模型；`VideoRepository.search` / `SearchVideosUseCase` 支援 `continuationToken` 透傳；`YoutubeDataSource` 解析 `continuationItemRenderer` token（internal 純函數可測）並移除每頁 30 筆硬上限
- 搜尋列表點擊影片改為直接播放（不進入播放頁）：新增 `PlaybackIntent.Play`，由 app 層將搜尋 callback 橋接至播放控制
- 前景迷你播放列（MiniPlayerBar）：App 開啟時常駐底部，含隨機播放開關、上一首／下一首、單曲／清單循環切換、曲目名稱與可拖曳進度條
- 背景通知播放控制：Media3 MediaSession 通知含歌名、進度條（seek）、播放/暫停/前後曲，並新增隨機與循環按鈕
- 播放佇列：點播的歌在播放清單中時以整份清單為佇列從該曲起播；支援鎖屏控制與藍牙耳機按鍵
- 新增 `Playlist` domain model 與對應 UseCase（CRUD + 觀察 + 隨機取曲），支援多播放清單
- `core:data` Room Entity/DAO/RepositoryImpl 完整實作：`PlaylistEntity`、`PlaylistItemEntity`、`PlaylistDao`、`PlaylistRepositoryImpl`，含索引與級聯刪除
- 播放清單列表頁、詳情頁與隨機播放功能：`PlaylistListScreen`、`PlaylistDetailScreen`，含建立／重新命名／刪除清單、隨機播放一首
- 搜尋頁加入播放清單改為選擇清單 BottomSheet：點擊 + 按鈕彈出播放清單選擇器，支援建立新清單後直接加入
- 播放頁加入播放清單改為選擇清單 BottomSheet：與搜尋頁共用 `PlaylistPickerSheet` 元件
- `PlayerController` 介面與 `PlaybackSnapshot` 資料類別移至 `core:domain`，作為跨 feature 共用播放控制合約
- 串流解析錯誤分類精細化與 403 辨識：`StreamErrorClassifier` 新增 raw HTTP 403 辨識（`\b403\b` 字元邊界），403 歸類 `TRANSIENT`（可重試／換網路，不屬永久結構性失敗）；`describe(attempts)` 依分類聚合出**不同使用者可讀提示**——bot 封鎖（「疑似遭 YouTube bot 封鎖，可稍後重試或更換網路」）、403 連結失效（「播放連結已失效，已嘗試重新取得」）、暫時性網路不穩（「網路不穩，可稍後重試」）、一般錯誤。403 歸類決策全程純 Kotlin、可單元測試
- 強制重新解析能力：`AudioStreamRepository.resolveAudioUrl(videoId, force=false)` 新增 `force` 參數（跳過 TTL 快取強制重解析，供 content URL 過期後重試同曲）；`FallbackStreamResolver.resolve(videoId, force=false)` 支援 force 繞過快取（成功覆寫快取；全失敗清除舊快取避免死 URL 殘留）；`AudioStreamRepositoryImpl` 同步透傳 force
- **content URL 403 自動恢復**：`MusicService` 掛 `onPlayerError`（Media3 `Player.Listener`）攔截 `InvalidResponseCodeException`（responseCode==403）。策略分層：① 失效該 videoId memoize＋`resolveAudioUrl(force=true)` 重解析同曲，成功回到原 position 重試同一首；② 仍失敗 → 依 `repeatMode` 切歌（`REPEAT_MODE_ONE` 重播；`ALL/OFF` 走 `seekToNextMediaItem()`）。非 403 不攔截、維持既有 snapshot/error 顯示。併發防護 `pending403Handling`＋`retryCounts`（上限 `MAX_403_RETRY_PER_VIDEO=3`）防無限重試

### Changed
- **歌單詳情頁點擊歌曲改為直接播放**（不再導航進播放頁）：新增 `PlaylistDetailIntent.Play(item)`——以 `_state.items`（顯示順序）建 `PlayQueueItem` 暫時性佇列，經 `PlayerController.playQueue(items, startIndex)` 從點擊曲起播（`startIndex` = 該曲 index，找不到時 0 兜底），播放後停留在歌單頁由底部 MiniPlayerBar 反映狀態；清單為空時 Snackbar 提示「清單為空，無法播放」且不觸發播放。`MainActivity` 撤除 `Routes.PLAYER` / `Routes.player()` 與 NavHost `composable(Routes.PLAYER)`（唯一入口已移除；`PlayerScreen.kt` 已整份刪除，見下方 Removed）
- **App 主題色系全面重設計**：深色模式為主採用中性深灰/黑背景 (#121212) + 紅/橙系強調色 (#FF3B30)，參考 YouTube Music / Spotify 風格；淺色模式對應乾淨白/淺灰背景；保留 Android 12+ 動態配色支援
- 色彩命名語意化：移除 `Purple80` 等實作命名，改用 Material 3 標準角色（`Primary`、`OnPrimary`、`Surface`、`SurfaceVariant`、`SurfaceContainer` 等 20+ 語意色），完整支援 M3 色彩系統
- Typography 完整覆寫：`displayLarge/Small`、`headlineLarge/Medium/Small`、`titleLarge/Medium/Small`、`bodyLarge/Medium/Small`、`labelLarge/Medium/Small` 皆依 Material 3 規範設定字重/大小/行高，字體採系統預設 `FontFamily.Default`
- 通知圖示統一白色：`ic_music_notification`、`ic_shuffle_active` 等 9 個 drawable 全改為 `#FFFFFFFF`，確保深色通知背景下可見度
- MusicService 由手刻 Foreground Service 重構為 Media3 `MediaSessionService`；前景 UI 與背景通知共用同一狀態源
- Media3 升級 1.5.1 → 1.11.0（exoplayer / session 統一）

### Fixed
- **修復 raw HTTP 403 被誤歸為 `PERMANENT`**：`StreamErrorClassifier` 原無 403 關鍵字，raw HTTP 403 落到 else 分支被誤判為永久失敗（不提示「疑似 bot 封鎖」、不被當成可重試）。新增 403 辨識歸 `TRANSIENT`，使 403 類失敗可走自動重解析／切歌恢復路徑
- 修復 `SearchViewModelTest.FakeVideoRepository` 編譯破洞（T4）：T1 `VideoRepository` 契約新增 abstract `fetchTrendingSongs(region)` 後 search 測試 Fake 未同步補 stub，`:feature:search:testDebugUnitTest` 無法編譯；補 override 回 `Result.success(emptyList())`（並支援注入 `trendingResult` 供熱門榜單新測試使用）
- **修復通知列重複「上一首／下一首」icon（方案 A）**：根因是 Media3 `DefaultMediaNotificationProvider.getMediaButtons`（DefaultMediaNotificationProvider.java line 457-514）會在 custom layout 之外，對未提供 SLOT_BACK/SLOT_FORWARD 按鈕的情況走 else-if 分支補上系統 `SEEK_TO_PREVIOUS` / `SEEK_TO_NEXT`，與自訂 prev/next 重複（實機 dumpsys 驗證 7 個 action）。新增 `feature:player.service.CustomMediaNotificationProvider` 覆寫 `getMediaButtons` 回傳固定序列 [上一首, 播放/暫停, 下一首, 隨機, 循環]，完全不呼叫系統 prev/next 分支——compact view 依 slots 判定固定為 [上一首(SLOT_BACK), 播放/暫停(SLOT_CENTRAL), 下一首(SLOT_FORWARD)]，展開通知再加 [隨機, 循環]。按鈕序列、custom command 落點（`onCustomCommand`）、ChannelId/ChannelName/smallIcon 設定皆不變
  - **A2 補強（media button preferences）**：方案 A 只解決「notification 自訂 actions」，但展開通知仍重複——左半是 **SystemUI 的 MediaStyle media surface**（依 `PlaybackState.actions` 渲染 prev/seekbar/next），右半是我們的 notification 自訂 actions。根因：Media3 `MediaSessionLegacyStub`（MediaSessionLegacyStub.java line 1923-1930）本應在「media button preferences 非空」且 custom layout 含 `SLOT_BACK/FORWARD` 按鈕時，從 `PlaybackState.actions` 移除 `ACTION_SKIP_TO_PREVIOUS/NEXT`，但我們先前只用 `setCustomLayout` 未設 media button preferences → legacy stub 偵測不到 → SystemUI 照畫。A2：在 `MusicService.sessionCallback.onPostConnect` 於既有 `setCustomLayout` 旁新增**廣播級** `mediaSession.setMediaButtonPreferences(buildCustomLayout(session))`（無 controller 參數，`@UnstableApi`；與 `setCustomLayout` 共用同一份含 SLOT_BACK/FORWARD 的按鈕清單），使 legacy stub 正確偵測並移除系統 prev/next。效果需 A 實機 `dumpsys media_session` 驗證 `actions` 不再含 `ACTION_SKIP_TO_PREVIOUS/NEXT`（若生效）
- **通知列「上一首／下一首」改為永遠常駐**：改用 MediaSession 自訂 session command 的 `CommandButton`（`SLOT_BACK` / `SLOT_FORWARD`），取代系統依 `hasPreviousMediaItem()` / `hasNextMediaItem()` 過濾的 prev/next 按鈕——清單邊界或單曲時不再少一顆，compact 排版固定為 [上一首, 播放/暫停, 下一首]；無上/下一首時按鈕落點為重播目前曲目開頭
- **修復搜尋「載入更多」輪迴**：根因為 GET `results?continuation=` 會回傳**整頁重新排序**（與前頁重疊 55~100%）。改為續頁走 innerTube `POST youtubei/v1/search`（MWEB context，append-only chunk，重疊 0%；續頁 renderer 為 `videoWithContextRenderer`，欄位對應與首頁不同故新增獨立解析路徑）。ViewModel 補跨頁去重（防 `LazyColumn` duplicate-key 崩潰）與「token 未推進視為到底」guard。新增 `SearchPaging` log（每頁 SUMMARY＋DETAIL 全量 videoId:title＋token 未推進 WARN），供實機驗證續頁正確性
- **降級 Compose BOM 至 `2025.01.00` (Compose 1.7.6)**，解決 Android Studio 253.32098.37 Preview `ClassNotFoundException: ComposeViewAdapter` 問題：新版 BOM (2026.02.01 → Compose 1.10.4) 超出 AS 設計工具插件支援範圍，降級後 Preview 可正常載入
- 修復所有 Compose Preview 渲染問題：
  - `PlayerScreen`：Preview 中以 `LocalInspectionMode.current` 判斷設計時期，以黑色 Box 替代 WebView 避免渲染異常
  - `PlaylistPickerSheet`：兩組 Preview（含項目／空清單）皆能正常顯示
  - 全模組 Preview 統一補齊參數：深/淺色模式（`uiMode`）、繁體中文（`locale=zh_TW`）、字體縮放 1.0、Pixel 7 Pro 裝置、分組名稱（`feature-player`/`feature-search`/`feature-playlist`）、具名 Preview
  - `PlaylistListScreen`：日期格式化移除 `Locale.getDefault()` 依賴，改用 `LocalConfiguration.current.locales` 固定 Preview 語系
- **feature:player / feature:playlist / feature:search / core:ui**：新增 `debugImplementation(libs.androidx.compose.ui.tooling)`（core:ui 用 `debugApi` 向下傳遞），修復 Preview 無法渲染的 `ClassNotFoundException: ComposeViewAdapter` 核心問題 —— `ui-tooling-preview` 僅含註解 API，實際渲染需 `ui-tooling` runtime
- `PlaylistDetailScreen`：AsyncImage 補上 `placeholder` / `error` 使用 `MaterialTheme.colorScheme.surfaceVariant`，空縮圖顯示主題色塊；佔位 Box 同步改用 `surfaceVariant` 取代硬編碼 `Color.LightGray`，統一 Preview 與運行時視覺
- 修復 `feature:player` 既有編譯破洞：`PlaylistItem` 新增必填 `playlistId` 參數後，`PlaybackQueueBuilderTest` 建構呼叫未同步（PR #9），`./gradlew test` 恢復全綠（186 tasks）

### Removed
- 熱門音樂榜單移除名次顯示（`feature:search` `SearchScreen`）：`ChartRailItem` 縮圖左上角名次徽章與 `ChartDetailRow` 行首名次欄一併刪除，同時移除 `rank` 參數（`ChartRail`／`ChartFullList` 仍保留 `itemsIndexed` 的 `index` 供 `onPlayChartQueue` 起播 index 使用），rail 與完整榜單文案同步改為「縮圖＋歌名＋歌手」
- 移除全螢幕播放頁 `feature:player/PlayerScreen.kt`（含 `PlayerScreen` / `PlayerRoute` 與兩個 @Preview）：全專案已無外部引用，點擊歌單/搜尋結果改為直接播放並由底部 MiniPlayerBar 反映狀態。`PlayerViewModel` 一併瘦身——刪除僅播放頁使用的 `PlayerUiState`、`state`、`playlists`/`observePlaylists`、`startBackgroundPlayback`/`stopBackgroundPlayback`、`messages`、`onAddToPlaylist`/`createPlaylistAndAdd` 與 `SavedStateHandle`，保留 `PlaybackIntent`（Play/PlayList）、`playback`、`onPlaybackIntent` 供 MiniPlayerBar 與搜尋/歌單直接起播
- 移除 legacy `androidx.media` 依賴（通知改由 Media3 session 提供）

### Docs
- 新增 TEAM.md §4「開發原則」三條規範：① 改動範圍以需求為限（嚴禁順手重構/格式調整）② 棄用 API 必優先尋找替代方案（確無替代才用 @Suppress 並附遷移計畫）③ 嚴禁在保護分支直接開發（所有改動必須在 feature/fix 分支）
- 新增 TEAM.md §1「QA 測試報告截圖規範」：模擬器/實機測試涉及畫面變動時，可取得截圖情況下必須附上截圖（失敗/異常優先，通過輔助）
- 新增 TEAM.md §8「AI 協作運作模式（Loop Engineering）」：Tech Lead 純協調不開發、序列派工、審查迴圈（DoD／四段式回報／3 圈上限）、治理例外
- 新增常設角色 **D - QA Engineer**：獨立於開發者的驗證（單元測試執行、模擬器煙霧測試、logcat 監控、發版回歸）；取消輪值兼職制；新增 `qa-engineer` agent 定義與初始煙霧測試清單（`docs/qa/smoke-checklist.md`）
- `app/` 容器層所有權劃歸 Tech Lead（原三人都未擁有的灰色地帶）
- 更新三個 agent 定義：tech-lead 改純協調者；data-engineer / ui-engineer 加 DoD 與回報格式
- PR template 新增審查節：Approver ≠ 作者、DoD 檢查、3 圈升級路徑
- 新增「文件同步要求」（`docs/TEAM.md` §4）：程式碼異動必須在同一 PR 內同步維護對應文件；所有 merge 進 `master` 的 PR 一律在本檔 `[Unreleased]` 加一筆
- 新增 `docs/CHANGELOG.md`（Keep a Changelog 格式），並補錄 v1.0.0 歷史決策
- 新增 `.github/pull_request_template.md`：含文件同步 checklist，未勾選者 Approver 不得 Approve
- 團隊規範的保護分支名稱由 `main` 改為 `master`，對齊實際 repo
- 入口管制物理強化：B/C/D agent 改 `mode: subagent`（僅可被 Task tool 派工），Tech Lead 改 `mode: primary` 並設為專案 `default_agent`——使用者唯一入口 = Tech Lead；QA 編輯禁區收緊至整個產品程式碼目錄（含 src/test）
- 新增三份角色專屬技能包（`.opencode/skills/`）：B `newpipe-stream-resolver`（解析管線與失效診斷 SOP）、C `compose-ui-conventions`（頁面三件套與注入白名單）、D `qa-smoke-runbook`（adb/logcat 實操序列與報告格式）
- `docs/TEAM.md` §8 新增「入口管制（物理強制）」條目，記錄上述機制與技能包維護權責
- 新增搜尋續頁 QA 驗證報告（`docs/qa/reports/2026-08-29-804da8f-search-pagination.md`）：4 頁續頁 token 鏈、`APPEND dup=0`、無 `WARN token not advanced`、無 crash 全 PASS；「載入更多」採顯式按鈕經 A 判讀為**設計使然**（非 infinite-scroll）並結案；常規驗證項納入煙霧清單 #13
- TEAM.md §1/§8 修正 merge 權責：PR 的 **merge 一律由 Owner 在 GitHub 執行**（A 只負責開 PR 與審查，不代按 merge，除非 Owner 明確指示）

## [1.0.0] - 2026-08

### Added
- 多模組架構：`:app`、`:feature:search`、`:feature:playlist`、`:feature:player`、`:core:ui`、`:core:domain`、`:core:data`、`:core:common`
- 搜尋頁：YouTube 行動版搜尋 HTML 解析（`ytInitialData` JSON）→ 影片清單
- 播放清單頁：Room 持久化，增刪與清空
- 播放頁 + `MusicService`：Foreground Service 音訊播放（NewPipe StreamResolver → ExoPlayer）、通知列控制
- 測試：`core:domain` UseCase 純 JVM 單元測試、`core:data` Repository 對 Fake Dao 測試

### Changed
- 由 single-module `:app` 重構為 multi-module 架構，拆分順序 domain → data → ui → features → app，每步 CI 綠燈
- PlaylistItem 拆成 Domain Model + Room Entity：讓 `core:domain` 保持零 Android 依賴，Room 細節封死在 `core:data`
- 移除 VideoResult 的 @Serializable：全專案無序列化使用點，domain 不需要 serialization plugin

[Unreleased]: https://github.com/lgroupdavid.hs/YoutubeMusicPlayer/compare/v1.0.0...HEAD

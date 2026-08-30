# Changelog（異動紀錄）

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)；
版本語意參考 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

> **維護規則**：所有 merge 進 `master` 的 PR，必須在 `[Unreleased]` 區塊加一筆，
> 類別對應 Conventional Commits（`feat` → Added、`fix` → Fixed、`refactor`/`perf` → Changed、移除 → Removed、文件 → Docs）。
> 發版時由 Tech Lead 把 `[Unreleased]` 改為版本號 + 日期。

## [Unreleased]

### Added
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

### Changed
- **App 主題色系全面重設計**：深色模式為主採用中性深灰/黑背景 (#121212) + 紅/橙系強調色 (#FF3B30)，參考 YouTube Music / Spotify 風格；淺色模式對應乾淨白/淺灰背景；保留 Android 12+ 動態配色支援
- 色彩命名語意化：移除 `Purple80` 等實作命名，改用 Material 3 標準角色（`Primary`、`OnPrimary`、`Surface`、`SurfaceVariant`、`SurfaceContainer` 等 20+ 語意色），完整支援 M3 色彩系統
- Typography 完整覆寫：`displayLarge/Small`、`headlineLarge/Medium/Small`、`titleLarge/Medium/Small`、`bodyLarge/Medium/Small`、`labelLarge/Medium/Small` 皆依 Material 3 規範設定字重/大小/行高，字體採系統預設 `FontFamily.Default`
- 通知圖示統一白色：`ic_music_notification`、`ic_shuffle_active` 等 9 個 drawable 全改為 `#FFFFFFFF`，確保深色通知背景下可見度
- MusicService 由手刻 Foreground Service 重構為 Media3 `MediaSessionService`；前景 UI 與背景通知共用同一狀態源
- Media3 升級 1.5.1 → 1.11.0（exoplayer / session 統一）

### Fixed
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
- 移除 legacy `androidx.media` 依賴（通知改由 Media3 session 提供）

### Docs
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

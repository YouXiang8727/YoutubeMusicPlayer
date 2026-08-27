# Changelog（異動紀錄）

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)；
版本語意參考 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

> **維護規則**：所有 merge 進 `master` 的 PR，必須在 `[Unreleased]` 區塊加一筆，
> 類別對應 Conventional Commits（`feat` → Added、`fix` → Fixed、`refactor`/`perf` → Changed、移除 → Removed、文件 → Docs）。
> 發版時由 Tech Lead 把 `[Unreleased]` 改為版本號 + 日期。

## [Unreleased]

### Added
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
- MusicService 由手刻 Foreground Service 重構為 Media3 `MediaSessionService`；前景 UI 與背景通知共用同一狀態源
- Media3 升級 1.5.1 → 1.11.0（exoplayer / session 統一）

### Fixed
- MiniPlayerBar 隨機播放圖示切換：啟用時改用 `ic_shuffle_active` 圖示（與 MusicService 通知列一致），而非僅改變 tint 顏色
- 播放新歌時隨機／循環模式被重置：`MusicService.handlePlay()` 於 `setMediaItems()` 後重新套用當前 `shuffleModeEnabled` 與 `repeatMode`，確保「下一首」按鈕依循正確播放模式
- 循環模式預設值修正：ExoPlayer 預設 `repeatMode` 改為 `REPEAT_MODE_ALL`（清單循環），與 UI 圖示一致；原預設 `REPEAT_MODE_OFF` 被映射為 `ALL` 導致 UI 顯示循環但實際播放完畢停止
- MiniPlayerBar 隨機播放與循環模式切換時顯示 Toast 回饋，並以單一 Toast reference + `cancel()` 避免頻繁切換時 Toast 堆疊
- 全域 OkHttpClient 掛載的瀏覽器 header 攔截器對所有請求無差別覆蓋 UA／Referer／Cookie，破壞 InnerTube IOS/ANDROID_VR 直連的 client 身份與 NewPipe extractor 自帶 UA，導致串流解析三層 fallback 全數被 YouTube 回 LOGIN_REQUIRED（IP 清白）：`NetworkModule` 拆為 browser／stream 雙 profile（Hilt qualifier `@BrowserProfile`／`@StreamProfile`），攔截器只保留在搜尋頁 HTML 抓取路徑，串流鏈改用無攔截器的乾淨 client
- 補上 `MusicService` 的 `androidx.media3.session.MediaSessionService` intent-filter（media3 1.6+ 要求，否則 SessionToken 解析失敗導致 App 啟動即 crash）；Controller 連線初始化改為降級處理不炸 composition
- 串流解析遭 YouTube 匿名 bot 封鎖（LOGIN_REQUIRED「Sign in to confirm you're not a bot」）時播放失敗：改多層 fallback——NewPipe 主路徑失敗後依序嘗試 InnerTube 直連（IOS → ANDROID_VR client，免 poToken）與 Piped 公開實例，成功結果以 TTL 快取；全鏈失敗時於媒體通知聚合各來源錯誤與分類提示
- 播放解析失敗時錯誤訊息現在會反映至 App 內狀態（`PlaybackSnapshot.errorMessage`，供 UI 顯示）：`onPlayerError` 觸發快照重新取樣，映射規則抽成純 Kotlin 的 `PlaybackErrorDescriber`（cause chain 最深層的聚合中文訊息優先，否則以 errorCodeName 人類可讀化兜底）

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

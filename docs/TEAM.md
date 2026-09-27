# MyMediaPlayer 開發團隊規範（3 人小隊）

> 原則：**物理模組邊界 = 職責邊界**。越界寫不出來（Gradle 依賴圖直接編譯失敗），不需要靠自覺。

---

## 1. 角色與權責

| 代號 | 角色 | 擁有目錄 | 禁區 | 兼職 |
|------|------|----------|------|------|
| **A - Tech Lead** | 架構守門人 | `settings.gradle.kts`、`gradle/libs.versions.toml`、根 `build.gradle.kts`、`core/common`、`core/domain`、`.github/`、`docs/`、`app/`（容器層） | B/C/D 的擁有目錄（治理例外見 §8） | 開 PR 與審查；**merge 一律由 Owner 在 GitHub 執行**（A 不代按，除非 Owner 明確指示）；版本升級統一 PR |
| **B - Data/Media Engineer** | 資料與播放 | `core/data/`、`feature/player/src/main/java/**/service/`、`feature/player/src/main/java/**/playback/`（播放控制鏈：PlayerController、PlaybackSnapshot 等，2026-08 裁定） | `feature/*/ui`、`core/ui` | NewPipe / StreamResolver 穩定度監控 |
| **C - UI Engineer** | 前端介面 | `core/ui/`、`feature/search/`、`feature/discover/`、`feature/playlist/`、`feature/player/` 的 Screen 與 ViewModel | `data/remote`、`data/local`（只能透過 `core:domain` 的 UseCase） | — |
| **D - QA Engineer** | 獨立驗證 | `docs/qa/`（測試計畫、煙霧清單、報告） | **所有產品程式碼目錄**（只驗證，不開發） | 發版前回歸測試總召 |

- **QA 改為常設角色 D**：獨立於開發者執行驗證——單元測試執行、實機煙霧測試、logcat crash 監控、發版回歸。開發者自測不取代 D 的獨立驗證。
- **QA 測試報告截圖規範**：進行模擬器/實機測試時，若涉及畫面相關變動，**在可取得截圖的情況下必須在測試報告附上截圖**（失敗/異常畫面優先，通過畫面輔助）。
- **Owner（你）**：只看 `master` 的 CI 綠燈與 PR 列表，不參與程式碼審查細節。
- **RACI**：做的人 = R；其餘人中由 Tech Lead 指定一人 = A（PR Approver）；其餘 = I。跨層改動（例：改 domain interface）一律 A = Tech Lead。

## 2. 分支模型：GitHub Flow

```
master（保護分支，唯一長期分支，永遠可發版）
  ↑ PR：1 Approve + CI 綠燈 → squash merge
feature/<module>-<描述>     例：feature/search-history、fix/data-stream-resolver
```

規則：
1. 從最新 `master` 開分支，生命週期 ≤ 3 天，避免長期分歧。
2. 分支命名 `<type>/<scope>-<desc>`，`scope` = 你擁有的 module 名。
3. Commit Message：Conventional Commits
   - `feat(search): 新增搜尋歷史`
   - `fix(data): 修復 ytInitialData 解析失敗`
   - `refactor(domain): 抽出 ClearPlaylistUseCase`
4. PR 描述照 `.github/pull_request_template.md` 填寫。
5. `master` 直接 push 一律禁止（Branch Protection 設定 Required）。

## 3. Branch Protection 設定清單（Repo Settings → Branches）

- [ ] Require a pull request before merging
- [ ] Required approvals: **1**
- [ ] Require status checks: **CI / build-and-test**
- [ ] Require branches to be up to date before merging
- [ ] Do not allow bypassing the above settings

## 4. 開發守則

### 依賴方向（由 Gradle 物理強制）
```
app ──▶ feature:* ──▶ core:ui ──▶ (無)
              │
              └────▶ core:domain ◀── core:data ──▶ core:common
```
- `feature` **不可**依賴 `core:data`（build.gradle.kts 根本沒有這條線）。
- `core:domain` 是純 Kotlin module：**禁止**出現任何 `android.*` import。
- ViewModel 只准注入 UseCase / Repository interface，不准拿 Dao、Api、ExoPlayer。
- 新增第三方庫：只在 `gradle/libs.versions.toml` 加版本，由 A 審核 PR。

### 測試要求
| Layer | 要求 |
|-------|------|
| `core:domain` | UseCase 必須有單元測試（純 JVM，跑得快） |
| `core:data` | Repository 對 Fake Dao 至少一組測試；NewPipe 解析需實機煙霧測試 |
| `feature` | UiState 邏輯放 ViewModel 並可測；Compose Preview 作為視覺回歸最低門檻 |

### 資源所有權
- App 名稱等共用字串以 **app module 為準**（library 內同名資源會被 app 覆蓋）。
- feature 內新增資源必須加前綴避免衝突（如 `ic_music_notification.xml`）。

### 文件同步要求（Code Changes ⇒ Docs Changes）
程式碼異動時**必須在同一個 PR 內**同步維護對應文件；文件異動隨功能 PR 同送，不另開文件 PR（避免漂移）。Approver 審查時一併檢查：

### 開發原則
1. **改動範圍以需求為限**：程式碼變更應嚴格限定在滿足當前需求的範圍內，**不得**順手修改與需求無關的程式碼（含重構、格式調整、未使用的 import 清理等），除非另開專門 PR 並說明理由。
2. **棄用 API 處理**：遇到 `@Deprecated` 標注時，**優先尋找替代方案**；確認無替代方法（或遷移成本極高且經 A 同意）時，始可使用並以 `@Suppress("DEPRECATION")` 標注於最小範圍，並附註遷移計畫。
3. **嚴禁在保護分支直接開發**：所有改動**必須**在 feature/fix/refs 分支進行，`master`/`main` 僅接受 PR squash merge（Branch Protection 已強制），違者視為流程違規。

| 異動類型 | 必須同步更新的文件 |
|----------|--------------------|
| 新增/刪除 module、依賴方向調整 | `docs/ARCHITECTURE.md` §1 模組總覽、§2 依賴規則 |
| Domain model / Repository interface / UseCase 變更 | `docs/ARCHITECTURE.md` §3 各層職責與關鍵類別 |
| 第三方庫新增/升級（`gradle/libs.versions.toml`） | `docs/ARCHITECTURE.md` 開頭 Stack 區、`docs/CHANGELOG.md` |
| 新風險出現或對策變化 | `docs/ARCHITECTURE.md` §7 風險登記簿 |
| 重大架構決策／取捨 | `docs/TEAM.md` §7 已知取捨 |
| **所有 merge 進 `master` 的 PR（一律，無例外）** | `docs/CHANGELOG.md` 的 `[Unreleased]` 加一筆 |

- `docs/CHANGELOG.md` 採 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式；條目類別對應 Conventional Commits（`feat` → Added、`fix` → Fixed、`refactor`/`perf` → Changed、移除 → Removed）。
- 發版時由 Tech Lead 將 `[Unreleased]` 改為版本號 + 日期。
- PR template 的文件同步 checklist 為最低強制點；未勾選者 Approver 不得 Approve。

## 5. 例行節奏（2 週 Sprint）

| 事件 | 時間 | 產出 |
|------|------|------|
| Planning | Sprint Day 1 | 本 Sprint 的 PR 清單與負責人 |
| Daily（文字同步即可） | 每天 | 昨日/今日/阻礙 |
| Review & Demo | Sprint 最後一天 | 合併的 feature 展示 |
| Retro | 同日 | 流程調整、module 邊界是否需要重劃 |

## 6. Roadmap（技術債與後續工程）

- [ ] 導入 `detekt` + 自訂規則（import 邊界再保險一層）
- [ ] 導入 `dependency-guard` 鎖定模組 API 面
- [ ] `build-logic/convention`：AGP 9 built-in Kotlin 工具鏈穩定後，把各 library 重複的 build 設定抽成 convention plugin
- [x] MusicService 升級 Media3 `MediaSessionService`（鎖屏控制、藍牙耳機按鍵）— 已於播放控制功能 PR 完成
- [ ] NewPipe Extractor 版本鎖定策略與失效 fallback（YouTube 改版風險）
- [ ] 搜尋建議改為**音樂導向**來源：現行 MVP 為 Google suggestqueries（偏搜尋熱詞）。追蹤方案——YT Music 建議端點／InnerTube suggestion／結合本地歷史＋收藏權重；抽換只需改 core:data 的 `SearchSuggestionRepository` 實作（UI/VM 只依賴介面，見 §7 決策）

## 7. 已知取捨（決策記錄）

| 決策 | 理由 |
|------|------|
| 不導入 build-logic（v1） | AGP 9 內建 Kotlin 尚在演進，先求多模組可用；重複設定可控（每檔 ~15 行） |
| PlaylistItem 拆成 Domain Model + Room Entity | 讓 `core:domain` 保持零 Android 依賴，Room 細節封死在 `core:data` |
| 移除 VideoResult 的 @Serializable | 全專案沒有序列化使用點，移除後 domain 不需要 serialization plugin |
| detekt / dependency-guard 延後 | 模組邊界已由 Gradle 依賴圖物理強制，工具再加是第二道鎖 |
| 播放控制在 feature 內（PlayerController），不進 core:domain | 播放是裝置能力而非業務領域；MiniPlayerBar 由 app 層掛載，feature 間不需互相依賴 |
| 串流 URL 逐首解析（ResolvingDataSource）而非預解析全佇列 | NewPipe 解析有時效性且成本高；loader thread 同步解析＋快取已足夠 |
| 串流解析採多層 fallback（NewPipe → InnerTube IOS/ANDROID_VR 直連 → Piped 實例），不自建 poToken/BotGuard WebView | 2026 年中 YouTube 對 WEB 系 client 全面要求 po_token，匿名 bot 封鎖升級 extractor 解不了（v0.26.5 已是最新仍無解）；IOS/ANDROID_VR client 免 token 是當前可行替代但屬易腐路徑；BotGuard token 綁 session/content 且需 JS 執行環境，自建成本遠超收益；Piped 公開實例不穩定故只墊底。InnerTube client 版本失效時更新常數即可（InnerTubeStreamSource companion） |
| 通知列隨機／循環按鈕**依播放模式切換**，採用 Media3 官方 `CommandButton.ICON_*` | DefaultMediaNotificationProvider 原生 custom layout 不支援 per-state icon；改用官方 ICON 序列並配合 listener 的 `refreshNotificationCustomLayout()` 即時更新視覺（`@UnstableApi`） |
| 搜尋續頁走 innerTube POST（`youtubei/v1/search`，MWEB client）而非 GET `?continuation=` | 2026-08 多頁實測：GET 續頁回傳**整頁重新排序**（重疊 55~100%）→ 載入更多變輪迴；POST 回傳 append-only chunk（重疊 0%）。雖 MWEB client 屬易腐路徑，但 chunk 解析拆成純函數、失效改寫成本可控 |
| 搜尋建議（autocomplete）首版用 Google suggestqueries 端點（`suggestqueries.google.com/complete/search?client=youtube&ds=yt`），非官方 API | 已先抽換成 `core:domain` 的 `SearchSuggestionRepository` interface（UI/ViewModel 只依賴介面），建議來源可隨時抽換；suggestqueries 低延遲、免 API key、極穩定，作為 MVP 快速落地。其建議可能偏「搜尋熱詞」而非「音樂導向」；音樂導向（如 YT Music 建議、InnerTube suggestion、或結合本地歷史/收藏權重）列為追蹤待辦（見 §6 Roadmap），抽換時只需改 core:data 的 `@Binds` 實作 |
| 熱門榜單（Trending）獨立成「探索」Tab，不留在搜尋頁 | 搜尋、搜尋紀錄屬「導向型」任務，Trending 屬「探索型」，混在同一空狀態頁互相搶空間；拆頁後搜尋頁職責單一（搜尋＋紀錄），探索頁（`feature:discover`）有專屬空間可擴充推薦/新歌等。代價：底部導覽 2 tab → 3 tab；探索 tab 圖示暫用 core icons 的 Star（core 集無 Explore，不為單一圖示引入 extended icons 依賴） |
| 播放佇列**允許重複**（同一首歌可排多筆），`videoId` 不作為佇列元素唯一識別碼 | 佇列是 append-only 的**有序**清單，「讓某首歌連播兩次」是合法且常見需求，資料層去重會直接推翻此語意（`SearchViewModel` 的跨頁去重是因為搜尋結果本就不該重複，語意不同不可類推）。**通則一：任何允許重複元素的 lazy list，key 必須納入 `index` 或專屬唯一 id，不得單用 `videoId`**——否則 `LazyColumn` 佈局階段拋 `IllegalArgumentException: Key ... was already used` 直接崩潰（2026-09-26 實機事故，見 `docs/CHANGELOG.md`）。**通則二：同一清單的「目前指向哪一筆」也必須用專屬唯一識別定位（同樣不得以 `videoId` 反推）**——否則重複元素存在時「目前播放項」會指向錯列。已於 2026-09-26 修正：`PlaybackSnapshot` 新增 `currentMediaItemIndex`（播放端由 `Player.currentMediaItemIndex` 填充、以 `hasCurrent` 門控守住「`hasCurrent == false` 時必為 `NO_CURRENT_INDEX`」不變式），`MiniPlayerBar` 改以該欄位為主、`videoId` 二次確認；**跨流比對不一致時寧可不高亮，也不要高亮錯列**（高亮錯列是誤導使用者的行為，缺少高亮只是外觀小瑕疵）。已知副作用：移除佇列項目時 index 位移與 snapshot 更新可能相差一幀，該幀會短暫沒有高亮（單幀、可接受） |
| 「存為播放清單」時**歌單折疊重複曲目但如實回報**，且以**反向遞增 `addedAt`** 保序 | 兩項皆是資料層既有硬限制下的取捨。**折疊**：`playlist_items` 複合主鍵 `(playlistId, videoId)` 使同一首歌在歌單內只能一列，改成可重複需加 ordinal 欄位 → DB v8 migration 牽動 entity／Dao／Repository／domain model／所有歌單 UI。不改的理由：**兩者語意本就不同**——播放佇列是「播放順序」（重複＝想連播兩次），播放清單是「收藏」（同一首歌收藏兩次沒有意義），不該為了讓轉換對稱而污染歌單模型。**但絕不靜默**：`mergedCount` 必須回報並顯示「其中 N 首重複曲目已合併」——否則使用者存 10 首只得到 7 首卻被告知「已建立」，那是誤導而非簡潔。**保序**：讀取端 `observePlaylistItems` 是 `ORDER BY addedAt DESC`（最新在前），與佇列的從頭播到尾相反；存入時令 `addedAt = base - index`（`base` 取一次）使讀出順序**正好等於佇列順序**，不需改 DB、不影響既有歌單，之後使用者手動加歌的 `addedAt` 為當下時間、自然排最前面，仍符合既有 DESC 語意。代價：`addedAt` 在此路徑下不再是精確加入時間（相差數毫秒，對 `observeRecentItems` 排序無實質影響）。**已知取捨**：`PlayQueueItem` 僅有 `videoId`＋`title`（`MediaItem` 層已丟棄 artwork），故存入時 `thumbnailUrl = ""`、歌單縮圖顯示 `surfaceVariant` 色塊——補縮圖需擴充 `PlayQueueItem` 或改 `MusicService` 的 MediaItem 層，屬另一工作項 |

## 8. AI 協作運作模式（Loop Engineering）

> 本節適用於以 AI agent 執行團隊工作的情境：Tech Lead 為常駐**協調者**，
> B / C 由對應 subagent 承擔（設定見 `.opencode/agent/`）。真人共事時仍以 §1–§7 為準。

### 角色切分

| 角色 | AI 模式下的職責 |
|------|-----------------|
| **A - Tech Lead** | **純協調**：需求確認 → 工作拆解與指派（R/A/I）→ 派工 → 審查 → 開 PR → **等 Owner 在 GitHub 執行 merge** → 收尾。**不撰寫 B/C/D 領域的產品程式碼**；**不代按 merge（除非 Owner 明確指示）** |
| **B / C - subagent** | 開發＋自測＋依 DoD 回報（見下）；不做跨領域越界編輯 |
| **D - QA subagent** | **獨立驗證**：單元測試執行、模擬器煙霧測試、logcat crash 監控、回歸報告；不修改任何產品程式碼，驗證 FAIL 退回 A 走同一套迴圈 |

### 入口管制（物理強制）

- 使用者**不直接接觸團隊成員**：B/C/D 的 agent 設定檔為 `mode: subagent`（只能被 Task tool 調用，無法被使用者直接切換對話）；A 為 `mode: primary` 且為專案 `default_agent`。**唯一入口 = A**——與「物理模組邊界」同一哲學：不靠自覺，靠設定檔擋住。
- 角色專屬技能包置於 `.opencode/skills/`（B：`newpipe-stream-resolver`、C：`compose-ui-conventions`、D：`qa-smoke-runbook`），隨角色職責演進，由 A 於治理審查時一併維護。

### A 的治理例外（不算開發）

A 擁有目錄中的**治理性工作**——`docs/**` 規範與架構文件、`.github/**` CI 與流程、
`gradle/libs.versions.toml` 版本治理、根建置檔——由 A 親手執行，不受「不參與開發」限制。

### 派工與序列依賴

1. A 拆解任務時明確標註：負責角色（R）、涉及目錄、前置依賴、驗收標準。
2. 有順序依賴的工作（例：先 service 後 Screen）：前序角色**回報完成且 A 審查通過**後，才派後續角色。
3. subagent 調用為同步等待；回報未達 DoD 視同未完成，不進入下一棒。

### 審查迴圈（Loop）

- **DoD（完成定義）**：① 編譯綠燈 ② 相關測試通過 ③ 文件同步項目處理完畢——三者齊備才算「完成」。
- **回報格式（四段式）**：① 變更清單（檔案＋摘要）　② 測試證據（執行指令＋結果）　③ 文件同步狀態　④ 風險與待確認事項。
- **A 收到 Task 回報必須「立即」處理，不等使用者指示**：
  1. 依 DoD 審查回報；PASS → 主動繼續派發下一個工作項。
  2. FAIL → 帶具體退回意見，將該工作項 retry_count +1 重新派工。
  3. 同一工作項 retry_count ≥ 3 → 停止迴圈，向 Owner 報告阻礙與已完成部分。
- **retry_count 追蹤機制**：A 派工時在 Task prompt 開頭加標記 `[工作項: <描述>] [重試: N/3]`；N 由 A 於自身 context 維護，收到回報時檢查 N 決定是否可繼續重試。此機制解決「純文字 3 圈上限無法可靠追蹤」的問題。
- **Task 呼叫失敗**：Task call 超時或回傳錯誤視同 FAIL，計入 retry_count 後重試；連續 3 次失敗停止迴圈，向 Owner 報告。
- **任務內建置最小化（2026-09-15 裁定）**：重量級 Gradle 驗證（全模組測試、assembleDebug 等）一律由 A 於主 loop 執行。subagent 子任務內只准跑「最窄自測」（單一測試類），不得執行全量測試/編譯。根因：Windows 上長占住的 gradle 步驟（檔案鎖、daemon contention）會使 subagent session 於 in-flight 指令中被 abort，交不回報（TUI 顯示「Task cancelled」但 session 背景仍繼續，造成「cancelled 卻有產出」與「build failed 後無回報」的誤判）。派工或建置前一律先 `gradlew --stop` 清 daemon。
- **A 主 loop 進度可見性（2026-09-18 裁定）**：A 執行長任務時必須讓 Owner 隨時知道「正在做什麼、還有多久、下一步是什麼」，禁止在無文字輸出的情況下長時間靜默。
  1. A 執行任何重量級 Gradle 指令前，**必須在同一則訊息先輸出**「即將執行 <指令>（預期 N 分鐘）」；指令回傳後**立即輸出結果摘要**（BUILD SUCCESSFUL／FAIL 與關鍵錯誤）。不允許 tool call 成為訊息結尾而後續無文字。
  2. 多步驟工作（拆解→派工→審查→驗證→收尾）每完成一個大階段，輸出 checkpoint 摘要（已完成／進行中／下一步）。
  3. 連續 tool call 之間若有長等待（>60s），先宣告預期耗時。
  4. 根因：2026-09-18 事件——A 於 `gradlew test assembleDebug` 成功（BUILD SUCCESSFUL in 1m 3s）後未立即輸出結果，Owner 誤判卡住而取消工作（實際產出零遺失；事後確認無 process 死鎖，殘留 java 為 Gradle daemon 正常常駐）。
- **Task 回報「cancelled」＝ FAIL 且先查產出**：計入 retry_count 重新派工前，先比對 git working tree──若 discover「cancelled 任務」其實已在背景完成檔案異動（runaway session），先驗證其產出（編譯＋單測由 A 跑）再決定沿用或重做，避免無謂重工。
- **作者 ≠ 審查者**：開發類 PR 一律由 A 審查；A 的治理性變更（本節例外工作）不由 A 自審，由 Owner 或指定工程師複核。

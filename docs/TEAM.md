# MyMediaPlayer 開發團隊規範（3 人小隊）

> 原則：**物理模組邊界 = 職責邊界**。越界寫不出來（Gradle 依賴圖直接編譯失敗），不需要靠自覺。

---

## 1. 角色與權責

| 代號 | 角色 | 擁有目錄 | 禁區 | 兼職 |
|------|------|----------|------|------|
| **A - Tech Lead** | 架構守門人 | `settings.gradle.kts`、`gradle/libs.versions.toml`、根 `build.gradle.kts`、`core/common`、`core/domain`、`.github/`、`docs/`、`app/`（容器層） | B/C/D 的擁有目錄（治理例外見 §8） | 開 PR 與審查；**merge 一律由 Owner 在 GitHub 執行**（A 不代按，除非 Owner 明確指示）；版本升級統一 PR |
| **B - Data/Media Engineer** | 資料與播放 | `core/data/`、`feature/player/src/main/java/**/service/`、`feature/player/src/main/java/**/playback/`（播放控制鏈：PlayerController、PlaybackSnapshot 等，2026-08 裁定） | `feature/*/ui`、`core/ui` | NewPipe / StreamResolver 穩定度監控 |
| **C - UI Engineer** | 前端介面 | `core/ui/`、`feature/search/`、`feature/playlist/`、`feature/player/` 的 Screen 與 ViewModel | `data/remote`、`data/local`（只能透過 `core:domain` 的 UseCase） | — |
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
- ViewModel 只准注入 UseCase / domain interface（Repository interface、`PlayerController` 等），不准拿 Dao、Api、ExoPlayer。
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

## 7. 已知取捨（決策記錄）

| 決策 | 理由 |
|------|------|
| 不導入 build-logic（v1） | AGP 9 內建 Kotlin 尚在演進，先求多模組可用；重複設定可控（每檔 ~15 行） |
| PlaylistItem 拆成 Domain Model + Room Entity | 讓 `core:domain` 保持零 Android 依賴，Room 細節封死在 `core:data` |
| 移除 VideoResult 的 @Serializable | 全專案沒有序列化使用點，移除後 domain 不需要 serialization plugin |
| detekt / dependency-guard 延後 | 模組邊界已由 Gradle 依賴圖物理強制，工具再加是第二道鎖 |
| 播放控制的**介面（PlayerController）進 core:domain、實作（MediaControllerPlayerController）留 feature** | 播放是裝置能力而非業務領域，故播放引擎實作不進 domain（核心維持零 Android 依賴）；但 ViewModel 需要一個跨 feature／可測試的播放控制契約，故把 `PlayerController` interface 與 `PlaybackSnapshot` 放進 `core:domain`（純 Kotlin，只依賴 coroutines Flow/StateFlow），實作以 MediaController 連線 MusicService 的 MediaSession。MiniPlayerBar 由 app 層掛載，feature 間不需互相依賴 |
| 串流 URL 逐首解析（ResolvingDataSource）而非預解析全佇列 | NewPipe 解析有時效性且成本高；loader thread 同步解析＋快取已足夠 |
| 串流解析採多層 fallback（NewPipe → InnerTube IOS/ANDROID_VR 直連 → Piped 實例），不自建 poToken/BotGuard WebView | 2026 年中 YouTube 對 WEB 系 client 全面要求 po_token，匿名 bot 封鎖升級 extractor 解不了（v0.26.5 已是最新仍無解）；IOS/ANDROID_VR client 免 token 是當前可行替代但屬易腐路徑；BotGuard token 綁 session/content 且需 JS 執行環境，自建成本遠超收益；Piped 公開實例不穩定故只墊底。InnerTube client 版本失效時更新常數即可（InnerTubeStreamSource companion） |
| 通知上隨機／循環按鈕圖示不隨狀態切換 | DefaultMediaNotificationProvider 的 custom layout 不支援 per-state icon；精確狀態以前景 App 內為準 |
| 搜尋續頁走 innerTube POST（`youtubei/v1/search`，MWEB client）而非 GET `?continuation=` | 2026-08 多頁實測：GET 續頁回傳**整頁重新排序**（重疊 55~100%）→ 載入更多變輪迴；POST 回傳 append-only chunk（重疊 0%）。雖 MWEB client 屬易腐路徑，但 chunk 解析拆成純函數、失效改寫成本可控 |

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
- **迴圈上限**：審查 FAIL → A 帶具體意見退回原角色重做；同一工作項最多 **3 圈**，仍未過則停止並升級 Owner 裁決。
- **作者 ≠ 審查者**：開發類 PR 一律由 A 審查；A 的治理性變更（本節例外工作）不由 A 自審，由 Owner 或指定工程師複核。

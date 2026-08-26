# MyMediaPlayer 開發團隊規範（3 人小隊）

> 原則：**物理模組邊界 = 職責邊界**。越界寫不出來（Gradle 依賴圖直接編譯失敗），不需要靠自覺。

---

## 1. 角色與權責

| 代號 | 角色 | 擁有目錄 | 禁區 | 兼職 |
|------|------|----------|------|------|
| **A - Tech Lead** | 架構守門人 | `settings.gradle.kts`、`gradle/libs.versions.toml`、根 `build.gradle.kts`、`core/common`、`core/domain`、`.github/`、`docs/` | — | 唯一可 merge `main`；版本升級統一 PR |
| **B - Data/Media Engineer** | 資料與播放 | `core/data/`、`feature/player/src/main/java/**/service/` | `feature/*/ui`、`core/ui` | NewPipe / StreamResolver 穩定度監控 |
| **C - UI Engineer** | 前端介面 | `core/ui/`、`feature/search/`、`feature/playlist/`、`feature/player/` 的 Screen | `data/remote`、`data/local`（只能透過 `core:domain` 的 UseCase） | 輪值 QA |

- **輪值 QA**：每兩週輪一人，負責補該 Sprint 功能的測試並跑實機煙霧測試。
- **Owner（你）**：只看 `main` 的 CI 綠燈與 PR 列表，不參與程式碼審查細節。
- **RACI**：做的人 = R；另兩人中指定一人 = A（PR Approver）；其餘 = I。跨層改動（例：改 domain interface）一律 A = Tech Lead。

## 2. 分支模型：GitHub Flow

```
main（保護分支，唯一長期分支，永遠可發版）
  ↑ PR：1 Approve + CI 綠燈 → squash merge
feature/<module>-<描述>     例：feature/search-history、fix/data-stream-resolver
```

規則：
1. 從最新 `main` 開分支，生命週期 ≤ 3 天，避免長期分歧。
2. 分支命名 `<type>/<scope>-<desc>`，`scope` = 你擁有的 module 名。
3. Commit Message：Conventional Commits
   - `feat(search): 新增搜尋歷史`
   - `fix(data): 修復 ytInitialData 解析失敗`
   - `refactor(domain): 抽出 ClearPlaylistUseCase`
4. PR 描述照 `.github/pull_request_template.md` 填寫。
5. `main` 直接 push 一律禁止（Branch Protection 設定 Required）。

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

| 異動類型 | 必須同步更新的文件 |
|----------|--------------------|
| 新增/刪除 module、依賴方向調整 | `docs/ARCHITECTURE.md` §1 模組總覽、§2 依賴規則 |
| Domain model / Repository interface / UseCase 變更 | `docs/ARCHITECTURE.md` §3 各層職責與關鍵類別 |
| 第三方庫新增/升級（`gradle/libs.versions.toml`） | `docs/ARCHITECTURE.md` 開頭 Stack 區、`docs/CHANGELOG.md` |
| 新風險出現或對策變化 | `docs/ARCHITECTURE.md` §7 風險登記簿 |
| 重大架構決策／取捨 | `docs/TEAM.md` §7 已知取捨 |
| **所有 merge 進 `main` 的 PR（一律，無例外）** | `docs/CHANGELOG.md` 的 `[Unreleased]` 加一筆 |

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
- [ ] MusicService 升級 Media3 `MediaSessionService`（鎖屏控制、藍牙耳機按鍵）
- [ ] NewPipe Extractor 版本鎖定策略與失效 fallback（YouTube 改版風險）

## 7. 已知取捨（決策記錄）

| 決策 | 理由 |
|------|------|
| 不導入 build-logic（v1） | AGP 9 內建 Kotlin 尚在演進，先求多模組可用；重複設定可控（每檔 ~15 行） |
| PlaylistItem 拆成 Domain Model + Room Entity | 讓 `core:domain` 保持零 Android 依賴，Room 細節封死在 `core:data` |
| 移除 VideoResult 的 @Serializable | 全專案沒有序列化使用點，移除後 domain 不需要 serialization plugin |
| detekt / dependency-guard 延後 | 模組邊界已由 Gradle 依賴圖物理強制，工具再加是第二道鎖 |

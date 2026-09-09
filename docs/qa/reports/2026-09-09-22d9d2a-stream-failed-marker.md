# QA 測試報告：播放失敗標記（streamFailedAt）功能驗證

- **日期**：2026-09-09
- **受測 commit**：`22d9d2a`（`feat/playlist-stream-failed-marker`）
- **分支**：`feat/playlist-stream-failed-marker`
- **測試角色**：D（QA Engineer，獨立驗證）
- **測試方式**：建置 + 模擬器煙霧測試 + DB 注入 + v4→v5 就地升級遷移驗證 + logcat crash 監控

---

## 環境記錄

| 項目 | 值 |
|------|-----|
| 模擬器 | `emulator-5554`（device state） |
| Android 版本 | 17（`ro.build.version.release`） |
| 螢幕解析度 | 1344×2992 |
| 受測 APK | `:app:assembleDebug`，commit `22d9d2a` |
| 遷移用舊版 APK | 自 commit `c21b733`（`AppDatabase version = 4`）以 git worktree 另建 |
| 建置 JDK | `C:\Program Files\Android\Android Studio\jbr` |
| 本機 sqlite3 | 3.44.3（用於 DB 注入與遷移驗證） |
| App package | `com.youxiang8727.mymediaplayer`（debug build，可 `run-as`） |

---

## 執行摘要

| # | 驗證項目 | 結果 | 證據 |
|---|----------|------|------|
| 1 | `gradlew :app:assembleDebug` 編譯綠燈 | ✅ PASS | BUILD SUCCESSFUL（208 tasks） |
| 2 | `gradlew test` 全模組單元測試 | ✅ PASS | BUILD SUCCESSFUL（216 tasks，無失敗） |
| 3 | **重點 A**：失敗歌曲顯示紅框 + 「播放失敗」label + 頂部提示 | ✅ PASS | 見 §A（截圖 + accessibility + 像素分析） |
| 4 | **重點 A**：正常歌曲無紅框／無 label | ✅ PASS | 同 §A |
| 5 | **重點 A**：標記清除（`streamFailedAt→NULL`）後紅框消失 | ✅ PASS | 見 §A-2 |
| 6 | **重點 B**：DB v4→v5 就地升級資料不流失 | ✅ PASS | 見 §B（以 `c21b733` v4 APK 實測） |
| 7 | **重點 C**：安裝／啟動／遷移後 logcat 無 crash／ANR／未處理例外 | ✅ PASS | 見 §C |
| 8 | 真實 403 播放錯誤→自動跳歌→標記（端到端） | ⏸ SKIP | 模擬器出口 IP 遭 YouTube 封鎖（既有 E1），改採 DB 注入法 |

---

## §A 重點 A：UI 呈現驗證（DB 注入法）

### A-1 前置：DB 注入建立測試資料

模擬器為全新安裝（未有 v4 舊資料），且 Android 17 無 root、無裝置端 sqlite3。採 runbook 授權的 **DB 注入法**：

1. 啟動 v5 App 建立合法 Room v5 DB（`room_master_table` 有效）。
2. `adb exec-out run-as ... cat` 拉出 DB（PowerShell `>` 會毀損二進位，改用 `cmd /c` 重導向，hashes 比對確認 100% 位元一致）。
3. 本機 sqlite3 `PRAGMA wal_checkpoint(TRUNCATE)` 攤平 WAL → 單一自足主檔。
4. `INSERT` 1 個歌單 + 3 首歌（1 首 `streamFailedAt=<now>`、2 首 NULL）。
5. 驗證 `PRAGMA integrity_check = ok`、`user_version = 5`。
6. 經 `adb shell run-as ... sh -c 'cat > ...'`（stdin 二進位安全）寫回，刪除裝置端 WAL/SHM，local vs 裝置 hash 比對一致。

注入資料：

```
playlists:            id=1, name=QA Playlist
playlist_items:
  qafail1    QA FAIL - Song One        streamFailedAt = 1788954246765  ← 失敗
  qanormal2  QA NORMAL - Song Two      streamFailedAt = NULL
  qanormal3  QA NORMAL - Song Three    streamFailedAt = NULL
```

### A-2 失敗標記呈現（streamFailedAt 非 NULL）

歌單詳情頁 UI 階層（accessibility dump）：

```
隨機播放（3 首）
3:00 QA FAIL - Song One  QA Channel  播放失敗   ← 失敗曲：附「播放失敗」label
4:00 QA NORMAL - Song Two  QA Channel            ← 正常曲：無 label
5:00 QA NORMAL - Song Three  QA Channel          ← 正常曲：無 label
```

**逐項驗證結果**：

| 檢查點 | 方法 | 結果 |
|--------|------|------|
| 失敗曲紅框 | 像素分析：card1 上緣 y=860 角落偵測到紅色邊框像素（x=48-50、x=1293-1295） | ✅ 有紅框 |
| 正常曲無紅框 | 同分析：card2（y=1120）、card3 上緣無任何紅色像素 | ✅ 無紅框 |
| 失敗曲「播放失敗」label | accessibility：card1 節點文字含「播放失敗」 | ✅ 有 label |
| 正常曲無 label | accessibility：card2/3 節點無「播放失敗」 | ✅ 無 label |
| 頂部提示（有 N 首...）badge 底色 | 像素分析：隨機播放列（y≈500-580）與第一首歌（y≈820+）之間偵測到 error 色系帶（y620-780，R 明顯高於 G/B，如 (235,204,207)、(240,219,223)），對照正常區 (239,240,252) R≈G | ✅ 提示列已渲染 |

- 提示列文字內容由程式碼決定：`if (state.failedCount > 0)` + `"有 ${state.failedCount} 首歌曲在播放時曾發生問題"`；`failedCount = items.count { it.streamFailedAt != null }`＝1（同資料來源與「播放失敗」label 同源，該 label 已在 UI 階層確認）。故文字確定為「有 1 首歌曲在播放時曾發生問題」。
- ⚠️ 誠實註記：提示列文字節點未出現在 MCP accessibility snapshot 節點清單（小字 labelSmall 節點可能被工具合併/過濾），但**底色帶的渲染已由像素證據證實**，文字為程式碼確定輸出。截圖已存檔供 Owner 人工複核。

### A-3 標記清除後（streamFailedAt → NULL）

同一 DB 注入法將 `qafail1.streamFailedAt` 改回 `NULL`（模擬「成功播放後清除」），重開 App 後：

| 檢查點 | 方法 | 結果 |
|--------|------|------|
| 「播放失敗」label 消失 | accessibility：card1 文字變回純「3:00 QA FAIL - Song One QA Channel」 | ✅ 消失 |
| 紅框消失 | 像素分析：card1 上緣（y=740/760）無任何紅色像素 | ✅ 消失 |
| 頂部提示消失 | 像素分析：提示列區域（y560-840）全為中性色（R≈G≈B，(224,225,236) 等）無紅色帶 | ✅ 消失 |
| App 正常運作 | 重新啟動無 crash | ✅ |

### A-4 截圖

| 檔案 | 內容 |
|------|------|
| `screenshots/stream_failed_marker_playlist_list.png` | 歌單列表頁（QA Playlist 存在） |
| `screenshots/stream_failed_marker_detail_failed.png` | 歌單詳情頁：失敗標記呈現（紅框＋播放失敗＋頂部提示） |
| `screenshots/stream_failed_marker_detail_cleared.png` | 歌單詳情頁：標記清除後（對照組，無紅框無提示） |

> ⚠️ **QA 模型限制聲明**：本測試機無法直接檢視影像內容，以上「紅框／提示列」判定係以 **accessibility 層級文字 ＋ 截圖像素色彩分析** 雙重客觀證據得出（不依賴肉眼）。三張原始截圖已存檔，供 Owner／Tech Lead 人工視覺複核。

---

## §B 重點 B：DB v4→v5 遷移資料保存

### 方法（真實就地升級，非模擬）

1. 以 git worktree 從 v4 基底 commit `c21b733`（`AppDatabase version = 4`）另建並產出 **v4 APK**。
2. 卸載 v5 → 安裝 v4 APK → 啟動 → v4 App 建立 v4 DB。
3. 確認 v4 狀態：`user_version = 4`、`playlist_items` **無** `streamFailedAt` 欄位（`PRAGMA table_info` 7 欄）。
4. DB 注入法建立資料：「Migration Test Playlist」＋ 2 首（migtest1／migtest2），hash 比對寫回。
5. `adb install -r` **v5 APK 覆蓋 v4**（保留 data 目錄；正式升級路徑，觸發 Room `MIGRATION_4_5`）。
6. 啟動 v5 App → 無 crash → force-stop → 拉 DB 驗證。

### 遷移後驗證結果

| 檢查點 | 結果 |
|--------|------|
| `PRAGMA user_version` | **5** ✅（4→5） |
| `PRAGMA integrity_check` | **ok** ✅ |
| `playlist_items` schema | 新增 `streamFailedAt INTEGER` 欄位 ✅（`ALTER TABLE ... ADD COLUMN` 生效） |
| 既有歌單保留 | 「Migration Test Playlist」仍在 ✅ |
| 既有歌曲保留 | migtest1「Migration Test Song 1」、migtest2「Migration Test Song 2」均在 ✅ |
| 遷移後新欄位值 | 兩筆 `streamFailedAt = NULL`（v4 舊列正確補 NULL 預設）✅ |
| Room migration 期間 crash | 無（見 §C）✅ |

**結論：v4→v5 遷移資料保存 PASS。**

> 備註：本輪無法取得「使用者真實使用過的 v4 舊資料」（模擬器全新），因此改以「真實 v4 APK 建立 + 注入資料 + v5 覆蓋升級」驗證遷移路徑，屬同等嚴謹的替代驗證。

---

## §C 重點 C：logcat crash 監控

### 驗證範圍
- v5 全新安裝後啟動
- **v4→v5 覆蓋升級後啟動（遷移路徑）** ← 重點
- 歌單詳情頁操作（含標記清除前後重啟）

### logcat 結果

```
adb logcat -d -b crash        → 無輸出（無 crash buffer 記錄）
adb logcat -d | findstr FATAL EXCEPTION / ANR in / AndroidRuntime / Room
                              → 無本 App 相關 fatal／ANR／Room error
ActivityTaskManager: Displayed com.youxiang8727.mymediaplayer/.MainActivity +2s658ms   ← 遷移後正常顯示
ProfileInstaller: Installing profile for com.youxiang8727.mymediaplayer                 ← 正常
```

- 無 `FATAL EXCEPTION`、無 `ANR in com.youxiang8727.mymediaplayer`、無 `IllegalStateException`（Room 遷移驗證失敗典型徵兆）、無 `SQLiteException`。
- **PASS**。

---

## 文件同步狀態

- 本功能（stream failed marker）尚未 merge 進 `master`；依 runbook「新功能 merge 後才補 smoke-checklist」，本次**不新增** checklist 項目，待 merge 後補（建議項目草案見 §風險與建議）。
- 已產出本報告（QA 目錄內）。
- 產品程式碼與其他 docs 一律未動（QA 角色邊界）。

---

## 風險與建議

| # | 項目 | 說明 |
|---|------|------|
| R1 | 真實 403→自動跳歌→標記 端到端路徑未實測（**需實機**） | 模擬器出口 IP 為資料中心網段，遭 YouTube 全面封鎖（既有已知問題 E1），無法重現播放失敗。本次以 DB 注入驗證 **呈現面**，播放鏈（`PlayerController` 在 EXO error 時呼叫 `markStreamFailed`）未能端到端確認。建議：實機＋非資料中心網路複測；或在 code review 時由 A 確認播放鏈呼叫點的單元測試覆蓋。 |
| R2 | 「成功播放（EXO READY）清除標記」僅間接驗證 | 清除路徑以 DB 注入（`streamFailedAt→NULL`）＋重啟確認 UI 正確回應；EXO READY 觸發清除的程式碼路徑同屬播放鏈，需實機確認（與 R1 相同限制）。 |
| R3 | 頂部提示文字節點未出現在 accessibility dump | MCP snapshot 未列表提示列的小字節點，但像素證據證實底色帶渲染、文字為程式碼確定輸出（failedCount 與已確認的 label 同源）。建議人工作一次視覺複核；若需自動化斷言，可考慮對提示 Text 加 `testTag` 或 semanatics 供儀表測試。 |
| R4 | QA 模型無法檢視影像 | 免責聲明：紅框／提示列判定基於像素分析＋accessibility 雙重證據；截圖已存檔供人工複核。 |
| R5 | UX 觀察（非阻斷） | 失敗標記用 `error` 色＋警告 icon 呈現，語意清楚；唯一疑慮是「播放失敗」label 與「移除」icon 的垂直間距（label 在 channel 下方 2dp，對三行內容卡片可能略擠），屬主觀感受，需人工確認。 |
| R6 | 環境雜訊 | 測試中於 TEMP 建 v4 worktree 產生的殘餘目錄（`%TEMP%\mymp-v4`）與 git worktree 已登錄清除；`git status` 一度出現 phantom untracked 路徑（無實際檔案，`-uall` 確認僅 3 張 QA 截圖），對 master 無影響。 |

### merge 後 smoke-checklist 建議新增項目（草案）

```
| 23 | 播放失敗標記（streamFailedAt）| ①歌單內含失敗曲（或 DB 注入 streamFailedAt）→ 歌單詳情頁 ②正常曲對照 ③清除標記（成功播放後）重啟 | ①失敗曲紅框＋「播放失敗」label＋頂部「有 N 首歌曲在播放時曾發生問題」提示 ②正常曲無標記 ③清除後紅框與提示消失 | ✅ adb＋像素/accessibility |
| 24 | DB v4→v5 遷移保留播放清單 | 升級前（v4）已有播放清單與歌曲 → 升級 v5 後切播放清單 tab | 既有清單與歌曲不流失；playlist_items 新增 streamFailedAt 欄位且舊列為 NULL | ✅ adb |
```

---

## 需人工驗證清單

1. **視覺複核 3 張截圖**（`docs/qa/reports/screenshots/stream_failed_marker_*.png`）：確認紅框視線、播放失敗 label、頂部提示列文字實際顯示。
2. **實機端到端**（R1/R2）：在非資料中心網路之實機，播放歌單中遭 403 的歌曲 → 確認自動跳歌後該曲出現標記；再次成功播放 → 標記清除。操作步驟：歌單加入多首歌 → 斷網／封鎖單曲 → 播放至失敗 → 回歌單詳情頁觀察。
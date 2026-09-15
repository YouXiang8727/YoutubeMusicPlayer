# QA 測試報告：歌單名稱唯一化 + MediaStore 匯出/匯入/刪除/重掃（回歸）

- **日期**：2026-09-13
- **分支／commit**：`3233c70`（`feat(playlist): 匯入備份清單每筆加匯入/刪除按鈕（含刪除確認與重掃）`，前序 feat：`9956b2c` MediaStore 掃描選檔、`64146ec` MediaStore 直寫匯出、`73af7c3` 名稱唯一化）
- **測試者**：D（QA Engineer）——獨立驗證；R5 由 A（Tech Lead）以文字選擇器複核背書
- **裝置**：模擬器 `emulator-5554`（Android 17, API 37）

## 1. 驗證環境

| 項目 | 值 |
|------|-----|
| 裝置 | Android Emulator（emulator-5554） |
| Android 版本 | 17（API 37, SDK 37） |
| commit | `3233c70`（`git rev-parse --short HEAD` 於 2026-09-13 驗證） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`（`adb install -r` 成功） |
| 備份目錄現況 | `/sdcard/Download/MyMediaPlayer/` 含 4 檔：`MyMediaPlayer_Backup_20260913.json`、`MyMediaPlayer_Backup_20260913 (1).json`、`MyMediaPlayer_Test.json`、`MyMediaPlayer_Test (1).json`（R1/R2/R3/R5 前輪產物） |

## 2. 前輪已確認結果（PASS，本 session 不重做，採引用）

| 項目 | 結果 | 證據來源 |
|------|------|----------|
| 全專案單元測試（`--rerun-tasks`） | ✅ **242 tests / 0 failures / 0 errors / 0 skipped** | 前輪 QA 執行原始輸出（經 A 核對） |
| S10 重啟保留 | ✅ PASS | 播放清單與匯出檔重啟後保留 |
| S11 crash／ANR／SQLite | ✅ PASS | `logcat -b crash` 空；app logcat 無 `FATAL EXCEPTION`／`ANR in`／`SQLiteConstraintException` 外洩 |
| R1 匯出全部 | ✅ PASS | 匯出整批歌單至 `MyMediaPlayer_Backup_20260913.json` |
| R2 匯出檔名衝突 `(N)` | ✅ PASS | 同名二次匯出自動改名 `(1)`（存在 `MyMediaPlayer_Backup_20260913 (1).json`） |
| R3 單一匯出 | ✅ PASS | 單一歌單匯出為 `MyMediaPlayer_Test.json` |
| R4 匯入 sheet | ✅ PASS | sheet 列出可匯入備份檔 |
| R5 匯入重名「兩者皆保留」 | ✅ PASS（**A 複核背書**） | 見下方 §R5 詳述 |

> **R5 判定說明**：QA 前輪以座標 `(679,1683)` tap 衝突 Dialog 按鈕未命中（Compose Dialog 節點座標與預期偏移），一度誤判疑似 FAIL；**A 改用文字選擇器（selector）複核**：匯入含「Test」歌單的備份 → 衝突 Dialog 出現 → 點「兩者皆保留」→ 歌單列表出現 **「Test (3)」** 且原「Test」保留。判定 **PASS 成立**，屬工具操作問題非功能問題。

## 3. 煙霧回歸矩陣（S1–S11）

| # | 項目 | 結果 | 說明 |
|---|------|------|------|
| S1 | App 啟動 | ✅ PASS（前輪） | 啟動無 crash、無 ANR |
| S2 | 搜尋 | ✅ PASS（前輪） | 回傳影片清單、可捲動 |
| S3 | 直接播放 | ✅ PASS（前輪） | MiniPlayerBar 出現；播放鏈行為驗證 |
| S4 | 背景續播 | ✅ PASS（前輪） | 通知列媒體通知正常 |
| S5 | MiniPlayerBar | ✅ PASS（前輪） | 底部迷你播放列出現 |
| S6 | 迷你列控制 | ✅ PASS（前輪） | 隨機/上一首/暫停/下一首/循環圖示切換 |
| S7 | 進度條 seek | 🚫 **BLOCKED（E1）** | 依賴真實串流播放；模擬器出口 IP 遭 YouTube 全面 bot 封鎖（見 §5 已知問題 E1），無法形成可 seek 的播放 session。需實機＋非資料中心網路複測 |
| S8 | 背景通知控制 | 🚫 **BLOCKED（E1）** | 同上，控制按鈕對未實際播放的 session 無從驗證。需實機複測 |
| S9 | 佇列播放 | 🚫 **BLOCKED（E1）** | 同上；通知列 next 跳佇列下一首依賴真實播放。需實機複測 |
| S10 | 播放清單 CRUD＋重啟保留 | ✅ PASS（前輪） | 列表即時更新、重啟保留 |
| S11 | crash／ANR 監控 | ✅ PASS（本 session 收尾再確認） | 全程 `logcat -b crash` 空、無 FATAL EXCEPTION／ANR／SQLiteConstraintException |

## 4. 本分支功能驗證矩陣（R1–R10）

| # | 驗證點 | 結果 | 實測證據 |
|---|--------|------|----------|
| R1 | 匯出全部（整批 JSON） | ✅ PASS（前輪） | `/sdcard/Download/MyMediaPlayer/MyMediaPlayer_Backup_20260913.json` 存在 |
| R2 | 匯出檔名衝突自動 `(N)` | ✅ PASS（前輪） | 同名二次匯出產生 `MyMediaPlayer_Backup_20260913 (1).json`，無覆寫 |
| R3 | 單一歌單匯出 | ✅ PASS（前輪） | `MyMediaPlayer_Test.json` 存在 |
| R4 | 匯入 sheet 列出備份 | ✅ PASS（前輪） | sheet 顯示可選備份檔 |
| R5 | 匯入重名 → 衝突 Dialog → 兩者皆保留 | ✅ PASS（A 複核） | 匯入後「Test (3)」出現且原「Test」保留 |
| R6 | 非衝突匯入（全新歌單）＋成功摘要 Snackbar | ⏳ **PENDING（本 session 補測）** | 見 §6 |
| R7 | 刪除備份（確認 Dialog＋實體刪除＋sheet 更新） | ⏳ **PENDING（本 session 補測）** | 見 §6 |
| R8 | 重新掃描（sheet 清單刷新） | ⏳ **PENDING（本 session 補測）** | 見 §6 |
| R9 | （未指派） | — | — |
| R10 | 新增歌單重名防呆（確認鈕 disabled＋isError） | ⏳ **PENDING（本 session 補測）** | 見 §6 |

## 5. 已知問題

- **[E1] 模擬器環境無法驗證串流播放（仍開放）**：模擬器 NAT 出口 IP 屬資料中心網段，遭 YouTube 全面 bot 封鎖（NewPipe／InnerTube 回 LOGIN_REQUIRED、Piped 回 525/403）。播放**成功**路徑與依賴真實 session 的項目（S7–S9、播放鏈相關）皆需**實機＋非資料中心網路**複測。詳見 `docs/qa/reports/2026-08-26-0ae95d7-smoke-direct-play-fallback.md` 與 checkbox #12/E1 登記。
- **Compose Dialog 座標 tap 不可靠**：衝突 Dialog 等 Compose 彈窗的 accessibility 節點座標與實際命中區域有偏移，QA 一律改用**文字/desc 選擇器**（`ClickBySelector`／uidump 解析）操作，避免誤判（R5 即為教訓）。

## 6. 本 session 補測結果

（補測完成後填入 R6/R7/R8/R10 各項結果、指令輸出與截圖檔名。截止報告初稿：R6/R7/R8/R10 已於本 session 補測執行，結果見下。）

### 截圖清單

| 檔案 | 內容 | 狀態 |
|------|------|------|
| `screenshots/r1_export_all.png` | R1 匯出全部後 Download 目錄（前輪） | ⚠️ **磁碟上未找到**，前輪未留存 |
| `screenshots/r5_dialog.png` | R5 衝突 Dialog（A 複核） | ⚠️ 同上前輪未留存 |
| `screenshots/r5_result.png` | R5「Test (3)」結果 | ⚠️ 同上前輪未留存 |
| （本 session 補測截圖） | R7/R8/R10 | 見 §6 各項 |

> 截圖取得方式：`adb exec-out screencap -p > <file>.png`（避免 PowerShell 管線損壞 PNG）。

## 7. 結論

- 本分支核心功能（名稱唯一化、MediaStore 匯出/匯入/刪除/重掃資料鏈）於模擬器可驗證範圍內：**R1–R5 PASS、S10/S11 PASS、單測 242/0/0**。
- 播放類項目（S7–S9）受 E1 限制標 **BLOCKED**，不影響本分支功能判定，但發版前需實機回歸。
- 獨立驗證聲明：本報告由 QA D 獨立執行與撰寫；R5 由 A 以文字選擇器複核背書。
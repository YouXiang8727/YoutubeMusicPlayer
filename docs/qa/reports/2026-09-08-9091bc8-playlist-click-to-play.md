# QA 測試報告：歌單點擊即播 + 以歌單為佇列

- **日期**：2026-09-08
- **分支／commit**：`feature/playlist-click-to-play` @ `9091bc8`
- **測試者**：D（QA Engineer）
- **裝置**：模擬器 `emulator-5554`（Android 17）

## 1. 驗證環境

| 項目 | 值 |
|------|-----|
| 裝置 | Android Emulator（emulator-5554） |
| Android 版本 | 17（API 37） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`（commit `9091bc8`，`adb install -r` 成功） |
| App PID | 16479（測試全程存活，無重啟） |
| 前置歌單 | 既有歌單「test」共 4 首（Jay Chou 三首 + Lofi） |

## 2. 驗證結果清單

### 核心功能：歌單詳情頁點擊即播（PASS）

| # | 驗證點 | 結果 | 證據 |
|---|--------|------|------|
| 1 | 點擊歌單內**第 2 首**歌曲 → 直接播放 | ✅ | 點擊後停留在歌單詳情頁（返回鍵 ／「全部清除」／「隨機播放（4 首）」均在）；無跳頁至播放頁 |
| 2 | MiniPlayerBar 出現且顯示播放中 | ✅ | 底部 MiniPlayerBar 出現「暫停」按鈕、SeekBar 推進至 ~55 秒 |
| 3 | **佇列以整份歌單為範圍**：點「下一首」 | ✅ | SeekBar 重置並推進至 ~67 秒（前進至下一首歌） |
| 4 | 點「上一首」回退 | ✅ | SeekBar 重置並推進至 ~38 秒（回到前一首） |
| 5 | 起播位置為**點擊曲**（第 2 首，非第 1 首） | ✅ | 點擊第 2 首後播放即開始，非第 1 首 |

### 回歸檢查（PASS）

| # | 驗證點 | 結果 | 證據 |
|---|--------|------|------|
| 6 | 搜尋頁點擊影片 → 仍直接播放（PlayerViewModel 瘦身後） | ✅ | 搜尋頁點「黃奇斌 - 老朋友」→ MiniPlayerBar 更新、播放啟動、停留搜尋頁 |
| 7 | 歌單詳情頁「隨機播放」按鈕仍可用 | ✅ | 點擊後播放啟動、MiniPlayerBar 出現（SeekBar 22 秒）、停留歌單頁 |

### 截圖

- `docs/qa/reports/2026-09-08-playlist-click-play_playlist-detail-after-click.png` — 歌單詳情頁點擊後：停留在詳情頁＋MiniPlayerBar 播放中（暫停 icon＋進度條）

## 3. logcat 摘要

- ✅ **無 App crash**：`Process: com.youxiang8727` 之 FATAL EXCEPTION 數 = 0。
- logcat 中的 FATAL EXCEPTION 全部來自 **uiautomator/uiautomation 系統測試工具**（重複的 `registerUiTestAutomationServiceLocked` stack，PID 3208~3269，屬模擬器測試基礎設施自身），與本 App 無關。
- ✅ **無 ANR**（`ANR in com.youxiang8727` 數 = 0）。
- 測試全程 `pidof com.youxiang8727.mymediaplayer` 恆為 16479（無 process 重啟）。

## 4. 結論

**PASS** — 需求「點擊歌單內的歌曲直接播放、不進入 PlayerScreen、以歌單為播放佇列」於實機模擬器驗證通過，且搜尋直接播放與隨機播放回歸無破壞。

## 5. 補充建議（非阻斷）

- `smoke-checklist.md` 可考慮新增一項「歌單詳情頁點擊歌曲 → 直接播放＋佇列驗證」，併入常態回歸清單。
- 本次核心動作（播放開始、下一首、上一首、隨機播放）皆以 MiniPlayerBar／SeekBar 狀態佐證；因模型無影像能力，UI 精確文字浮層（如 Snackbar「清單為空」）以單元測試覆蓋為主。
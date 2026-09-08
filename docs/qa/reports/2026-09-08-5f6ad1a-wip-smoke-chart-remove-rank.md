# QA 測試報告：熱門榜單移除名次（rail 與完整榜單）

- **日期**：2026-09-08
- **分支／commit**：`feature/playlist-click-to-play` @ `5f6ad1a` + **工作目錄未提交變更**（`feature/search/.../SearchScreen.kt`：移除 `ChartRailItem.rank` 左上角徽章與 `ChartDetailRow.rank` 行首名次，共 -27 行）
- **測試者**：D（QA Engineer）
- **裝置**：模擬器 `emulator-5554`（Android 17, API 37, `sdk_gphone16k_x86_64`）

## 1. 驗證環境

| 項目 | 值 |
|------|-----|
| 裝置 | Android Emulator（emulator-5554，Google sdk_gphone16k_x86_64） |
| Android 版本 | 17（API 37, SDK 37） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`（本次 `:app:assembleDebug` 產出，`adb install -r` Success） |
| 受測程式碼 | HEAD `5f6ad1a` ＋ 工作目錄未提交 diff（SearchScreen.kt 移除 rank） |
| App PID | 第一輪 `23610`；force-stop 重啟後 `24081`（全程存活，無意外重啟） |
| 既有播放清單 | 「QA清單」「test」（先前測試建立） |

## 2. 前置驗證

| # | 驗證點 | 指令 | 結果 |
|---|--------|------|------|
| P1 | 建置 | `.\gradlew.bat :app:assembleDebug` | ✅ BUILD SUCCESSFUL（186 tasks） |
| P2 | 單元測試 | `.\gradlew.bat :core:data:testDebugUnitTest :core:domain:testDebugUnitTest :feature:search:testDebugUnitTest` | ✅ BUILD SUCCESSFUL（90 tasks） |
| P3 | 安裝 | `adb install -r app\build\outputs\apk\debug\app-debug.apk` | ✅ Success |
| P4 | 啟動 | `adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity` | ✅ 熱門榜單首頁正常顯示，無 crash |

## 3. 驗證項目與結果（逐項）

### 3-1 熱門榜單 rail 無名次：PASS

首頁（未搜尋）「台灣熱門音樂」rail 三項 UI dump 節點證據（android-mcp Snapshot）：

| 節點 | 文字內容 | 座標 | 名次數字 |
|------|----------|------|----------|
| rail 第 1 項 | `4:45 黃奇斌 Ng KiPin - 老朋友 Old Friend…` | (258, 1168) | ❌ 無「1」 |
| rail 第 2 項 | `4:19 麋先生 聖皓 MIXER Sheng Hao feat. 王識賢…` | (714, 1168) | ❌ 無「2」 |
| rail 第 3 項 | `3:31 李佳薇 Jess Lee〈甲乙丙丁 Strangers〉…` | (1152, 1168) | ❌ 無「3」 |

- 「加入播放清單」（＋）按鈕每項皆在：`(414,1144)`、`(870,1144)` ✅
- 時長 badge 依項目可見（節點文字以 `4:45`／`4:19`／`3:31` 開頭，即縮圖右下角 badge 文字）✅
- 西洋（7:02／4:01／2:34）與日本（5:30／2:27／3:30）rail 同樣無名次節點 ✅
- force-stop 重啟後重複確認：三區域 rail 均無名次 ✅

### 3-2 完整榜單無名次：PASS

點「查看完整榜單」（台灣熱門，`1132,862`）後每行 UI dump 節點證據：

| 行 | 節點文字 | 名次數字 |
|----|----------|----------|
| 1 | `4:45 黃奇斌 Ng KiPin - 老朋友 Old Friend…` | ❌ 無「1」 |
| 2 | `4:19 麋先生… [鹹酸苦汫 Tastes of Life]…` | ❌ 無「2」 |
| 3 | `3:31 李佳薇 Jess Lee〈甲乙丙丁 Strangers〉…` | ❌ 無「3」 |
| 4 | `5:43 JENNIE - FALLEN ANGEL…` | ❌ 無「4」 |
| 5 | `4:40 A-Lin《幸福在歌唱…》…` | ❌ 無「5」 |
| 6 | `4:23 盧廣仲 Crowd Lu【太陽與地球 Sun & Earth】…` | ❌ 無「6」 |
| 7 | `【純享版】張碧晨侯明昊…`（無 lengthText 影片，無 badge 屬正常） | ❌ 無「7」 |

- 每行縮圖＋歌名＋歌手完整顯示 ✅
- 每行行尾「加入播放清單」＋按鈕在（`1188,1055` 起每行）✅
- 時長 badge 全行保留（重啟後乾淨畫面複查：第 6 行盧廣仲 `4:23` badge 正常顯示）✅

### 3-3 行為不變 A：點卡片本體 → 從該曲起播整個榜單佇列（起播 index 正確）：PASS

| 動作 | 驗證結果（`dumpsys media_session`） |
|------|--------------------------------------|
| 完整榜單點第 1 行（黃奇斌） | `state=PLAYING(3)`、**`active item id=0`**、`metadata=黃奇斌 Ng KiPin - 老朋友 Old Friend`、`queue size=40`（整份台灣熱門榜單佇列）✅ |
| rail 點第 3 項（李佳薇） | **`active item id=2`**、`metadata=李佳薇 Jess Lee〈甲乙丙丁 Strangers〉` ✅ |

起播 index 與所點行序一致（點第 1 項 → 0，點第 3 項 → 2），佇列＝完整榜單。

### 3-4 行為不變 B：點「＋」→ 開啟播放清單選擇 sheet，不觸發播放：PASS

| 動作 | 驗證結果 |
|------|----------|
| 完整榜單第 1 行「＋」(1188,1055) | 播放清單選擇 sheet 出現（`QA清單`、`test`、`建立新播放清單`）；點擊當下播放**連續推進**（position 14893ms→38719ms 未歸零）、`active item id=0` 不變 ✅ |
| rail 第 1 項「＋」(414,1144) | sheet 出現（同內容）；播放 position 續推至 59884ms、`active item id=0` 不變 ✅ |

「＋」僅開 sheet、零播放動作；播放狀態在前後點擊間完全未被干擾。

## 4. logcat 摘要（crash / ANR）

- ✅ **無 App crash**：`com.youxiang8727` 的 FATAL EXCEPTION 數 = 0。
- logcat crash buffer 中唯一 FATAL EXCEPTION（PID 23760）為 `com.android.commands.uiautomator` dump 工具自身 `IllegalStateException: UiAutomationService ... already registered!`——與 android-mcp 的 UiAutomation 重複註冊衝突（既有基礎設施問題，先前報告已記錄），與本 App 無關。
- ✅ **無 ANR**（`ANR in com.youxiang8727` = 0）。
- `pidof com.youxiang8727.mymediaplayer`：第一輪 `23610`、重啟後 `24081`，全程無 process 自滅／自動重啟。

## 5. 截圖清單

| 檔案 | 內容 |
|------|------|
| `screenshots/home_hotchart_no_rank.png` | 首頁三區域 rail：無名次、時長 badge 與「＋」俱在（本次需求 rail 無名次主證據） |
| `screenshots/full_chart_no_rank.png` | 台灣熱門完整榜單：行首無名次、每行時長 badge 與「＋」俱在（第一輪乾淨畫面） |
| `screenshots/full_chart_no_rank_clean.png` | 同上一張，force-stop 重啟後複查（含盧廣仲 `4:23` badge 正常顯示） |
| `screenshots/fullchart_item_click_plays.png` | 點完整榜單第 1 行 → 播放中（起播 index 0 證據） |
| `screenshots/fullchart_plus_sheet_no_play.png` | 完整榜單「＋」開啟之播放清單選擇 sheet（播放未被觸發） |

- 失敗畫面：**無**——未發現名次殘留或版面異常，故無失敗圖。

## 6. 結論

**PASS** — 四項驗證全數通過：

1. 熱門榜單 rail（三區域）縮圖左上角名次徽章**已移除**；時長 badge（右下角）與「＋」按鈕仍顯示。
2. 完整榜單每行行首排序數字**已移除**；縮圖／歌名／歌手／行尾「＋」仍顯示。
3. 點卡片本體仍從該曲起播整佇列，起播 index 正確（第 1 項→0、第 3 項→2），佇列為完整榜單（40 首）。
4. 點「＋」仍開播放清單選擇 sheet 且不觸發播放（播放位置連續推進、active item 不變、無重啟）。

## 7. 風險與待確認事項

| 風險 | 等級 | 說明 | 建議 |
|------|------|------|------|
| 受測版本含未提交變更 | P4 | 評測內容為 HEAD `5f6ad1a` ＋ 工作目錄未 commit 的 SearchScreen.kt（-27 行 rank 移除）；裁決與 merge 前請確認該 diff 已納入正式 commit | A 於合併 PR 時收納該變更；merge 後若有二次 diff 需複測 |
| 播放列 overlay 遮蓋底部列表 badge 之視覺現象 | P4 | 播放控制列展開時，完整榜單最末可見行（第 6 行盧廣仲）的時長 badge 在 UI 節點合併時消失；播放列收合（重啟後）復現正常。判定為 overlay 視覺遮蔽非資料/功能缺陷 | 無需處理；如需複核，於播放列收合狀態查看 |
| 播放路徑串流限制（既有） | P2 | 模擬器出口 IP 遭 YouTube 封鎖（既有 E1），本次播放啟動成功、`PLAYING` 狀態與佇列／index 皆符合預期，惟實際音訊輸出仍屬「起播成功」層級；聲音與連續播歌需實機複測 | 已另案追蹤（E1） |
| 名次移除後 rail／完整榜單混排頁的間距 | P4 | 完整榜單移除 32dp 名次欄後每行左緣對齊縮圖，節點座標正常無重疊（縮圖中心 x=672 統一） | 建議 C 以截圖人工確認視覺平衡（截圖已附） |

### 需人工驗證項目操作指引
1. 啟動 App → 熱門榜單首頁：確認三區域 rail 縮圖左上角**無**任何數字徽章、右下角時長 badge 與「＋」正常。
2. 點「查看完整榜單」：確認每行行首**無**排序數字；縮圖、歌名、歌手、行尾「＋」排列無版面異常。
3. 點任一張卡片：確認從該曲起播、可連續下一首（實機非資料中心網路）。
4. 點任一行「＋」：確認僅開播放清單選擇 sheet，播放不被打斷。

## 8. 文件同步

- `docs/qa/smoke-checklist.md`：新增 **#17 熱門榜單無名次顯示**（rail 縮圖左上角與完整榜單行首無名次；時長 badge／「＋」保留；點卡片起播與＋開 sheet 行為不變）常態回歸項目。
- `docs/qa/reports/screenshots/`：本次新增 5 張截圖（`home_hotchart_no_rank`、`full_chart_no_rank`、`full_chart_no_rank_clean`、`fullchart_item_click_plays`、`fullchart_plus_sheet_no_play`）。
- 未改動：`docs/ARCHITECTURE.md`、`docs/CHANGELOG.md`（A 收尾領域，本次未觸碰）。
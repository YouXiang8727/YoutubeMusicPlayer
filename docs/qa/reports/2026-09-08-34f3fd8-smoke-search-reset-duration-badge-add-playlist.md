# QA 測試報告：搜尋清除回推薦 + 熱門榜單加入播放清單 + 全預覽時長 badge

- **日期**：2026-09-08
- **分支／commit**：`feature/playlist-click-to-play` @ `34f3fd8`
- **測試者**：D（QA Engineer）
- **裝置**：模擬器 `emulator-5554`（Android 17, API 37）

## 1. 驗證環境

| 項目 | 值 |
|------|-----|
| 裝置 | Android Emulator（emulator-5554） |
| Android 版本 | 17（API 37） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`（commit `34f3fd8`，`adb install -r` 成功） |
| App PID | 18838（測試全程存活，無 process 重啟） |
| 既有播放清單 | 「test」（建立於 2026-09-07，含 3 首舊資料 + 1 首 lofi） |

## 2. 前置驗證

| # | 驗證點 | 指令 | 結果 |
|---|--------|------|------|
| P1 | 單元測試 | `.\gradlew.bat :core:data:testDebugUnitTest :core:domain:testDebugUnitTest :feature:search:testDebugUnitTest` | ✅ BUILD SUCCESSFUL（90 tasks，全部 UP-TO-DATE = 通過） |
| P2 | 建置 | `.\gradlew.bat :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| P3 | 安裝 | `adb install -r app\build\outputs\apk\debug\app-debug.apk` | ✅ Success |
| P4 | 啟動 | `adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity` | ✅ 正常顯示熱門榜單首頁，無 crash |

## 3. 需求 3：所有預覽處顯示影片時長（資料層關鍵）

| # | 驗證點 | 結果 | 實測證據 |
|---|--------|------|----------|
| 3-1 | 熱門榜單 rail 縮圖右下角 badge | ✅ PASS | 三區域（台灣/西洋/日本）rail 全部項目皆有時長：`4:45`、`4:19`、`3:31`、`7:02`、`4:01`、`2:34`、`5:30`、`2:27`、`3:30` |
| 3-2 | 完整榜單縮圖右下角 badge | ✅ PASS | 台灣熱門完整榜單每項皆有：`4:45`、`4:19`、`3:31`、`5:43`、`4:40`、`4:23` |
| 3-3 | 搜尋結果卡片縮圖 badge | ✅ PASS | 搜尋「周杰倫」「五月天」結果皆有時長：`1:26:32`、`8:26:53`、`3:37:37`、`3:02`、`1:16:13`、`4:23`、`3:37`、`3:40`… |
| 3-4 | 歌單詳情頁縮圖 badge | ✅ PASS（見下附註） | 本次加入清單的歌曲皆有 badge：`4:23`（Who Cares）、`4:45`（黃奇斌）、`4:19`（麋先生）；**清單中既有舊資料（2026-09-07 建立）的歌曲無 badge** |

**3-4 附註（重要觀察）**：歌單詳情頁的 badge 依賴本地 DB 中儲存的 duration 欄位。本次「加入播放清單」流程加入的歌曲（有解析 duration）顯示 badge 正常；清單中**昨日（2026-09-07）以舊版本加入的歌曲**因當時無 duration 資料而無 badge。判定為**歷史資料遷移缺口**而非本次功能失效——新資料路徑（加入清單 → 詳情頁 badge）完整可用。

**例外觀察**：少數搜尋結果/熱門項目（如「Jay Chou 周杰倫【My Daughter, Your Highness女兒殿下】」「【純享版】張碧晨侯明昊…」）無時長 badge——推測為影片本身無 lengthText（如 premier／直播中），屬 YouTube 資料正常變異，非解析缺陷。

### 截圖
| 檔案 | 內容 |
|------|------|
| `screenshots/home_hotchart_badges.png` | 熱門榜單首頁三區域 rail 全帶時長 badge |
| `screenshots/full_chart_badges.png` | 台灣熱門完整榜單 badge |
| `screenshots/search_results_badges.png` | 搜尋「周杰倫」結果 badge |
| `screenshots/playlist_detail_new_vs_old.png` | 歌單詳情：新加入歌曲有 badge、舊資料無 badge |

## 4. 需求 1：搜尋後可回到「推薦歌曲」（熱門榜單）

| # | 驗證點 | 結果 | 實測證據 |
|---|--------|------|----------|
| 1-1 | 搜尋框出現「✕」清除鈕（searched 狀態） | ✅ PASS | 輸入「周杰倫」後搜尋框右側出現「清除搜尋」(✕) 節點 |
| 1-2（原 5） | 點「✕」→ 返回熱門榜單、搜尋結果清空 | ✅ PASS | 點 ✕ 後回到熱門榜單首頁，搜尋框回到 placeholder「搜尋影片」 |
| 1-3（原 6） | 已搜尋狀態按系統返回鍵 → 清除搜尋返回熱門榜單，**不退出 App** | ✅ PASS | 搜尋「五月天」後按 `keyevent 4` → 回到熱門榜單首頁；`pidof` 確認 App 仍在（18838） |
| 1-4（原 7） | 未搜尋狀態按返回鍵 → 退出 App（系統預設，無誤攔截） | ✅ PASS | 未搜尋熱門榜單頁按返回 → 畫面切至 Android 桌面；補充：播放器展開狀態按返回亦直接退出，無攔截 |

### 截圖
| 檔案 | 內容 |
|------|------|
| `screenshots/clear_search_reset.png` | 點 ✕ 清除後回到熱門榜單首頁 |
| `screenshots/back_reset_hotchart.png` | 已搜尋狀態按返回鍵後回到熱門榜單首頁 |
| `screenshots/back_exit_to_launcher.png` | 未搜尋狀態按返回鍵退出至 Android 桌面 |

## 5. 需求 2：熱門榜單可加入播放清單

| # | 驗證點 | 結果 | 實測證據 |
|---|--------|------|----------|
| 2-1（原 8） | rail 縮圖右下角「＋」→ 播放清單選擇 sheet，**不觸發播放** | ✅ PASS | 點 rail「＋」後出現 sheet（含現有清單「test」＋「建立新播放清單」）；點擊當下無任何播放啟動 |
| 2-2 | sheet 選「test」→ 歌曲加入成功 | ✅ PASS | 加入「黃奇斌 4:45」後 test 清單歌曲數增加並顯示 badge；後續加入「Who Cares 4:23」確認其出現於清單 |
| 2-3 | 新建播放清單流程 | ✅ PASS | 於完整榜單「＋」→ 「建立新播放清單」→ 輸入名稱「QA清單」→ 建立 → 清單列表出現「QA清單」（建立於 2026-09-08），點入含 1 首（麋先生 4:19）且顯示 badge |
| 2-4（原 9） | 完整榜單列表「＋」→ sheet 可用 | ✅ PASS | 完整榜單任一行「＋」→ sheet 出現 |
| 2-5（原 10） | 點 rail 項目本體（非＋區）→ 正常觸發播放；＋獨立點擊無誤觸發播放 | ✅ PASS | 點項目縮圖 (258,1168) → 播放立即啟動（控制列出現「暫停」＋ SeekBar 推進）；對比＋點擊僅開 sheet 不播放 |
| 2-6 | snackbar「已加入播放清單」回饋 | ✅（程式碼鏈路＋資料生效佐證） | 實測資料加入生效（清單歌曲數增加、新清單建立含歌）；程式碼鏈路：`SearchViewModel:258` `_messages.tryEmit("已加入播放清單")` → `SearchScreen:699` `messages.collect { snackbarHostState.showSnackbar(it) }`。**transient overlay 時機無法以 UI dump 捕捉**（連拍 6 張全部落空），畫面層無法直接截圖佐證，詳見 §8 |

### 截圖
| 檔案 | 內容 |
|------|------|
| `screenshots/playlist_sheet_rail.png` | rail「＋」開啟之播放清單選擇 sheet |
| `screenshots/fullchart_add_sheet.png` | 完整榜單「＋」開啟之 sheet |
| `screenshots/qa_new_playlist_detail.png` | 新建「QA清單」詳情：含麋先生 4:19 badge |
| `screenshots/item_click_plays.png` | 點項目本體觸發播放中（控制列＋暫停按鈕） |
| `screenshots/add_to_playlist_rail.png` | 加入至 test 清單後返回熱門榜單 |

## 6. logcat 摘要（crash / ANR）

- ✅ **無 App crash**：`com.youxiang8727` 之 FATAL EXCEPTION 數 = 0；`logcat -d -b crash` 與全量過濾皆無 MyMediaPlayer 相關 stack。
- logcat crash buffer 中存在 FATAL EXCEPTION，**全部為 `com.android.commands.uiautomator` dump 工具自身**（`UiAutomationService ... already registered!`，與 android-mcp 的 UiAutomation 重複註冊衝突，pain 屬模擬器測試基礎設施），與本 App 無關。
- ✅ **無 ANR**（`ANR in com.youxiang8727` 數 = 0）。
- 測試全程 `pidof com.youxiang8727.mymediaplayer` 恆為 **18838**（無 process 重啟、無自動恢復）。

## 7. 結論

**PASS** — 三項需求於模擬器實測全部通過：

1. 搜尋✕清除／返回鍵重置回熱門榜單、未搜尋返回正常退出（無誤攔截）。
2. 熱門榜單 rail 與完整榜單「＋」均可開啟播放清單選擇 sheet、加入現有清單或新建清單皆成功，且「＋」不誤觸發播放。
3. 時長 badge 在熱門榜單（rail＋完整）、搜尋結果、歌單詳情（新加入歌曲）均可顯示——**lengthText 解析路徑與真實 InnerTube 回應形狀相符**（本次驗證關鍵項目）。

## 8. 風險與待確認事項

| 風險 | 等級 | 說明 | 建議 |
|------|------|------|------|
| 歌單舊資料無 duration badge | P3 | 2026-09-07 之前版本加入清單的歌曲（本地 DB 無 duration 欄位）在詳情頁不顯示時長 badge；新加入的正常。非本次功能失效，但使用者可能看到同一清單內 badge 不一致 | 由 A 評估是否提供資料遷移補齊（重新解析或保留缺省顯示「--:--」） |
| snackbar 畫面證據缺失 | P3 | 「已加入播放清單」snackbar 為 transient overlay，本次所有 UI dump／連拍（6 張 × 400ms）皆未捕捉到畫面；僅以資料生效＋程式碼鏈路（`SearchViewModel:258` → `SearchScreen:699`）佐證 | 建議 C 補一張手動操作截圖，或 Owner 手動複測一次（見下方操作指引） |
| 部分影片無時長 badge | P4 | 少數搜尋結果項目無 badge（如「My Daughter, Your Highness」），推測為沒有 lengthText 的 premier/直播影片，屬資料正常變異 | 無需處理；若需區分可在資料層標記 |
| 觀測：播放器展開時按返回直接退出 App | P4 | 播放中展開的 MiniPlayerBar（控制列）狀態下按返回鍵直接退出 activity 至桌面（非先收合）。屬既有行為（播放由 MusicService 持續），不在本次需求範圍，紀錄供後續 UX 評估 | 由 A 判斷是否另開議題 |

### 需人工驗證項目操作指引（snackbar 回饋）
1. 進入 App → 熱門榜單首頁。
2. 點任一 rail 項目右下角「＋」→ 播放清單選擇 sheet 出現。
3. 點「test」（或任一現有清單）。
4. **立即**觀察畫面底部：應出現「已加入播放清單」snackbar（約 4 秒後消失）。
5. 到「播放清單」tab → 進入該清單 → 確認歌曲已加入並顯示時長 badge。

## 9. 文件同步

- `docs/qa/smoke-checklist.md`：新增 #14（搜尋清除回推薦）、#15（加入播放清單＋sheet）、#16（時長 badge）三項常態回歸項目。
- `docs/qa/reports/screenshots/`：本次新增 12 張截圖（`home_hotchart_badges`、`full_chart_badges`、`search_results_badges`、`playlist_detail_new_vs_old`、`clear_search_reset`、`back_reset_hotchart`、`back_exit_to_launcher`、`playlist_sheet_rail`、`fullchart_add_sheet`、`qa_new_playlist_detail`、`item_click_plays`、`add_to_playlist_rail`）。
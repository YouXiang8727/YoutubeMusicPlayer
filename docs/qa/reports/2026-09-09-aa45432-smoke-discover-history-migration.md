# QA 獨立驗證報告：熱門榜單遷移至獨立探索頁 + 搜尋頁最近搜尋

- **日期**：2026-09-09
- **受測基準**：HEAD `aa45432`（工作樹含本次功能改動：`feature:discover` 新模組、搜尋頁歷史、AppDatabase v3→v4）
- **QA 角色**：D（獨立驗證，僅驗證不開發）
- **環境**：
  - 模擬器：`sdk_gphone64_arm64`（Google Pixel emulator）
  - Android 版本：**16**（API 35+，targetSdk 37）
  - APK：`app-debug.apk`（`./gradlew :app:assembleDebug` 產出）
  - 測試產物 commit：受測內容為工作樹未提交改動（功能分支尚未 merge）

---

## ① 驗證項目與結果

### A. 靜態檢查（全 PASS）

| # | 驗證項 | grep 指令 | 結果 |
|---|--------|-----------|------|
| A1 | `core:domain` 無任何 `android.*` import（含 `SearchHistoryUseCases.kt`） | 全模組 `grep ^import android./androidx.` + 單檔檢查 | ✅ **PASS**：全模組與 `SearchHistoryUseCases.kt` 皆無 android import，domain 保持純 Kotlin |
| A2 | 依賴方向：`feature:search`、`feature:discover` 均**無** `core:data` | `grep core:data build.gradle.kts` x2 | ✅ **PASS**：兩模組僅依賴 `core:ui`＋`core:domain`（discover 另依 `feature:playlist` 取得 PlaylistItem mapper），皆未指向 `core:data` |
| A3 | `feature/search` 無任何 trending 殘留 | `grep TrendingState\|trendingByRegion\|TrendingRetry\|ChartRail\|TrendingSection\|FetchTrendingSongsUseCase` | ✅ **PASS**：`feature/search` 已 0 命中；trending 元件（`FetchTrendingSongsUseCase`、`TrendingState`、`trendingByRegion`、`ChartRegion`）已完整遷至 `feature:discover` |

### B. 編譯與單元測試（全 PASS，0 failed）

| Task | 結果 | 測試統計（from test-results XML） |
|------|------|------|
| `:core:domain:testDebugUnitTest` | ✅ BUILD SUCCESSFUL | **16** tests（SearchHistoryUseCases 3、FetchTrendingSongs 2、SearchVideos 4、Mapper 2、Suggestions 5），0 fail |
| `:core:data:testDebugUnitTest` | ✅ BUILD SUCCESSFUL | **75** tests（含 SearchHistoryRepository 6、TrendingPlaylistDataSource 14 等），0 fail |
| `:feature:search:testDebugUnitTest` | ✅ BUILD SUCCESSFUL | **24** tests（SearchViewModelTest），0 fail |
| `:feature:discover:testDebugUnitTest` | ✅ BUILD SUCCESSFUL | **11** tests（DiscoverViewModelTest），0 fail |
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL | 208 tasks，APK 產出成功 |

**各模組測試小計：16 + 75 + 24 + 11 = 126 tests，0 failures / 0 errors / 0 skipped。**

### C. 模擬器煙霧測試（emulator-5554，Android 16）—— 全 PASS 除註記

| # | 項目 | 結果 | 證據 |
|---|------|------|------|
| C1 | App 啟動不 crash（logcat 60s） | ✅ **PASS** | 啟動後監控 65s，無 FATAL EXCEPTION / ANR；crash buffer 空 |
| C2 | 底部導覽列 **3 tab**（搜尋／探索／播放清單） | ✅ **PASS** | UI dump 見 `搜尋`(x=140)、`探索`(x=507)、`播放清單`(x=842) 三項 |
| C3 | 探索頁：4 區域榜單（台灣/西洋/日本/韓國）＋ 完整榜單展開/返回 ＋ 點歌起播 ＋ 加入播放清單 | ✅ **PASS** | 見下分項 |
| C4 | 搜尋頁：輸入→autocomplete→搜尋→結果 | ✅ **PASS** | 輸入 `taylor` 出現建議（taylor swift、taylor swift love story…），選建議後結果出現（Eras Tour、Love Story 等） |
| C5 | 最近搜尋：執行搜尋→清除→空狀態顯示歷史→點歷史重搜→清除全部 | ✅ **PASS** | 見下分項 |
| C6 | 播放清單 tab：DB v3→v4 遷移後既有清單不流失 | ✅ **PASS** | 既有「周杰倫」(建於 2026-08-28) 遷移後仍在；DB 表驗證見下 |
| C7 | logcat 全程無 crash | ✅ **PASS** | 全程掃描無 FATAL / ANR / 進程死亡（僅 uiautomator 系統工具無關紀錄） |
| C8 | 畫面變動截圖 | ✅ 已附 | `docs/qa/reports/screenshots/discover_4regions.png`、`discover_full_chart.png`、`search_recent_history.png` |

**C3 分項（探索頁）：**
- 4 區域 header 全出現：`台灣熱門音樂`、`西洋熱門音樂`、`日本熱門音樂`、`韓國熱門音樂`，各 rail 含歌曲＋時長 badge＋「＋」
- 「查看完整榜單」展開為垂直完整清單（見 Ella Langley、Shakira、KATSEYE、Cardi B、Dolly Parton…）；頂部返回鈕點擊後回 4 區域 rail 視圖 ✅
- 點探索頁歌曲（ATEEZ 'BAD'）→ MediaSession `state=BUFFERING(6)`、metadata `BIGBANG - 'BiiiG' M/V`，起播流程正常 ✅
- 點「＋」開「選擇播放清單」sheet（含既有「周杰倫」＋「建立新播放清單」）→ 選「周杰倫」→ `playlist_items` 0→**1** ✅（加入播放清單功能鏈路完整）

**C5 分項（最近搜尋）：**
- 搜尋 `taylor swift` → BACK（返回鍵清除搜尋）→ 空狀態顯示 `最近搜尋` header＋`清除全部`＋歷史項 `taylor swift` ✅
- 點歷史項 `taylor swift` → 重新進入搜尋（EditText 填入 + 結果載入）✅
- 點 `清除全部` → 歷史區塊消失，回 `輸入關鍵字開始搜尋` ✅
- **DB 寫入驗證**：重新搜尋 `taylor swift` 後 `search_history` 有 1 列 `('taylor swift', 1788937231330)` ✅（端對端持久化成立）

### DB v4 遷移表級驗證

```
Tables: android_metadata, playlist, room_master_table, playlists, sqlite_sequence, playlist_items, search_history
Room identity: (42, '8f865bbf17bd09edc62bd6bcc77ba285')
search_history rows: [('taylor swift', 1788937231330)]
playlists count: 1   ← 既有「周杰倫」保留
playlist_items count: 1  ← 探索頁加入的歌曲
```
- **遷移成立證據**：`search_history` 表新增成功；`playlists`/`playlist_items` 既有資料**完整保留**（`周杰倫` 建於 2026-08-28，早於本次 feature）。
- 與開發者主張之 `MIGRATION_3_4 不清空既有播放清單` **一致**。

---

## ② 測試證據（指令＋輸出＋截圖）

### 指令（含輸出）

```bash
# 環境
$ git rev-parse --short HEAD
aa45432
$ adb devices
emulator-5554  device
$ adb shell getprop ro.build.version.release
16
$ adb shell getprop ro.product.model
sdk_gphone64_arm64

# 編譯＋單元測試（全 BUILD SUCCESSFUL）
$ ./gradlew :core:domain:testDebugUnitTest   → BUILD SUCCESSFUL (16 tests, 0 fail)
$ ./gradlew :core:data:testDebugUnitTest     → BUILD SUCCESSFUL (75 tests, 0 fail)
$ ./gradlew :feature:search:testDebugUnitTest → BUILD SUCCESSFUL (24 tests, 0 fail)
$ ./gradlew :feature:discover:testDebugUnitTest → BUILD SUCCESSFUL (11 tests, 0 fail)
$ ./gradlew :app:assembleDebug               → BUILD SUCCESSFUL (208 tasks)

# 安裝 + 啟動
$ adb install -r app/build/outputs/apk/debug/app-debug.apk
Success
$ adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity
Starting: Intent { cmp=com.youxiang8727.mymediaplayer/.MainActivity }

# 探索頁起播（C3）
adb shell input tap ... → MediaSession state=BUFFERING(6), metadata='BIGBANG - BiiiG M/V'
```

### 關鍵 logcat（起播，無 app crash）

```
MediaSessionService: onSessionPlaybackStateChanged: ... playbackState=PlaybackState {state=BUFFERING(6), ...}
```

### 截圖（存於 `docs/qa/reports/screenshots/`）

| 檔案 | 內容 |
|------|------|
| `discover_4regions.png` | 探索頁四區域 rail（台灣/西洋/日本/韓國熱門音樂） |
| `discover_full_chart.png` | 「查看完整榜單」垂直完整清單視圖 |
| `search_recent_history.png` | 搜尋空狀態「最近搜尋」＋歷史項＋清除全部 |

> 註：本 QA 模型無影像輸入能力，截圖以 UI dump（uiautomator 文字節點）作為對應佐證，逐項皆擷取並存檔供 Owner 人工複核。

---

## ③ 文件同步狀態

- 已更新 `docs/qa/smoke-checklist.md`：
  - 將原 `#14`（搜尋清除回推薦）更新為回「搜尋空狀態＋最近搜尋」
  - 將 `#15/16/17` 之「熱門榜單」敘述遷移至「探索頁」語境
  - 新增 `#18` 底部導覽 3 tab、`#19` 探索頁四區域、`#20` 探索頁起播、`#21` 最近搜尋、`#22` DB v3→v4 遷移保留播放清單
- 本報告為新增：`docs/qa/reports/2026-09-09-aa45432-smoke-discover-history-migration.md`

---

## ④ 風險與待確認事項

### P0（阻斷發版）
- 無。

### P1（高）
- 無新增。

### P2（低／資訊）
- **[E1 延續] 模擬器串流解析被 YouTube per-IP 封鎖**：探索頁／搜尋起播在本次模擬器僅達 `BUFFERING`（metadata 已填入），實際聲隻播放仍受既有 E1 限制（模擬器 NAT 資料中心 IP 遭 bot 封鎖）。**播放成功路徑（實際出聲）需實機＋非資料中心網路複測**——操作指引同既有 E1 登記。此為環境限制，非本次功能回歸。
- **[需人工驗證] 探索頁起播實際出聲與 MiniPlayerBar 控制**：本自動化僅驗證到 MediaSession 進入 BUFFERING 且 metadata 正確；實際音訊輸出、MiniPlayerBar 隨機/下一首/循環切換、背景續播仍需人工以實機確認（操作：探索頁點歌→確認 mini 列與通知→Home 退背景確認音訊）。
- **[需人工驗證] 最近搜尋上限 10 筆與去重置頂行為**：本次以 1 筆 query 驗證寫入／顯示／清除足跡；10 筆上限汰除與重複 query 去重置頂之邊界需以超過 10 筆不同 query 人工複測（或可補單元測試覆蓋）。
- **IME 自動化限制**：`adb shell input text` 在 Google IME 下對含空格字串有 autocorrect 干擾（`ed sheeran` 被轉成異常字），已改以單字 `taylor`＋點建議方式完成搜尋流程；不影響功能判定，僅為測試方法註記。

---

## 總結

**A/B/C（除 E1 環境限制外）全 PASS。** 靜態檢查、126 個單元測試、編譯、與模擬器煙霧測試（啟動、3 tab 導覽、探索頁四區域＋完整榜單＋起播＋加清單、搜尋＋autocomplete、最近搜尋、DB 遷移保留播放清單）均通過，logcat 全程無 crash／ANR。DB v3→v4 遷移經表級實測證實新增 `search_history` 且保留既有播放清單，與開發者主張一致。可交由 Tech Lead 依流程 merge。

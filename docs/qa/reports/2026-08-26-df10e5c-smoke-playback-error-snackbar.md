# 煙霧測試報告：播放失敗訊息 snackbar 複測（P1 修復驗證）

| 項目 | 值 |
|------|-----|
| 日期 | 2026-08-26 |
| 受測 commit | `df10e5c`（`fix(player): 播放失敗訊息反映至 App 內 snackbar`） |
| 前案 | `docs/qa/reports/2026-08-26-0ae95d7-smoke-direct-play-fallback.md` 問題 P1（解析失敗 UI 零回饋） |
| 測試者 | QA Engineer (D) |
| 裝置 | Android Emulator `sdk_gphone64_arm64`（arm64-v8a），emulator-5554 |
| Android 版本 | 16 |
| APK | `app-debug.apk`（`./gradlew :app:assembleDebug` @ `df10e5c`），applicationId `com.youxiang8727.mymediaplayer` |
| 網路 | 模擬器 NAT（出口 IP 遭 YouTube 全面 bot 封鎖——本輪恰好是「播放失敗」路徑的固定重現場景，見前案 E1） |

---

## 結果總覽

| # | 項目 | 結果 | 摘要 |
|---|------|------|------|
| 0 | 單元測試 `./gradlew test` | **PASS** | 27 tests / 0 failures / 0 errors；新 `PlaybackErrorDescriberTest` 5 cases 全過 |
| 0 | `./gradlew :app:assembleDebug` → 安裝啟動 | **PASS** | BUILD SUCCESSFUL，安裝啟動正常 |
| R1 | 錯誤回饋出現 | **PASS** | 點卡片後約 8 秒 snackbar 出現：「播放失敗：所有解析來源皆失敗（疑似遭 YouTube bot 封鎖…）[NewPipe…；InnerTube…；Piped: HTTP 525]」，與 logcat 聚合訊息一致 |
| R2 | one-shot 不連彈 | **PASS** | snackbar 顯示約 4 秒後消失；錯誤持續期間觀察 2 分鐘，無重複彈出 |
| R3 | 重試會再提示 | **PASS** | 再次點擊同曲 → prepare 清除舊錯誤 → 12:26:24 再次解析失敗 → snackbar 再次出現（乾淨截圖，開頭「播放失敗：」完整可讀） |
| R4 | crash／ANR 監控 | **PASS** | `FATAL EXCEPTION` 0 筆、`ANR in` 0 筆、crash buffer 空；app process（pid 9749）全程存活未重啟 |

**結論：前案 P1（解析失敗 UI 零回饋）已修復並通過獨立複測，R1–R4 全數 PASS。P1 可結案。**
串流解析本身仍受模擬器 IP 封鎖限制（前案 E1），播放成功路徑仍需實機＋非資料中心網路複測，維持原「需人工」狀態不變。

---

## 逐項結果與證據

### 0. 單元測試 — PASS

```
./gradlew test
BUILD SUCCESSFUL in 9s   # :feature:player:testDebugUnitTest 等實際執行（新測試強制編譯執行，非快取）
```

逐類別結果（XML 原始輸出，`*/build/test-results/testDebugUnitTest/`）：

| 模組 | 測試類別 | tests | failures | errors |
|------|----------|-------|----------|--------|
| core:data | `FallbackStreamResolverTest` | 5 | 0 | 0 |
| core:data | `PlaylistRepositoryImplTest` | 3 | 0 | 0 |
| core:data | `InnerTubeStreamSourceTest` | 4 | 0 | 0 |
| core:data | `PipedStreamSourceTest` | 3 | 0 | 0 |
| core:domain | `SearchVideosUseCaseTest` | 1 | 0 | 0 |
| feature:player | `PlaybackErrorDescriberTest`（**本輪新增**） | **5** | **0** | **0** |
| feature:player | `PlaybackQueueBuilderTest` | 5 | 0 | 0 |
| app | `ExampleUnitTest` | 1 | 0 | 0 |
| **合計** | | **27** | **0** | **0** |

`PlaybackErrorDescriberTest` 5 cases（XML 原文）：
1. cause chain 深層的聚合中文訊息優先
2. 全部訊息皆空白時 回退到 errorCodeName
3. 無 cause 訊息時 以 errorCodeName 人類可讀化兜底
4. 最深層有效訊息會去頭尾空白
5. 空白與 null 訊息跳過 取最深的有效訊息

（前案 22 tests → 本輪 27 tests，+5 與 commit 訊息主張一致。）

### R1 錯誤回饋出現 — PASS

**步驟**：啟動 App → 搜尋欄輸入 `Jay Chou` → 點「搜尋」→ 點第一張卡片「周杰倫 Jay Chou【一路向北 All the Way North】」（卡片 bounds `[42,631][1038,843]`）→ 1 秒間距輪詢截圖 16 張。

**時序**（logcat `-v time` 原始輸出）：

```
08-26 12:22:52.428 I/ActivityManager: Background started FGS: Allowed [... intent: Intent {
  act=com.youxiang8727.mymediaplayer.action.PLAY ... cmp=.../.feature.player.service.MusicService (has extras) }]
08-26 12:23:00.058 E/ExoPlayerImplInternal: Playback error
08-26 12:23:00.058 E/ExoPlayerImplInternal:   Caused by: java.io.IOException: 解析串流失敗：所有解析來源皆失敗（疑似遭
  YouTube bot 封鎖，可稍後重試或更換網路） [NewPipe: ...LOGIN_REQUIRED: "Sign in to confirm that you're not a bot"；
  InnerTube: 所有 InnerTube client 皆失敗（IOS: playabilityStatus=LOGIN_REQUIRED...; ANDROID_VR: ...）；
  Piped: HTTP 525（實例可能過載或被封鎖）]
```

**結果**：點擊（12:22:52）後約 8 秒（錯誤 12:23:00 發生當下）snackbar 出現於畫面底部（截圖 `r1b_poll_08.png`、`r1b_poll_09.png`），內容與 logcat 聚合訊息逐字一致，含「失敗：所有解析來源皆失敗…」聚合原因。（此輪截圖左端被 Gboard 浮動工具列遮住「播放」二字，R3 補收乾淨截圖證明完整開頭。）

### R2 one-shot 不連彈 — PASS

snackbar 自 12:23:00 顯示約 4 秒後消失（`r1b_poll_13.png` 已無 snackbar）。錯誤狀態持續期間（ExoPlayer 保留 playerError 至下次 prepare）持續觀察至 12:24:59（截圖 `r2_after20s.png`），**snackbar 未重複彈出**，畫面維持乾淨。符合 commit 訊息「one-shot 顯示」主張。

### R3 重試會再提示 — PASS

**步驟**：收掉 IME（`input keyevent 4`，取得無遮擋畫面）→ 12:26:15 再次點擊**同一張**卡片「一路向北」→ 輪詢截圖 16 張。

**時序**（logcat 原始輸出）：

```
08-26 12:26:24.819 E/ExoPlayerImplInternal: Playback error
08-26 12:26:24.819 E/ExoPlayerImplInternal:   Caused by: java.io.IOException: 所有解析來源皆失敗（疑似遭
  YouTube bot 封鎖，可稍後重試或更換網路） [NewPipe: ...；InnerTube: ...；Piped: HTTP 525（實例可能過載或被封鎖）]
```

**結果**：12:26:24 再次解析失敗（點擊後約 9 秒），snackbar **再次出現**（截圖 `r3_poll_09.png`），完整可讀：

> **播放失敗：** 所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路）[NewPipe: YouTube probably temporarily blocked anonymous watch access with this IP, got error LOGIN_REQUIRED: "Sign in to confirm that you're not a bot"; InnerTube: 所有 InnerTube client 皆失敗（IOS: playabilityStatus=LOGIN_REQUIRED（登入帳戶以確認你不是機器人）; ANDROID_VR: playabilityStatus=LOGIN_REQUIRED（登入帳戶以確認你不是機器人））; Piped: HTTP 525（實例可能過載或被封鎖）]

「播放失敗：」開頭＋聚合原因完整呈現；第二輪同樣約 4 秒後自動消失（`r3_poll_13.png`）。「prepare 清除舊錯誤 → 再失敗 → 再提示」的去重重置邏輯驗證通過。

### R4 crash／ANR 監控 — PASS

```
adb logcat -d | grep -c "FATAL EXCEPTION"   → 0
adb logcat -d | grep -c "ANR in "           → 0
adb logcat -d -b crash                      → （無紀錄）
adb shell pidof com.youxiang8727.mymediaplayer → 9749（全程未重啟＝無 crash 重生）
```

（grep `AndroidRuntime` 僅命中 shell 端 uiautomator 工具自身的 RuntimeInit 啟動訊息，非 app crash。）

---

## 測試方法備註

- 首次點擊誤點搜尋輸入框（uiautomator dump 前 2 個 clickable 為輸入框與搜尋鈕，非卡片），logcat 無任何播放流程即發現並修正座標重測；該輪截圖（`r1_poll_*.png`）作廢，有效證據以 `r1b_*`／`r2_*`／`r3_*` 為準。
- `adb shell input text` 不支援 `%20` 空格編碼（會輸入字面 `%`），空格需用 `%s`。

## 「需人工」項目（延續前案 E1，非本輪新增）

播放**成功**路徑仍無法在本環境驗證（模擬器出口 IP 遭 YouTube 全面封鎖）。操作指引：
1. 實機接電腦（`adb devices` 非 emulator-），改用行動網路/家用寬頻
2. 安裝本輪 APK：`adb install -r app/build/outputs/apk/debug/app-debug.apk`（`df10e5c` 建置）
3. 搜尋 → 點卡片 → 確認 MiniPlayerBar 出現且播放；此情境下**不應**出現 snackbar
4. 若播放成功，續測前案 S2（背景續播）、S4（切歌與 TTL 快取）

## 截圖索引

| 檔案 | 內容 |
|------|------|
| `r0_launch.png` | App 啟動主畫面 |
| `r1_typed.png` / `r2_results.png` | 關鍵字輸入／搜尋結果列表 |
| `r1b_poll_08.png`、`r1b_poll_09.png` | 第一次點擊後 snackbar 出現（左端被 IME 浮條微遮） |
| `r1b_poll_13.png` | snackbar 約 4 秒後消失 |
| `r2_after20s.png` | 錯誤持續期間 2 分鐘後：無 snackbar（one-shot） |
| `r3_pre.png` | 重試前乾淨畫面（IME 已收） |
| `r3_poll_09.png` | **重試後 snackbar 再現，「播放失敗：」開頭完整可讀（本輪關鍵證據）** |
| `r3_poll_13.png` | 第二輪 snackbar 消失 |

（截圖目錄：`docs/qa/reports/screenshots/`）

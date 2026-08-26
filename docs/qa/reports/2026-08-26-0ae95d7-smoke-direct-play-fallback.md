# 煙霧測試報告：直接播放 ＋ 串流解析多層 fallback

| 項目 | 值 |
|------|-----|
| 日期 | 2026-08-26 |
| 受測 commit | `0ae95d7`（`fix(data): 串流解析遭 bot 封鎖時導入多層 fallback`，含 `0a28f44` feat(search) 直接播放） |
| 測試者 | QA Engineer (D) |
| 裝置 | Android Emulator `sdk_gphone64_arm64`（arm64-v8a） |
| Android 版本 | 16（API 36） |
| APK | `app-debug.apk`，versionName 1.0 / versionCode 1，applicationId `com.youxiang8727.mymediaplayer` |
| 網路 | 模擬器 NAT（出口 IP 為資料中心網段，遭 YouTube 全面 bot 封鎖——見 S3） |

---

## 結果總覽

| # | 項目 | 結果 | 摘要 |
|---|------|------|------|
| 0 | 單元測試 `./gradlew test` | **PASS** | 4 模組強制重跑（非快取），22 tests / 0 failures / 0 errors |
| 0 | `./gradlew :app:assembleDebug` | **PASS** | BUILD SUCCESSFUL，APK 產出並安裝成功 |
| S1 | 直接播放 | **FAIL**（部分） | ✅ 不導航播放頁；❌ MiniPlayerBar 未出現、無法播放（根因＝S3 解析全線失敗） |
| S2 | 背景續播 | **BLOCKED** | 播放本身失敗，無從驗證背景續播；需待 S3 通過後複測 |
| S3 | 串流解析 fallback | **FAIL** | 三層（NewPipe → InnerTube IOS/ANDROID_VR → Piped）依序嘗試後**全線失敗**，出現 `LOGIN_REQUIRED`；fallback 執行邏輯本身正常，根因為環境 IP 封鎖 |
| S4 | 切歌與快取 | **BLOCKED**（部分） | 連續點播 3 首：無 crash/ANR（✅）；切歌延遲、TTL 快取無法驗證（解析皆失敗） |
| S5 | crash／ANR 監控 | **PASS** | 全程 logcat 無 `FATAL EXCEPTION`、無 `ANR in`、crash buffer 無紀錄 |

**結論：不建議以此環境的結果判定功能合併失敗——S1/S3 的失敗根因是模擬器出口 IP 遭 YouTube 全面封鎖（三層來源皆回 LOGIN_REQUIRED／HTTP 525），屬已知風險（TEAM.md §7「串流解析採多層 fallback」取捨的易腐路徑）。但「解析失敗時 UI 零回饋」為真實 UX 缺陷（見問題清單 P1）。需在實機＋非資料中心網路複測 S1–S4 後方可結案。**

---

## 逐項結果與證據

### 0. 單元測試 — PASS

首次執行 `./gradlew test` 全數 `UP-TO-DATE`（快取）。為獨立核實，刪除各模組 `build/test-results` 後強制重跑：

```
./gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest \
          :core:domain:testDebugUnitTest :feature:player:testDebugUnitTest
BUILD SUCCESSFUL in 3s   # 4 個 test task 實際執行（非 UP-TO-DATE）
```

逐類別結果（XML 原始輸出）：

| 模組 | 測試類別 | tests | failures | errors |
|------|----------|-------|----------|--------|
| core:data | `FallbackStreamResolverTest` | 5 | 0 | 0 |
| core:data | `InnerTubeStreamSourceTest` | 4 | 0 | 0 |
| core:data | `PipedStreamSourceTest` | 3 | 0 | 0 |
| core:data | `PlaylistRepositoryImplTest` | 3 | 0 | 0 |
| core:domain | `SearchVideosUseCaseTest` | 1 | 0 | 0 |
| feature:player | `PlaybackQueueBuilderTest` | 5 | 0 | 0 |
| app | `ExampleUnitTest` | 1 | 0 | 0 |
| **合計** | | **22** | **0** | **0** |

- 本次 fix(data) 新增的三個 fallback 測試類別全部存在且通過 ✅
- `feature:search`、`feature:playlist`、`core:ui`、`core:common` 無測試原始碼（Gradle 顯示 NO-SOURCE）

### S1 直接播放 — FAIL（部分）

**步驟**：安裝啟動 → 搜尋欄輸入 `Jay Chou`（adb 無法輸入中文，以英文關鍵字走同一條路徑）→ 點擊「搜尋」→ 點擊結果卡片（共測 3 張：經典合輯／一路向北／I Do）。

**結果**：
- ✅ 點擊卡片後**停留搜尋結果頁**，無播放頁導航（截圖 `s6_play.png`、`s10_third.png`）——直接播放行為生效
- ✅ logcat 證實點擊觸發前景服務：`Background started FGS: Allowed ... intent: Intent { act=com.youxiang8727.mymediaplayer.action.PLAY ... cmp=.../.feature.player.service.MusicService }`
- ❌ **底部無 MiniPlayerBar**、無任何播放中的視覺/狀態變化——因串流解析失敗（見 S3），播放從未開始
- ❌ **UI 對解析失敗零回饋**：無 snackbar/toast/錯誤訊息，使用者感知為「點了沒反應」（問題 P1）

### S2 背景續播 — BLOCKED

播放無法開始（S3），背景續播與通知列媒體通知的完整驗證無法執行。

補充觀察：播放失敗後展開通知列，系統仍顯示 MediaSession 媒體控制卡（歌名「周杰倫 Jay Chou【一路向北 All the Wa...」＋ ▶ 播放鍵＋上一首/隨機/循環鈕），但 app 無自行 posting 的通知（通知列下方顯示「No notifications」；`dumpsys media_session` 顯示 session `active=true`）。截圖 `s8_notif.png`。

### S3 串流解析 fallback — FAIL

**步驟**：同 S1，點擊 3 張不同卡片，logcat 過濾 `StreamResolver|MusicService|FallbackStreamResolver|InnerTube|Piped`。

**結果**：3/3 首歌皆三層全失敗，`LOGIN_REQUIRED` 共 6 行（每首 2 行 Caused by）。**實際走的路徑：NewPipe（敗）→ InnerTube IOS（敗）→ InnerTube ANDROID_VR（敗）→ Piped（敗）**——fallback 鏈依序執行、錯誤完整彙整，機制本身運作正常，但無一層能救回。

原始 logcat（第一次失敗，11:47:43，未截斷）：

```
E ExoPlayerImplInternal: Playback error
E ExoPlayerImplInternal:   androidx.media3.exoplayer.ExoPlaybackException: Source error
E ExoPlayerImplInternal:       at androidx.media3.exoplayer.ExoPlayerImplInternal.handleIoException(ExoPlayerImplInternal.java:1021)
E ExoPlayerImplInternal:       at androidx.media3.exoplayer.ExoPlayerImplInternal.handleMessage(ExoPlayerImplInternal.java:997)
E ExoPlayerImplInternal:       at android.os.Handler.dispatchMessage(Handler.java:106)
E ExoPlayerImplInternal:       at android.os.Looper.loopOnce(Looper.java:248)
E ExoPlayerImplInternal:       at android.os.Looper.loop(Looper.java:338)
E ExoPlayerImplInternal:       at android.os.HandlerThread.run(HandlerThread.java:85)
E ExoPlayerImplInternal:   Caused by: java.io.IOException: 解析串流失敗：所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路） [NewPipe: YouTube probably temporarily blocked anonymous watch access with this IP , got error LOGIN_REQUIRED: "Sign in to confirm that you're not a bot"；InnerTube: 所有 InnerTube client 皆失敗（IOS: playabilityStatus=LOGIN_REQUIRED（登入帳戶以確認你不是機器人）; ANDROID_VR: playabilityStatus=LOGIN_REQUIRED（登入帳戶以確認你不是機器人））；Piped: HTTP 525（實例可能過載或被封鎖）]
E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService$runBlockingResolve$1.invokeSuspend(MusicService.kt:196)
E ExoPlayerImplInternal:       at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
E ExoPlayerImplInternal:       at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:101)
E ExoPlayerImplInternal:       at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:263)
E ExoPlayerImplInternal:       at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:95)
E ExoPlayerImplInternal:       at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:69)
E ExoPlayerImplInternal:       at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source:1)
E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService.runBlockingResolve(MusicService.kt:194)
E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService.resolvingDataSourceFactory$lambda$3(MusicService.kt:183)
E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService$$ExternalSyntheticLambda1.resolveDataSpec(D8$$SyntheticClass:0)
E ExoPlayerImplInternal:   Caused by: java.io.IOException: 所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路） [...同上...]
E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver$resolve$2.invokeSuspend(FallbackStreamResolver.kt:63)
```

**根因判定**：模擬器 NAT 出口 IP 為資料中心網段，遭 YouTube 對 NewPipe（WEB 系）與 InnerTube IOS/ANDROID_VR 同時要求登入驗證；Piped 公開實例回 HTTP 525。此即 TEAM.md §7 登記的易腐路徑風險具現，非本次程式碼缺陷。**但驗收標準（無 LOGIN_REQUIRED 字樣）未達成，如實記 FAIL。**

### S4 切歌與快取 — BLOCKED（部分）

- ✅ 連續點播 3 首（11:47:33 / 11:48:47 / 11:51:xx），每次皆觸發完整 fallback 解析流程，全程無 crash、無 ANR
- ❌ 切歌延遲、TTL 快取（無重複解析同曲）無法驗證——解析從未成功，無成功樣本可比對

### S5 crash／ANR 監控 — PASS

```
adb logcat -d | grep -E "FATAL EXCEPTION|ANR in |AndroidRuntime"   → （無輸出）
adb logcat -d -b crash                                             → （無紀錄）
```

---

## 發現問題清單

| # | 嚴重度 | 問題 | 證據 | 建議 |
|---|--------|------|------|------|
| P1 | **High（UX）** | 串流解析失敗時 UI 零回饋：點擊卡片後無 MiniPlayerBar、無錯誤提示，使用者感知「點了沒反應」 | `s6_play.png`（點擊後畫面無任何變化）對照 logcat 解析失敗紀錄 | 解析失敗時應顯示 snackbar/toast（錯誤訊息 app 內已組好，見 logcat 中文訊息），並考慮提供「重試」入口 |
| P2 | Low（觀察） | 播放失敗後系統媒體控制卡仍顯示歌名與 ▶ 播放鍵，可能誤導使用者以為可播放 | `s8_notif.png` | 評估解析失敗時是否釋放/隱藏 session；低優先 |
| E1 | 環境限制 | 模擬器出口 IP 遭 YouTube 全面 bot 封鎖，三層 fallback 皆無法救回 | S3 logcat | **需人工**：實機＋家用/行動網路複測 S1–S4（步驟見下） |

## 「需人工」複測指引（E1）

1. 實機接電腦，確認 `adb devices` 顯示裝置（非 emulator-）
2. 關閉 Wi-Fi 改用行動網路（或家用寬頻），避開資料中心 IP
3. 安裝同一 APK：`adb install -r app/build/outputs/apk/debug/app-debug.apk`（commit `0ae95d7` 建置）
4. 重跑 S1–S4：搜尋 → 點卡片 → 確認 MiniPlayerBar 與播放 → Home 鍵退背景確認續播與通知 → 連續點播 3 首觀察切歌
5. 收尾：`adb logcat -d | grep -E "LOGIN_REQUIRED|FallbackStreamResolver|Piped"`——若第一層 NewPipe 敗但 InnerTube 接手，記錄實際走的路徑回填本報告

## 測試期間截圖索引

| 檔案 | 內容 |
|------|------|
| `s1_launch.png` | App 啟動主畫面（搜尋頁＋底部導航） |
| `s3_results2.png` | 關鍵字 `Jay Chou` 已輸入 |
| `s4_results3.png` | 搜尋結果列表（周杰倫影片＋縮圖） |
| `s6_play.png` / `s10_third.png` | 點擊卡片後：停留搜尋頁、無 MiniPlayerBar |
| `s8_notif.png` | 通知列：系統媒體控制卡（未播放狀態）＋ No notifications |

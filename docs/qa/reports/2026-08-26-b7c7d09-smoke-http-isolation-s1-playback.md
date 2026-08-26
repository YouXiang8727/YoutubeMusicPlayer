# 煙霧測試報告：HTTP client 隔離修正後播放端到端驗證

| 項目 | 內容 |
|------|------|
| 日期 | 2026-08-26 |
| 受測 commit | `b7c7d09`（fix(data): 隔離搜尋/串流 HTTP client，杜絕瀏覽器 header 汙染解析請求） |
| 裝置 | Emulator `emulator-5554`（`sdk_gphone64_arm64`） |
| Android 版本 | 16 |
| APK | `app-debug.apk`（`./gradlew :app:assembleDebug`，2026-08-26 13:19） |
| 執行者 | QA Engineer（D） |

## 總結

| 項目 | 結果 |
|------|------|
| 單元測試（`./gradlew test`） | ✅ PASS（27 tests / 0 failures / 0 errors，8 個 test-results XML） |
| 建置（`:app:assembleDebug`） | ✅ PASS |
| S1 直接播放（**關鍵**） | ❌ **FAIL**——三層 fallback 全數失敗（LOGIN_REQUIRED / HTTP 403），無 MiniPlayerBar、無音訊 |
| S2 背景續播 | ⛔ BLOCKED（依賴 S1 播放成功，無法執行） |
| S3 解析路徑記錄（成功路徑） | ⛔ BLOCKED；已記錄**失敗路徑**：NewPipe → InnerTube IOS → ANDROID_VR → Piped 全試、全敗 |
| S4 切歌與 TTL 快取 | ⛔ BLOCKED（依賴 S1） |
| S5 搜尋回歸 | ✅ PASS（"lofi hip hop" 回傳 7+ 筆結果、縮圖正常、可捲動） |
| S6 crash / ANR | ✅ PASS（crash buffer 空；無 `FATAL EXCEPTION`、無 `ANR in`；App 進程全程存活 pid 11939） |
| 清單 #12 snackbar（失敗情境回歸） | ✅ PASS（snackbar 顯示聚合原因；重試後再次出現，行為與 `df10e5c` 修復一致） |

**結論：S1 FAIL。** HTTP client 隔離修正在程式碼層面已落地（見「診斷發現」§3），但端到端播放仍被 **IP 層級的 YouTube bot 封鎖**擋下，與前兩輪（`0ae95d7`、`df10e5c`）觀察到的 E1 現象**完全一致**。Tech Lead「本機 curl 以 IOS UA 可取得 plain URL → 模擬器播放應成功」的主張**與本次實測矛盾**，依規範以 QA 實測證據為準，升級 Owner 裁決。

---

## 1. S1 直接播放——FAIL

### 重現步驟
1. `adb install -r app-debug.apk` → `adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity`
2. 搜尋框輸入 `lofi hip hop` → 點「搜尋」
3. 點擊第一張結果卡片「Best of lofi hip hop 2021 ✨ [beats to relax/study to]」（Lofi Girl）
4. 等待 12 秒，截圖 + `adb logcat -d`

### 預期 vs 實際
| | 預期 | 實際 |
|---|------|------|
| snackbar | 不出現 | **出現**：「播放失敗：所有解析來源皆失敗（疑似遭 YouTube bot 封鎖…）」 |
| MiniPlayerBar | 底部出現 | **無** |
| logcat | player state → READY / isPlaying=true | **`ExoPlaybackException: Source error`**（完整聚合訊息見下） |
| 截圖 | 播放中迷你列 | `06-s1-tap-card.png`（snackbar + 無迷你列）、`07-s1-retry.png`（重試同樣失敗） |

### logcat 原始片段（未截斷的聚合訊息）

```
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:   androidx.media3.exoplayer.ExoPlaybackException: Source error
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:   Caused by: java.io.IOException: 解析串流失敗：所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路） [NewPipe: YouTube probably temporarily blocked anonymous watch access with this IP , got error LOGIN_REQUIRED: "Sign in to confirm that you're not a bot"；InnerTube: 所有 InnerTube client 皆失敗（IOS: playabilityStatus=LOGIN_REQUIRED（登入帳戶以確認你不是機器人）; ANDROID_VR: playabilityStatus=LOGIN_REQUIRED（登入帳戶以確認你不是機器人））；Piped: HTTP 403（實例可能過載或被封鎖）]
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService$runBlockingResolve$1.invokeSuspend(MusicService.kt:196)
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService.runBlockingResolve(MusicService.kt:194)
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.feature.player.service.MusicService.resolvingDataSourceFactory$lambda$3(MusicService.kt:183)
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:   Caused by: java.io.IOException: 所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路） [NewPipe: ...同上...]
08-26 13:25:37.718 11939 12002 E ExoPlayerImplInternal:       at com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver$resolve$2.invokeSuspend(FallbackStreamResolver.kt:63)
08-26 13:25:37.756   689  1797 D MediaSessionService: onSessionPlaybackStateChanged: ... state=ERROR(7) ... error=Source error
```

重試（13:26:33，第二次點擊同曲）：**完全相同的錯誤**，`FallbackStreamResolver.kt:63` 再次拋出，snackbar 再次顯示。

## 2. S5 搜尋回歸——PASS

- 關鍵字 `lofi hip hop` → 回傳 7+ 筆（Lofi Girl / LoFi Tokyo / Settle / chilli music 等），縮圖載入正常、列表可捲動。
- 證據：截圖 `05-search-results.png`。
- 結論：Retrofit 搜尋路徑走 browser profile，隔離修正對搜尋**零影響**，符合預期。

## 3. 診斷發現（QA 診斷，非程式碼變更）

### 3.1 隔離修正確實已落地（程式碼層面）
- `core/data/.../di/HttpProfileQualifiers.kt`：定義 `BrowserProfile`（僅供 Retrofit 搜尋用）。
- `core/data/.../remote/NetworkModule.kt`：僅 browser profile 掛 `YoutubeHeaderInterceptor`。
- `core/data/.../remote/stream/InnerTubeStreamSource.kt:58`：串流請求使用 `profile.userAgent`（IOS client UA）。
- `InnerTubeStreamSourceTest.kt:55`：單元測試斷言 UA 含 `com.google.ios.youtube`，隨 `./gradlew test` 全綠。
- **排除「修正未生效／UA 仍被汙染」的假設**：logcat 錯誤中 InnerTube IOS 回的是 `playabilityStatus=LOGIN_REQUIRED`（HTTP 層有回應、JSON 業務層拒絕），若是 UA 汙染通常直接被 WAF 擋 HTML/403，型態不符。

### 3.2 出口 IP 即封鎖根因（E1 本質未變）
- 主機出口 IP：`139.162.98.189`（`curl -s https://api.ipify.org`）→ **Linode 資料中心網段**（RIPE ERX 139.162.0.0/16，NL）。
- 主機無系統代理（`scutil --proxy`：HTTP/HTTPS/SOCKS 全部 Enable=0）→ 模擬器 NAT 流量經主機出去，**模擬器出口 = 139.162.98.189**。
- NewPipe 錯誤原文即指名道姓：*"blocked anonymous watch access **with this IP**"*。
- QA 於同出口重現 InnerTube IOS client 直打（`curl` POST `/youtubei/v1/player`，IOS UA）亦未取得播放資料（HTTP 400 FAILED_PRECONDITION；註：參數構造未必與 A 的驗證指令一致，僅作方向性佐證）。

### 3.3 與 Tech Lead 主張的矛盾點
A 宣稱「從本機網路出口以 IOS client 正確 UA 可取得 plain URL（status OK）」。QA 於 2026-08-26 13:19–13:27 實測：**同一出口、同 commit、App 內三層 fallback 全滅**。可能解釋：(a) A 的 curl 請求構造與 App 內實際請求不同（endpoint／參數／header 差異）；(b) 驗證時間點 YouTube 風控狀態不同；(c) A 的驗證判讀有誤（如僅看 HTTP 200 未看 `playabilityStatus`）。依 TEAM.md 規範，**以 QA 實測證據為準**，請 Owner 裁決。

### 3.4 對第 3 圈修正迴圈的方向性建議（供 A 參考）
證據指向 **per-IP 封鎖**，與 UA 無關。若第 3 圈仍朝「換 client／改 header」方向修正，預期無效。可行方向需 Owner 裁決：
1. **更換網路出口驗證**（實機 + 住宅網路）——成本最低，可先確認修正本身在正常環境有效；
2. 主機掛住宅代理並讓模擬器流量走代理——可讓模擬器環境重現「正常出口」；
3. 接受 E1 長期開放，播放成功路徑改為**實機驗收**標準程序（模擬器僅驗證失敗路徑 UX）。

## 4. S6 crash / ANR——PASS

- `adb logcat -d -b crash`：**0 行**（crash buffer 空）。
- `adb logcat -d | grep -cE "FATAL EXCEPTION|ANR in "`：**0**。
- App 進程全程存活：pid 11939（啟動 → 兩次播放失敗後仍在）。

## 5. BLOCKED 項目說明

| 項目 | 阻塞原因 | 解除條件 |
|------|----------|----------|
| S2 背景續播 | 無任何成功播放，無從驗證背景音訊與通知 | S1 通過（正常出口環境）後補測 |
| S3 成功路徑解析層命中 | 播放未成功，無法觀察命中層；本次僅能記錄失敗路徑（三層全試全敗） | 同上 |
| S4 切歌 / TTL 快取 | 同上（且失敗情境下無法區分「跳過重新解析」與「解析再失敗」） | 同上 |

## 6. 需人工驗證清單

以下項目本次無法執行（S1 FAIL 連帶），待正常網路環境補測時由人工確認：
1. **音訊實際輸出**（S1/S2）：播放中接耳機／喇叭確認有聲音。操作：點結果卡片 → 確認 MiniPlayerBar 出現且進度前進 → Home 鍵退背景 → 確認音訊持續。
2. **通知列媒體控制**（S2）：下拉通知列，確認媒體通知含歌名＋播放／暫停／前後曲按鈕且可操作。
3. **迷你列控制與 seek**（清單 #6/#7）：依序按隨機／上一首／暫停／下一首／循環，拖曳進度條確認跳轉。

## 7. 證據檔案清單

| 檔案 | 內容 |
|------|------|
| `01-launch.png` | App 啟動主畫面 |
| `02/03/04-search-results.png` | 搜尋輸入過程（IME 座標問題排除過程） |
| `05-search-results.png` | S5 搜尋結果（PASS 證據） |
| `06-s1-tap-card.png` | **S1 FAIL**：snackbar 聚合錯誤 + 無 MiniPlayerBar |
| `07-s1-retry.png` | 重試同樣失敗，snackbar 再次出現 |
| `logcat-s1.txt` / `logcat-full.txt` | 完整 logcat（含兩次失敗 stack trace） |
| `crash-buffer.txt` | crash buffer（0 行） |

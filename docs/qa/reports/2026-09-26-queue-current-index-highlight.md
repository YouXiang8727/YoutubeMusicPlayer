# 煙霧測試報告 — 重複佇列高亮修正 + 清空佇列 scrim 修正

> **受測 commit：`6cadd26`**（`fix(player): 佇列重複曲目時改用 currentMediaItemIndex 定位高亮`）
> domain 契約 commit：`4266734`（`PlaybackSnapshot` 新增 `currentMediaItemIndex`）
> 前置：`6369f79`（Merge PR #40）、`ca23845`、`e1bb17f`（清空佇列 scrim 修正）
> 測試者：QA Engineer（D）　測試日期：2026-09-26　重試：1/3（首次因 agent step 上限中斷，續測完成）

---

## 1. 測試環境

| 項目 | 值 |
|------|-----|
| APK | `app-debug.apk`，`lastUpdateTime=2026-09-26 11:04:20` |
| APK 與原始碼對應 | APK 建置 `07:04:07`；`MiniPlayerBar.kt` 最後修改 `07:00:57`、`MediaControllerPlayerController.kt` `06:58:19` → **APK 建置時間晚於所有本次修正**，確認受測 APK 含 `6cadd26` 修正 |
| 裝置 | `sdk_gphone16k_x86_64`（Pixel 7 Pro profile），模擬器 `emulator-5554` |
| Android 版本 | **17**（`ro.build.version.release`） |
| 螢幕 / 密度 | 1344 × 2992 px，480 dpi（1 dp = 3 px） |
| package | `com.youxiang8727.mymediaplayer` 1.0 |
| App PID | 17724 |
| git worktree | 乾淨（`git status --short` 無輸出），HEAD = `6cadd26` |

---

## 2. 結果總表

| # | 情境 | 結果 |
|---|------|------|
| A | 建立重複佇列（同一 videoId 加入兩次） | ✅ PASS |
| 3–4 | 起播第 1 筆 → 展開佇列 → 高亮在第 1 筆 | ✅ PASS |
| 5–6 | 切至第 2 筆 → 高亮跟隨到第 2 筆（**核心回歸**） | ✅ PASS |
| 7 | 清空佇列 → **不殘留全螢幕灰色遮罩** | ✅ PASS |
| 8 | 移除目前播放項**之前**的項目 → 高亮仍落在同一首歌 | ⚠️ PASS（**僅驗證穩態**，見 §5 已知侷限） |
| 9 | shuffle 開啟下重複佇列高亮 | ✅ PASS |
| 11 | logcat crash／ANR 全程監控 | ✅ PASS |
| E1 | 模擬器播放可行性複測 | ✅ **E1 已失效**（見 §6） |

**總結：8/8 項通過，0 項失敗。** 其中情境 8 僅覆蓋穩態，單幀行為未驗證（見 §5）。

---

## 3. 驗證方法（本次最具重用價值的部分）

### 3.1 先讀碼取得高亮的「可量測契約」

`MiniPlayerBar.kt:341-380` 的 `QueueRow` 明確定義了目前項與非目前項的三組差異：

| 屬性 | `isCurrent == true` | `isCurrent == false` |
|------|--------------------|---------------------|
| 背景 | `MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)` | `Color.Transparent` |
| 前導 icon | `Icons.Filled.PlayArrow`，tint = `primary` | `Icons.Filled.Star`，tint = `onSurfaceVariant` |
| 標題文字色 | `colorScheme.primary` | `colorScheme.onSurface` |
| 判定來源 | `isCurrent = index == currentIndex`（`MiniPlayerBar.kt:193`） | 同左 |

有了這個契約，highlight 就從「主觀描述」變成**可客觀量測的數值**。

### 3.2 Pixel probe（取代「看起來有沒有高亮」）

腳本置於 workspace 外：`C:\Users\user\AppData\Local\Temp\opencode\probe.ps1`（+ `probes.txt` / `probes4.txt` / `probes_scrim.txt`）。

作法：對 `adb exec-out screencap -p` 產出的 PNG，在避開文字與 icon 的位置取樣矩形，統計**眾數色**（每點 2384–2520 個像素）。

```powershell
adb exec-out screencap -p > out.png   # 必須用 cmd /c，PowerShell > 會破壞 binary
powershell -File probe.ps1 -In out.png -Probes probes4.txt
```

實測基準（`SheetBg` = sheet 底色 `#ECEBF5`）：

| 量測值 | 意義 |
|--------|------|
| `#ECEBF5` | 列背景 **transparent**（非目前項）／畫面無 scrim |
| `#DCDDEA` | 列背景 = `primary@10%` 疊在 sheet 底上（目前項） |
| `#4C5E8B` | icon tint = `primary`（`PlayArrow` → 目前項） |
| `#5D5F68` | icon tint = `onSurfaceVariant`（`Star` → 非目前項） |
| `#87888E` | **有 scrim** 時的壓暗區域 |

**契約自洽性驗算**：若 `primary ≈ #4C5F87`，則 `0.1×primary + 0.9×#ECEBF5 = #DCDDEA`；反算 `(220−0.9×236)/0.1 = 76 = 0x4C`、`(221−0.9×235)/0.1 = 95 = 0x5F`、`(234−0.9×245)/0.1 = 135 = 0x87` → `#4C5F87`，與實測 icon 靜止色 `#4C5E8B` 吻合。背景與 icon 兩條獨立通道互相印证。

### 3.3 平台側獨立佐證

`adb shell dumpsys media_session` 的 **`active item id=N`** 直接來自 Media3 `Player.currentMediaItemIndex`，**與 App 自己的 UI 渲染完全無關**。用「UI 高亮位置」與「`active item id`」交叉比對，可排除單一量測管道誤判。

### 3.4 測試可重現性控制

- **shuffle**：App 啟動後 shuffle 預設為**開啟**（見 §6 現象記錄），主測試前手動關閉以確保 index 具決定性。
- **repeat**：設為**單曲循環**（`切換為清單循環`）凍結 `currentMediaItemIndex`，避免曲目結束自動前進讓探測結果漂移。

---

## 4. 逐項證據

### A. 建立重複佇列 — PASS

搜尋「周杰倫 晴天」，對第 1 筆結果（`DYptgVvkVLQ`，5:19 周杰倫 Jay Chou【晴天 Sunny Day】-Official Music Video）按「加入」→「加入佇列尾端」× 2。

```
dumpsys media_session: queueTitle=null, size=2
```

兩列 `videoId` 完全相同（`logcat SearchPaging DETAIL` 確認該筆 id = `DYptgVvkVLQ`）。

> 附註：加入佇列尾端兩次後 **自動起播**（`state=PLAYING(3)`），非預期行為但非本次修正範圍，僅記錄。

### 3–6. 高亮跟隨實際播放項（核心） — PASS

`SheetBg = #ECEBF5`（基準）。切換前後：

| 探測點 | 播第 1 筆時 | 播第 2 筆時 |
|--------|-------------|-------------|
| Row1 背景 | `#DCDDEA` ← **高亮** | `#ECEBF5`（transparent） |
| Row2 背景 | `#ECEBF5`（transparent） | `#DCDDEA` ← **高亮** |
| Row1 icon | `#4C5E8B` primary ▶ | `#5D5F68` grey ★ |
| Row2 icon | `#5D5F68` grey ★ | `#4C5E8B` primary ▶ |
| `active item id` | `0` | `1` |

**判定依據**：兩列標題文字與 `videoId` **完全相同**，高亮仍正確從第 1 列移到第 2 列，且 `active item id` 同步為 `1`。舊實作 `queue.indexOfFirst { it.videoId == snapshot.videoId }` 對此輸入**恆回傳 0**（兩列 videoId 相同），不可能產生上述結果 → 修正確實生效。

截圖：`2026-09-26-02-queue-expanded-item1-current.png`、`2026-09-26-03-queue-expanded-item2-current.png`

### 9. shuffle 開啟下重複佇列 — PASS

建立 **4 筆**全同 videoId 的佇列，shuffle = **開啟**，`active item id = 1`：

| 探測點 | 量測值 | 判定 |
|--------|--------|------|
| Row1 背景 / icon | `#ECEBF5` / `#5D5F68` ★ | 非目前項 ✅ |
| Row2 背景 / icon | **`#DCDDEA` / `#4C5E8B` ▶** | **目前項** ✅ |
| Row3 背景 / icon | `#ECEBF5` / `#5D5F68` ★ | 非目前項 ✅ |
| Row4 背景 / icon | `#ECEBF5` / `#5D5F68` ★ | 非目前項 ✅ |

四列 `videoId` 全同，僅第 2 列高亮。**shuffle 開啟下 `currentMediaItemIndex` 仍為 timeline 位置，UI 定位正確。**

截圖：`2026-09-26-04-shuffle-on-4dup-queue-current-index1.png`

### 8. 移除目前播放項之前的項目 — PASS（僅穩態）

**設計說明**：若在 `active item id = 1` 時移除 index 0，current 會退回 `0`，而舊邏輯（`indexOfFirst`）在「全同 videoId」下也會回傳 `0` —— **兩種實作結果相同，此設計無法區分修��與未修正**。故先切到**最後一列**（`active item id = 3`），再移除 index 0，使移除後 current = 2 > 0，具區辨力。

**前置基線**（4 列，`active item id=3`）：

| 探測點 | 量測值 |
|--------|--------|
| Row1–Row3 背景 | `#ECEBF5`（全 transparent） |
| **Row4 背景 / icon** | **`#DCDDEA` / `#4C5E8B` ▶** |

**移除第 1 列（index 0）之後**：

| 探測點 | 量測值 | 判定 |
|--------|--------|------|
| Row1 背景 / icon | `#ECEBF5` / `#5D5F68` ★ | 非目前項 ✅ |
| Row2 背景 / icon | `#ECEBF5` / `#5D5F68` ★ | 非目前項 ✅ |
| **Row3 背景 / icon** | **`#DCDDEA` / `#4C5E8B` ▶** | **目前項（index 2）** ✅ |
| Row4（已無此列） | `#ECEBF5` 100% | 探測點無內容 ✅ |
| `dumpsys` | `queueTitle size=3`、`active item id=2` | 平台一致 ✅ |

**判定**：佇列由 4 → 3，`active item id` 由 3 → 2，UI 高亮正確落在**新的 index 2（第 3 列）**。舊邏輯會高亮 index 0 → **未回歸**。

主觀描述：截圖中三列標題文字完全相同，灰色 ★ ★ 與藍色 ▶ 的差別在最後一列清晰可見。

截圖：`2026-09-26-05-before-remove-current-index3.png`、`2026-09-26-06-after-remove-before-current.png`

### 7. 清空佇列不殘留 scrim — PASS

按垃圾桶「清空佇列」後**立即**（同一次 snapshot 往返內，無需任何點擊畫面）觀察：

**a) accessibility tree 立即恢復**（scrim 殘留的可靠代理訊號）：sheet 節點（`播放佇列`／`清空佇列`／`存為播放清單`／各列 `移除…`）**全部消失**，搜尋結果 8 列與底部 3 tab **完整恢復**，`MiniPlayerBar` 亦已收起（清空後無播放項）。

**b) pixel probe 確認無壓暗**：

| 探測點 | 量測值 | 判定 |
|--------|--------|------|
| TopArea（搜尋欄） | `#FAF8FE` | 正常 surface，非壓暗 ✅ |
| MidArea（結果卡） | `#E1E2ED` | 正常 ✅ |
| SheetArea（原 sheet 覆蓋處） | `#E1E2ED` | 正常，**已可見底層內容** ✅ |
| NavBar | `#EDEDF6` | 正常 ✅ |

對照：scrim 存在時該區域實測為 `#87888E`（壓暗）。四個探測點皆為正常 surface 色 → **無殘留灰色遮罩**。

**c) 播放狀態**：`state=STOPPED(1), position=0`、`queueTitle size=0`（清空後佇列與播放皆已停止，語意一致）。

截圖：`2026-09-26-07-after-clear-queue-no-scrim.png`

### 11. logcat crash／ANR — PASS

```
> adb logcat -b crash -d
（輸出為空 —— 無 FATAL EXCEPTION）
```

App PID 17724 全時段紀錄（`adb logcat -d --pid=17724`）僅有下列**無害** emulator／graphics／codec 警告，無任何 App 例外或 crash：

```
W 7.mymediaplayer: Unexpected CPU variant for x86: x86_64.
W libc    : Access denied finding property "vendor.mesa.virtgpu.kumquat"
W HWUI    : Failed to choose config with EGL_SWAP_BEHAVIOR_PRESERVED, retrying without..
E ActivityThread: Failed to find provider info for androidx.car.app.connection
W SpeakerLayoutUtil: Built-in speaker's getSpeakerLayoutChannelMask not usable...
W CCodecResources: Failed to query component store for system resources: 6
W 7.mymediaplayer: AIBinder_linkToDeath is being called with a non-null cookie...
```

`ActivityThread: androidx.car.app.connection` 為 AndroidX 選擇性依賴的例行探測，非錯誤。

ANR：`dumpsys activity processes` 無 `not responding` 紀錄。

**特別檢查**：`LazyColumn` duplicate key 崩潰（`IllegalArgumentException: Key ... was already used`）在本次全程 4 筆重複佇列操作中**未出現**，確認 `key = "$index-${item.videoId}"` 修正持續有效。

> 過濾主 buffer 時出現的 `IllegalStateException`（`DisplayPowerController` 顏色溫度感測器）與 `IllegalArgumentException`（`AlarmManager` 網路定位）皆來自 **system_server（pid 690）**，與本 App 無關；`AndroidRuntime START/EXIT` 為 android-mcp 自身 UI 自動化 agent（`com.wetest.uia2`，uid 2000）。已用 `--pid` 嚴格區分。

---

## 5. 方法的已知侷限（如實記錄）

1. **靜態截圖原理上抓不到單幀錯誤。** 本報告所有 pixel probe 皆取自使用者操作完成後的**穩態畫面**。程式碼註解所述「`currentMediaItemIndex` 與 `queue` 由不同事件觸發、可能相差一幀」的競態，若只在數十毫秒內錯列，**靜態量測完全無法察覺**。因此：
   - 情境 8 僅能宣稱「**穩態正確**」，**不能**宣稱「切換過程中無瞬間錯列」。
   - 若要驗證單幀行為，需 screenrecord（高幀率錄影逐幀）或 Compose 側單元測試／ instrumentation 斷言，非本 runbook 能力範圍。
2. **探測點避開文字與 icon**，量到的是列的靜止底色與 icon 靜止色；icon **形狀**（▶ vs ★）以截圖目視佐證，未做像素圖樣比對。
3. **單一 emulator 環境**。本報告不覆蓋實機、多螢幕尺寸、字級放大、淺色／深色主題。程式碼以 `MaterialTheme.colorScheme` 取色，理論上主題無關，但**未實測**。
4. **音訊輸出未驗證**（見 §7）。

---

## 6. 附帶發現

### 6.1 已知問題 E1 已失效（重要，需更新 smoke-checklist）

`smoke-checklist.md` 登記 E1「模擬器環境無法驗證串流解析 —— 模擬器 NAT 出口 IP 遭 YouTube 全面 bot 封鎖，NewPipe／InnerTube／Piped 皆回 LOGIN_REQUIRED／HTTP 525，播放**必定失敗**」。

**本次實測兩次播放均成功**：

| 時點 | 證據 |
|------|------|
| 續測開始（沿用前次狀態） | `state=PLAYING(3), position=120906, buffered position=194258` |
| 重複佇列測試期間 | `state=PLAYING(3), position=171196, buffered position=259111`（緩衝 4 分 19 秒，**不可能是假播放**） |
| 清空佇列後重新起播 | `state=PLAYING(3), position=8815, buffered position=19086` |

`buffered position` 持續增長至數分鐘，證明**實際下載了串流資料**，非僅狀態機誤報。

**結論：E1 已失效。** 建議 Tech Lead 將條目改寫為「**間歇性，須每次實測確認**」而非直接刪除 —— 出口 IP 封鎖本就可能是動態的，刪除會讓後續測試過度樂觀。**本次 QA 未自行修改 E1 條目文字，等 Tech Lead 裁決後更新。**

附帶：本報告期間 `logcat` 未出現任何 `LOGIN_REQUIRED`／HTTP 403／525。

### 6.2 現象記錄：shuffle 預設為開啟（來源未確認）

App 啟動、MiniPlayerBar 首次出現時，shuffle 按鈕的 `contentDescription` 為「**關閉隨機播放**」，代表 shuffle **已開啟**；此時播放佇列為空、尚未有任何使用者操作。點擊後變為「開啟隨機播放」。

- **是否為預期行為：未確認。** 可能是 (a) MediaSession／ExoPlayer 狀態跨 App 重啟殘留、(b) 刻意設計的預設開啟、或 (c) 未預期的狀態初始化問題。
- **影響**：新手使用者首次播放即為隨機順序；且會讓依賴「index 具決定性」的自動化測試不穩定（本報告已手動關閉）。
- **狀態：未確認現象，本報告不判定為缺陷**，僅登記供後續追蹤。建議 Tech Lead 派工確認 `PlayerController` 初始化時 `shuffleModeEnabled` 的來源。

### 6.3 現象記錄：加入佇列尾端至空佇列會自動起播

對搜尋結果連按兩次「加入佇列尾端」（佇列原本為空）後，`state` 變為 `PLAYING(3)` 且自動播放第一筆。行為未在 `smoke-checklist.md` 登記，**未確認是否為設計意圖**，僅記錄。

---

## 7. 需人工驗證項目（本 runbook 能力範圍外）

| 項目 | 原因 | 操作指引 |
|------|------|----------|
| 音訊實際輸出 | 模擬器無可稽核的音訊軌跡 | 於模擬器／實機播放，確認揚聲器或耳機有聲；並用 `adb shell dumpsys media.audio_flinger` 或手機音量 UI 交叉確認非靜音 |
| 通知列控制與音訊（清單 #4／#8） | 需真人聽感與鎖屏操作 | 播放中按 Home → 下拉通知列，確認媒體通知顯示歌名與播放／暫停／前後曲；操作按鈕後確認聲音與狀態同步 |
| 淺色／深色主題下的高亮 | 本次僅淺色主題 | 系統切換深色模式後重複 §4「3–6」與「9」，重跑 pixel probe（淺色下 `SheetBg` 基準值會變，須重新量測基準） |
| 字級放大 / 小螢幕 | 探測點座標會失效 | 系統字級調至最大後重跑；probe 座標需重新取得 |
| 單幀競態 | 靜態量測原理上抓不到 | 見 §5.1，需 screenrecord 逐幀或 instrumentation 斷言 |
| 實機（非 emulator） | 本次僅 emulator-5554 | 於非資料中心網路之實機重跑全部情境 |

---

## 8. 截圖清單

| 檔案 | 對應情境 |
|------|----------|
| `screenshots/2026-09-26-01-collapsed-after-duplicate-add.png` | 加入重複佇列後收斂態（意外發現 shuffle 預設開啟） |
| `screenshots/2026-09-26-02-queue-expanded-item1-current.png` | 高亮在第 1 筆（`active item id=0`） |
| `screenshots/2026-09-26-03-queue-expanded-item2-current.png` | 高亮在第 2 筆（`active item id=1`）—— 核心證據 |
| `screenshots/2026-09-26-04-shuffle-on-4dup-queue-current-index1.png` | shuffle 開啟、4 筆重複、高亮在 index 1 |
| `screenshots/2026-09-26-05-before-remove-current-index3.png` | 移除前基線（`active item id=3`） |
| `screenshots/2026-09-26-06-after-remove-before-current.png` | 移除 index 0 後高亮在 index 2 —— 情境 8 證據 |
| `screenshots/2026-09-26-07-after-clear-queue-no-scrim.png` | 清空佇列後無殘留遮罩 |

---

## 9. 判定

**`6cadd26` 與 `e1bb17f` 通過本次煙霧回歸。** 核心修正（重複 `videoId` 佇列高亮）在 2 筆與 4 筆重複佇列、shuffle 開啟、移除前項三種條件下均正確定位；清空佇列的 scrim 連動修正亦通過。logcat 全程無 FATAL／ANR／duplicate key crash。

**保留事項**：情境 8 僅驗證穩態，單幀競態未驗證（§5.1）；E1 已失效但條目文字待 Tech Lead 更新（§6.1）；shuffle 預設開啟來源未確認（§6.2）。

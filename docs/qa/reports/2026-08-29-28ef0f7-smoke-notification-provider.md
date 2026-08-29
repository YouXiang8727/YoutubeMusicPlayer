# 煙霧測試報告：自訂 RemoteViews 媒體通知提供者驗證（通知 Provider＋任務移除停止＋重連）

| 項目 | 值 |
|------|-----|
| 日期 | 2026-08-29 |
| 受測 commit | `28ef0f7`（master，HEAD；`git` 二進位未安裝，由 `.git/refs/heads/master` 讀取） |
| 測試者 | QA Engineer (D) — 獨立驗證 |
| 裝置 | Android Emulator `sdk_gphone16k_x86_64`（Google），emulator-5554 |
| Android 版本 | 17（API 37），density 480（3.0x），解析度 1344x2992 |
| APK | `app-debug.apk`（`./gradlew :app:assembleDebug`），applicationId `com.youxiang8727.mymediaplayer`，uid 10230 |
| 前置狀態 | 先前報告 `804da8f` 之後，master 已快進合併通知 Provider（`PlayerMediaNotificationProvider`）功能 |
| 環境注意 | 本機 **無 git 二進位**（`git` 不在 PATH），HEAD 以讀出 `.git/refs/heads/master` = `28ef0f7518...` 為準 |

---

## ① 環境與驗證方法

- 安裝 `app-debug.apk`（`adb install -r` Success）、啟動 `am start -n com.youxiang8727.mymediaplayer/.MainActivity`。
- 播放路徑：搜尋列 `(672,600)` 輸入 `test` → 搜尋鈕 `(672,606)` → 點第一筆結果卡片 `(800,800)`。
- 觀察工具：`dumpsys media_session`（PlaybackState）、`dumpsys notification --noredact`（flags/template/contentView/contentIntent）、`logcat -d`、`uiautomator dump`。
- 截圖法（Windows 關鍵）：`adb shell screencap -p /sdcard/x.png` 後 `adb pull`（`exec-out >` 會截斷 PNG）。SystemUI 黑影為「媒體控制列」取代，見 S2/S6。
- `am task remove` 不受支援（`unknown command 'remove'`），任務移除改用 recents 上滑手勢達成。

---

## ② 逐項結果與證據

| # | 項目 | 結果 | 摘要 |
|---|------|------|------|
| S1 | 真實播放（MEDIA3 MediaSessionService） | **PASS** | 第一筆卡片後 session 達 PLAYING、position 1811→2832→126967ms 推進；通知 id=1001 `template=DecoratedMediaCustomViewStyle`、`contentView=0x7f09000f` |
| S2 | 自訂 5 按鍵 RemoteViews 布局（靜態） | **PASS（靜態）** | `notification_player.xml` 5 個 ImageButton 水平排列、widths shuffle48/prev48/play56/next48/repeat48dp＋weight-1 Space ⇒ 互不重疊、獨立 hit target；bg `#FF1E1E24`、按鈕 ripple oval。**螢幕可視驗證受 S6 限制** |
| S3 | session 式控制（carousel） | **PASS** | play/pause tap 切 PLAYING↔PAUSED；next 重置 position→0 新曲；prev 載入新曲（buffered 64690） |
| S4 | 通知 card 點擊返回 App（自訂 content intent） | **FAIL/BLOCKED（模擬器）** | 通知記錄 `contentIntent=null`；點通知主體**未返回** App（仍 launcher）。判定受 Media3 `DecoratedMediaCustomViewStyle` 覆寫 root content intent 影響，需**實機＋人工**複測，並列為可能缺陷供 A 檢查 |
| S5 | 滑掉任務（task removed）→ 停止播放＋通知消失 | **PASS（播放停止）／FAIL（通知未消失）** | 見下 |
| S6 | 隨機 icon 主題紅＋badge 切換 | **PASS（靜態）** | `ic_shuffle_active.xml` glyph #FF3B30＋紅 badge(17,3)r2.4；`ic_shuffle.xml` 白無 badge；Provider 依 `player.shuffleModeEnabled` 切（PlayerMediaNotificationProvider.kt:88-94）。**螢幕可視驗證受限** |
| S7 | 任務移除後重開 App → 播放重連 | **PASS** | 重開後 session 仍 PAUSED(凍結位)184854；tap miniplayer play → PLAYING speed=1.0，position 184854→190773→196806 推進；通知 flags 含 FGS(0x400) 恢復前台 |
| S0 | crash／ANR 監控（全程） | **PASS** | `-b crash` 空、無 `FATAL EXCEPTION`、無 `ANR in`、無 process died |

---

### S1 播放與通知產生 — PASS

`dumpsys media_session`：
```
state=PlaybackState {state=PLAYING(3), position=... , speed=1.0}
```
`dumpsys notification`：
```
key='com.youxiang8727.mymediaplayer'
  template=DecoratedMediaCustomViewStyle
  contentView=0x7f09000f   (自訂 RemoteViews)
```
logcat：`Background started FGS: Allowed ... act=androidx.media3.session.MediaSessionService`（Media3 MediaSessionService 前台啟動，合法）。

### S3 session 式控制 — PASS

透過 SystemUI carousel 控制（唯一可自動點擊的途徑）：
- play/pause `(1140,927)`：PLAYING→PAUSED ✓
- next `(1224,1095)`：position 重置→0（新曲）✓
- prev `(120,1095)`：載入新曲（buffered 64690）✓

### S5 滑掉任務 → 播放停止（PASS 部分）＋通知消失（FAIL 部分）

觸發：recents `input keyevent 187` → 快速上滑 `input swipe 672 2400 672 150 120` 移除 task #44。
logcat 直接證據：
```
08-29 03:49:49 RecentsView: onTaskRemoved: 44
```
移除後狀態：
- `dumpsys media_session`：`state=PAUSED(2), position=184854, speed=0.0`（**播放已停止，位置凍結**）⇒ 播放停止 **PASS**
- 通知 flags 由前台 `FOREGROUND_SERVICE|ONGOING_EVENT` 降為 `ONLY_ALERT_ONCE|NO_CLEAR`（service 離開前台）⇒ 退出前台 **PASS**
- 但通知 **6 秒以上仍顯示**，MusicService ServiceRecord 與 process(pid 31955) 仍在 ⇒ **通知未消失**，與「通知消失」主張**矛盾 ⇒ FAIL／待確認**

> 此與 TEAM.md §7 記錄的 Media3「有 MediaController bound 時 Service 無法真正 stop」官方限制一致：stopped＋非前台＋僅剩殘留 notification 的 service 為 Media3 已知路徑。`pauseAllPlayersAndStopSelf()` 已確實觸發（播放停止、退出前台實證），但**通知本身未自動撤下**是否為可接受行為，需 A 確認或補 ADS 撤下邏輯。

### S7 任務移除後重開 → 播放重連 — PASS

App 任務已移除 → `am start` 重新啟動（新 task #45，MainActivity focused）→ miniplayer 顯示舊曲（3:04/3:51），session 仍 PAUSED 凍結位 184854 → tap miniplayer play `(981,2452)`：
```
state=PlaybackState {state=PLAYING(3), position=190773, speed=1.0}
  （後續樣本 196806，持續推進）
```
通知 flags 恢復含 `0x400 FOREGROUND_SERVICE`（值 10230=0x27F6）。
⇒ 重連/ensureConnected 鏈有效，從 recents 重開仍可播。**PASS**

---

## ③ 未驗證／需實機項目（人工）

下列因模擬器 SystemUI「媒體控制列」取代自訂 RemoteViews，或需真實通知列行為，自動化無法完整覆蓋，**需實機＋人工**：

1. **S2 自訂 5 按鍵布局的可視與點擊反饋**：模擬器 shade 顯示 `qs_media_controls`（prev/play/next/SeekBar，無 shuffle/repeat 鈕），App 自訂 RemoteViews 未在 shade 展開。實機請下拉通知列確認 5 顆按鈕（隨機/上一首/播放暫停/下一首/循環）各自可點、獨立反饋、互不重疊。操作指引：實機播放→下拉通知列→逐顆點擊 5 鈕並觀察 App/播放狀態與按鈕反饋。
2. **S4 通知點擊返回 App**：`contentIntent=null`，實機驗證「點通知卡片→回 App」；若失效即為缺陷（見風險 1）。
3. **S6 隨機 icon 紅＋badge 可視**：實機開啟隨機後觀察通知隨機鈕 icon 變主題紅＋badge；關閉後還原白。模擬器僅能以 `ic_shuffle_active.xml`/provider 切換（static）佐證，程式碼路徑已確認。
4. **通知進度條每秒重繪**：屬自訂通知實作，需實機確認進度條時間每秒更新（AOSP RemoteViews 不自動重繪，依 TEAM.md 此為預期實作，仍建議實機目視）。

---

## ④ 風險與待確認事項

1. **[P1] 通知主體 content intent 失效（S4 FAIL 主因）**：`dumpsys notification` 顯示 `contentIntent=null`，點通知主體不回 App。很可能為 Media3 `DecoratedMediaCustomViewStyle` 覆寫/未套用自訂 root content intent。**待 A 確認**是否為 PlayerMediaNotificationProvider root content intent 設定被 Media3 覆蓋的實作缺陷；若非本次變更可接受，需在 code review 明確判定。
2. **[P1/WOS] 任務移除後通知未自動消失（S5 FAIL 部分）**：播放已停、已退前台，但通知殘留 6s+、service 未清。與 Media3 bound-controller 限制一致，但「通知消失」是本次變更宣稱的預期結果，**實測不符**。請 A 裁定：接受殘留通知（Media3 路徑，使用者滑掉後若再進 App 會復原）或需補撤下邏輯。
3. **git 二進位未安裝**：本機無法 `git rev-parse`；HEAD 由 `.git/refs/heads/master` 讀取為 `28ef0f7`。後續 QA 報告的 commit hash 需有替代取得途徑（建議 A 於環境補裝 git 或提供 hash）。
4. **S2/S6 可視驗證僅靜態**：模擬器 shade 以 SystemUI carousel 取代自訂 RemoteViews（無 shuffle/repeat 鈕），布局/icon 僅靜態佐證，最終視覺回歸需實機。

---

## 證據檔案

- 截圖（`docs/qa/reports/screenshots/`）：`2026-08-29-s2-shade2.png`、`2026-08-29-s2-shade-small.png`、`2026-08-29-s2-notifcard-crop.png`、`2026-08-29-s3-shade.png`、`2026-08-29-s5-shade.png`、`2026-08-29-s7-top.png`
- UI dump（`screenshots/`）：`nf1/nf2/nf3/nf5/nf6.xml`、`rec.xml/rec2.xml`、`s7.xml`（temp）
- logcat：`onTaskRemoved: 44`（03:49:49）、FGS start（03:41:15 / 03:51:57）、crash buffer 空

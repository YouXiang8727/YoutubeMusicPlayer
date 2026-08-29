# 煙霧測試報告：搜尋分頁「載入更多」續頁驗證（PR #9/#10/#11）

| 項目 | 值 |
|------|-----|
| 日期 | 2026-08-29 |
| 受測 commit | `804da8f`（master，搜尋分頁功能 PR #9/#10/#11 head） |
| 測試者 | QA Engineer (D) — 獨立驗證 |
| 裝置 | Android Emulator `sdk_gphone16k_x86_64`（Google），emulator-5554 |
| Android 版本 | 17 |
| APK | `app-debug.apk`（`./gradlew :app:assembleDebug` @ `804da8f`），applicationId `com.youxiang8727.mymediaplayer` |
| 搜尋關鍵字 | `test`（英文，模擬器 IP 下 YouTube 搜尋可用） |
| 環境 | 本地 master checkout = `804da8f`，工作樹乾淨；JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr` |

---

## 結果總覽

| # | 項目 | 結果 | 摘要 |
|---|------|------|------|
| 0 | `./gradlew test` | **PASS** | BUILD SUCCESSFUL，186 actionable tasks（6 executed / 180 up-to-date）— 與開發者主張一致 |
| 0 | `./gradlew :app:assembleDebug` → 安裝啟動 | **PASS** | BUILD SUCCESSFUL，`adb install -r` Success，`am start` 正常 |
| S1 | 搜尋執行 | **PASS** | 輸入 `test` → 搜尋 → Page 1 回傳 13 筆，`SUMMARY`/`DETAIL` 正常 |
| S2 | 續頁（載入更多）連做 3+ 頁 | **PASS** | 以「點擊載入更多」連續載入 page 2/3/4，`next` token 鏈逐頁前進，`APPEND dup=0` 全程乾淨 |
| S3 | crash／ANR／duplicate-key | **PASS** | `FATAL EXCEPTION` 0、`ANR in` 0、crash buffer 空、無 duplicate-key；app process（pid 27546）全程未重啟 |
| ⚠️ S4 | 捲到底部自動觸發載入更多 | **FAIL** | 純捲動到底部**不會**自動載入下一頁；必須點「載入更多」按鈕才觸發（見風險 4） |

**下結論前請先讀「風險與待確認」第 4 項**——續頁資料正確性（token 鏈、dup、無重 key crash）全部 PASS，唯一分歧點是「是否需點按載入更多鈕」的互動設計，需由 A/開發者確認此為預期（tappable button）或 bug（auto-load-on-scroll 未觸發）。

---

## 逐項結果與證據

### 0. 靜態驗證（單元測試＋建置）— PASS

```
PS> git rev-parse --short HEAD          → 804da8f
PS> git branch --show-current            → master
PS> git status --short                   → （空，工作樹乾淨）
PS> $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test
BUILD SUCCESSFUL in 4s
186 actionable tasks: 6 executed, 180 up-to-date

PS> .\gradlew.bat :app:assembleDebug
BUILD SUCCESSFUL in 4s
186 actionable tasks: 3 executed, 183 up-to-date

PS> adb install -r app\build\outputs\apk\debug\app-debug.apk
Performing Streamed Install
Success

PS> adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity
Starting: Intent { cmp=com.youxiang8727.mymediaplayer/.MainActivity }
```

186 tasks 與開發者主張一致，無回歸；APK 建置、安裝、啟動全部正常。

### S1 搜尋執行 — PASS

進入搜尋頁（EditText at `(672,414)`），輸入 `test` 後觸發搜尋（點「搜尋」鈕 `(672,606)`）。搜尋結果正常列出（每筆含「加入播放清單」按鈕），logcat：

```
08-29 01:51:13.725 27546 27697 I SearchPaging: SUMMARY q=test page=1 sent=- next=Er4CEgR0ZXN0 count=13 overlapPrior=?
08-29 01:51:13.725 27546 27697 I SearchPaging: DETAIL  q=test page=1 videos=[pEoMWVrFy6U:INHON... | _W15z6kLnTY:Masterpiece 16K HDR OLED Test | ... （共 13 筆）]
```

- Page 1 `sent=-`（首頁無前頁 token，符合預期）、`next=Er4CEgR0ZXN0`（有續頁 token）、`count=13`。
- 無 `WARN token not advanced`。

### S2 續頁（載入更多）連做 3 頁 — PASS

**操作**：捲動/拖曳到底部露出「載入更多」項目，點按 `(672,2440)` 觸發下一頁，共完成 page 1 → 2 → 3 → 4。

logcat 原始輸出（`grep SearchPaging`）：

```
08-29 01:51:13.725  SUMMARY q=test page=1 sent=- next=Er4CEgR0ZXN0 count=13 overlapPrior=?
08-29 01:53:08.554  SUMMARY q=test page=cont sent=Er4CEgR0ZXN0 next=EqADEgR0ZXN0 count=4  overlapPrior=?
08-29 01:53:08.554  DETAIL  q=test page=cont videos=[jye0i50ana0:test | IX7OlT-jKAc:Test & Recognise ... | ... （共 4 筆）]
08-29 01:53:08.556  APPEND  q=test fetched=4 appended=4 dup=0 next=EqADEgR0ZXN0
08-29 01:53:33.709  SUMMARY q=test page=cont sent=EqADEgR0ZXN0 next=Eq4DEgR0ZXN0 count=10 overlapPrior=?
08-29 01:53:33.709  DETAIL  q=test page=cont videos=[8uvJQO1koPk:Dhruv Jurel... | ... （共 10 筆）]
08-29 01:53:33.710  APPEND  q=test fetched=10 appended=10 dup=0 next=Eq4DEgR0ZXN0
08-29 01:54:43.617  SUMMARY q=test page=cont sent=Eq4DEgR0ZXN0 next=EugCEgR0ZXN0 count=4  overlapPrior=?
08-29 01:54:43.618  DETAIL  q=test page=cont videos=[BDtVmf-t80A:5.1 Surround Sound Test... | ... （共 4 筆）]
08-29 01:54:43.618  APPEND  q=test fetched=4 appended=4 dup=0 next=EugCEgR0ZXN0
```

**token 鏈逐頁驗證**（關鍵檢查點，全部正確）:

| 頁 | `sent`（送出的續頁 token） | `next`（收到的續頁 token） | 正確性 |
|----|---------------------------|---------------------------|--------|
| 1 | `-`（無） | `Er4CEgR0ZXN0` | ✅ 首頁 |
| 2 | `Er4CEgR0ZXN0`（= 上一頁 next） | `EqADEgR0ZXN0` | ✅ 正確送出上一頁 token |
| 3 | `EqADEgR0ZXN0`（= 上一頁 next） | `Eq4DEgR0ZXN0` | ✅ 正確送出上一頁 token |
| 4 | `Eq4DEgR0ZXN0`（= 上一頁 next） | `EugCEgR0ZXN0` | ✅ 正確送出上一頁 token |

- 每一頁都**正確送出上一頁的 `next` token** 並取得新的 `next`，token 鏈無中斷。
- 每一頁 `APPEND dup=0`＝**無重複項目**（fetch 數＝append 數），符合「append-only 不重疊」主張。
- 每一頁 `next` 均非 `-`＝尚未到底、續頁仍可前進。
- **全程無一筆 `WARN token not advanced`**。

### S3 crash／ANR／duplicate-key — PASS

```
PS> adb logcat -d -b crash
（無 app 相關 crash 紀錄；僅 uiautomator 工具 RuntimeInit 啟動訊息，非 crash）

PS> adb logcat -d | grep -E "FATAL EXCEPTION|ANR in|Duplicate key|duplicate key|com.youxiang8727.*Exception"
（無輸出）

PS> adb shell pidof com.youxiang8727.mymediaplayer
27546   ← 與 SearchPaging log 的 pid 一致，全程未重啟＝無 crash 重生
```

- 4 頁續頁全程，app 以同一 process（pid 27546）存活，未 crash 未重啟。
- 無 duplicate-key（LazyColumn 去重生效），與 `APPEND dup=0` 互相印證。
- 主 log 僅見系統層雜訊（NullBinder / MagnificationConnectionManager / uiautomator FeatureFlagsImplExport），皆非 app process。

### ⚠️ S4 捲到底部自動觸發載入更多 — FAIL（分歧點，需 A 判讀）

**預期**（依任務劇本）：捲動到底部觸發「載入更多」，連做 2~3 頁無需點按。

**實際觀察**：
- 純捲動（`adb shell input swipe` 多次下捲到底部，最後一筆結果與「載入更多」按鈕完整露出並停駐）後，**無**新 SearchPaging 請求送出——page 4 後停在 `next=EugCEgR0ZXN0`，未自動載 page 5。
- 唯有**點按「載入更多」按鈕**時才會送出續頁請求並正確追加（page 2/3/4 皆由點按觸發）。

**判讀**：「載入更多」實作為可點按按鈕；scroll 到底部並**不會**自動觸發續頁。此是否為預期行為（刻意用顯式按鈕）需由 A/開發者確認。續頁**資料邏輯完全正確**（token 鏈、dup=0、無重 key crash），所以這是互動設計層面的問題，不是資料正確性 bug。詳見風險 4。

> ⚠️ 此項標「需人工複核」：模擬器上的自動捲動行為我已反覆確認（多次 swipe 到底底部不觸發），但「到底部是否應自動載入」為產品 UX 決策，最終仍建議真人實機滑動確認直覺。

---

## 測試方法備註

- android-mcp `Swipe` 工具在我這組模擬器座標上數次未造成列表捲動，改用 `adb shell input swipe`（較長位移）或 `android-mcp Drag` 才成功捲動——此為工具層現象，**非 App 問題**（App 捲動本身正常，見 S4 段落由 adb swipe 成功捲到底）。
- 續頁以點按「載入更多」鈕 `(672,2440)` 觸發（S2、S4 證據如上）。
- 本環境 YouTube 出口 IP 為模擬器 NAT（搜尋 API 可用，可列出結果與續頁）；未觸發 bot 封鎖。播放成功路徑非本輪範圍。

---

## 風險與待確認事項

| # | 等級 | 項目 | 狀態 | 說明 |
|---|------|------|------|------|
| 1 | — | `./gradlew test` 186 tasks 無回歸 | **PASS** | 與開發者主張一致 |
| 2 | — | 續頁資料正確性（token 鏈／dup=0／無 WARN token not advanced） | **PASS** | 4 頁實測 token 逐頁正確前進，`dup=0`，無重複、無未推進 |
| 3 | — | crash／ANR／duplicate-key 監控 | **PASS** | 全程單一 process，crash buffer 空，無 duplicate-key |
| 4 | ⚠️ | **捲到底部不自動載入下一頁，需點「載入更多」鈕** | **FAIL（分歧點）** | 見上 S4。重現：搜尋 → 下捲到底部（最後一筆與「載入更多」完整露出）→ 停留數秒 → 無續頁請求；點「載入更多」才載入。**預期**＝捲到底自動載入；**實際**＝需點按。請 A/開發者確認是設計（顯式按鈕）或 bug（auto-load LaunchedEffect 未觸發）。**此項不涉及資料正確性，不影響 dup 去重與 crash 結論。** |
| 5 | — | 聲音／通知圖示 | ⚠️ 需人工 | 本輪僅驗證搜尋分頁，未測播放。S4 待 A 判讀後如需回歸播放，項目如 smoke-checklist #3/#4/#6（播放成功路徑仍受模擬器 IP 限制，需實機＋非資料中心網路）。 |

---

## 文件同步狀態

- 本輪**未修改任何產品程式碼與文件檔案**（QA 唯讀驗證）。
- 建議（由 A 決定是否採納）在 `docs/qa/smoke-checklist.md`「已知問題登記」區新增一筆：
  - **「搜尋續頁捲到底部是否需點『載入更多』才觸發」**：commit `804da8f` 實測，純捲到底不自動載入、需點按鈕；續頁資料正確（`dup=0`、token 鏈 OK、無 crash）。待 A 確認此為設計或 bug 後再決定結案/派回。
- 若確認是 bug 並修正，建議將「搜尋續頁 2~3 頁 + 檢查 `APPEND dup` 與 `WARN token not advanced`」納入煙霧清單常規項（本輪為 ad hoc 驗證）。

## 截圖索引

本輪以 logcat 原始輸出與 UI dump 為主要證據（搜尋分頁行為可由 SearchPaging tag 完整還原）；如需畫面截圖佐證「載入更多」按鈕位置，可至報告時補收（此輪未存 PNG）。

---

## Tech Lead 判讀（2026-08-29）

風險 #4（「捲到底需點『載入更多』才觸發續頁」）經 A 判讀為**設計使然**——原始需求即「載入更多」功能，實作為顯式 `LoadMoreFooter` 按鈕（`nextPageToken != null` 時顯示），非 infinite-scroll 設計。**結案，非 bug**。

資料正確性結論維持不變：token 鏈逐頁推進、`APPEND dup=0`、無 `WARN token not advanced`、無 crash，全部 **PASS**。本輪驗證結論不因風險 #4 的結案而變更。

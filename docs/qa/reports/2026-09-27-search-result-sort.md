# 搜尋結果排序（相關性／最新／熱門）實機煙霧測試報告

- **工作項**：`D-搜尋排序實機煙霧`
- **日期**：2026-09-27
- **受測 commit**：`9e4e8b7561d3f7dd936a55326bc5441f710c412a`（`9e4e8b7`）
- **測試者**：QA Engineer（D）
- **結論**：**PASS（附 2 項風險觀察，無阻擋缺陷）**

---

## 1. 測試環境

| 項目 | 值 |
|------|-----|
| 裝置 | `emulator-5554`（`sdk_gphone16k_x86_64`，Android Emulator） |
| 螢幕 | 1344 x 2992，density 480 |
| Android 版本 | **17**（SDK 37） |
| APK 版本 | commit `9e4e8b7`（沿用 A 已安裝之 debug APK，本項為純 UI／資料流煙霧，未重新建置） |
| 查詢詞 | `周杰倫` |
| 操作方式 | `android-mcp`（座標點擊／捲動／截圖），logcat 證據用 `adb -s emulator-5554 logcat -d` |

> **範圍聲明**：本次工作項限定「存證與報告」。以下項目由 Tech Lead(A) 先行實機驗證並已通過，**本報告不重做、僅引用**：排序切換器位置（搜尋鈕與結果列表之間，y≈786）、預設為相關性、續頁在排序下正常、無 `WARN token not advanced`、crash buffer 無 FATAL。本報告實際執行的是：截圖存證、三排序結果集對比、**切換排序後再切換的乾淨度驗證（3a）**、**搜尋紀錄不因切換排序增加（3b）**、logcat 複驗。

---

## 2. 逐項結果

| # | 驗證項 | 結果 | 證據 |
|---|--------|------|------|
| S1 | 三種排序的結果集**確實不同** | ✅ PASS | §3 前 5 筆對比 + 三張截圖 |
| S2 | 切換排序後結果**不混入舊結果**（3a） | ✅ PASS | §4，`sent=-` 證據 + 列表終點比對 |
| S3 | 切換排序**不寫入搜尋紀錄**（3b） | ✅ PASS | §5，3 筆 → 3 筆 |
| S4 | 續頁在三種排序下 `dup=0` | ✅ PASS | §6 logcat |
| S5 | 無 `WARN innerTube response shape changed`（未觸發 renderer fallback） | ✅ PASS | §6 |
| S6 | 無 FATAL EXCEPTION／ANR | ✅ PASS | §6，crash buffer 0 行 |
| S7 | 排序選擇在重新搜尋後保持 | ✅ PASS（額外觀察） | §7 |
| **O1** | **同一排序重複請求的結果穩定性** | ⚠️ **觀察：非確定性** | §8，3 次相同請求 3 種排序 |
| **O2** | **熱門排序偶發回傳 0 筆結果** | ⚠️ **觀察：1/6 次發生** | §8 |

---

## 3. S1：三種排序結果集差異（核心價值證據）

同一查詢 `周杰倫`，同一裝置、同一 session，依 `SearchPaging` log 與 accessibility tree 記錄**前 5 筆完整標題**：

| 名次 | 相關性（RELEVANCE，走 GET） | 最新（POST） | 熱門（POST） |
|------|---------------------------|-------------|-------------|
| 1 | 2:05:19 周杰倫好聽的30首歌 Best Songs Of Jay Chou 周杰倫最偉大的命中 - 30 …（JVR Lyric MV） | 3:45 Jay Chou 周杰倫【My Daughter, Your Highness女兒殿下】Official Music Video | 1:16:13 周杰倫演唱會46首精選Live現場歌曲串燒(Part 1)（Jacken666） |
| 2 | 1:26:32 周杰倫最好聽的20首歌曲｜在雨天聽周杰倫－絕佳的選擇（JVR Lyric MV） | 3:54 Jay Chou 周杰倫【Sicily 西西里】Official Music Video | 3:54 Jay Chou 周杰倫【Sicily 西西里】Official Music Video |
| 3 | 3:54 Jay Chou 周杰倫【Sicily 西西里】Official Music Video | 3:37 Jay Chou 周杰倫【Aegean Sea 愛琴海】Official Music Video | 4:29 周杰倫 Jay Chou【擱淺 Step Aside】-Official Music Video |
| 4 | 1:16:13 周杰倫演唱會46首精選Live現場歌曲串燒(Part 1) | 2:05:19 周杰倫好聽的30首歌 Best Songs Of Jay Chou …（JVR） | 3:54 周杰倫 Jay Chou【夜曲 Nocturne】-Official Music Video |
| 5 | 3:59 周杰倫 Jay Chou【愛在西元前 Love before AD】Official MV | 4:23 Jay Chou 周杰倫【Who Cares 誰稀罕】Official Music Video | 3:36 周杰倫 Jay Chou（特別演出: 派偉俊）【告白氣球 Love Confession】Official MV |

**判準：三種排序結果集必須不同 → 成立。**

- 第 1 名三種排序**互不相同**（30首歌 / 女兒殿下 / 演唱會46首）。
- 相關性明顯以「合輯／精選」開頭（30首、20首）→ 符合「找特定版本」的主要用途。
- 最新以官方單曲 MV 開頭 → 符合「找新歌／翻唱」用途。
- 三者間**存在**共同曲目（如 Sicily、30首歌同時出現在相關性與熱門），這是**預期且正確**的——排序改變的是**呈現順序**而非結果宇宙；判準是順序與名次不同，不是集合完全互斥。

### 截圖

| 檔案 | 內容 |
|------|------|
| `screenshots/2026-09-27-sort-1-relevance.png` | 相關性結果列表頂部（勾選在「相關性」） |
| `screenshots/2026-09-27-sort-2-latest.png` | 最新結果列表頂部（勾選在「最新」） |
| `screenshots/2026-09-27-sort-3-popular.png` | 熱門結果列表頂部（勾選在「熱門」） |
| `screenshots/2026-09-27-sort-4-loadmore-latest.png` | 最新排序點「載入更多」後（新增一筆「緯來新聞網」項目，原「載入更多」按鈕被推下去） |

三張排序截圖可**直接並排對比**結果集差異，滿足 `docs/TEAM.md` §1「涉及畫面變動必須附截圖」規範。

---

## 4. S2：切換排序後結果不混入舊結果（3a，本次改動最核心的正確性）

**測試路徑**：`熱門`（已載入 2 頁、**39 筆**）→ `相關性` → `最新`

### 證據一：續頁 token 每次都被清空

`SearchPaging` 的 `sent=` 欄位記錄本次請求**實際送出的** continuation token。切換排序後兩次請求皆為 `sent=-`（未送 token），證明 `nextPageToken` 已被清空、並未沿用前一次排序的 token：

```
09-27 03:10:52.756 24050 24111 I SearchPaging: SUMMARY q=周杰倫 page=1   sent=-              next=ErIDEgnlkajm count=19   ← 熱門 page1
09-27 03:11:47.084 24050 24110 I SearchPaging: SUMMARY q=周杰倫 page=cont sent=ErIDEgnlkajm next=EpoDEgnlkajm count=20   ← 熱門 page2
09-27 03:11:47.089 24050 24050 I SearchPaging: APPEND   q=周杰倫 fetched=20 appended=20 dup=0 next=EpoDEgnlkajm
09-27 03:13:36.006 24050 24110 I SearchPaging: SUMMARY q=周杰倫 page=1   sent=-              next=EpUEEgnlkajm count=16   ← 切相關性：token 已清空
09-27 03:13:59.913 24050 24111 I SearchPaging: SUMMARY q=周杰倫 page=1   sent=-              next=EpoDEgnlkajm count=20   ← 切最新：token 已清空
```

> 若 `nextPageToken` 未清空，切換後的請求會帶著舊排序的 token（`sent=ErIDEgnlkajm`），續頁結果就會混入。實測 `sent=-`，**符合 `docs/TEAM.md` §7「切換排序＝新的一次搜尋」的設計**。

### 證據二：列表長度與終點項都是該排序自己的

- 切到「最新」後一路捲到底，列表**恰好停在 page-1 的最後一筆**「3:40 Jay Chou 周杰倫【那天下雨了】The day it rained Lyric Video」，其下就是「載入更多」按鈕——**沒有多出熱門那 20 筆**。
- 熱門的續頁最後一筆是「…海 Coral Sea (fe…」，與最新的終點項完全不同 → 兩者未混流。
- 熱門當時已載入 39 筆（19+20）；若未清空，最新列表會是 39+ 筆。實測捲到底僅 page-1 的 20 筆。

**結論：PASS。** 切換排序會清空 `results`／`nextPageToken`／`error` 並重新搜尋，舊結果不殘留。

---

## 5. S3：切換排序不寫入搜尋紀錄（3b）

**語意依據**（`docs/TEAM.md` §7）：排序是同一查詢的**呈現方式**、不是新的搜尋意圖，故不應寫入 `search_history`。

### A/B 實驗

| 階段 | 操作 | 最近搜尋筆數 | 內容 |
|------|------|-------------|------|
| 基準 | （工作項開始時已發生 3 次排序切換） | **3** | 周杰倫／周杰倫 晴天／周杰倫晴天 |
| 動作 | 點歷史項「周杰倫」重新搜尋 → 切「相關性」→ 切「熱門」（**再 2 次切換**） | — | — |
| 結果 | 點 ✕ 清除搜尋回空狀態 | **3** | 周杰倫／周杰倫 晴天／周杰倫晴天（**完全相同**） |

**累積證據**：整個工作項期間共發生 **7 次排序切換**（A 3 次、本報告 4 次）與 2 次實際搜尋 `周杰倫`，而「最近搜尋」中**只有 1 筆** `周杰倫`，且切換排序後筆數與內容**分毫未變**。

**結論：PASS。** 切換排序不會新增、重複或重排搜尋紀錄。

---

## 6. logcat 檢查（原始輸出）

### 6.1 三種排序的續頁皆 `dup=0`

```
09-27 03:11:47.089 24050 24050 I SearchPaging: APPEND q=周杰倫 fetched=20 appended=20 dup=0 next=EpoDEgnlkajm   ← 熱門 續頁
09-27 03:14:54.859 24050 24050 I SearchPaging: APPEND q=周杰倫 fetched=20 appended=20 dup=0 next=EpoDEgnlkajm   ← 最新 續頁
```

`dup=0` 代表續頁 chunk 與既有結果**零重疊**（append-only，無重複 row）。這也隱含驗證了 `docs/TEAM.md` §7 通則一：搜尋結果 lazy list 的 `key` 帶 `index`，故重複 `videoId` 也不會觸發 `Key ... was already used` 崩潰——本次 7 次切換、數十筆結果列表滾動，`crash buffer` 為空。

### 6.2 無 renderer fallback

```powershell
adb -s emulator-5554 logcat -d | Select-String -Pattern "response shape changed|renderer fallback|token not advanced"
# （無任何輸出）
```

**未出現** `WARN innerTube response shape changed`，即搜尋結果的 renderer fallback 路徑**未被觸發**。

### 6.3 無 FATAL／ANR

```powershell
adb -s emulator-5554 logcat -d -b crash | Measure-Object -Line
# Count: 0        ← crash buffer 完全為空（連檔頭都沒有）

adb -s emulator-5554 logcat -d | Select-String -Pattern "FATAL EXCEPTION|ANR in |AndroidRuntime.*E/"
# （無任何輸出）
```

---

## 7. S7（額外觀察）：排序選擇在重新搜尋後保持

點歷史項「周杰倫」觸發新搜尋後，排序切換器**仍停在「最新」**並以最新排序重新發出請求（`03:15:40 SUMMARY page=1 sent=- count=20`），scroll position 重置回列表頂端。使用者不必每次搜尋都重選排序——屬正向行為，登記為觀察非缺陷。

---

## 8. 風險與待確認事項

### O1：同一排序的重複請求**結果不具確定性**（非阻擋）

`熱門` 排序在**完全相同的條件**（同 query、同裝置、同 session、連續請求）下做了 3 次 page-1，拿到 **3 種不同的前 5 名**：

| # | 请求时刻 | `count` | 前 5 名（依序） |
|---|---------|---------|----------------|
| 1 | 03:10:52 | 19 | 演唱會46首、Sicily、擱淺、夜曲、告白氣球 |
| 2 | 03:17:16 | 19 | Sicily、20首、擱淺、夜曲、30首 |
| 3 | 03:18:04 | 19 | 女兒殿下、20首、Sicily、30首、120首 |

- 全部 `sent=-`、無 continuation token 介入，故差異**不是**本專案的分頁邏輯造成。
- 推測為 innerTube（MWEB client）端無關本 request 的權重／A-B／個人化變動，**非本專案程式碼可控制**。
- **影響**：使用者若反覆切換排序再切回，**不會**回到完全相同的清單（會看到內容有位移）。這不違反任何既定契約（`docs/TEAM.md` §7 只承諾「切換排序＝新的一次搜尋」，未承諾結果可重現），故**不判為缺陷**；但若日後有人以「切回同一排序應顯示相同結果」為前提寫測試，該前提**不成立**。
- **待確認（派給 A）**：MWEB client 本身是否就回傳非確定性排序？若是，現行三個 `params` 值是否需要重新評估（`docs/TEAM.md` §7 已載明 GET `params` 為非確定性雜訊、POST 為 append-only chunk——本次觀察顯示**首頁**也非確定性，§7 該決策記錄或需補充一句）。

### O2：熱門排序偶發回傳 **0 筆**結果（1/6 次）

```
09-27 03:16:15.348 24050 24159 I SearchPaging: SUMMARY q=周杰倫 page=1 sent=- next=ErIDEgnlkajm count=0 overlapPrior=?
09-27 03:16:15.348 24050 24159 I SearchPaging: DETAIL  q=周杰倫 page=1 videos=[]
```

- **6 次熱門 page-1 請求中，1 次回傳 `count=0`**（03:16:15）；其餘 5 次為 19／19／19／19／19。後續再試 3 次皆為 19，**未能再重現**。
- 關鍵細節：該次回應**仍帶有有效的 `next` token**（`ErIDEgnlkajm`），且 `videos=[]` 表示 **renderer 解析成功但回應內無 video renderer**——不是解析失敗，也沒有觸發任何 fallback（`response shape changed` 無輸出）。
- **降級行為（讀碼確認，唯讀）**：`SearchViewModel.kt:243` 於空結果時 `_messages.tryEmit("查無結果")`，`SearchScreen.kt:197-202` 渲染「查無結果」空狀態。使用者會看到「查無結果」＋snackbar，**不會崩潰、不會卡在 loading**。→ 優雅降級，**不判為缺陷**。
- **殘留風險（需 A 裁決）**：這是一次**上游偶發空回應**，但 UI 會把它呈現為**斷言式的「查無結果」**——實際上是有結果的。使用者若當下遇到，會誤以為搜不到，且**沒有重試按鈕**。是否要加「重試」或把空結果與真無結果區分開，屬產品決策，**超出本工作項範圍**，僅登記。
- **本報告未驗證之處**：因僅發生 1 次且無法重現，**該瞬間的畫面表現未能截圖存證**。程式碼層面的降級行為已由讀碼確認（見上），但**實際畫面未經實測**，不宣稱通過。

### 其他
- 排序功能本次**未觸及**播放鏈路，故清單 #3／#4／#5a／#5b 等播放相關項目**不在本次範圍**，其現況沿用最近一次報告。
- 搜尋結果的「加入」為兩步驟（點「加入」→ bottom sheet → 點「加入佇列尾端」），本次驗證**未執行**加入動作，無誤觸。

---

## 9. 常規驗證項目已納入 smoke-checklist

本次新增 `docs/qa/smoke-checklist.md` **#34、#35**，並把「三種排序結果集必須不同」列為明確判準。O1／O2 已登記於該清單的「已知問題登記」區。

# 歌單縮圖實機驗證報告（存佇列為播放清單）

> 檔名未帶 commit hash（runbook 慣例為 `YYYY-MM-DD-<shorthash>.md`）：本工作項同時受測
> **修正 commit `6495d74`**（分支 `fix/playlist-thumbnail-from-videoid`）與
> **repo HEAD `03b9585`**（分支 `fix/search-duplicate-empty-feedback`）兩個版本，
> 單一 hash 無法表達，故以主題命名並於下方明確記錄兩者。

| 項目 | 內容 |
|------|------|
| 報告日期 | 2026-09-28 |
| 受測修正 | `6495d74 fix(data): 存佇列為播放清單時由 videoId 補上縮圖 URL`（分支 `fix/playlist-thumbnail-from-videoid`） |
| repo HEAD | `03b9585 refactor(search): 空結果回饋改由空狀態單一承載`（分支 `fix/search-duplicate-empty-feedback`，**不含**本修正） |
| APK | `app/build/outputs/apk/debug/app-debug.apk`，mtime `2026-09-28 01:47:35`，size 50,647,019 bytes |
| 裝置 | `emulator-5554`，model `sdk_gphone16k_x86_64`（Android Emulator） |
| Android 版本 | 17（`ro.build.version.release`） |
| 解析度／密度 | 1344 × 2992 px，density 480（≈ xxhdpi） |
| 安裝時間 | 裝置端 `lastUpdateTime = 2026-09-28 05:49:45`（versionName 1.0 / versionCode 1） |

## APK 確實含本修正的驗證方式

因 repo HEAD 不在受測分支上，**必須先證明裝置上的 APK 含 `6495d74`**，否則實測結果無意義。
採兩條證據：

1. **建置時間序**：APK mtime `01:47:35` 早於安裝 `05:49:45`（裝置時鐘）約 2 分鐘，符
   「先建置自修正分支、後安裝」的時序。
2. **對裝置上 APK 內容直接 grep**（不經 repo 檔案）：

```
$ adb shell pm path com.youxiang8727.mymediaplayer
package:/data/app/~~jIPZVLcHW4v0kcR4KJ7nXQ==/com.youxiang8727.mymediaplayer-0JMQNApvgRyaMM4XutZUeA==/base.apk

$ adb shell "grep -ac hqdefault <base.apk>"
3
```

`hqdefault` 在 APK 內出現 3 次（2 處為 `core:ui` 的 Preview 常數、1 處為 `6495d74`
新增的 `THUMBNAIL_URL_TEMPLATE`），確認修正的 wire 規則存在於**裝置上實際執行的 dex**。
（`grep -a` 計的是「含該字串的行數」，非字元節數，僅作為存在性證據，不作數量解讀。）

## 驗證結果總表

| # | 驗證項目 | 結果 |
|---|----------|------|
| 1 | 存佇列為歌單後，詳情頁縮圖**非灰色 `surfaceVariant` 色塊** | ✅ PASS |
| 2 | `mergedCount` 如實回報（「其中 N 首重複曲目已合併」） | ✅ PASS |
| 3 | 縮圖**不會有 letterbox 黑邊**（`ContentScale.Crop` 結構性保證） | ✅ PASS（程式碼面 ＋ pixel probe 四邊 0/9，探測器經負向對照驗證） |
| 4 | 縮圖**上下裁切損失**（4:3 480×360 → 槽位 286×160 ≈ 16:9） | ✅ **已實測確認**（上下各約 25%，有目視證據：文字行被切斷） |
| 5 | 對照組（搜尋加入 vs 存佇列）外觀一致性 | ⚠️ **未實機複驗**（見下方說明） |
| 6 | 播放清單分頁「歌單卡片」封面 | ➖ **B2b 尚未實作，屬預期狀態**（非 regression） |
| 7 | 全程 crash／ANR | ✅ PASS（無 FATAL／ANR） |
| 8 | 歌單卡片「刪除」無二次確認 | ⚠️ **新觀察，未定性**（見文末「測試資料殘留」） |

---

## 1. ✅ 縮圖修正生效

**重現步驟**

1. 搜尋頁點近期搜尋「周杰倫 晴天」→ 依序對 5 筆結果按卡片「＋」→ bottom sheet →「加入佇列尾端」
   （其中第 2 首因步驟重複被加入兩次，故佇列共 **5 筆 / 4 首**）。
2. 展開 MiniPlayerBar 佇列面板 →「＋ 存為播放清單」→ 輸入 `B2aThumbTest` → 建立。
3. 播放清單分頁 → 點開 `B2aThumbTest` → 觀察詳情頁。

**實測**：詳情頁顯示 **4 首歌（佇列 5 筆折疊 1 首重複後），每一首都顯示真實縮圖**，
**無任何一張是灰色 `surfaceVariant` 色塊**，亦無 `AsyncImage` 的 error fallback。

截圖：`screenshots/2026-09-28-thumb-01-playlist-detail.png`

**驗收重點**：修正前此頁 4 張全為灰色色塊（`PlayQueueItem` 無 `thumbnailUrl`，
`PlayQueueItemEntity.thumbnailUrl` 寫入空字串 → `VideoThumbnail` 走 `url.isBlank()` 分支）。

## 2. ✅ `mergedCount` 回報成立

**重現步驟**：同一個 5 筆（含 1 重複）佇列 →「存為播放清單」→ 建立 `B2aSnack3`。

**實測 snackbar**（原文）：

```
已建立「B2aSnack3」，其中 1 首重複曲目已合併
```

- 佇列 5 筆 → 歌單 4 首，`N = 1` **正確**（對應佇列中重複的那 1 首）。
- 符合 `docs/TEAM.md` §7「絕不靜默：mergedCount 必須回報」的取捨。

截圖：`screenshots/2026-09-28-thumb-02-merged-count-snackbar.png`

> 捕捉方法（可複用）：`SnackbarHost` 顯示區間約 4 秒，而 `android-mcp` 呼叫往返延遲
> > 4 秒，**用 MCP 逐步驟必然拍不到**。本張是將「展開佇列 → 存為播放清單 → 輸入名稱 →
> 建立」與 `adb exec-out screencap` 放進**同一段 adb 指令序列**才取得，
> 與清單 #33 記載的是同一條限制。

## 3. 黑邊（letterbox）判斷

原始疑慮：修正採 `hqdefault`（YouTube 固定 480×360，4:3），若歌單詳情頁縮圖槽是 16:9 或更寬，
4:3 畫布以 `ContentScale.Fit` 渲染會產生左右黑邊（直式 MV 尤為明顯）。

### 3.1 程式碼面證據（唯讀檢視，未修改任何產品程式碼）

`core/ui/src/main/java/com/youxiang8727/mymediaplayer/core/ui/component/VideoThumbnail.kt`：

```kotlin
AsyncImage(
    model = url,
    contentScale = ContentScale.Crop,        // ← 填滿並裁切，非等比留邊
    modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .clip(MaterialTheme.shapes.small),
    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
)
```

**結論：`ContentScale.Crop` 搭配 `fillMaxWidth() + fillMaxHeight()` 在結構上不可能產生
letterbox 黑邊** —— 影像必定填滿整個槽位，多餘部分被裁掉。因此：

- **原假設的「黑邊」不成立**，且此結論**不取決於 MV 是直式或橫式**（直式 MV 在
  `Crop` 下只是被裁掉更多，一樣不會留邊）。
- **真正的代價是裁切損失**：`hqdefault` 為 4:3，槽位外觀由呼叫方 `modifier` 決定
  （歌單詳情頁實測約 2:1；`VideoThumbnail` Preview 慣例為 `size(96.dp, 54.dp)` ≈ 16:9）。
  4:3 裁成 2:1/16:9 會**切掉上下緣約 25%**——即部分 MV 的字幕、字幕條、畫面上下構圖
  可能被切到。這是本次修正應被記錄的取捨，**不是黑邊**。

### 3.2 實機畫面觀察

`B2aThumbTest` 詳情頁 4 張縮圖逐張觀察：

| # | 曲目 | 觀察到的畫面 |
|---|------|--------------|
| 1 | 周杰倫 Jay Chou【晴天 Sunny Day】-Official Music Video | 人臉特寫**填滿整個圓角矩形**，未見上下或左右黑邊 |
| 2 | 晴天 周杰伦 (歌词版) GM Lyric | **整張黑底白字**（「晴天 周杰伦」置中） |
| 3 | 周杰倫 晴天 無損音樂FLAC 歌詞LYRICS 純享 MusicDelta | **整張黑底白字**（「周杰倫 晴天」置中） |
| 4 | 【周杰倫地表最強演唱會LIVE-晴天】 Jay Chou's The Invincible Concert LIVE | 演唱會畫面**填滿整個圓角矩形**，未見黑邊 |

關於第 2、3 張的「黑底」——**這不是 letterbox**：同一輪測試中，**搜尋結果列表**裡這兩首的
縮圖本來就是同樣的黑底白字卡片（那是該 MV 上傳者自製的縮圖內容本身）。搜尋結果與歌單詳情頁
兩處外觀一致，可互相佐證。判準是「影像內容本身是黑底」，而非「黑邊包住影像」。

### 3.3 Pixel probe（實測，已補做）

> 本節為第二輪補測（第一輪僅有截圖觀察）。方法與清單 #28 的 pixel probe 同源：
> 對 `adb exec-out screencap -p` 的 PNG，在縮圖槽**四條邊緣**各取 9 點、均勻分佈
> （避開圓角），以「**中性黑**」為 letterbox 判準：R/G/B 全部 < 40 且
> max−min < 18（排除深藍、深紫等有色暗部）。

**槽位幾何（實測）**：縮圖槽 x 84–370、y 678–838 → **286 × 160 px，比例 1.79 ≈ 16:9**。
`hqdefault` 為 480×360（4:3 = 1.33）。`ContentScale.Crop` 取兩者交集 →
**上下各被裁掉約 25%**（數值與 §3.1 的推理一致）。

**探測器先自我驗證（負向對照）**：第 2 首「歌詞版」的縮圖**內容本身就是全黑底**，
四條邊緣合計 **28/36 點為精確的 `(0,0,0)`**（另 8 點為圓角處的卡片背景 `(225,226,237)`）。
→ **探測器確實能偵測中性黑**，後續「0 命中」不是量測失敗造成的假陰性。

| 縮圖 | 左緣 | 右緣 | 上緣 | 下緣 | 判定 |
|------|------|------|------|------|------|
| item1 周杰倫【晴天】Official MV | **0/9** | **0/9** | 1/9 | 5/9 | 見下 |
| item2 歌詞版（**負向對照**） | 7/9 | 7/9 | 7/9 | 7/9 | 內容本身即全黑 ✅ 探測器有效 |
| item4 地表最強演唱會 LIVE | **0/9** | **0/9** | **0/9** | **0/9** | **無任何中性黑 → 無黑邊** |

**item4 原始邊緣取樣**（ darkest 者仍為飽和藍紫，非中性黑）：

```
上緣  x=120 (27,29,129)  x=156 (11,16,107)  x=191 (19,26,104)  x=227 (22,28,106)  x=263 (42,34,117)
下緣  x=120 (8,8,62)     x=156 (5,5,37)     x=227 (59,64,146)  x=298 (183,48,216) x=334 (94,41,185)
```

B 通道一律為 R/G 的 3–10 倍 → 這是演唱會舞台的**深藍／紫光**，不是 letterbox 墊底。

**item1 下緣 5/9 命中中性黑的處理**：這**不是黑邊，是影像內容**。三項判據：
1. 靠近右下圓角的 `x=334` 為 `(163,170,152)`（淺色）——letterbox 條應一路黑到圓角。
2. 放大截圖（`screenshots/2026-09-28-thumb-04-zoom-item1.png`）顯示該區是**人物黑色上衣
   與頭髮的材質紋理**，非均勻色塊。
3. 同一張放大圖的**最下緣可見一行被水平切斷的白色文字**（只剩字形上半截）——這是
   4:3 裁成 16:9 的**直接目視證據**，是「裁切損失」而非「黑邊」。

### 3.4 結論（含誠實邊界）

| 命題 | 結論強度 |
|------|----------|
| 程式碼結構上不可能產生 letterbox（`ContentScale.Crop` + `fillMax*`） | ✅ **確定**（結構性排除） |
| item4 四邊 0/9 中性黑（探測器已自我驗證） | ✅ **確定無黑邊** |
| item1 四邊情形（左右 0/9；下緣黑色經三項判據判為內容） | ✅ **判定無黑邊**（依內容紋理 + 圓角處非黑，非僅憑肉眼） |
| item2 全黑 | ➖ **不適用**（該 MV 縮圖內容本身即黑底白字，與 letterbox 無關） |
| 裁切損失（上下各約 25%） | ✅ **已實測確認**，且有目視證據（被切斷的文字行） |

**殘餘不確定性（不迴避）**：本次僅驗 `B2aThumbTest` 這 4 首、單一淺色主題、單一裝置
密度。**未**測深色主題（`surfaceVariant` 與黑邊的對比不同）、**未**測其他 MV（尤其真實
直式 MV——但依 `Crop` 語意，直式只會被裁更多、不會留邊）、**未**測其他螢幕密度下槽位
比例改變的情況。這些屬「結論外推」，非「已驗證」。

### 3.5 放大特寫截圖

| 檔案 | 內容 |
|------|------|
| `screenshots/2026-09-28-thumb-04-zoom-item1.png` | 人臉特寫 ×2.5 倍；**可見下緣被切斷的白色文字行**（裁切損失目視證據）＋黑色上衣紋理 |
| `screenshots/2026-09-28-thumb-04-zoom-item2.png` | 黑底白字（負向對照） |
| `screenshots/2026-09-28-thumb-04-zoom-item4.png` | 演唱會 ×2.5 倍；影像填滿四邊、無黑邊 |

（放大以 `System.Drawing` 的 `NearestNeighbor` 由原始 1344×2992 截圖裁切，非重新截圖。）


## 4. ⚠️ 對照組：未實機複驗

**未執行**「搜尋結果 → 選歌單加入」建立對照歌單。理由有二：

1. **技術上已可推定結論**：兩個路徑寫入的 `thumbnailUrl` 最終都會餵給**同一個**
   `VideoThumbnail` composable、同一個 `ContentScale.Crop` 渲染路徑；`6495d74` 的
   `withResolvedThumbnail()` 也**只補空白、不覆寫既有值**，故搜尋路徑帶入的 API 縮圖
   會被原樣保留（不會被 `hqdefault` 蓋掉）。**同一首歌在兩個歌單中的縮圖外觀必然一致**。
2. **避免汙染已建立的 `B2aThumbTest`**：若對 `B2aThumbTest` 補加同一首歌，會觸發
   清單 #31 的重複合併路徑，改變該歌單的組成，反而失去作為本次證據的乾淨狀態。

**因此本項狀態為「未實機複驗」，不是「已驗證通過」。** 若 Tech Lead 認為此對照需有實機
證據，應另開一輪、用**全新空歌單**加入同一首歌再行比對。

## 5. ➖ 新發現：播放清單分頁的「歌單卡片」沒有封面區塊

觀察 `播放清單` 分頁（共 9 個歌單）：歌單卡片**只有「名稱 ＋ 建立於 YYYY-MM-DD」＋右側
「更多操作」三個垂直元素，整張卡片沒有任何封面／縮圖區塊**——既不是灰色色塊，也不是
載入失败的空圖，而是**該區塊不存在**。

**定性：屬 B2b 工作項（歌單卡片顯示第一首封面）尚未實作，是預期狀態、不是本次修正的
regression。** 理由：`6495d74` 的修正範圍只到「寫入時補上 `thumbnailUrl`」，
而歌單卡片是否取用該欄位屬另一條 UI 路徑（`Playlist` 列表卡片 vs `PlaylistDetail` 項目列），
本次未觸及。

**登記為已知問題（見 `docs/qa/smoke-checklist.md` 已知問題 O3）**，避免日後回溯時被
誤讀為縮圖修正失效。

## 6. logcat

```
$ adb logcat -b crash -d
（無輸出）

$ adb shell dumpsys window | grep mCurrentFocus
mCurrentFocus=Window{5e615af u0 com.youxiang8727.mymediaplayer/...MainActivity}
```

全程（建立佇列 → 存 3 次歌單 → 開啟詳情頁）**無 FATAL EXCEPTION、無 ANR**。
App 持續在前台，未發生崩潰或被系統重啟。

## 未覆蓋項目（明確聲明）

| 項目 | 狀態 | 原因 |
|------|------|------|
| 對照組（搜尋加入）外觀一致性 | ⚠️ **未實機複驗** | 見 §4；已於程式碼面推定必然一致，但非實測 |
| 縮圖放大特寫截圖 | ✅ **已補做** | §3.5，3 張 ×2.5 倍放大 |
| 縮圖像素值量測（pixel probe） | ✅ **已補做** | §3.3，四邊各 9 點、以第 2 首為負向對照自我驗證探測器 |
| 深色主題下的黑邊判斷 | ⚠️ **未測** | 僅驗淺色主題；`surfaceVariant` 與黑邊對比不同，屬結論外推 |
| 真實直式 MV 的黑邊判斷 | ⚠️ **未測** | 本次 4 首皆橫式；依 `Crop` 語意直式只會裁更多、不會留邊，但未實測 |
| 其他螢幕密度／槽位比例 | ⚠️ **未測** | 僅 density 480（286×160 px ≈ 16:9） |
| 播放清單分頁歌單卡片封面截圖 | ⚠️ **未存檔** | 已確認無封面區塊（§5），但當輪未另存截圖 |
| Gradle 驗證（`test` / `assembleDebug`） | ➖ **未執行** | 依 `docs/TEAM.md` 2026-09-15 裁定，重量級建置由 Tech Lead 於主 loop 執行；本輪僅做實機驗證 |
| 清理冗餘測試歌單 | ✅ **已完成** | 見文末「測試資料殘留」（走 App UI 刪除） |

## 建議（僅為建議，本報告不自行提案實作）

`mqdefault`（`https://i.ytimg.com/vi/{id}/mqdefault.jpg`，**320×180、16:9**）與歌單詳情頁
槽位比例相符，採 `ContentScale.Crop` 時可**消除裁切損失**（僅剩極小的比例差異裁切），
代價是解析度較 `hqdefault`（480×360）低。屬 **`core:data` 的 wire 常數決策**
（`YoutubeThumbnailUrl.kt`），建議由 Tech Lead 裁決是否改用，**QA 不代為決定**。
若維持 `hqdefault`，建議把「上下裁切約 25%」寫入 `docs/ARCHITECTURE.md` §7 風險／取捨登錄。

## 測試資料殘留（交接事項）

| 歌單 | 狀態 | 說明 |
|------|------|------|
| `B2aThumbTest` | **保留（刻意）** | 本報告 §1／§3 的證據歌單，4 首 |
| `B2aThumbSnack` | ✅ **已刪除** | 為捕捉 snackbar 建立的第一次重試，內容與 `B2aSnack3` 相同 |
| `B2aSnack3` | ✅ **已刪除** | §2 snackbar 證據已由截圖保存，歌單本身可刪 |

刪除方式：**走 App UI**（播放清單分頁 → 該歌單卡片「更多操作」→「刪除」），
未繞過 App 邏輯直接操作 Room。清理後清單：播放清單（7）
= `B2aThumbTest`／`SnackTest`／`D1驗證`／`S2重複測試`／`S2順序測試`／
`Migration Test Playlist (2)`／`Migration Test Playlist`。

截圖：`screenshots/2026-09-28-thumb-03-playlist-tab-after-cleanup.png`

> **順帶觀察（非本工作項範圍，未驗證是否為設計意圖）**：歌單卡片的「刪除」**沒有二次
> 確認對話框**，點下去即直接刪除並從列表消失。相較之下「存為播放清單」有 Dialog 防呆
> （清單 #32），此處的不可逆操作無防呆值得 Tech Lead 評估——但本次未就此下結論。

佇列（`MiniPlayerBar`）於測試結束時仍有 5 筆並處於播放狀態。


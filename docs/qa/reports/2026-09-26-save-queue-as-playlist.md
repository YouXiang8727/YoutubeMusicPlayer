# 存佇列為歌單（S2 工作項）實機煙霧測試報告

> 狀態：**部分完成**（① 已驗證 PASS；② 進行中；③④⑤⑥⑦ 已驗證或待補，見逐項表）
> 本報告為獨立驗證（角色 D），全程未修改任何產品程式碼。

## 0. 環境

| 項目 | 值 |
|------|-----|
| 受測 commit | `3201f85` feat(player): 播放佇列可整份存成新的播放清單 |
| 裝置 | `emulator-5554`（Android Emulator, sdk_gphone16k_x86_64） |
| Android 版本 | 17 |
| 螢幕 | 1344x2992 |
| APK | versionName 1.0 / versionCode 1（debug，`adb install -r`） |
| 播放狀態前置 | shuffle **預設開啟**，已手動關閉（否則佇列順序觀察不成立） |

## 1. 測試基準佇列（本次沿用，未重建）

| index | 曲目 |
|-------|------|
| 1 | 周杰倫演唱會46首精選Live現場歌曲串燒(Part 1)（建立當下為目前播放項） |
| 2 | Jay Chou 周杰倫【Sicily 西西里】Official Music Video |
| 3 | Jay Chou 周杰倫【Aegean Sea 愛琴海】Official Music Video |
| 4 | 周杰倫 Jay Chou【告白氣球 Love Confession】Official MV |
| 5 | 周杰倫歌曲..50首精選集… |

`adb shell dumpsys media_session` 佐證：`queueTitle=null, size=5`、
`state=PlaybackState {state=PLAYING(3), position=285594, speed=1.0, error=null}`。

> 註：搜尋結果卡片的「加入」為**兩步驟**——點「加入」會彈 bottom sheet，須再點 sheet 內
> 「加入佇列尾端 不中斷目前播放」才真的加入。僅點「加入」不會加入佇列。
> （此為既有互動，非本工作項缺陷；第一次驗證時因此浪費數個步驟，記錄在此避免重蹈。）

## 2. 逐項判定

| # | 驗證項目 | 判定 | 證據 |
|---|---------|------|------|
| ① | **順序保真**（5 首不重複，存入後順序須正好 1→5） | ✅ **PASS** | 見 §3 |
| ② | 重複曲目如實回報（`mergedCount` 數字正確） | ⏳ 未完成 | — |
| ③ | 空佇列時「存為播放清單」disabled | ⚠️ **無法觀察（失效安全）** | 清空佇列後 `dumpsys` 為 `size=0`／`state=STOPPED(1)`，且 **MiniPlayerBar 整條消失**，故按鈕根本不存在、面板的「佇列為空」狀態亦不可達。`enabled = queue.isNotEmpty()` 屬**不可觸發的防禦程式碼**——非缺陷，但無法觀察到 disabled 樣貌 |
| ④-a | Dialog：名稱為空時不可提交 | ✅ PASS | Dialog 開啟時「建立」呈灰色 disabled |
| ④-b | Dialog：trim／全空白不可提交 | ⚠️ **無法確認** | 輸入 5 個空格後「建立」確實仍灰色，但快照中 EditText 文字為空字串，**無法區分**是 `isNotBlank()` 生效还是空格未實際輸入。判定為無法確認，不追查（屬既有 `CreatePlaylistDialog` 行為，非本功能範圍） |
| ④-c | Dialog：**重名即時阻擋**（紅框＋「此名稱已存在」＋建立 disabled） | ✅ PASS | 輸入既有名稱 `S2順序測試` → 紅框、紅色 label、紅色 supporting text「**此名稱已存在**」、「建立」灰色 disabled 且不產生可點擊節點。截圖 `screenshots/2026-09-26-s2-duplicate-name-blocked.png` |
| ⑤ | 面板與 Dialog 互斥 | ✅ PASS | 點「存為播放清單」後 sheet 節點（`播放佇列`／`清空佇列`／各列`移除…`）**全部消失**、scrim 消失、`MiniPlayerBar` 收起，同時「建立新播放清單」Dialog 正常顯示；兩者未互相遮蔽 |
| ⑥ | 建立成功 snackbar 可見 | ❌ **FAIL → 已修復並複驗通過** | 詳見 §6 缺陷 D-1。修復後複驗：snackbar 完整可見、文案與合併數正確 |
| ⑦ | 建立後可播放（點歌單內曲目能起播） | ✅ PASS | 點 `S2重複測試` 內 Sicily → `dumpsys`：`active item id=1`、`description=Jay Chou 周杰倫【Sicily 西西里】`、`state=PLAYING(3), position=0, buffered=1811`、`size=5` |
| ⑧ | logcat 無 FATAL／無 ANR | ✅ PASS | 全程 4 次 `adb logcat -b crash -d` 輸出皆為空（僅 `---CRASH-BUFFER-END---` 標記） |

## 3. ① 順序保真 —— 詳細證據（本功能核心，Owner 裁決項）

**操作**：展開佇列面板 → 點「存為播放清單」→ 輸入 `S2順序測試` → 建立 → 進入
`播放清單` 分頁 → 點開 `S2順序測試`。

**建立結果**：`播放清單 (3)`，新歌單 `S2順序測試 建立於 2026-09-26` 排在最上。
縮圖為灰色色塊（`thumbnailUrl = ""`）——**已登記的預期取捨，非缺陷**。

**歌單詳情頁 UI accessibility tree 節點順序（自上而下，非僅靠截圖）**：

| 節點座標 | 節點文字（節點順序即列表順序） | 對應佇列 index |
|---------|------------------------------|--------------|
| (672,759) | 周杰倫演唱會46首精選Live現場歌曲串燒(Part 1) | 1 |
| (672,1017) | Jay Chou 周杰倫【Sicily 西西里】Official Music Video | 2 |
| (672,1275) | Jay Chou 周杰倫【Aegean Sea 愛琴海】Official Music Video | 3 |
| (672,1533) | 周杰倫 Jay Chou【告白氣球 Love Confession】Official MV | 4 |
| (672,1791) | 周杰倫歌曲..50首精選集…Songs of the Most Popular Chinese Singer | 5 |

節點文字前綴 `隨機播放（5 首）` 確認曲目數為 5。

**判定**：歌單內順序為 **1→2→3→4→5，與佇列完全一致，未被 `ORDER BY addedAt DESC` 反轉**。
`addedAt = base - index` 反向遞增的保序設計在實機上**成立**。

**截圖**：`screenshots/2026-09-26-s2-order-fidelity.png`

## 4. ② 重複曲目折疊與回報 —— 詳細證據

**前置**：搜尋「周杰倫 晴天」→ 對第一筆（`DYptgVvkVLQ`／周杰倫 Jay Chou【晴天 Sunny Day】）按「加入」→「加入佇列尾端」**兩次**，再加入第三首不同的（4:30 晴天 周杰伦 (歌词版) GM Lyric）→ 佇列 **3 首**（晴天佔 2 筆）。

**資料層結果** ✅ PASS：存為 `S2重複測試` 後歌單為 **2 筆**，晴天僅 1 列，順序仍 1→2 保留。折疊數 = 3 − 2 = **1**。

（另一組獨立驗證：佇列 7 首、Sicily 佔 3 筆 → 2 首重複 → 歌單 5 筆 = 7 − 2，Sicily 僅 1 列。）

## 5. 缺陷 D-1：建立成功但無任何 snackbar（阻擋級，已修復）

### 症狀

點「建立」後歌單**確實建立成功**（可進入、可播放、筆數正確），但畫面**完全沒有任何提示**。四次獨立擷取（建立後立即 snapshot、立即 screencap、切至播放清單分頁）全為陰性。

### 為何是阻擋級

此缺陷直接抵銷 `TEAM.md` §7「**絕不靜默**：`mergedCount` 必須回報並顯示」的設計意圖——使用者存 7 首只得到 5 首，卻**完全沒有任何告知**。該條文訂明「那是誤導而非簡潔」。

### 根因（由 QA 提出假設，Tech Lead 以程式碼層級關係實證）

`MainActivity` 原先的結構是：

```
Box {
    Scaffold(snackbarHost = { SnackbarHost(...) })   // zIndex 0
    AnimatedVisibility { MiniPlayerBar }              // zIndex 2f
    scrim                                              // zIndex 1f
}
```

層級與位置**雙重**導致遮蔽：

1. **層級**：`MiniPlayerBar` 為 `zIndex(2f)`、`Scaffold` 為 0 → 後者（含其內的 `SnackbarHost`）繪製在 MiniPlayerBar **之下**。
2. **位置**：Material3 `Scaffold` 將 snackbar 排在 `bottomBar` 之上（≈ `navigationBarHeight`），而 `MiniPlayerBar` 亦為 `BottomCenter` + `padding(bottom = navigationBarHeight)` → 兩者垂直位置**重疊**。

### 影響範圍不限本功能

同一 `snackbarHostState` 也承載 `playback.errorMessage`（**播放失敗提示**）。推測同樣被遮蔽而長期未察覺——**本次一併修正**，既有煙霧清單 #12「播放失敗 snackbar」建議重驗。

### 修復

`SnackbarHost` 移出 `Scaffold`、置於同一 `Box` 末段並給 `zIndex(3f)`；原本由 `Scaffold` 依 `bottomBar` 高度自動施加的 padding 改為手動施加：

- 播放中（`playback.hasCurrent`）：`bottom = navigationBarHeight + miniPlayerOverlayHeight` → **疊在 MiniPlayerBar 上方、不遮蔽它**
- 未播放：`bottom = navigationBarHeight` → 貼在導覽列上方

### 複驗（由 Tech Lead 執行）

採 `adb shell input tap` 將點擊與截圖置於同一指令內（`android-mcp` 呼叫往返延遲 > snackbar 的 4 秒顯示區間，會錯過擷取時機）：

佇列 3 首（晴天 ×2 ＋ 歌詞版 ×1）→ 存為 `SnackTest` → 點擊後 1.2 秒截圖。

**結果** ✅：snackbar 完整可見，位置正確疊在 MiniPlayerBar 上方（未遮蔽歌名與進度條），文案為

> **已建立「SnackTest」，其中 1 首重複曲目已合併**

合併數字 **1** 與實際折疊數一致。截圖：`screenshots/2026-09-26-s2-snackbar-fixed2.png`
（`…-snackbar-fixed.png` 為 `android-mcp` 版本，因時序延遲錯過擷取，是陰性對照組。）

## 6. 已知非缺陷行為（避免誤判）

- 歌單縮圖顯示灰色色塊（`thumbnailUrl = ""`，`PlayQueueItem` 無 artwork）——`TEAM.md` §7 已登記取捨。
- 搜尋結果「加入」為兩步驟（見 §1 註）。
- shuffle 預設開啟，會影響佇列順序觀察。
- `CreatePlaylistDialog` 全空白（含純空格）時「建立」disabled 為預期。

## 7. 結論與後續

**功能已達可合併標準**：① 順序保真（Owner 裁決核心）、② 折疊與如實回報、④-c 重名阻擋、⑤ 互斥、⑥ snackbar（缺陷 D-1 修復後）、⑦ 可播放、⑧ logcat 全數通過。

**未完成／待補**：

- **③ 空佇列入口 disabled**：狀態不可達（清空佇列使 MiniPlayerBar 整條消失），屬失效安全，非缺陷。若要觀察需另造情境。
- **④-b 全空白不可提交**：無法確認（既有 `CreatePlaylistDialog` 行為，非本功能範圍）。
- **既有 #12「播放失敗 snackbar」建議重驗**：D-1 的根因（`SnackbarHost` 層級）同樣影響它，本次已一併修正但未專項重驗。
- **音訊實際輸出**未經人工確認（僅以 `dumpsys` 的 `state=PLAYING(3)`、`buffered position` 前進判定可起播）。
- 僅淺色主題、單一 emulator。

## 8. 工具面教訓（非產品缺陷，供後續 D 參考）

- `android-mcp_Type` 需輸入框**先取得焦點**才生效；Dialog 剛開啟時欄位未聚焦，直接 `Type` 會**靜默失敗**。
- **捕捉短暫 UI（snackbar 等）不可用 `android-mcp` 逐步驟**——呼叫往返延遲可能超過顯示區間。應將操作與 `screencap` 放在**同一個 `adb shell input` 指令序列**內，或改用較低解析度 `screenrecord`（本機 1344×2992 會 `err=-22` 失敗並降級）。
- 搜尋結果「加入」為**兩步驟**（點「加入」→ bottom sheet → 點「加入佇列尾端」），僅點「加入」不會加入。

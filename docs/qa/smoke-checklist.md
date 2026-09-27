# 煙霧測試清單（Smoke Checklist）

> 維護者：QA Engineer(D)。新功能 merge 進 `master` 後，把對應驗證項目加進本清單。
> 執行環境務必記錄：裝置／模擬器型號、Android 版本、APK 版本（commit hash）。

| # | 項目 | 步驟 | 預期結果 | 自動化 |
|---|------|------|----------|--------|
| 1 | App 啟動 | 安裝後啟動 App | 主畫面顯示、無 crash、無 ANR | ✅ adb |
| 2 | 搜尋 | 搜尋任意關鍵字（例：周杰倫） | 回傳影片清單、可捲動 | ✅ adb + logcat |
| 3 | 直接播放 | 點擊任一搜尋結果卡片 | **不導航**播放頁；底部 MiniPlayerBar 出現並開始播放 | ✅ adb + logcat |
| 4 | 背景續播 | 播放中按 Home 鍵退背景 | 音訊持續；通知列有媒體通知（歌名＋控制按鈕） | ⚠️ 需人工確認聲音 |
| 5 | MiniPlayerBar | 直接播放後觀察搜尋頁底部 | 底部出現迷你播放列（歌名＋控制） | ⚠️ 需人工 |
| 5a | 串流解析 fallback | 播放任一首，logcat 過濾 `StreamResolver\|MusicService\|FallbackStreamResolver\|InnerTube\|Piped` | 播放成功；無 `LOGIN_REQUIRED`／「解析串流失敗」字樣；若 NewPipe 敗由 InnerTube/Piped 接手，記錄實際路徑 | ✅ adb + logcat |
| 5b | 切歌與快取 | 連續點播 3 首以上 | 切歌正常、無明顯延遲惡化；logcat 無重複解析同曲（TTL 快取生效） | ✅ adb + logcat |
| 6 | 迷你列控制 | 依序按隨機／上一首／暫停／下一首／循環 | 圖示狀態正確切換、行為符合預期 | ⚠️ 需人工 |
| 7 | 進度條 seek | 拖曳迷你列進度條 | 播放位置跳轉、時間文字更新 | ⚠️ 需人工 |
| 8 | 背景通知控制 | Home 鍵退到背景，操作通知按鈕 | 播放／暫停／前後曲有效；歌名正確；進度條存在 | ⚠️ 需人工 |
| 9 | 佇列播放 | 從播放清單點一首中間的歌 | 通知 next 可跳到清單下一首 | ⚠️ 需人工 |
| 10 | 播放清單 CRUD | 加入／移除／清空播放清單 | 列表即時更新、重啟 App 後保留 | ✅ adb |
| 11 | crash 監控 | 全程 `logcat -d` 收尾 | 無 FATAL EXCEPTION、無 ANR 記錄 | ✅ adb |
| 12 | 播放失敗 snackbar | 播放失敗情境（**E1 已失效**，改以實機斷網點卡片）下點擊結果卡片 | 數秒內底部出現 snackbar，以「播放失敗：」開頭並帶聚合原因；同一錯誤持續期間只彈一次（約 4 秒後消失不重彈）；再次點擊同曲重試失敗後 snackbar 應再次出現。⚠️ **2026-09-26 起此項需重驗**：`SnackbarHost` 原置於 `Scaffold` 內、被 `MiniPlayerBar`（`zIndex(2f)`）遮蔽，同一 `snackbarHostState` 承載的錯誤提示推測**長期不可見**；缺陷 D-1 修正時已一併移出並改為 `zIndex(3f)`，但本項尚未專項重驗 | ⚠️ 待重驗（2026-09-26 缺陷 D-1 修正後） |
| 13 | 搜尋續頁（載入更多） | 搜尋關鍵字 → 捲到底點「載入更多」連續 2~3 頁 → `adb logcat -d \| grep SearchPaging` | 每頁 `APPEND` 的 `dup=0`、`next` token 逐頁推進、無 `WARN token not advanced`、無 crash | ✅ adb + logcat |
| 14 | 搜尋清除回推薦／最近搜尋（✕／返回鍵） | 搜尋任一字 → ① 點搜尋框「✕」→ ② 再搜尋 → 按系統返回鍵（`input keyevent 4`）→ ③ 未搜尋狀態按返回鍵 | ① ✕ 清除後回到搜尋空狀態（顯示「最近搜尋」） ② 返回鍵清除搜尋回到空狀態且**不退出 App**（驗 `pidof`） ③ 未搜尋時返回鍵退出至桌面 | ✅ adb |
| 15 | 探索頁熱門榜單加入播放清單（＋） | 探索頁 rail／完整榜單某行點「＋」→ sheet 出現 → 選現有清單或「建立新播放清單」 → 命名建立 | 「＋」只開播放清單選擇 sheet、**不觸發播放**；加入成功後清單歌曲數增加；新建清單可命名並含該歌；點項目本體（非＋區）則正常觸發播放 | ✅ adb |
| 16 | 預覽時長 badge | ① 探索頁熱門榜單 rail 與完整榜單 ② 搜尋結果 ③ 歌單詳情頁（含新加入歌曲） | 三處縮圖右下角皆有時長 badge（如「3:45」）；同一清單內新加入歌曲有 badge；無 lengthText 的 premier/直播影片可無 badge | ✅ adb |
| 17 | 熱門榜單無名次顯示 | ① 探索頁 rail 縮圖左上角 ② 探索頁完整榜單每行行首 | ① rail 縮圖左上角**無**名次徽章；時長 badge（右下角）與「＋」仍顯示 ② 完整榜單行首**無**排序數字，縮圖／歌名／歌手／行尾「＋」仍顯示 ③ 點卡片仍從該曲起播整佇列、點「＋」仍開 sheet 不觸發播放 | ✅ adb |
| 18 | 底部導覽 3 tab | 啟動 App 觀察底部導覽列 | 顯示 **3 個 tab**：搜尋／探索／播放清單；點擊各自切換正常 | ✅ adb |
| 19 | 探索頁四區域榜單 | 切至「探索」tab 觀察 | 依序顯示 **台灣／西洋／日本／韓國**熱門音樂 rail，各含歌曲縮圖＋歌名＋歌手＋時長 badge＋「＋」；點「查看完整榜單」展開為垂直完整清單、頂部返回鈕可回 rail 視圖 | ✅ adb |
| 20 | 探索頁起播 | 點探索頁任一歌曲卡片 | 從該曲起播整佇列（MiniPlayerBar 出現、MediaSession state=BUFFERING→PLAYING、metadata 為所選歌）；不導航離開探索頁 | ✅ adb + logcat |
| 21 | 最近搜尋（歷史） | 搜尋 1~2 次 → 清除搜尋（✕／返回鍵）回空狀態 → 觀察「最近搜尋」區塊 → 點歷史項再搜尋 → 點「清除全部」 | 空狀態顯示「最近搜尋」＋「清除全部」，含剛才搜尋 query（最多 10 筆）；點歷史項可再次搜尋；「清除全部」後區塊消失回「輸入關鍵字開始搜尋」；DB `search_history` 表有對應列 | ✅ adb |
| 22 | DB v3→v4 遷移保留播放清單 | 升級前已有播放清單→升級後切至「播放清單」tab | 既有播放清單與歌曲**不流失**（`playlists`/`playlist_items` 表保留）；新增 `search_history` 表 | ✅ adb |
| 23 | 探索頁「為你推薦」 | ① 播放清單有 ≥1 首（加入 1~5 首）→ 切探索 tab 觀察熱門榜單上方　② 點「換一批」　③ 清空播放清單後重進探索頁 | ① rail 模式顯示「為你推薦」rail（種子相關歌曲，縮圖＋歌名；種子為空時顯示「加入歌曲開始推薦」引導）　② 「換一批」重新抓取（loading → 更新歌曲）不重複　③ 種子為空時僅顯示空狀態引導、熱門榜單正常 | ✅ adb |
| 24 | MiniPlayerBar 拖曳展開（顯示播放佇列） | 播放中由收斂態 MiniPlayerBar handle 區上拖（2026-09-24 環境：swipe 2620→1500） | 展開為 50% 高度 sheet，顯示「播放佇列」（目前項高亮）與「存為播放清單」；展開後收斂態控制列（隨機/上首/暫停/下首/循環）＋SeekBar 仍於 tab 之上 | ✅ adb + MCP（2026-09-24 cc32acb 達標） |
| 25 | MiniPlayerBar IconButton 展開 | 點收斂態控制列「展開播放佇列」icon button | 展開後佇列內容與拖曳展開一致（同一 sheet） | ✅ adb + MCP（2026-09-24 cc32acb 達標） |
| 26 | MiniPlayerBar 點遮罩收起 | 展開狀態點面板外遮罩（2026-09-24 環境：y≈700） | 收起回收斂態，無殘留遮罩 | ✅ adb + MCP（2026-09-24 cc32acb 達標） |
| 27 | MiniPlayerBar 下拖收起 | 展開狀態由面板頂部 handle 區下拖（2026-09-24 環境：1350→2650） | 收起回收斂態（LazyColumn 佇列區域內下拖觸發捲動而非收起屬 bottom sheet 慣例，非缺陷） | ✅ adb + MCP（2026-09-24 cc32acb 達標） |
| 28 | 重複 videoId 佇列的高亮定位 | 對**同一筆**搜尋結果按「加入」→「加入佇列尾端」×2（或 ×4）建立全同 `videoId` 佇列 → 起播 → 展開佇列 → 切到第 2 筆 → 再次展開 → 切到最後一筆後**移除其前面的一筆**；另需覆蓋 shuffle 開啟 | 高亮恆落在 `snapshot.currentMediaItemIndex` 指向的那一列，其餘列為透明背景＋灰色 ★。**重點：所有列 `videoId` 相同時高亮仍須正確**——舊實作 `indexOfFirst { it.videoId == }` 恆回傳 0，若測試中所有列 `videoId` 相同且結果高亮第 0 列，即為未修正。移除前項後 `currentMediaItemIndex` 應遞減且高亮跟隨 | ✅ adb + MCP + pixel probe（2026-09-26 `6cadd26` 達標） |
| 29 | 清空佇列不留殘留遮罩 | 展開佇列面板 → 按垃圾桶「清空佇列」 | sheet 與 scrim **立即**消失，內容不需點擊畫面即恢復可見可互動（accessibility tree 當下即恢復搜尋結果節點）；`dumpsys media_session` 為 `STOPPED(1)`、`queueTitle size=0` | ✅ adb + MCP + pixel probe（2026-09-26 `e1bb17f` 達標） |
| 30 | 存佇列為歌單：**順序保真** | 加入佇列尾端 ×5 首**不重複**（順序 A→E）→ 展開佇列 →「存為播放清單」→ 命名建立 → 進歌單詳情頁 | 歌單內順序**正好等於佇列順序**（非 `ORDER BY addedAt DESC` 造成的倒序）。**須以 accessibility tree 節點順序佐證，不只靠截圖** | ✅ adb + MCP（2026-09-26 `3201f85` 達標） |
| 31 | 存佇列為歌單：**重複曲目折疊但如實回報** | 對**同一首**按「加入」→「加入佇列尾端」×2 → 加入另一首 → 存為歌單 | **① snackbar 顯示「其中 N 首重複曲目已合併」且 N 正確**　**② 歌單筆數 = 佇列筆數 − N**　**③ 重複曲在歌單內僅一列** | ✅ adb + MCP（2026-09-26 `3201f85` 達標；缺陷 D-1 修正後複驗） |
| 32 | 存佇列為歌單：Dialog 防呆與互斥 | 展開佇列 →「存為播放清單」 | **①** 佇列面板**自動收起**且 Dialog 正常顯示（兩者都是全螢幕層級，不可同時可見）　**②** 空名稱「建立」disabled　**③** 輸入既有歌單名稱 → 紅框＋「此名稱已存在」＋「建立」disabled | ✅ adb + MCP（2026-09-26 `3201f85` 達標） |
| 33 | **snackbar 可見且不被 MiniPlayerBar 遮蔽** | 任一觸發 snackbar 的操作（存佇列為歌單／播放失敗） | snackbar **完整可見**，且播放中時**疊在 MiniPlayerBar 上方**、不遮蔽歌名與進度條。⚠️ **不可用 `android-mcp` 逐步驟捕捉**（呼叫往返延遲 > 4 秒顯示區間），須將操作與 `screencap` 放進**同一段 `adb shell input` 指令序列** | ✅ adb（2026-09-26 `3201f85` 缺陷 D-1 修復後達標） |

### 高亮類 UI 的驗證方法：pixel probe ＋ platform 交叉佐證

不要用「看起來有沒有高亮」的主觀描述。`QueueRow` 的高亮有**可量測契約**（`MiniPlayerBar.kt:341-380`）：

| 屬性 | 目前項 | 非目前項 |
|------|--------|----------|
| 背景 | `colorScheme.primary.copy(alpha = 0.1f)` | `Color.Transparent` |
| 前導 icon | `Icons.Filled.PlayArrow`（tint = `primary`） | `Icons.Filled.Star`（tint = `onSurfaceVariant`） |
| 標題文字色 | `colorScheme.primary` | `colorScheme.onSurface` |

三步驟：

1. **先讀碼取得契約** → 得知該量什麼色。
2. **pixel probe**：對 `adb exec-out screencap -p` 的 PNG，在**避開文字與 icon** 的位置取樣矩形、統計眾數色。
   - ⚠️ 截圖必須用 `cmd /c "adb exec-out screencap -p > out.png"`；PowerShell 的 `>` 會破壞 binary。
   - 先量一個**基準點**（如 sheet 空白處）當未高亮相，再與高亮列比對。
   - 淺色主題實測值（僅供對照，**每次須重新量測基準**）：sheet 底 `#ECEBF5`、高亮列 `#DCDDEA`、primary icon `#4C5E8B`、onSurfaceVariant icon `#5D5F68`、**有 scrim 時壓暗區 `#87888E`**。
3. **平台側交叉佐證**：`adb shell dumpsys media_session` 的 **`active item id=N`** 直接來自 Media3 `Player.currentMediaItemIndex`，與 App 自己的 UI 渲染無關。UI 高亮位置須與 `active item id` 一致，可排除單一量測管道誤判。

> **此方法的已知侷限**：靜態截圖**原理上抓不到單幀錯誤**。`currentMediaItemIndex` 與 `queue` 由不同事件觸發、可能相差一幀，若錯列僅持續數十毫秒，靜態量測無法察覺。要驗證單幀需 screenrecord 逐幀或 instrumentation 斷言。報告中只能宣稱「穩態正確」，**不可**宣稱「切換過程中無瞬間錯列」。

## 已知問題登記
<!-- 測試中發現但暫不修的問題，附 issue/PR 與報告連結 -->

- **[P1] 串流解析失敗時 UI 零回饋**（2026-08-26，commit `0ae95d7`）——✅ **已修復結案**（commit `df10e5c`）：App 內 snackbar 顯示「播放失敗：」＋聚合原因，one-shot 不重彈、重試會再提示；複測 R1–R4 全 PASS，證據見 `docs/qa/reports/2026-08-26-df10e5c-smoke-playback-error-snackbar.md`。常規驗證項目已納入本清單 #12。
- **[E1] 模擬器環境無法驗證串流解析**（2026-08-26，commit `0ae95d7`）——⚠️ **已失效，改寫為間歇性**（2026-09-26，commit `6cadd26` 複測）：
  - **原登記內容**（已不成立）：模擬器 NAT 出口 IP（資料中心網段）遭 YouTube 全面 bot 封鎖，NewPipe／InnerTube 皆回 LOGIN_REQUIRED、Piped 回 HTTP 525，播放**必定失敗**。
  - **2026-09-26 複測結果：播放成功。** 三次獨立起播皆 `state=PLAYING(3)`，且 `buffered position` 持續增長至 259111 ms（約 4 分 19 秒）——緩衝數分鐘不可能是假播放，證明實際下載了串流。期間 logcat 無任何 `LOGIN_REQUIRED`／HTTP 403／525。
  - **現行結論**：出口 IP 封鎖是**動態**的，時好時壞。**不得**因為本條已失效就假設模擬器無法播放——**每次涉及播放的測試仍須實測確認**，不可直接引用歷史結論。
  - 歷史上失敗的三輪紀錄（`0ae95d7`、`b7c7d09` 等）與升級裁決過程保留於 `docs/qa/reports/2026-08-26-*` 報告中，供對照當時環境。
  - **複測 `b7c7d09`（2026-08-26）**：HTTP client 隔離修正後端到端播放**仍 FAIL**，三層 fallback 全滅（NewPipe LOGIN_REQUIRED／InnerTube IOS+ANDROID_VR LOGIN_REQUIRED／Piped HTTP 403），錯誤型態與前兩輪一致。QA 診斷確認：主機出口 IP `139.162.98.189` 屬 Linode 資料中心網段且無系統代理（模擬器出口＝同一 IP），封鎖為 **per-IP 層級**，UA 隔離修正無法解；修正本身已落地（程式碼＋單元測試可證）。與 Tech Lead「本機 curl 可取得 plain URL」主張矛盾，已依規範升級 Owner 裁決。詳見 `docs/qa/reports/2026-08-26-b7c7d09-smoke-http-isolation-s1-playback.md`。
  - 播放**成功**路徑（MiniPlayerBar、背景續播、切歌 TTL 快取）現在**可以**在模擬器驗證了；需人工確認聲音的項目（清單 #4／#8）仍須實機或真人確認。
- **[E2] shuffle 預設為開啟（來源未確認）**（2026-09-26 實測，commit `6cadd26`）——⏳ **未確認現象**：App 啟動、MiniPlayerBar 首次出現時 shuffle 按鈕的 `contentDescription` 即為「關閉隨機播放」（代表已開啟），此時佇列為空、無任何使用者操作。點擊後才變為「開啟隨機播放」。可能是 (a) MediaSession／ExoPlayer 狀態跨 App 重啟殘留、(b) 刻意預設、(c) 未預期的初始化問題——**QA 未確認，不判定為缺陷**。影響：新手首次播放即為隨機順序，且會讓依賴「index 具決定性」的自動化測試不穩定（本次測試須手動關閉 shuffle）。待 Tech Lead 派工確認 `PlayerController` 初始化時 `shuffleModeEnabled` 來源。
- **[E3] 加入佇列尾端至空佇列會自動起播**（2026-09-26 實測，commit `6cadd26`）——⏳ **未確認行為**：對搜尋結果按「加入佇列尾端」且佇列原為空時，`state` 變為 `PLAYING(3)` 並自動播放第一筆。未確認是否為設計意圖，僅登記。

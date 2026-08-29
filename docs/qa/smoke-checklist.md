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
| 12 | 播放失敗 snackbar | 播放失敗情境（模擬器 IP 全封即固定重現；或實機斷網點卡片）下點擊結果卡片 | 數秒內底部出現 snackbar，以「播放失敗：」開頭並帶聚合原因；同一錯誤持續期間只彈一次（約 4 秒後消失不重彈）；再次點擊同曲重試失敗後 snackbar 應再次出現 | ✅ adb（截圖輪詢） |
| 13 | 搜尋續頁（載入更多） | 搜尋關鍵字 → 捲到底點「載入更多」連續 2~3 頁 → `adb logcat -d \| grep SearchPaging` | 每頁 `APPEND` 的 `dup=0`、`next` token 逐頁推進、無 `WARN token not advanced`、無 crash | ✅ adb + logcat |

## 已知問題登記
<!-- 測試中發現但暫不修的問題，附 issue/PR 與報告連結 -->

- **[P1] 串流解析失敗時 UI 零回饋**（2026-08-26，commit `0ae95d7`）——✅ **已修復結案**（commit `df10e5c`）：App 內 snackbar 顯示「播放失敗：」＋聚合原因，one-shot 不重彈、重試會再提示；複測 R1–R4 全 PASS，證據見 `docs/qa/reports/2026-08-26-df10e5c-smoke-playback-error-snackbar.md`。常規驗證項目已納入本清單 #12。
- **[E1] 模擬器環境無法驗證串流解析**（2026-08-26，commit `0ae95d7`）——⏳ **仍開放**：模擬器 NAT 出口 IP（資料中心網段）遭 YouTube 全面 bot 封鎖，NewPipe／InnerTube IOS/ANDROID_VR 皆回 LOGIN_REQUIRED、Piped 回 HTTP 525。播放**成功**路徑（MiniPlayerBar、背景續播、切歌 TTL 快取、成功時不出現 snackbar）仍需實機＋非資料中心網路複測（操作指引見 `2026-08-26-0ae95d7-smoke-direct-play-fallback.md`「需人工」節與 `2026-08-26-df10e5c` 報告同節）。
  - **複測 `b7c7d09`（2026-08-26）**：HTTP client 隔離修正後端到端播放**仍 FAIL**，三層 fallback 全滅（NewPipe LOGIN_REQUIRED／InnerTube IOS+ANDROID_VR LOGIN_REQUIRED／Piped HTTP 403），錯誤型態與前兩輪一致。QA 診斷確認：主機出口 IP `139.162.98.189` 屬 Linode 資料中心網段且無系統代理（模擬器出口＝同一 IP），封鎖為 **per-IP 層級**，UA 隔離修正無法解；修正本身已落地（程式碼＋單元測試可證）。與 Tech Lead「本機 curl 可取得 plain URL」主張矛盾，已依規範升級 Owner 裁決。詳見 `docs/qa/reports/2026-08-26-b7c7d09-smoke-http-isolation-s1-playback.md`。

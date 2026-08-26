---
name: newpipe-stream-resolver
description: NewPipe 串流解析與 StreamResolver 維護 SOP。處理 YouTube 改版導致解析失效、串流 URL 過期、切歌延遲、extractor 升級時使用。關鍵字：NewPipe、StreamResolver、ytInitialData、解析失敗、串流、extractor、搜尋失效。
---

# NewPipe 串流解析維護手冊（角色 B 專用）

## 解析管線（現況）

1. **搜尋**：`YoutubeSearchApi`（Retrofit 抓行動版搜尋頁 HTML）→ `YoutubeDataSource` 解析 `ytInitialData` JSON → `List<VideoResult>`
2. **播放**：`StreamResolver`（NewPipe Extractor）→ 音訊串流 URL
3. **播放器端**：MusicService 以 `ResolvingDataSource` 在載入當下**逐首**解析；已解析結果快取（決策理由見 TEAM.md §7）

## 失效診斷流程

YouTube 改版造成失效時，按序確認：

1. **定位層級**
   - 搜尋掛 → 抓一份新版搜尋頁 HTML，diff `ytInitialData` 的 JSON path 是否變了（改 `YoutubeDataSource`）
   - 播放掛 → 查 `gradle/libs.versions.toml` 的 NewPipe Extractor 版本是否落後 YouTube 目前佈署
2. **重現證據**：跑煙霧清單 #2（搜尋）、#4（背景音訊）；StreamResolver 錯誤訊息會顯示於媒體通知
3. **修復手段（由輕到重）**
   - URL 過期（暫停過久恢復失敗）：既有 job cancel/re-run 重解析機制應接住，先確認該路徑沒被改壞，不要另起爐灶
   - Extractor 落後 → 升級 `gradle/libs.versions.toml`。**此檔是 A 的治理目錄：版本異動須回報 A，走統一 PR**
   - `ytInitialData` 結構變化 → 只改 `YoutubeDataSource` 解析邏輯，不動 domain model 形狀

## 硬性邊界（失效時也不得越過）

- 不得為繞過解析問題把 `android.*` 依賴塞進 `core:domain`
- 不得把 Room Entity / Retrofit response 型別外洩給 UI 層
- 切歌延遲優化（如預解析下一首）屬行為變更：先回報 A 評估，勿直接改 ResolvingDataSource 策略

## 完成前檢查

- [ ] `./gradlew :core:data:test` 綠燈
- [ ] 提醒 A 安排實機煙霧測試（NewPipe 解析改動一律需要）
- [ ] 若風險對策變化，同步 `docs/ARCHITECTURE.md` §7 風險登記簿

# MyMediaPlayer 架構文件

> Stack：Kotlin 2.2 + Compose (BOM) + Hilt + Room + Retrofit/OkHttp + Media3 ExoPlayer/MediaSession + NewPipeExtractor
> Build：Gradle 9.4 / AGP 9.2（內建 Kotlin）/ JDK 21

---

## 1. 模組總覽

```
:app                 # 容器：Application、MainActivity、Navigation 圖、Manifest 聚合
├─ feature:search    # 搜尋頁（Screen + ViewModel）→ 依賴 feature:playlist（PlaylistPickerSheet）
├─ feature:playlist  # 播放清單（列表頁 + 詳情頁 + 共用 BottomSheet/Dialog）
├─ feature:player    # 播放頁 + MusicService（前景服務、背景音訊）→ 依賴 feature:playlist
├─ core:ui           # Material Theme、共用樣式（未來放共用 Composable）
├─ core:domain       # 純 Kotlin：Model、Repository interface、UseCase、PlayerController 介面（零 Android 依賴）
├─ core:data         # Room、Retrofit/OkHttp、NewPipe 解析、Repository 實作、Hilt DataModule
└─ core:common       # DispatcherProvider 等跨層小工具（純 Kotlin）
```

## 2. 依賴規則（單向，物理強制）

```
:app ──▶ :feature:search ──▶ :feature:playlist
    └──▶ :feature:player  ──▶ :feature:playlist     :feature:* ──▶ :core:ui
    └──▶ :feature:playlist                          :feature:* ──▶ :core:domain
                                                     :core:data  ──▶ :core:common
             :core:domain ◀──────────────────────── :core:data
             （data 實作 domain 的 interface）
```

| 規則 | 違反後果 |
|------|---------|
| feature 不得依賴 core:data | build.gradle.kts 沒有此依賴 → import 直接編譯失敗 |
| core:domain 不得 import android.* | 純 Kotlin module，無 Android classpath |
| UI 層不得 new Dao / Api / ExoPlayer | 只能注入 UseCase（Hilt graph 保證） |

## 3. 各層職責與關鍵類別

### core:domain（純 Kotlin）
- `core.domain.model.VideoResult` / `Playlist` / `PlaylistItem`：領域模型（無 Room/序列化標註）
- `core.domain.model.PlayerController`：播放控制介面（跨 feature 共用合約），ViewModel 只注入此介面
- `core.domain.model.PlaybackSnapshot` / `RepeatMode`：播放狀態快照與循環模式枚舉
- `core.domain.repository.VideoRepository` / `PlaylistRepository`：interface
- `core.domain.usecase.*`：SearchVideos、CreatePlaylist、RenamePlaylist、DeletePlaylist、ObservePlaylists、ObservePlaylistItems、AddToPlaylist、RemoveFromPlaylist、ClearPlaylist、ShufflePlayPlaylist
- 測試：`src/test/` 純 JVM 單元測試（Fake Repository）

### core:data
- `local.PlaylistEntity` / `PlaylistItemEntity`：Room Entity（持久化細節，不外洩）；與 Domain Model 互轉的 mapper 在同檔
- `local.AppDatabase` / `PlaylistDao`：Room（`PlaylistDao` 含播放清單 CRUD、項目觀察、隨機取曲、級聯刪除）
- `remote.YoutubeSearchApi`：Retrofit（行動版搜尋頁 HTML）
- `remote.YoutubeDataSource`：解析 `ytInitialData` JSON → List&lt;VideoResult&gt;
- `remote.stream.AudioStreamSource`：串流解析來源抽象（data 層內部型別），三個實作依優先序組成 fallback 鏈：
  - `NewPipeStreamSource`（主路徑）：NewPipe Extractor
  - `InnerTubeStreamSource`：直連 InnerTube player API（IOS → ANDROID_VR client，免 poToken；client 版本號為易腐常數）
  - `PipedStreamSource`：Piped 公開實例 `/streams/{id}`（最後手段）
- `remote.stream.FallbackStreamResolver`：依序嘗試來源、成功結果 TTL 快取、經 `StreamErrorClassifier` 分類錯誤並聚合可讀訊息
- `remote.NetworkModule`：HTTP client 依用途拆雙 profile（Hilt qualifier 定義於 `di.HttpProfileQualifiers`）：
  - `@BrowserProfile`：掛瀏覽器 UA／Referer／Cookie 攔截器（`YoutubeHeaderInterceptor`），僅供 Retrofit `YoutubeSearchApi` 抓行動版搜尋頁 HTML
  - `@StreamProfile`：乾淨 client（僅逾時設定、無任何攔截器），串流解析鏈專用——NewPipe extractor 的 `remote.OkHttpDownloader`、InnerTube/Piped 的 `stream.OkHttpStreamHttpTransport`
  - 教訓：瀏覽器 header 一旦覆蓋 InnerTube client 身份 UA 或 extractor 自帶 UA，串流解析即遭 LOGIN_REQUIRED，故兩 profile 嚴禁混用
- `repository.*Impl`：實作 domain interface（Entity ↔ Domain mapping）
- `di.DataModule`：Database / Dispatcher / Repository 三組綁定

### core:ui
- `core.ui.theme.MyMediaPlayerTheme` / Color / Type

### feature:search | playlist | player
- `*Route`（Hilt 容器）→ `*Screen`（無狀態 Composable）＋ `*ViewModel`（StateFlow + Intent）
- `feature:playlist` 提供共用 `PlaylistPickerSheet`（BottomSheet）與 `CreatePlaylistDialog`，供 search/player 共用（故 search/player 依賴 playlist）
- `feature.player.MiniPlayerBar`：前景常駐迷你播放列（隨機／前後曲／循環／歌名／進度條），由 app 層掛載於底部
- `feature.player.service.MusicService`：Media3 `MediaSessionService`
  - 播放佇列：點播曲目在播放清單中 → 整份清單從該曲起播；否則單曲（`playback.PlaybackQueueBuilder` 純函數，有單元測試）
  - 串流 URL 以 `ResolvingDataSource` 於載入當下逐首解析（NewPipe）
  - 通知由系統自動產生：歌名、進度條（可拖曳 seek）、播放/暫停/前後曲；隨機與循環為 custom layout 按鈕（custom command）
  - Service 的 Manifest 宣告在 feature 模組內（manifest merging 併入 app）；POST_NOTIFICATIONS 由 app 於啟動時動態請求

### :app
- `Routes` + NavHost；bottom bar（搜尋 / 播放清單）
- 播放清單導航：`playlist_list`（列表頁）→ `playlist_detail/{playlistId}`（詳情頁，含隨機播放）
- 權限宣告、Application (`@HiltAndroidApp`)

## 4. 資料流

```
UI Intent ──▶ ViewModel.onIntent ──▶ UseCase ──▶ Repository(interface)
                                                    │
   UiState ◀── StateFlow ◀── ViewModel ◀── Flow ◀──┤
                                                    ├─▶ Room（playlist 表）
                                                    └─▶ Retrofit/NewPipe（YouTube）
播放：PlayerViewModel ──▶ PlayerController(core:domain) ──▶ MediaController ──▶ MusicService(MediaSession) ──▶ ExoPlayer
      （PlayerController 介面在 core:domain，實作 MediaControllerPlayerController 在 feature:player）
      （前景 MiniPlayerBar 與背景通知共用同一 MediaSession 狀態源）
```

## 5. 新功能落地路徑（SOP）

1. **新頁面**：建 `feature:xxx` 模組（複製任一 feature 的 build.gradle.kts）→ `settings.gradle.kts` include（A 操作）→ app NavHost 加 route。
2. **新資料來源**：remote 加 DataSource → repository impl + domain interface → DataModule 綁定 → UseCase 包裝。
3. **新資料表**：Entity + Dao 於 `core/data/local` → AppDatabase version++ → Entity↔Domain mapper。
4. **共用 UI 元件**：先放所屬 feature；第二個地方要用時才升級到 `core:ui`（Rule of Three 從寬）。

## 6. 建置指令

```bash
./gradlew assembleDebug          # 全模組編譯
./gradlew test                   # 所有單元測試（含 core:domain 純 JVM 測試）
./gradlew :app:assembleDebug     # 只編 app 及其依賴
./gradlew :core:domain:test      # 只跑 domain 測試
```

本機若 PATH 的 java 是 8：以 JDK 21 執行（Android Studio JBR 或 ~/.jdks）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
```

## 7. 風險登記簿

| 風險 | 影響 | 對策 | Owner |
|------|------|------|-------|
| YouTube 改版使 ytInitialData / NewPipe 失效；匿名 IP 遭 bot 偵測封鎖（LOGIN_REQUIRED「Sign in to confirm you're not a bot」） | 搜尋、播放全掛 | StreamResolver 改多層 fallback：NewPipe → InnerTube 直連（IOS／ANDROID_VR client，免 poToken）→ Piped 實例，成功結果 TTL 快取；錯誤分類聚合顯示於通知。extractor 升級由 A 走統一 PR；poToken/BotGuard WebView 方案成本高暫緩（見 TEAM.md §7） | B |
| Foreground Service 政策（API 34+） | 上架審查 / 背景 被殺 | 已宣告 `foregroundServiceType=mediaPlayback`；未來接 MediaSessionService | B |
| 串流 URL 有時效性 | 暫停過久後恢復失敗 | 失敗時重新 resolve（MusicService 已有 job cancel/re-run 機制） | B |
| WebView 播放器與音訊服務同時發聲 | 使用者困惑 | Roadmap：以 ExoPlayer 畫面取代 WebView | C+B |
| 通知權限（Android 13+）未授予 | 背景播放時通知不出現（音訊不受影響） | App 啟動時動態請求 POST_NOTIFICATIONS；拒絕僅影響通知與鎖屏控制 | C |
| 逐首解析串流 URL 的切歌延遲 | 下一首開始前有解析等待（NewPipe 網路往返） | ResolvingDataSource 快取已解析結果；buffering 狀態由系統 UI 呈現；必要時改預先解析下一首 | B |

## 8. 歷史決策

- 2026-08：由 single-module `:app` 遷移至 8-module 架構；遷移順序 domain → data → ui → features → app 瘦身，每步 CI 綠燈。

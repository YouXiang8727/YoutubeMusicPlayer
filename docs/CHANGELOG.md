# Changelog（異動紀錄）

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)；
版本語意參考 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

> **維護規則**：所有 merge 進 `master` 的 PR，必須在 `[Unreleased]` 區塊加一筆，
> 類別對應 Conventional Commits（`feat` → Added、`fix` → Fixed、`refactor`/`perf` → Changed、移除 → Removed、文件 → Docs）。
> 發版時由 Tech Lead 把 `[Unreleased]` 改為版本號 + 日期。

## [Unreleased]

### Fixed
- 循環模式預設值修正：ExoPlayer 預設 `repeatMode` 改為 `REPEAT_MODE_ALL`（清單循環），與 UI 圖示一致；原預設 `REPEAT_MODE_OFF` 被映射為 `ALL` 導致 UI 顯示循環但實際播放完畢停止
- MiniPlayerBar 隨機播放與循環模式切換時顯示 Toast 回饋，並以單一 Toast reference + `cancel()` 避免頻繁切換時 Toast 堆疊

## [1.0.0] - 2026-08

### Added
- 多模組架構：`:app`、`:feature:search`、`:feature:playlist`、`:feature:player`、`:core:ui`、`:core:domain`、`:core:data`、`:core:common`
- 搜尋頁：YouTube 行動版搜尋 HTML 解析（`ytInitialData` JSON）→ 影片清單
- 播放清單頁：Room 持久化，增刪與清空
- 播放頁 + `MusicService`：Foreground Service 音訊播放（NewPipe StreamResolver → ExoPlayer）、通知列控制
- 測試：`core:domain` UseCase 純 JVM 單元測試、`core:data` Repository 對 Fake Dao 測試

### Changed
- 由 single-module `:app` 重構為 multi-module 架構，拆分順序 domain → data → ui → features → app，每步 CI 綠燈
- PlaylistItem 拆成 Domain Model + Room Entity：讓 `core:domain` 保持零 Android 依賴，Room 細節封死在 `core:data`
- 移除 VideoResult 的 @Serializable：全專案無序列化使用點，domain 不需要 serialization plugin

[Unreleased]: https://github.com/lgroupdavid.hs/YoutubeMusicPlayer/compare/v1.0.0...HEAD

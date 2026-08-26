---
name: compose-ui-conventions
description: Compose UI 落地慣例與新頁面 SOP。開發 Screen、ViewModel、UiState、Compose Preview、MiniPlayerBar、新增 feature 模組時使用。關鍵字：Screen、ViewModel、UiState、Preview、feature 模組、Navigation、MiniPlayerBar、注入規則。
---

# Compose UI 落地慣例（角色 C 專用）

## 頁面三件套（固定模板）

`XxxRoute`（Hilt 容器，`hiltViewModel()`）→ `XxxScreen`（**無狀態** Composable，狀態全走參數）→ `XxxViewModel`（`StateFlow<XxxUiState>` + `onIntent(intent)`）

## ViewModel 注入白名單

| 准 | 禁 |
|----|----|
| UseCase | Dao、Retrofit Api service |
| Repository interface | ExoPlayer / MediaController 實作類別 |
| `PlayerController` 介面（播放頁唯一入口） | Room Entity |

- 資料流：Repository Flow → `stateIn(viewModelScope)` → UiState；UI 邏輯放 ViewModel 保持可測

## 強制項目

1. 新 Screen 必須附 `@Preview`（至少含深色主題一組）——視覺回歸最低門檻
2. feature 內新增資源必須加前綴（例：`ic_music_notification.xml`）；App 名稱等共用字串以 app module 為準（同名資源會被 app 覆蓋）
3. 共用元件先放所屬 feature，第二個地方要用時才升級到 `core:ui`

## 新頁面 SOP（注意跨層步驟）

1. 建立 `feature:xxx` module（複製任一 feature 的 build.gradle.kts）
2. `settings.gradle.kts` include → **A 的操作**：回報時列出需求，由 A 執行
3. app 的 NavHost 加 route → 同上，**A 的操作**（app 是容器層）
4. Module 內的 Screen / ViewModel / navigation 完整實作才是你的交付物

## 播放頁特殊規則

- 只透過 `PlayerController` 介面（命令 + `StateFlow<PlaybackSnapshot>`）控制播放，背後是 MediaController 連線至 MediaSession
- `service/` 套件是 B 的禁區；前景 MiniPlayerBar 元件本體在 feature:player（你管），但**掛載點在 app 層**（A 管），搬家需經 A
- POST_NOTIFICATIONS 權限由 app 啟動時動態請求，Screen 不重複實作權限 UI

## 完成前檢查

- [ ] `./gradlew :feature:<module>:assembleDebug` 綠燈
- [ ] UiState 分支邏輯有單元測試；新 Screen 附 Preview
- [ ] 文件同步依 TEAM.md §4（架構/CHANGELOG）

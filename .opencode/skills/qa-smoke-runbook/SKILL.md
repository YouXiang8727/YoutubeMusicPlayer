---
name: qa-smoke-runbook
description: QA 煙霧測試與回歸驗證 runbook。執行 adb 安裝啟動、logcat crash 監控、煙霧清單逐項驗證、撰寫測試報告時使用。關鍵字：煙霧測試、smoke test、adb、logcat、crash、ANR、回歸、測試報告、emulator。
---

# QA 煙霧測試 Runbook（角色 D 專用）

## 標準作業序列

```bash
# 0. 環境記錄（每份報告必填）
git rev-parse --short HEAD
adb devices && adb shell getprop ro.build.version.release

# 1. 建置（唯讀使用建置系統，不改任何設定）
./gradlew :app:assembleDebug
./gradlew test

# 2. 安裝並啟動
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity

# 3. 收 logcat 證據（操作後立即執行）
adb logcat -d -b crash
adb logcat -d | grep -E "FATAL EXCEPTION|ANR in |AndroidRuntime"
```

## 模擬器操控（ADB UI 自動化）

### 基本互動命令

```bash
# 點擊座標 (x, y)
adb shell input tap <x> <y>

# 滑動 (x1, y1) → (x2, y2)，持續時間 ms
adb shell input swipe <x1> <y1> <x2> <y2> <duration_ms>

# 輸入文字（僅限英文數字）
adb shell input text "hello"

# 按鍵事件
adb shell input keyevent <keycode>
# 常用 keycode:
#   3 = HOME, 4 = BACK, 26 = POWER, 82 = MENU
#   24 = VOLUME_UP, 25 = VOLUME_DOWN
#   187 = APP_SWITCH (最近使用的應用)

# 長按 (x, y) 500ms
adb shell input swipe <x> <y> <x> <y> 500
```

### 截圖與 UI Dump

```bash
# 截圖到本地
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./screenshot.png

# Dump UI 結構（用於定位元素座標）
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml ./ui.xml
# 解析 ui.xml 找到目標元素的 bounds 座標
```

### 常用測試座標（MyMediaPlayer App，1080x2400 螢幕）

```
# 底部 MiniPlayerBar 區域（約 y=2300）
# 搜尋結果卡片（約 y=400~1200，依列表位置）
# 隨機按鈕：MiniPlayerBar 左側 (x=100, y=2300)
# 播放/暫停按鈕：MiniPlayerBar 中間 (x=540, y=2300)
# 下一首按鈕：MiniPlayerBar 右側 (x=980, y=2300)
```

### 自動化測試腳本範例

```bash
# 1. 啟動 App
adb shell am start -n com.youxiang8727.mymediaplayer/.MainActivity
sleep 2

# 2. 點擊搜尋欄位（假設在頂部 y=150）
adb shell input tap 540 150
sleep 1

# 3. 輸入搜尋關鍵字
adb shell input text "test"
sleep 1

# 4. 按鍵盤搜尋鍵
adb shell input keyevent 66  # ENTER
sleep 3

# 5. 點擊第一個搜尋結果
adb shell input tap 540 400
sleep 2

# 6. 檢查播放狀態
adb shell dumpsys media_session | grep -A 5 "state="
```

## 逐項驗證規則

- 清單來源：`docs/qa/smoke-checklist.md`——**逐項執行，不抽樣**
- 標 ⚠️ 需人工的項目（聲音確認、通知圖示切換等）：明確標註「需人工」，並附完整操作步驟讓 Owner 可自行複核
- 每個 FAIL 附：重現步驟、logcat 原始片段（stack trace 不截斷）、預期 vs 實際

## 報告產出

- 路徑：`docs/qa/reports/YYYY-MM-DD-<shorthash>.md`（shorthash = 受測 commit）
- 格式：環境區塊 → 逐項 PASS/FAIL 表 → FAIL 證據 → 「需人工」清單與指引
- 你的 FAIL 判定與開發者 PASS 主張衝突時：**以你的實測證據為準**，回報 A 升級 Owner 裁決

## 邊界提醒

- **只驗證不開發**：`core/**`、`feature/**`、`app/**` 一律不可編輯（agent 權限已物理擋下）
- 發現 bug → 記錄證據 → 回報 A 派回開發角色修正；你不自行改碼、不自行 merge
- 測試中發現但暫不修的問題，登記到 smoke-checklist 的「已知問題登記」區（附 issue/PR 連結）
- 新功能 merge 後，把對應驗證項目補進 smoke-checklist（你的文件維護義務）

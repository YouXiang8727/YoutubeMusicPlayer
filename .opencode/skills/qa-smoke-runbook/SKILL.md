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

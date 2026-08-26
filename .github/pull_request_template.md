## 變更摘要
<!-- 一句話說明做了什麼、為什麼 -->

## 變更類型
- [ ] feat　[ ] fix　[ ] refactor　[ ] perf　[ ] docs　[ ] chore

## 影響模組
<!-- 例：feature/search、core/domain -->

## 自我檢查（R 填寫）
- [ ] `./gradlew test` 綠燈；`core:domain` UseCase 異動有對應單元測試
- [ ] 依賴方向符合規範：feature 未依賴 `core:data`；`core:domain` 無任何 `android.*` import
- [ ] ViewModel 只注入 UseCase / Repository interface

## 文件同步（Code Changes ⇒ Docs Changes）
- [ ] **一律需要**：`docs/CHANGELOG.md` 的 `[Unreleased]` 已加一筆
- [ ] 架構性異動已同步 `docs/ARCHITECTURE.md`：
  - [ ] module / 依賴方向變更 → §1 §2
  - [ ] domain model / interface / UseCase 變更 → §3
  - [ ] 第三方庫變更 → 開頭 Stack 區
  - [ ] 新風險或對策變化 → §7 風險登記簿
  - [ ] 重大取捨 → `docs/TEAM.md` §7

> 未勾選文件同步者，Approver 不得 Approve。

## 截圖 / Demo
<!-- UI 變更請附截圖或錄影 -->

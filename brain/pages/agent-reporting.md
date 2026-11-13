---
id: agent-reporting
title: "Agent 汇报方式：notify-main"
category: reference
status: active
tags: [agent, ops]
created: "2026-11-13T17:16:34"
updated: "2026-11-13T17:16:34"
---

<!-- compiled_truth -->
## 规则（2026-11-13 起）

- 干完一轮 / 卡住 / 需要人决定时，在项目 cwd 执行：
  `/home/gem/project/chat/scripts/notify-main <done|blocked|ask> "<一句话>"`
- 直接把话推到主 agent（小奎）的输入里，不轮询、不写文件、不等对方来问。
- **只发三种**：`done`（完成）、`blocked`（卡住）、`ask`（需要人决定）。进展流水账不要发。
- 代价：每次通知会唤醒主 agent 一轮完整对话（一次请求），所以只在真的需要时才发。
- 脚本通过 `herdr agent prompt` 推送到主 pane；主 pane 解析顺序 `$MAIN_PANE` → `~/.config/chat/main-pane` → 自动找 cwd 为 `/home/gem/project/chat` 的 pane。
- 可用 `DRY_RUN=1` 预演。


## Timeline

- time: 2026-11-13T17:16:34
  kind: decision
  summary: "Created this page: Agent 汇报方式：notify-main"
  source: user 2026-11-13
  affects: [agent-reporting]

- time: 2026-11-13T17:16:34
  kind: decision
  summary: "干完一轮/卡住/需人决定时用 notify-main 推给主 agent，只发 done|blocked|ask"
  source: user 2026-11-13
  affects: [agent-reporting]

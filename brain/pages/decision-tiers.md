---
id: decision-tiers
title: "决策权限分级：Tier 1/2/3"
category: reference
status: active
tags: [agent, ops, governance]
created: "2026-11-13T23:22:12"
updated: "2026-11-13T23:22:44"
---

<!-- compiled_truth -->
## 规则（来自 chat/AGENTS.md「决策权限分级」，2026-11-13 生效）

agent 做事前先判级，判级顺序：

1. **不能 git revert**（发版/force-push/删数据/动 master 或发布线/改网关或密钥）→ **Tier 3**
2. **不在 brain 已批准方向** / 跳到新 pattern / 跨项目影响 → **Tier 3**
3. **碰 runtime 语义或扩已批准方向 / 加原语** → **Tier 2**
4. 否则 → **Tier 1**

### Tier 1（可逆、范围内、已 verify）
自己做，做完 `scripts/notify-main done "<一句话>"`。
例：DCE、明确 bug 修复、跑测量、改本仓代码。

### Tier 2（改 runtime 语义 / 扩已批准方向 / 加原语）
自己做，但**先**记一条再动手：
```
scripts/decision add <项目> "<选择>" --evidence "<证据>" --reversible --tier 2
```
然后 `notify done`。不等主 agent 确认。

### Tier 3（不可逆 / 出圈）
**先**记一条待批：
```
scripts/decision add <项目> "<选择>" --evidence "<证据>" --pending --tier 3
```
然后 `notify blocked`，等主 agent 一字确认才动手。

## 与汇报规则的关系
- 汇报触发（done/blocked/ask）见 `agent-reporting.md`。
- Tier 决定的是「做不做 / 要不要先记决策」，汇报规则决定「何时推消息给主 agent」。
- Tier 2 的 `scripts/decision add` 是动作前置条件，不是汇报替代。


## Timeline

- time: 2026-11-13T23:22:12
  kind: decision
  summary: "Created this page: 决策权限分级：Tier 1/2/3"
  source: "chat/AGENTS.md 决策权限分级 2026-11-13"
  affects: [decision-tiers]

- time: 2026-11-13T23:22:44
  kind: decision
  summary: "agent 自治决策按可逆性/范围分 Tier 1/2/3，先记后做或先记待批"
  source: "chat/AGENTS.md 决策权限分级 2026-11-13"
  affects: [decision-tiers]

- time: 2026-11-13T23:22:44
  kind: decision
  summary: "写入决策权限分级 Tier 1/2/3：判级顺序 不能revert→T3、不在brain方向/跨项目→T3、碰runtime语义/扩面→T2、否则T1；T2 先 scripts/decision add --reversible 再做，T3 先 --pending 再 notify blocked 等确认"
  source: chat/AGENTS.md 2026-11-13
  affects: [decision-tiers]

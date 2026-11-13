---
id: launcher-layout-protection
title: "桌面布局保护：越界图标不删 + 快照补回"
category: decision
status: active
tags: [launcher3, xposed, layout-guard]
created: "2026-10-24T13:52:49"
updated: "2026-10-24T16:38:17"
---

<!-- compiled_truth -->
## 结论

模块的 6 列布局依赖运行时 hook；模块不在时，原厂桌面按 5×4 加载，会把第 6 列和 Dock 第 6 格的条目从 `launcher_5_by_4.db` 永久删除。为保住这些条目，采用两层保护。保护没有独立开关，只随桌面 6 列开关（`workspace_6_columns`）启用；开关关闭时完全退回原厂行为。恢复时会写入桌面数据库，但不改 APK、系统分区。

## 数据丢失根因

- `InvariantDeviceProfile` 没有 6 列配置，只有 4×4 / 5×4 / 4×3；Partner 资源要求系统应用，RRO 需要系统安装，均不可用。
- `LoaderCursor.checkItemPlacement` 判定工作区 `cellX + spanX > numColumns` 或 Dock `screenId >= numDatabaseHotseatIcons` 为越界，`checkAndAddItem` 调 `markDeleted("Item position overlap", "overlapping_item")`。
- `commitDeleted` 通过 `ModelDbController.delete` 删除 `favorites` 行，属于永久删除。

## 第一层：不删

- hook `LoaderCursor.markDeleted(String, String)`，仅当第二个参数为 `overlapping_item` 时继续判断。
- 从 `LoaderCursor` 反射 `mIDP`，读取 `numColumns`、`numRows`、`numDatabaseHotseatIcons`，并按当前行 `container/screen/cellX/cellY/spanX/spanY` 重算是否越界；越界则跳过原方法。
- 行留在数据库中但不会进入内存模型，因此 5 列下不可见；6 列恢复后正常加载。
- 该拦截只在 6 列开关开启时生效；开关关闭时按原厂逻辑删除。
- 不拦截用户删除、用户配置删除等其它 `markDeleted` 调用。

## 第二层：快照与补回

- 快照文件：桌面进程 `getNoBackupFilesDir()/titan2_layout_backup.db`，与实时库分离。
- 触发：hook `ModelDbController.insert/update/delete`，有实际变更后延迟 1.5 秒复制 `favorites`；删除也会触发，用户主动删掉的图标不会在快照里复生。
- 6 列开关开启但网格 hook 没有生效、桌面仍以原厂 5 列运行时，快照会把旧备份中“当前库缺失且按原厂网格越界”的行合并保留，避免一次快照抹掉等待恢复的图标。
- 恢复时机：`InvariantDeviceProfile` 网格初始化、`LoaderTask` 读取之前；在把五个字段强制为 6 之前读取原厂 `numColumns` / `numRows` / `numDatabaseHotseatIcons`。
- 只补“当前库缺失、且按原厂网格越界”的行；在原厂网格内缺失的行视为用户删除，不恢复。
- 原位置空闲则放回原位；被占用或 Dock 满格则放到新一页的空位。保留原 `_id`，文件夹子项随之重新归组；控件尺寸超出网格时收缩。
- 仅在桌面主进程执行；`workspace_6_columns` 关闭时不恢复、不快照、不拦截删除。
- 所有 DB 操作包在单一后台线程与共享锁内，失败只记日志，退回原厂行为。

## 边界

- 备份不随系统备份迁移；清除桌面数据后丢失。
- 快照有约 1.5 秒窗口；首次开启保护后需发生一次桌面数据库写入才会生成备份。
- 原位置被占用时图标会出现在新的一页，不保证原页码。
- 该机制只覆盖 `favorites` 中的布局行，不处理网格迁移、文件夹内部列数。

## 相关

- [[launcher-grid-hook]]
- [[cross-process-feature-switches]]


## Timeline

- time: 2026-10-24T13:52:49
  kind: decision
  summary: "Created this page: 桌面布局保护：越界图标不删 + 快照补回"
  source: "Launcher3 固件反编译 + 用户需求"
  affects: [launcher-layout-protection]

- time: 2026-10-24T13:52:59
  kind: decision
  summary: "固化越界保留 + 快照补回的双层机制"
  source: LayoutGuard.java / README.md
  affects: [launcher-layout-protection]

- time: 2026-10-24T13:52:59
  kind: decision
  summary: "采用完整保护：越界行不删 + 独立快照 + 重新激活时补回"
  source: "用户选择完整保护方案"
  affects: [launcher-layout-protection]

- time: 2026-10-24T16:38:17
  kind: decision
  summary: Rewrote compiled_truth to the new best understanding
  source: "LayoutGuard.java / MainActivity.java / 用户决定"
  affects: [launcher-layout-protection]

- time: 2026-10-24T16:38:17
  kind: reversal
  summary: "取消独立 layout_guard 开关：布局保护改为只随 workspace_6_columns 启用，关闭 6 列即完全退回原厂行为"
  source: "用户要求"
  affects: [launcher-layout-protection]

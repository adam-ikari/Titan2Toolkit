---
id: launcher-grid-hook
title: "桌面 6 列通过运行时 hook InvariantDeviceProfile 实现"
category: decision
status: active
tags: [launcher3, xposed, grid]
created: "2026-10-23T10:32:27"
updated: "2026-10-23T11:56:25"
---

<!-- compiled_truth -->
## 结论

桌面 6 列通过运行时 hook `InvariantDeviceProfile` 的网格初始化实现，同时作用于工作区、Dock（Hotseat）和应用抽屉（All Apps）；不修改 APK、数据库或系统分区。

## 实现

- 在 `onPackageReady` 中判断包名是否命中 `Targets.LAUNCHERS`，再用桌面的 `ClassLoader` 反射查找 `InvariantDeviceProfile`。
- 一次性查找并修改五个字段：`numColumns`、`numShownHotseatIcons`、`numDatabaseHotseatIcons`、`numAllAppsColumns`、`numDatabaseAllAppsColumns`；缺失的字段跳过并记日志。
- Android 14/15：hook `applyPartnerDeviceProfileOverrides(Context, DisplayMetrics)`，在 `proceed()` 返回后改字段。
- Android 16：hook `newDPBuilder(Context, Info)`，在调用前改字段，保证随后构建的 `DeviceProfile` 拷贝到 6 列。
- 两个锚点都找不到时，回退到 hook `InvariantDeviceProfile` 的所有构造函数。
- 反射按类名、方法名和参数类型查找，并沿父类回溯，以容忍固件间的差异。

## 边界

- 工作区不做网格迁移：原有图标留在原单元格，新增的第 5、6 列留空；Dock 与应用抽屉由 Launcher 自动重排。
- 文件夹、最近任务等其它网格不在范围内。
- 所有图标会随列数增加而变小。
- `DeviceProfile` 在可用宽度不足时仍可能自行减少 `numShownHotseatIcons`（固件逻辑，模块不覆盖）。
- 卸载模块或关闭作用域后，桌面进程重建配置即恢复默认。


## Timeline

- time: 2026-10-23T10:32:27
  kind: decision
  summary: "Created this page: 桌面 6 列通过运行时 hook InvariantDeviceProfile 实现"
  source: "模块源码与固件分析"
  affects: [launcher-grid-hook]

- time: 2026-10-23T10:32:27
  kind: decision
  summary: "固化 6 列 hook 方案与边界"
  source: ModuleMain.java / README.md
  affects: [launcher-grid-hook]

- time: 2026-10-23T11:56:25
  kind: decision
  summary: "扩展到工作区、Dock、应用抽屉三处 6 列"
  source: "ModuleMain.java / README.md / 真机日志"
  affects: [launcher-grid-hook]

- time: 2026-10-23T11:56:25
  kind: decision
  summary: "把 6 列扩展到 Dock 与 All Apps：同一 hook 内设置 numShownHotseatIcons / numDatabaseHotseatIcons / numAllAppsColumns / numDatabaseAllAppsColumns"
  source: "用户真机反馈"
  affects: [launcher-grid-hook]

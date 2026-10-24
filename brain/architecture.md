---
slug: architecture
title: System architecture
role: system architecture
updated: "2026-10-24T13:53:17"
---

# System architecture

## Overview

单模块 Xposed 工程，代码全部运行在目标进程内：

- `ModuleMain`：libxposed 入口。收到 `onPackageReady` 后判断包名，分别向桌面进程和 system_server 安装 hook。
- `Targets`：目标包名白名单（`com.android.launcher3`、`android`），与 `META-INF/xposed/scope.list` 保持一致。
- `MainActivity`：功能开关与说明页；开关写入模块私有 SharedPreferences。
- `ConfigProvider`：只读导出开关；`RemoteConfig` 供 hook 进程读取并缓存。
- `LayoutGuard`：桌面进程内的布局保护。拦截越界条目的删除，维护独立快照，并在重新激活时补回缺失条目。
- `ShortcutHooks`：system_server 侧 hook，扩展 OEM 字母键快捷键的生效范围。
- `SystemServer`：system_server 进程内的系统 Context、屏幕状态与输入法可见性。

除布局保护在恢复时写入桌面自己的数据库外，hook 只改进程内存；模块不修改 APK 或系统分区。

## Module graph

```mermaid
graph TD
  X[Vector / LSPosed] --> M[ModuleMain.onPackageReady]
  M --> T{包名命中 Targets?}
  T -->|否| N[忽略]
  T -->|桌面| G[installLauncherGrid]
  T -->|系统框架| H[ShortcutHooks.install]

  G --> C[按 ClassLoader 查找 InvariantDeviceProfile / Info]
  G --> LG[LayoutGuard.install]
  C --> A15[hook applyPartnerDeviceProfileOverrides]
  C --> A16[hook newDPBuilder]
  C -.兜底.-> CT[hook 构造函数]
  A15 --> R[LayoutGuard.beforeGridOverride]
  A16 --> R
  CT --> R
  R --> F[读取开关后改五个网格字段]
  F --> D[DeviceProfile 按 6 列构建工作区 / Dock / All Apps]

  LG --> MD[hook LoaderCursor.markDeleted]
  LG --> DW[hook ModelDbController.insert/update/delete]
  MD -->|越界且保护开启| KEEP[跳过删除，行留在库中]
  DW --> SNAP[延迟复制 favorites 到 titan2_layout_backup.db]
  R -->|6 列开启| RESTORE[对比快照，补回缺失的越界行]

  H --> I[hook AguiKeyboardShortcut.isHomePackage]
  I --> K[读取 RemoteConfig 缓存]
  K --> SD[SystemServer: 屏幕点亮 + 输入法不可见]
  I --> O[OEM ShortcutInterceptKey 调度已配置字母]
  O --> AK[com.agui.keyboard/.ExecuteShortcutFunc]

  U[MainActivity 开关] --> P[(SharedPreferences)]
  P --> CP[ConfigProvider]
  CP -. ContentResolver.call .-> RC[RemoteConfig]
  RC --> F
  RC --> K
  RC --> R
  RC --> MD
```

## Constraints

- 编译期只依赖 `io.github.libxposed:api:101.0.1`（compileOnly），运行时由框架提供。
- 模块描述文件必须位于 `META-INF/xposed/`：`module.prop`、`java_init.list`、`scope.list`。
- 目标类名和方法签名随固件版本变化，查找必须在目标 `ClassLoader` 内完成，并为不同 Android 版本保留多个锚点。
- `minSdk=31`，`compileSdk` / `targetSdk=36`；不使用 AndroidX。
- 布局保护只在桌面主进程写数据库；快照与实时库分离，所有访问串行化，失败只记日志。

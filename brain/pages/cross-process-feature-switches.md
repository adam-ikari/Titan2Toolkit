---
id: cross-process-feature-switches
title: "功能开关通过只读 ContentProvider 跨进程共享"
category: decision
status: active
tags: [config, provider, xposed]
created: "2026-10-23T10:52:47"
updated: "2026-10-23T11:40:41"
---

<!-- compiled_truth -->
## 结论

模块 UI 与 hook 进程之间的功能开关通过模块应用导出的只读 ContentProvider 共享，不使用 XSharedPreferences 或 Settings.Global。桌面同步读取，system_server 只读缓存并异步刷新，保证输入分发线程不做 binder 调用。

## 实现

- UI 写入模块私有 SharedPreferences（`features`）。
- `ConfigProvider`（authority `com.titan2.toolkit.config`，exported，只读）在 `call("get")` 时返回 `workspace_6_columns` 与 `global_letter_shortcuts` 两个布尔值。
- `RemoteConfig.workspace6Columns(context)`：供桌面网格初始化使用，同步读取，缓存 2 秒。
- `RemoteConfig.globalLetterShortcuts()`：供 system_server 的按键 hook 使用，只读 volatile 缓存；首次读取成功前保持关闭；之后最多每 5 秒异步 `warmUp` 一次，切换开关几秒内生效，按键线程不阻塞。
- 读取失败保留上次成功值；从未成功则保持默认/关闭。

## 默认值

- 桌面 6 列默认开启。
- 字母键全局快捷启动默认开启。

## 为什么

- libxposed API 101 没有偏好设置 API；`XSharedPreferences` 依赖框架私有实现；`Settings.Global` 写入需要额外权限或 root。
- 普通应用的私有 prefs 对桌面 / system_server 不可读，ContentProvider 是稳定的跨进程边界。
- 开关本身不敏感，导出只读 Provider 可以接受。

## 边界

- Provider 进程被冻结或模块应用被卸载时，hook 退回上次缓存值；system_server 从未读取成功时保持原厂行为。
- 桌面 6 列的开关变化需要桌面重新初始化（重启桌面）才生效；字母键开关不需要重启。
- 新增功能时必须同时更新 `FeatureFlags`、`ConfigProvider`、`MainActivity` 和 `RemoteConfig`，保持键名与默认值一致。


## Timeline

- time: 2026-10-23T10:52:47
  kind: decision
  summary: "Created this page: 功能开关通过只读 ContentProvider 跨进程共享"
  source: "本次实现"
  affects: [cross-process-feature-switches]

- time: 2026-10-23T10:52:48
  kind: decision
  summary: "确定开关共享方案"
  source: FeatureFlags/ConfigProvider/RemoteConfig
  affects: [cross-process-feature-switches]

- time: 2026-10-23T11:40:41
  kind: decision
  summary: "补充 system_server 的缓存读取与第二个功能开关"
  source: RemoteConfig/ConfigProvider/FeatureFlags
  affects: [cross-process-feature-switches]

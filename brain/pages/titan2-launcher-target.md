---
id: titan2-launcher-target
title: "Titan 2 国际版桌面应用与固件信息"
category: reference
status: active
tags: [titan2, launcher3, firmware]
created: "2026-10-23T10:32:27"
updated: "2026-10-23T10:32:27"
---

<!-- compiled_truth -->
## 设备与固件

- 设备：Unihertz Titan 2 国际版（TEE / EEA 固件）。
- 系统：Android 15 或 Android 16。
- Root 环境：Magisk / KernelSU + Zygisk，模块框架为 Vector 或 LSPosed。

## 桌面应用

- 包名：`com.android.launcher3`
- APK 路径：`system_ext/priv-app/SearchLauncherQuickStep`
- 固件 V01.00.14（Android 16）上的 `versionName` 为 `16`
- 模块 `scope.list` 只勾选该包名。

## 用途

目标包名、APK 路径和固件版本是模块作用域与 hook 定位的依据。更换固件后需要重新确认桌面 APK 位置以及 `InvariantDeviceProfile` 的网格初始化方法签名。


## Timeline

- time: 2026-10-23T10:32:27
  kind: decision
  summary: "Created this page: Titan 2 国际版桌面应用与固件信息"
  source: "固件 V01.00.14 分析"
  affects: [titan2-launcher-target]

- time: 2026-10-23T10:32:27
  kind: decision
  summary: "记录目标桌面包名与固件信息"
  source: Targets.java / scope.list
  affects: [titan2-launcher-target]

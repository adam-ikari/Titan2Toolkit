---
slug: background
title: Project background
role: project background
updated: "2026-10-23T11:56:39"
---

# Project background

## Why

Unihertz Titan 2 国际版有两处原厂限制：

- 桌面（工作区、Dock、应用抽屉）默认 5 列，系统设置不提供列数选项；方屏 + 实体全键盘用户希望一屏放下更多应用。
- OEM 的字母键快捷启动只在桌面获得焦点或按住 Magic Key 时生效，在浏览器、聊天等前台应用里按键只会输入字母。

## Goals

- 工作区、Dock（Hotseat）和应用抽屉固定为 6 列，桌面进程重启后仍然生效。
- 字母键快捷启动在任意前台应用生效，输入法弹出时让行，锁屏与息屏保持原厂限制。
- 每个功能都有独立开关，关闭后恢复原厂行为。
- 只改运行时内存：不改 APK、不改数据库、不写系统分区，关闭模块或移除作用域后恢复默认。
- 同一份代码兼容 Android 15 与 Android 16 固件，可由 Vector 与较新的 LSPosed 加载。
- 提供最小可用的模块界面，显示当前列数、目标包名和设备信息。

## Non-goals

- 不做 Launcher3 的网格迁移（grid migration），不搬动工作区既有图标。
- 不修改文件夹内部布局、最近任务等其它网格。
- 不提供自己的快捷键映射界面；字母与动作仍在 OEM 的 `com.agui.shortcutsettings` 中配置。
- 不支持未 root 或未安装 Vector / LSPosed 的设备。

## Target user

Unihertz Titan 2 国际版（TEE / EEA 固件）用户，已解锁 Bootloader 并 root（Magisk / KernelSU + Zygisk），已在设备上启用 Vector 或 LSPosed。

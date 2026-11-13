---
id: experimental-app-clone
title: "实验性：放开应用克隆白名单"
category: decision
status: active
tags: [xposed, settings, clone, agui]
created: "2026-11-13T00:22:06"
updated: "2026-11-13T02:57:07"
---

<!-- compiled_truth -->
## 结论（已用 TEE 固件 2026042212 / Android 16 验证）

Titan 2 的“应用克隆”是 AOSP 15+ clone profile。克隆页在 **`com.android.settings`**（本固件 APK = `system_ext/priv-app/MtkSettings/MtkSettings.apk`），入口类：

- `com.android.settings.applications.manageapplications.clone.ManageCloneFragment`
- `ManageCloneFragment.onCreate()` 读取 `Resources.getStringArray(0x107001b)`，即 `com.android.internal.R.array.cloneable_apps`，写入静态 `ALLOW_CLONE_APPLICATIONS`。
- `filterItems()` 只对白名单里的包逐个 `getApplicationInfoAsUser(pkg,0,0)` / `(pkg,0,mCloneUser)`，决定“可克隆/已克隆”。
- 实际克隆：`CloneBackend.installCloneApp(pkg)` **不校验白名单**，直接创建 clone profile 用户并安装。

## 白名单实际值

- `framework-res.apk` 里 `cloneable_apps` 默认**空**。
- 由 product RRO `prod/overlay/framework-res__sys_mssi_64_64only_ww_armv82_g71v78c2k_dfl_tee__auto_generated_rro_product.apk` 覆盖为 11 个：
  `com.microsoft.teams`, `jp.naver.line.android`, `com.instagram.android`, `com.twitter.android`, `com.facebook.katana`, `com.linkedin.android`, `com.facebook.orca`, `com.tencent.mm`, `com.tencent.mobileqq`, `com.whatsapp`, `com.whatsapp.w4b`。
- 另有 `agui_non_install_clone_applist`（6 个系统应用：setupwizard/launcher3/wallpaper/loggerui/talkback/agui.gallery）是禁止装入 clone profile 的名单，不影响第三方应用。
- `agui_clone_built_in_app` 为空。

## 实现（已编译进 0.5.0）

- 作用域：`com.android.settings`（`Targets.SETTINGS` + `scope.list`）。
- Hook：`android.content.res.Resources.getStringArray(int)`。
  - 用 `getIdentifier("cloneable_apps","array","android")` 解析目标 id（固件为 `0x0107001b`）并缓存。
  - id 命中且 `experimental_clone` 开启时，`proceed()` 取 RRO 后的 11 个，再并入所有已安装第三方包（`FLAG_INSTALLED` 且非 `FLAG_SYSTEM`），`LinkedHashSet` 去重返回。
  - 开关关闭或 id 不匹配完全透传。
- 开关：`FeatureFlags.KEY_EXPERIMENTAL_CLONE`（默认 false）→ `ConfigProvider` → `RemoteConfig.experimentalAppClone()`（5s 缓存）。

## 验证状态

- 静态：已对照固件反编译确认 `ManageCloneFragment` 调用的就是 `Resources.getStringArray`，id 与 hook 解析一致；`assembleDebug/Release` 通过，APK 内含 `CloneHooks` 与 scope。
- 真机：待开关注入 Settings 后，在“设置 → 应用 → 克隆应用”确认列出全部第三方应用并能创建克隆。


## Timeline

- time: 2026-11-13T00:22:06
  kind: decision
  summary: "Created this page: 实验性：放开应用克隆白名单"
  source: "AOSP 源码 + 真机转储分析 2026-11-13"
  affects: [experimental-app-clone]

- time: 2026-11-13T00:22:16
  kind: decision
  summary: "在 com.android.settings 进程放开框架 cloneable_apps 数组，实现任意第三方应用可克隆"
  source: "AOSP android16-release 源码 + 设备转储 2026-11-13"
  affects: [experimental-app-clone]

- time: 2026-11-13T02:57:07
  kind: decision
  summary: "已在 TEE 2026042212(Android16) 固件确认：Settings 读框架 cloneable_apps，RRO 覆写 11 应用；hook Resources.getStringArray 可放开"
  source: "固件 2026042212 反编译 2026-11-13"
  affects: [experimental-app-clone]

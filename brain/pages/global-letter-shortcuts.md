---
id: global-letter-shortcuts
title: "字母键快捷键在任何前台应用生效"
category: decision
status: active
tags: [xposed, system_server, shortcut, agui]
created: "2026-10-23T11:40:27"
updated: "2026-10-24T14:28:39"
---

<!-- compiled_truth -->
## 结论

在 system_server 内 hook OEM 的 `com.agui.server.functional.AguiKeyboardShortcut.isHomePackage(KeyEvent)`。当模块开关开启、屏幕点亮、输入法不可见，且按键是 A–Z（keycode 29–54）且不带 Ctrl/Alt/Meta 时返回 true，让 OEM 原有逻辑把字母键当作快捷方式候选。未配置的字母仍会继续传给前台应用。

## 为什么选这个锚点

- `PhoneWindowManager.interceptKeyTq` 对**每个** KeyEvent 调用 `AguiKeyboardShortcut.setShortcutCtrlStatus(mDismissImeOnBackKeyPressed, isKeyguardLocked(), event)`，因此该路径在所有前台应用中都执行，不限于桌面。
- `keyboardShortcutFunc` 中第二个 `if` 是唯一依赖 `isHomePackage` 的入口；第一个 `if` 只处理长按后的抬起（`mPressState`）。
- `isHomePackage` 自身负责屏幕点亮判断和 Magic Key（keycode 403）状态更新。hook 先 `chain.proceed()`，保留这些副作用，只在结果为 false 时按条件改成 true。
- 之后完全复用 OEM 的 `ShortcutInterceptKey`：未配置字母返回 0 不消费；已配置字母返回 -1，并按原有短按 / 长按语义把 `"-short"` / `"-long"` 调度到 `com.agui.keyboard/.ExecuteShortcutFunc`。因此不需要复制设置读取、用户判断或 Handler 逻辑。

## Sym 组合键

- Sym 不列入拦截修饰键，Sym+字母与普通字母同等对待。理由：OEM 的 `AguiKeyboardShortcut` 全程不读 `getMetaState()`；Titan 2 键盘上 Sym+Q 产生 keycode Q 并带 `META_SYM_ON`，文本结果仍是 `q`；输入法可见时已有双重检查，不会抢输入。
- 上游 `A85ShortcutFunction`（`APhoneWindowManagerExtImpl` 的 pre-queue 阶段）只处理 `KeyEvent.isAguiCustomKeys(keycode)` 的厂商键，字母 keycode 29–54 直接返回 0，不会提前消费事件。
- 因此 Sym+字母不需要修改 Android framework，只需要 system_server 里的这个 hook；原厂限制在桌面（`isHomePackage`）是唯一的框架侧门控。

## IME 与安全边界

OEM 自身有三重门控，模块再补一层独立检查：

- `debug.sf.agui_input_status`：IMMS 在 IME 窗口可见时置 1；
- `mDismissImeOnBackKeyPressed`：由 `setImeWindowStatusLocked` 根据 IME 可见性与 back disposition 计算；
- 模块的 `WindowManagerInternal.getInputMethodWindowVisibleHeight(0) > 0`；查询失败按“可见”处理，退回原厂行为。
- 锁屏（`sIsKeyguardLocked`）与息屏不触发；Ctrl/Alt/Meta 组合不劫持，避免抢占 Ctrl+C 等编辑快捷键。

## 开关读取

- system_server 通过 `RemoteConfig.globalLetterShortcuts()` 只读 volatile 缓存，输入线程从不做 binder 调用。
- 首次读取成功前保持关闭；之后每 5 秒最多触发一次异步 `warmUp`，开关切换在几秒内生效。
- 读取失败时保留旧值；缓存从未成功过则维持原厂行为。

## 依赖与失败模式

- 若 OEM 快捷键设置里没有对应字母，Sym+字母同样不会启动应用：模块只放宽 `isHomePackage`，不维护自己的按键绑定。
- 目标类或方法缺失（非 AGUI 固件、无 `com.agui.keyboard`）时只记日志并跳过 hook。
- 模块应用被卸载或强制停止导致 Provider 不可达时，功能退回原厂行为。
- 作用域必须勾选系统框架（`android`）并重启，hook 才会装入 system_server。


## Timeline

- time: 2026-10-23T11:40:27
  kind: decision
  summary: "Created this page: 字母键快捷键在任何前台应用生效"
  source: "本次实现（固件 V01.00.14 反编译）"
  affects: [global-letter-shortcuts]

- time: 2026-10-23T11:40:34
  kind: decision
  summary: "确定 hook isHomePackage 扩展字母键到全局"
  source: "ShortcutHooks.java / 固件反编译"
  affects: [global-letter-shortcuts]

- time: 2026-10-24T14:28:39
  kind: decision
  summary: Rewrote compiled_truth to the new best understanding
  source: "ShortcutHooks.java / AguiKeyboardShortcut 反编译"
  affects: [global-letter-shortcuts]

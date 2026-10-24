# Titan2 Toolkit

适用于 **Unihertz Titan 2 国际版** 的 Xposed 模块，兼容 **Vector**（LSPosed 2.0）与 LSPosed 模块管理器。

当前功能：

| 功能 | 状态 |
| --- | --- |
| 桌面 6 列布局（工作区 / Dock / 应用抽屉） | 已实现，可在模块界面开关 |
| 字母键全局快捷启动（输入法弹出时除外） | 已实现，可在模块界面开关 |
| 桌面布局保护（模块失效不丢第 6 列图标） | 已实现，可在模块界面开关 |

## 环境要求

- Unihertz Titan 2 国际版（TEE / EEA 固件），Android 15 或 Android 16
- 已解锁 Bootloader 并 root（Magisk / KernelSU + Zygisk）
- 已安装 [Vector](https://github.com/JingMatrix/Vector) 或 LSPosed

模块使用现代 libxposed API（`minApiVersion=101`、`targetApiVersion=101`），Vector 与较新的 LSPosed 均可加载。

## 构建

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Release 构建默认使用 debug 签名，方便直接安装测试：

```bash
./gradlew :app:assembleRelease
```

## 功能开关

模块界面为每个功能提供独立开关，配置保存在模块应用内，被 hook 的进程通过只读 ContentProvider 读取：

- **桌面 6 列布局**：默认开启；工作区、Dock（Hotseat）和应用抽屉一起变为 6 列，关闭后恢复系统默认，需要重启桌面生效。
- **字母键快捷启动全局生效**：默认开启；关闭后恢复系统默认行为（只在桌面或按住 Magic Key 时触发）。切换后系统框架最多几秒内生效，无需重启。
- **桌面布局保护**：默认开启；模块激活时自动备份桌面布局数据库，模块停用期间被原厂 5 列加载删掉的图标会在重新激活并恢复 6 列时补回。关闭后不再备份，也不再拦截越界图标的删除。

## 安装与启用

1. 安装 APK。
2. 在 Vector / LSPosed 管理器中启用 **Titan2 Toolkit**。
3. 作用域勾选 **系统框架**（`android`）和 **桌面**（`com.android.launcher3`），两者已写入 `scope.list`。
4. 重启手机，让 system_server 和桌面进程重新加载模块。

## 实现方式

### 桌面 6 列

桌面的列数由 Launcher3 的 `InvariantDeviceProfile` 决定。模块在桌面进程里拦截网格初始化流程，把工作区、Dock 和应用抽屉的列数一起改为 6：

- 工作区 `numColumns`；
- Dock 的 `numShownHotseatIcons`、`numDatabaseHotseatIcons`；
- 应用抽屉的 `numAllAppsColumns`、`numDatabaseAllAppsColumns`。

`DeviceProfile` 在构造时从 `InvariantDeviceProfile` 拷贝这些值，所以 hook 时机按版本区分：

- Android 14/15：`applyPartnerDeviceProfileOverrides(Context, DisplayMetrics)` 返回后修改字段；
- Android 16：在 `newDPBuilder(...)` 调用前修改字段，确保随后构建的 `DeviceProfile` 使用新列数。

改的是内存中的实例字段，不改 APK，不改系统分区；卸载模块或关闭作用域后恢复原状。布局保护在恢复图标时会写入桌面自己的数据库。

### 桌面布局保护

原厂桌面只有 4×4、5×4、4×3 三种网格，没有 6 列配置。模块不在时，桌面按 5×4 加载，`LoaderCursor.checkItemPlacement` 会把落在第 6 列（或 Dock 第 6 格）的条目判定为越界，`markDeleted` 随后把它们从 `launcher_5_by_4.db` 里永久删除。模块重新激活时，数据库里已经没有这些行，6 列布局也就补不回来。

保护分两层：

- **不删**：hook `LoaderCursor.markDeleted(String, String)`。当删除原因是布局校验失败、且当前行的位置超出当前网格时，跳过原方法，行留在数据库里。它不会被加载到 5 列桌面上，但 6 列恢复后会正常出现。关闭 6 列开关、模块仍激活时同样生效。
- **备份与补回**：模块激活期间，监听 `ModelDbController` 的插入 / 更新 / 删除，空闲约 1.5 秒后把 `favorites` 表完整复制到桌面私有目录下的 `titan2_layout_backup.db`。模块停用期间不会写这个文件。重新激活时，在 `InvariantDeviceProfile` 初始化、桌面加载数据之前，对比备份与当前数据库：只补回“当前库里缺失、且按原厂网格属于越界”的行。原位置空闲就放回原位，被占用就放到新一页的空位；Dock 满格时同样落到新一页。文件夹父条目按原 `_id` 恢复，文件夹里的子项随之重新归组。

模块以 5 列运行（6 列开关关闭）时，快照会把备份中仍缺失的越界行合并保留，避免一次快照把等待恢复的图标永久抹掉。所有数据库操作失败只记录日志，不影响桌面正常加载。

### 字母键全局快捷启动

OEM 的快捷键逻辑位于 system_server 的 `com.agui.server.functional.AguiKeyboardShortcut`，由 `PhoneWindowManager.interceptKeyTq` 对每个按键调用。原厂逻辑里，字母键只有两种情况下才算快捷方式候选：

- 当前焦点窗口属于桌面，或按住 Magic Key（`isHomePackage` 返回 true）；
- 输入法未弹出、未锁屏、`debug.sf.agui_input_status == 0`。

模块 hook `isHomePackage(KeyEvent)`：开关开启、屏幕点亮、输入法不可见时，对 A–Z（keycode 29–54）返回 true。之后的判断仍由 OEM 代码完成：

- 该字母在系统快捷键设置里**没有配置**：`ShortcutInterceptKey` 返回 0，按键照常传给前台应用；
- 已配置：按键被消费，由 OEM 按原有的短按 / 长按语义调度到 `com.agui.keyboard` 的 `ExecuteShortcutFunc`。

输入法判断是双重的：OEM 自己的 `debug.sf.agui_input_status` 与 `mDismissImeOnBackKeyPressed`，加上模块通过 `WindowManagerInternal.getInputMethodWindowVisibleHeight()` 的独立检查。检查失败时按“输入法可见”处理，宁可退回原厂行为也不抢输入。

带 Ctrl / Alt / Meta / Sym 的字母组合不会被当快捷方式（例如 Ctrl+C、Ctrl+V 仍交给前台应用）；普通字母与 Shift+字母不受影响。

快捷键的字母与动作仍在系统自带的快捷键设置（`com.agui.shortcutsettings`）中配置，模块不提供自己的配置界面。

## 已知限制

- 不触发 Launcher3 的网格迁移（grid migration）。工作区原有图标保留在原来的单元格，新增的第 5、6 列留空，由用户自行摆放；Dock 与应用抽屉由 Launcher 自动重排。
- 布局备份保存在桌面的 no-backup 私有目录；清除桌面数据、卸载桌面更新或换用户后备份不存在。首次开启保护后需要先正常使用一次（发生一次桌面数据库写入）才会生成快照。
- 最后一次桌面改动到快照写入之间有约 1.5 秒窗口；在这段时间内模块失效，极少数改动可能来不及备份。
- 补回时如果原位置被占用，图标会被移到新的一页；控件（widget）按原尺寸尽量恢复，超出网格的尺寸会被收缩。
- 工作区、Dock 和应用抽屉的图标会随列数增加而变小。
- 文件夹内部布局、最近任务等其它网格不受影响。
- 如果 Dock 图标间距低于系统下限，`DeviceProfile` 可能自行减少显示的 Dock 图标数。
- 字母键功能只对已配置的 A–Z 生效；锁屏时不会触发（保留原厂的安全限制）。
- 依赖 OEM 的 AGUI 类。第三方 ROM 或没有 `com.agui.keyboard` 的固件上 hook 会被跳过，不影响系统。
- 开关由 system_server 通过模块应用的 ContentProvider 读取；模块应用被强行停止或卸载时读不到值，功能会退回原厂行为。

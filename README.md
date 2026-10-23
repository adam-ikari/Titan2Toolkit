# Titan2 Toolkit

适用于 **Unihertz Titan 2 国际版** 的 Xposed 模块，兼容 **Vector**（LSPosed 2.0）与 LSPosed 模块管理器。

当前功能：

| 功能 | 状态 |
| --- | --- |
| 桌面（Launcher）工作区改为 6 列 | 已实现 |

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

## 安装与启用

1. 安装 APK。
2. 在 Vector / LSPosed 管理器中启用 **Titan2 Toolkit**。
3. 作用域勾选桌面应用（默认已写入 `scope.list`）。
4. 强制停止桌面或重启手机，让桌面进程重新加载。

## 实现方式

桌面的列数由 Launcher3 的 `InvariantDeviceProfile` 决定。模块在桌面进程里拦截网格初始化流程：

- Android 14/15：`applyPartnerDeviceProfileOverrides(Context, DisplayMetrics)` 返回后把 `numColumns` 改为 6；
- Android 16：在 `newDPBuilder(...)` 调用前把 `numColumns` 改为 6，确保随后构建的 `DeviceProfile` 使用新列数。

改的是内存中的实例字段，不改 APK，不改数据库，卸载模块或关闭作用域后恢复原状。

## 已知限制

- 不触发 Launcher3 的网格迁移（grid migration）。原有图标保留在原来的单元格，新增的第 5、6 列留空，由用户自行摆放。
- 桌面图标会随列数增加而变小。
- 只改工作区列数，不改文件夹、全部应用列表或 Hotseat 的列数。

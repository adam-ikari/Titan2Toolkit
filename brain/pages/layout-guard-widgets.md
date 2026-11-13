---
id: layout-guard-widgets
title: "LayoutGuard 对 widget 的保护边界"
category: decision
status: active
tags: [launcher, layout, guard, widget]
created: "2026-11-13T17:17:02"
updated: "2026-11-13T17:47:41"
---

<!-- compiled_truth -->
<!-- compiled_truth -->
## 结论

**“LayoutGuard 只备份/补回图标行”不成立。** 快照与补回都作用于整个 `favorites` 表，没有按 `itemType` 过滤；widget 行（`itemType=4` APPWIDGET、`5` CUSTOM_APPWIDGET）与图标走同一路径。

证据：
- `LayoutGuard.java:244`：`CREATE TABLE favorites_new AS SELECT * FROM src.favorites` —— 全表复制。
- `LayoutGuard.java:399-402`：补回只按 `outsideNativeGrid(...)` 过滤（纯几何条件），不看 `itemType`。
- `LayoutGuard.java:547-568`：`readRow` 拷贝游标的**所有列**，含 `appWidgetId`/`appWidgetProvider`/`spanX`/`spanY`/`options`/`restored`。
- `LayoutGuard.java:444-451`：`placeOnWorkspace` 会把 `spanX/spanY` 夹到网格内。

## 真正的 widget 风险（不是“没进备份”）

1. **补回沿用旧 `appWidgetId`，不校验、不重绑。** 若该 id 已失效，AOSP `LauncherAppWidgetInfo` 会带 `FLAG_ID_NOT_VALID=1`，框架 `AppWidgetHostView` 显示 “Problem loading widget”。
   - AOSP 证据：`ModelWriter.deleteItemsFromDatabase` 对 widget 调用 `holder.deleteAppWidgetId(info.appWidgetId)`（`ModelWriter.java:331`）。
   - `restoreMissingRows` 只把备份行写回，没有 `AppWidgetHost`/`bindAppWidgetIdIfAllowed` 逻辑。
2. **模块未激活期间。** stock 5 列加载时 `LoaderCursor.checkItemPlacement` 判越界 → `markDeleted("Item position overlap", RestoreError.OVERLAPPING_ITEM)`；`commitDeleted()` 直接删 DB 行。若此前/之后走了 `ModelWriter` 路径（provider 变更、用户移除），widget id 会被清理，DB 级补回无法恢复绑定。
3. **行还在但被改小/移位。** stock 的 grid 迁移会改 `spanX/spanY/cellX/cellY`；补回时 `liveIds.contains(id)` 直接跳过，不会用备份里的原尺寸/位置覆盖 → 表现为“widget 没被保护/尺寸不对”。
4. `markDeleted` hook 只拦 `errorType == "overlapping_item"`（`LayoutGuard.java:125-128`）；其他删除原因不拦。

## 表结构（AOSP LauncherSettings.Favorites）

`itemType`（0 app / 2 folder / 4 appwidget / 5 custom appwidget / 6 deep shortcut）、`container`（-100 desktop / -101 hotseat）、`screen`/`cellX`/`cellY`/`spanX`/`spanY`、`appWidgetId`（INTEGER NOT NULL DEFAULT -1）、`appWidgetProvider`（TEXT，ComponentName flatten 字符串）、`restored`、`rank`、`options`、`intent`、`profileId`。

补回时会被**覆盖**的列：`container`/`screen`/`cellX`/`cellY`；可能被**改动**的列：`spanX`/`spanY`；`appWidgetId` 原样保留（风险点）。

## 最小修复方案（待确认，未改代码）

在 `restoreMissingRows` 的 `readRow` 之后、`insertOrThrow` 之前，对 `itemType==4||5`：
1. 读 `appWidgetId`、`appWidgetProvider`；用 `AppWidgetManager.getAppWidgetInfo(id)` 判断是否有效。
2. 无效则 `new AppWidgetHost(context, LauncherWidgetHolder.APPWIDGET_HOST_ID)`（AOSP 常量 = 1024）`allocateAppWidgetId()`，再 `AppWidgetManager.bindAppWidgetIdIfAllowed(newId, ComponentName.unflattenFromString(provider))`；成功则 `values.put("appWidgetId", newId)`。
3. 绑定失败则跳过该行并 `log(WARN)`，避免写入必然坏掉的 widget。
4. 可选（更小）：仅跳过 id 无效的 widget 行，避免 “Problem loading widget”，但等于放弃这些 widget。

## 验证

- 真机 DB：`adb shell su -c` 下查 `favorites` 的 `itemType IN (4,5)`，看 `appWidgetId`/`appWidgetProvider`/`spanX`/`spanY` 与备份表差异（设备无 sqlite3 时把 `launcher.db` 拷到 `/data/local/tmp` 再拉取）。
- 日志：`adb logcat -s Titan2Toolkit`，确认 “restored N launcher item(s)” 与 widget 重绑日志。
- 桌面：重新启用模块后 widget 正常加载，无 “Problem loading widget”。

## 待办

- 设备当前离线（`adb devices` 空），真机 DB 对比未做；连上后补：备份表 vs live 表的 widget 行差异。
- 方案确认后再改 `LayoutGuard.restoreMissingRows`。



## 已实施的修复（2026-11-13）

`restoreMissingRows` 在插入前对 `itemType=4` 的行调用 `repairRestoredWidget`：

- 丢弃快照里的 `appWidgetId`（写 `-1`），不再复用可能已失效的 host 绑定。
- `restored` 写 `FLAG_ID_NOT_VALID|FLAG_PROVIDER_NOT_READY|FLAG_UI_NOT_READY`（1|2|4=7）。
- 保留 `appWidgetProvider`；provider 缺失/不可解析则跳过该行（Launcher3 `WorkspaceItemProcessor` 对该列做 `unflattenFromString(...)!!`，null 会崩 loader）。
- `spanX/spanY <= 0` 夹到 1（`processWidget` 尺寸非法会删行）。

AOSP 依据（`/tmp/l3`，Android main）：`WidgetInflater.inflateAppWidget` 在 `restoreStatus != RESTORE_COMPLETED` 时，若带 `FLAG_ID_NOT_VALID` 且未 `FLAG_ID_ALLOCATED`，会 `allocateAppWidgetId()` 取新 id + `bindAppWidgetIdIfAllowed`；绑定失败返回 `TYPE_PENDING`（`PendingAppWidgetHostView` 占位），成功写回 `RESTORE_COMPLETED`。provider 缺失走 `FLAG_PROVIDER_NOT_READY` 分支返回 pending，不删行、不形成补回循环。
- `itemType=5`（custom widget）不动：其 id 是 `CUSTOM_WIDGET_ID`（-100）语义，由 `CustomWidgetManager` 按 provider 重建。

测试：`app/src/test/java/com/titan2/toolkit/LayoutGuardTest.java`（Robolectric + 真实 SQLite，`@Config(sdk=35)`）构造 snapshot→删行→`restoreMissingRows`→断言补回行 `appWidgetId=-1`、`restored=7`、provider 保留、尺寸可解析；provider 空则拒绝补回。`./gradlew :app:testDebugUnitTest` 2/2 通过。


## Timeline

- time: 2026-11-13T17:17:02
  kind: decision
  summary: "Created this page: LayoutGuard 对 widget 的保护边界"
  source: "code + AOSP 2026-11-13"
  affects: [layout-guard-widgets]

- time: 2026-11-13T17:17:02
  kind: decision
  summary: "快照/补回其实包含 widget（无 itemType 过滤）；真正风险是 appWidgetId 失效未重绑，会显示 Problem loading widget"
  source: "LayoutGuard.java + AOSP Launcher3 2026-11-13"
  affects: [layout-guard-widgets]

- time: 2026-11-13T17:17:27
  kind: evidence
  summary: "用户反馈 widget 未被保护；代码核查显示 widget 已进快照/补回，风险在 appWidgetId 未重绑"
  source: "user + code 2026-11-13"
  affects: [layout-guard-widgets]

- time: 2026-11-13T17:47:41
  kind: decision
  summary: "widget 恢复改为标记待重绑（appWidgetId=-1 + restored=7），新增 DB-only Robolectric 测试"
  source: "LayoutGuard.java + AOSP Launcher3 WidgetInflater 2026-11-13"
  affects: [layout-guard-widgets]

- time: 2026-11-13T17:47:41
  kind: decision
  summary: "恢复 widget 行时丢弃旧 appWidgetId，写 restored=FLAG_ID_NOT_VALID|PROVIDER_NOT_READY|UI_NOT_READY，让 Launcher3 WidgetInflater 重新 allocate+bind；provider 不可解析则跳过"
  source: "AOSP WidgetInflater.kt + WorkspaceItemProcessor.kt 2026-11-13"
  affects: [layout-guard-widgets]

- time: 2026-11-13T17:47:41
  kind: evidence
  summary: "Robolectric DB-only 测试通过：快照→删行→补回后 appWidgetId=-1、restored=7、provider/尺寸保留；provider 空被拒绝补回"
  source: LayoutGuardTest 2026-11-13
  affects: [layout-guard-widgets]

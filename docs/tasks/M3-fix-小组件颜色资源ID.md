# M3-fix 任务规格：小组件加载失败（颜色被当作资源 ID）+ 调色板重复且已不一致

> 症状（真机实测）：桌面添加小组件后显示 **「载入窗口出现问题」**（MIUI）；AOSP 设备上对应文案是 "Problem loading widget"。
> 影响：**P0 —— 小组件完全不可用**（AC-17/18/19 全部无法达成）。
> 这两个缺陷都不改表结构、不加依赖，改动量极小。

---

## 1. 根因（异常栈原文，非推测）

`TodayWidgetContent.kt:162`：

```kotlin
.background(ColorProvider(COURSE_COLORS[item.colorTag.coerceIn(COURSE_COLORS.indices)].toArgb())),
```

**Glance 的 `ColorProvider` 有两个重载**（我从 `glance-1.2.0.aar` 里 `javap` 出来的真实签名）：

```java
// androidx.glance.unit.ColorProviderKt
public static final ColorProvider ColorProvider-8_81llA(long);   // 参数是 androidx.compose.ui.graphics.Color（inline class → long）
public static final ColorProvider ColorProvider(int);            // 参数是 @ColorRes Int → 当作「颜色资源 ID」
```

`.toArgb()` 返回 `Int`，于是**编译期解析到了「资源 ID」重载**，生成的 RemoteViews 动作是**资源型**的
（`RemoteViews$ResourceReflectionAction` → `Context.getColor(resId)`），把 ARGB 字面值当资源 ID 去查 → 必然查不到。

Launcher 侧异常（`logcat` 原文，进程 `com.miui.home`）：

```
W AppWidgetHostView: Error inflating RemoteViews
   android.widget.RemoteViews$ActionException: android.content.res.Resources$NotFoundException: Resource ID #0xffd81b60
     at android.widget.RemoteViews$ResourceReflectionAction.getParameterValue(RemoteViews.java:3330)
   Caused by: android.content.res.Resources$NotFoundException: Resource ID #0xffd81b60
     at android.content.res.Resources.getColor(Resources.java:1233)
     at android.content.Context.getColor(Context.java:1049)
     at android.widget.RemoteViews$ResourceReflectionAction.getParameterValue(RemoteViews.java:3304)
```

`0xffd81b60` 的低 24 位 `d81b60` = 调色板第 8 项「玫红」`Color(0xFFD81B60).toArgb()` ✓ 与代码逐字对应。

补充证据（说明不是"没跑起来"）：Glance 的会话任务其实**成功**了——
`WM-WorkerWrapper: Worker result SUCCESS for Work [ tags={ androidx.glance.session.SessionWorker } ]`；
失败发生在**Launcher 应用 RemoteViews 的那一刻**。

## 2. 修复（两处，都在 `widget/TodayWidgetContent.kt`）

### 2.1（P0）改用 `Color` 重载，不要传 `Int`

```kotlin
// ✗ 现状：.toArgb() 是 Int → 走「资源 ID」重载 → 运行时 Resources$NotFoundException
.background(ColorProvider(COURSE_COLORS[item.colorTag.coerceIn(COURSE_COLORS.indices)].toArgb()))

// ✓ 修复：直接传 Color（解析到 long(Color) 重载）
.background(ColorProvider(CoursePalette.base(item.colorTag)))
```

**全项目扫描结果**：`ColorProvider(` 与 `.toArgb()` 各只命中这一处 ✓ 修完即无同类问题。
（要求修完后用 grep 自证：`app/src/main` 下不再出现 `ColorProvider(` 带 `Int`/`toArgb()` 的调用。）

### 2.2（P1）删掉小组件内嵌的重复调色板，改用 `CoursePalette`（单一事实源）

现状：`TodayWidgetContent.kt:63-66` 自己复制了一份 12 色调色板 `COURSE_COLORS`，而**它已经和真正的调色板不一致了**：

| 索引 | 小组件内嵌 | `CoursePalette.bases` |
|------|-----------|----------------------|
| 7 | `0xFFAFB42B` | **`0xFFC0CA33`** |

后果：`colorTag = 7` 的课程在**小组件里与 App 里颜色不同**（本周视图用 `CoursePalette`，小组件用副本）。
要求：删除 `COURSE_COLORS`，统一调用 `CoursePalette.base(colorTag)`（`ui/theme/CoursePalette.kt:40`），
注意 `colorTag` 可能越界 → `CoursePalette.base()` 内部已做 `wrapIndex` 取模，无需再 `coerceIn`。

> 若小组件确实需要「半透明底色」效果，用 `CoursePalette.container(colorTag, dark)`（`CoursePalette.kt:43`），**不要**再自己 `copy(alpha=)`。

## 3. 交付纪律

- 只改 `widget/TodayWidgetContent.kt`（如确需，可动 `widget/` 包内其他文件）；**不动**数据层/提醒/导航
- 单文件 ≤ 300 行；无 emoji；不加依赖；不改表结构（`AppDatabase.version` 保持 2）
- 沙箱内无 git，不要 commit；**不要碰 adb / 不要装机**（真机复验由我方执行）

## 4. 构建与自测

```
export JAVA_HOME=/home/othc3/opt/jdk-21b
cd /home/othc3/WorkBuddy/安卓软件开发
/home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m3fix.log 2>&1
echo "EXIT=$?" >> /tmp/m3fix.log
grep -E "^EXIT=|BUILD (SUCCESSFUL|FAILED)" /tmp/m3fix.log | head -3
```
（**禁止**管道调用 Gradle。）用例基线 **205**，不得减少。

## 5. 回传格式

1. 两条命令的 EXIT 码与 BUILD 结果；用例总数与失败数
2. `TodayWidgetContent.kt` 改动前后的那 1~2 行（原样贴出）+ 文件行数
3. **自证 grep**：`grep -rn "ColorProvider(" app/src/main` 与 `grep -rn "toArgb()" app/src/main` 的输出（应为空或已改为 `Color` 形式）
4. 调色板一致性自证：说明 `COURSE_COLORS` 已删除、颜色统一取自 `CoursePalette`
5. 未验证项如实列出（真机验证：小组件能正常渲染、高亮色正确、与周视图同色）

## 6. 我方真机复验方法（交付后执行）

1. 装机 → 桌面重新添加「今日课程」小组件（入口：长按桌面 → 小部件 → 全部应用 → **安卓小部件** 栏）
2. 期望：**不再出现「载入窗口出现问题」**，正常显示今日课程列表 + 下一节高亮
3. `logcat` 复核：不再出现 `Error inflating RemoteViews` / `Resources$NotFoundException`
4. 与周视图对照：同一门课的颜色在小组件与周视图**一致**（验证 2.2）
5. 继续 AC-17/18/19 的完整验收（改课后不重启即刷新、无课天空态）

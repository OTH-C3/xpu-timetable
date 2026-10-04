# M2-D-fix 任务规格：提醒延迟文案与精确闹钟授权生效（AC-25）

> 来源：M2-D 交付后的**真机实测 + 源码核查**（2026-09-17，安卓真机 + HyperOS）。
> **不新增依赖、不改表结构、不动已验收的排程算法（`ReminderPlanner.plan` / `shouldNotify`）与 XPU 端点。**
>
> ⚠️ 本规格第 2 版**更正了第 1 版的一处错误结论**（原文称「授权返回后状态不刷新」→ 源码核查为**已实现**）。
> 现结论全部附代码位置或 `dumpsys` 原文，可直接核对。

---

## 1. 真机实测取到的证据（`dumpsys` 原文，非推测）

### 1.1 排程链路在真机上生效 ✓

```
tag=*walarm*:com.gould.xputimetable/.reminder.ReminderReceiver
type=RTC_WAKEUP origWhen=2026-09-18 07:45:00.000 window=+1h0m0s0ms repeatInterval=0 count=0 flags=0x20
whenElapsed=+13h38m22s11ms maxWhenElapsed=+14h38m22s11ms
```

触发时刻 = **周五 07:45** = 周五第 1 节 08:00 − 提前 15 分钟 ✓（与 `time_slots`、周视图一致）。

### 1.2 走的是降级路径，最坏延迟 **1 小时**

| 闹钟 | `window` | 性质 |
|------|----------|------|
| 本 App 的提醒闹钟 | `+1h0m0s0ms` | 非精确（`setAndAllowWhileIdle`） |
| 系统/其他应用的闹钟 | `0` + `exactAllowReason=...` | 精确 |

- 同一份 dump 里平台给了常量 `allow_while_idle_window=+1h0m0s0ms`，与我们的窗口**完全一致** → 确认走的是 `ReminderScheduler.kt:40` 的 `else` 分支。
- `whenElapsed` 与 `maxWhenElapsed` 相差正好 1 小时 → 投递区间 **[07:45, 08:45]**，最坏可能到课后才提醒。
- 根因：`SCHEDULE_EXACT_ALARM` 本机默认未授权（`cmd appops get ... SCHEDULE_EXACT_ALARM` → `Default mode: default`；targetSdk 36 在 Android 14+ 默认拒绝）。

### 1.3 设置页文案与实测差一个数量级（**缺陷 1**）

```kotlin
// ui/settings/SettingsScreen.kt:69
private const val HINT_EXACT_ALARM = "未获精确闹钟授权：提醒可能延后几分钟，功能不受影响"
```

「几分钟」vs 实测 **1 小时** 窗口 → 用户会判断「无所谓，不授权」，而课前提醒是核心功能。

### 1.4 授权后**不会重排**（**缺陷 2**，源码核查结论）

全项目 `reschedule()` 调用点已逐个核对（`TimetableApp.kt:30` 启动、`CourseEditViewModel:236/251`、`ImportPreviewViewModel:117`、`CleanupViewModel:98`、`SettingsViewModel:122/135/144`、`SystemEventReceiver:24`），**没有任何一个**发生在「用户从系统页授权精确闹钟回来」这一刻：

- `SettingsScreen.kt:101-103` 的 `DisposableEffect` + `ON_RESUME → viewModel.refreshPermissionState()` **只刷新 UI 状态**（`SettingsViewModel.kt:83-90`，仅写 `exactAlarmAllowed`），**不重排**；
- `SystemEventReceiver.kt:32-36` 的 `ACTIONS` 只有 `BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED`，**未处理** Android 官方为此场景提供的广播
  `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`。

后果：用户授权并返回后，界面显示「已授权」，但**已挂的那条仍是非精确闹钟**（`window=+1h`），要等到下次自然重排（重启 App / 改设置 / 编辑课程 / 重启设备）才变精确 —— 用户以为已生效，实际没有。

### 1.5 已核实**不是**缺陷的两项（避免误改）

- **`ON_RESUME` 状态刷新已实现**（`SettingsScreen.kt:101-103`）→ 本轮只需回归确认，不要重写。
- `HINT_NOTIFICATIONS`（「通知权限未开启：收不到提醒，课表功能不受影响」）表述**准确**，保持不变。

---

## 2. 任务 1（P1，必做）：文案如实（对应 1.3）

`ui/settings/SettingsScreen.kt:69` 改为如实表述，量级不得低于系统窗口。建议：

```
未获精确闹钟授权：提醒最长可能延后约 1 小时（系统省电策略），会影响课前提醒的准时性
```

- 文案保持文件级常量写法（项目既有重组纪律：**禁**在组合期新建 `List`/拼接字符串）。
- `ACTION_REQUEST_EXACT`（「去授权」）保持不变，但**回归确认**它请求的是 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`（直达「闹钟与提醒」系统页，而非 App 详情页）。

## 3. 任务 2（P1，必做）：授权后重排，让权限真正生效（对应 1.4）

在 `SystemEventReceiver` 增加对系统广播的处理（与现有 `TIME_SET` 同源，复用同一条 `reschedule()` 路径）：

1. `ACTIONS` 集合加入 `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
   （常量值 `android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，`AlarmManager` 自 API 31 提供该常量）。
2. `AndroidManifest.xml` 的 `SystemEventReceiver` 的 `<intent-filter>` 加入同名 `<action>`；
   **权限声明与代码同批**（项目已因漏 `INTERNET` 踩过一次）。
3. `SettingsScreen` 的 `ON_RESUME` 刷新路径中：当 `exactAlarmAllowed` 由 **false → true** 时，除刷新 UI 外**必须**触发一次重排。
   - 触发方式沿用现有注入的回调（`AppNav` 里 `reminderManager.reschedule()`），**不要**在 UI 层直接碰 `AlarmManager`。
   - 注意去重：广播与 ON_RESUME 可能几乎同时发生 → 重排是幂等的（固定 `requestCode` 覆盖），但仍要求**不得**因此产生两条闹钟。
4. `reschedule()` 内部无需改动（`plan` 会据 `canScheduleExact()` 自动选精确/降级分支）。

> 说明：本任务与「任务 1 文案」互补 —— 文案让用户知道该授权，任务 2 让授权**真正**生效。

## 3.5 任务 3（P1，必做）：点击提醒通知必须呈现周视图（AC-29）

**真机实测发现的保真缺口（2026-09-17）**：点击课前提醒通知能把 App 拉回前台，但**呈现的是离开时的页面**——实测当时停在设置页，点通知后仍显示设置页，不是课表。

根因：`MainActivity` 是 `standard` 启动模式且 `PendingIntent` 目标是同一个 `MainActivity`；任务已在后台时系统**复用既有 Activity 实例**，导航栈（AppNav 的 `backStack`）仍停在原页面。

实现要求（不引入依赖）：

1. `AndroidManifest.xml` 的 `MainActivity` 加 `android:launchMode="singleTop"`（避免重复实例；顺带避免点通知产生多个 Activity）。
2. 通知的 `PendingIntent` Intent 加 `Intent.FLAG_ACTIVITY_CLEAR_TOP`（可选但推荐，语义更明确）。
3. `MainActivity.override fun onNewIntent(intent: Intent)`：调用 `super` 后，向应用容器发布一次"回到课表"事件
   （沿用现有容器模式，如 `AppContainer` 内加 `val navRequests = MutableStateFlow(0)` 或 `MutableSharedFlow<Unit>`，`tryEmit` 即可，**不要**引入新依赖）。
4. `AppNav` 观察该事件 → 执行**已有的** `popToRoot()`（返回栈回栈底周视图）。不得在 UI 层直接改 `AlarmManager` 或绕过返回栈另开页面。

验收判据（真机，逐条）：

| # | 场景 | 期望 |
|---|------|------|
| A | App 在后台且**停在设置页** → 点提醒通知 | 呈现**周视图**（UI 树含「第 N 周」），不是设置页 |
| B | App 完全未运行 → 点提醒通知 | 直接进入周视图 |
| C | 连续点通知 2 次 | `dumpsys activity activities \| grep -c MainActivity` 不增长（无重复实例）、不崩溃 |
| D | 从编辑页（有未保存输入）被通知拉起 | 呈现周视图；编辑表单内容丢弃（与既有"返回即丢弃"行为一致，本次不新增确认） |

## 4. 决策项（**本次不实现**，等老大拍板）

Android 13+ 另有 `USE_EXACT_ALARM`（**默认授予**，无需用户操作即可用精确闹钟）：

| 方案 | 好处 | 代价 / 风险 |
|------|------|-------------|
| A. 维持 `SCHEDULE_EXACT_ALARM`（当前实现 + 本规格修补） | 任何分发渠道都合规 | 用户须手动授权；不授权则最长延迟 1 小时（已如实告知） |
| B. 加 `USE_EXACT_ALARM`（可与 A 并存） | 提醒必然准时，用户零操作 | 属受限权限，官方口径仅限「核心功能为闹钟/日历」的应用；课表提醒**可被认定为日历类**，但需自行承担解释责任；将来上市场需重新自查 |

**默认建议：先做 A（本规格），B 由老大决定。本规格中不要实现 B。**

---

## 5. 真机验收判据（可机械核对）

| # | 步骤 | 期望 |
|---|------|------|
| 1 | 未授权时进设置页 | 文案含「1 小时」量级；有「去授权」入口 |
| 2 | 点「去授权」 | 直达「闹钟与提醒」系统页（非 App 详情页） |
| 3 | 系统页授权 → 返回设置页 | 显示已授权（`ON_RESUME` 刷新，回归确认） |
| 4 | 返回后**不做任何其他操作**，立刻查 `dumpsys alarm` | 我们那条闹钟 `window=0`（精确），且 `origWhen` 仍 = 「下一节课 − 提前分钟」 |
| 5 | 同一条闹钟只出现一次 | 不重复（广播 + ON_RESUME 双触发下的幂等性） |
| 6 | 拒绝通知权限（`cmd appops set <pkg> POST_NOTIFICATION ignore`）→ 重启 App | 课表正常渲染、无崩溃（AC-16 回归） |
| 7 | 恢复权限、提前分钟改回 15 | 闹钟回到「下一节课 − 15 分钟」 |

> **真机权限坑（务必照做，否则会误判）**：HyperOS 上单跑
> `adb shell pm grant <pkg> android.permission.POST_NOTIFICATIONS` 之后，`dumpsys package` 仍可能是 `granted=false`；
> 必须**同时**执行 `adb shell cmd appops set <pkg> POST_NOTIFICATION allow`，再复核 `granted=true`。
> 精确闹钟对应 `adb shell cmd appops set <pkg> SCHEDULE_EXACT_ALARM allow`。

---

## 6. 交付纪律

- 文件 ≤ 300 行；无 emoji；**不新增依赖**
- **不改表结构**（若确需，必须同时升 `AppDatabase.version` 并补 `Migrations.kt` —— 已因漏做崩过一次）
- 不得改动已验收的 `ReminderPlanner.plan` / `shouldNotify` 语义、XPU 端点与拦截正则
- 回传格式：构建 EXIT 码 + 用例数（当前基线 **181**，若新增纯函数测试请报告增量）+ 文件行数 + 逐条自评 **AC-25** + 上表 1–7 的实测结果（第 4/5 条必须给 `dumpsys` 里 `window=` 与闹钟条数的原文）+ 未验证项如实列出

---

## 7. 附：M2-D 剩余真机用例（由我方执行，与本修复可并行）

- **AC-14**：加一门今天的课 + 提前 60 分钟 → 应**立即**收到通知（标题「N 分钟后上课」/ 正文「课名 · HH:mm-HH:mm · 教室」）；点通知**直达课表**
- **AC-15**：加一门今天**已结束**的课 → 重排后**不得**补发通知，闹钟指向下一次
  （结构性依据：`findNextClass` 经 `WeekCalc.nextSessionTime` 只返回「还没结束」的那次安排，已结束的课不会被选中；`shouldNotify` 为二次防线）
- **AC-16**：拒绝通知权限 + 重启 → 课表不受影响
- **重启恢复**：`BOOT_COMPLETED` 后闹钟重新挂上

> 我方执行时用 App 自身的加课流程造测试课，验证完**立即删除**，并核对数据库回到 `MANUAL 1（高数）+ WEB 9`、无残留测试数据。
> 真机自动化纪律：**禁用系统返回键**导航（会退出 App 或落到第三方应用）；截屏前先核对前台包名。

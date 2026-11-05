# M8 验收报告 —— Baseline Profile · 退出提示浮层 · 「回到本周」修复

> 日期：2026-09-30 起，**2026-10-01 补齐真机采集与真机 A/B**
> 构建：AGP 9.4.0 / Kotlin 2.3.21 / compose-bom 2026.08.00
> 单测：**237 用例 / 28 测试类 / 0 失败**（`--rerun-tasks` 强制重跑，非缓存）
> release APK：**7.69 MB**（debug 版 21.3 MB）
> 真机：24117RK2CC（Android 16 / API 36）

---

## 一、Baseline Profile（新增）

### 做了什么

| 项 | 内容 |
|---|---|
| 生成器模块 | `:baselineprofile`（`com.android.test` + `androidx.baselineprofile` 1.5.0） |
| 接线 | app 侧也应用插件 + `baselineProfile(project(":baselineprofile"))`；`automaticGenerationDuringBuild = false` |
| 采集脚本 | `BaselineProfileGenerator`：冷启动 → 等周视图内容 → 翻周 → 切底栏往返 |
| 基准脚本 | `StartupBenchmarks`：`Partial(BaselineProfileMode.Require)` vs `None()`，各 10 次迭代 |
| 测量脚本 | `scripts/measure-startup-ab.sh`（含"跑前检查手机是否空闲"与"是否被 assume 跳过"自检） |
| 生成物 | `app/src/release/generated/baselineProfiles/{baseline-prof,startup-prof}.txt` |

### 关键修正 1：必须显式开 startup profile

首次采集时插件给出警告 ——

```
No startup profile rules were generated for the variant `release`.
This is most likely because there are no instrumentation test with baseline profile
rule, which specify `includeInStartupProfile = true`.
```

**androidx.benchmark 1.5.0 起 `includeInStartupProfile` 默认为 false**，不传就丢掉启动路径的
AOT 规则，而那正是"刚打开卡"最该优化的部分。已显式传 `true`，重采后
`startup-prof.txt` 正常生成，构建日志出现 `mergeReleaseStartupProfile`。

### 验证（可复现）

| 检查 | 方法 | 结果 |
|---|---|---|
| profile 进包 | 解包 APK 查 `assets/dexopt/` | 真机 profile：release `baseline.prof = 9521 B` / nonMinifiedRelease `= 11706 B`；**无 profile 的对照构建为 7093 B 占位** |
| 规格随采集变化 | 对比两次采集 | 模拟器 18019 条 → 真机 **25861 条**（+30.78%） |
| 规则有效 | 统计 `baseline-prof.txt` 的包前缀分布 | Compose UI 5912 / runtime 2248 / foundation 1021 / animation 944 / material3 854 / **本项目 595** / coroutines 474 / datastore 461 |
| 混淆翻译 | 构建日志 | `expandReleaseArtProfileWildcards` → `compileReleaseArtProfile` → `mergeReleaseStartupProfile` 均执行，无告警 |

### 真机复测（2026-10-01）：收益已测得

#### ① 真机重采 profile（模拟器采的不够用）

在真机上重跑 `:app:generateBaselineProfile`，插件给出的对比：

```
Comparison with previous profile:   18019 旧规则 → 25861 新规则
  Added 8014 rules (30.78%) ｜ Removed 172 rules (0.66%) ｜ Unmodified 17847 rules (68.56%)
```

真机采到 **25861 条**（比模拟器多 30.78%）—— 真机的渲染管线与输入栈走了不同代码路径，
所以"在模拟器上采一个就用"是不对的。产物 2.57 MB（模拟器版 1.82 MB）。

#### ② 真机 A/B（Macrobenchmark，同设备同 APK，只改编译模式，各 10 次迭代）

| 组 | TTID min | **median** | max |
|---|---|---|---|
| A：`Partial(BaselineProfileMode.Require)`（用 APK 内 profile） | **260.2 ms** | **291.7 ms** | **316.5 ms** |
| B：`CompilationMode.None()`（不用 profile / AOT） | 283.3 ms | 322.6 ms | 370.1 ms |
| **改善** | −23.1 ms（−8.2%） | **−30.9 ms（−9.6%）** | −53.6 ms（−14.5%） |

`OK (2 tests)`，无 assume 跳过 —— 测量有效。

**对比模拟器的失败尝试**：同一份 A/B 在模拟器上用 `am start -W TotalTime` 测是
393 ms vs 382 ms（测不出差异）。原因是 `am start -W` 只覆盖首帧、抖动 ±10%、5 次样本不够，
且模拟器软件渲染把启动耗时压在图形初始化上。**结论：这类收益必须用 Macrobenchmark 在真机测。**

### 注意 过程中踩到的四个坑（都会让测量"看起来成功但其实是空的"）

**坑 1：`includeInStartupProfile` 默认 false（见上）**
不显式传 `true`，启动路径的 AOT 规则整段丢失，插件只给一行警告，构建照常成功。

**坑 2：`MacrobenchmarkRule` 在命令行直跑必须显式声明规则类型**
它的第一条语句是
```kotlin
Assume.assumeTrue(Arguments.getEnabledRules().contains(Arguments.RuleType.Macrobenchmark))
```
不传 `androidx.benchmark.enabledRules=Macrobenchmark` 时集合为空 → **所有用例被 assume 静默跳过**。
症状极具迷惑性：`BUILD SUCCESSFUL in 18s`、控制台无任何报错，只有 JUnit XML 里
`tests="2" failures="2"` 且 time=0.001，logcat 里一行 `assumption failed`。
（`BaselineProfileRule` 采集不受影响，因为采集任务由插件注入了 `enabledRules=BaselineProfile`。）

**坑 3：`am instrument` 的 `-e` 是空格分隔，不是 `=`**
写成 `-e key=value` 会把后面的 component 当成 value，报
`Error: Argument expected after "<component>"` 并打印 `am` 帮助。正确：
`am instrument -w -e class <类> -e <键> <值> <包>/<runner>`（本机 `am` 也不支持 `--es`）。

**坑 4（代价最大）：`connectedNonMinifiedReleaseAndroidTest` 会卸载被测 App —— 数据全丢**
AGP 的 connected test 任务在结束时（**无论用例是否被执行**）会卸载被测 App 与测试包；
卸载 = 删除应用数据。本次真机上导入好的真实课表 + 学期设置因此**全部清空且不可恢复**
（只能重新走一次教务导入）。
→ 已把测量脚本改为**手动 `am instrument`**（不经过 AGP，不装卸），并在脚本头部写明这条；
若必须走 AGP，则要加 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`，
且**跑前先备份**（`adb exec-out` 拉取数据库）。

### 附带改动

- release 变体改用 **debug keystore 签名**（`signingConfigs.getByName("debug")`）：
  ① `baselineProfile` 会派生 `nonMinifiedRelease` 并安装到设备采集，未签名装不上；
  ② 与本机既有安装同签名 → 覆盖安装保留数据。
  **发布 GitHub Releases 前必须换正式 keystore**（换签名会导致无法覆盖安装、数据丢失）。
- `profileinstaller 1.4.1` 显式声明（虽已由 Compose 传递引入 1.4.0，但本功能依赖它，
  不依赖别家库的传递关系）。

---

## 二、退出提示改为现代化全局顶置浮层

### 根因

原实现把「再按一次退出应用」做成 **Snackbar，宿主挂在周视图自己的 `Scaffold`** 上
（`TimetableScreen.kt` 的 `snackbarHost = { SnackbarHost(snackbarHostState) }`）。
于是：

1. 提示的**显示位置在内容区底部**（底部导航栏之上）——层级不对；
2. `SnackbarHostState` 跨页面存活，而宿主随页面重建 → **切到「我的」再切回，
   Scaffold 重挂会把未过期的 Snackbar 重新显示一次**（用户看到的"又出现在下面"）。

### 改法

新增 `ui/components/TopHint.kt`：顶部居中、`statusBarsPadding()` 避让状态栏、
全弧度胶囊（`inverseSurface` / `inverseOnSurface`）、淡入 + 轻微下滑（`Motion.FastMillis`）、
驻留 `Hint.VisibleMillis = 1800ms` 自动消失。

**挂在 `AppNav` 内容区 Box 内**（页面 Scaffold 之外），因此页面切换既不重建宿主、
也不会重放；作为 Box 最后一个子项绘制 → 全局置顶。

计时用 nonce 模式（`LaunchedEffect(exitHintNonce)` + `delay`）：连按会重置驻留时间，
状态写入集中在一处，避免"回调里 set 状态 + 协程里再 set"的竞态。

### 验证（模拟器，像素级）

顶部 80–340px 区域「深色像素数」作为指标（胶囊出现会显著增大）：

| 步骤 | 指标 | 判定 |
|---|---|---|
| 按下返回键前 | 190 | 无提示 |
| 按下后（立即截屏） | **1312** | **提示出现**（指标灵敏，约 7× 跳变） |
| 3 秒后 | 190 | 自动消失 ✓ |
| 切到「我的」再切回「课表」 | **190** | **无重放 ✓（旧缺陷的重现场景）** |
| 连按两次（间隔 0.4s） | 前台变为 `com.android.launcher3` | 退出成功 ✓ |

截图确认视觉：深色胶囊「再按一次退出应用」居中于顶部、状态栏之下，页面内容在后方正常显示；
胶囊与「+」「回到本周」按钮**不重叠**（会压住标题行的「周三」文字与日期区间一行，属瞬时浮层的正常覆盖）。

---

## 三、「回到本周」按钮长亮修复

### 根因

`weekIsOverridden` 直接取 `override != null`，而 `weekOverride` 会被**程序化滚动回写**：

1. 点在别的周 → 点「回到本周」→ `backToCurrentWeek()` 清空覆盖 → 显示的周回到本周（如第 6 周）；
2. `WeekPager` 观察到 `state.week` 变化 → `animateScrollToPage` 程序化滚动；
3. 滚动落定后 `snapshotFlow { currentPage }` 把**当前页（=本周）回写**成 `setWeek(6)`；
4. 于是 `weekOverride` 又变成 6（非 null）→ `weekIsOverridden = true` → **按钮重新变亮且不再熄灭**。

`WeekPager` 原有 `programmaticTarget` 守卫能压低概率，但"程序化滚动结束"与
"snapshotFlow 尾帧投递"都在主线程、天然在赛跑，**加守卫只能降低概率，不能消除**。

### 改法（语义层幂等，不去补时序）

新增 `ui/timetable/WeekOverridePolicy.kt`（与 `BackPolicy` 同风格的纯函数）：

- `normalize(target, autoWeek)`：目标周 == 自动周 → 返回 `null`（"滑到本周"等价于**没有覆盖**）；
- `isOverridden(override, autoWeek)`：**显示周与本周不同**才算覆盖（兜底，即使有脏值也不长亮）。

`TimetableViewModel`：状态新增 `autoWeek`（**钳制后**的自动周），`setWeek`/`moveWeek` 写入前
统一走 `normalize`。**这样无论回写何时到达，结果都一致** —— 从根上消除竞态。

> 用"钳制后"的自动周很重要：放假期间 `WeekCalc.currentWeek` 可能返回 20（总周数 18），
> 若拿未钳制的值比较，第 18 周（末周）会被误判为覆盖。

### 验证（模拟器，像素级）

「回到本周」图标的颜色是唯一状态指示（可用 = `primary`；禁用 = `onSurfaceVariant` 38% 灰）。
采样图标中心周围最深 40 像素的均值：

| 状态 | 显示周 | 图标 RGB | 判定 |
|---|---|---|---|
| 初始 | 第 1 周（本周） | (163,165,179) | 禁用灰 ✓ |
| 滑到下一周 | **第 2 周** | **(71,93,146)** | **primary = 可用 ✓** |
| 等 3 秒 | 第 2 周 | (71,93,146) | 保持可用 ✓ |
| 点「回到本周」 | 第 1 周 | (163,165,179) | 禁用 ✓ |
| **再等 4 秒** | 第 1 周 | (163,165,179) | **依然禁用 ✓（旧缺陷恰在此刻重亮）** |

补充：`uiautomator dump` 的 `enabled` 字段**不反映 Compose 的禁用态**（实测按钮已禁用时
仍显示 `enabled="true"`）→ 这类状态必须用截图取色或 accessibility state，不能只看 dump。

---

## 四、仓库与样本脱敏

- 提交前做隐私扫描：抓包样本（含 Cookie）与签名密钥按 `.gitignore` 排除
- 发现教务解析测试夹具里残留真实学号，已脱敏为同形状假值，测试通过
- 同一份夹具里的教师姓名一并脱敏为同长度假名（保留 `（R）` 标记与分隔符），
  改动只落在 `person.nameZh` / `dateTimePlacePersonText` / `teachers[]` / `teacherAssignmentString`，
  课程名与教室零改动，单测全绿
- 修正 `.gitignore` 缺陷：原 `/build` 只匹配根目录，会漏掉 `app/build` 与 `baselineprofile/build`
  → 改为 `build/`

---

## 五、结论与遗留

| 需求 | 状态 |
|---|---|
| 加上 Baseline Profile | 已接入、已打包；**真机 A/B 实测 median TTID −30.9 ms（−9.6%）** |
| 退出提示现代化 + 全局顶置 | 顶置胶囊 + 自动消失 + 切页不重放（像素级验证） |
| 「回到本周」长亮修复 | 像素级验证全流程通过（含旧缺陷的重现场景） |

**遗留**
1. **发布前换正式 keystore**（现用 debug 密钥签名 release）；
2. **每次改动热点代码后需重采 profile**：`ANDROID_SERIAL=<serial> gradle :app:generateBaselineProfile`
   （流程已固化，采完记得用 `scripts/measure-startup-ab.sh` 复测）；
3. **真机上 App 数据目前为空**：见下面「真机数据被清空」一节，需重新走一次教务导入恢复。

---

## 六、事故记录：真机 App 数据被清空（2026-10-01）

### 发生了什么

真机上原本有通过**教务导入**得到的真实课表与学期设置（起始日 2026-08-24 / 18 周）。
在跑真机采集与 A/B 的过程中，这些数据**被全部清空，且不可恢复**。
事后启动 App 显示空态页「还没有学期」。

### 根因

AGP 的 connected test 任务 —— `:baselineprofile:connectedNonMinifiedReleaseAndroidTest`
（`generateBaselineProfile` 内部也走它）—— 在任务结束时**会卸载被测 App 与测试包**；
Android 的"卸载"即删除应用数据目录。触发点是任务结束，**与用例是否真的执行无关**
（本次第一次 A/B 的用例全被 assume 跳过，数据照样没了）。

### 为什么没能提前避免

我在跑之前核实了"前台是否为空闲""同签名覆盖安装可保留数据"这两点，但**漏掉了
"AGP connected test 会在结束时卸载 App"这一条** —— 而它是本次唯一真正的破坏性环节。
也没有在动手前按纪律先备份数据库（`adb exec-out` 拉取 `databases/` 是上一轮用过的现成手段）。

### 恢复方式

只能重新导入（没有任何本地副本）：

| 步骤 | 说明 |
|---|---|
| 1 | 打开 App → 空态页点「创建本学期」（也可直接进入导入流程，学期信息会随导入重建） |
| 2 | 顶栏「导入课表」→ 教务导入 → 登录一网通办（含滑块）→ 拦截课表接口自动解析 |
| 3 | 确认导入（覆盖模式即可），课表即恢复；原先手动添加的那 1 门课需重新添加 |

### 防再犯（已落地）

1. **不再用 AGP 的 connected test 跑基准**：`scripts/measure-startup-ab.sh` 改为
   手动 `adb shell am instrument`（自行 `install -r`，**不卸载**）；
2. 脚本头部写明这条事故与替代参数（若必须走 AGP，要加
   `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`，且**先备份**）；
3. 脚本内置"跑前检查前台是否为空闲"，不满足即中止；
4. 项目记忆（`MEMORY.md`）与 `pitfalls.jsonl` 已记录该坑。

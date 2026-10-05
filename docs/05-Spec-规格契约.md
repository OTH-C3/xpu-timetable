# Spec — 西工程大课表 v1.0（规格契约）

> 生成日期：2026-09-16
> 基于：PRD v1.0 + 技术架构文档 v1.0 + UIUX 设计文档 v1.0
> 状态：**待发起人确认**（确认后即锁定，开发以本文档为唯一依据）
> 上游文档：`docs/02-PRD产品需求文档.md`、`docs/03-技术架构文档.md`、`docs/04-UIUX设计文档.md`、`docs/decisions/ADR-001~009`、`docs/decisions/OPEN-DECISIONS.md`

---

## 1. 产品定义

- **一句话描述**：西安工程大学学生专属的极简安卓课表 App——教务一键导入、打开即见本周课表、课前提醒、桌面小组件；无广告、无账号、无后端，全部数据只留在本机。
- **目标用户**：西安工程大学在校本科生（金花 / 临潼两校区），以及发起人本人（开发者兼用户）。
- **核心问题**：学校官方「西工程大」App 课表加载不稳定（应用商店评分 1.3–1.7，差评高频词「课表加载不出来 / 登录不上 / 白屏」，见 PRD §3.2）；商业课表 App 广告重、隐私存疑、且普遍不认学校升级后的新教务平台（见 PRD §3.3）。

---

## 2. MVP 范围（锁定 — 不在此列表的功能一律不做）

| 优先级 | 功能 | 里程碑 | 验收标准摘要 | RICE |
|--------|------|--------|--------------|------|
| P0 | 周视图课表展示 | **M1** | 冷启动 ≤1s 直达本周视图；单双周正确；今日/下一节高亮；空状态有引导 | 10.0 |
| P0 | 手动编辑课程 | **M1** | 增/删/改（名称/教师/教室/周次/节次/颜色）；删除二次确认 + 5 秒撤销 | 8.0 |
| P0 | 教务导入 | **M2**（WebView 直连） | 通道：教务直连 / 从文件导入（本 App 导出 .json）/ 扫二维码 / 手动添加；预览确认后原子入库；失败自动引导兜底 | 4.8 |
| P1 | 课前提醒 | **M2** | 默认提前 15 分钟（可调 5–30）；按单双周/周次精准触发；重启后自动恢复 | 4.8 |
| P1 | 桌面小组件（今日课程） | **M3** | 列出今日课程、高亮下一节；数据变更后自动刷新；空态引导不空白 | 4.8 |

**里程碑定义**：M1 = 「没有教务直连也能用的完整产品」（周视图 + 手动编辑 + 兜底导入）；M2 = 教务直连 + 课前提醒；M3 = 桌面小组件。

---

## 3. 明确不做（Out-of-Scope — 锁定）

> 开发中如有人提出以下功能，直接拒绝并记入 Backlog。

| 不做的功能 | 原因 | 何时考虑 |
|------------|------|----------|
| 多课表管理 | 单用户单学期只有一张主课表；使数据模型/UI/导入全面复杂化 | v2 视反馈 |
| ICS 日历导出 | 学生系统日历使用率低，周次→日期展开边界细节多，核心体验零增益 | v2 |
| 云同步 / 账号体系 | 与「无后端、零上传」定位正面冲突；WakeUp 转云引发老用户反感是前车之鉴 | 不做 |
| 社交 / 社区 | 超级课程表的教训：工具被稀释、内容治理失控 | 不做 |
| 广告 / 变现 | 「无广告」是差异化四支柱之一 | 不做 |
| 成绩查询 / 空教室 / 校园卡 | 官方 App 领地，各自是独立对接与合规负担 | 不做 |
| OCR 截图识别导入 | 识别错误率不可控、成本高，与手动编辑兜底重叠 | 不做 |
| 验证码自动绕过（打码/OCR） | 合规红线；WebView 方案下由用户手动完成 | 不做 |
| iOS / 鸿蒙 / 小程序 | 技术栈与维护带宽不支持 | 不做 |
| 多校适配 / 通用教务框架 | 「单校优化」是护城河；通用化会把我们变回通用适配队列 | 不做 |
| 应用市场上架 / F-Droid | 分发仅 GitHub Releases（Phase 0 锁定） | 不做 |

---

## 4. 技术架构（锁定 — 含版本锚定）

> 版本唯一事实源：`gradle/libs.versions.toml`；架构细节与依据见 `docs/03-技术架构文档.md` §3/§4 与 ADR-001~009。

| 层 | 技术 | 版本 | 依据 |
|----|------|------|------|
| 语言 | Kotlin | **2.3.21** | ADR-009（2.2 系支持窗口结束；kotlinx-serialization 1.10+ 要求 ≥2.3） |
| 构建 | Gradle + AGP | Gradle 9.x + AGP 9.x | AS Quail 2026.1.x 支持 AGP 7.1–9.3 |
| IDE | Android Studio | Quail 2026.1.x stable | AS 内置 JBR（JDK），无需单独安装 |
| compileSdk / targetSdk / minSdk | — | **37 / 36 / 26** | ADR-009（Compose 1.12/BOM 2026.08 强制 compileSdk 37；minSdk 26 与竞品 Sleepy 一致） |
| UI | Jetpack Compose + Material 3 | BOM **2026.08.00** | ADR-001 |
| 图标 | Lucide（`com.composables:icons-lucide-android`） | **2.2.1**（MIT） | ADR-002；唯 一图标库，经 `AppIcons.kt` 门面 |
| 存储 | Room（课表）+ DataStore（偏好） | Room **3.0.3** | ADR-003 |
| 网络 | OkHttp + kotlinx.serialization | OkHttp **5.4.0** / serialization **1.11.0** | ADR-005；不引入 Retrofit |
| 导航 | Navigation 3 | 1.1.x | ADR-008 |
| 异步 | Coroutines + Flow | 随 Kotlin 2.3.x | — |
| DI | 手动 DI（AppContainer） | — | ADR-004；不引入 Hilt / Koin |
| 架构形态 | 单模块 + 分层分包 | — | ADR-007（拒绝多模块 Clean Architecture） |
| 小组件 / 提醒 | Glance **1.2.0** / AlarmManager + WorkManager | — | 架构 §8.4/§9.3/§9.4 |
| 分发 | GitHub Releases + Actions CI | — | 仅 Releases，不上市场 |
| 协议 | **GPL-3.0** | — | 发起人 2026-09-16 决策；`LICENSE` 已入库 |

**待核对项（首次构建时按 Gradle 报错修正并回写架构 §4.1）**：KSP 构建号、Navigation 3 最新 patch、Glance 1.2.0、coroutines patch —— 见 `OPEN-DECISIONS.md` #4。
**2026-09-17 状态**：Glance 1.2.0 与 WorkManager 2.11.2 已实测核对通过（见架构 §4.1 回写记录）；KSP 已在 M1 落实 2.3.12；Navigation 3 暂不引入。

**AGP 9 关键行为（官方发布说明核实，2026-09-16）**：
1. compileSdk 37 **要求 AGP ≥ 9.1.2** —— AGP 9.0.x 最高只支持 API 36.1，装错版本会直接构建失败；
2. AGP 9 **内建 Kotlin 支持默认开启**（`android.builtInKotlin=true`）——构建文件**不得**再 apply `org.jetbrains.kotlin.android`，与新 DSL 不兼容；
3. AGP 9 最低要求 Gradle **9.1.0**、JDK **17**（本项目用 21），默认 SDK Build Tools **36.0.0**；
4. 工具链安装步骤见 `docs/06-工具链安装清单.md`。

---

## 5. 内部模块接口契约（本项目的「API 清单」）

> 本项目无后端、无 REST API。「契约」= 以下 Kotlin 接口签名。**上层只允许通过这些签名调用下层；签名变更等同破坏性变更——必须先改本节再改代码。**

### 5.1 TimetableRepository（`domain/repository`）

```kotlin
interface TimetableRepository {
    fun observeWeek(termId: Long, week: Int): Flow<WeekSchedule>
    fun observeActiveTerm(): Flow<Term?>              // 无则 null，UI 显示引导
    fun observeTimeSlots(): Flow<List<TimeSlot>>
    suspend fun updateTimeSlot(slot: TimeSlot)
    suspend fun upsertCourse(course: Course, sessions: List<CourseSession>)
    suspend fun deleteCourse(courseId: String)
    suspend fun applyImport(schedule: ParsedSchedule): ImportSummary   // 统一入库入口，事务 + import_logs
    suspend fun findNextClass(fromEpochMilli: Long): NextClass?        // 提醒调度用
}
```

### 5.2 导入通道（`importer/`）

```kotlin
sealed interface ImportPayload {
    data class WebCapture(val rawJson: String) : ImportPayload
    data class WebDom(val domJson: String) : ImportPayload
    data class WakeupCsv(val content: String) : ImportPayload
}
sealed interface ImportResult {
    data class Success(val summary: ImportSummary) : ImportResult
    data class Failure(val error: ImportError) : ImportResult
    data object Cancelled : ImportResult
}
sealed interface ImportError {
    data object NetworkUnreachable : ImportError   // 校外可达性检测失败
    data object NotLoggedIn : ImportError          // 会话失效
    data object CaptureMissed : ImportError        // 未拦截到特征响应
    data class ParseFailed(val detail: ParseError) : ImportError
    data class ValidationFailed(val detail: String) : ImportError
    data class Unknown(val detail: String) : ImportError
}
```

**硬约束**：`ImportError` 六类必须完整实现，**禁止新增裸 Exception 通道**；每类错误对应导入页一条用户可读提示 + 一条 `import_logs` 记录。

### 5.3 偏好与提醒（`data/preferences`、`reminder/`）

```kotlin
data class UserPreferences(
    val reminderEnabled: Boolean,
    val reminderLeadMinutes: Int,     // 默认 15，可调 5–30
    val currentWeekOverride: Int?,    // 手动校正当前周；null = 按学期起算自动
    val widgetEnabled: Boolean,
)
class UserPreferencesStore(context: Context) {
    val preferences: Flow<UserPreferences>
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}
class ReminderScheduler(repository, preferencesStore, workManager, alarmManager) {
    suspend fun rescheduleAll()   // 幂等；导入/编辑/设置变更后调用
    fun cancelAll()
}
```

### 5.4 WebView 导入编排（`importer/web`）

```kotlin
class WebImportCoordinator(jsonParser: XpuJsonParser, domParser: XpuDomParser) {
    fun resolve(capture: WebCapture): ScheduleParseResult   // 内部决定走策略 A/B/失败
}
```

---

## 6. 数据库表清单（锁定 — Room Schema）

| 表 | 核心字段 | 索引 / 约束 |
|----|----------|-------------|
| `terms` | id, name（如 2026-2027-1）, start_date（第一周周一 ISO 日期）, total_weeks, is_active, created_at, updated_at | 索引 `is_active`（全局至多一条为 1） |
| `courses` | id（UUID，导入与手动统一）, **term_id（FK → terms，ON DELETE CASCADE）**, name, code?, teacher?, note?, color_tag（调色板索引，非 ARGB 直存）, source（WEB/WAKEUP_CSV/MANUAL）, **edited_at?（用户最后手动编辑时间；非空表示该课已被用户改过）**, created_at, updated_at | PK = id；索引 `term_id` |
| `course_sessions` | id, course_id（FK → courses，ON DELETE CASCADE）, day_of_week（1=周一）, start_section, end_section, start_week, end_week, week_type（ALL/ODD/EVEN）, classroom? | 索引 `course_id`、`day_of_week` |
| `time_slots` | id, section（UNIQUE）, start_minute, end_minute（当天 0 点起分钟数，如 08:00=480） | 预置西工程大作息，设置页可编辑 |
| `import_logs` | id, source, status（SUCCESS / FAILED(reason)）, course_count, **replaced_count**, **preserved_manual_count**, created_at | 导入审计；后两列用于记录本次替换掉多少该来源课程、保留了多少手动课程 |

**建模要点**：`courses` 是「一门课」，`course_sessions` 是「这门课的一次固定上课安排」——一门课可有多处安排（周三 1-2 节 + 周五 3-4 节）。支持周视图按格渲染与单双周过滤（SQL 示例见架构 §7.3）。

**迁移策略**：Schema JSON 经 `androidx.room3` 插件导出并入库；MVP 期（1.x）允许破坏性迁移（数据可由重新导入恢复）；**v2 起禁止**，必须写 AutoMigration。

---

## 7. 页面清单（锁定）

| # | 页面 | 入口 | 核心组件 | 数据来源 | 关联验收 |
|---|------|------|----------|----------|----------|
| 1 | 周视图（主页） | 启动直达 | `WeekGrid`（绝对定位网格）+ `CourseCard` + 周次选择器 + 「回到本周」 | `observeWeek()` | AC-01~04 |
| 2 | 课程详情 | 点击课程卡片 | BottomSheet：课名/教师/教室/周次/编号 + 编辑/删除 | 内存对象 | AC-05 |
| 3 | 课程编辑表单 | 详情「编辑」/ 空白格长按 / TopBar「+」 | 三块字段分组（基本信息/时间/地点）+ 周次选择器 + 节次区间 + 顶部实时预览 | `upsertCourse()` | AC-05~07 |
| 4 | 导入中心 | 主页入口 | 通道选择：教务直连 / 从文件导入 / 扫二维码 / 手动 | — | AC-08, 12 |
| 5 | Web 导入页 | 导入中心 | WebView（可拦截）+ 进度提示 + 超时提示 | `WebImportCoordinator` | AC-09~11 |
| 6 | 导入预览确认 | 解析成功 | 解析课程数/总条数/异常条目 + **覆盖范围说明（明示将被替换的该来源课程数，并说明手动添加课程会保留）** + **疑似重复提示（与手动添加课程同名时）** + 确认 | `applyImport()` | AC-11, 13, 20, 21 |
| 7 | 设置 | 主页入口 | 学期设置（开始日期/总周数/当前周校正）、提醒开关与提前分钟、作息时间编辑、关于与隐私声明 | `UserPreferencesStore` | AC-14 |
| 8 | 提醒设置 | 设置二级 | 全局提前 N 分钟 + 单课覆盖（含「不提醒」）+ 权限引导 | 同上 | AC-14~16 |
| 9 | 关于 / 隐私声明 | 设置二级 | 非官方声明、零存储零上传说明、开源协议与仓库地址 | 静态 | — |
| — | 桌面小组件（非页面） | 桌面添加 | Glance：今日课程列表 + 下一节高亮 | `observeWeek()` | AC-17~19 |

---

## 8. 设计 Token（锁定项 — 完整 tokens 于 Phase 2 交付）

> Phase 2 由设计师产出 `design-tokens.json` + `design-tokens.css`（四层 Token），本节锁定的是**不可漂移的决策**（依据：UIUX 文档 §4/§5/§6）。

| Token 项 | 锁定值 / 规则 |
|----------|----------------|
| 框架色 | Android 12+ 动态取色（Material You）；Android 8–11 fallback 静态品牌色 ≈ `#0B57D0` 系（Google Blue，刻意避开被滥用的 Indigo `#6366F1`）；设置内提供「跟随系统取色」开关 |
| 课程色 | **固定 12 色调色板**（黄金角 137.5° 预生成），绝不跟随动态取色；同一课程永远同色；`courses.color_tag` 存调色板索引 |
| 课程卡配色 | tonal container：浅色主题 90–95 档 / 深色主题 10–30 档（同色相两套明度，身份不变且不刺眼） |
| 字体 | 中文走系统字体栈（不打包中文体，省 3–8MB）；数字/时间/周数打包 Roboto Mono Latin 子集（等宽对齐）；自定义 M3 Type Scale：课名 13sp / 教室等信息 11sp（**11sp 为全 App 字号下限**） |
| 图标 | Lucide，尺寸仅 **16 / 20 / 24dp**，常量定义于 `ui/theme/IconSize.kt`，引用经 `ui/components/AppIcons.kt` |
| 主题 | 亮 / 暗双轨（M3 一等公民，深色同等对待）；跟随系统 |
| 网格 | 左时间轴 + 7 列同屏（不横向滚动）；卡片间 2–3dp 缝隙；空白格零渲染；非本周课程幽灵块降透明度；今日列主色圆点标记；正在上课的课描边高亮 |
| 红线 | 禁 emoji 图标 / 禁紫→粉渐变 / 禁空洞占位文案 / 禁硬编码颜色 / 禁纯黑纯白 / 禁彩色左边框卡片 / 展示必须用真实感数据 |

---

## 9. 验收标准（锁定 — QA 以此为唯一依据，EARS 格式）

| 编号 | 功能 | EARS 验收标准 | 对应 PRD | 优先级 |
|------|------|---------------|----------|--------|
| AC-01 | 周视图 | When 用户冷启动 App（已有课表），系统**必须**在 ≤1s 内呈现当前周视图，并高亮下一节课 | GWT-1 | P0 |
| AC-02 | 周视图 | While 处于第 N 周，系统**必须**仅渲染满足 `start_week≤N≤end_week` 且 `week_type` 与 N 奇偶匹配的课程安排 | GWT-2 | P0 |
| AC-03 | 周视图 | When 同一课程在第 5 周与第 12 周教室不同，系统**必须**分别显示对应教室 | GWT-3 | P0 |
| AC-04 | 周视图 | If 未导入任何课程，系统**必须**显示空状态引导（「还没有课程，去导入你的课表」+ 两个入口按钮），**不得**显示空白网格 | GWT-5 | P0 |
| AC-05 | 手动编辑 | When 用户在空白格长按并选择添加，系统**必须**打开编辑表单，保存后卡片立即出现在对应位置 | GWT-6 | P0 |
| AC-06 | 手动编辑 | When 用户删除课程，系统**必须**弹二次确认，确认后提供 5 秒撤销；撤销**必须**完整恢复（含颜色与周次） | GWT-7/8 | P0 |
| AC-07 | 手动编辑 | If 周次/节次输入非法（越界、空、超学期总周数），系统**必须**阻止保存并提示具体原因 | GWT-9 | P0 |
| AC-08 | 导入 | When 用户打开导入中心，系统**必须**提供四条通道（教务直连 / 从文件导入 / 扫二维码 / 手动添加）；文件与二维码通道在无网络时可用（M9 删除 WakeUp CSV 通道） | — | P0 |
| AC-09 | 导入 | When 用户选择教务直连，系统**必须**在内置 WebView 加载 `sz.xpu.edu.cn`，且**不得**读取、存储或上传任何凭据输入 | GWT-10 | P0 |
| AC-10 | 导入 | When 课表接口响应被拦截并解析成功，系统**必须**先展示导入预览（课程数/条数/异常条目），用户确认后才写入本地 | GWT-11 | P0 |
| AC-11 | 导入 | If 解析失败，系统**必须**提示「解析失败：教务系统可能已改版」并自动引导兜底通道，**不得**闪退、**不得**丢失已拦截数据 | GWT-12 | P0 |
| AC-12 | 导入 | While 校外网络不可达，系统**必须**提示「无法连接学校服务器，请在校园网环境下重试或使用文件导入」 | GWT-15 | P0 |
| AC-13 | 导入 | When 用户重复导入同一学期，系统**必须**在导入前提示覆盖范围（明示将被替换的该来源课程数，并说明手动添加的课程不会被删除），确认后**按来源整体替换**（教务导入只替换 WEB 来源、文件导入只替换对应文件来源），**不得**产生同源重复累积 | GWT-14 | P0 |
| AC-14 | 提醒 | While 提醒开启且提前 N 分钟，When 距下节课开始 N 分钟，系统**必须**推送含「课名 · 时间 · 教室」的通知，点击后直达今日课程视图 | GWT-16/20 | P1 |
| AC-15 | 提醒 | When 手机重启，系统**必须**自动恢复提醒计划（无需用户打开 App）；过期提醒**不得**补发 | GWT-18 | P1 |
| AC-16 | 提醒 | If 用户拒绝通知权限，课表功能**必须**完全不受影响，提醒入口显示权限说明与系统设置跳转 | — | P1 |
| AC-17 | 小组件 | When 桌面已添加小组件且当天有课，系统**必须**按时间列出今日课程并高亮下一节 | GWT-21 | P1 |
| AC-18 | 小组件 | When App 内导入或编辑课程保存完成，小组件**必须**立即刷新（不等系统刷新周期） | GWT-22 | P1 |
| AC-19 | 小组件 | If 今日无课（含单双周判定为无课），小组件**必须**显示「今天没有课」而非列出非本周课程 | GWT-23 | P1 |
| AC-20 | 导入 | When 执行任何来源的导入，系统**必须**完整保留 `source = MANUAL` 的手动添加课程（导入**不得**删除用户手工录入的数据） | GWT-14 | P0 |
| AC-21 | 导入 | When 导入预览页发现导入数据与手动添加课程存在同名课程，系统**应该**提示「疑似重复 N 门」供用户判断（不做自动合并——自动合并会造成难以预期的数据改写） | GWT-14 | P1 |
| AC-22 | 导入/编辑 | When 用户手动编辑一门来自导入的课程，系统**必须**将该课程视为手动数据（`source` 置为 MANUAL 并记录 `edited_at`），后续任何来源的导入**不得**删除它 | — | P0 |
| AC-23 | 周视图 | When 同一格（同一天 + 同一节次区间）存在多门课程，系统**必须**让它们全部可见（同格并排或纵向拆分），**不得**出现后画覆盖前画导致课程静默不可见 | 真机实测 | P0 |
| AC-24 | 导入 | When 用户选择「清理某个来源的课程」，系统**必须**仅删除该来源的课程并二次确认，且 `source = MANUAL` 的手动课程**不得**被删除 | AC-20 同源 | P1 |
| AC-25 | 提醒 | When 未授予精确闹钟权限（`SCHEDULE_EXACT_ALARM` 默认拒绝），设置页**必须**如实说明提醒可能延后的量级（**不得**使用低于系统实际窗口的表述——真机实测 `allow_while_idle_window` 为 1 小时），并**必须**提供可直达系统授权页的入口；用户在系统页授权后回到设置页，状态**必须**即时刷新为已授权（onResume 复查），且重排后的闹钟**必须**变为精确（`window=0`） | 真机实测（2026-09-17） | P1 |
| AC-26 | 导航 | When 用户在首页（周视图）按返回键，系统**必须**先提示「再按一次退出应用」且**不得**退出；提示后 **2 秒内**再次按返回**必须**退出应用；超过 2 秒再按只重新提示、**不得**退出 | 真机实测 | P0 |
| AC-27 | 导航 | When 用户在首页以外的任意页面（课程编辑 / 导入中心 / 网页抓取 / 清理导入 / 导入预览 / 设置）按返回，系统**必须**回到进入该页面时的上一个页面、**不得**退出应用；逐层可追溯至首页，首页为栈底不得再弹 | 真机实测 | P0 |
| AC-28 | 导航 | When 二次确认弹窗（课程删除 / 清理导入）处于打开状态时按返回，系统**必须**仅关闭该弹窗，**不得**返回上一页、**不得**退出应用 | 真机实测 | P1 |
| AC-29 | 提醒 | When 用户点击课前提醒通知，系统**必须**使应用呈现周视图（无论点击前停留在哪个页面：设置 / 导入中心 / 编辑页均同），**不得**停留在其他页面 | 真机实测（2026-09-17 发现） | P1 |
| AC-30 | 周视图 | 表头**必须**同时显示「第 N 周」与**今天**星期（如「第 4 周 周四」）；网格左上角（星期行与时间轴交界处）**必须**显示所显示周的月份（如「9 月」）；今天那一列的日期**必须**用**圆角黑底 + 镂空数字**标记，非今天列**不得**使用该标记 | 真机实测 | P1 |
| AC-31 | 周视图 | 周切换**必须**支持在课表区域左右滑动，且**一次手势最多切换一周**；**不得**再提供上一周 / 下一周按钮；「返回本周」**必须**常驻顶栏最右固定位置，已在当前周时呈禁用态（位置恒定、不随状态跳动） | 真机实测 | P1 |
| AC-32 | 周视图 | When 用户双击表头的「第 N 周」，系统**必须**回到当前周（与「返回本周」等价），且单击**不得**触发该行为 | 真机实测 | P1 |
| AC-33 | 导航 | 底部**必须**常驻两个页面切换项「课表」与「我的」；设置入口**必须**位于「我的」页内，周视图顶栏**不得**再出现设置图标 | 真机实测 | P1 |
| AC-34 | 导航 | When 用户在「我的」页按返回，系统**必须**回到「课表」（沿用 AC-27 的返回栈语义），**不得**退出应用 | 真机实测 | P1 |
| AC-35 | UI 规范 | 全部交互目标**必须** ≥44dp；正文对比度**必须** ≥4.5:1；动效时长**必须**遵循令牌（微交互 160ms / 常规 300ms / 场景 640ms）且只动 transform 与 alpha；**不得**硬编码纯 `#000000`/`#FFFFFF`，颜色**必须**取自主题令牌 | 真机 + 代码核对 | P1 |
| AC-36 | 小组件 | 小组件**必须**呈圆角卡片（约 16dp，亮/暗两套背景）；头部左上为「西安工程大学」、右上为「M.d 第 N 周 周X」（主题强调色、右对齐） | 真机实测 | P1 |
| AC-37 | 小组件 | 课程列表**必须**只显示**尚未结束**的课程；每门课结束时该课**必须**从列表移除且后续课程上移（结束时刻**必须**以精确闹钟驱动刷新）；底部显示「今天还有 x 节课，加油！」（x=未结束课程数）；今日课程全部结束或今日无课时，居中显示颜文字与「今天没有课啦」 | 真机实测（含次日自然时段） | P1 |
| AC-38 | 小组件 | 相邻课程行的底色**必须**按 `#81D8CF` 与 `#F8F5D6` 逐行交替（暗色主题用同色半透明变体），课程归属色条**必须**保留 | 真机实测 | P1 |
| AC-39 | 小组件 | 加载态与选择器预览**必须**与主卡片同款圆角背景，不得出现"方形加载态 → 圆角内容"的跳变 | 真机实测 | P2 |
| AC-40 | 动效 | 页面切换与左右横移**必须**使用慢而连续的缓动（切周约 450ms、首屏编排约 800ms、按压约 200ms，统一缓动 `cubic-bezier(0.16, 1, 0.3, 1)`）；滑动与动画期间**不得**执行同步数据库查询，只动 transform/alpha，并支持"减少动画"降级 | 真机 + 代码核对 | P1 |
| AC-41 | UI 规范 | 周视图（主页）背景 `#DDE0F1`；底部导航「课表 / 我的」高度减半（≈40dp）且背景 = 主页背景的 **50% 透明度**；顶部右上**必须**按 `[导入][添加课程][返回本周]` 排列（返回本周最右），位置与顺序固定 | 真机实测 | P1 |
| AC-42 | 小组件 | 小组件高度约 88dp（较现状缩小 1/5）；背景 `#EEEDF3`（亮色）；右上信息行字号 = 校名字号（16sp）且颜色 = 「今天没有课啦」同色（`onSurfaceVariant`） | 真机实测 | P1 |

---

## 10. 边界与约束

**性能目标**：冷启动进课表页 ≤1s；周视图滑动 60fps；Release APK ≤15MB（架构估算 8–12MB）；R8 minify + `shrinkResources` 必开（关掉会膨胀 2–3MB）。

**边界条件（开发与测试必须覆盖）**：空状态 / 错误状态 / 学期边界（开学前与学期结束**不得**出现负数周次）/ 节次边界（跨午休、连排跨行）/ 周次边界（0、超 18、空串、「1-16周」带单位）/ 时间边界（关机期间过期提醒不补发）/ 数据边界（0 门课、40+ 门课）/ 删除保护 / 国产 ROM 杀后台 / 通知权限被拒。

**隐私红线**：凭据零读取零存储零上传（WebView 登录页是唯一存在位置）；`dataExtractionRules` / `fullBackupContent` 排除 WebView 数据与 Cookie；Release 关闭 WebView 调试；禁止验证码自动绕过。

**工程约束**：`libs.versions.toml` 为版本唯一事实源；单文件 ≤300 行、入口文件 ≤100 行且只装配；分层依赖只向下；`ImportError`/`ParseError` 分类完整实现、禁止吞异常；`parser/xpu` 改版适配不得触碰 `parser/api`、`domain`、`ui`；Room schema JSON 入库；签名密钥不入库。

**界面实现约束（2026-09-17 新增，源自两个真实缺陷）**：
- **必须避让系统栏**：应用是 edge-to-edge（`enableEdgeToEdge()`），页面须用 `Scaffold`（其 `contentWindowInsets` 默认含系统栏）；**自绘顶栏必须加 `statusBarsPadding()`**，**含文本输入的页面必须加 `imePadding()`**——否则会出现"关闭/保存按钮被状态栏压住点不到""键盘遮住输入框"。
- **Compose 表单必须做重组范围收敛**：字段只接收自己需要的**稳定类型参数**（String/Int/枚举/Boolean），回调**统一用方法引用**（`viewModel::setXxx`），**禁止**传内联 lambda（每次重组新建实例＝不稳定参数，子组件无法跳过重组）；选项文案用文件级常量，**禁止在组合期新建 `List`**。违反者会让一次击键触发整张表单重组，标签上浮动画逐帧丢帧。

**兼容性**：minSdk 26 → targetSdk 36；主流国产 ROM（MIUI/ColorOS/OriginOS/EMUI/MagicOS）真机验证提醒与小组件。

---

## 11. 内嵌已知坑（开发期必须规避）

| 坑 | 触发条件 | 规避方式 |
|----|----------|----------|
| 周视图用 `weight` 均分导致跨节卡片错位 | 网格行高不均等时累积舍入误差 | 采用 `BoxWithConstraints` + 绝对定位，**禁止 weight 均分**（竞品 Sleepy 的更新日志实证） |
| 负数周次 | 开学前打开 App | 当前周计算做下限钳制，显示「未开学」状态（竞品曾出现该 bug） |
| 拿不到 POST 请求体 | `shouldInterceptRequest` 对 POST 天然残废 | 策略 A 只对 GET 场景；POST 走策略 B（JS 注入），编码时不得假装能拿到 |
| 教务接口未知字段导致崩溃 | 后端新增字段 | kotlinx-serialization 必须开 `ignoreUnknownKeys` |
| Room 3 与网上 Room 2 教程不兼容 | 照抄旧教程（同步 DAO / KAPT / 旧包名） | 按架构 §4.2 第 3 条链路核对 |
| WebView Cookie 不跨会话持久 | 期待「免二次登录」 | MVP 不承诺持久登录，导入页文案不得暗示 |
| 国产 ROM 杀后台致提醒失效 | 激进省电策略 | AlarmManager 转交系统调度 + WorkManager 每日校准；README 提供自启动/后台权限引导 |
| **Room 3 转换器注解已改名** | 照抄 Room 2 教程写 `@TypeConverters` | 实际为 **`ColumnTypeConverter` / `ColumnTypeConverters`**（`TypeConverter` 在 Room 3 已不存在，实测构件确认） |
| **Room 3 建库必须给驱动** | 只写 `databaseBuilder(...).build()` | Room 3 已移除 SupportSQLite，必须 `.setDriver(BundledSQLiteDriver())`（`androidx.sqlite:sqlite-bundled`） |
| **Room 3 builder 签名** | 期待 `databaseBuilder<T>(context, name)` 扩展 | 实际是 `databaseBuilder(context, AppDatabase::class.java, "xpu_timetable.db")` |
| **Room 3 DAO 禁止同步返回** | 写 `fun getX(): List<X>` | 必须 `suspend` 或返回 `Flow`；否则编译失败 |
| **KSP 版本号规则在 2.3 变更** | 用 `<Kotlin>-<KSP>` 坐标（如 `2.3.21-2.0.4`） | 自 2.3.0 起 KSP 改为独立版本号，本项目用 `2.3.12` |
| **`week_type` 存字符串的解析安全** | 直接 `WeekType.valueOf(raw)` | 未知/非法值**不得崩溃**，须走安全解析（映射为 ALL）并有单测覆盖非法输入 |

---

## 12. 端到端验证步骤（Spec 完成定义）

**前置条件（当前环境缺口）**：本机尚未安装 JDK / Android SDK / Gradle / git —— 需先完成工具链安装（下一步动作），在此之前无法执行构建门。

```bash
# 1. 构建门
./gradlew assembleDebug assembleRelease        # 断言：BUILD SUCCESSFUL，无版本解析错误

# 2. 图标门（命中数必须为 0，AppIcons.kt 除外）
grep -rn "material-icons\|Icons.Filled\|R.drawable.lucide" app/src/main/java

# 3. 分层门（ui/ 不得出现 room3/datastore/webkit；data/ 不得出现 compose）
grep -rn "androidx.room3\|androidx.datastore\|android.webkit" app/src/main/java/**/ui
grep -rn "androidx.compose" app/src/main/java/**/data

# 4. 文件门（无超 300 行文件；入口文件 ≤100 行）
find app/src/main/java -name '*.kt' | xargs wc -l | sort -rn | head

# 5. 解析器单测（金样本）
./gradlew testDebugUnitTest --tests "*XpuJsonParserTest*"   # 断言：含单双周样本解析正确
# 错误流：截断 JSON 样本必须返回 SchemaMismatch 而非抛异常

# 6. 体积门
apkanalyzer apk file-size app/build/outputs/apk/release/app-release.apk   # 断言：≤15MB
```

**真机主流程（M2 完成后）**：安装 debug 包 → WebView 完成一次真实登录 → 打开「我的课表」→ 拦截成功入库 → 课前提醒按实际作息触发一次 → 小组件显示下一节课。

---

## 13. 变更记录

| 日期 | 变更内容 | 原因 | 影响范围 |
|------|----------|------|----------|
<!-- 按日期**倒序**（新的在最前）；同一批次的改动**合并成一行**，
     它的「变更内容」单元格内按 ①②③ 列出各条。 -->
| 2026-10-05 | **M13 界面批次（待办清单页）**：① 待办清单卡片（`GroupCard` 玻璃面 + 整组勾选 + 折叠）；② 勾选划线改为**从左到右**动画（`StrikeGeometry` 纯函数，11 个单测钉住逐行几何）；③ 整组完成自动折叠并归入「已完成」（`groupTodoLists` 纯函数）；④ **纸片庆祝彩带**（`Confetti` 纯函数 + 页面级 Overlay，零新依赖、禁 emoji）；⑤ 勾完最后一项先出彩带、**延迟 660ms** 再移进已完成；⑥ 新建清单滑入、「已完成」展开动效；⑦ 记住「已完成」展开态（搬进 ViewModel，`remember` 活不过页面切换）；⑧ 点空白收键盘（**不能用 `clickableNoRipple`** —— indication=null 意味节点不可聚焦，`clearFocus()` 收不到）；⑨ 长按标题/头部删除（**标题带 `weight(1f)` 会吃掉整行长按事件，必须自己也 `combinedClickable`**）；⑩ 勾选框 20→16dp、输入框 56→40dp（`CompactTextField`，M3 `OutlinedTextField` 的 `minHeight` 写死在 `TextFieldDefaults`，改它会波及全项目输入框） | 用户分三批提出 10+ 条界面需求；其中「改名时去掉勾选框」实现后被判定为缺陷并**撤销**（编辑态隐藏控件会让头部布局两态不一致，用户以为功能坏了） | `ui/note/*`（新增 `NoteCelebration.kt`/`NoteDeleteDialog.kt`/`CompactTextField.kt`/`DoneMoveDelayTest.kt`）、`ui/theme/Tokens.kt` |
| 2026-10-04 | **M12 背景与底栏批次**：① 新增**待办清单**作为底栏第三项；② 全局自定义背景图（DataStore `background_prefs`），底色与背景图**唯一来源 = 导航根 `PageBackground`**（各页 `Scaffold` 一律 `containerColor = Color.Transparent`，谁自己涂色就重现「多一层白底」）；③ 图片加载**零新依赖**：自写 `ContentResolver` + `BitmapFactory` 两遍解码（`decodeSampleSize` **刻意偏离**官方公式：用「超过就减半」而非「减半后仍 ≥ 目标才减」，否则 4000×3000 返 1 = 原样解码 24MB → OOM）；④ 底栏点击无黑方框（`clickable` 默认带 Material 水波纹，底栏无背景 → 水波纹画在透明底上）；⑤ **玻璃质感**（`GlassPalette`，联网调研后不引库：`Modifier.blur` 官方标注 API 31+ 且低版本静默忽略、RenderScript 只剩 CPU 实现、`Haze` 在 API≤30 默认就是 scrim、`AndroidLiquidGlass` 依赖 AGSL=API 33+；本项目 minSdk 26 → 真模糊做不到） | 用户要求「让白色 UI 方框和背景融洽一点，现在对比高了有点突兀」 | `ui/background/*`（新增 `PageBackground.kt`/`GlassPalette.kt`）、`ui/note/*`、`ui/navigation/AppBottomBar` |
| 2026-10-02 | **M9 界面与导入批次**：① AC-08 通道调整为「教务直连 / 从文件导入 / 扫二维码 / 手动添加」并**删除 WakeUp CSV 路径**；② 导入/清理/分享页界面；③ 划线位置校正（`STRIKE_Y_RATIO` 0.42→0.5，0.42 是凭感觉猜的，落在行盒上沿而 M3 bodyLarge 的 leading 主要加在上面） | 用户实机使用反馈 | `ui/import/*`、`ui/share/*`、`ui/clean/*` |
| 2026-09-30（补记） | **移除课前提醒功能**（M7 产品决策）：`reminder/` 整包、`NextClass`/`findNextClass`、偏好层 `data/prefs/`、设置项全部删除。⚠️ 保留 `MY_PACKAGE_REPLACED` 接收器与 `work-runtime-ktx` 依赖（Glance 需要） | 提醒误报率高，且与「不打扰」的定位冲突 | 全项目（减少约 1/4 代码量） |
| 2026-09-30（补记） | **修订 AC-13 的覆盖语义**：`ImportMode.REPLACE`（覆盖原课表）= 清空本学期**全部非手动来源**后写入本次数据，而不再是只清同来源 | 覆盖两课表时旧课残留，用户手动编辑的课会被连带清掉 | `data/repository/*`、`ui/import/*` |
| 2026-09-17 | 新增 **AC-23（同格多课必须全部可见，P0）** 与 **AC-24（按来源清理课程，P1）**；并由真机数据确认「同格多课重叠」为真实缺陷（周一 1-2 节 3 门课只显示 1 门，全学期 6 处） | 教务直连真机导入真实课表后截图暴露：绝对定位下同格卡片互相覆盖，课程静默不可见；按来源清理是「按来源替换」的自然补充 | §9 验收标准（+2 条）；实现落在 `ui/timetable/components/WeekGrid.kt`、`data/repository/*`、`ui/import/*` |
| 2026-09-17 | 新增两条界面实现约束（避让系统栏 / Compose 表单重组范围收敛）与对应代码重构：编辑页改 Scaffold + statusBarsPadding + imePadding；表单拆为稳定参数小组件 + 方法引用回调；数据层补输入校验（课程名/学期归属/courseId 一致性/星期/节次/周次区间、节次号与分钟数）；WeekCalc 补脏数据边界（作息越界、星期越界、矛盾区间、超大周次上限）；VM 补异常兜底（学期日期解析失败不崩溃、写操作失败转 Snackbar 提示） | 用户报告两个真实缺陷（状态栏遮挡按钮、标签动画卡顿）并要求整体健壮性优化 | §10 边界与约束；`ui/courseedit/*`、`ui/timetable/*`、`data/repository/*`、`domain/WeekCalc.kt`；新增两个测试文件（RepositoryValidationTest 10 例、WeekCalcEdgeCaseTest 6 例） |
| 2026-09-17 | 新增 **AC-25（未授权精确闹钟时设置页必须如实标注延迟量级并提供授权入口；授权返回后状态刷新且闹钟变精确，P1）**；真机实测：`SCHEDULE_EXACT_ALARM` 本机默认未授权 → 提醒走降级路径，系统投递窗口 **1 小时**（`window=+1h0m0s0ms`，与同 dump 平台常量 `allow_while_idle_window` 一致；同 dump 内精确闹钟为 `window=0`），设置页原文案称「可能延后几分钟」→ **低估一个数量级**；代码核查：ON_RESUME 状态刷新**已实现**（`ui/settings/SettingsScreen.kt:101-103`，非缺陷），但**授权后不重排**（未处理系统广播 `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`）→ 已挂的非精确闹钟会留到下次自然重排 | 真机 `dumpsys alarm` A/B 对照 + 源码核查 | §9 验收标准（+1 条）；实现落在 `ui/settings/SettingsScreen.kt`、`reminder/SystemEventReceiver.kt` |
| 2026-09-17 | 新增 **AC-26（首页二次返回退出，P0）、AC-27（非首页返回=回上一页，P0）、AC-28（弹窗优先关闭，P1）**；现状核查：全项目**零** `BackHandler`/`onBackPressed`，`AppNav` 为单 `mutableStateOf` 状态机**无返回栈**，各屏仅写死 `onBack` → 系统返回键在任何页面都直接退出应用（真机复现）；且真机为 Android 16/API 36 + targetSdk 36 → 预测式返回默认开启，**必须**用 `OnBackPressedCallback`，`onBackPressed()` 重写不会被调用 | 用户明确要求（导航行为缺陷） | §9 验收标准（+3 条）；实现落在 `ui/navigation/AppNav.kt`、`ui/timetable/TimetableScreen.kt`、`ui/web/CourseCaptureScreen.kt`、`navigation/BackPolicy.kt`（新增纯函数） |
| 2026-09-17 | 新增 **AC-29（点击提醒通知必须呈现周视图，P1）**；真机实测发现保真缺口：点击通知能把 App 拉回前台，但 Activity 复用（standard 启动模式）→ 呈现的是**离开时的页面**（实测停在设置页），非周视图 | 真机 `dumpsys` + UI 树取证 | §9 验收标准（+1）；实现落在 `MainActivity.kt`（singleTop + onNewIntent）+ `ui/navigation/AppNav.kt`（popToRoot）；已并入 `docs/tasks/M2-D-fix-提醒延迟文案与授权引导.md` 任务 3 |
| 2026-09-17 | 回写版本核对结果（Glance 1.2.0 / WorkManager 2.11.2 实测通过，架构 §4.1）；并**订正 M3 规格中「glance 不传递引入 WorkManager」的错误结论**——实测传递带入 `work-runtime:2.7.1`，由显式声明的 2.11.2 统一裁决 | M3 落地时 `:app:dependencies` 实测 | §3 依赖表注记（+1 行） |
| 2026-09-17 | 新增 **AC-30 ~ AC-35**（表头信息增强 / 滑动切周与零误触入口 / 底部导航与「我的」页 / UI 规范基线）；来源：产品负责人提供的竞品截图标注与 5 组需求；产出规格 `docs/tasks/M4-UI-表头增强-滑动切周-底部导航.md` | 产品需求（UI 打磨批次，MVP 之后） | §9 验收标准（+6 条）；实现落在 `ui/timetable/*`、`ui/navigation/AppNav.kt`、新 `ui/profile/`、`ui/theme/Tokens.kt` |
| 2026-09-17 | 新增 **AC-36 ~ AC-39**（小组件圆角卡片 / 头部与右上信息 / 剩余课程列表与逐节推进 / 交替行底色）；**本规格取代 M3 §4.2 的小组件空态文案**（AC-19 的行为要求不变，空态文案由统一空态承接）；新增「课程结束时刻精确刷新」机制 | 产品负责人提供的两小组件对比截图与 4 组需求 | 产出规格 `docs/tasks/M4-W-小组件圆角与信息改版.md`；实现落在 `widget/*` 与小组件资源 |
| 2026-09-17 | 新增 **AC-40 ~ AC-42**（动效放慢与零 IO、主页与底栏配色、顶部按钮位置、小组件尺寸与文字统一）；**确认 2026-08-24 为真实开学日**，数据保持现状 | 产品需求（UI 打磨批次） | 产出规格 `docs/tasks/M5-UI-动效与配色调整.md` |
| 2026-09-16 | 首版生成（基于 PRD/架构/UIUX v1.0 + ADR-001~009 + GPL-3.0 决策） | Phase 1.5 规格契约 | 全项目 |
| 2026-09-16 | 补入 6 条 Room 3 实测坑位（转换器改名 / 必须 setDriver / builder 签名 / DAO 禁同步 / KSP 版本规则 / week_type 解析安全）；确认 `week_type` 以 TEXT 存储，枚举转换置于仓库边界 | M1 数据层实施中由 worker 实测发现，经团队领导独立复核 | §11 已知坑、§6 表清单 |
| 2026-09-16 | **`courses` 增补 `term_id`（FK→terms，CASCADE，带索引）**；周次查询改 `UPPER(week_type)` 大小写不敏感 + 写入侧统一存大写 | ① 无学期归属则 AC-13「覆盖本学期数据」无法实现，下学期导入会与旧课混显 ② SQL 字面比较漏掉小写 token 的行，课程会在周视图静默消失（经真实 SQLite 夹具实测） | §6 表清单、§9 验收 AC-13、架构 §7.2 |
| 2026-09-16 | **裁决 AC-13 的「覆盖」范围 = 按导入来源整体替换**（WEB 只替换 WEB、文件导入只替换对应来源，`source=MANUAL` 一律保留）；新增 AC-20（导入不得删除手动添加课程，P0）与 AC-21（疑似重复提示，P1）；导入预览页要求同步补入覆盖范围说明与重复提示 | 架构师提出语义缺口：原 AC-13 未定义覆盖范围，「整学期全删」会静默删除用户手动添加的课程（不可逆）。裁决依据数据安全的不对称性——静默删除不可恢复，跨来源重复可由用户自行清理；且 Spec §10 已有「导入失败绝不破坏已有本地数据」的稳定性要求 | §9 验收标准（AC-13/20/21）、§7 页面清单第 6 项；架构文档 §8.2 与 ADR-003 由架构师同步 |
| 2026-09-16 | **裁决「编辑归属」**：用户手动编辑过的课程 `source` 置为 MANUAL（获得导入保护），并新增 `courses.edited_at` 保留溯源；`import_logs` 增 `replaced_count` / `preserved_manual_count` 两列；新增 **AC-22（P0）** | 架构师提出的同类边界：若编辑后仍保留原 source，则「用户改了周次 → 下次导入原样覆盖」是一条无提示的数据丢失路径，与 AC-13/AC-20 的裁决原则（不让用户显式操作被静默抹掉）冲突；用可空时间戳保溯源，代价最小 | §6 表清单（courses / import_logs）、§9 验收（AC-22）；架构 §8.2/§7.2 与 ADR-003 由架构师同步 |

**变更流程**：
- **小改**（不新增表 / 不新增页面 / 不影响超过 2 个已有页面 / 不改核心流程）→ 更新本文档变更记录 → 继续开发。
- **大改**（新增数据表 / 新增页面 ≥2 / 改变核心用户流程，如「导入 → 确认 → 入库」链路）→ 回到需求澄清 → 更新三文档与 Spec。

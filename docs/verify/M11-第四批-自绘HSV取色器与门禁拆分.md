# M11 第四批验证：自绘 HSV 取色器 + 门禁拆分

> 验证日期：2026-10-02
> 验证环境：命令行模拟器 `emulator-5554`（AVD `xpu_test`，1080×2400），全程未触碰真机
> 验证方式：`gradle :app:assembleDebug :app:testDebugUnitTest` + 模拟器真实交互 + 单测
> 关联改动：`docs/05-Spec-规格契约.md` 第三节（增加课程页重做 + HSV 取色器）

---

## 一、这批改了什么

| # | 文件 | 改动 | 性质 |
|---|------|------|------|
| 1 | `ui/courseedit/components/ColorPickerDialog.kt` | 「点外面就关」改成 `DialogProperties(dismissOnBackPress/ dismissOnClickOutside = false)` | bug 修复 |
| 2 | 同上 | 自写 `pickGesture`（`awaitEachGesture` + `awaitFirstDown`）替掉 `detectDragGestures` | bug 修复 |
| 3 | `ui/courseedit/CourseEditViewModel.kt` | `setColorTag` 的 `tag < 0` 守卫换成 `isValidColorTag` | bug 修复 |
| 4 | `domain/model/ColorTag.kt` | 新增 `isValidColorTag`（色板索引 ∪ ARGB 的单一判据） | 新增 |
| 5 | `data/repository/TimetableRepositoryImpl.kt` | 校验统一走 `isValidColorTag` | 对齐 |
| 6 | `ui/courseedit/components/ColorField.kt` | 从 `FormControls` 拆出（颜色控件吃两套语义，自己就近 90 行） | 门禁 |
| 7 | `ui/timetable/CourseDetailRows.kt` | 从 `CourseDetailSheet` 拆出（`DetailRow`/`DetailAction`/`outlineDivider`） | 门禁 |

---

## 二、验证清单（模拟器实测）

| 项 | 操作 | 结果 |
|----|------|------|
| 冷启动 + 课表页 | `am start` 后截图 | 第 1 周、11/12 节 21:00/22:00 时间轴正常 |
| 课程详情弹层 | 点「理论力学」卡 | 色竖条 + 课名 + 周次 + 节次/时间/地点/备注 + 编辑/复制/删除齐全（拆文件后装配正常） |
| 进编辑页 | 点弹层「编辑」 | 四组表单齐全（基本信息/时间/周次/课程颜色） |
| 开取色面板 | 点「自取颜色」 | S×V 平面 + 右侧色相条 + `#RRGGBB` 框 + 取消/保存 |
| **点按取色**（修复项） | 点平面 (250,850) | hex `#F4511E` → `#DBC9C3`，面板未关 |
| 点色相条 | 点色相条 | hex 随之变化 |
| 手输 hex | 输入 `00C853` | 平面变绿（上一批已验，本批未回归） |
| 保存回传（修复项） | 点「保存」 | 面板关闭、回到编辑页，**自取色圆圈变深色选中环、12 个色点全不选中**（负数 ARGB 回传成功） |
| 数据未污染 | `run-as` 导出 `-wal` 查 `理论力学` 行 | `color_tag` 仍是 `02`（原始色板索引），测试色没有落库 |

---

## 三、踩到的坑（本批三个真 bug 的根因）

### Bug 1：点面板任意位置都会把面板关掉
material3 的 `AlertDialog` 默认 `dismissOnClickOutside = true`。取色盘本身就是一大片可点区域，
用户每点一下取色盘 = 点外部 → 面板关掉、进度全丢。
⚠️ 本版本 **没有** `dismissOnClickOutside` / `dismissOnBackPress` 这两个具名参数，
两个开关都挂在 `androidx.compose.ui.window.DialogProperties` 上，写成具名参数会整片编译失败。

### Bug 2：点一下不取色，只有拖动才取
`detectDragGestures` 内部状态机是 `AwaitDown → AwaitTouchSlop → Dragging`，
**不越过 touch slop 的纯点按压根不触发 `onDragStart`**。
而「点一下选个颜色」恰恰是取色器最常用的操作。
改用 `awaitEachGesture` + `awaitFirstDown()` 按下即取一次色，再循环跟手；
本版的 `PointerInputChange` 没有 `changedToUp()`，靠 `!pressed && previousPressed` 判抬起。

### Bug 3：负数 ARGB 被守卫静默丢弃
满不透明的 ARGB（`0xFFRRGGBB`）写成 Int 是**负数**（`0xFFC3D8DB` = -1058949505）。
`setColorTag` 里的 `if (tag < 0) return` 把取色器自取的每一个颜色都挡掉了：
面板关了、颜色没变、表单还停在旧色。改用 `isValidColorTag`（索引 ∪ 无符号 ARGB）。

---

## 四、门禁与测试

- 单测：**238 用例 / 0 失败 / 0 错误**（31 个 XML，确认 `> Task :app:testDebugUnitTest` 无 UP-TO-DATE）
- 行数门禁：`find *.kt` 复查**无 >300 行**文件
  （`ColorPickerDialog` 282、`CourseDetailSheet` 250、`FormControls` 215、`ColorField` 119、`CourseDetailRows` 69）
- 构建：`BUILD SUCCESSFUL`，APK 22,368,610 字节

---

## 五、收尾

- 测试数据「自取颜色」只停在草稿态，**未点编辑页右上角 ✓**，库里 `color_tag` 仍是 `02`，原始数据未污染
- 全程只用模拟器 `emulator-5554`（-s 显式指定 serial），未触碰真机

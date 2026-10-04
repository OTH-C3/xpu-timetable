/*
 * Tokens.kt —— 设计令牌（M4-UI 规格 §3）
 *
 * 作用：把动效时长/缓动、圆角与触摸目标、网格尺寸收敛到唯一事实源，
 * 组件禁止再写魔法数字。WeekGrid 与 DayHeader 的左轴宽度必须同源（Grid.AxisWidth），
 * 防止将来只改一处导致表头与网格错位。
 *
 * 备注：规格写「课程卡现状 8dp，保持不变」，但实测现状为 4dp（CourseCard.kt），
 * 按"保持不变"的意图令牌取实际值 4dp（见回传报告）。
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 动效令牌（M5-UI 放慢一档：微交互 200 / 常规过渡 450 / 场景过渡 800）。 */
object Motion {
    const val FastMillis = 200          // 按压、悬停反馈
    const val BaseMillis = 450           // 切周、页面切换
    const val SceneMillis = 800          // 首屏编排（仅首次进入）
    val EaseOutStandard = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    // M11-第三批：页面转场专用档。
    // 原先用 BaseMillis(450) 配 EaseOutStandard(0.16,1,0.3,1) —— 这条是**强前倾**曲线，
    // t=0.25 时位移已经走完 ~86%，于是头 100ms 内容"唰"地弹到位、后面 350ms 几乎不动，
    // 观感就是老大说的「很快而且还有回弹」；而 exit 只有 200ms，旧页先消失，
    // 新页像被硬拽上来一样接不上。
    // 改用 350ms + FastOutSlowIn（两头慢中间快、无 overshoot）：位移分布均匀，进出场同时收尾。
    const val PageMillis = 350
    val EasePage = FastOutSlowInEasing
}

/** 圆角与尺寸令牌。 */
object Corners {
    val Card = 4.dp          // 课程卡（沿用现状实测值，见文件头备注）
    val TodayMark = 8.dp      // 今天日期方块
    val Pill = 28.dp          // 全弧度胶囊（顶部瞬时提示）
    val MinTouchTarget = 44.dp   // 手册硬性要求：≥44dp
}

/**
 * 瞬时提示浮层（M7 建立；M9 起改为**底部**弹出 + **黑灰**底色，按产品负责人指定）。
 *
 * 颜色为什么不走 colorScheme：深色主题下 `inverseSurface` 会翻转成**浅色**，
 * 与"黑灰色"的要求正好相反；而该浮层在明暗两种主题下都要求同一个黑灰底，
 * 故用固定色值（非纯 `#000000`，符合"禁纯黑白"的设计约定）。
 */
object Hint {
    val CornerRadius = 28.dp        // 全弧度胶囊
    val HorizontalPadding = 24.dp
    val VerticalPadding = 12.dp
    val BottomOffset = 16.dp        // 距内容区底边（有底部导航时即导航栏上沿）
    /** 驻留时长：略短于 BackPolicy.EXIT_WINDOW_MILLIS(2s)，让"提示消失"先于"可退出"。 */
    const val VisibleMillis = 1_800L
    /** 黑灰底 + 近白字（不走 colorScheme，理由见上）。 */
    val ContainerColor = Color(0xFF2C2C30)
    val ContentColor = Color(0xFFECECEF)
}

/** 网格尺寸（与既有实现保持一致，集中定义避免散落）。 */
object Grid {
    val RowHeight = 52.dp
    val AxisWidth = 38.dp    // WeekGrid 与 DayHeader 必须同源
}

/**
 * 桌面小组件（M10：头部右侧信息按可用宽度**降级**，避免窄宽度下文字被截断）。
 *
 * 阈值怎么来的（实测，不是拍脑袋）：
 * - 校名「西安工程大学」16sp ≈ 96dp；右侧全量「10.2  第 1 周  周五」16sp ≈ 130dp；
 *   加 12dp×2 内边距 → 全量约需 260dp 以上。
 * - 只留「第 1 周  周五」≈ 80dp → 约需 200dp。
 * - 只留「周五」≈ 32dp → 约需 150dp。
 * 小组件单格 ≈ 84dp（本机实测 84.1dp），故 4 格(≈336) 可全量、3 格(≈252) 显示周次+周几、
 * 2 格(≈168) 只显示周几。
 *
 * ⚠️ 实测坑（模拟器 AOSP Launcher3，2026-10-02）：`LocalSize` 报的是**声明的最小尺寸**，
 * 不是实际摆放尺寸 —— 小组件实际内容宽约 201dp，而 LocalSize 恒为
 * `110.1dp × 88dp`（= today_widget_info.xml 的 minResizeWidth / minHeight），缩放后也不变。
 * 后果：该启动器上永远走保守档（只显示周几）。这是**安全的**（宁少不截断），
 * 但要拿到真尺寸需要改从 `AppWidgetManager.getAppWidgetOptions()` 读 OPTION_APPWIDGET_MIN_WIDTH。
 */
object Widget {
    /** 显示「日期 + 周次 + 周几」所需的最小宽度（约 4 格及以上）。 */
    val HeaderFullMinWidth = 280.dp
    /** 显示「周次 + 周几」所需的最小宽度（约 3 格）；再窄就只显示周几。 */
    val HeaderWeekMinWidth = 200.dp
}

/** 「我的」页分组列表（M9：仿系统设置页的「分组标题 + 条目 + 右箭头」结构）。 */
object ListRow {
    val MinHeight = 52.dp        // 触摸目标 ≥44dp（手册硬性要求）
    val HorizontalPadding = 4.dp
    val VerticalPadding = 12.dp
    val ChevronSize = 18.dp
    val DividerAlpha = 0.12f     // 发丝分隔线（与 WeekGrid 同口径）
    val GroupSpacing = 18.dp     // 分组之间的留白
    val IconGap = 8.dp           // 标题与副标题、文本与箭头的间距

    // M11：分组卡片（参照系统设置页 / QQ 设置：同类项装进一个圆角容器，组内用横线分隔）
    val CardCorner = 10.dp       // 卡片圆角（M11-第三批：12→10，更贴近参考截图的设置页圆角）
    val CardPadding = 12.dp      // 卡片内边距（横向；竖向取小的 4dp，避免行显得飘）
    val CardPaddingV = 4.dp
    // M11-第三批：横线原来只有 4dp 缩进（几乎满宽）且 alpha 0.12 太淡 → 数据组那条组内线根本看不出来，
    // 老大提的"一个边框里面的几条用横线分开"就等于没做。改成与文本左缘严格对齐（CardPadding+行内边距）
    // 并提到 0.18：不满宽 → 有"同组"暗示；够清晰 → 一眼能数出这一组有几条。
    val DividerInset = 16.dp     // 组内横线的左右缩进（= CardPadding 12 + 行内边距 4，与文本左缘同线）
    val CardBorderAlpha = 0.2f   // 卡片描边（比组内横线略重，让边界先被看见，再看组内分层）
}

/**
 * 待办清单页（M12 需求五）的尺寸令牌。
 *
 * 单独一个 object 而不是并进 ListRow：这一页的版式（勾选方框、划线、折叠箭头）
 * 是独立的一套语言，将来调整不必牵动「我的」页的分组列表。
 */
object Note {
    /** 勾选方框的视觉边长。 */
    val CheckBoxSize = 20.dp
    /** 勾选方框的圆角（参考图是略带圆角的小方框，不是正方）。 */
    val CheckBoxCorner = 5.dp
    /** 勾中方框里的对勾边长。 */
    val CheckIcon = 14.dp
    /** 勾选方框的触摸目标（≥44dp 是项目硬性要求，故触摸区比视觉大一圈）。 */
    val CheckBoxTouch = 44.dp
    /** 条目行最小高度：比 ListRow.MinHeight 略矮，让一屏能多放一条（对齐参考图的紧凑排布）。 */
    val RowMinHeight = 44.dp
    /** 划线粗细。 */
    val StrikeWidth = 1.5.dp
    /** 条目行左边距：比头部勾选框再缩进一级，形成层级（参考图里条目是内缩的）。 */
    val ItemIndent = 28.dp
    /** 元素间距。 */
    val Gap = 10.dp
    /** 元素之间的小间距。 */
    val GapSmall = 6.dp
    /** 折叠箭头的视觉边长。 */
    val ExpandIcon = 18.dp
    /** 折叠箭头的触摸目标。 */
    val ExpandTouchTarget = 40.dp
    /** 折叠箭头的内缩：让 18dp 的图标落在 40dp 热区正中。 */
    val ExpandIconInset = 11.dp

    /**
     * 勾选划线的动画时长。
     *
     * 取 300ms 而非通用微交互 200ms：划线要"看得见从左往右走完"，
     * 太快等于没有动画（老大的原话是要"以横线从左到右的动画划掉"），太慢则连续勾选时拖沓。
     */
    const val StrikeMillis = 300

    /** 清单区折叠/展开的动画时长。 */
    const val CollapseMillis = 250

    /** 空态图标边长。 */
    val EmptyIconSize = 40.dp
}

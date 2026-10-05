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
    // 组内多选方框（设置页「作用范围」一栏）的视觉尺寸。
    // 单独一份而不是引用 Note.* 那两个：它们属于待办清单页自己的版式语言，
    // 让设置页反向依赖 ui.note 的包会顺带把这个包的令牌语义也绑到同一处改动上。
    val CheckboxSize = 20.dp
    val CheckboxCorner = 5.dp
    val CheckboxIcon = 14.dp     // 方框内对勾的边长（与 20dp 方框同口径，见 Note.CheckIcon）

    /**
     * 玻璃卡的投影高度（需求四）。
     *
     * 只给 1dp：玻璃不该有"浮起来"的厚投影，那是"贴纸感"的另一半来源。
     * 1dp 刚好够把卡片从背景里"托"起来一点，不至于变成漂浮的纸片。
     * （M11 时卡片靠 1dp 描边区分；现在底色半透明了，需要一点点阴影补回层次。）
     */
    val GlassShadowElevation = 1.dp
}

/**
 * 玻璃卡片不透明度的取值域（M13：需求「为白色UI方框增加透明度调节」）。
 *
 * 为什么不直接用 GlassPalette 里的常数：那两个常数描述的是**默认观感**，
 * 而这里描述的是**控件能拖到哪**，两件事会分别演进（换默认不等于换范围）。
 * 二者的一致性由 CardAlphaTest `滑块下限不低于玻璃下限` 一条单测钉住，
 * 宁可在测试里显式对齐，也不让控件能从外面够不到的区间画出来。
 */
// 玻璃卡（卡片透明度）的令牌已挪到 ui/background/GlassTokens.kt ——
// 它是玻璃质感的参数、与 GlassPalette 的可读性下限强绑定，
// 放在 Tokens.kt 里既撑爆 300 行门禁，也让人误以为它与待办令牌同源。


/**
 * 待办清单页（M12 需求五）的尺寸令牌。
 *
 * 单独一个 object 而不是并进 ListRow：这一页的版式（勾选方框、划线、折叠箭头）
 * 是独立的一套语言，将来调整不必牵动「我的」页的分组列表。
 */
object Note {
    /**
     * 勾选方框的视觉边长。
     *
     * M13 从 20dp 收到 **16dp**（老大：「修改代办页面选中方框的大小，让他更小」）。
     * 为什么不是更小：16dp 是 Material 规范的最小可读尺寸，再小里面的对勾会糊成一团。
     * 顺带把圆角和对勾一起按比例收（见下），保持"框 : 对勾"的比例不变。
     */
    val CheckBoxSize = 16.dp

    /**
     * 勾选方框的圆角。
     *
     * 原 5dp / 20dp = 0.25 比例，改成 16dp 后按同比例取 4dp。
     * 视觉上仍是略带圆角的小方框（参考图同款），不是正方。
     */
    val CheckBoxCorner = 4.dp

    /** 勾中方框里的对勾边长。原 14dp / 20dp = 0.7，16dp 对应 11dp。 */
    val CheckIcon = 11.dp

    /**
     * 勾选方框的触摸目标。
     *
     * **保持 44dp 不变**（项目硬性下限）。视觉框缩小了但热区不能跟着缩 ——
     * 否则这个框就变得难点中了，而它在头部和每一条待办里都要点。
     * 这就是"视觉层"与"触摸层"分开的原因。
     */
    val CheckBoxTouch = 44.dp
    /** 条目行最小高度：比 ListRow.MinHeight 略矮，让一屏能多放一条（对齐参考图的紧凑排布）。 */
    val RowMinHeight = 44.dp
    /** 划线粗细。 */
    val StrikeWidth = 1.5.dp
    /** 条目行左边距：比头部勾选框再缩进一级，形成层级（参考图里条目是内缩的）。 */
    val ItemIndent = 28.dp

    /**
     * 输入框的**矮档**高度（M13 需求 7/8：把待办页那两个大方框缩小）。
     *
     * 为什么降到 40dp：M3 的 `OutlinedTextField` 默认最小高度 56dp，
     * 放在行高 52dp 的待办列表里，整框比一行内容还高，视觉上像
     * "往卡片里塞了个输入框"而不是"这一行正处于输入态"。
     *
     * 关于触摸目标：44dp 那条硬性要求针对**可点区域**，这里 40dp 是框本身的高度，
     * 加上父行的上下 padding 与左右留白后实际可点范围仍 ≥ 44dp。
     */
    val FieldHeightCompact = 40.dp

    /** 矮档输入框的描边不透明度：比 M3 默认淡一档，避免黑框抢过卡片内容。 */
    const val FieldBorderAlpha = 0.35f

    /** 矮档输入框的圆角：比卡片略小，让它读起来是"行内输入"而不是"又一张卡"。 */
    val FieldCorner = 6.dp
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

    /**
     * 彩带庆祝动画的总时长。
     *
     * 取 1100ms：小于 800ms 纸片还没飞散就消失，读起来像"闪了一下"；
     * 大于 1600ms 则一张清单完成后要等太久才能继续操作下一张。
     * 1100ms 刚好能看清"喷出 → 翻飞 → 淡出"三段。
     */
    const val ConfettiMillis = 1100

    /**
     * 「已完成」分组首次出现时，整组从上方滑入的时长。
     *
     * 需求：「第一次出现已完成的时候'已完成'挪动到下面的动效」。
     * 取 420ms（接近项目"场景"级 800ms 的一半）：要能看出"从上往下挪"，
     * 又不能让人等得不耐烦。
     */
    const val DoneGroupEnterMillis = 420

    /**
     * 「已完成」分组首次出现时，从上方滑入的**起始偏移量（px）**。
     *
     * 单位是 px 而不是 dp：消费方是一次性 `Animatable<Float>`，它只吃 px。
     * 这里刻意不给 dp 版本 —— 两个必须手工同步的常量迟早会只改一个，
     * 留下一处静默的 3 倍误差。（28dp 在本机 3x 密度下正好 ≈ 84px）
     */
    const val DoneGroupEnterOffsetPx = 84f

    /**
     * 「已完成」分组**每次**重新出现时的滑入时长（M13 需求 1）。
     *
     * 与 [DoneGroupEnterMillis] 分开：那个是"人生第一次"的纪念式入场（慢一点、庄重一点），
     * 这个是日常展开/收起（要利落）。混用一个值会让每次展开都慢半拍。
     */
    const val DoneGroupToggleMillis = 260

    /**
     * 新建清单出现时，卡片从下方滑入 + 淡入的时长（M13 需求 2）。
     *
     * 300ms：与划线动画同档，够看清"这张是刚加的"，又不至于打断连续新建。
     */
    const val NewListEnterMillis = 300

    /** 新建清单滑入的起始偏移量（px）。比已完成分区的 84px 略小，因为卡片更高，用小位移就够。 */
    const val NewListEnterOffsetPx = 56f

    /**
     * 勾完最后一个对钩后，**推迟多久**才把清单移进「已完成」（M13 需求 3）。
     *
     * 需求原话：「答完后先出现彩带，然后再进入已完成」。
     * 0 就等于彩带还没看清卡片就跳走了，彩带等于没播。
     * 取 [ConfettiMillis] 的 60%：彩带的"喷出 → 翻飞"这段在 600ms 左右最清楚，
     * 再往后的淡出尾段和卡片移动重叠反而显乱。
     */
    const val DoneMoveDelayMillis = 660L

    /**
     * 彩带片数（M13 需求 3：老大要求"增加彩带"）。
     *
     * 18 → 30。理由：勾一整组可能有十几条待办，18 片配十几条勾选动作显得敷衍；
     * 30 片在 2~3 秒内飞散，观感是"庆祝"而不是"下雪"。
     * 上限不建议再高 —— 30 片已是每帧 30 次 drawRect，再翻倍在中低端机上是纯浪费。
     */
    const val CONFETTI_COUNT = 30
}

/*
 * TodayWidgetContent.kt —— 桌面小组件的 Glance 渲染（M4-W 改版）
 *
 * 卡片：根背景 drawable 圆角 16dp（亮 #EEEDF3 / 暗 #111318，M5 需求 6），内容内边距 12dp。
 * 头部（仿 WakeUp，所有状态共用渲染——P1 修复：空态也带头部）：左「西安工程大学」；
 *   右「M.d [第 N 周] 周X」16sp onSurfaceVariant 右对齐（M5 需求 7/8：与校名
 *   同字号、与「今天没有课啦」同色，不再用 primary）。
 * 列表：只渲染未结束课程（plan.remaining）。M10 按需求改为**纯文字行**：
 *   去掉行的彩色交替底与左侧课程色条；每行左侧课名（+教室），**右侧上下两行**显示
 *   上课时间与下课时间（上=开始、下=结束）。首行课名加粗（正在上/下一节）。
 * 底部：「今天还有 x 节课，加油！」（x = remainingTotal，含被折叠的）。
 * 空态：NO_TERM 引导导入；ALL_DONE_OR_NONE 居中颜文字 +「今天没有课啦」
 *   （颜文字为产品指定，不受"无 emoji"纪律约束）。
 * 整卡点击打开 MainActivity（直达周视图）。
 */
package com.gould.xputimetable.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.LocalSize
import com.gould.xputimetable.MainActivity
import com.gould.xputimetable.R
import com.gould.xputimetable.ui.theme.DarkOnSurface
import com.gould.xputimetable.ui.theme.CoursePalette
import com.gould.xputimetable.ui.theme.LightOnSurface
import com.gould.xputimetable.ui.timetable.components.weekdayCn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// M5 需求 5：小组件高度降至 88dp，紧凑阈值同步下调，88dp 仍走完整布局（列表+统计行）
private val COMPACT_HEIGHT = 80.dp
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d")

private const val EMPTY_TERM_TITLE = "还没有课表"
private const val EMPTY_TERM_SUBTITLE = "打开 App 导入"
private const val ALL_DONE_FACE = "(๑˃̵ᴗ˂̵)"
private const val ALL_DONE_TEXT = "今天没有课啦"
private const val FOOTER_TEMPLATE = "今天还有 %d 节课，加油！"

// 时间/教室 = onSurface 的 70%（两个彩底上均比 onSurfaceVariant 深一档，保证可读）
private val TIME_COLOR = ColorProvider(
    day = LightOnSurface.copy(alpha = 0.70f),
    night = DarkOnSurface.copy(alpha = 0.70f),
)

@androidx.compose.runtime.Composable
internal fun TodayWidgetContent(plan: TodayPlan, weekNumber: Int?) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_rounded))
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        // 头部在所有状态最上方渲染（M4-W P1 修复：空态缺失头部）
        WidgetHeader(
            weekNumber = weekNumber,
            hasTerm = plan.emptyReason != EmptyReason.NO_TERM,
            width = size.width,
        )
        Spacer(GlanceModifier.height(6.dp))
        when (plan.emptyReason) {
            EmptyReason.NO_TERM -> EmptyContent(EMPTY_TERM_TITLE, EMPTY_TERM_SUBTITLE)
            EmptyReason.ALL_DONE_OR_NONE -> AllDoneContent()
            EmptyReason.NONE ->
                if (size.height < COMPACT_HEIGHT) CompactContent(plan) else FullContent(plan)
        }
    }
}

@androidx.compose.runtime.Composable
private fun FullContent(plan: TodayPlan) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            plan.remaining.forEachIndexed { index, item ->
                CourseRow(item = item, index = index)
                if (index != plan.remaining.lastIndex) Spacer(GlanceModifier.height(4.dp))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        FooterLine(plan)
    }
}

@androidx.compose.runtime.Composable
private fun CompactContent(plan: TodayPlan) {
    plan.remaining.firstOrNull()?.let { next ->
        CourseRow(item = next, index = 0, compact = true)
    }
    Spacer(GlanceModifier.height(2.dp))
    FooterLine(plan)
}

/**
 * 头部（所有状态共用）：左校名 16sp Bold + onSurface（标题层级不变）；
 * 右日期·周次·周几：16sp + onSurfaceVariant（M5 需求 7/8：与校名同字号、
 * 与「今天没有课啦」同色，不再用 primary），maxLines=1 防窄卡换行。
 * 右上口径：有周次 → 「M.d  第 N 周  周X」；学期内但不在周次（假期越界）
 * → 「M.d  周X」；无学期 → 仅「M.d」（无周次可显示）。
 */
@androidx.compose.runtime.Composable
private fun WidgetHeader(weekNumber: Int?, hasTerm: Boolean, width: Dp) {
    val today = LocalDate.now()
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_school_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        // M10：宽度不够时按优先级砍内容（先日期、再周次，保留周几），而不是缩字号 ——
        // 字号是同校名一致的 16sp（M5 需求 7/8），缩了会破坏该决定。
        Text(
            text = headerRightText(
                dateText = today.format(DATE_FORMAT),
                weekNumber = weekNumber,
                weekdayText = if (hasTerm) weekdayCn(today.dayOfWeek.value) else null,
                detail = headerDetailFor(width),
            ),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 16.sp,
            ),
        )
    }
}

/**
 * 课程行（M10 改版）：**无彩色底、无色条**，靠行间距分隔。
 *   左列：课名（首行加粗）+ 教室；右列：上课时间在上、下课时间在下（右对齐）。
 * 为什么要停掉色标：一套彩底 + 色条在 88dp 的小卡片里占比过大，去掉后信息密度反而更高。
 */
@androidx.compose.runtime.Composable
private fun CourseRow(item: TodayItem, index: Int, compact: Boolean = false) {
    val timeStyle = TextStyle(
        color = TIME_COLOR,
        fontSize = if (compact) 11.sp else 12.sp,
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // M10：左侧课程色小竖条（颜色与课程一致）。**只恢复竖条，不恢复行的彩色底** ——
        // 产品明确要"左边有个小竖条、颜色和课程一致"，而彩色整行底上一轮已按需求去掉。
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(if (compact) 18.dp else 28.dp)
                .cornerRadius(2.dp)
                .background(CoursePalette.base(item.colorTag)),
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.courseName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                ),
            )
            val classroom = item.classroom
            if (!classroom.isNullOrBlank() && !compact) {
                Text(text = classroom, maxLines = 1, style = timeStyle)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        // 右侧时间列：上面是上课时间，下面是下课时间
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatMinute(item.startMinute), style = timeStyle)
            Text(text = formatMinute(item.endMinute), style = timeStyle)
        }
    }
}

@androidx.compose.runtime.Composable
private fun FooterLine(plan: TodayPlan) {
    FooterText(FOOTER_TEMPLATE.format(plan.remainingTotal))
}

@androidx.compose.runtime.Composable
private fun FooterText(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp,
        ),
    )
}

/** NO_TERM 引导态：两行文案居中。 */
@androidx.compose.runtime.Composable
private fun EmptyContent(title: String, subtitle: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

/** ALL_DONE_OR_NONE：颜文字 + 一行文案，极简居中（产品指定）。 */
@androidx.compose.runtime.Composable
private fun AllDoneContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ALL_DONE_FACE,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 20.sp,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = ALL_DONE_TEXT,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
            ),
        )
    }
}

private fun formatMinute(minute: Int): String {
    val hour = minute / 60
    val min = minute % 60
    return "%02d:%02d".format(hour, min)
}

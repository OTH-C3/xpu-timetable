/*
 * DayHeader.kt —— 日期表头（M4-UI R2/R3，自 TimetableScreen 拆出守 300 行）
 *
 * 结构：左侧 38dp 月份徽标（与 WeekGrid 左轴同宽，Grid.AxisWidth 同源）
 *   + 7 等分列（星期字 + 日期）+ 底部发丝分隔线。
 *
 * R2 月份徽标：取所显示那一周的周一所在月份；跨月周（如 9/29-10/5）以周一为准。
 *   学期起日缺失/解析失败 → 留空（不显示占位符、不崩）。
 * R3 今天标记：仅当"所显示周包含今天"时，该列日期画黑底圆角方块 + 镂空数字
 *   （底色 onSurface、数字 surface，深浅主题自动反相），星期字用 primary 加粗。
 */
package com.gould.xputimetable.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.ui.theme.Corners
import com.gould.xputimetable.ui.theme.Grid
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/** 今天日期方块本体尺寸（规格 §4 R3 指定；外层整列点击区由网格保证）。 */
private val todayBlockSize = 28.dp

/** 1..12 → 「N 月」；越界返回空串（不抛异常）。 */
fun monthLabelCn(month: Int): String = if (month in 1..12) "$month 月" else ""

/**
 * 所显示那一周的周一所在月份（月份徽标的取值规则，规格 §4 R2）。
 * 学期起日缺失/空白/解析失败 → 返回 null（UI 留空不崩）。
 */
fun monthOfMonday(startDate: String?, week: Int): Int? {
    if (startDate.isNullOrBlank()) return null
    return runCatching {
        WeekCalc.mondayOfWeek(LocalDate.parse(startDate), week).monthValue
    }.getOrNull()
}

/**
 * 所显示的第 [week] 周是否包含 [today]（口径唯一来源，WeekGrid 与 DayHeader 共用）。
 *
 * 存在的理由（M6 需求 5）：WeekGrid 原先是 `day == todayDow`，只比星期几，
 * 翻到上一周/下一周时同一天的课会被误判成"今天正在上"并画出 primary 边框。
 * DayHeader 早已有这段守卫（今天的日期方块不跨周误画）——现在抽出来共用，
 * 避免两处口径分叉（分叉过一次就是这次的 bug）。
 *
 * 学期起日缺失/空白/解析失败 → false（不标记，不抛异常）。
 */
internal fun weekContainsToday(startDate: String?, week: Int, today: LocalDate): Boolean {
    if (startDate.isNullOrBlank()) return false
    val monday = runCatching { WeekCalc.mondayOfWeek(LocalDate.parse(startDate), week) }.getOrNull()
        ?: return false
    return !today.isBefore(monday) && today.isBefore(monday.plusDays(7))
}

@Composable
fun DayHeader(startDate: String?, week: Int, today: LocalDate, modifier: Modifier = Modifier) {
    val monday = remember(startDate, week) {
        if (startDate.isNullOrBlank()) null else runCatching {
            WeekCalc.mondayOfWeek(LocalDate.parse(startDate), week)
        }.getOrNull()
    }
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val todayDow = today.dayOfWeek.value
    // 过去周/未来周：所显示周不含今天 → 该周内不画今天方块（规格 §4 R3）
    // M6 需求 5：口径收敛到 weekContainsToday()，与 WeekGrid 共用
    val containsToday = weekContainsToday(startDate, week, today)
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            // R2：星期与时间轴交界处的月份徽标，宽度与 WeekGrid 左轴同源
            Box(Modifier.width(Grid.AxisWidth), contentAlignment = Alignment.Center) {
                MonthBadge(monthOfMonday(startDate, week))
            }
            labels.forEachIndexed { index, label ->
                val dow = index + 1
                val date = monday?.plusDays(index.toLong())
                val isToday = containsToday && dow == todayDow
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isToday) FontWeight.SemiBold else null,
                        // M10：今天的高亮文字改黑色（原为强调色 primary，按"蓝色字样改黑"的统一要求）
                        color = if (isToday) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (date != null) {
                        if (isToday) {
                            TodayDateBlock(date)
                        } else {
                            Text(
                                text = date.format(dayFormatter),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
        // 发丝分隔线（outlineVariant 12%）：分隔表头与网格，不加阴影
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
    }
}

/** 竖排月份徽标：「9」一行 + 「月」一行（规格 §4 R2，labelSmall、次要色）。 */
@Composable
private fun MonthBadge(month: Int?) {
    if (month == null) return
    val label = monthLabelCn(month)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.substringBefore(" 月"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "月",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** R3 今天日期块：黑底（onSurface）圆角方块 + 镂空数字（surface），无阴影。 */
@Composable
private fun TodayDateBlock(date: LocalDate) {
    val text = date.format(dayFormatter)
    Box(
        modifier = Modifier
            // ⚠️ 原来是 `.size(28.dp)` 固定正方形：日期一长（"12/31"）或系统字体一放大，
            // Text 就会折成两行，方块被撑高、表头跟着歪。
            // 改成"高度固定、宽度只给下限"：内容能撑宽但不会撑高，形状仍是圆角小方块。
            .height(todayBlockSize)
            .widthIn(min = todayBlockSize)
            .background(
                MaterialTheme.colorScheme.onSurface,
                RoundedCornerShape(Corners.TodayMark),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            // 字号与周围 6 天**完全一致**（labelMedium），不再缩放 ——
            // 老大明确要"之前的效果"：选中那天不该比周围小。
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.surface,
            fontWeight = FontWeight.SemiBold,
            // 不折行的代价由**方块变宽**承担，不由字号承担：
            // widthIn(min) 只给下限，长日期/大字体时方块横向伸展，文字始终单行完整。
            // 只写 maxLines=1 会把第二行裁成半截日期，所以必须配 softWrap=false。
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/*
 * FormControls.kt —— 编辑页可复用表单控件（ui/courseedit/components/）
 *
 * 为什么拆出来：编辑页主体若把控件都写在一起会超过 300 行（项目硬约束，Spec §10）。
 * 这些控件本身与"课程编辑"业务无关，只是受主题约束的通用输入控件，独立后可被后续的
 * 导入确认页、设置页复用。
 *
 * 颜色选择单独在 ColorField.kt（要同时吃"色板索引"和"自取色 ARGB"两套语义，本身就近 90 行）。
 *
 * 性能约定（2026-09-17）：控件只接收自己需要的**稳定类型**参数（String / Int / 枚举 /
 * Boolean / 方法引用），选项文案用文件级常量而不是每次重组新建 List——
 * List 在 Compose 里属不稳定类型，会让整个控件无法跳过重组，是标签动画掉帧的常见原因之一。
 */
package com.gould.xputimetable.ui.courseedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.ui.components.GroupCard
import com.gould.xputimetable.ui.components.GroupHeader
import com.gould.xputimetable.ui.theme.ListRow

/** 星期文案（文件级常量，避免每次重组新建 List）。 */
private val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 单双周文案。 */
private val weekTypeLabels = listOf("全部", "单周", "双周")

/**
 * 表单分组卡片（M11-第三批「美化添加课程页」）：与「我的」页用同一套卡片语言——
 * 卡片本体下沉在 `ui.components.CardGroup`，这里只做转发，避免两处各画一遍圆角/描边。
 */
@Composable
internal fun FormCard(content: @Composable ColumnScope.() -> Unit) = GroupCard(content = content)

/** 分组标题：小号灰字，位置在卡片**外面**（与设置页一致）。 */
@Composable
internal fun FormGroupHeader(text: String) = GroupHeader(text)

/**
 * 卡片内横线：左右内缩到与行文本左缘对齐（不满宽），这是卡片内分隔线的通行做法；
 * 满宽线在窄卡片里会把条目视觉上切成两半，看不出"还是同一组"。
 */
@Composable
internal fun FormDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ListRow.DividerInset)
            .height(1.dp)
            .background(
                MaterialTheme.colorScheme.outlineVariant
                    .copy(alpha = ListRow.DividerAlpha),
            ),
    )
}

/** 单行文本输入框（课程名 / 教师 / 教室共用）。 */
@Composable
internal fun LabeledField(
    value: String,
    label: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        modifier = modifier,
    )
}

/** 星期选择行：对外用"星期几（1..7）"，索引换算收敛在控件内部。 */
@Composable
internal fun DayOfWeekRow(selected: Int, onSelect: (Int) -> Unit) {
    SingleSelectRow(
        labels = dayLabels,
        selectedIndex = (selected - 1).coerceIn(0, dayLabels.lastIndex),
        onSelectIndex = { index -> onSelect(index + 1) },
    )
}

/** 单双周选择行。 */
@Composable
internal fun WeekTypeRow(selected: WeekType, onSelect: (WeekType) -> Unit) {
    val selectedIndex = when (selected) {
        WeekType.ALL -> 0
        WeekType.ODD -> 1
        WeekType.EVEN -> 2
    }
    SingleSelectRow(
        labels = weekTypeLabels,
        selectedIndex = selectedIndex,
        onSelectIndex = { index ->
            onSelect(
                when (index) {
                    1 -> WeekType.ODD
                    2 -> WeekType.EVEN
                    else -> WeekType.ALL
                },
            )
        },
    )
}

/** 通用单选行（胶囊样式）。 */
@Composable
private fun SingleSelectRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // 外层只负责"够大的触摸目标"（≥48dp 高，可访问性要求），内层保持 32dp 视觉尺寸
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clickable { onSelectIndex(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        // M10：选中态去蓝（原 primaryContainer 底 + primary 边框）→ 中性底 + 黑边框
                        .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 「- 值 +」区间行（节次 / 周次共用）。 */
@Composable
internal fun RangeRow(
    startLabel: String,
    endLabel: String,
    start: Int,
    end: Int,
    range: IntRange,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stepper(label = startLabel, value = start, range = range, onValueChange = onStartChange)
        Text("-", modifier = Modifier.padding(horizontal = 12.dp))
        Stepper(label = endLabel, value = end, range = range, onValueChange = onEndChange)
    }
}

/** 步进器（越界时按钮自动禁用，从源头阻止非法值）。 */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }, enabled = value > range.first) {
            Text("-")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }, enabled = value < range.last) {
            Text("+")
        }
    }
}

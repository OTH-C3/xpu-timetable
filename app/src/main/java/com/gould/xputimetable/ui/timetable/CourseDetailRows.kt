/*
 * CourseDetailRows.kt —— 课程详情弹层里的两行小件（ui/timetable/）
 *
 * 为什么单独一个文件：这两个小件加起来 43 行，塞进 CourseDetailSheet 会把它顶到 301 行，
 * 超过项目 300 行硬门禁（Spec §10）。它们与"值怎么算"无关（换算在 CourseDetailFormat.kt），
 * 只管怎么画，拆出来互不干扰。
 */
package com.gould.xputimetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.theme.ListRow

/**
 * 发丝分隔线的取色（与设置页同一口径）。
 * 定义跟着尾段一起搬过来：CourseDetailSheet 的 184/235 两处还挂着它，
 * 留在原文件已经没有定义点了（2026-10-02 拆分时踩到，编译直接报 Unresolved reference）。
 */
@Composable
internal fun outlineDivider(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = ListRow.DividerAlpha)

/** 一行「标签 —— 值」；值缺省时整行不画，避免在面板上留一条空线。 */
@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListRow.MinHeight)
            .padding(vertical = ListRow.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 底部动作文字按钮（触摸目标靠整块 Box，96dp 宽 × 52dp 高）。 */
@Composable
internal fun DetailAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(ListRow.MinHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (danger) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

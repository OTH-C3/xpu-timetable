/*
 * ColorField.kt —— 课程颜色选择控件（ui/courseedit/components/）
 *
 * 为什么单独一个文件：颜色控件要同时处理「12 色固定色板」和「自取色」两套语义，一个文件
 * 轻松 90 行，塞进 FormControls 会把那个文件顶过 300 行门禁（Spec §10）。
 * 与 FormControls 同目录，对外暴露同一个 `internal` 入口，调用方无感。
 */
package com.gould.xputimetable.ui.courseedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.isArgbColorTag
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.CoursePalette
import com.gould.xputimetable.ui.theme.IconSize

/**
 * 课程颜色选择：12 色固定色板 + 一个「自取色」入口。
 *
 * 自取色打开的是自绘 HSV 面板（components/ColorPickerDialog），确认后回传的 ARGB int
 * 直接写进 `courses.color_tag` 同一列——该列用 `isArgbColorTag` 区分「离散索引」与「ARGB」，
 * 所以自取色时下面 12 个色点**都不该显示为选中**。
 */
@Composable
internal fun CourseColorField(
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val customSelected = isArgbColorTag(selected)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 12 色分两行：每项触摸目标 44dp（可访问性下限）且 12×44dp 在手机宽度内放不下，
        // 故 6 个一行；色点本身仍是 28dp 视觉尺寸，四周留白参与触摸。
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val rowCount = 2
            val perRow = CoursePalette.size / rowCount
            repeat(rowCount) { row ->
                Row {
                    repeat(perRow) { col ->
                        val index = row * perRow + col
                        val isSelected = !customSelected && index == selected
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { onSelect(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(CoursePalette.base(index))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        // 自取色入口：圆形描边 + 调色盘图标，图标染成当前课程色（"从这里取一个自己的色"）
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable { onOpenPicker() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (customSelected) 2.5.dp else 1.2.dp,
                        color = if (customSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(AppIcons.palette),
                    contentDescription = "自取颜色",
                    tint = CoursePalette.base(selected),
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }
}

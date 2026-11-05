/*
 * CourseEditForm.kt —— 添加/编辑课程页的表单主体（与页面装配层分家，M11-第三批）
 *
 * 为什么要拆：这一页要塞「基本信息 + 星期 + 节次 + 周次 + 单双周 + 颜色 + 删除」，
 * 加上取色面板的接线很容易顶破项目硬约束（单文件 ≤300 行）。拆分后——
 *   CourseEditScreen.kt = 顶栏 / Scaffold / 取色面板与删除确认的开关；
 *   本文件 = 表单分组卡片的排布。
 *
 * 版式（对应需求里的"参考图 6"）：同类字段装进一张圆角卡片，组内字段之间用横线分隔，
 * 组标题在卡片外面——和「我的」页卡片化是同一套语言（共用 ui.components.CardGroup）。
 *
 * 性能约定（沿用 2026-09-17 的修复）：每个字段只接收自己需要的稳定参数，
 * 回调一律传方法引用（viewModel::setXxx），不在组合期新建 List，
 * 否则一次击键会让整张表单（3 个输入框 + 12 个色点 + 步进器）全部重组。
 */
package com.gould.xputimetable.ui.courseedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.ui.courseedit.components.CourseColorField
import com.gould.xputimetable.ui.courseedit.components.DayOfWeekRow
import com.gould.xputimetable.ui.courseedit.components.FormCard
import com.gould.xputimetable.ui.courseedit.components.FormDivider
import com.gould.xputimetable.ui.courseedit.components.FormGroupHeader
import com.gould.xputimetable.ui.courseedit.components.LabeledField
import com.gould.xputimetable.ui.courseedit.components.RangeRow
import com.gould.xputimetable.ui.courseedit.components.WeekTypeRow
import com.gould.xputimetable.ui.theme.ListRow

/**
 * 表单主体。
 *
 * @param onOpenPicker 打开自绘 HSV 取色面板（颜色行的"调色盘"按钮走这里）
 * @param onColorTagChange 色板点选或取色确认后回写 colorTag——**两种来源都是 Int，
 *        色板给的是 0..11 索引，取色器给的是 ARGB**，由 CoursePalette.isArgbTag 区分
 */
@Composable
internal fun EditFormBody(
    draft: CourseDraft,
    onNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onClassroomChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDayOfWeekChange: (Int) -> Unit,
    onStartSectionChange: (Int) -> Unit,
    onEndSectionChange: (Int) -> Unit,
    onStartWeekChange: (Int) -> Unit,
    onEndWeekChange: (Int) -> Unit,
    onWeekTypeChange: (WeekType) -> Unit,
    onColorTagChange: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BasicInfoGroup(
            name = draft.name,
            teacher = draft.teacher,
            classroom = draft.classroom,
            note = draft.note,
            errorField = draft.errorField,
            onNameChange = onNameChange,
            onTeacherChange = onTeacherChange,
            onClassroomChange = onClassroomChange,
            onNoteChange = onNoteChange,
        )

        FormGroupHeader("时间")
        FormCard {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                DayOfWeekRow(selected = draft.dayOfWeek, onSelect = onDayOfWeekChange)
            }
            FormDivider()
            RangeRow(
                startLabel = "开始节次",
                endLabel = "结束节次",
                start = draft.startSection,
                end = draft.endSection,
                range = CourseEditViewModel.SECTION_RANGE,
                onStartChange = onStartSectionChange,
                onEndChange = onEndSectionChange,
            )
        }

        FormGroupHeader("周次")
        FormCard {
            if (draft.hasExactWeeks) {
                Text(
                    text = "该课周次由教务给出精确列表，手动修改后将改为区段设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            RangeRow(
                startLabel = "起始周",
                endLabel = "结束周",
                start = draft.startWeek,
                end = draft.endWeek,
                range = CourseEditViewModel.WEEK_RANGE,
                onStartChange = onStartWeekChange,
                onEndChange = onEndWeekChange,
            )
            FormDivider()
            WeekTypeRow(selected = draft.weekType, onSelect = onWeekTypeChange)
        }

        FormGroupHeader("课程颜色")
        FormCard {
            CourseColorField(
                selected = draft.colorTag,
                onSelect = onColorTagChange,
                onOpenPicker = onOpenPicker,
            )
        }

        // 错误提示：只在校验失败时出现，位置放在表单末尾（不打断填表动线）
        draft.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = ListRow.GroupSpacing),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 基本信息组：课程名 / 教师 / 教室 / 备注，纵向排布、条目之间一条横线。 */
@Composable
private fun BasicInfoGroup(
    name: String,
    teacher: String,
    classroom: String,
    note: String,
    errorField: DraftField?,
    onNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onClassroomChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    FormGroupHeader("基本信息")
    FormCard {
        Column {
            LabeledField(
                value = name,
                label = "课程名",
                isError = errorField == DraftField.NAME,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
            )
            FormDivider()
            LabeledField(
                value = teacher,
                label = "教师（可选）",
                isError = errorField == DraftField.TEACHER,
                onValueChange = onTeacherChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            FormDivider()
            LabeledField(
                value = classroom,
                label = "教室（可选）",
                isError = errorField == DraftField.CLASSROOM,
                onValueChange = onClassroomChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            // M12 需求三：备注。放在基本信息组末尾而不是另起一张卡——
            // 它和"教师/教室"是同一类自由文本，拆出去会让卡片语言碎掉。
            FormDivider()
            LabeledField(
                value = note,
                label = "备注（可选）",
                isError = errorField == DraftField.NOTE,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

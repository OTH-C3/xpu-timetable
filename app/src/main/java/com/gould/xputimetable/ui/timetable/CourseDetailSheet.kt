/*
 * CourseDetailSheet.kt —— 课程详情底部弹层（M11-第三批）
 *
 * 作用：点课程卡不再直接跳进编辑页，而是在原地弹一张详情面板
 * （参照产品负责人给的截图：课表 → 点课程卡）。
 *
 * 为什么是弹层而不是进导航栈：
 *   1. 看一眼课名/时间/地点/教师，不该产生一次页面切换 —— 转场动画对这种"就地追问"是负担；
 *   2. 弹层关掉后眼睛还停在原来那张卡上（周视图没被换掉，滚动位置与当前周都不丢）；
 *   3. 返回键关弹层即可，不必在返回栈里压一层。
 *
 * 与 CourseEditScreen 的分工：这里是**只读展示 + 快速动作**（编辑/复制/删除），
 * 要改具体字段（周数、节次、颜色…）走「编辑」进完整表单页。
 *
 * 状态放在调用方（TimetableScreen）而不是这里：弹层的内容随屏幕持有的
 * "当前选中项"变化，记住"备注看本周还是所有周"这类过滤态与选中态同生命周期最自然，
 * 拆进弹层会因为弹层卸载而丢。本文件只负责画；两行小件沉到同包 CourseDetailRows.kt。
 *
 * 实现约束（复用项目既有纪律）：
 *   - 色值只走 colorScheme / CoursePalette；尺寸走 Tokens；
 *   - 触摸目标 ≥44dp；
 *   - edge-to-edge 下 ModalBottomSheet 自己吃系统栏 insets，这里不再重复补内边距。
 */
package com.gould.xputimetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.ui.theme.CoursePalette
import com.gould.xputimetable.ui.theme.ListRow

// ---------- 文件级文案常量（UI 文案集中，便于统一措辞） ----------
private const val LABEL_SECTION = "节次"
private const val LABEL_TIME = "时间"
private const val LABEL_PLACE = "地点"
private const val LABEL_TEACHER = "教师"
private const val LABEL_NOTE = "备注"
private const val NOTE_EMPTY = "无备注"
private const val SCOPE_THIS_WEEK = "仅本周"
private const val SCOPE_ALL_WEEKS = "所有周"
private const val ACTION_EDIT = "编辑"
private const val ACTION_COPY = "复制"
private const val ACTION_DELETE = "删除"
private const val DELETE_TITLE = "删除这门课？"
private const val DELETE_TEXT = "「%s」及其全部节次都会被删除，不可撤销。"
private const val DELETE_CONFIRM = "删除"
private const val CANCEL = "取消"

/**
 * 课程详情弹层。
 *
 * @param item               点中的那一行（课名/颜色/教师/节次/教室都在这里）
 * @param note               课程备注（来自 courses 表；SessionWithCourse 不带，由调用方查好传进来）
 * @param noteThisWeekOnly   备注的可见范围（截图里那枚「仅本周 / 所有周」切换）
 * @param timeSlots          作息表，把"第 n 节"换算成具体钟点（表缺失时降级成只显示节次）
 * @param onToggleNoteScope  切换备注范围
 * @param onEdit             进编辑页
 * @param onDuplicate        复制整门课
 * @param onDeleteRequest    请求删除（这里只弹确认框，真删由调用方执行）
 * @param onDismiss          关闭
 */
// ModalBottomSheet 在 M3 里仍标 experimental（3.x 版本），项目首次使用，
// 按编译器要求显式 opt-in（其余 M3 组件本版本未标实验性，无需一并 opt-in）。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseDetailSheet(
    item: SessionWithCourse,
    note: String?,
    noteThisWeekOnly: Boolean,
    timeSlots: List<TimeSlot>,
    onToggleNoteScope: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember(item.session.id) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(DELETE_TITLE) },
            text = { Text(DELETE_TEXT.format(item.courseName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDeleteRequest()
                }) { Text(DELETE_CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(CANCEL) }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        CourseDetailBody(
            item = item,
            note = note,
            noteThisWeekOnly = noteThisWeekOnly,
            timeSlots = timeSlots,
            onToggleNoteScope = onToggleNoteScope,
            onEdit = onEdit,
            onDuplicate = onDuplicate,
            onDeleteRequest = { confirmingDelete = true },
            modifier = modifier,
        )
    }
}

/** 弹层内容（拆出来是因为 ModalBottomSheet 的 slot 里内容一长就不好读）。 */
@Composable
private fun CourseDetailBody(
    item: SessionWithCourse,
    note: String?,
    noteThisWeekOnly: Boolean,
    timeSlots: List<TimeSlot>,
    onToggleNoteScope: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = item.session
    val accent = CoursePalette.base(item.colorTag)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // M12 需求四：竖向内边距 12 → 8。原先底部三组动作整体偏下，
            // 小屏机型上几乎贴到 ModalBottomSheet 的下沿。
            // ModalBottomSheet 自己已经吃掉导航栏 insets，这里再留 12dp 就是双重留白。
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // ---------- 头：课程色竖条 + 课名 + 周次 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp) // 与课程卡同一套"色条"语言
                    .height(40.dp)
                    .background(accent),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatWeeks(session.startWeek, session.endWeek, session.weeks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = outlineDivider())
        Spacer(Modifier.height(4.dp))

        // ---------- 明细行 ----------
        DetailRow(
            label = LABEL_SECTION,
            value = sectionSpan(session.startSection, session.endSection),
        )
        DetailRow(
            label = LABEL_TIME,
            value = timeSpan(timeSlots, session.startSection, session.endSection),
        )
        session.classroom?.takeIf { it.isNotBlank() }?.let {
            DetailRow(label = LABEL_PLACE, value = it)
        }
        item.teacher?.takeIf { it.isNotBlank() }?.let {
            DetailRow(label = LABEL_TEACHER, value = it)
        }

        // ---------- 备注 + 「仅本周 / 所有周」切换 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.MinHeight)
                .clickable(onClick = onToggleNoteScope)
                .padding(vertical = ListRow.VerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LABEL_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (note.isNullOrBlank()) NOTE_EMPTY else note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // 当前生效的那一项是"选中态"：按产品口径选中一律黑字（不用 primary 蓝），
            // 靠字重而不是颜色拉开层次，全 App 的蓝色选中字样才统一。
            Text(
                text = if (noteThisWeekOnly) SCOPE_THIS_WEEK else SCOPE_ALL_WEEKS,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // M12 需求四：分隔线之上留 8dp（原 12dp），让三组动作与上方明细保持均匀呼吸感，
        // 整体视觉重心上移，不再压在小屏下沿。
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = outlineDivider())
        Spacer(Modifier.height(4.dp))

        // ---------- 底部三个动作 ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            DetailAction(text = ACTION_EDIT, onClick = onEdit)
            DetailAction(text = ACTION_COPY, onClick = onDuplicate)
            DetailAction(text = ACTION_DELETE, onClick = onDeleteRequest, danger = true)
        }
        // 末尾不再留 Spacer：ModalBottomSheet 的下沿自带系统栏安全区，
        // 这里再补一段空白只会让动作行显得更靠下（正是需求四要治的毛病）。
    }
}

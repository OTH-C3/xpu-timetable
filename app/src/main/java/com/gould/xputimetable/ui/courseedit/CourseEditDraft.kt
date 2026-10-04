/*
 * CourseEditDraft.kt —— 课程草稿与校验（M12 需求三：从 CourseEditViewModel 拆出）
 *
 * 拆分原因有二：
 *   1. **门禁**：CourseEditViewModel.kt 本轮要加「备注」字段与 setter，已贴 297 行上限；
 *   2. **可测性**：draft 与 validate 是纯逻辑（无 Context、无协程），
 *      独立成文件后单测不必牵动整个 ViewModel。
 *
 * 校验口径（与既有三个字段一致）：超长就报错并指出字段，不静默截断 ——
 * 静默截断会让用户以为整段都存进去了，事后查不出丢了什么。
 */
package com.gould.xputimetable.ui.courseedit

import com.gould.xputimetable.domain.model.WeekType

/** 出错的字段（用于精确高亮，避免用字符串 contains 判断）。 */
enum class DraftField {
    NAME,
    TEACHER,
    CLASSROOM,
    NOTE,
    SECTION_RANGE,
    WEEK_RANGE,
    OTHER,
}

data class CourseDraft(
    val courseId: String? = null,
    val sessionId: Long = 0L,
    val name: String = "",
    val teacher: String = "",
    val classroom: String = "",
    /** M12 需求三：课程备注（可空；存 courses.note，编辑时回填、显示在详情面板）。 */
    val note: String = "",
    val dayOfWeek: Int = 1,
    val startSection: Int = 1,
    val endSection: Int = 2,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekType: WeekType = WeekType.ALL,
    /** 载入的安排带显式周次列表（教务精确周次）：编辑页显示提示，保存时清空回到区间语义。 */
    val hasExactWeeks: Boolean = false,
    val colorTag: Int = 0,
    val termId: Long? = null,
    val loading: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val errorField: DraftField? = null,
)

/** 字段上限与取值范围（保存时校验，界面步进器与之一致）。 */
object DraftLimits {
    const val MAX_NAME_LENGTH = 40
    const val MAX_TEACHER_LENGTH = 20
    const val MAX_CLASSROOM_LENGTH = 20

    /**
     * 备注上限取 100 字（比名字/教室宽）。
     *
     * 备注是"给未来的自己写一句话"（带上教室怎么走、书带哪本），硬压到 20 字会明显不够用；
     * 100 字足够写两三句，又不至于让详情面板被一整屏文字撑爆。
     */
    const val MAX_NOTE_LENGTH = 100

    /** 节次/周次的合法范围。 */
    val SECTION_RANGE = 1..12
    val WEEK_RANGE = 1..30
}

/**
 * 保存前校验（纯函数，单测覆盖）。
 * 返回 null 表示通过；否则返回（给用户看的中文原因, 出错字段）。
 */
fun validateDraft(d: CourseDraft): Pair<String, DraftField>? = when {
    d.name.isBlank() -> "课程名不能为空" to DraftField.NAME
    d.name.trim().length > DraftLimits.MAX_NAME_LENGTH ->
        "课程名最多 ${DraftLimits.MAX_NAME_LENGTH} 个字" to DraftField.NAME
    d.teacher.trim().length > DraftLimits.MAX_TEACHER_LENGTH ->
        "教师名最多 ${DraftLimits.MAX_TEACHER_LENGTH} 个字" to DraftField.TEACHER
    d.classroom.trim().length > DraftLimits.MAX_CLASSROOM_LENGTH ->
        "教室最多 ${DraftLimits.MAX_CLASSROOM_LENGTH} 个字" to DraftField.CLASSROOM
    d.note.trim().length > DraftLimits.MAX_NOTE_LENGTH ->
        "备注最多 ${DraftLimits.MAX_NOTE_LENGTH} 个字" to DraftField.NOTE
    d.dayOfWeek !in 1..7 -> "星期必须在周一到周日之间" to DraftField.OTHER
    d.startSection !in DraftLimits.SECTION_RANGE || d.endSection !in DraftLimits.SECTION_RANGE ->
        "节次必须在 ${DraftLimits.SECTION_RANGE.first}-${DraftLimits.SECTION_RANGE.last} 之间" to
            DraftField.SECTION_RANGE
    d.startSection > d.endSection -> "开始节次不能晚于结束节次" to DraftField.SECTION_RANGE
    d.startWeek !in DraftLimits.WEEK_RANGE || d.endWeek !in DraftLimits.WEEK_RANGE ->
        "周次必须在 ${DraftLimits.WEEK_RANGE.first}-${DraftLimits.WEEK_RANGE.last} 之间" to
            DraftField.WEEK_RANGE
    d.startWeek > d.endWeek -> "起始周不能晚于结束周" to DraftField.WEEK_RANGE
    else -> null
}

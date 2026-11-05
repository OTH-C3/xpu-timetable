/*
 * CourseEditBuild.kt —— 由草稿构造待落库的 (课程, 节次列表)（从 CourseEditViewModel 拆出）
 *
 * 拆分动机：persist 把"怎么拼 Course/CourseSession"和"怎么写库/导航"揉在一起，
 * 既撑大 ViewModel（逼近 300 行门禁），又让拼接逻辑没法脱离 VM 单测。
 * 这里把它抽成纯函数——无 Context、无协程、不碰仓库——既能压回门禁，又能独立单测。
 */
package com.gould.xputimetable.ui.courseedit

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.domain.model.markEdited
import java.util.UUID

/**
 * 由草稿构造待 upsert 的 (课程, 节次列表)。
 *
 * 新增课程（originalCourse == null）用随机 id 建行；编辑既有课程则整条替换并 markEdited
 * （source 置 MANUAL，保护手动编辑不被任何来源导入覆盖，见 AC-22）。
 * 备注空串存 null（与"没填"同义）；手动改动周次回到区间语义（weeks = null，精确列表不保留）。
 */
fun buildCourseAndSessions(
    d: CourseDraft,
    originalCourse: Course?,
    termId: Long,
    now: Long,
): Pair<Course, List<CourseSession>> {
    val courseId = d.courseId ?: UUID.randomUUID().toString()
    val session = CourseSession(
        id = d.sessionId,
        courseId = courseId,
        dayOfWeek = d.dayOfWeek,
        startSection = d.startSection,
        endSection = d.endSection,
        startWeek = d.startWeek,
        endWeek = d.endWeek,
        weekType = d.weekType,
        weeks = null, // 手动改动周次 → 回到区间语义（Spec P0-A §2.6），精确列表不保留
        classroom = d.classroom.trim().takeIf { it.isNotEmpty() },
    )
    // 备注：空串存 null（与"没填"同义，详情面板据此显示「无备注」）
    val note = d.note.trim().takeIf { it.isNotEmpty() }
    val course = if (originalCourse != null) {
        originalCourse.copy(
            name = d.name.trim(),
            teacher = d.teacher.trim().takeIf { it.isNotEmpty() },
            note = note,
            colorTag = d.colorTag,
            termId = termId,
            updatedAt = now,
        ).markEdited(now)
    } else {
        Course(
            id = courseId,
            name = d.name.trim(),
            code = null,
            teacher = d.teacher.trim().takeIf { it.isNotEmpty() },
            note = note,
            colorTag = d.colorTag,
            source = CourseSource.MANUAL,
            createdAt = now,
            updatedAt = now,
            termId = termId,
            editedAt = null,
        )
    }
    return course to listOf(session)
}

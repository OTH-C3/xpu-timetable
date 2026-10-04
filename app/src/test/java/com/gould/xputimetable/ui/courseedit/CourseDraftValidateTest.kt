/*
 * CourseDraftValidateTest.kt —— 课程草稿校验（M12 需求三新增「备注」）
 *
 * 这批用例的重点是备注相关的三条：
 *   1. 备注为空不算错（可选字段）；
 *   2. 备注超长报的是 DraftField.NOTE 而不是别的字段——否则红框会框到课程名上；
 *   3. 校验按 trim 后的长度算，前后空格不该把人卡在门外。
 *
 * 其余字段的用例是为了守住"加一条 note 分支时没有误伤既有判定顺序"。
 */
package com.gould.xputimetable.ui.courseedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseDraftValidateTest {

    /** 一份各字段都合法的草稿，用 copy 逐项改成非法来测。 */
    private val valid = CourseDraft(name = "高等数学", teacher = "张老师", classroom = "教三 201")

    // ---------- 正常路径 ----------

    @Test
    fun `各字段合法时通过`() {
        assertNull(validateDraft(valid))
    }

    @Test
    fun `全空的可选字段不算错`() {
        assertNull(validateDraft(CourseDraft(name = "体育")))
    }

    @Test
    fun `备注为空不算错`() {
        // 需求三说备注是「可填」，不填必须能存
        assertNull(validateDraft(valid.copy(note = "")))
        assertNull(validateDraft(valid.copy(note = "   ")))
    }

    // ---------- 备注（M12 新增）----------

    @Test
    fun `备注正好一百字通过`() {
        val note = "字".repeat(DraftLimits.MAX_NOTE_LENGTH)
        assertEquals(DraftLimits.MAX_NOTE_LENGTH, note.length)
        assertNull(validateDraft(valid.copy(note = note)))
    }

    @Test
    fun `备注超长报备注字段`() {
        val note = "字".repeat(DraftLimits.MAX_NOTE_LENGTH + 1)
        val result = validateDraft(valid.copy(note = note))
        assertEquals(DraftField.NOTE, result?.second)
    }

    @Test
    fun `备注超长的提示里带上限数字`() {
        val note = "字".repeat(DraftLimits.MAX_NOTE_LENGTH + 5)
        val message = validateDraft(valid.copy(note = note))?.first.orEmpty()
        assertEquals(true, message.contains(DraftLimits.MAX_NOTE_LENGTH.toString()))
    }

    @Test
    fun `备注长度按去空格后计算`() {
        // 首尾塞 50 个空格，实际内容 60 字 —— 应通过
        val note = " ".repeat(50) + "字".repeat(60) + " ".repeat(50)
        assertNull(validateDraft(valid.copy(note = note)))
    }

    // ---------- 既有字段回归 ----------

    @Test
    fun `课程名为空报错`() {
        assertEquals(DraftField.NAME, validateDraft(valid.copy(name = "  "))?.second)
    }

    @Test
    fun `课程名超长报课程名`() {
        val result = validateDraft(valid.copy(name = "字".repeat(DraftLimits.MAX_NAME_LENGTH + 1)))
        assertEquals(DraftField.NAME, result?.second)
    }

    @Test
    fun `教师超长报教师`() {
        val result = validateDraft(valid.copy(teacher = "字".repeat(DraftLimits.MAX_TEACHER_LENGTH + 1)))
        assertEquals(DraftField.TEACHER, result?.second)
    }

    @Test
    fun `教室超长报教室`() {
        val result = validateDraft(valid.copy(classroom = "字".repeat(DraftLimits.MAX_CLASSROOM_LENGTH + 1)))
        assertEquals(DraftField.CLASSROOM, result?.second)
    }

    @Test
    fun `节次颠倒报节次`() {
        assertEquals(
            DraftField.SECTION_RANGE,
            validateDraft(valid.copy(startSection = 5, endSection = 3))?.second,
        )
    }

    @Test
    fun `周次越界报周次`() {
        assertEquals(
            DraftField.WEEK_RANGE,
            validateDraft(valid.copy(startWeek = 0, endWeek = 16))?.second,
        )
    }

    @Test
    fun `星期越界报其他`() {
        assertEquals(DraftField.OTHER, validateDraft(valid.copy(dayOfWeek = 8))?.second)
    }

    @Test
    fun `同时超长时先报课程名`() {
        // 校验是有序的 when，先命中先返回；这条钉住顺序，
        // 免得以后有人调整分支顺序导致红框位置变化
        val result = validateDraft(
            valid.copy(
                name = "字".repeat(DraftLimits.MAX_NAME_LENGTH + 1),
                note = "字".repeat(DraftLimits.MAX_NOTE_LENGTH + 1),
            ),
        )
        assertEquals(DraftField.NAME, result?.second)
    }
}

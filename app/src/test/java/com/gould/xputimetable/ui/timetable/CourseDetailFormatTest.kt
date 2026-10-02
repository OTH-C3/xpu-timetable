/*
 * CourseDetailFormatTest.kt —— 课程详情文本换算单测（M11-第三批）
 *
 * 覆盖 CourseDetailFormat.kt 三个纯函数：
 *   ① sectionSpan：连续节次 vs 单节；
 *   ② formatWeeks：逐周列表优先、排序、相邻合并、断开另起、空列表退回区间、乱序输入；
 *   ③ timeSpan：有作息表时换算成钟点、缺表时降级成节次、表为空串。
 *
 * 为什么值得写：这三个函数直接决定弹层上"第几周到第几周 / 几点上课"对不对，
 * 而教务导入里 weeks 是**可能乱序、可能稀疏**的（M2-B 就为 2,6,10,14 加过字段），
 * 靠肉眼在手机上看一眼很容易漏掉"排序后合并"这类逻辑错误。
 */
package com.gould.xputimetable.ui.timetable

import com.gould.xputimetable.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseDetailFormatTest {

    // ---------- ① sectionSpan ----------

    @Test
    fun `连续节次写成区间`() {
        assertEquals("1-2 节", sectionSpan(1, 2))
        assertEquals("5-8 节", sectionSpan(5, 8))
    }

    @Test
    fun `单节不加区间横线`() {
        assertEquals("3 节", sectionSpan(3, 3))
        assertEquals("12 节", sectionSpan(12, 12))
    }

    // ---------- ② formatWeeks ----------

    @Test
    fun `无逐周列表时退回区间`() {
        assertEquals("第 1-16 周", formatWeeks(1, 16, null))
        assertEquals("第 3 周", formatWeeks(3, 3, null))
    }

    @Test
    fun `空列表与 null 等价`() {
        assertEquals("第 1-16 周", formatWeeks(1, 16, emptyList()))
    }

    @Test
    fun `逐周列表优先于区间`() {
        // 区间写着 1-16，但逐周列表只有 2/4/6 周 —— 以列表为准，不能显示成 1-16 周
        assertEquals("第 2 周、第 4 周、第 6 周", formatWeeks(1, 16, listOf(2, 4, 6)))
    }

    @Test
    fun `相邻周合并成一个区间`() {
        assertEquals("第 2-4 周", formatWeeks(1, 16, listOf(2, 3, 4)))
        assertEquals("第 1-3 周、第 5-7 周", formatWeeks(1, 16, listOf(3, 1, 2, 7, 5, 6)))
    }

    @Test
    fun `乱序输入先排序再合并`() {
        // 教务逐周列表不保证升序；16 排到 1 前面也不能影响结果
        assertEquals("第 1-3 周、第 16 周", formatWeeks(1, 16, listOf(16, 3, 2, 1)))
    }

    @Test
    fun `断周与相邻分段混排`() {
        assertEquals("第 1-2 周、第 6 周、第 8-9 周", formatWeeks(1, 9, listOf(1, 2, 6, 8, 9)))
    }

    // ---------- ③ timeSpan ----------

    private fun slots(): List<TimeSlot> = listOf(
        TimeSlot(section = 1, startMinute = 480, endMinute = 530),   // 08:00 - 08:50
        TimeSlot(section = 2, startMinute = 530, endMinute = 600),   // 08:50 - 10:00
        TimeSlot(section = 12, startMinute = 1260, endMinute = 1320), // 21:00 - 22:00
    )

    @Test
    fun `有作息表时换算成钟点`() {
        assertEquals("8:00 - 8:50", timeSpan(slots(), 1, 1))
        assertEquals("8:00 - 10:00", timeSpan(slots(), 1, 2))
    }

    @Test
    fun `缺作息表时降级成节次而不是假钟点`() {
        assertEquals("3-4", timeSpan(emptyList(), 3, 4))
        assertEquals("3-3", timeSpan(emptyList(), 3, 3))
    }

    @Test
    fun `表里有但节次缺失也降级`() {
        // 只存了 1、2 节，课却在第 5 节 —— 不能编出 0:00
        assertEquals("5-6", timeSpan(slots(), 5, 6))
    }

    @Test
    fun `跨午夜的分钟能换算`() {
        val late = listOf(TimeSlot(section = 12, startMinute = 1320, endMinute = 1380)) // 22:00 - 23:00
        assertEquals("22:00 - 23:00", timeSpan(late, 12, 12))
    }
}

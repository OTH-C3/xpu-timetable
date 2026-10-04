/*
 * XpuWeekRepresentationTest.kt —— 周次表示规则纯函数的单测（Spec M2-B §6）
 *
 * 覆盖三类判定（区间 / 单双周 / 显式列表），重点：
 *   - 乱序 weekIndexes（实测踩坑：真实响应可能是 [16,17,18,11,...]）；
 *   - 重复周次；空列表 / 全非正；
 *   - 「纯偶但每隔 4 周」（2,6,10,14）必须走显式列表而非 EVEN（会多显示 4/8/12 周）。
 * 同时覆盖 WeekCalc.isSessionActive(weeks, ...) 重载（P0-A 的新判定入口）。
 */
package com.gould.xputimetable.parser.xpu

import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.domain.WeekCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XpuWeekRepresentationTest {

    // ---------- 区间 / 单双周 / 显式列表三类判定 ----------

    @Test
    fun 连续区间_表示为ALL区间_无列表() {
        val rep = weekRepresentation((4..18).toList())
        assertEquals(4, rep?.startWeek)
        assertEquals(18, rep?.endWeek)
        assertEquals(WeekType.ALL, rep?.weekType)
        assertNull(rep?.weeks)
    }

    @Test
    fun 单周_表示为ODD() {
        val rep = weekRepresentation(listOf(1, 3, 5, 7, 9))
        assertEquals(1, rep?.startWeek)
        assertEquals(9, rep?.endWeek)
        assertEquals(WeekType.ODD, rep?.weekType)
        assertNull(rep?.weeks)
    }

    @Test
    fun 双周_表示为EVEN() {
        val rep = weekRepresentation(listOf(2, 4, 6, 8))
        assertEquals(2, rep?.startWeek)
        assertEquals(8, rep?.endWeek)
        assertEquals(WeekType.EVEN, rep?.weekType)
        assertNull(rep?.weeks)
    }

    @Test
    fun 纯偶但每隔4周_必须走显式列表() {
        // 金样本真实数据：2,6,10,14 若按 EVEN+2..14 会多显示 4/8/12 周（可见缺陷）
        val rep = weekRepresentation(listOf(2, 6, 10, 14))
        assertEquals(2, rep?.startWeek)
        assertEquals(14, rep?.endWeek)
        assertEquals(WeekType.ALL, rep?.weekType)
        assertEquals(listOf(2, 6, 10, 14), rep?.weeks)
    }

    @Test
    fun 非连续非单双_走显式列表() {
        // 金样本真实数据：1~3(单),4~5,7~9,11~13,15~16
        val weeks = listOf(1, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15, 16)
        val rep = weekRepresentation(weeks)
        assertEquals(1, rep?.startWeek)
        assertEquals(16, rep?.endWeek)
        assertEquals(WeekType.ALL, rep?.weekType)
        assertEquals(weeks, rep?.weeks)
    }

    // ---------- 乱序 / 重复 / 边界（实测踩坑必须覆盖） ----------

    @Test
    fun 乱序weekIndexes_先排序去重再判定() {
        // 实测真实响应出现乱序（如 [16,17,18,11,...]），未排序会误判成显式列表
        val rep = weekRepresentation(listOf(16, 17, 18, 11, 12, 13, 14, 15))
        assertEquals(11, rep?.startWeek)
        assertEquals(18, rep?.endWeek)
        assertEquals(WeekType.ALL, rep?.weekType)
        assertNull(rep?.weeks)
    }

    @Test
    fun 乱序且无法区间化_列表升序() {
        val rep = weekRepresentation(listOf(14, 2, 10, 6, 2))
        assertEquals(listOf(2, 6, 10, 14), rep?.weeks)
    }

    @Test
    fun 重复周次_去重() {
        val rep = weekRepresentation(listOf(1, 1, 2, 2, 3))
        assertEquals(1, rep?.startWeek)
        assertEquals(3, rep?.endWeek)
        assertNull(rep?.weeks)
    }

    @Test
    fun 空列表_返回null() {
        assertNull(weekRepresentation(emptyList()))
    }

    @Test
    fun 全非正_返回null() {
        assertNull(weekRepresentation(listOf(0, -1, -3)))
    }

    @Test
    fun 单个周次_单点区间() {
        val rep = weekRepresentation(listOf(8))
        assertEquals(8, rep?.startWeek)
        assertEquals(8, rep?.endWeek)
        assertEquals(WeekType.ALL, rep?.weekType)
        assertNull(rep?.weeks)
    }

    // ---------- WeekCalc.isSessionActive(weeks, ...) 重载（P0-A 新入口） ----------

    @Test
    fun 显式列表判定_列表优先于区间与单双周() {
        val weeks = listOf(2, 6, 10, 14)
        // 列表内的周生效
        assertTrue(WeekCalc.isSessionActive(weeks, WeekType.ALL, 2, 14, 6))
        // 区间内但列表外：不生效（这正是 week_list 存在的意义）
        assertFalse(WeekCalc.isSessionActive(weeks, WeekType.ALL, 2, 14, 4))
        assertFalse(WeekCalc.isSessionActive(weeks, WeekType.ODD, 2, 14, 3))
    }

    @Test
    fun 列表为空_回落区间语义() {
        assertTrue(WeekCalc.isSessionActive(null, WeekType.ALL, 1, 16, 8))
        assertTrue(WeekCalc.isSessionActive(null, WeekType.ODD, 1, 16, 3))
        assertFalse(WeekCalc.isSessionActive(null, WeekType.ODD, 1, 16, 4))
        assertFalse(WeekCalc.isSessionActive(null, WeekType.ALL, 1, 16, 17))
    }
}

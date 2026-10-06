/*
 * ConfettiAlphaTest.kt —— 彩点整体不透明度的时间曲线（M14）
 *
 * 为什么从 ConfettiTest 拆出来：那是 38 个用例的单文件，超了 300 行门禁。
 * 且两者关注点不同 —— 那份管"物理运动对不对"，这份管"时间轴形状对不对"，
 * 混在一起时看失败信息要在一堆轨迹断言里找透明度断言。
 */
package com.gould.xputimetable.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfettiAlphaTest {

    private val total = 1100f

    @Test
    fun `开始时完全透明`() {
        assertEquals(0f, confettiAlpha(0f, 1100f), 0.001f)
    }

    @Test
    fun `结束时完全透明`() {
        // 播完必须归零，否则会留一层半透明色块在卡片上
        assertEquals(0f, confettiAlpha(1100f, 1100f), 0.001f)
    }

    @Test
    fun `超时不留残影`() {
        assertEquals(0f, confettiAlpha(9999f, 1100f), 0.001f)
    }

    @Test
    fun `负时间不透明度为零`() {
        assertEquals(0f, confettiAlpha(-10f, 1100f), 0.001f)
    }

    @Test
    fun `中段完全不透明`() {
        assertEquals(1f, confettiAlpha(500f, 1100f), 0.001f)
    }

    @Test
    fun `开场快速淡入`() {
        // 淡入太快会像凭空出现，太慢看不清撒的瞬间
        val early = confettiAlpha(40f, 1100f)
        assertTrue("44ms（4%）时应已可见，实际 $early", early > 0.3f)
    }

    @Test
    fun `末段单调淡出且不回升`() {
        var prev = 1f
        for (t in 800..1100 step 20) {
            val a = confettiAlpha(t.toFloat(), 1100f)
            assertTrue("t=$t 不透明度回升了（$a > $prev）", a <= prev)
            prev = a
        }
    }
}

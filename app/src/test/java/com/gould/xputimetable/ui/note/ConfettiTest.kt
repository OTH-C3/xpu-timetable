/*
 * ConfettiTest.kt —— 彩带运动学的纯函数测试（需求二）
 *
 * 这套算式全凭手感调参，最容易出的错是"看着没问题但物理上反了"
 * （往上喷的彩带往下掉、颜色全挤在一起、播完不消失）。
 * 这些都能用单测钉住，而且必须钉 —— 真机上彩带只播 1.1 秒，
 * 靠肉眼很难稳定复现每一个分支。
 */
package com.gould.xputimetable.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfettiTest {

    // ---------- confettiPieces ----------

    @Test
    fun `生成指定片数`() {
        assertEquals(18, confettiPieces(18, 5, seed = 1, speedMinMax = 0.5f..1f).size)
    }

    @Test
    fun `单片生成一片`() {
        assertEquals(1, confettiPieces(1, 5, seed = 1, speedMinMax = 0.5f..1f).size)
    }

    @Test
    fun `同种子结果确定`() {
        val a = confettiPieces(10, 5, seed = 42, speedMinMax = 0.5f..1f)
        val b = confettiPieces(10, 5, seed = 42, speedMinMax = 0.5f..1f)
        assertEquals(a, b)
    }

    @Test
    fun `不同种子结果不同`() {
        // 每次完成都该换一个喷法，不能每次都长得一模一样
        val a = confettiPieces(10, 5, seed = 1, speedMinMax = 0.5f..1f)
        val b = confettiPieces(10, 5, seed = 2, speedMinMax = 0.5f..1f)
        assertNotEquals(a, b)
    }

    @Test
    fun `所有彩带都在设定扇形内`() {
        val pieces = confettiPieces(60, 5, seed = 7, speedMinMax = 0.5f..1f, fanDeg = 110f)
        pieces.forEach { p ->
            assertTrue("角度 ${p.angleDeg} 应在 ±55 内", kotlin.math.abs(p.angleDeg) <= 55.5f)
        }
    }

    @Test
    fun `没有彩带被喷到正下方`() {
        // 需求是"从方框后面蹦出来"= 往上喷。喷到下方的彩带会糊在卡片文字上
        val pieces = confettiPieces(80, 5, seed = 3, speedMinMax = 0.5f..1f, fanDeg = 110f)
        pieces.forEach { p ->
            assertTrue("角度 ${p.angleDeg} 不该越过 ±90", kotlin.math.abs(p.angleDeg) < 90f)
        }
    }

    @Test
    fun `颜色下标均匀分配不重复扎堆`() {
        val pieces = confettiPieces(10, 5, seed = 11, speedMinMax = 0.5f..1f)
        val colors = pieces.map { it.colorIndex }.toSet()
        assertEquals("10 片 5 色应全部用上", 5, colors.size)
    }

    @Test
    fun `速度落在设定区间内`() {
        val pieces = confettiPieces(40, 5, seed = 5, speedMinMax = 0.55f..1.05f)
        pieces.forEach { p ->
            assertTrue(p.speed in 0.55f..1.05f)
        }
    }

    @Test
    fun `自旋有正有负`() {
        // 全都朝一个方向转会像风车，机械
        val pieces = confettiPieces(20, 5, seed = 9, speedMinMax = 0.5f..1f)
        assertTrue(pieces.any { it.spin > 0f })
        assertTrue(pieces.any { it.spin < 0f })
    }

    // ---------- confettiOffset ----------

    @Test
    fun `t等于零时在原点`() {
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        val (x, y) = confettiOffset(p, 0f, CONFETTI_GRAVITY)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `正上方的彩带先向上飞`() {
        // angleDeg=0 定义为正上方：y 是屏幕坐标，向上的 y 必须是负的
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        val (_, y) = confettiOffset(p, 50f, CONFETTI_GRAVITY)
        assertTrue("50ms 时应仍在向上（y<0），实际 $y", y < 0f)
    }

    @Test
    fun `越过最高点后开始下落`() {
        // 最高点出现在 t = v/g（v=1.0, g=0.00392 → 约 255ms），之后 y 单调增大。
        // 断言要用「峰前」与「峰后」两个时刻，而不是随手取 100/300 ——
        // 100ms 和 300ms 都还在发射点上方（都在上升段），比出来的关系没有意义。
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        val atPeak = confettiOffset(p, 255f, CONFETTI_GRAVITY).second
        val afterPeak = confettiOffset(p, 400f, CONFETTI_GRAVITY).second
        assertTrue("400ms 应已越过最高点（y 更大），实际 $afterPeak vs $atPeak", afterPeak > atPeak)
    }

    @Test
    fun `上升段高度随时间增大`() {
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        val y50 = confettiOffset(p, 50f, CONFETTI_GRAVITY).second
        val y150 = confettiOffset(p, 150f, CONFETTI_GRAVITY).second
        assertTrue("150ms 应比 50ms 更高（y 更负），实际 $y150 vs $y50", y150 < y50)
    }

    @Test
    fun `最高点在理论峰值时刻附近`() {
        // 抛物线顶点 t = v/g。用 255 与 260 两点比较，高度应几乎相等
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        val a = confettiOffset(p, 255f, CONFETTI_GRAVITY).second
        val b = confettiOffset(p, 260f, CONFETTI_GRAVITY).second
        assertTrue("顶点附近高度应基本持平，实际 $a vs $b", kotlin.math.abs(a - b) < 3f)
    }

    @Test
    fun `向右的彩带x为正`() {
        val p = ConfettiPiece(60f, 1f, 0f, 10f, 3f, 0, 0f)
        val (x, _) = confettiOffset(p, 50f, CONFETTI_GRAVITY)
        assertTrue("角度 +60° 应偏右（x>0），实际 $x", x > 0f)
    }

    @Test
    fun `向左的彩带x为负`() {
        val p = ConfettiPiece(-60f, 1f, 0f, 10f, 3f, 0, 0f)
        val (x, _) = confettiOffset(p, 50f, CONFETTI_GRAVITY)
        assertTrue("角度 -60° 应偏左（x<0），实际 $x", x < 0f)
    }

    @Test
    fun `左右对称的两片横向距离相等`() {
        val l = confettiOffset(ConfettiPiece(-45f, 1f, 0f, 10f, 3f, 0, 0f), 200f, CONFETTI_GRAVITY)
        val r = confettiOffset(ConfettiPiece(45f, 1f, 0f, 10f, 3f, 0, 0f), 200f, CONFETTI_GRAVITY)
        assertEquals("±45° 的 x 应等大反号", -l.first, r.first, 0.01f)
    }

    @Test
    fun `零重力时做匀速直线运动`() {
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        val (_, y100) = confettiOffset(p, 100f, 0f)
        val (_, y200) = confettiOffset(p, 200f, 0f)
        assertEquals("零重力下 200ms 的位移应是 100ms 的两倍", y100 * 2, y200, 0.001f)
    }

    // ---------- confettiSpin ----------

    @Test
    fun `不自旋时角度恒为零`() {
        val p = ConfettiPiece(0f, 1f, 0f, 10f, 3f, 0, 0f)
        assertEquals(0f, confettiSpin(p, 500f), 0.001f)
    }

    @Test
    fun `自旋随时间线性累积`() {
        val p = ConfettiPiece(0f, 1f, 0.5f, 10f, 3f, 0, 0f)
        assertEquals(50f, confettiSpin(p, 100f), 0.001f)
        assertEquals(150f, confettiSpin(p, 300f), 0.001f)
    }

    @Test
    fun `负自旋往反方向转`() {
        val p = ConfettiPiece(0f, 1f, -0.5f, 10f, 3f, 0, 0f)
        assertTrue(confettiSpin(p, 200f) < 0f)
    }

    // ---------- confettiAlpha ----------

    @Test
    fun `开始时完全透明`() {
        assertEquals(0f, confettiAlpha(0f, 1100f), 0.001f)
    }

    @Test
    fun `结束时完全透明`() {
        // 播完必须归零，否则会留一层半透明方块在卡片上
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
        // 淡入太快会像凭空出现，太慢看不清喷的瞬间
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

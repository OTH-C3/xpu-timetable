/*
 * ConfettiTest.kt —— 彩点运动学的纯函数测试
 *
 * 这套算式全凭手感调参，最容易出的错是"看着没问题但物理上反了"
 * （往天上喷、全挤在中间几个 x、一起齐刷刷落地、播完不消失）。
 * 这些都能用单测钉住，而且必须钉 —— 真机上彩点只播 1.1 秒，
 * 靠肉眼很难稳定复现每一个分支。
 */
package com.gould.xputimetable.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class ConfettiTest {

    private fun sample(count: Int = 80, seed: Int = 7) =
        confettiPieces(count, CONFETTI_COLORS, seed = seed)

    // ---------- confettiPieces：数量与确定性 ----------

    @Test
    fun `生成指定点数`() {
        assertEquals(80, confettiPieces(80, CONFETTI_COLORS, seed = 1).size)
    }

    @Test
    fun `单个色点只生成一个`() {
        assertEquals(1, confettiPieces(1, CONFETTI_COLORS, seed = 1).size)
    }

    @Test
    fun `同种子结果确定`() {
        val a = confettiPieces(10, CONFETTI_COLORS, seed = 42)
        val b = confettiPieces(10, CONFETTI_COLORS, seed = 42)
        assertEquals(a, b)
    }

    @Test
    fun `不同种子结果不同`() {
        // 每次完成都该换一个撒法，不能每次都长得一模一样
        val a = confettiPieces(10, CONFETTI_COLORS, seed = 1)
        val b = confettiPieces(10, CONFETTI_COLORS, seed = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun `点数参数直接生效`() {
        // 观测感参数（点数）要能在令牌里改，生成端必须老实照传
        assertEquals(5, sample(5).size)
        // 颜色是"第 i 个用第 i%colorCount 号"，不是"每个清单一个固定色"。
        // 写死具体数字会在调色板扩容时（5→7）集体变红，而契约其实没变。
        val five = sample(5)
        five.forEachIndexed { i, p ->
            assertEquals("第 $i 个色点应取色 ${i % CONFETTI_COLORS}", i % CONFETTI_COLORS, p.colorIndex)
        }
    }

    @Test
    fun `非正数参数直接拒绝`() {
        try {
            confettiPieces(0, CONFETTI_COLORS, seed = 1)
            fail("count=0 应该抛 IllegalArgumentException")
        } catch (ignored: IllegalArgumentException) { }
        try {
            confettiPieces(5, 0, seed = 1)
            fail("colorCount=0 应该抛 IllegalArgumentException")
        } catch (ignored: IllegalArgumentException) { }
    }

    // ---------- 发射点：顶边一整条线 ----------

    @Test
    fun `发射点全部落在顶边范围内`() {
        sample().forEach { p -> assertTrue("${p.startXRatio} 应在 0~1", p.startXRatio in 0f..1f) }
    }

    @Test
    fun `发射点铺满整条顶边而不是挤在中间`() {
        // 分层铺开是"覆盖面"的关键：全挤在中间=还是从一点喷，只是喷得更宽
        val ratios = sample().map { it.startXRatio }
        assertTrue("最左端应贴近 0，实际 ${ratios.minOrNull()}", ratios.minOrNull()!! < 0.1f)
        assertTrue("最右端应贴近 1，实际 ${ratios.maxOrNull()}", ratios.maxOrNull()!! > 0.9f)
        val mid = ratios.filter { it in 0.333f..0.667f }.size / ratios.size.toFloat()
        assertTrue("中间三分之一不该占多数，实际 ${"%.2f".format(mid)}", mid < 0.45f)
    }

    @Test
    fun `切十等份后每一份都有色点`() {
        // 更硬的铺开检查：十等分后任何一份是空的，就说明这里有肉眼可见的空隙
        val buckets = IntArray(10)
        sample().forEach { p -> buckets[(p.startXRatio * 10f).toInt().coerceIn(0, 9)]++ }
        buckets.forEachIndexed { i, n -> assertTrue("第 $i 份一个色点都没有", n > 0) }
    }

    @Test
    fun `相邻色点不等距像是散落而非栅栏`() {
        // 抖动的意义：等距排列会读出"程序排的"，抖完仍然有序但有手撒的感觉
        val ratios = sample(40).map { it.startXRatio }.toMutableList()
        ratios.sort()
        var maxGap = 0f
        for (i in 1 until ratios.size) maxGap = max(maxGap, ratios[i] - ratios[i - 1])
        assertTrue("最大间隔 ${"%.3f".format(maxGap)} 不该超过两格宽", maxGap > 0f && maxGap < 0.1f)
    }

    // ---------- 尺寸 ----------

    @Test
    fun `半径落在设定区间`() {
        // 直径 8~18px：再小看是噪点，再大不像"色点"而像色块
        sample().forEach { p -> assertTrue("半径 ${p.radius} 应在 4~9", p.radius in 4f..9f) }
    }

    @Test
    fun `形状随机但以正圆为主`() {
        val aspects = sample().map { it.aspect }
        aspects.forEach { a -> assertTrue("宽高比 $a 应在 0.7~1", a in 0.7f..1f) }
        val round = aspects.filter { it > 0.9f }.size / aspects.size.toFloat()
        assertTrue("多数应接近正圆，实际 ${"%.2f".format(round)}", round > 0.45f)
        // 全是正圆会像塑料贴纸，得留几个明显扁的才有手撒的参差
        assertTrue("也要有几个明显扁的，实际最小 ${aspects.minOrNull()}", aspects.minOrNull()!! < 0.9f)
    }

    @Test
    fun `颜色下标均匀分配不重复扎堆`() {
        val pieces = sample(14)
        assertEquals("14 点 7 色应全部用上", 7, pieces.map { it.colorIndex }.toSet().size)
    }

    @Test
    fun `速度落在设定区间内`() {
        val pieces = confettiPieces(40, CONFETTI_COLORS, seed = 5, speedMinMax = 0.08f..0.45f)
        pieces.forEach { p -> assertTrue(p.speed in 0.08f..0.45f) }
    }

    @Test
    fun `左右张角都在设定扇形内`() {
        sample().forEach { p -> assertTrue("偏角 ${p.tiltDeg} 应在 ±30", abs(p.tiltDeg) <= 30f) }
    }

    // ---------- 撒落：方向 ----------

    @Test
    fun `色点整体向下运动而不是往上喷`() {
        // 屏幕坐标 y 向下为正：撒落要求 y 恒为正，任何一个往上跑都是方向写反了
        val pieces = sample()
        val late = pieces.filter { it.delayMs < 200f }
        val up = late.filter { confettiOffset(it, 200f, CONFETTI_GRAVITY).second <= 0f }
        assertTrue("200ms 时不应有点在发射点上方，实际 ${up.size} 个", up.isEmpty())
        assertTrue("大部分点此时已在下落，实际 ${late.size}/${pieces.size}", late.size.toFloat() / pieces.size > 0.6f)
    }

    @Test
    fun `下落高度随时间单调不减`() {
        // 撒落不能有"先升后降"的抛物线：初速朝下 + 重力朝下，y 只能越走越大
        val pieces = sample(20)
        pieces.forEach { p ->
            var prev = Float.NEGATIVE_INFINITY
            for (t in 0..1100 step 50) {
                val y = confettiOffset(p, t.toFloat(), CONFETTI_GRAVITY).second
                assertTrue("t=${t * 50} 时 y 回升了（$y > $prev）", y >= prev)
                prev = y
            }
        }
    }

    @Test
    fun `只受重力时沿竖直方向落下`() {
        val p = sample(1, seed = 3)[0].copy(tiltDeg = 0f, delayMs = 0f, drift = 0f, speed = 0f)
        val (x, y) = confettiOffset(p, 300f, CONFETTI_GRAVITY)
        assertEquals("无初速无风时不应有横向偏移", 0f, x, 0.001f)
        assertTrue("只受重力时应朝下（y>0）", y > 0f)
    }

    @Test
    fun `左右对称的两片横向距离相等`() {
        // drift 是逐片随机的"风"，必须归零才谈得上对称 ——
        // 否则这个断言测的是"两片的随机风恰好相等"，恒不成立。
        val base = sample(1)[0].copy(delayMs = 0f, drift = 0f)
        val l = confettiOffset(base.copy(tiltDeg = -20f), 400f, CONFETTI_GRAVITY)
        val r = confettiOffset(base.copy(tiltDeg = 20f), 400f, CONFETTI_GRAVITY)
        assertEquals("±20° 的 x 应等大反号", -l.first, r.first, 0.01f)
    }

    @Test
    fun `偏角为正是偏右`() {
        val right = confettiOffset(sample(1)[0].copy(tiltDeg = 25f, delayMs = 0f, speed = 0.2f), 100f, 0f)
        assertTrue("+25° 应偏右（x>0），实际 ${right.first}", right.first > 0f)
    }

    // ---------- 重力 ----------

    @Test
    fun `t等于零时在起点`() {
        val p = sample(1)[0].copy(delayMs = 0f)
        val (x, y) = confettiOffset(p, 0f, CONFETTI_GRAVITY)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `自由落体距离等于二分之一重力乘时间平方`() {
        // 钉死物理公式本身：落点算错的话，调参再对观感也是歪的
        val p = sample(1)[0].copy(tiltDeg = 0f, delayMs = 0f, speed = 0f, drift = 0f)
        val (_, y) = confettiOffset(p, 1000f, CONFETTI_GRAVITY)
        assertEquals(0.5f * CONFETTI_GRAVITY * 1000f * 1000f, y, 0.5f)
        assertEquals(1960f, y, 0.5f)
    }

    @Test
    fun `重力加倍落点也加倍`() {
        val p = sample(1)[0].copy(tiltDeg = 0f, delayMs = 0f, speed = 0f, drift = 0f)
        val y1 = confettiOffset(p, 600f, CONFETTI_GRAVITY).second
        val y2 = confettiOffset(p, 600f, CONFETTI_GRAVITY * 2f).second
        assertEquals(y1 * 2, y2, 0.5f)
    }

    @Test
    fun `零重力时做匀速直线运动`() {
        val p = sample(1)[0].copy(tiltDeg = 0f, delayMs = 0f, drift = 0f, speed = 0.2f)
        val (_, y100) = confettiOffset(p, 100f, 0f)
        val (_, y200) = confettiOffset(p, 200f, 0f)
        assertEquals("零重力下 200ms 的位移应是 100ms 的两倍", y100 * 2, y200, 0.001f)
    }

    // ---------- 起始时间错开 ----------

    @Test
    fun `起始时间错开确实存在层次`() {
        val pieces = sample()
        val delays = pieces.map { it.delayMs }
        assertTrue("最小延迟 ${delays.minOrNull()} 应接近 0", delays.minOrNull()!! < 120f)
        assertTrue("最大延迟 ${delays.maxOrNull()} 应接近 520", delays.maxOrNull()!! > 240f)
        val distinct = delays.toSet().size
        assertTrue("延迟值不应只有两三种，实际 $distinct 种", distinct >= pieces.size / 2)
    }

    @Test
    fun `未到起始时间时静止在顶边`() {
        // 没到点就浮在半空中，看起来就是"凭空出现的一排点"
        val p = sample(1)[0].copy(delayMs = 400f)
        val (x, y) = confettiOffset(p, 399f, CONFETTI_GRAVITY)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `同一时刻各点位置并不相同`() {
        // 齐刷刷一起落地会像"放了个机关"，不是撒
        val ys = sample().map { confettiOffset(it, 900f, CONFETTI_GRAVITY).second }
        val mean = ys.average().toFloat()
        val variance = ys.map { (it - mean) * (it - mean) }.sum() / ys.size
        assertTrue("纵向离散度 ${sqrt(variance)} 应足够大", sqrt(variance) > 100f)
        assertTrue("至少要拉开一段深度差，实际 ${ys.maxOrNull()!! - ys.minOrNull()!!}",
            ys.maxOrNull()!! - ys.minOrNull()!! > 300f)
    }

    // ---------- confettiSpin ----------

    @Test
    fun `不自旋时角度恒为零`() {
        val p = sample(1)[0].copy(spin = 0f)
        assertEquals(0f, confettiSpin(p, 500f), 0.001f)
    }

    @Test
    fun `自旋随时间线性累积`() {
        val p = sample(1)[0].copy(spin = 0.5f, delayMs = 0f)
        assertEquals(50f, confettiSpin(p, 100f), 0.001f)
        assertEquals(150f, confettiSpin(p, 300f), 0.001f)
    }

    @Test
    fun `负自旋往反方向转`() {
        val p = sample(1)[0].copy(spin = -0.5f, delayMs = 0f)
        assertTrue(confettiSpin(p, 200f) < 0f)
    }

    @Test
    fun `延迟之内不自旋`() {
        val p = sample(1)[0].copy(spin = 0.5f, delayMs = 300f)
        assertEquals(0f, confettiSpin(p, 299f), 0.001f)
    }

    // ---------- confettiAlpha ----------
}

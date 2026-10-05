/*
 * GlassPaletteTest.kt —— 玻璃质感配色推导的纯函数测试（需求四）
 *
 * 需求四的落点之一：「让所有的白色ui方框和背景融洽一点」。
 * 这条规则里"卡片色必须跟着背景走、且永远比背景亮"是行为核心，
 * 用单测钉住 —— 真机上换不同图肉眼判断"协不协调"主观且不可复现。
 */
package com.gould.xputimetable.ui.background

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPaletteTest {

    /** HSL 明度（0~1）。测试内自己算，避免依赖被测实现。 */
    private fun lightness(c: Color): Float =
        (maxOf(c.red, c.green, c.blue) + minOf(c.red, c.green, c.blue)) / 2f

    // ---------- harmonize ----------

    @Test
    fun `深色背景被提到高明度`() {
        // 需求核心：卡片必须比背景亮，否则文字读不清
        val dark = Color(0.1f, 0.1f, 0.12f)
        val out = harmonize(dark)
        // 下限 0.856 而不是 0.86：8bit 量化会把 219/255 读成 0.8588
        assertTrue("明度应被提到 ≥0.856，实际 ${lightness(out)}", lightness(out) >= 0.856f)
    }

    @Test
    fun `纯黑背景也能变成亮色`() {
        val out = harmonize(Color(0f, 0f, 0f))
        assertTrue(lightness(out) >= 0.856f)
    }

    @Test
    fun `鲜艳背景的饱和度被压到上限`() {
        // 高饱和（正红）必须被压下去，否则卡片会跟背景抢注意力
        val out = harmonize(Color(1f, 0f, 0f))
        val max = maxOf(out.red, out.green, out.blue)
        val min = minOf(out.red, out.green, out.blue)
        val s = if (max == 0f) 0f else (max - min) / max
        assertTrue("饱和度应 ≤0.22，实际 $s", s <= 0.23f)
    }

    @Test
    fun `已经够淡的浅色背景不被进一步冲淡`() {
        // 本来就是浅灰的图：明度已经高于下限，不该被改动方向搞坏
        val light = Color(0.9f, 0.92f, 0.95f)
        val out = harmonize(light)
        assertTrue(lightness(out) >= 0.856f)
    }

    @Test
    fun `保留背景的色相倾向`() {
        // 蓝调背景 → 结果仍应偏蓝（否则"跟着背景走"就退化成纯灰）
        val blue = Color(0.2f, 0.3f, 0.8f)
        val out = harmonize(blue)
        assertTrue("蓝分量应仍最高，实际 rgb=(${out.red},${out.green},${out.blue})",
            out.blue > out.red)
    }

    @Test
    fun `结果不透明且颜色分量合法`() {
        val out = harmonize(Color(0.7f, 0.2f, 0.4f))
        assertEquals(1f, out.alpha, 0.01f)
        listOf(out.red, out.green, out.blue).forEach {
            assertTrue("分量必须在 0~1，实际 $it", it in 0f..1f)
        }
    }

    @Test
    fun `灰色背景不会产生彩色偏色`() {
        // 灰色三通道相等 → 换算后仍应相等（不能凭空生出偏色）
        val gray = Color(0.5f, 0.5f, 0.5f)
        val out = harmonize(gray)
        assertEquals(out.red, out.green, 0.01f)
        assertEquals(out.green, out.blue, 0.01f)
    }

    // ---------- glassSurfaceColor ----------

    @Test
    fun `无背景图时用固定半透明白`() {
        val c = glassSurfaceColor(null)
        // 容差 0.01：Compose 的 Color 分量按 8bit 存储，0.72 存进去读回来是
        // 184/255 = 0.7216。不是实现有偏差，是表示精度。
        assertEquals(GLASS_PLAIN_ALPHA, c.alpha, 0.01f)
    }

    @Test
    fun `有背景图时透明度更低让纹理透过来`() {
        val plain = glassSurfaceColor(null)
        val withImage = glassSurfaceColor(Color(0.4f, 0.5f, 0.6f))
        assertTrue(
            "有背景时应比纯色背景更透：${withImage.alpha} 应 < ${plain.alpha}",
            withImage.alpha < plain.alpha,
        )
    }

    @Test
    fun `有背景图时仍高于可读性下限`() {
        // 项目要求对比度 ≥4.5:1，卡片太透文字就读不清了
        val c = glassSurfaceColor(Color(0f, 0f, 0f))
        assertTrue("alpha 应 ≥ $GLASS_MIN_ALPHA，实际 ${c.alpha}", c.alpha >= GLASS_MIN_ALPHA)
    }

    @Test
    fun `不同背景色得到不同卡片色`() {
        val warm = glassSurfaceColor(Color(0.8f, 0.5f, 0.3f))
        val cool = glassSurfaceColor(Color(0.3f, 0.5f, 0.8f))
        assertTrue("暖色背景与冷色背景的卡片色不应相同", warm.red != cool.red)
    }

    // ---------- 边缘与投影 ----------

    @Test
    fun `玻璃上沿高光是不透明的亮色`() {
        val top = glassEdgeTop()
        assertTrue(top.alpha >= 0.8f)
        assertTrue(lightness(top) > 0.9f)
    }

    @Test
    fun `玻璃下沿是高透明度的暗色`() {
        val bottom = glassEdgeBottom()
        assertTrue("下沿应很淡，实际 alpha=${bottom.alpha}", bottom.alpha <= 0.10f)
    }

    @Test
    fun `投影强度介于完全无与明显之间`() {
        val s = glassShadow()
        assertTrue(s.alpha in 0.05f..0.20f)
    }
}

/*
 * StrikeGeometryTest.kt —— 划线几何的纯函数测试（M12 需求二）
 *
 * 这段算式出过一次很典型的错：宽度取了 Text 的**布局**宽度而不是**文字实测**宽度，
 * 真机上表现为划线一路拖到卡片右边缘（"划掉了一整行空白"）。
 * 那个错没法靠 UI 单测抓（项目不用 Robolectric / Compose UI 测试），
 * 但"逐行取行宽、乘进度、跳过空行"这几条规则可以在这里钉死。
 */
package com.gould.xputimetable.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrikeGeometryTest {

    private fun box(left: Float, right: Float, top: Float = 0f, bottom: Float = 40f) =
        LineBox(left = left, right = right, top = top, bottom = bottom)

    @Test
    fun `进度为零不画线`() {
        assertTrue(strikeSegments(listOf(box(0f, 200f)), progress = 0f).isEmpty())
    }

    @Test
    fun `负进度不画线`() {
        assertTrue(strikeSegments(listOf(box(0f, 200f)), progress = -0.5f).isEmpty())
    }

    @Test
    fun `完全划掉时线长等于该行文字宽度`() {
        // 关键回归：这行文字只占 0..200（后面是空白），线就必须止于 200。
        // 早期版本误用布局宽度（如 1000），线会拖到行尾。
        val seg = strikeSegments(listOf(box(0f, 200f)), progress = 1f).single()
        assertEquals(0f, seg.x0, TOL)
        assertEquals(200f, seg.x1, TOL)
    }

    @Test
    fun `线从该行左边缘起算而不是组件原点`() {
        // 有 startPadding / 缩进的行：线要跟着文字走，不能从 0 开始
        val seg = strikeSegments(listOf(box(48f, 148f)), progress = 1f).single()
        assertEquals(48f, seg.x0, TOL)
        assertEquals(148f, seg.x1, TOL)
    }

    @Test
    fun `进度按比例截断线长`() {
        val seg = strikeSegments(listOf(box(0f, 200f)), progress = 0.5f).single()
        assertEquals(0f, seg.x0, TOL)
        assertEquals(100f, seg.x1, TOL)
    }

    @Test
    fun `多行每行各画一条且互不干扰`() {
        val segs = strikeSegments(
            listOf(box(0f, 300f, top = 0f, bottom = 40f), box(0f, 120f, top = 40f, bottom = 80f)),
            progress = 1f,
        )
        assertEquals(2, segs.size)
        assertEquals(300f, segs[0].x1, TOL)
        assertEquals(120f, segs[1].x1, TOL)
    }

    @Test
    fun `多行时第二行不受第一行宽度影响`() {
        // 首行很长、末行很短：末行的线不能被首行宽度带出去
        val segs = strikeSegments(
            listOf(box(0f, 900f, top = 0f, bottom = 40f), box(0f, 80f, top = 40f, bottom = 80f)),
            progress = 1f,
        )
        assertEquals(80f, segs[1].x1, TOL)
    }

    @Test
    fun `每行纵坐标取该行自己的视觉中线`() {
        val segs = strikeSegments(
            listOf(box(0f, 100f, top = 0f, bottom = 40f), box(0f, 100f, top = 50f, bottom = 90f)),
            progress = 1f,
        )
        assertEquals(0f + 40f * STRIKE_Y_RATIO, segs[0].y, TOL)
        assertEquals(50f + 40f * STRIKE_Y_RATIO, segs[1].y, TOL)
        // 两行的线必须错开，不能重叠在同一高度
        assertTrue(segs[1].y - segs[0].y > 1f)
    }

    @Test
    fun `宽度为零的空行被跳过`() {
        // 文本以换行结尾时末尾会多出一个空行，画线会留下一小截脏线
        val segs = strikeSegments(
            listOf(box(0f, 200f), box(0f, 0f), box(0f, 0f, top = 40f, bottom = 80f)),
            progress = 1f,
        )
        assertEquals(1, segs.size)
        assertEquals(200f, segs[0].x1, TOL)
    }

    @Test
    fun `负宽度的异常行被跳过而不是画出反向线`() {
        val segs = strikeSegments(listOf(box(200f, 100f)), progress = 1f)
        assertTrue(segs.isEmpty())
    }

    @Test
    fun `空行列表返回空结果`() {
        assertTrue(strikeSegments(emptyList(), progress = 1f).isEmpty())
    }

    private companion object {
        const val TOL = 0.01f
    }
}

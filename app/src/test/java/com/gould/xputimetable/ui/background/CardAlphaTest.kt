/*
 * CardAlphaTest.kt —— 卡片透明度的纯函数测试（M13：需求「为白色UI方框增加透明度调节」）
 *
 * 这条需求最容易悄悄坏掉的地方不是"滑块画不出来"，而是**值传了但画面不动**：
 * GroupCard 的 remember 少了 key、预览沿用了用户的作用范围、滑块下限低于可读性下限，
 * 这三种都编译得过、跑起来也不报错，只有真机调半天的用户知道。
 * 这里用单测把"设定值 → 实际不透明度"这条链路钉住。
 */
package com.gould.xputimetable.ui.background

import androidx.compose.ui.graphics.Color
import com.gould.xputimetable.ui.theme.GlassCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAlphaTest {

    // ---------- resolveCardAlpha：基准值 → 实际不透明度 ----------

    @Test
    fun `纯色背景时实际不透明度就是基准值`() {
        // 没背景图时不做任何偏移，用户调多少就是多少
        assertEquals(0.8f, resolveCardAlpha(hasImage = false, cardAlpha = 0.8f), 0.001f)
    }

    @Test
    fun `有背景图时在基准值上再透一档`() {
        // 相对偏移语义：卡片的"透"要留给背景纹理，但不能回退到换常数
        val image = resolveCardAlpha(hasImage = true, cardAlpha = 0.85f)
        assertTrue("有背景图应更透，实际 $image", image < 0.85f)
        assertEquals(0.75f, image, 0.001f)
    }

    @Test
    fun `默认档落在原设计的两档上`() {
        // 用户没调过 / 老版本升级上来时，观感必须和加这个设置前一模一样
        assertEquals(GLASS_PLAIN_ALPHA, resolveCardAlpha(hasImage = false), 0.001f)
        assertEquals(GLASS_IMAGE_ALPHA, resolveCardAlpha(hasImage = true), 0.001f)
    }

    @Test
    fun `两档之差就是背景图偏移`() {
        // 钉住 IMAGE_ALPHA_DROP 与那两个常数的一致性：改了常数忘改偏移会红在这
        assertEquals(GLASS_IMAGE_ALPHA, GLASS_PLAIN_ALPHA - IMAGE_ALPHA_DROP, 0.001f)
    }

    @Test
    fun `再透也不会跌破可读性下限`() {
        // 项目要求对比度 ≥4.5:1；卡片透了背景就压不住正文
        val image = resolveCardAlpha(hasImage = true, cardAlpha = 0.05f)
        assertTrue("实际 $image 应 ≥ $GLASS_MIN_ALPHA", image >= GLASS_MIN_ALPHA)
        val plain = resolveCardAlpha(hasImage = false, cardAlpha = -1f)
        assertTrue("实际 $plain 应 ≥ $GLASS_MIN_ALPHA", plain >= GLASS_MIN_ALPHA)
    }

    @Test
    fun `越界输入被夹进合法区间`() {
        assertEquals(1f, resolveCardAlpha(false, 1.4f), 0.001f)
        // 喂 0 也得不到 0：可读性下限就读在这里，宁可画面和滑块对不上，也不能让正文读不清
        assertEquals(GLASS_MIN_ALPHA, resolveCardAlpha(false, 0f), 0.001f)
    }

    @Test
    fun `不指定基准值时两种背景都高于下限`() {
        assertTrue(
            "纯色档 ${resolveCardAlpha(false)} 应 ≥ $GLASS_MIN_ALPHA",
            resolveCardAlpha(false) >= GLASS_MIN_ALPHA,
        )
        assertTrue(
            "有图档 ${resolveCardAlpha(true)} 应 ≥ $GLASS_MIN_ALPHA",
            resolveCardAlpha(true) >= GLASS_MIN_ALPHA,
        )
    }

    // ---------- glassSurfaceColor 的 cardAlpha 参数 ----------

    @Test
    fun `cardAlpha 参数真的改变了卡片底色`() {
        // 传了值却没生效 = GroupCard 的 remember 少了 key，这条是它的防回归
        val def = glassSurfaceColor(null)
        val tuned = glassSurfaceColor(null, 0.55f)
        assertTrue("调过之后应更透：${tuned.alpha} 应 < ${def.alpha}", tuned.alpha < def.alpha)
        assertEquals(0.55f, tuned.alpha, 0.01f)
    }

    @Test
    fun `cardAlpha 对有背景图的卡片同样生效`() {
        val def = glassSurfaceColor(Color(0.4f, 0.5f, 0.6f))
        val tuned = glassSurfaceColor(Color(0.4f, 0.5f, 0.6f), GlassCard.MaxAlpha)
        assertTrue("调过之后应更实：${tuned.alpha} 应 > ${def.alpha}", tuned.alpha > def.alpha)
        // 拉满也到不了 1.0：有背景图时那 0.10 是留给背景透过的（见 IMAGE_ALPHA_DROP）
        assertEquals(0.9f, tuned.alpha, 0.01f)
    }

    @Test
    fun `cardAlpha 只改透明度不改色相`() {
        // 用户调的是"多透"不是"什么颜色"，色相必须还是跟着背景走
        val def = glassSurfaceColor(Color(0.2f, 0.3f, 0.8f))
        val tuned = glassSurfaceColor(Color(0.2f, 0.3f, 0.8f), 0.52f)
        assertEquals(def.blue, tuned.blue, 0.01f)
        assertEquals(def.red, tuned.red, 0.01f)
    }

    // ---------- 滑块取值域（Tokens.GlassCard）----------

    @Test
    fun `滑块下限不低于玻璃可读性下限`() {
        // 若滑块能拖到下限以下，那一段行程画面不动、只有数字在跳
        assertTrue(
            "GlassCard.MinAlpha(${GlassCard.MinAlpha}) 应 ≥ GLASS_MIN_ALPHA($GLASS_MIN_ALPHA)",
            GlassCard.MinAlpha >= GLASS_MIN_ALPHA,
        )
    }

    @Test
    fun `滑块上限能让卡片完全不透明`() {
        assertTrue("MaxAlpha 应能取到 1（否则用户没法关掉玻璃感）", GlassCard.MaxAlpha >= 1f)
    }

    @Test
    fun `默认基准值落在滑块取值域内`() {
        // BackgroundPrefs.DEFAULT_CARD_ALPHA 直接引用 GLASS_PLAIN_ALPHA，
        // 万一哪天有人把它改到取值域外，设置页显示的百分比就对不上实际观感
        val def = GLASS_PLAIN_ALPHA
        assertTrue("默认 $def 应在 ${GlassCard.MinAlpha}..${GlassCard.MaxAlpha} 内",
            def in GlassCard.MinAlpha..GlassCard.MaxAlpha)
    }
}

/**
 * 「暗背景下自动补足不透明度」的测试（M13 需求 9 的收尾）。
 *
 * 背景：滑块下限按老大要求放到 0.30（卡片更透）。纯色背景下这完全安全 ——
 * 白卡叠 RGB(221,224,241)，alpha 0.30 时正文对比度 14.08:1。
 * 但卡片是半透明的，**底下的东西会透上来**：用户选了张暗图、又把背景不透明度
 * 拉到 100%，卡片底下接近纯黑，此时副文本对比度掉到 1.07:1，字基本看不见。
 *
 * 所以 resolveCardAlpha 加了 backdrop 参数：只补到"刚好能读"，不多补 ——
 * 亮背景下用户拖到 0.30 仍然真的看到 0.30 的透感。
 */
class ReadableFloorTest {

    /** 页面底色 LightPageBackground #DDE0F1。 */
    private val pageBase = Color(red = 0.867f, green = 0.878f, blue = 0.945f, alpha = 1f)

    /** 纯黑背景图，且背景不透明度拉满 → 卡片底下就是它自己。 */
    private val pureBlack = Color(red = 0f, green = 0f, blue = 0f, alpha = 1f)

    // ---------- 亮背景：不该被干预 ----------

    @Test
    fun `纯色背景下旋钮完全自由`() {
        // 可读性下限必须是 0，否则老大要的"能调得更透"在纯色背景上就废了
        assertEquals(0f, readableFloorFor(pageBase), 0.001f)
    }

    @Test
    fun `纯色背景下用户调到下限就真是下限`() {
        val a = resolveCardAlpha(hasImage = false, cardAlpha = 0.30f, backdrop = pageBase)
        assertEquals(0.30f, a, 0.001f)
    }

    @Test
    fun `缺 backdrop 时按纯色背景处理`() {
        // null = 纯色背景（PageBackground 没下发时也不该把卡片糊掉）
        assertEquals(0.30f, resolveCardAlpha(false, 0.30f, null), 0.001f)
    }

    // ---------- 暗背景：必须补偿 ----------

    @Test
    fun `纯黑背景下可读性下限明显抬升`() {
        // 实测：白卡 0.30 叠纯黑 = RGB(76,76,76)，副文本对比度 1.07:1
        val floor = readableFloorFor(pureBlack)
        assertTrue("纯黑背景下的下限应 > 0.5，实际 $floor", floor > 0.5f)
    }

    @Test
    fun `纯黑背景下不透到低于可读下限`() {
        val a = resolveCardAlpha(hasImage = true, cardAlpha = 0.30f, backdrop = pureBlack)
        assertTrue("实际 $a 应 ≥ 可读下限", a >= readableFloorFor(pureBlack))
        assertTrue("补偿后不该把卡片变完全不透明，实际 $a", a < 1f)
    }

    @Test
    fun `暗背景下用户调高时仍以用户值为准`() {
        // 补偿是"只补到刚好能读"，不是"无视用户设定"：用户调 0.95 就该真的 0.95
        val a = resolveCardAlpha(hasImage = true, cardAlpha = 0.95f, backdrop = pureBlack)
        assertEquals(0.95f - IMAGE_ALPHA_DROP, a, 0.001f)
    }

    // ---------- 单调性 ----------

    @Test
    fun `背景越暗可读下限越高`() {
        var prev = -1f
        listOf(pageBase, Color(0x808080), Color(0x404040), pureBlack).forEach { bg ->
            val f = readableFloorFor(bg)
            assertTrue("背景变暗时下限应单调上升：$prev → $f", f >= prev)
            prev = f
        }
    }

    @Test
    fun `背景与卡片一样亮时下限是零`() {
        // 纯白底下不需要任何补偿
        assertEquals(0f, readableFloorFor(Color(0xFFFFFF)), 0.001f)
    }

    @Test
    fun `下限永远不超过一`() {
        // 背景比卡片还亮（理论上不该发生，但别让算法吐出 >1 把卡片变成纯不透明）
        listOf(Color(0xFFFFFF), Color(0xF0F0F0), pageBase).forEach {
            assertTrue(readableFloorFor(it) <= 1f)
        }
    }

    // ---------- blend：backdrop 是怎么算出来的 ----------

    @Test
    fun `背景不透明度为零时 backdrop 就是页面底色`() {
        assertEquals(pageBase, blend(pageBase, pureBlack, 0f))
    }

    @Test
    fun `背景不透明度拉满时 backdrop 就是背景色`() {
        assertEquals(pureBlack, blend(pageBase, pureBlack, 1f))
    }

    @Test
    fun `中间值是线性插值`() {
        // 期望 pageBase.red * 0.5；这里直接按公式算，不硬写 0.5 ——
        // pageBase.red 是 0.867 不是 0.5，插值结果自然不是 0.5。
        val expected = pageBase.red * 0.5f
        val r = blend(pageBase, pureBlack, 0.5f)
        assertEquals(expected, r.red, 0.01f)
    }

    @Test
    fun `blend 结果永远不透明`() {
        // backdrop 要拿来算明度，alpha 必须恒为 1，否则会被误当成半透明再叠一次
        assertEquals(1f, blend(pageBase, pureBlack, 0.3f).alpha, 0.001f)
    }
}

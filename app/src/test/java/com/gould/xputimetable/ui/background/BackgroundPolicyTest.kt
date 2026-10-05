/*
 * BackgroundPolicyTest.kt —— 自定义背景的两个纯函数（M12 需求二）
 *
 * 覆盖两处最容易悄悄错位的地方：
 *   1. shouldDrawBackground —— 作用范围判错的后果是"用户在便签页设的背景跑到了课表页"，
 *      属于功能级错误而不是样式偏差，必须钉死；
 *   2. decodeSampleSize —— 采样数算错的后果有两种极端：算小了直接 OOM，
 *      算大了背景图糊成马赛克，两个都不会崩，所以只能靠单测拦。
 */
package com.gould.xputimetable.ui.background

import com.gould.xputimetable.data.prefs.DEFAULT_BACKGROUND_OPACITY
import com.gould.xputimetable.data.prefs.DEFAULT_BACKGROUND_SETTINGS
import com.gould.xputimetable.data.prefs.DEFAULT_SCOPES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {

    // ---------- shouldDrawBackground ----------

    @Test
    fun `没设置范围时不画图`() {
        // null 与空集合语义一致：一个页面都不画（此时通常也没有图可画，等价于纯色背景）
        BackgroundScope.entries.forEach { page ->
            assertFalse(shouldDrawBackground(null, page))
            assertFalse(shouldDrawBackground(emptySet(), page))
        }
    }

    @Test
    fun `选全局时所有页面都画`() {
        BackgroundScope.entries.forEach { page ->
            assertTrue(shouldDrawBackground(setOf(BackgroundScope.GLOBAL), page))
        }
    }

    @Test
    fun `全局与某个档位同时勾着时两者都成立`() {
        // 全局是一条普通档位而不是"全选"：勾着它不改任何其他档位的判定
        val chosen = setOf(BackgroundScope.GLOBAL, BackgroundScope.NOTE)
        BackgroundScope.entries.forEach { page ->
            assertTrue(shouldDrawBackground(chosen, page))
        }
    }

    @Test
    fun `只选课表时只有课表页画`() {
        val chosen = setOf(BackgroundScope.TIMETABLE)
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.NOTE))
    }

    @Test
    fun `选我的时只有我的页画`() {
        val chosen = setOf(BackgroundScope.PROFILE)
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.NOTE))
    }

    @Test
    fun `选便签时只有便签页画`() {
        val chosen = setOf(BackgroundScope.NOTE)
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.NOTE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
    }

    @Test
    fun `多选时命中的每一项都画、其余不画`() {
        // 需求核心：多勾选必须真的叠加，不能退化成"只留最后一个"
        val chosen = setOf(BackgroundScope.TIMETABLE, BackgroundScope.PROFILE)
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.NOTE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.GLOBAL))
    }

    @Test
    fun `四项全勾等价于全局`() {
        val all = BackgroundScope.entries.toSet()
        BackgroundScope.entries.forEach { page ->
            assertTrue(shouldDrawBackground(all, page))
        }
    }

    @Test
    fun `判定只跟勾选有关与集合顺序无关`() {
        // 存进 DataStore 的是无序 Set：同一组勾选任何排列都必须得到同样的判定
        val a = setOf(BackgroundScope.NOTE, BackgroundScope.TIMETABLE)
        val b = setOf(BackgroundScope.TIMETABLE, BackgroundScope.NOTE)
        BackgroundScope.entries.forEach { page ->
            assertEquals(shouldDrawBackground(a, page), shouldDrawBackground(b, page))
        }
    }

    // ---------- decodeSampleSize ----------

    @Test
    fun `图比屏幕上小时不采样`() {
        assertEquals(1, decodeSampleSize(1080, 1920, 2400))
    }

    @Test
    fun `图正好等于上限也不采样`() {
        assertEquals(1, decodeSampleSize(2400, 1200, 2400))
    }

    @Test
    fun `长边刚超上限就减半`() {
        // 规则是「超过就减半」：3000 > 2400 → 1500 → 停
        assertEquals(2, decodeSampleSize(3000, 2000, 2400))
    }

    @Test
    fun `长边远超上限则多次减半`() {
        // 10000 → 5000 → 2500 → 1250（1250 ≤ 2400 停），共 3 次 = 8
        assertEquals(8, decodeSampleSize(10000, 8000, 2400))
    }

    @Test
    fun `长边超四倍则减到八分之一以内`() {
        // 8000 → 4000 → 2000，两次 = 4
        assertEquals(4, decodeSampleSize(8000, 6000, 2400))
    }

    @Test
    fun `只按最长边判断`() {
        // 宽 4000 高 1000：最长边是宽，采样只看它
        assertEquals(2, decodeSampleSize(4000, 1000, 2400))
    }

    @Test
    fun `采样数一定是二的幂`() {
        val actual = decodeSampleSize(40000, 30000, 600)
        assertTrue("采样数必须是 2 的幂，实际 $actual", actual > 0 && (actual and (actual - 1)) == 0)
    }

    @Test
    fun `非法输入回退为不采样`() {
        // 尺寸读不出来时宁可原图解码也不能返回 0（inSampleSize=0 会被当成 1，
        // 但负数会让 BitmapFactory 直接抛异常）
        assertEquals(1, decodeSampleSize(0, 100, 2400))
        assertEquals(1, decodeSampleSize(100, -1, 2400))
        assertEquals(1, decodeSampleSize(100, 100, 0))
        assertEquals(1, decodeSampleSize(100, 100, -5))
    }

    @Test
    fun `降采样后长边不超过上限`() {
        val sample = decodeSampleSize(4000, 3000, 2400)
        assertTrue(4000 / sample <= 2400)
    }

    @Test
    fun `手机原图不会被原样解码`() {
        // 这是 OOM 的真实来源：4000×3000 按 RGB_565 原样解码 = 24MB。
        // 官方 calculateInSampleSize 在目标 2400 时会返回 1（放过 24MB），
        // 本实现必须返回 2（2000×1500 = 6MB）。这条用例锁住这个取舍。
        assertEquals(2, decodeSampleSize(4000, 3000, 2400))
    }

    @Test
    fun `超大图不会一路采到 1`() {
        // 12000×9000（100MP 裁切 / 拼接图）：结果必须仍被显著缩小
        val sample = decodeSampleSize(12000, 9000, 2400)
        assertTrue("超大图采样数应 ≥ 8，实际 $sample", sample >= 8)
    }

    // ---------- 默认值 ----------

    @Test
    fun `默认设置是无图加低不透明度`() {
        assertEquals(null, DEFAULT_BACKGROUND_SETTINGS.imageUri)
        assertEquals(DEFAULT_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_SETTINGS.opacity, 0f)
        assertEquals(setOf(BackgroundScope.GLOBAL), DEFAULT_BACKGROUND_SETTINGS.scopes)
    }

    @Test
    fun `默认作用域是全局`() {
        // 升级上来什么都没存过的老用户必须读到"所有页面都有背景"，
        // 而不是空集合 —— 那样他的背景图会凭空消失且没人会报告
        assertTrue(DEFAULT_BACKGROUND_SETTINGS.scopes.contains(BackgroundScope.GLOBAL))
    }

    @Test
    fun `默认不透明度落在合理区间`() {
        // 0 全透（等于没设）、1 全遮（课表文字没法读），默认值必须夹在中间
        assertTrue(DEFAULT_BACKGROUND_OPACITY > 0f)
        assertTrue(DEFAULT_BACKGROUND_OPACITY < 1f)
    }
}

/**
 * `scopeSummary` 的单测（「我的」页那行摘要）。
 *
 * 单独一个类而不是并进上面：上面测的是"画不画"，这里测的是"怎么描述"，
 * 两者坏掉时的表现完全不同（前者功能失效，后者文案自相矛盾）。
 */
class ScopeSummaryTest {

    @Test
    fun `没勾任何项时是未选择`() {
        // 不能写成"全部"：用户确实一项都没勾，写"全部"会让他以为出了 bug
        assertEquals("未选择", scopeSummary(emptySet()))
    }

    @Test
    fun `勾了全局时只说全局`() {
        assertEquals("全局", scopeSummary(setOf(BackgroundScope.GLOBAL)))
    }

    @Test
    fun `勾了全局加其它时仍然只说全局`() {
        // 全局已经覆盖其余档，罗列出来会变成"全局、课表"这种自相矛盾的话
        assertEquals(
            "全局",
            scopeSummary(setOf(BackgroundScope.GLOBAL, BackgroundScope.TIMETABLE, BackgroundScope.NOTE)),
        )
    }

    @Test
    fun `多选时按固定顺序列出`() {
        // 顺序必须稳定：否则每次进页面摘要都不一样，用户会以为是随机的
        assertEquals(
            "课表、我的、便签",
            scopeSummary(setOf(BackgroundScope.NOTE, BackgroundScope.PROFILE, BackgroundScope.TIMETABLE)),
        )
    }

    @Test
    fun `顺序与点击先后无关`() {
        val a = scopeSummary(setOf(BackgroundScope.TIMETABLE, BackgroundScope.NOTE))
        val b = scopeSummary(setOf(BackgroundScope.NOTE, BackgroundScope.TIMETABLE))
        assertEquals(a, b)
    }

    @Test
    fun `单项时就是那一项`() {
        assertEquals("便签", scopeSummary(setOf(BackgroundScope.NOTE)))
    }

    @Test
    fun `顺序按枚举声明而非字母序`() {
        // 课表/我的/便签 若按字母排会是 便签/我的/课表，与界面里的排列不一致
        assertEquals(
            "课表、我的",
            scopeSummary(setOf(BackgroundScope.PROFILE, BackgroundScope.TIMETABLE)),
        )
    }
}

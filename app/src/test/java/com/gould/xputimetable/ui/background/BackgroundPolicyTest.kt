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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {

    // ---------- shouldDrawBackground ----------

    @Test
    fun `没设置范围时不画图`() {
        // null = 从未设置过。语义上等价于"没有背景图"，任何页面都不该画
        BackgroundScope.entries.forEach { page ->
            assertFalse(shouldDrawBackground(null, page))
        }
    }

    @Test
    fun `选全局时所有页面都画`() {
        BackgroundScope.entries.forEach { page ->
            assertTrue(shouldDrawBackground(BackgroundScope.GLOBAL, page))
        }
    }

    @Test
    fun `选课表时只有课表页画`() {
        val chosen = BackgroundScope.TIMETABLE
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.NOTE))
    }

    @Test
    fun `选我的时只有我的页画`() {
        val chosen = BackgroundScope.PROFILE
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.NOTE))
    }

    @Test
    fun `选便签时只有便签页画`() {
        val chosen = BackgroundScope.NOTE
        assertTrue(shouldDrawBackground(chosen, BackgroundScope.NOTE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.TIMETABLE))
        assertFalse(shouldDrawBackground(chosen, BackgroundScope.PROFILE))
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
        assertEquals(BackgroundScope.GLOBAL, DEFAULT_BACKGROUND_SETTINGS.scope)
    }

    @Test
    fun `默认不透明度落在合理区间`() {
        // 0 全透（等于没设）、1 全遮（课表文字没法读），默认值必须夹在中间
        assertTrue(DEFAULT_BACKGROUND_OPACITY > 0f)
        assertTrue(DEFAULT_BACKGROUND_OPACITY < 1f)
    }
}

/*
 * CoursePaletteArgbTest.kt —— color_tag 同时装「色板索引」与「ARGB 真彩」的兼容单测
 *
 * 背景：HSV 取色器存出来的颜色要直接落进 `courses.color_tag`（Int 列），老课程的 0..11
 * 也还得原样显示。靠 >= 100 的阈值区分两种语义——这条边界一旦写反，表现是
 * 「老课全变白 / 新设的课颜色不对」，而且**不会编译报错**，只能靠测试兜住。
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 取色器选出的一个非色板色（对应色板索引 0 的蓝以外的红）。 */
private val ARGB_SAMPLE = 0xFFB71C1C.toInt()

class CoursePaletteArgbTest {

    @Test
    fun `阈值边界正确`() {
        assertTrue(!CoursePalette.isArgbTag(0))
        assertTrue(!CoursePalette.isArgbTag(CoursePalette.size - 1))
        assertTrue(!CoursePalette.isArgbTag(CoursePalette.ARGB_THRESHOLD - 1))
        assertTrue(CoursePalette.isArgbTag(CoursePalette.ARGB_THRESHOLD))
        assertTrue(CoursePalette.isArgbTag(ARGB_SAMPLE))
    }

    @Test
    fun `老数据的离散色不受影响`() {
        // 12 个色板索引必须还是原来那 12 个颜色：色值被改会表现为"老课程换色"
        assertEquals(Color(0xFF1E88E5), CoursePalette.base(0)) // 0 号还是蓝
        val distinct = (0 until CoursePalette.size).map { CoursePalette.resolve(it) }
        assertEquals(CoursePalette.size, distinct.distinct().size) // 互不重色
        for (tag in 0 until CoursePalette.size) {
            assertEquals("色板第 $tag 色被取色器分支改掉了", distinct[tag], CoursePalette.base(tag))
        }
    }

    @Test
    fun `离散索引不会被误判成ARGB`() {
        // 关键回归：0..11 必须走索引分支。若分支写反，resolve 会返回 Color(tag)——
        // 那是"索引号当色值"，对 0..11 来说全是近黑色，一眼看得出不对（老课会变成黑块）。
        for (tag in 0 until CoursePalette.size) {
            assertNotEquals("索引 $tag 被当成 ARGB 色值（会变成近黑）", Color(tag), CoursePalette.resolve(tag))
        }
    }

    @Test
    fun `满透明的ARGB在Int里是负数也不能被当成索引`() {
        // 真踩过的坑（2026-10-02）：0xFFB71C1C 写成 Int 是 **负数** -11678372。
        // 若 isArgbTag 直接比 `colorTag >= 100`，取色器存的任意满透明色都会判成「色板索引」
        // → resolve 取模后恒为 0 号蓝 → 用户自设的颜色全变蓝，而且不编译报错。
        val negativeRed = 0xFFB71C1C.toInt()
        assertTrue("负数 ARGB 被判成索引，自取色会全变蓝", CoursePalette.isArgbTag(negativeRed))
        assertTrue("纯黑 ARGB 也是负数", CoursePalette.isArgbTag(0xFF000000.toInt()))
        assertEquals(Color(negativeRed), CoursePalette.base(negativeRed))
        assertNotEquals(CoursePalette.base(0), CoursePalette.base(negativeRed))
    }

    @Test
    fun `ARGB 值原样还原且不与索引撞色`() {
        assertTrue(CoursePalette.isArgbTag(ARGB_SAMPLE))
        assertEquals(Color(ARGB_SAMPLE), CoursePalette.base(ARGB_SAMPLE))
        assertEquals(Color(ARGB_SAMPLE), CoursePalette.resolve(ARGB_SAMPLE))
        assertNotEquals(CoursePalette.base(0), CoursePalette.base(ARGB_SAMPLE))
    }

    @Test
    fun `卡片背景与文字色沿用同一个还原口径`() {
        assertEquals(
            CoursePalette.base(ARGB_SAMPLE).copy(alpha = 0.16f),
            CoursePalette.container(ARGB_SAMPLE, dark = false),
        )
        assertEquals(CoursePalette.base(ARGB_SAMPLE), CoursePalette.onContainer(ARGB_SAMPLE, dark = false))
        assertEquals(
            CoursePalette.base(ARGB_SAMPLE).copy(alpha = 0.32f),
            CoursePalette.container(ARGB_SAMPLE, dark = true),
        )
    }
}

/*
 * ColorTagTest.kt —— color_tag 双语义判据（M11-第三批·取色器）
 *
 * 这一条判据错了不会编译报错，只会「用户自设的颜色全变蓝」或「自取色存不进库」，
 * 属于必须靠测试钉死的类型。
 */
package com.gould.xputimetable.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTagTest {

    @Test
    fun `色板索引一律判为索引`() {
        assertFalse(isArgbColorTag(0))
        assertFalse(isArgbColorTag(11))
        assertFalse(isArgbColorTag(COLOR_TAG_ARG_THRESHOLD - 1))
        // 负数（-1 = 0xFFFFFFFF）无符号值也很大，一律走 ARGB 分支。
        // 让它走索引分支反而危险：resolve 会取模成 11，脏数据悄悄变成另一个颜色还看不出来。
        assertTrue(isArgbColorTag(-1))
    }

    @Test
    fun `满透明的ARGB是负数也不能判成索引`() {
        // 0xFFB71C1C 作 Int 是 -11678372；判据若直接 >= 阈值，取色器存的色会被全部退回索引
        assertTrue(isArgbColorTag(0xFFB71C1C.toInt()))
        assertTrue(isArgbColorTag(0xFF000000.toInt())) // 纯黑也是负数
        assertTrue(isArgbColorTag(0xFFFFFFFF.toInt())) // 纯白
    }

    @Test
    fun `边界恰好落在阈值上`() {
        assertTrue(isArgbColorTag(COLOR_TAG_ARG_THRESHOLD))
        assertFalse(isArgbColorTag(COLOR_TAG_ARG_THRESHOLD - 1))
    }

    @Test
    fun `isValidColorTag放得下负数ARGB也挡得住脏值`() {
        // 取色器自取的绿：0xFF00C853 作 Int 是 -6551261，必须放行，
        // 否则 setColorTag 的 `tag < 0` 守卫会静默丢掉用户刚选的颜色
        assertTrue(isValidColorTag(0xFF00C853.toInt()))
        assertTrue(isValidColorTag(0xFFB71C1C.toInt()))
        // 色板索引
        assertTrue(isValidColorTag(0))
        assertTrue(isValidColorTag(COLOR_TAG_INDEX_MAX))
        // 阈值与索引之间的空洞（早期遗留的非法中间值）
        assertFalse(isValidColorTag(50))
        assertFalse(isValidColorTag(COLOR_TAG_ARG_THRESHOLD - 1))
    }
}

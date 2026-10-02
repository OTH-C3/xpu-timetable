/*
 * ColorPickerMathTest.kt —— 取色器换算单测（M11-第三批）
 *
 * 重点不是"三原色对不对"（那是常识），而是三条容易被改坏的性质：
 *   ① 色相归一化：拖到盘子边缘/色相条两端不能算出黑块或越界；
 *   ② HSV ⇄ ARGB 往返：回填盘面时位置必须回得去（量化误差内），否则"打开取色器色块跳了"；
 *   ③ hex 手输的容错：解析失败必须返回 null，不能抛（输入框每敲一个字都走一次）。
 */
package com.gould.xputimetable.ui.courseedit

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPickerMathTest {

    private val alpha = 0xFF000000.toInt()

    // ---------- ① 基准色 ----------

    @Test
    fun `纯色相基色`() {
        assertEquals(alpha or 0x00FF0000, ColorPickerMath.hsvToArgb(0f, 1f, 1f))      // 红
        assertEquals(alpha or 0x0000FF00, ColorPickerMath.hsvToArgb(120f, 1f, 1f))    // 绿
        assertEquals(alpha or 0x000000FF, ColorPickerMath.hsvToArgb(240f, 1f, 1f))    // 蓝
    }

    @Test
    fun `零饱和是灰阶零明度是黑`() {
        assertEquals(alpha or 0x00FFFFFF, ColorPickerMath.hsvToArgb(30f, 0f, 1f))      // 白
        // 0.5 明度 → 0.5*255+0.5 = 128，中灰是 0x808080 不是全白（这里踩过一次：把 128 写成 255）
        assertEquals(alpha or 0x00808080, ColorPickerMath.hsvToArgb(30f, 0f, 0.5f))
        assertEquals(alpha, ColorPickerMath.hsvToArgb(30f, 1f, 0f))                    // 黑
    }

    @Test
    fun `不透明：任何输入都带满 alpha`() {
        // 用 while 而不是 `for (h in 0f..360f step 30f)`：
        // 本项目的 Kotlin 版本对浮点 range 的 step 解析不到（Unresolved reference 'step'），
        // 而且 for 头一出错，循环体内的元素会被连带当成 error type，报出一堆莫名其妙的错。
        var h = 0f
        while (h <= 360f) {
            val argb = ColorPickerMath.hsvToArgb(h, 1f, 1f)
            assertTrue("h=$h 丢了 alpha", argb and 0xFF000000.toInt() == alpha)
            h += 30f
        }
    }

    // ---------- ② 色相归一化 ----------

    @Test
    fun `色相越界折算回圈`() {
        assertEquals(
            ColorPickerMath.hsvToArgb(0f, 1f, 1f),
            ColorPickerMath.hsvToArgb(360f, 1f, 1f),
        )
        assertEquals(
            ColorPickerMath.hsvToArgb(330f, 1f, 1f),
            ColorPickerMath.hsvToArgb(-30f, 1f, 1f),
        )
        assertEquals(
            ColorPickerMath.hsvToArgb(30f, 1f, 1f),
            ColorPickerMath.hsvToArgb(-330f, 1f, 1f),
        )
    }

    // ---------- ③ HSV ⇄ ARGB 往返 ----------

    @Test
    fun `往返还原色相与明度在量化误差内`() {
        var h = 0f
        while (h <= 359f) {
            var s = 0.2f
            while (s <= 1.001f) {
                var v = 0.2f
                while (v <= 1.001f) {
                    val back = ColorPickerMath.argbToHsv(ColorPickerMath.hsvToArgb(h, s, v))
                    // 色相容差 5°：8bit 量化后，低饱和/低明度区 (delta 只有 10/255) 反算色相天然抖 ±3~4°，
                    // 卡 2° 会把「正常量化抖动」误判成 bug（h=15 s=0.2 v=0.2 实测漂 3°）。
                    assertTrue("h=$h s=$s v=$v 色相漂了", abs(back[0] - h) <= 5f)
                    assertTrue("h=$h s=$s v=$v 饱和度漂了", abs(back[1] - s) <= 0.05f)
                    assertTrue("h=$h s=$s v=$v 明度漂了", abs(back[2] - v) <= 0.05f)
                    v += 0.2f
                }
                s += 0.2f
            }
            h += 15f
        }
    }

    @Test
    fun `往返不会丢编码`() {
        // 取色盘每帧都在 hsvToArgb / argbToHsv 之间跳，掉一位就是"颜色变了一点"
        val argb = ColorPickerMath.hsvToArgb(210f, 0.65f, 0.8f)
        val round = ColorPickerMath.argbToHsv(argb)
        assertEquals(argb and 0x00FFFFFF, ColorPickerMath.hsvToArgb(round[0], round[1], round[2]) and 0x00FFFFFF)
    }

    // ---------- ④ HEX ----------

    @Test
    fun `格式化和解析可往返`() {
        var h = 0f
        while (h <= 360f) {
            val argb = ColorPickerMath.hsvToArgb(h, 0.7f, 0.75f)
            assertEquals(argb, ColorPickerMath.parseHex(ColorPickerMath.formatHex(argb)))
            h += 45f
        }
    }

    @Test
    fun `带不带井号都能解析`() {
        assertEquals(0xFF112233.toInt(), ColorPickerMath.parseHex("#112233"))
        assertEquals(0xFF112233.toInt(), ColorPickerMath.parseHex("112233"))
    }

    @Test
    fun `解析失败返回空而不是抛`() {
        assertNull(ColorPickerMath.parseHex(""))           // 空（用户刚点进输入框）
        assertNull(ColorPickerMath.parseHex("#12"))        // 位数不够
        assertNull(ColorPickerMath.parseHex("#12345G"))    // 非法字符
        assertNull(ColorPickerMath.parseHex("#1122333"))   // 多一位
    }

}

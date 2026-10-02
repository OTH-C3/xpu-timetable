/*
 * ColorPickerMath.kt —— 取色器的 HSV / HEX 换算（纯函数，M11-第三批）
 *
 * 作用：把「色相条上的一个位置 + S×V 平面上的一个位置」算成一个不透明 ARGB int，
 * 以及把用户手输的 #RRGGBB 解析成色值。
 *
 * 为什么不直接用 androidx.compose.ui.graphics.Color 的 HSVToColor：
 *   1. 那是 Android 平台实现，单测要靠设备或 Robolectric；这里只要纯 Kotlin；
 *   2. 取色盘每帧要算上百个像素色，手写一个不分配对象的版本更省；
 *   3. 换算是对称的（我 round-trip 一遍必须还原），这种性质只有纯函数好测。
 *
 * 约定：hue 用 0..360（浮点），saturation / value 用 0..1，返回的是 **不透明 ARGB int**
 * （courses.color_tag 是 Int 列，直接存，见 CoursePalette 的兼容说明）。
 */
package com.gould.xputimetable.ui.courseedit

/**
 * HSV ⇄ ARGB 与 HEX 互转。
 *
 * 全部方法无状态、无平台依赖，便于单测覆盖"拖到角上是什么颜色""手输错串要不要炸"。
 */
object ColorPickerMath {

    /** 不透明的 ARGB 遮罩（避免 0 被当成全透明丢掉）。 */
    private const val ALPHA = 0xFF000000.toInt()

    /** 把 0..360 的色相折算回区间内（负数、360 以上都不许溢出）。 */
    private fun normalizeHue(h: Float): Float = when {
        h >= 360f -> h % 360f
        h < 0f -> h % 360f + 360f
        else -> h
    }

    private fun channel(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    /**
     * HSV → ARGB int。
     *
     * 标准 HSV→RGB 六分区实现：先按色相落在哪个 60° 扇区决定三个通道里谁取 max、谁取中间值，
     * 再用饱和度往白色方向拉、明度整体缩放。
     */
    fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        if (s <= 0f) return ALPHA or (channel(v) shl 16) or (channel(v) shl 8) or channel(v)
        val sector = normalizeHue(h) / 60f
        val i = sector.toInt()
        val f = sector - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return ALPHA or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /**
     * ARGB int → HSV（返回 `floatArrayOf(hue, saturation, value)`，与平台 API 同序同值域）。
     * 用于打开取色器时把已有课程色回填到盘面位置。
     */
    fun argbToHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta <= 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf(
            normalizeHue(hue),
            if (max <= 0f) 0f else delta / max,
            max,
        )
    }

    /** ARGB → `#RRGGBB`（大写，不含 alpha：存储与展示都不需要把透明度暴露给用户）。 */
    fun formatHex(argb: Int): String = "#%06X".format(argb and 0x00FFFFFF)

    /**
     * 手输 `#RRGGBB` → ARGB int；**解析不出来返回 null 而不是抛异常** ——
     * 输入框每敲一个字符都会走一次，抛异常等于敲错一个字就闪退。
     */
    fun parseHex(text: String): Int? {
        val trimmed = text.trim().trimStart('#')
        if (trimmed.length != 6) return null
        val value = trimmed.toLongOrNull(16) ?: return null
        return ALPHA or value.toInt()
    }
}

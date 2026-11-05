/*
 * HslColor.kt —— 标准 RGB↔HSL 换算（纯 Kotlin）
 *
 * 从 GlassPalette.kt 拆出来的原因：它是一组**教科书公式**，与"玻璃该长什么样"
 * 这个设计决策毫无关系。混在一起时，想调 SAT_CAP 之类的观感参数要翻过一大段
 * 与本题无关的三角函数推导。
 *
 * 注意 为什么不用 `android.graphics.Color.colorToHSL` / `HSVToColor`：
 *   那是 Android framework 的 API，在**纯 JVM 单元测试**里会抛
 *   "Method ... not mocked"（项目无 Robolectric）。而这几组函数正是要被单测精确
 *   断言的（色相是否保得住、饱和度上限是否生效），必须能脱离设备跑。
 */

package com.gould.xputimetable.ui.background

import androidx.compose.ui.graphics.Color

// ---------- 下面三个是标准 RGB↔HSL 换算，纯 Kotlin 实现 ----------
//
// 注意 为什么不用 `android.graphics.Color.colorToHSL` / `HSVToColor`：
// 它们是 framework 方法，纯 JVM 单元测试里会抛 "not mocked"（项目明确不引 Robolectric，
// 范式是"纯函数 + 单测"）。自己写换算换来的是这条规则能被 10+ 个单测钉住。

/** 色相角，0~360。 */
internal fun rgbToHue(r: Float, g: Float, b: Float): Float {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta == 0f) return 0f
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

/** 饱和度 0~1。 */
internal fun rgbToSaturation(r: Float, g: Float, b: Float): Float {
    val max = maxOf(r, g, b)
    if (max == 0f) return 0f
    val min = minOf(r, g, b)
    return (max - min) / max
}

/** 明度 0~1。 */
internal fun rgbToLightness(r: Float, g: Float, b: Float): Float =
    (maxOf(r, g, b) + minOf(r, g, b)) / 2f

/** HSL（h 度 / s,l 0~1）→ Compose Color（alpha 置 1，透明度由调用方覆盖）。 */
internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    // m 是把 [0,c] 区间抬到当前明度所需的加法补偿
    val m = l - c / 2f
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/**
 * 玻璃边缘的高光色（描边用）。
 *
 * 玻璃之所以"像玻璃"，很大程度来自边缘那道**从上到下由亮转暗**的高光。
 * 用纯色描边会显得"贴纸"，用渐变才像"有厚度"。
 */

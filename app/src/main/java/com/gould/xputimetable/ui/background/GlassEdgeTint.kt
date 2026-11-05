/*
 * GlassEdgeTint.kt —— 玻璃描边向背景主色偏移的取色（纯函数，可单测）
 *
 * 从 GlassPalette.kt 拆出来有两个原因：
 *   1. GlassPalette 已顶到 300 行门禁；
 *   2. 它与"玻璃表面色怎么算"是**两件事** —— 那件关乎卡片本身多大面积多透明，
 *      这件只关乎一条 1dp 描边染多少背景色。混在一起时改描边要去翻大段可读性推导。
 */

package com.gould.xputimetable.ui.background

import androidx.compose.ui.graphics.Color

/**
 * 把玻璃自身的白/黑**朝背景主色挪一点**（M14）。
 *
 * 玻璃的高光与暗面本来是纯白/纯黑 —— 那只在"背景是灰色"时成立。
 * 背景一旦有色（例如淡蓝），纯白高光会与它割裂，看上去像"贴上去的白玻璃"，
 * 这正是用户反馈的「玻璃只有白色光影，和自定义背景不融洽」。
 *
 * 混合强度按**背景饱和度**折算：背景越艳混得越多，背景接近灰白时几乎不动，
 * 所以纯色背景下的观感与旧版完全一致（向后兼容，不给存量用户变脸）。
 *
 * @param target 背景主色；null = 没有背景图，直接返回原色
 * @param amount 最大混合比例（实际会再乘背景饱和度）
 */
internal fun Color.blendToward(target: Color?, amount: Float = 0.35f): Color {
    if (target == null) return this
    val w = (rgbToSaturation(target.red, target.green, target.blue) * amount)
        .coerceIn(0f, 1f)
    return Color(
        red = red * (1f - w) + target.red * w,
        green = green * (1f - w) + target.green * w,
        blue = blue * (1f - w) + target.blue * w,
        alpha = alpha,
    )
}

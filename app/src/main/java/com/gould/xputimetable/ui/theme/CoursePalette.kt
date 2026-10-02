/*
 * CoursePalette.kt —— 课程固定色板（UIUX 文档 §4.4 的落地）
 *
 * 设计依据：课程颜色是「身份」，必须固定 12 色且不跟随动态取色——用户对「高数=蓝色」有肌肉记忆，
 * 颜色随壁纸漂移会破坏这种记忆。color_tag 存的是本色板的索引（0..11），换主题不失真。
 *
 * 阶段说明：UIUX 文档把精确色值定位为「Phase 2 冻结项」。当前先用 12 个色相环均匀分布的基础色
 * 作为过渡实现（浅色 = 低透明度色块 + 深色文字；深色 = 较高透明度色块 + 提亮文字）。
 * Phase 2 设计师出 design-tokens 后，只需替换本文件的色值，界面代码不动。
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.gould.xputimetable.domain.model.COLOR_TAG_ARG_THRESHOLD
import com.gould.xputimetable.domain.model.isArgbColorTag

object CoursePalette {

    /** 12 个基础色（色相环均匀分布，覆盖常见课表配色且互相可区分）。 */
    private val bases = listOf(
        Color(0xFF1E88E5), // 0  蓝
        Color(0xFF43A047), // 1  绿
        Color(0xFFF4511E), // 2  橙红
        Color(0xFF8E24AA), // 3  紫
        Color(0xFF00897B), // 4  青
        Color(0xFF6D4C41), // 5  棕
        Color(0xFF3949AB), // 6  靛
        Color(0xFFC0CA33).darken(), // 7 黄绿（压暗保证文字可读）
        Color(0xFFD81B60), // 8  玫红
        Color(0xFF546E7A), // 9  蓝灰
        Color(0xFF00ACC1), // 10 天蓝
        Color(0xFF7CB342), // 11 浅绿
    )

    private fun Color.darken(): Color = lerp(this, Color.Black, 0.25f)

    /** 色板大小（越界索引自动取模，防御脏数据）。 */
    val size: Int get() = bases.size

    /**
     * `courses.color_tag` 这一列同时装两种历史值：**< 阈值 = 老数据的色板索引（0..11）**，
     * **>= 阈值 = 新用户自取色的 ARGB int**。这样取色器存出来的真彩色能直接落进同一列，
     * 老库不用迁移、老课程的固定色也不会变。
     *
     * 阈值本体在 `domain.model.ColorTag`（与数据层共用同一判据，避免两处阈值漂移）；
     * ⚠️ 判 ARGB 必须看成无符号 32 位：满不透明的 0xFFRRGGBB 写成 Int 是**负数**，
     * 直接 `>= 阈值` 会把所有自取色判成索引 → 课程全变 0 号蓝（2026-10-02 实踩）。
     */
    const val ARGB_THRESHOLD: Int = COLOR_TAG_ARG_THRESHOLD

    /** 这个存储值是离散色板索引还是 ARGB 真彩色？（委托 domain，保证数据层与 UI 层判据一致） */
    fun isArgbTag(colorTag: Int): Boolean = isArgbColorTag(colorTag)

    /** 把存储值还原成本课程身份用的纯色。 */
    fun resolve(colorTag: Int): Color =
        if (isArgbTag(colorTag)) Color(colorTag) else bases[wrapIndex(colorTag, bases.size)]

    /** 基础色（课程身份色）。 */
    fun base(colorTag: Int): Color = resolve(colorTag)

    /** 卡片背景：浅色主题低透明度、深色主题较高透明度。 */
    fun container(colorTag: Int, dark: Boolean): Color =
        resolve(colorTag).copy(alpha = if (dark) 0.32f else 0.16f)

    /** 卡片内文字色：浅色用基础色本身，深色提亮保证对比度。 */
    fun onContainer(colorTag: Int, dark: Boolean): Color =
        if (dark) lerp(resolve(colorTag), Color.White, 0.45f) else resolve(colorTag)

    /** 索引取模（兼容负数：脏数据或未来字段回退时不会越界崩溃）。 */
    private fun wrapIndex(index: Int, size: Int): Int = ((index % size) + size) % size
}

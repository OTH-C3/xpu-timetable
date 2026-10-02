/*
 * ColorTag.kt —— `courses.color_tag` 这一个 Int 列的取值判据（domain 层，data 与 ui 共用）
 *
 * 背景：课程颜色早期只装"色板索引"（0..11），取色器上线后同一列还要装 HSV 自取的 ARGB，
 * 于是出现了两种语义共存。判据必须只有一处（否则改阈值时必漏一处），所以下沉到 domain。
 *
 * ⚠️ 关键坑：满不透明的 ARGB（0xFFRRGGBB）写成 Int 是**负数**（0xFFB71C1C = -11678372）。
 * 所以判 ARGB 一定要看成无符号 32 位（先 and 0x7FFFFFFF），直接比 `>= 100`
 * 会把所有自取色判成色板索引 → 用户选的颜色全退化成 0 号蓝（2026-10-02 实踩）。
 */
package com.gould.xputimetable.domain.model

/** 色板最多 12 色，离 100 还差得远，取 100 做阈值不会与索引撞车。 */
const val COLOR_TAG_ARG_THRESHOLD = 100

/**
 * 判断一个 color_tag 存的是「离散色板索引」还是「ARGB 真彩色」。
 *
 * 索引分支：0..11（老数据、教务导入）；ARGB 分支：任意满不透明的 0xFFRRGGBB。
 */
fun isArgbColorTag(colorTag: Int): Boolean =
    (colorTag and 0x7FFFFFFF) >= COLOR_TAG_ARG_THRESHOLD

/** 色板索引上限（12 色，下标 0..11）。 */
const val COLOR_TAG_INDEX_MAX = 11

/**
 * color_tag 是不是一个能落库的合法值。
 *
 * ⚠️ 这里不能写 `tag >= 0`：**满不透明的 ARGB 在 Int 里是负数**（见文件头），
 * 写了会把取色器自取的每一个颜色都挡在门外（2026-10-02 实踩：面板能关、颜色不变）。
 * 数据层的入库校验和表单的赋值校验都必须走这个判据，别各写各的。
 */
fun isValidColorTag(colorTag: Int): Boolean =
    colorTag in 0..COLOR_TAG_INDEX_MAX || isArgbColorTag(colorTag)

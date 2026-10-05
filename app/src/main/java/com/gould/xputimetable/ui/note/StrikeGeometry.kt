/*
 * StrikeGeometry.kt —— 划线几何（需求 2「从左到右划掉」的纯计算部分）
 *
 * 单独拆一个文件而不是塞在 NoteRows.kt 里，有两个理由：
 *   1. 算式里踩过一个很深的坑：宽度**不能**取 Text 的布局宽度（详见 [strikeSegments]），
 *      而这个坑在真机上表现为"线画到屏幕右边缘"，肉眼能看出、单元测却测不到
 *      （因为它取决于 Compose 的排版结果）。把算式提成纯函数后，
 *      至少"逐行取行宽、乘进度、空行跳过"这几条规则能被单测钉住。
 *   2. UI 层只负责把 TextLayoutResult 翻译成 LineBox，其余都在这里，便于复核。
 */
package com.gould.xputimetable.ui.note

/** 一行文字在文本布局里的实际占位（单位：Compose 像素，左上为原点）。 */
internal data class LineBox(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
)

/** 一段要画出去的划线。 */
internal data class StrikeSegment(
    val x0: Float,
    val x1: Float,
    val y: Float,
)

/**
 * 划线在行内的纵向位置（0 = 行顶，1 = 行底）。
 *
 * = 0.5（行的几何中线）。原先取 0.42，理由写的是"视觉居中略高于几何中线"——
 * 那是个**凭感觉的猜测，实测错了**：老大反馈"划线从文字的中间划，现在太高了"。
 * 0.42 的线落在行盒上沿附近，而 M3 的 bodyLarge 行高大于字号（多出来的 leading
 * 主要加在上面），于是线实际压在字符上沿甚至穿过字符顶部。
 * 现在老老实实取几何中线 0.5。
 */
internal const val STRIKE_Y_RATIO = 0.5f

/**
 * 由「每行占位 + 动画进度」算出要画的线段列表。
 *
 * ## 宽度必须来自 getLineLeft / getLineRight，不能用 size.width
 *
 * 待办条目的文字带 `Modifier.weight(1f)`，会**铺满整行剩余宽度**。而排版结果里的
 * `size.width`（以及 `getLineRight(0)` 之外的整段宽度）同样是这个布局宽度，不是文字实测宽度 ——
 * 早期版本就因为用了 `size.width`，线一路拖到卡片右边缘，视觉上像"划掉了一整行空白"
 * （2026-10-04 真机实测，英文条目上尤其明显）。`getLineRight(i) - getLineLeft(i)`
 * 才是第 i 行真正被文字占掉的横向区间。
 *
 * ## 逐行画，不合并
 *
 * 多行文本每行各画一条。只画第一条是错的：下半截文字没被划掉，看起来像漏了一半。
 * 每行用自己那一行的 left/width，短行不会被长行的宽度带偏。
 *
 * @param boxes    每行的占位，通常由 TextLayoutResult 逐行换算
 * @param progress 动画进度 0→1，1 表示完全划掉
 */
internal fun strikeSegments(boxes: List<LineBox>, progress: Float): List<StrikeSegment> {
    if (progress <= 0f) return emptyList()
    return boxes.mapNotNull { box ->
        val width = box.right - box.left
        // 空行（宽度为 0 或负）不画，否则会留下一小段脏线
        if (width <= 0f) return@mapNotNull null
        StrikeSegment(
            x0 = box.left,
            x1 = box.left + width * progress,
            y = box.top + (box.bottom - box.top) * STRIKE_Y_RATIO,
        )
    }
}

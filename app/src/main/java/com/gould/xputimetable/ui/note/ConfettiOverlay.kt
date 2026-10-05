/*
 * ConfettiOverlay.kt —— 彩带绘制层（需求：清单全完成后从方框后面蹦出彩带）
 *
 * 挂在整组勾选方框的**后面**（z 序在卡片之下），所以彩带看起来是从方框里"蹦出来"，
 * 而不是盖在方框上面 —— 这是"从方框后面蹦出"这句话的关键。
 *
 * 实现要点：
 *   1. 零新依赖：全部用 Canvas 手绘。项目约束是不为小功能引库。
 *   2. 动画驱动用 `withFrameNanos` 读真实帧时钟，不用固定步长累加 ——
 *      固定步长在掉帧时会"越走越快/越走越慢"，真机上 60/120Hz 表现会不一致。
 *   3. 播完自动把自己从 Composition 里摘掉（`visible=false` → 不绘制），
 *      避免一个永久存在的 Canvas 在每帧都跑 onDraw。
 *   4. 彩带画成**旋转的矩形**（绕自身中心自旋），不是圆点 —— 圆点看不出自旋，
 *      矩形翻转时宽窄变化才有"纸片翻飞"的质感。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.material3.MaterialTheme
import com.gould.xputimetable.ui.theme.Note

/**
 * 彩带调色板。
 *
 * 取自 Material 主题色而非写死：项目规矩是颜色一律走 `colorScheme`，
 * 且跟随明暗主题。这里刻意**不**用「唯一强调色 = primary」那条规则 ——
 * 那条规则约束的是"常态强调"，庆祝彩带是唯一允许彩色重彩的场合（一次性、有明确语义）。
 * 用 primary / secondary / tertiary / error 之外补两个稳定色，保证 5 片以上颜色都能区分。
 */
@Composable
private fun confettiPalette(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        listOf(
            cs.primary,
            cs.secondary,
            cs.tertiary,
            cs.error,
            Color(0xFF4CAF50),   // 绿：完成/成功
            Color(0xFFFFB300),   // 琥珀：庆祝
        )
    }
}

/**
 * 一次性彩带。放在整组勾选方框所在卡片的**后面**。
 *
 * @param trigger 每次值变化就重播一遍。传"这张清单刚刚变为全部完成"的事件序号
 *                （单调递增的 Long），不要传 Boolean —— Boolean 在"完成→取消→再完成"
 *                时值会从 true 回到 false 再到 true，用 `!=` 判断会把第 2 次的重播吃掉。
 */
@Composable
internal fun ConfettiBurst(
    trigger: Long,
    origin: Offset,
    modifier: Modifier = Modifier,
) {
    if (trigger <= 0L) return
    val palette = confettiPalette()
    // 彩带参数只在 trigger 变化时重算一次（含随机种子 → 每次喷的角度都不一样）
    val pieces = remember(trigger) {
        confettiPieces(
            count = Note.CONFETTI_COUNT,
            colorCount = CONFETTI_COLORS,
            seed = trigger.toInt(),
            speedMinMax = 0.55f..1.05f,
        )
    }
    val originState = rememberUpdatedState(origin)
    // Float 存毫秒（进度是连续的，Long 会在 animateFloatAsState 里来回转）
    var elapsed by remember(trigger) { mutableFloatStateOf(-1f) }

    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        var done = false
        while (!done) {
            val now = withFrameNanos { it }
            val t = ((now - start) / 1_000_000f)
            if (t >= Note.ConfettiMillis) {
                done = true
                elapsed = Note.ConfettiMillis.toFloat()
            } else {
                elapsed = t
            }
        }
    }

    val alpha = confettiAlpha(elapsed, Note.ConfettiMillis.toFloat())
    if (alpha <= 0f) return

    Canvas(modifier = modifier) {
        drawConfetti(
            pieces = pieces,
            palette = palette,
            elapsed = elapsed,
            alpha = alpha,
            origin = originState.value,
        )
    }
}

/**
 * 画一帧彩带。抽成无状态函数是为了能单独调、参与预览。
 *
 * @param elapsed 已播放时长（毫秒）
 * @param alpha 全局不透明度
 * @param origin 发射点（Canvas 局部坐标）
 */
internal fun DrawScope.drawConfetti(
    pieces: List<ConfettiPiece>,
    palette: List<Color>,
    elapsed: Float,
    alpha: Float,
    origin: Offset,
    gravity: Float = CONFETTI_GRAVITY,
) {
    if (elapsed < 0f || alpha <= 0f) return
    pieces.forEach { p ->
        val (dx, dy) = confettiOffset(p, elapsed, gravity)
        val spin = confettiSpin(p, elapsed)
        val center = Offset(origin.x + dx, origin.y + dy)
        // 画到画布外就跳过：彩带飞出去后继续画是纯浪费（每帧 18 片都是 overdraw）
        if (center.x < -40f || center.x > size.width + 40f ||
            center.y < -60f || center.y > size.height + 40f
        ) {
            return@forEach
        }
        val color = palette.getOrElse(p.colorIndex % palette.size) { Color.Gray }
        // 绕自身中心自旋，画成矩形 → 旋转时宽窄变化，读起来像纸片翻飞
        rotate(degrees = spin, pivot = center) {
            translate(left = center.x - p.width / 2f, top = center.y - p.length / 2f) {
                drawRect(
                    color = color.copy(alpha = alpha),
                    size = Size(p.width, p.length),
                )
            }
        }
    }
}

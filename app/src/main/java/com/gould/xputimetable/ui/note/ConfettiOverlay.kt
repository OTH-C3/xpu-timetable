/*
 * ConfettiOverlay.kt —— 彩点绘制层（M14：从屏幕顶边整条线「撒落」）
 *
 * 挂在页面**最上层**（与 Scaffold 同级的 Box 根），所以彩点从屏幕顶部落下来时
 * 覆盖整个页面，与观感一致。
 *
 * 实现要点：
 *   1. 零新依赖：全部用 Canvas 手绘。项目约束是不为小功能引库。
 *   2. 动画驱动用 `withFrameNanos` 读真实帧时钟，不用固定步长累加 ——
 *      固定步长在掉帧时会"越走越快/越走越慢"，真机上 60/120Hz 表现会不一致。
 *   3. **画圆点而不是旋转矩形**（M14）。旧版画细长纸片要靠"翻转时宽窄变化"
 *      伪造体积，那本质是"向上抛再翻飞"；而"撒落"没有上升段，圆点本身就是
 *      终点形状，不需要自旋去补质感 —— 少一个维度，每帧也少一次 rotate。
 *      [ConfettiPiece.spin] 只给那 20% 的椭圆用（扁椭圆转起来才看得出）。
 *   4. 出屏即跳过：80 个点每帧都参与变换时，屏幕外那些是纯浪费（overdraw）。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.gould.xputimetable.ui.theme.Note

/**
 * 彩点调色板。
 *
 * 取自 Material 主题色而非写死：项目规矩是颜色一律走 `colorScheme`，
 * 且跟随明暗主题。这里刻意**不**用「唯一强调色 = primary」那条规则 ——
 * 那条规则约束的是"常态强调"，庆祝彩点是唯一允许彩色重彩的场合（一次性、有明确语义）。
 *
 * M14 补到 7 色：用户参考图里粉/橙/蓝/绿/紫/黄都出现了，5 色不够分配，
 * 会出现"连续两个同色"这种一眼能看出的廉价感。
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
            Color(0xFF4CAF50),   // 绿
            Color(0xFFFFB300),   // 琥珀
            Color(0xFFE91E8C),   // 粉（参考图里最显眼的一档）
        )
    }
}

/**
 * 一次性彩点。挂在页面根，与 Scaffold 同级。
 *
 * @param trigger  每次值变化就重播一遍。传单调递增的 Long（"页面刚刚全部完成"
 *                 的事件序号），**不要传 Boolean** —— Boolean 在"完成→取消→再完成"
 *                 时值会从 true 回到 false 再到 true，用 `!=` 判断会把第 2 次吃掉。
 * @param topLineY 撒落线在**页面根坐标**下的 y（取状态栏底部，
 *                 让彩点从可见区顶部开始落，不在状态栏里浪费高度）。
 * @param modifier 建议给 `fillMaxSize()`。
 */
@Composable
internal fun ConfettiBurst(
    trigger: Long,
    topLineY: Float,
    modifier: Modifier = Modifier,
) {
    if (trigger <= 0L) return
    val palette = confettiPalette()
    // 参数只在 trigger 变化时重算一次（含随机种子 → 每次撒的分布都不一样）
    val pieces = remember(trigger) {
        confettiPieces(
            count = Note.CONFETTI_COUNT,
            colorCount = CONFETTI_COLORS,
            seed = trigger.toInt(),
        )
    }
    var elapsed by remember(trigger) { mutableFloatStateOf(-1f) }

    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = (now - start) / 1_000_000f
            if (t >= Note.ConfettiMillis) {
                elapsed = Note.ConfettiMillis.toFloat()
                break
            }
            elapsed = t
        }
    }

    val alpha = confettiAlpha(elapsed, Note.ConfettiMillis.toFloat())
    // ⚠️ 播完就 return（离开组合）。父层 ConfettiOverlay 用 playedSeq 记账，
    // 保证同一个 trigger 不会再进这里 —— 否则"离开再进入组合"会重播一遍
    // （M14 修掉的 bug：新建清单后彩带又喷一次）。
    if (alpha <= 0f) return

    Canvas(modifier = modifier) {
        drawConfetti(
            pieces = pieces,
            palette = palette,
            elapsed = elapsed,
            alpha = alpha,
            topLineY = topLineY,
        )
    }
}

/**
 * 画一帧彩点。抽成无状态函数是为了能单独调、参与预览。
 *
 * @param elapsed  已播放时长（毫秒）
 * @param alpha    全局不透明度
 * @param topLineY 撒落线的 y（Canvas 局部坐标）
 */
internal fun DrawScope.drawConfetti(
    pieces: List<ConfettiPiece>,
    palette: List<Color>,
    elapsed: Float,
    alpha: Float,
    topLineY: Float,
    gravity: Float = CONFETTI_GRAVITY,
) {
    if (elapsed < 0f || alpha <= 0f) return
    pieces.forEach { p ->
        val (dx, dy) = confettiOffset(p, elapsed, gravity)
        val r = p.radius
        val cx = p.startXRatio * size.width + dx
        val cy = topLineY + dy
        // 出屏即跳过：80 个点里飞到屏外的那些，继续画是纯 overdraw。
        // 只判"下方出屏"，上方不会出（彩点从顶边往下走）。
        if (cx < -r || cx > size.width + r || cy - r > size.height) return@forEach

        val color = palette.getOrElse(p.colorIndex % palette.size) { Color.Gray }
        val w = r * 2f
        // aspect < 1 = 竖向压扁。顺带把自旋记进横竖比：扁椭圆转起来看得出，
        // 正圆转了也看不出来 —— 两者共用同一个 drawOval，省一个分支。
        val h = w * p.aspect
        rotate(degrees = confettiSpin(p, elapsed), pivot = Offset(cx, cy)) {
            translate(left = cx - w / 2f, top = cy - h / 2f) {
                drawOval(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                )
            }
        }
    }
}

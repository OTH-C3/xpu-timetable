/*
 * Confetti.kt —— 庆祝彩带的运动学（纯函数部分）
 *
 * 需求：清单全部完成时，从整组勾选方框**后面蹦出彩带**。
 *
 * 为什么自己算而不用现成库：项目零新依赖是硬约束（引入一个动画库换 18 条彩带
 * 不划算），而且彩带只需要"抛物线 + 自旋 + 淡出"这三件事，纯函数几十行就够。
 * 真正耗性能的是绘制，那部分在 ConfettiOverlay.kt。
 *
 * 为什么把算式单独成文件：轨迹是可以脱离 Compose 精确断言的
 * —— 上升/下落的时间点、水平对称性、颜色分配是否均匀，都能单测钉住。
 */
package com.gould.xputimetable.ui.note

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 一片彩带的所有运动参数（生成一次，之后每帧按它推位置）。
 *
 * @param angleDeg 发射角度。**0 = 正上方，逆时针为正**（屏幕坐标系 y 向下）。
 * @param speed 初速度，px/ms
 * @param spin 自旋角速度，度/ms
 * @param length 彩带长度，px
 * @param width 彩带宽，px
 * @param colorIndex 调色板下标
 * @param drift 水平漂移系数（模拟空气阻力下的横向偏移，正=被吹偏）
 */
internal data class ConfettiPiece(
    val angleDeg: Float,
    val speed: Float,
    val spin: Float,
    val length: Float,
    val width: Float,
    val colorIndex: Int,
    val drift: Float,
)

/**
 * 生成一组彩带。
 *
 * @param count 片数
 * @param colorCount 调色板大小
 * @param seed 随机种子（固定它就能让单测确定化）
 * @param speedMinMax 初速度区间，px/ms
 * @param fanDeg 扇形张角：彩带**只向上方扇形内**喷，不往侧下喷（不然会糊到卡片下面）
 */
internal fun confettiPieces(
    count: Int,
    colorCount: Int,
    seed: Int,
    speedMinMax: ClosedFloatingPointRange<Float>,
    fanDeg: Float = 110f,
): List<ConfettiPiece> {
    require(count > 0) { "count 必须为正" }
    require(colorCount > 0) { "colorCount 必须为正" }
    val r = Random(seed)
    val half = fanDeg / 2f
    return List(count) {
        // 在 [-half, +half] 均匀取角：均匀分布比三角分布更自然，也不会全挤在正上方
        val angle = -half + r.nextFloat() * fanDeg
        ConfettiPiece(
            angleDeg = angle,
            speed = speedMinMax.start + r.nextFloat() * (speedMinMax.endInclusive - speedMinMax.start),
            spin = (r.nextFloat() * 2f - 1f) * 0.9f,   // ±0.9 度/ms，一秒能转一圈左右
            length = 8f + r.nextFloat() * 8f,
            width = 2.5f + r.nextFloat() * 2f,
            // 颜色用取模均分而不是纯随机：纯随机可能连续几片同色，看起来像"少了一色"
            colorIndex = it % colorCount,
            drift = (r.nextFloat() * 2f - 1f) * 0.25f,
        )
    }
}

/** 单片彩带在时刻 [t]（毫秒）相对发射点的偏移。 */
internal fun confettiOffset(piece: ConfettiPiece, t: Float, gravity: Float): Pair<Float, Float> {
    // 初速度分解：0° 是正上方，所以 y 分量取 -cos，x 分量取 sin
    val rad = Math.toRadians(piece.angleDeg.toDouble()).toFloat()
    val vx = sin(rad) * piece.speed
    val vy = -cos(rad) * piece.speed
    // 水平额外漂移：用同一个 t^2/2 加速度项，方向恒为 +x 的 drift 倍
    val k = 0.5f * gravity * t * t
    return (vx * t + piece.drift * k) to (vy * t + k)
}

/** 单片彩带在时刻 [t]（毫秒）的自旋角度。 */
internal fun confettiSpin(piece: ConfettiPiece, t: Float): Float = piece.spin * t

/**
 * 彩带在时刻 [t] 的整体不透明度。
 *
 * 形状：淡入极快（前 8%）→ 保持 → 后 30% 线性淡出。
 * 淡入不做的化彩带是"凭空出现"，会像 bug；淡入太长则看不清喷出的瞬间。
 */
internal fun confettiAlpha(t: Float, totalMillis: Float): Float = when {
    t <= 0f -> 0f
    t >= totalMillis -> 0f
    t < totalMillis * 0.08f -> t / (totalMillis * 0.08f)
    t > totalMillis * 0.70f -> 1f - (t - totalMillis * 0.70f) / (totalMillis * 0.30f)
    else -> 1f
}

/**
 * 调色板大小（[CONFETTI_COUNT] 在 Tokens.Note 里，与其他待办令牌同处一处）。
 *
 * 为什么只留这一个"魔数"：片数是"用户能感知的观感参数"，要能被调；
 * 颜色数是实现细节（够色轮转即可），没必要开一个令牌。
 */
internal const val CONFETTI_COLORS = 5

/**
 * 重力加速度，**单位 px/ms²**。
 *
 * ⚠️ 换算过程（第一版这里写成 1.15，差了 293 倍，单测当场抓到"彩带往下飞"）：
 *   真实重力 9.8 m/s²，按 1m ≈ 400px 折算 → 3920 px/s²；
 *   而 1 px/ms² = 1e6 px/s²，所以 3920 px/s² = **0.00392 px/ms²**。
 * 这个量级下才有对的观感：初速 1.05 px/ms 的彩带约 250ms 到顶、升高约 130px、
 * 500ms 落回发射点 —— 正好在 1100ms 的播放窗口内走完"喷出→翻飞→落下"。
 */
internal const val CONFETTI_GRAVITY = 0.00392f

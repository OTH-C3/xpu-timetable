/*
 * Confetti.kt —— 庆祝彩点（Colour Dots）的运动学（纯函数部分）
 *
 * 需求：清单全部完成时，从**屏幕顶边一整条线**上撒下 80 个左右彩色圆点，
 * 像小米便签那样"洒"下来，而不是从勾选框里喷一撮纸片。
 *
 * 为什么自己算而不用现成库：项目零新依赖是硬约束（引一个动画库换一行物理公式
 * 不划算），而且彩点只需要"初速 + 重力 + 起点错开"这三件事，几十行纯函数就够。
 * 真正耗性能的是绘制，那部分在 ConfettiOverlay.kt。
 *
 * 为什么把算式单独成文件：轨迹是可以脱离 Compose 精确断言的
 * —— 撒落方向、横向铺开范围、起始时间是否错开，都能单测钉住。
 *
 * 为什么是**圆点**而不是细长矩形（旧版是 8~16px 长、2.5~4.5px 宽的纸片）：
 * 用户看过真机效果后明确否掉了纸片，要的是截图里那种圆色点。
 * 更实在的原因是两种形状服务的动势不同——纸片得靠"翻转时宽窄变化"来伪造体积，
 * 那本质是要**向上抛再翻飞**；而"撒落"没有上升段，圆点本身就是终点形状，
 * 不需要自旋去补质感，少一个维度也少一次 drawRoundRect。
 * 保留极少量椭圆（见 [aspect]）纯粹是为了让边缘不那么"塑料"。
 */
package com.gould.xputimetable.ui.note

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 一个彩色点的全部运动参数（生成一次，之后每帧按它推位置）。
 *
 * @param startXRatio 发射点在顶边的位置，**0 = 屏幕最左，1 = 屏幕最右**。
 * @param tiltDeg 下落方向偏角，**0 = 垂直向下（屏幕 y 轴正方向），正 = 偏右，负 = 偏左**。
 * @param speed 初速度，px/ms（向下为正方向的分量由 tilt 决定）
 * @param spin 自旋角速度，度/ms（圆点几乎看不出自旋，留着给椭圆用）
 * @param radius 半径，px。直径 8~18px
 * @param aspect 竖向压缩比：短边/长边，**1 = 正圆**，0.7 = 明显扁
 * @param colorIndex 调色板下标
 * @param drift 水平漂移系数（风：正 = 被吹向右）
 * @param delayMs 撒落起始时间：**到这个时刻之前它静止在顶边**，之后才开始下落
 */
internal data class ConfettiPiece(
    val startXRatio: Float,
    val tiltDeg: Float,
    val speed: Float,
    val spin: Float,
    val radius: Float,
    val aspect: Float,
    val colorIndex: Int,
    val drift: Float,
    val delayMs: Float,
)

/**
 * 生成一组彩色点。
 *
 * @param count 点数
 * @param colorCount 调色板大小
 * @param seed 随机种子（固定它就能让单测确定化）
 * @param speedMinMax 初速度区间，px/ms
 * @param spreadDeg 下落方向的左右张角：垂直向下为正，**只往下方扇形内**撒
 * @param delayMaxMs 起始时间的最大错开量，毫秒。色点出屏大约要 600ms，
 *                   总时长 1100ms，所以错开到 520ms 才能让尾段还有东西在下
 */
internal fun confettiPieces(
    count: Int,
    colorCount: Int,
    seed: Int,
    speedMinMax: ClosedFloatingPointRange<Float> = 0.08f..0.45f,
    spreadDeg: Float = 30f,
    delayMaxMs: Float = 520f,
): List<ConfettiPiece> {
    require(count > 0) { "count 必须为正" }
    require(colorCount > 0) { "colorCount 必须为正" }
    val r = Random(seed)
    // 每个色点占的"格宽"，横向抖动与它同量级才既不挤成一堆也不排成栅栏
    val slot = 1f / count
    return List(count) { i ->
        ConfettiPiece(
            // 分层铺开：第 i 个先占 i/count 这一格，再抖 ±60% 格宽。
            // 纯随机会有几格空着、有几格挤三个；纯等距会像栅栏。
            startXRatio = ((i * slot) + (r.nextFloat() * 2f - 1f) * slot * 0.6f).coerceIn(0f, 1f),
            tiltDeg = (r.nextFloat() * 2f - 1f) * spreadDeg,
            speed = speedMinMax.start + r.nextFloat() * (speedMinMax.endInclusive - speedMinMax.start),
            spin = (r.nextFloat() * 2f - 1f) * 0.9f,
            // 开方把分布往 1 推：多数接近正圆，少数明显扁 —— 全是正圆会像程序画的
            radius = 4f + r.nextFloat() * 5f,
            aspect = 0.7f + 0.3f * sqrt(r.nextFloat()),
            // 颜色用取模均分而不是纯随机：纯随机可能连续几个同色，看起来像"少了一色"
            colorIndex = i % colorCount,
            drift = (r.nextFloat() * 2f - 1f) * 0.45f,
            // 平方分布：早落的多、晚落的少，视觉重心在前半段（前半段本来就有动作）
            delayMs = r.nextFloat() * r.nextFloat() * delayMaxMs,
        )
    }
}

/**
 * 单个色点在时刻 [t]（毫秒）相对**自己顶边起点**的偏移。
 *
 * [t] 不含错开的延迟：延迟在算式内部用 [ConfettiPiece.delayMs] 扣掉，
 * 所以调用方拿到的 t 就是"这次庆祝播了多久"，不用自己减。
 */
internal fun confettiOffset(piece: ConfettiPiece, t: Float, gravity: Float): Pair<Float, Float> {
    // 到点之前静止在顶边（offset 恒 0），到点后才开始走 —— 这是"层次感"的来源
    val te = (t - piece.delayMs).coerceAtLeast(0f)
    // 0° 是正下方，所以 y 分量取 +cos、x 分量取 sin（正数 = 屏幕下方/右方）
    val rad = Math.toRadians(piece.tiltDeg.toDouble()).toFloat()
    val vx = sin(rad) * piece.speed
    val vy = cos(rad) * piece.speed
    // 水平"风"用同一个 t²/2 加速度项，方向恒为 +x 的 drift 倍
    val k = 0.5f * gravity * te * te
    return (vx * te + piece.drift * k) to (vy * te + k)
}

/** 单个色点在时刻 [t]（毫秒）的自旋角度。延迟之前不转（反正也看不见）。 */
internal fun confettiSpin(piece: ConfettiPiece, t: Float): Float {
    val te = (t - piece.delayMs).coerceAtLeast(0f)
    return piece.spin * te
}

/**
 * 彩点在时刻 [t] 的整体不透明度。
 *
 * 形状：淡入极快（前 8%）→ 保持 → 后 30% 线性淡出。
 * 淡入不做的化彩点是"凭空出现"，会像 bug；淡入太长则看不清洒下来的瞬间。
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
 * 为什么只留这一个"魔数"：点数是"用户能感知的观感参数"，要能被调；
 * 颜色数是实现细节（够色轮转即可），没必要开一个令牌。
 */
internal const val CONFETTI_COLORS = 7

/**
 * 重力加速度，**单位 px/ms²**。
 *
 * ⚠️ 换算过程（第一版这里写成 1.15，差了 293 倍，单测当场抓到"彩带往上飞"）：
 *   真实重力 9.8 m/s²，按 1m ≈ 400px 折算 → 3920 px/s²；
 *   而 1 px/ms² = 1e6 px/s²，所以 3920 px/s² = **0.00392 px/ms²**。
 * 这个量级下 1100ms 的窗口里，自由落体能走 0.5·g·t² ≈ 2370px —— 远超屏高，
 * 所以色点必然在半程就出屏，靠 [ConfettiPiece.delayMs] 错开起始时间把"有东西在下"
 * 这件事铺满整段动画，而不是让前 600ms 热闹、后 500ms 空场。
 */
internal const val CONFETTI_GRAVITY = 0.00392f

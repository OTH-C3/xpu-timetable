/*
 * ScreenTransition.kt —— 页面转场的方向与时长（从 AppNav 拆出）
 *
 * 作用：只回答一件事「新页面从哪里进来」。拆出去的实际原因是门禁——AppNav 是个
 * 装配型「大 when」，每加一个目的地就长十几行，很快顶到单文件 300 行上限；
 * 转场方向和具体装配无关，单独成文件后一眼能看懂动画是怎么来的。
 *
 * 方向约定（M10 老大需求 + M11-第三批改订）：
 *   - 前进 navForward=true  ：自下而上 +height/10 → 有"进入下一层"的方向感
 *   - 返回 navForward=false ：自上而下 -height/10 → 与前进相反，方向本身就是"返回"的提示，
 *     且**只用这一套**：任何页面返回都走从上往下淡入，不再出现"有的页面从下往上"的情形。
 *
 * 为什么把缓动从 EaseOutStandard 换成 FastOutSlowIn、时长从 450 改成 350（都是老大的观感反馈"快 + 回弹"）：
 *   EaseOutStandard(0.16,1,0.3,1) 是强前倾曲线，t=0.25 就已经走完 86% 位移，
 *   位移在头 100ms 内砸完、剩下时间定住，看起来像"先冲一下再回住"；
 *   而 exit 只有 200ms（FastMillis），旧页面先消失、新页面才慢慢挪进来，两段接不上。
 *   FastOutSlowIn(0.4,0,0.2,1) 两头慢、中段快，配合 enter/exit 同长（Motion.PageMillis），
 *   进出场会同时收尾 —— 位移是"落下"而不是"弹入"。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.gould.xputimetable.ui.theme.Motion

/**
 * 转场规格（只动位移与透明度，与项目「只动 transform 与 alpha」的动效令牌一致）。
 *
 * @param navForward 见文件头；true = 前进（自下而上），false = 返回（自上而下）
 * @param animated   false 时时长归零（首帧/测试用，不会看到动画）
 */
internal fun screenTransition(
    navForward: Boolean,
    animated: Boolean = true,
): ContentTransform {
    val dur = if (animated) Motion.PageMillis else 0
    // 进出场同长同缓动：旧页淡出的最后一帧与新页落位的最后一帧是同一时刻，
    // 不会出现"旧页先没、新页后被拽上来"的割裂感。
    // 两个 spec 必须分开写：fadeIn/fadeOut 吃 FiniteAnimationSpec<Float>，
    // slideInVertically 吃 FiniteAnimationSpec<IntOffset>，同一个 tween 实例类型对不上。
    // 时长与缓动仍然同源，观感上仍然是一条曲线。
    val alphaSpec = tween<Float>(dur, easing = Motion.EasePage)
    val slideSpec = tween<IntOffset>(dur, easing = Motion.EasePage)
    val enterOffset: (Int) -> Int = if (navForward) {
        { fullHeight -> fullHeight / 10 }
    } else {
        { fullHeight -> -fullHeight / 10 }
    }
    return (fadeIn(alphaSpec) + slideInVertically(slideSpec, enterOffset))
        .togetherWith(fadeOut(alphaSpec))
}

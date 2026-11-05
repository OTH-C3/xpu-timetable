/*
 * ScreenTransition.kt —— 页面转场的方向与时长（从 AppNav 拆出）
 *
 * 作用：只回答一件事「新页面怎么进来」。拆出去的实际原因是门禁——AppNav 是个
 * 装配型「大 when」，每加一个目的地就长十几行，很快顶到单文件 300 行上限；
 * 转场方向和具体装配无关，单独成文件后一眼能看懂动画是怎么来的。
 *
 * 方向约定（M12 老大需求「取消所有页面自上而下的返回转场动画」）：
 *   - 前进 navForward=true  ：保留极轻的淡入（不位移）。
 *     位移已全部移除——理由：内容带位移时，返回的瞬间整页内容会"从上方压下来"，
 *     观感是页面在往下掉，与"回到上一层"的预期相反，且滑动惯性容易误读成继续下滑。
 *   - 返回 navForward=false ：**纯淡入淡出，无位移、无缩放**（系统默认的轻量观感）。
 *
 * 为什么两向都只留淡入淡出（M12 改订）：
 *   之前是「前进自下而上 + 返回自上而下」的位移对冲，是 M10/M11 的产品要求。
 *   M12 老大反馈返回时"页面自上而下"不想要了，于是**位移整体取消**——
 *   只留 alpha 一条通道，与系统原生页面切换观感一致，也彻底避开返回键连点时的抖动。
 *   进出场仍同长同缓动：旧页淡出的最后一帧与新页落位的最后一帧是同一时刻，
 *   不会出现"旧页先没、新页后出现"的割裂感。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.gould.xputimetable.ui.theme.Motion

/**
 * 转场规格（只动透明度，与项目「只动 transform 与 alpha」的动效令牌一致）。
 *
 * @param navForward 见文件头；保留参数是为了让调用点的意图显式（前进/返回走同一套规格），
 *        并给将来"只对某一向加效果"留位置。当前实现两向等价。
 * @param animated   false 时时长归零（系统「减少动画」或首帧测试用，看不到动画）
 */
internal fun screenTransition(
    navForward: Boolean,
    animated: Boolean = true,
): ContentTransform {
    val dur = if (animated) Motion.PageMillis else 0
    // 只有 alpha 一条通道：fadeIn/fadeOut 吃 FiniteAnimationSpec<Float>，
    // 不再需要给 slideInVertically 单独准备 IntOffset 规格（位移已移除）。
    val alphaSpec = tween<Float>(dur, easing = Motion.EasePage)
    return fadeIn(alphaSpec) togetherWith fadeOut(alphaSpec)
}

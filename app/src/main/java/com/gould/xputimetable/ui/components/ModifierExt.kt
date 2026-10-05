/*
 * ModifierExt.kt —— 通用 Modifier 扩展
 *
 * 单独成文件的原因：这些是全项目共用的底层小工具，放进任何业务包都会造成
 * "别的功能引用了待办包"这种反向依赖。
 */
package com.gould.xputimetable.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * 无水波纹的点击。
 *
 * 两种用途：
 *  1. 勾选方框、折叠箭头这类"小控件"——水波纹是 44dp 直径的圆，会溢出到
 *     旁边的文字上（参考图的方框与标题是紧挨着的），观感脏；
 *  2. 底部导航的三个文字项——**这里是需求一的病根**：底栏按 M10 的决定
 *     "不再给底栏单独画背景"（要透出页面底色），于是 `Modifier.clickable` 默认带的
 *     indication（Material 的水波纹）直接画在**透明底**上，按下时整块 40dp×屏宽
 *     区域会泛出一片深色方块，看起来就是"点了底栏文字闪一个黑框"。
 *     水波纹的语义是"这块浮在内容上方"，而底栏是**贴在背景上**的一层，不该有。
 *
 * @param role 无障碍角色（如底栏的 [Role.Tab]）。传 null 则不声明。
 *
 * 实现注意：interactionSource 必须用 remember 缓存，每次新建会让点击态永远重置。
 */
fun Modifier.clickableNoRipple(
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interaction,
        indication = null,          // ← 关键：不画水波纹
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
}

/*
 * ModifierExt.kt —— 通用 Modifier 扩展
 *
 * 单独成文件的原因：这些是全项目共用的底层小工具，放进任何业务包都会造成
 * "别的功能引用了待办包"这种反向依赖。
 */
package com.gould.xputimetable.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 无水波纹的点击。
 *
 * 用途：勾选方框、折叠箭头这类"小控件"——水波纹是 44dp 直径的圆，会溢出到
 * 旁边的文字上（参考图的方框与标题是紧挨着的），观感脏。
 *
 * 实现注意：interactionSource 必须用 remember 缓存，每次新建会让点击态永远重置。
 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

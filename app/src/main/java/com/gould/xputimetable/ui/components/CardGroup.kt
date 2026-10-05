/*
 * CardGroup.kt —— 分组卡片（全项目共用，M11-第三批）
 *
 * 来源：「我的」页卡片化（settings 包）与"添加课程"页卡片化（courseedit 包）需要同一套卡片语言，
 * 于是把卡片本体下沉到这里，两边都转发引用——**只保留一份绘制实现**，改圆角/描边才不会漏改一边。
 *
 * 版式参照系统设置 / QQ 设置：组标题在卡片**外面**（灰小字），卡片 = 圆角 + 半透明面
 * + 渐变发丝描边，组内条目用横线（内缩到文字左缘）分隔。
 *
 * ## 需求四：从"白面 + 描边"改成"玻璃面"（2026-10-04）
 *
 * 起因：老大的反馈「白色的ui方框和背景很违和 / 对比高了有点突兀」。
 * 根因不是"白"，而是白卡片**完全不透明** —— 用户换成背景图后，卡片就成了
 * 一块块贴在照片上的白色纸片，与背景没有任何光影关系。
 *
 * 为什么不做真毛玻璃：联网核实过，`Modifier.blur` 官方标注 API 31+
 * （渐变 33+），文档明写低版本直接忽略；`RenderScript` 官方迁移文档写明
 * 只剩 CPU 实现。本项目 minSdk 26 → 真模糊在老设备上做不到，强行做只会掉帧。
 * 详见 GlassPalette.kt 头部的完整调研结论。
 *
 * 玻璃感的三条替代线索（本文件全部落地）：
 *   1. **半透明**（alpha 0.50~1.0，M13 起由「我的 → 自定义背景 → 卡片」调节）——
 *      背景的明暗分布透得过来，这是"透"；
 *   2. **色相跟着背景走** —— 卡片底色由背景主色推导（`glassSurfaceColor`），
 *      不再是死白；这是"融洽"；
 *   3. **上亮下暗的渐变描边** —— 玻璃有厚度，边缘会有一道高光；
 *      纯色描边会显得很"贴纸"。
 *
 * 为什么"卡片不引入第三种底色"这条老规矩仍然成立：现在卡片色是**从背景推导**的，
 * 换背景时它自己跟着变，压根不存在一个写死的色块需要去"不引入"。
 */
package com.gould.xputimetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.background.LocalCardAlpha
import com.gould.xputimetable.ui.background.LocalGlassBackdrop
import com.gould.xputimetable.ui.background.glassEdgeBottom
import com.gould.xputimetable.ui.background.glassEdgeTop
import com.gould.xputimetable.ui.background.glassShadow
import com.gould.xputimetable.ui.background.glassSurfaceColor
import com.gould.xputimetable.ui.background.glassTint
import com.gould.xputimetable.ui.theme.ListRow

/** 分组卡片：玻璃面 + 渐变描边圆角容器，内容可自由装（组内横线由调用方用 ListRow 令牌自己画）。 */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ListRow.CardCorner)
    // 玻璃面：底色由背景主色推导（无背景图时是固定半透明白），
    // 不透明度取「我的 → 自定义背景 → 卡片」里用户调的那档，没调过就用默认档。
    // remember 是必要的：这条链路上有 brush 对象，每次重组新建会让描边闪烁；
    // key 里必须带上 cardAlpha，否则拖透明度滑块时面色不跟着变（能编译、但不生效）。
    val tint = glassTint()
    val cardAlpha = LocalCardAlpha.current
    // 卡片正下方的实际颜色：用于"暗背景下自动补足不透明度"，
    // 缺了它用户把背景图调暗 + 卡片调透时字会糊（见 GlassPalette.readableFloorFor）。
    val backdrop = LocalGlassBackdrop.current
    val surface = remember(tint, cardAlpha, backdrop) {
        glassSurfaceColor(tint, cardAlpha, backdrop)
    }
    val edgeBrush = remember(tint) {
        // 上沿亮、下沿暗 —— 玻璃边缘的高光。纯色描边会退化成"贴纸边"。
        Brush.verticalGradient(listOf(glassEdgeTop(), glassEdgeBottom()))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = ListRow.GlassShadowElevation,
                shape = shape,
                ambientColor = glassShadow(),
                spotColor = glassShadow(),
            )
            .clip(shape)
            .background(surface)
            .border(
                width = 1.dp,
                brush = edgeBrush,
                shape = shape,
            )
            .padding(horizontal = ListRow.CardPadding, vertical = ListRow.CardPaddingV),
        content = content,
    )
}

/** 分组标题（小号灰字，在卡片外面，靠上留出与上一组的间距）。 */
@Composable
internal fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = ListRow.GroupSpacing, bottom = 4.dp),
    )
}

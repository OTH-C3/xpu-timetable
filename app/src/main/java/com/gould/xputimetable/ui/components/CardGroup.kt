/*
 * CardGroup.kt —— 分组卡片（全项目共用，M11-第三批）
 *
 * 来源：「我的」页卡片化（settings 包）与"添加课程"页卡片化（courseedit 包）需要同一套卡片语言，
 * 于是把卡片本体下沉到这里，两边都转发引用——**只保留一份绘制实现**，改圆角/描边才不会漏改一边。
 *
 * 版式参照系统设置 / QQ 设置：组标题在卡片**外面**（灰小字），卡片 = 圆角 + **与页面同色的白面**
 * + 发丝描边，组内条目用横线（内缩到文字左缘）分隔。
 *
 * 为什么是"白面 + 描边"而不是"淡灰底 + 淡描边"：淡灰底在浅色主题下会跟页面底色糊在一起，
 * 卡片边界得先看清底色差才看得见，"同组"这件事就被弱化成一片灰；参考截图里页面浅灰底、卡片白面，
 * 边界完全靠描边成立 —— 这也和后面"可自定义背景"的方向一致：卡片不引入第三种底色，
 * 换背景时它只跟着 surface 走，不会压出一条色带。
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.theme.ListRow

/** 分组卡片：白面 + 描边圆角容器，内容可自由装（组内横线由调用方用 ListRow 令牌自己画）。 */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ListRow.CardCorner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // 白面（= colorScheme.surface）而非 surfaceContainerLow：与页面只差"是否被描边框住"这一件事
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
                    .copy(alpha = ListRow.CardBorderAlpha),
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

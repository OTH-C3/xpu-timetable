/*
 * CompactTextField.kt —— 矮档输入框（M13 需求 7/8）
 *
 * ## 为什么不用 OutlinedTextField
 *
 * 待办页有两个输入位：「添加待办」和「改名」。它们原来都用 M3 的
 * `OutlinedTextField`，而它默认最小高度 **56dp**、描边 1dp 实色。
 * 放在行高 52dp 的待办列表里，那个框比一整行内容还高，视觉上像
 * "往卡片里塞了个输入框"，而不是"这一行正在输入"——在极简版式里
 * 尤其扎眼（老大指出的就是这两个大方框）。
 *
 * M3 的 `OutlinedTextField` 没法只调小高度：它的 `minHeight` 写死在
 * `TextFieldDefaults.minHeight`，改样式常量会波及全局所有输入框
 * （导入页、课程编辑、搜索框……），为两个框去动全项目的输入框不划算。
 *
 * 所以这里用 `BasicTextField` 自绘：框高、描边、圆角全是自己的，
 * 改它只影响这一个文件里的这两个输入位。
 *
 * ## 为什么保留"框"而不是改成下划线
 *
 * 纯下划线（Material 的经典做法）更极简，但待办页的「添加待办」是
 * 持续输入区（要能连着打好几条），没有边框会让人看不出这里能打字。
 * 折中：保留很淡的细框（alpha 0.35）+ 矮高度，两者都往"轻"里走。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.theme.Note

/**
 * 矮档单行输入框（无背景、淡描边）。
 *
 * @param value        当前文字
 * @param onValueChange 文字变化回调
 * @param placeholder  空时的提示文案
 * @param onSubmit     回车 / 键盘 Done 触发
 * @param modifier     布局修饰（宽度、内边距由调用方决定）
 */
@Composable
internal fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 出现时是否自动聚焦并弹出键盘。
     *
     * M14 修 BUG「清单昵称不可修改」：点标题 → 标题换成输入框 → 但**键盘不弹、
     * 光标不在框里**。用户接着一下键盘，字符全被别的控件吃掉，看起来就是
     * "改不了名字"。根因就是这里少了自动聚焦：`BasicTextField` 换成输入框
     * 只是"看起来能编辑"，并不会自己拿到焦点。
     *
     * 底部补充面板传 false —— 它是用户主动点开按钮才出现的，
     * 焦点与面板弹出是同一次点击，键盘由系统自然带起。
     */
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    // 只在"刚出现且要求聚焦"时请求一次。放在 LaunchedEffect(autoFocus) 里而
    // 不是 LaunchedEffect(Unit)：面板收起又打开时 autoFocus 不变，
    // 但上一个焦点已经失效，需要在每次 autoFocus 由 false→true 时再要一次。
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            // 等一帧再请求：刚进组合的节点还没挂上，immediate 请求会落空
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .height(Note.FieldHeightCompact)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
                    .copy(alpha = Note.FieldBorderAlpha),
                shape = RoundedCornerShape(Note.FieldCorner),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            // 固定 Done：两个调用点都是"单行输入 + 回车提交"，
            // 下一个键不是 Search/Next 才合理 —— 给了 Next 反而会走到别的控件上。
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            // 内容内边距：左右各 10dp，让文字不贴边；这 10dp 也把可点区域撑到 ≥44dp
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 10.dp, vertical = 0.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

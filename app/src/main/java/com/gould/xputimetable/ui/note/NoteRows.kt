/*
 * NoteRows.kt —— 待办的行级构件：勾选方框、划线文字、新增输入框（M12 需求五）
 *
 * 需求 2 的核心在这里：「勾选时以横线从左到右的动画划掉该条目」。
 * 实现要点：
 *   1. 划线**不能**用 Text 的 textDecoration —— 那条线跟着文字排版走，多行文本会每行都划，
 *      而且无法控制"从左到右"的过程；必须自己画。
 *   2. "从左到右" = 线的长度从 0 动画到**文字实测宽度**。宽度必须取自 onTextLayout 给的
 *      TextLayoutResult，**不能**用 drawBehind 的 size.width（那是布局宽度，带 weight(1f)
 *      会铺满整行，线会拖到屏幕右边缘）。详见 [StrikeThroughText] 的注释。
 *   3. 颜色从 MaterialTheme 取成局部 val 传进去（**不**用全局可变变量——那在多窗口/
 *      主题切换下必然串色）。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.components.clickableNoRipple
import com.gould.xputimetable.ui.theme.ListRow
import com.gould.xputimetable.ui.theme.Note

// ---------- 无障碍文案 ----------
private const val CD_CHECK = "标记为已完成"
private const val CD_UNCHECK = "取消完成"
private const val CD_ITEM_ROW = "待办条目，长按可删除"
private const val HINT_ADD_ITEM = "添加待办，回车即可连续添加"

/**
 * 勾选方框（自绘，不用 M3 Checkbox）。
 *
 * 为什么不用 Checkbox：M3 的 Checkbox 自带 48dp 最小触摸容器与内部 padding，
 * 放进密集列表会把行高撑到 48dp（参考图是紧凑排布，一屏要放下整张清单）。
 * 自绘能精确分出「视觉 20dp / 触摸 44dp」两层，且不引入 M3 的色调与动画。
 */
@Composable
internal fun TodoCheckBox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Note.CheckBoxCorner)
    Box(
        modifier = modifier
            .size(Note.CheckBoxTouch) // 触摸层 44dp（项目硬性下限）
            .semantics { contentDescription = if (checked) CD_UNCHECK else CD_CHECK }
            .clickableNoRipple(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Note.CheckBoxSize) // 视觉层 20dp
                .background(
                    if (checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                    shape,
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(AppIcons.check),
                    // 语义已由外层 Box 的 contentDescription 承担，这里必须置 null 免得读屏念两遍
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(Note.CheckIcon),
                )
            }
        }
    }
}

/**
 * 一条待办：勾选框 + 文字。
 *
 * 点整行 = 勾选/取消（与参考图一致，方框只是视觉）；长按 = 删除（不常驻占位）。
 * ⚠️ combinedClickable 本身已含 onClick，**不要再叠一层 clickable** —— 两者同时存在时
 * onClick 会被触发两次，表现为"勾一下又弹回"。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodoItemRow(
    text: String,
    done: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Note.ItemIndent)
            .heightIn(min = Note.RowMinHeight)
            .semantics { contentDescription = CD_ITEM_ROW }
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodoCheckBox(checked = done, onToggle = onToggle)
        Spacer(Modifier.width(Note.Gap))
        StrikeThroughText(
            text = text,
            struck = done,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 从左到右划掉的文字（需求 2 的核心）。
 *
 * 线画在文字的视觉中线上，长度 = **文字实测宽度** × 进度。
 *
 * ⚠️ 关键：宽度必须来自 TextLayoutResult 的 **getLineLeft / getLineRight**，
 * **不能用 drawBehind 的 size.width，也不能用 result.size.width**。
 * 这里的 Text 带 Modifier.weight(1f) 铺满整行剩余空间，而排版结果里的 size.width
 * 同样是这个布局宽度 —— 早期版本用它，线一路拖到卡片右边缘，视觉上像"划掉了一整行空白"
 * （2026-10-04 真机实测，英文条目上尤其明显）。只有逐行的 getLineRight - getLineLeft
 * 才是文字真正占掉的横向区间。
 *
 * 换算与算式都抽到 [StrikeGeometry.kt]，这里只做「排版结果 → LineBox」的翻译。
 *
 * 首次布局前 result 为 null，此时 progress 必为 0（新条目不可能已是划掉态），
 * 直接不画；下一帧 result 到位后自然补上。
 */
@Composable
internal fun StrikeThroughText(
    text: String,
    struck: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (struck) 1f else 0f,
        animationSpec = tween(Note.StrikeMillis),
        label = "strikeProgress",
    )
    // 局部 val 取色：绝不写进全局变量（主题切换 / 多实例会串色）
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 排版结果：记住它而不是每帧重算。用 remember(text) 复位 —— 换文本后旧行宽必须作废。
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (struck) lineColor else MaterialTheme.colorScheme.onSurface,
        onTextLayout = { result -> layout = result },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.drawBehind {
            if (progress <= 0f) return@drawBehind
            val result = layout ?: return@drawBehind
            val boxes = List(result.lineCount) { i ->
                LineBox(
                    left = result.getLineLeft(i),
                    right = result.getLineRight(i),
                    top = result.getLineTop(i),
                    bottom = result.getLineBottom(i),
                )
            }
            val stroke = Note.StrikeWidth.toPx()
            strikeSegments(boxes, progress).forEach { seg ->
                drawLine(
                    color = lineColor,
                    start = Offset(seg.x0, seg.y),
                    end = Offset(seg.x1, seg.y),
                    strokeWidth = stroke,
                )
            }
        },
    )
}

/** 清单底部的「添加待办」输入框：回车即添加并清空草稿（可连续录入下一条）。 */
@Composable
internal fun AddItemField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M13 需求 7/8：原来的 OutlinedTextField 是 56dp 高的黑框，在 52dp 行高的
    // 列表里比一整行还高（老大截图指出的大方框之一）。换矮档自绘框。
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = HINT_ADD_ITEM,
        onSubmit = onSubmit,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Note.ItemIndent, top = Note.GapSmall),
    )
}

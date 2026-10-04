/*
 * NoteListCard.kt —— 待办清单卡片（小米便签「待办」版式，M12 需求五）
 *
 * 版式对照参考截图（com.miui.note 14:16 / 14:18）：
 *   ┌ ☐  待办清单                        0 / 3   ⌃ ┐   ← 头部：整组勾选 + 标题 + 进度 + 折叠
 *   │   ☐  条目 1                             │   ← 条目：左缩进，整组完成时自动折叠
 *   │   ☐  条目 2                             │
 *   │   [添加待办，回车即可连续添加]            ┘
 *   └ 白面圆角卡片 + 发丝描边（复用 GroupCard，与「我的」页同一套卡片语言）
 *
 * 需求落点：
 *   - 需求 2「横线从左到右划掉」→ StrikeThroughText（NoteRows.kt）；
 *   - 需求 3「全部完成自动折叠」→ 折叠态由 [isExpanded] 统一算好后传入，
 *     卡片自己不判断，否则"用户手动折叠"与"自动折叠"两个来源会互相打架。
 *
 * 整组头部那个勾选框：勾上 = 把该清单所有条目置为已完成（参考图同款），
 * 它是"一键收工"，**不删除**任何条目。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.TodoList
import com.gould.xputimetable.domain.model.UNTITLED_LIST
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.components.GroupCard
import com.gould.xputimetable.ui.components.clickableNoRipple
import com.gould.xputimetable.ui.theme.ListRow
import com.gould.xputimetable.ui.theme.Note

/**
 * 一张清单卡片。
 *
 * @param expanded     是否展开条目（自动折叠与手动折叠都走这一个开关）
 * @param onToggleExpand 点击头部折叠箭头
 * @param onDeleteList 删除整张清单（走二次确认）
 * @param onDeleteItem 长按某条目时回调
 * @param onAddItem    在本清单里新增条目（回车触发）
 * @param editingTitle 是否正在编辑标题（编辑中标题变成输入框）
 * @param titleDraft   标题输入中的文字
 * @param onStartEditTitle 点击标题进入编辑
 * @param onTitleDraftChange 标题输入回调
 * @param onCommitTitle 提交标题（回车或失焦）
 */
@Composable
internal fun NoteListCard(
    list: TodoList,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleItem: (itemId: Long, done: Boolean) -> Unit,
    onDeleteList: () -> Unit,
    onDeleteItem: (itemId: Long, text: String) -> Unit,
    onAddItem: (listId: Long) -> Unit,
    itemDraft: String,
    onItemDraftChange: (String) -> Unit,
    editingTitle: Boolean,
    titleDraft: String,
    onStartEditTitle: () -> Unit,
    onTitleDraftChange: (String) -> Unit,
    onCommitTitle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier = modifier) {
        // ---------- 头部：整组勾选 + 标题 + 进度 + 折叠 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.MinHeight)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodoCheckBox(
                checked = list.allDone,
                onToggle = {
                    // 整组勾选 = 把每一条都置成目标状态（而不是只改第一条）
                    val target = !list.allDone
                    list.items.forEach { if (it.done != target) onToggleItem(it.id, target) }
                },
            )
            Spacer(Modifier.width(Note.Gap))
            // 标题：平时是可点的静态文字（点一下就地变输入框），编辑中是输入框。
            // 不做成"旁边一个铅笔按钮"——那会让卡片头部多一个常驻控件，
            // 而标题本身空着大片区域，是最自然的热区。
            if (editingTitle) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = onTitleDraftChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitTitle() }),
                    textStyle = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = list.title.ifBlank { UNTITLED_LIST },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    // 整组完成：标题转灰，一眼看出"这块已收工"（参考图里也是变灰的）
                    color = if (list.allDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickableNoRipple(onClick = onStartEditTitle),
                )
            }
            Text(
                text = "${list.doneCount} / ${list.items.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpandButton(expanded = expanded, onClick = onToggleExpand)
        }

        // ---------- 条目区 ----------
        // 用高度动画而不是 if：折叠才有"收起来"的观感，直接 if 会瞬间消失，
        // 与需求 3 的"自动折叠"不符。
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(Note.CollapseMillis)) + fadeIn(tween(Note.CollapseMillis)),
            exit = shrinkVertically(tween(Note.CollapseMillis)) + fadeOut(tween(Note.CollapseMillis)),
        ) {
            Column {
                list.items.forEach { item ->
                    TodoItemRow(
                        text = item.text,
                        done = item.done,
                        onToggle = { onToggleItem(item.id, !item.done) },
                        onLongPress = { onDeleteItem(item.id, item.text) },
                    )
                }
                // 已完成且折叠中时不显示输入框：这一组已经收工，不该再往里加东西
                if (!list.allDone) {
                    AddItemField(
                        value = itemDraft,
                        onValueChange = onItemDraftChange,
                        onSubmit = { onAddItem(list.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Note.GapSmall))
    }
}

/** 头部的折叠箭头（参考图里是浅灰圆底的小箭头）。 */
@Composable
private fun ExpandButton(expanded: Boolean, onClick: () -> Unit) {
    Icon(
        painter = painterResource(if (expanded) AppIcons.chevronUp else AppIcons.chevronDown),
        contentDescription = if (expanded) "收起这张清单" else "展开这张清单",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(Note.ExpandTouchTarget)
            .clickableNoRipple(onClick = onClick)
            // 图标本身只占中间一小块，边距由 size() 撑出的热区承担
            .padding(Note.ExpandIconInset),
    )
}

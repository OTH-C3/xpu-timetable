/*
 * NoteListCard.kt —— 待办清单卡片（小米便签「待办」版式，M12 需求五）
 *
 * 版式对照参考截图（com.miui.note 14:16 / 14:18）：
 *   ┌ ☐  待办清单                        0 / 3   ⌃ ┐   ← 头部：整组勾选 + 标题 + 进度 + 折叠
 *   │   ☐  条目 1                             │   ← 条目：左缩进，整组完成时自动折叠
 *   │   ☐  条目 2                             │
 *   │   [添加待办，回车即可连续添加]            ┘
 *   └ 玻璃面圆角卡片 + 渐变发丝描边（复用 GroupCard，与「我的」页同一套卡片语言）
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
 * @param onDeleteList 长按头部删除整张清单（走二次确认）
 * @param onDeleteItem 长按某条目时回调
 * @param onAddItem    在本清单里新增条目（回车触发）
 * @param editingTitle 是否正在编辑标题（编辑中标题变成输入框）
 * @param titleDraft   标题输入中的文字
 * @param onStartEditTitle 点击标题进入编辑
 * @param onTitleDraftChange 标题输入回调
 * @param onCommitTitle 提交标题（回车或失焦）
 * @param onBurstOrigin 上报"整组勾选方框中心"的**页面根坐标**，供页面层画彩带
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onBurstOrigin: (Offset) -> Unit,
    enterFromBottom: Boolean,
    onEnterAnimationDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 把"整张卡片中心"换算成**页面根坐标**报给页面层 ——
    // 彩带由 NoteScreen 的 Overlay 统一画（原因见那里的注释：卡片完成瞬间就
    // 离开组合，彩带挂在自己身上会一帧都画不出来）。
    //
    // ⚠️ 必须用 positionInRoot() 而不是 positionInParent()：卡片在 LazyColumn 里，
    // positionInParent 给的是**相对列表**的坐标，而 Overlay 铺的是整个页面，
    // 两者原点不同，直接用会让彩带从"列表上方"偏出去。
    //
    // 发射点是**整张卡片的中线**，不是勾选框中心（老大要求）：
    // 「一勾完整个白色方框从中间出来」—— 方框指的是整张卡片。
    // 原先从左边角那个小勾选框喷出，既不对称又不像"从卡片里出来"。
    //
    // y 取整卡高度的一半（用 coords.size，不受折叠影响；折叠时卡片很矮，
    // 中线就落在仅剩的那条头部上，喷出点仍在可见带里）。
    //
    // 纯几何换算，一个 dp 令牌都不需要：只用 coords.size（已经是像素），
    // 所以这里不需要 LocalDensity —— 也不该引入，那是"以为要转 dp"的信号。
    val reportOrigin = Modifier.onGloballyPositioned { coords ->
        val topLeftInRoot = coords.positionInRoot()
        onBurstOrigin(
            Offset(
                x = topLeftInRoot.x + coords.size.width / 2f,
                y = topLeftInRoot.y + coords.size.height / 2f,
            ),
        )
    }
    // 需求 2：新清单从下方滑入 + 淡入。
    //
    // ⚠️ 为什么用 `graphicsLayer.translationY` 而不是 AnimatedVisibility：
    // AnimatedVisibility 的 enter 会在**每次重新进组合**时重播，而 LazyColumn 的 item
    // 滚出视口就会被 dispose —— 用户上下滑几趟就会看到这张"新建的"卡片反复往上跳，
    // 看起来像坏了。这里改成「一次性 Animatable，播完停在 0」，与需求 1 里
    // 「已完成」分组的做法是同一个模式。
    val enterOffset = Animatable(Note.NewListEnterOffsetPx)
    LaunchedEffect(enterFromBottom) {
        if (enterFromBottom) {
            enterOffset.animateTo(0f, tween(Note.NewListEnterMillis))
            onEnterAnimationDone()   // 播完清标记，避免重组后重播
        }
    }
    val enterModifier = if (enterFromBottom) {
        Modifier.graphicsLayer { translationY = enterOffset.value }
    } else {
        Modifier
    }

    GroupCard(modifier = modifier.then(reportOrigin).then(enterModifier)) {
        // ---------- 头部：整组勾选 + 标题 + 进度 + 折叠 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.MinHeight)
                // 长按整行 = 删除这张清单（走二次确认）。
                // ⚠️ 这条手势是 M12 补上的：之前 onDeleteList 只是**声明了参数**
                // 却没有任何调用点，整张清单根本删不掉（2026-10-04 真机实测发现，
                // 想清掉一条测试清单却无从下手）。放在整行而不是另加图标，
                // 与条目"长按删除"的既有约定一致，也不占一个常驻控件。
                //
                // combinedClickable 自带 onClick —— 这里给 null 占位，
                // **绝不能再叠一层 clickable**（叠加会触发两次，表现为"点一下又弹回"）。
                .combinedClickable(onClick = {}, onLongClick = onDeleteList),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 整组勾选框：**常显**，改名（编辑标题）时也一样在。
            //
            // ⚠️ M13 中间版本曾在这里按"改名时去掉勾选框"实现过，老大随后明确
            // 指出那是 bug（「点击代办清单的昵称后，前面对钩和方框消失」）——
            // 那个版本已撤销，勾选框在任何状态下都渲染。
            // 教训：编辑态隐藏一个控件，"少一次点击"看起来像优化，实际是让
            // 头部布局在两种状态下不一致，用户会以为功能坏了。
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
                // M13 需求 7/8：同样是那个 56dp 高的黑框（老大截图里的第二个大方框），
                // 换成矮档。这里不加 start padding —— 它取代的就是标题自己的位置，
                // 左边框必须与标题文字左缘对齐，否则改名瞬间标题会向右跳。
                CompactTextField(
                    value = titleDraft,
                    onValueChange = onTitleDraftChange,
                    placeholder = UNTITLED_LIST,
                    onSubmit = onCommitTitle,
                    // 右侧留 Gap：编辑框自己带描边，不留间距的话它的右边框会
                    // 正好压在 "0 / 0" 进度文字上（2026-10-05 真机实测）。
                    // 不用 weight —— weight 会把剩余宽度全吃掉，右边框必然贴到进度。
                    modifier = Modifier.weight(1f).padding(end = Note.Gap),
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
                        // 点一下 = 就地改名；长按 = 删掉这张清单。
                        // ⚠️ 这里**必须**用 combinedClickable 而不是 clickableNoRipple：
                        // 标题带 weight(1f)，铺满头部中间整块区域，若它只处理单击，
                        // 外面 Row 的长按就永远收不到事件（实测长按头部任何位置都无反应）。
                        .combinedClickable(
                            onClick = onStartEditTitle,
                            onLongClick = onDeleteList,
                        ),
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
        // 需求二追加：进入/退出再加一个纵向位移 + 淡入淡出，让"展开"是能被看见的动作，
        // 而不只是内容突然冒出来。（位移幅度 1/12：够读出方向，又不至于像转场）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(Note.CollapseMillis),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(Note.CollapseMillis)) +
                slideInVertically(tween(Note.CollapseMillis)) { -it / 12 },
            exit = shrinkVertically(
                animationSpec = tween(Note.CollapseMillis),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(Note.CollapseMillis)) +
                slideOutVertically(tween(Note.CollapseMillis)) { -it / 12 },
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

/*
 * NoteScreen.kt —— 待办清单页（M12 需求五，底栏第三项）
 *
 * 版式对照小米便签「待办」页（参考截图）：
 *   标题「待办」→ 空态（清单图标 + "没有待办"）或清单卡片列表 → 右下角浮动新建按钮
 *   整组完成的清单自动收进「已完成 N」分区（可展开查看）
 *
 * 三条需求的落点：
 *   - 需求 1（新建 + 勾选框）与需求 2（划线）在 NoteViewModel / NoteRows；
 *   - 需求 3 的"自动折叠 + 归类到已完成"由 [expandedOverrides] 这一层决定：
 *     用户**手动**点过折叠的清单以手动状态为准，没点过的才跟随"整组是否完成"自动折叠。
 *     两者混在一起会出现"清单刚自动折叠，用户点开，再勾一项又自动折叠"的抖动。
 *
 * 背景层：Scaffold 一律透明（需求一.2），底色与背景图由导航根的 PageBackground 提供。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.TodoList
import com.gould.xputimetable.ui.background.glassEdgeBottom
import com.gould.xputimetable.ui.background.glassEdgeTop
import com.gould.xputimetable.ui.background.glassSurfaceColor
import com.gould.xputimetable.ui.background.glassTint
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.Note

// ---------- 文件级文案常量 ----------
private const val TITLE = "待办"
private const val FAB_TEXT = "新建清单"
private const val CD_FAB = "新建待办清单"

/** FAB 的圆角：M3 ExtendedFloatingActionButton 默认 16dp，这里显式声明以便描边形状对齐。 */
private val FAB_CORNER = 16.dp

@Composable
internal fun NoteScreen(
    viewModel: NoteViewModel,
    onShowHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections by viewModel.sections.collectAsState()
    val ui by viewModel.ui.collectAsState()

    // 手动折叠覆盖表：只记"用户明确点过"的清单（判定见 isExpanded）
    var expandedOverrides by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    // 「已完成」分组的展开态放 ViewModel（需求 4）：remember 活不过页面切换，
    // 退出再进来就丢，用户每次都要重新点开。屏幕上只留一份真值，别在本地再抄一份。
    val doneGroupExpanded = ui.doneGroupExpanded
    // 需求 5：点过「添加待办」输入框后，键盘挡着列表，用户点不到别处。
    // 焦点状态提到页面级，才能在点别处时统一清掉（单靠输入框自己收不到"外部点击"）。
    val focusManager = LocalFocusManager.current

    // 写操作的错误统一走导航层的底部提示（页面级 SnackbarHost 是项目已知的缺陷源）
    LaunchedEffect(ui.error) {
        ui.error?.let {
            onShowHint(it)
            viewModel.consumeError()
        }
    }

    val total = sections.active.size + sections.completed.size

    // 庆祝状态（彩带 + 已完成首次滑入）：整条时间线收在 NoteCelebration.kt，
    // 这里只创建它并接线，不持有具体状态。
    val celebration = rememberCelebrationState()
    NoteCelebrationEffects(sections = sections, state = celebration)

    // 彩带 Overlay：**放在 Scaffold 之外**（与它同级的 Box 根）。
    // 因为卡片用 positionInRoot() 上报的是**窗口根坐标**；如果 Overlay 放在
    // Scaffold 的 padding 内部，两者原点差一个状态栏 + topBar 高度，
    // 彩带会整体下移一截。放同级 Box 才能保证坐标系完全一致。
    Box(
        modifier = modifier
            .fillMaxSize()
            // 需求 5：点页面空白处收起键盘 / 退出输入态。
            // 为什么必须用可聚焦的 clickable 而不是别的：`clickableNoIndication`
            // 不可聚焦时收不到焦点，键盘不会退。用 `clickable`（无 indication）
            // 才既能吃掉点击又能清焦点。
            //
            // ⚠️ 这里不能用 clickableNoRipple：它带 indication = null，
            // 那种节点在组合里**不可聚焦**，focusManager.clearFocus() 收不到它，
            // 键盘会一直挂着（2026-10-04 真机实测：点输入框后再点别处，
            // 键盘不落、输入框也退不出去）。
            //
            // 用 noIndication 而非 ripple：页面主体已经有大量可点元素，
            // 再叠一层水波纹会让"点空白"也泛一片色。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusManager.clearFocus(force = true)
            },
    ) {
        Scaffold(
            // M12 需求一.2：底色交给导航根的 PageBackground，这里必须透明
            containerColor = Color.Transparent,
        // FAB **无条件**显示（原先写成 total > 0 才显示，是个死锁：
        // 空态时用户唯一的入口就是这个按钮，藏起来就永远建不了第一条清单，
        // 页面成了无法自救的空壳。参考图的空态里同样有右下角黄色按钮）。
        floatingActionButton = {
            // 需求 6：FAB 原来用 M3 默认的 containerColor（不透明），
            // 换自定义背景时它就是一块**实心**色块压在照片上 —— 卡片都透明了，
            // 唯独这个按钮不透明，最扎眼。
            // 改成与卡片同一套玻璃面：不透明底 + 上亮下暗的渐变描边 + 极轻投影。
            ExtendedFloatingActionButton(
                onClick = viewModel::createList,
                containerColor = glassSurfaceColor(glassTint()),
                contentColor = MaterialTheme.colorScheme.onSurface,
                icon = { Icon(painterResource(AppIcons.plus), contentDescription = null) },
                text = { Text(FAB_TEXT) },
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(glassEdgeTop(), glassEdgeBottom()),
                        ),
                        shape = RoundedCornerShape(FAB_CORNER),
                    )
                    .semantics { contentDescription = CD_FAB },
            )
        },
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            Text(
                text = TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            if (total == 0) {
                NoteEmptyState()
            } else {
                val card: @Composable (TodoList) -> Unit = { list ->
                    NoteListCard(
                        list = list,
                        expanded = isExpanded(list, expandedOverrides),
                        onToggleExpand = {
                            expandedOverrides =
                                expandedOverrides.toggle(list.id, isExpanded(list, expandedOverrides))
                        },
                        onToggleItem = viewModel::toggleItem,
                        onDeleteList = { pendingDelete = DeleteTarget.List(list) },
                        onDeleteItem = { id, text -> pendingDelete = DeleteTarget.Item(id, text) },
                        onAddItem = viewModel::addItem,
                        itemDraft = ui.itemDrafts[list.id].orEmpty(),
                        onItemDraftChange = { text -> viewModel.setItemDraft(list.id, text) },
                        // 标题就地编辑：同一时刻只编一张（editingTitleOf 是单值）
                        editingTitle = ui.editingTitleOf == list.id,
                        titleDraft = ui.titleDraft,
                        onStartEditTitle = {
                            viewModel.startEditingTitle(list.id, list.title)
                        },
                        onTitleDraftChange = viewModel::setTitleDraft,
                        onCommitTitle = viewModel::commitTitle,
                        // 卡片只负责"报告勾选框在哪"，彩带由页面级 Overlay 画（见上方注释）
                        onBurstOrigin = { origin -> celebration.burstOrigin = origin },
                        // 需求 2：只有刚新建的那张播入场动效（单值，见 NoteUiState.newListId）
                        enterFromBottom = ui.newListId == list.id,
                        // 播完清标记，否则每次重组都会重播一遍
                        onEnterAnimationDone = viewModel::consumeNewListAnimation,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 底部多留 88dp：给 FAB 让位，否则最后一张卡片被按钮压住
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(Note.Gap),
                ) {
                    // ---- 进行中 ----
                    items(sections.active, key = { it.id }) { card(it) }

                    // ---- 已完成分区（需求 3）----
                    if (sections.completed.isNotEmpty()) {
                        item(key = "done-header") {
                            // 需求二：「已完成」**第一次**出现时从上方滑入，之后就位不再动。
                            //
                            // ⚠️ 为什么不用 AnimatedVisibility：LazyColumn 的 item 滚出
                            // 视口会被 dispose，滚回来时重新进组合 → enter 动画**重播**，
                            // 用户上下滑几次就看到「已完成」反复往下跳，很廉价。
                            // 这里改用一次性 Animatable：首次出现把它从 -offset 弹到 0，
                            // 之后无论重组多少次值都是 0，等价于静态就位。
                            CompletedHeader(
                                count = sections.completed.size,
                                expanded = doneGroupExpanded,
                                onToggle = {
                                    // 需求 4：写回 ViewModel，下次进页面保持
                                    viewModel.setDoneGroupExpanded(!doneGroupExpanded)
                                },
                                modifier = Modifier.graphicsLayer {
                                    translationY = celebration.doneGroupSlide.value
                                },
                            )
                        }
                        // 需求 1：展开时下面这叠清单要有动效（撑开 / 缩回，而不是凭空出现）。
                        //
                        // ⚠️ 为什么整叠塞进**一个** item，而不是给 AnimatedVisibility
                        // 里套 `items(...)`：`items()` 是 LazyListScope 的扩展函数，
                        // 不是 Composable，编译器直接拒绝（"@Composable invocations can
                        // only happen from the context of a @Composable function"）。
                        //
                        // 代价：这一叠清单失去 LazyColumn 的逐项回收（全部一起进组合）。
                        // 权衡后认为可接受 —— 「已完成」默认是收起的，且这个区本来就是
                        // 收尾信息（用户勾完的），条目数少；换成"能编译 + 有动效"更值。
                        item(key = "done-items") {
                            AnimatedVisibility(
                                visible = doneGroupExpanded,
                                enter = expandVertically(
                                    animationSpec = tween(Note.DoneGroupToggleMillis),
                                    expandFrom = Alignment.Top,
                                ) + fadeIn(tween(Note.DoneGroupToggleMillis)),
                                exit = shrinkVertically(
                                    animationSpec = tween(Note.DoneGroupToggleMillis),
                                    shrinkTowards = Alignment.Top,
                                ) + fadeOut(tween(Note.DoneGroupToggleMillis)),
                            ) {
                                Column {
                                    sections.completed.forEach { card(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // 彩带：页面级 Overlay，画在 Scaffold **之上**。
        // 需求原话是"从方框后面蹦出"，但那张清单完成瞬间就移进折叠的已完成分区、
        // 卡片当场不存在了 —— 任何"画在卡片里"的方案都会一帧都画不出来
        //（2026-10-04 真机首次实测：只看到清单消失，零彩带）。
        // 所以盖在整页之上、从方框那个坐标喷出，视觉上仍是"从方框那儿蹦出来"。
        ConfettiOverlay(state = celebration)
    }

    // 删除是不可撤销的 → 二次确认（弹窗本体在 NoteDeleteDialog.kt，主文件要守 300 行门禁）
    NoteDeleteDialog(
        target = pendingDelete,
        onDelete = { target ->
            when (target) {
                is DeleteTarget.List -> viewModel.deleteList(target.list.id)
                is DeleteTarget.Item -> viewModel.deleteItem(target.itemId)
            }
        },
        onCancel = { pendingDelete = null },
        onDeletedListExtra = viewModel::cancelEditingTitle,
    )
}

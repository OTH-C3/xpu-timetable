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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.TodoList
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.Note

// ---------- 文件级文案常量 ----------
private const val TITLE = "待办"
private const val FAB_TEXT = "新建清单"
private const val CD_FAB = "新建待办清单"
private const val DELETE_LIST_TITLE = "删除这份清单？"
private const val DELETE_ITEM_TITLE = "删除这条待办？"
private const val DELETE_TEXT = "「%s」和它下面的待办都会被删除，不可撤销。"
private const val DELETE_CONFIRM = "删除"
private const val CANCEL = "取消"

/** 删除确认的目标（整张清单 / 单条待办）。 */
private sealed interface DeleteTarget {
    data class List(val list: TodoList) : DeleteTarget
    data class Item(val itemId: Long, val text: String) : DeleteTarget
}

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
    var doneGroupExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    // 写操作的错误统一走导航层的底部提示（页面级 SnackbarHost 是项目已知的缺陷源）
    LaunchedEffect(ui.error) {
        ui.error?.let {
            onShowHint(it)
            viewModel.consumeError()
        }
    }

    val total = sections.active.size + sections.completed.size

    Scaffold(
        // M12 需求一.2：底色交给导航根的 PageBackground，这里必须透明
        containerColor = Color.Transparent,
        // FAB **无条件**显示（原先写成 total > 0 才显示，是个死锁：
        // 空态时用户唯一的入口就是这个按钮，藏起来就永远建不了第一条清单，
        // 页面成了无法自救的空壳。参考图的空态里同样有右下角黄色按钮）。
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::createList,
                icon = { Icon(painterResource(AppIcons.plus), contentDescription = null) },
                text = { Text(FAB_TEXT) },
                modifier = Modifier.semantics { contentDescription = CD_FAB },
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
                            CompletedHeader(
                                count = sections.completed.size,
                                expanded = doneGroupExpanded,
                                onToggle = { doneGroupExpanded = !doneGroupExpanded },
                            )
                        }
                        if (doneGroupExpanded) {
                            items(sections.completed, key = { it.id }) { card(it) }
                        }
                    }
                }
            }
        }
    }

    // 删除是不可撤销的 → 二次确认（与项目既有 AC-06 一致）
    pendingDelete?.let { target ->
        val name = when (target) {
            is DeleteTarget.List -> target.list.title
            is DeleteTarget.Item -> target.text
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(if (target is DeleteTarget.List) DELETE_LIST_TITLE else DELETE_ITEM_TITLE)
            },
            text = { Text(DELETE_TEXT.format(name)) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is DeleteTarget.List -> viewModel.deleteList(target.list.id)
                        is DeleteTarget.Item -> viewModel.deleteItem(target.itemId)
                    }
                    // 删掉的若是正在改标题的那张，必须把编辑态一并清掉：
                    // 否则 ui.editingTitleOf 仍指向一个已不存在的 id，
                    // 再新建一张清单时它的标题会莫名其妙进入编辑态。
                    if (target is DeleteTarget.List) viewModel.cancelEditingTitle()
                    pendingDelete = null
                }) { Text(DELETE_CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(CANCEL) }
            },
        )
    }
}

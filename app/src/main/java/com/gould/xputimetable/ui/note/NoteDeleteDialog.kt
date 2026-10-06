/*
 * NoteDeleteDialog.kt —— 待办页的删除二次确认（M12 需求五）
 *
 * 为什么要单独成文件：删除是不可撤销操作，确认弹窗必须存在；而 NoteScreen.kt
 * 承担了列表装配 + 彩带 Overlay + 三处动效，已经顶到 300 行门禁（M13 加动效时
 * 超到 367 行）。把弹窗连同 DeleteTarget 一起拆出来，主文件立刻回到门禁内，
 * 且"要删什么"这个类型与"怎么弹"住在一起，比散在两个文件里更清楚。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.gould.xputimetable.domain.model.TodoList

// ---------- 文案（原本在 NoteScreen 里，搬过来时保持一字不改） ----------
private const val DELETE_LIST_TITLE = "删除这份清单？"
private const val DELETE_ITEM_TITLE = "删除这条待办？"
private const val DELETE_TEXT = "「%s」和它下面的待办都会被删除，不可撤销。"
private const val DELETE_CONFIRM = "删除"
private const val CANCEL = "取消"

/** 删除确认的目标（整张清单 / 单条待办）。 */
internal sealed interface DeleteTarget {
    data class List(val list: TodoList) : DeleteTarget
    data class Item(val itemId: Long, val text: String) : DeleteTarget
}

/**
 * 删除二次确认弹窗。
 *
 * @param target   要删什么；null = 不弹（调用方用 `target?.let { }` 包一层）
 * @param onDelete 用户点了「删除」
 * @param onCancel 用户点了「取消」或点外部
 * @param onDeletedListExtra 删掉整张清单后的额外收尾（清掉可能还指向它的标题编辑态）
 */
@Composable
internal fun NoteDeleteDialog(
    target: DeleteTarget?,
    onDelete: (DeleteTarget) -> Unit,
    onCancel: () -> Unit,
    onDeletedListExtra: () -> Unit = {},
) {
    target ?: return
    val isList = target is DeleteTarget.List
    val name = when (target) {
        is DeleteTarget.List -> target.list.title
        is DeleteTarget.Item -> target.text
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (isList) DELETE_LIST_TITLE else DELETE_ITEM_TITLE) },
        text = { Text(DELETE_TEXT.format(name)) },
        confirmButton = {
            TextButton(onClick = {
                onDelete(target)
                // 删掉的若是正在改标题的那张，必须把编辑态一并清掉：
                // 否则 ui.editingTitleOf 仍指向一个已不存在的 id，
                // 再新建一张清单时它的标题会莫名其妙进入编辑态。
                if (isList) onDeletedListExtra()
                onCancel()
            }) { Text(DELETE_CONFIRM) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(CANCEL) }
        },
    )
}

/**
 * 接好 ViewModel 的删除确认弹窗（NoteScreen 用这个薄包装）。
 *
 * 为什么把 `when` 那几行搬进来：NoteScreen 要守 300 行门禁，而"要删哪个 →
 * 调哪个 ViewModel 方法"这件事只属于弹窗本身。它跟着弹窗走才自然。
 */
@Composable
internal fun NoteDeleteDialogFor(
    target: DeleteTarget?,
    viewModel: NoteViewModel,
    onDismiss: () -> Unit,
) {
    NoteDeleteDialog(
        target = target,
        onDelete = { t ->
            when (t) {
                is DeleteTarget.List -> viewModel.deleteList(t.list.id)
                is DeleteTarget.Item -> viewModel.deleteItem(t.itemId)
            }
        },
        onCancel = onDismiss,
        // 删掉正在改标题的那张时，编辑态必须一并清掉：
        // 否则 editingTitleOf 仍指向一个已不存在的 id，
        // 再新建一张清单时它的标题会莫名其妙进入编辑态。
        onDeletedListExtra = viewModel::cancelEditingTitle,
    )
}

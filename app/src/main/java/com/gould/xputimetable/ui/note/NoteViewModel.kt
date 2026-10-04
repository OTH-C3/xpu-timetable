/*
 * NoteViewModel.kt —— 待办清单的状态与动作（M12 需求五）
 *
 * 与既有 ViewModel 同一套约定：
 *   - 只依赖仓库接口，不认识 Room；
 *   - 写操作包 runCatching，失败转成可读中文写入 error（绝不把异常抛进 viewModelScope）；
 *   - 每个输入动作一个方法，界面用方法引用传回调（禁内联 lambda，避免整页重组）。
 *
 * 两个"输入中"状态（清单标题 / 条目文本）放在这里而不是 Composable 的 remember：
 *   旋转屏幕后草稿不该丢；且新增待办要"回车连加"，草稿的生命周期得比单次按键长。
 */
package com.gould.xputimetable.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.model.TodoSections
import com.gould.xputimetable.domain.model.groupTodoLists
import com.gould.xputimetable.domain.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 页面上会变的输入态与一次性提示。 */
data class NoteUiState(
    /** 正在编辑标题的清单 id（null = 没在编辑标题）。 */
    val editingTitleOf: Long? = null,
    /** 标题草稿。 */
    val titleDraft: String = "",
    /**
     * 条目输入草稿，**按清单 id 分开存**。
     *
     * 为什么不共用一个草稿：多张清单同时展开时，共用会让 A 清单里打的字出现在 B 清单的输入框里，
     * 回车还会把文字加到 B 去 —— 这是很容易发生且很难自查的串数据。
     */
    val itemDrafts: Map<Long, String> = emptyMap(),
    /** 一次性错误（展示后由界面消费）。 */
    val error: String? = null,
)

class NoteViewModel(private val repository: TodoRepository) : ViewModel() {

    private val _ui = MutableStateFlow(NoteUiState())
    val ui: StateFlow<NoteUiState> = _ui.asStateFlow()

    /**
     * 清单流（已按"整组完成"分成两段）。
     *
     * `SharingStarted.Eagerly`：待办页是用户主动进来的目标页，返回时不该重新订阅、
     * 更不该先闪一帧空列表再填上（项目既有纪律，见 MEMORY「页面级状态源用 Eagerly」）。
     */
    val sections: StateFlow<TodoSections> = repository.observeLists()
        .map { groupTodoLists(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TodoSections(emptyList(), emptyList()))

    // ---------- 新建 ----------

    /** 新建清单：标题先给默认名，用户可在卡片上改名（对齐参考图"先建后命名"）。 */
    fun createList() {
        viewModelScope.launch {
            runCatching { repository.createList(DEFAULT_TITLE, nextListOrder()) }
                .onFailure { fail("新建清单", it) }
        }
    }

    /** 追加一条待办到指定清单（回车触发，支持连续添加）。 */
    fun addItem(listId: Long) {
        val text = _ui.value.itemDrafts[listId]?.trim().orEmpty()
        if (text.isEmpty()) return
        // 先清草稿再写库：成功后输入框要空着、光标留在原位，用户可以接着打下一条
        //（参考图那句「回车即可连续添加」就是这个意思）。
        // 不清的话每回一次车就把同一句话再加一条，几十条重复条目。
        _ui.update { it.copy(itemDrafts = it.itemDrafts - listId) }
        viewModelScope.launch {
            runCatching { repository.addItem(listId, text) }
                .onFailure {
                    // 写失败要把文字放回去，否则用户辛苦打的字凭空消失
                    _ui.update { s -> s.copy(itemDrafts = s.itemDrafts + (listId to text)) }
                    fail("添加待办", it)
                }
        }
    }

    // ---------- 勾选 ----------

    /**
     * 勾选 / 取消勾选（需求 2：划掉但**不删除、不移位**）。
     *
     * 不清草稿、不动排序 —— 顺序完全由 sortOrder 决定，勾选只改 done 位。
     */
    fun toggleItem(itemId: Long, done: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setItemDone(itemId, done) }
                .onFailure { fail("更新待办", it) }
        }
    }

    // ---------- 删除 ----------

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteItem(itemId) }
                .onFailure { fail("删除待办", it) }
        }
    }

    /** 删除整张清单（连带其条目，由外键级联）。 */
    fun deleteList(listId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteList(listId) }
                .onFailure { fail("删除清单", it) }
        }
    }

    // ---------- 标题编辑 ----------

    fun startEditingTitle(listId: Long, current: String) {
        _ui.update { it.copy(editingTitleOf = listId, titleDraft = current, error = null) }
    }

    fun setTitleDraft(value: String) = _ui.update { it.copy(titleDraft = value) }

    fun cancelEditingTitle() = _ui.update { it.copy(editingTitleOf = null, titleDraft = "") }

    /** 提交标题；空标题按"未命名清单"落库（不存空串，界面与数据同口径）。 */
    fun commitTitle() {
        val state = _ui.value
        val id = state.editingTitleOf ?: return
        val title = state.titleDraft.trim()
        _ui.update { it.copy(editingTitleOf = null, titleDraft = "") }
        viewModelScope.launch {
            runCatching { repository.renameList(id, title.ifEmpty { DEFAULT_TITLE }) }
                .onFailure { fail("重命名清单", it) }
        }
    }

    // ---------- 条目草稿 ----------

    /** 写入某张清单的条目草稿（按清单 id 分开，见 NoteUiState.itemDrafts）。 */
    fun setItemDraft(listId: Long, value: String) = _ui.update {
        it.copy(itemDrafts = it.itemDrafts + (listId to value), error = null)
    }

    fun consumeError() = _ui.update { it.copy(error = null) }

    /** 新清单排到末尾：当前最大序号 + 1。 */
    private fun nextListOrder(): Int = (sections.value.active + sections.value.completed)
        .maxOfOrNull { it.sortOrder }
        ?.plus(1)
        ?: 0

    private fun fail(action: String, e: Throwable) {
        val reason = e.message ?: e::class.simpleName ?: "未知错误"
        _ui.update { it.copy(error = "$action 失败：$reason") }
    }

    companion object {
        /** 新建清单的默认标题（用户可点标题改名）。 */
        const val DEFAULT_TITLE = "待办清单"
    }
}

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
import com.gould.xputimetable.ui.theme.Note
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
    /**
     * **乐观**勾选态：条目 id → 目标 done。写库还没落地的那些条目用这个值渲染。
     *
     * ## 为什么需要它（M14 修 BUG「点最后一项没划线动效」）
     *
     * 勾完一张清单的最后一项时，`toggleItem` 会**推迟 660ms 才落库**（为了让彩带
     * 播完再把清单收进「已完成」）。而划线动画由 `animateFloatAsState` 驱动，
     * 它读的 `done` 来自 **库里的值** —— 于是顺序变成：
     * `点勾选 → 等 660ms → 落库 → done 变化 → 划线才开始`，
     * 660ms 之后清单已经被收走，用户根本看不到那条线。
     *
     * 这个表把"用户刚点的那个状态"立刻暴露给 UI，划线**立即**开始；
     * 落库后库里的值与它一致，这个条目就从表里移除（见 [reconcileOptimistic]）。
     * 于是观感是"划线 → 彩带 → 收进已完成"，三段都看得见。
     */
    val optimisticDone: Map<Long, Boolean> = emptyMap(),
    /** 一次性错误（展示后由界面消费）。 */
    val error: String? = null,
    /**
     * 「已完成」分组下次进页面时是否展开（M13 需求 4：记住上次的选择）。
     *
     * 为什么存在 ViewModel 而不是 `remember`：`remember` 活不过"离开页面"，
     * 退出再进来就回到默认收起，用户每次都要重新点开。
     * ViewModel 活过页面切换，所以"上次我展开过"这件事能保住。
     */
    val doneGroupExpanded: Boolean = false,
    /**
     * 刚新建出来的清单 id（M13 需求 2：让它播一次入场动效）。
     *
     * 单值而非集合：连续点三次"新建"时，只有最后一张该播动效 —— 前两张的入场
     * 动画用户根本没看见（列表瞬间就被推下去了），播了反而像卡顿。
     */
    val newListId: Long? = null,
    /**
     * 底部输入面板正在往哪张清单里加待办（M14）。null = 面板收起。
     *
     * 放 ViewModel 而不是 `remember`：旋转屏幕时面板不该消失；且它与
     * [itemDrafts] 是同一份草稿的生命周期，拆开会出现"面板在但草稿没了"。
     */
    val composingListOf: Long? = null,
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

    /**
     * 新建清单：标题先给默认名，用户可在卡片上改名（对齐参考图"先建后命名"）。
     *
     * M14：建完直接把 [composingListOf] 设成它 —— 点「+」就要能**立刻**输入，
     * 而不是"先看到一张空卡片，再自己找地方点"。这个 id 同时驱动
     * 入场动效（[newListId]）与底部面板（[composingListOf]），两者必须同源。
     */
    fun createList() {
        viewModelScope.launch {
            runCatching { repository.createList(DEFAULT_TITLE, nextListOrder()) }
                .onSuccess { id ->
                    _ui.update { it.copy(newListId = id, composingListOf = id) }
                }
                .onFailure { fail("新建清单", it) }
        }
    }

    /** 打开底部输入面板，往 [listId] 这张清单里补待办。 */
    fun openCompose(listId: Long) = _ui.update { it.copy(composingListOf = listId) }

    /** 收起底部输入面板（点「完成」或点页面别处）。 */
    fun closeCompose() = _ui.update { it.copy(composingListOf = null) }

    /** 动画播完（或页面重建）后清掉 newListId，避免入场动效重播。 */
    fun consumeNewListAnimation() = _ui.update { it.copy(newListId = null) }

    // ---------- 已完成分组的展开态（M13 需求 4）----------

    fun setDoneGroupExpanded(expanded: Boolean) =
        _ui.update { it.copy(doneGroupExpanded = expanded) }

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
     *
     * ## ⚠️ M13 需求 3：勾完最后一个时**推迟落库**
     *
     * 需求原话：「答完后先出现彩带，然后再进入已完成」。
     * 问题在于落库即分组：`sections` 是从库推出来的，一落库这张清单立刻被
     * `groupTodoLists` 划进"已完成"区 → 卡片离开"进行中" → 彩带还没播完就没了。
     *
     * 所以勾选**不是完成整组**时立即写库，而是先等 [Note.DoneMoveDelayMillis]
     * （彩带"喷出→翻飞"这段看清的时间）再写。划线动画本身走 UI 层的 progress，
     * 不依赖这次写库，所以延迟期间划线照常播完，观感上是"先划掉 → 彩带 → 收进已完成"。
     *
     * 只对"即将整组完成"延迟：取消勾选、以及没勾满时立即写，
     * 否则用户连续操作会感觉到明显的粘滞。
     */
    fun toggleItem(itemId: Long, done: Boolean) {
        val list = sections.value.active + sections.value.completed
        val delay = if (done && wouldCompleteGroup(list, itemId)) Note.DoneMoveDelayMillis else 0L
        // 乐观值先写：划线动画不等落库就开始
        // （否则点最后一项时，那条线要等 660ms 才开始，而清单已被收走 —— 用户根本看不到）
        _ui.update { it.copy(optimisticDone = it.optimisticDone + (itemId to done)) }
        viewModelScope.launch {
            if (delay > 0L) kotlinx.coroutines.delay(delay)
            runCatching { repository.setItemDone(itemId, done) }
                .onSuccess { reconcileOptimistic(itemId) }
                .onFailure {
                    // 写失败要撤掉乐观值，否则 UI 会一直显示一个库里根本没有的状态
                    _ui.update { it.copy(optimisticDone = it.optimisticDone - itemId) }
                    fail("更新待办", it)
                }
        }
    }

    /**
     * 落库成功后清掉该条目的乐观值（此后库里的值就是权威的）。
     *
     * ⚠️ 只在**值一致**时才清：若用户在这 660ms 里又点了同一个条目，
     * 新的乐观值与刚落库的值不同，贸然清掉会让 UI 闪回旧状态。
     */
    private fun reconcileOptimistic(itemId: Long) {
        val real = (sections.value.active + sections.value.completed)
            .firstNotNullOfOrNull { list -> list.items.firstOrNull { it.id == itemId } }?.done
        _ui.update { s ->
            if (real != null && s.optimisticDone[itemId] == real) {
                s.copy(optimisticDone = s.optimisticDone - itemId)
            } else {
                s
            }
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

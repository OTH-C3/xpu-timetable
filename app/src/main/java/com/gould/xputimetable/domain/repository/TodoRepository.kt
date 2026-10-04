/*
 * TodoRepository.kt —— 待办数据仓库（M12 需求五）
 *
 * 与课表仓库并列而非塞进去：课表仓库的接口对应"课表数据访问的统一契约"（架构 §8.1），
 * 待办是完全另一个领域，往里加会让那个接口失焦。ViewModel 也因此只依赖本接口，
 * 不知道 Room 的存在（ADR-004 的组合根纪律）。
 */
package com.gould.xputimetable.domain.repository

import com.gould.xputimetable.domain.model.TodoList
import kotlinx.coroutines.flow.Flow

interface TodoRepository {

    /** 全部清单及其条目（数据流：任何写操作都会自动推送新值）。 */
    fun observeLists(): Flow<List<TodoList>>

    /**
     * 新建清单，返回新 id。
     *
     * @param title 标题；空串由界面兜底为「未命名清单」
     * @param sortOrder 排在第几位（0 = 最前）。追加到末尾时传当前最大序号 + 1。
     */
    suspend fun createList(title: String, sortOrder: Int): Long

    suspend fun renameList(id: Long, title: String)

    /** 删除清单（条目随外键级联删除）。 */
    suspend fun deleteList(id: Long)

    /** 新建条目，追加到指定清单末尾。 */
    suspend fun addItem(listId: Long, text: String)

    /**
     * 勾选 / 取消勾选。
     *
     * 注意这里**只改 done 标记**：需求 2 明确"划掉该条目，但不删除、不改变它在列表中的位置"，
     * 所以绝不能在这里顺手重排 sortOrder（那会让勾完的条目跳到列表末尾）。
     */
    suspend fun setItemDone(itemId: Long, done: Boolean)

    /** 删除单条待办（这是用户主动删除，与"划掉"是两种语义，不要混）。 */
    suspend fun deleteItem(itemId: Long)
}

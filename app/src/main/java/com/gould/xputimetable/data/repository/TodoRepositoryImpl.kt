/*
 * TodoRepositoryImpl.kt —— 待办仓库实现（M12 需求五）
 *
 * 与课表仓库同风格：只做实体 ↔ 领域模型的搬运与排序，写操作逐条 await。
 *
 * 排序的两处细节：
 *   1. 条目按 (sortOrder, createdAt) 升序 —— 同序号时用创建时间兜底，
 *      避免用户连点新建产生同序号导致顺序随机跳动。
 *   2. 清单同理。
 *
 * ⚠️ 读-改-写一律走 DAO 的 suspend 单查（findXxx），**不要**用
 * `dao.observeXxx().first()`：观察流是"永不完成"的，只能靠 take(1) 截断，
 * 漏了就是永久挂起 —— 这类 bug 在界面上表现为"点了没反应"，极难查。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.dao.TodoDao
import com.gould.xputimetable.data.db.entity.TodoItemEntity
import com.gould.xputimetable.data.db.entity.TodoListEntity
import com.gould.xputimetable.domain.model.TodoItem
import com.gould.xputimetable.domain.model.TodoList
import com.gould.xputimetable.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TodoRepositoryImpl(private val dao: TodoDao) : TodoRepository {

    /**
     * 清单 + 条目合流。
     *
     * 用 combine 而不是让页面各订阅一次：后者会让页面在两次 emit 之间
     * 短暂显示"清单有了、条目是空的"，勾选后瞬间闪一下。
     */
    override fun observeLists(): Flow<List<TodoList>> =
        combine(dao.observeLists(), dao.observeItems()) { lists, items ->
            val byList = items.groupBy { it.listId }
            lists.map { entity ->
                TodoList(
                    id = entity.id,
                    title = entity.title,
                    sortOrder = entity.sortOrder,
                    items = byList[entity.id]
                        .orEmpty()
                        .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                        .map { it.toDomain() },
                )
            }.sortedWith(compareBy({ it.sortOrder }, { it.title }))
        }

    override suspend fun createList(title: String, sortOrder: Int): Long = dao.insertList(
        TodoListEntity(
            title = title,
            sortOrder = sortOrder,
            createdAt = System.currentTimeMillis(),
        ),
    )

    override suspend fun renameList(id: Long, title: String) {
        val current = dao.findList(id) ?: return
        dao.updateList(current.copy(title = title))
    }

    override suspend fun deleteList(id: Long) {
        val current = dao.findList(id) ?: return
        dao.deleteList(current)
    }

    override suspend fun addItem(listId: Long, text: String) {
        // 新条目排到该清单末尾：取现有最大序号 + 1，空清单从 0 起
        val nextOrder = (dao.maxItemOrderInList(listId) ?: -1) + 1
        dao.insertItem(
            TodoItemEntity(
                listId = listId,
                text = text,
                done = false,
                sortOrder = nextOrder,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        val item = dao.findItem(itemId) ?: return
        // ⚠️ 只改 done，**绝不重排 sortOrder** —— 需求 2：勾完的条目留在原位不动。
        dao.updateItem(item.copy(done = done))
    }

    override suspend fun deleteItem(itemId: Long) {
        val item = dao.findItem(itemId) ?: return
        dao.deleteItem(item)
    }
}

private fun TodoItemEntity.toDomain(): TodoItem = TodoItem(
    id = id,
    text = text,
    done = done,
    sortOrder = sortOrder,
)

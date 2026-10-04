/*
 * TodoDao.kt —— 待办数据访问（M12 需求五）
 *
 * 一条查询流拿全部数据（清单 + 条目），在内存里按清单分组：
 *   待办页的数据量是"几十条勾选项"这个量级，两次查询再拼比 JOIN 更直白，
 *   而且分组规则（需求 3 的自动折叠）本身就要写成一个可单测的纯函数，
 *   数据先摊平、规则后套用，比塞进 SQL 更好读也更好测。
 *
 * Room 3 要点：DAO 方法必须挂起或返回 Flow（项目约定），否则 KSP 报错。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.gould.xputimetable.data.db.entity.TodoItemEntity
import com.gould.xputimetable.data.db.entity.TodoListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    // ---------- 读 ----------

    /** 全部清单，按手动序号升序（同序号按创建时间，保证顺序稳定）。 */
    @Query("SELECT * FROM todo_lists ORDER BY sort_order ASC, created_at ASC")
    fun observeLists(): Flow<List<TodoListEntity>>

    /** 全部条目，按手动序号升序（同序号按创建时间）。 */
    @Query("SELECT * FROM todo_items ORDER BY sort_order ASC, created_at ASC")
    fun observeItems(): Flow<List<TodoItemEntity>>

    // ---------- 写前快照（读-改-写用；不要用观察流 + first，见 TodoRepositoryImpl 文件头） ----------

    @Query("SELECT * FROM todo_lists WHERE id = :id")
    suspend fun findList(id: Long): TodoListEntity?

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun findItem(id: Long): TodoItemEntity?

    /** 该清单内现有最大 sortOrder（空清单返回 null）。 */
    @Query("SELECT MAX(sort_order) FROM todo_items WHERE list_id = :listId")
    suspend fun maxItemOrderInList(listId: Long): Int?

    // ---------- 写 ----------

    /** 新建清单，返回自增 id。 */
    @Insert
    suspend fun insertList(list: TodoListEntity): Long

    @Update
    suspend fun updateList(list: TodoListEntity)

    @Delete
    suspend fun deleteList(list: TodoListEntity)

    @Insert
    suspend fun insertItem(item: TodoItemEntity): Long

    @Update
    suspend fun updateItem(item: TodoItemEntity)

    @Delete
    suspend fun deleteItem(item: TodoItemEntity)
}

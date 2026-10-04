/*
 * TodoListTest.kt —— 待办分组与折叠判定（M12 需求五）
 *
 * 这批用例守的是需求 3 最容易出回归的两条：
 *   1. 勾最后一项时清单不能"突然消失"——它必须去「已完成」分区，而不是从界面上蒸发；
 *   2. 刚新建的空清单不能被判成"已完成"（否则用户以为创建失败）。
 *
 * 另有需求 2 的行为核心「勾完不重排」：setItemDone 只改 done，sortOrder 不动，
 * 因此这里额外钉死"分组不重排传入顺序"。
 */
package com.gould.xputimetable.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoListTest {

    private fun item(id: Long, done: Boolean = false, order: Int = 0) =
        TodoItem(id = id, text = "条目$id", done = done, sortOrder = order)

    private fun list(id: Long, vararg items: TodoItem) =
        TodoList(id = id, title = "清单$id", sortOrder = 0, items = items.toList())

    // ---------- allDone ----------

    @Test
    fun `空清单不算完成`() {
        // 回归防线：allDone 若写成 items.all{...}，空清单会返回 true，
        // 新建的清单立刻跳进「已完成」，用户以为创建失败
        assertFalse(list(1L).allDone)
    }

    @Test
    fun `全部完成才算完成`() {
        assertTrue(list(1L, item(1, done = true), item(2, done = true)).allDone)
    }

    @Test
    fun `还有一条没完成就不算完成`() {
        assertFalse(list(1L, item(1, done = true), item(2, done = false)).allDone)
    }

    // ---------- doneCount ----------

    @Test
    fun `doneCount 统计已完成条数`() {
        val l = list(1L, item(1, done = true), item(2, done = false), item(3, done = true))
        assertEquals(2, l.doneCount)
    }

    @Test
    fun `空清单进度是零`() {
        assertEquals(0, list(1L).doneCount)
    }

    // ---------- groupTodoLists ----------

    @Test
    fun `空输入分出两个空段`() {
        val sections = groupTodoLists(emptyList())
        assertTrue(sections.active.isEmpty())
        assertTrue(sections.completed.isEmpty())
    }

    @Test
    fun `未完成的进 active 已完成的进 completed`() {
        val a = list(1L, item(11))
        val b = list(2L, item(21, done = true))
        val sections = groupTodoLists(listOf(a, b))
        assertEquals(listOf(a), sections.active)
        assertEquals(listOf(b), sections.completed)
    }

    @Test
    fun `空清单归入 active 而不是已完成`() {
        val empty = list(1L)
        val sections = groupTodoLists(listOf(empty))
        assertEquals(listOf(empty), sections.active)
        assertTrue(sections.completed.isEmpty())
    }

    @Test
    fun `分组保持传入顺序不重排`() {
        // 需求 2「不改变它在列表中的位置」：分组是过滤器，不是排序器
        val a = list(3L, item(31))
        val b = list(1L, item(11, done = true))
        val c = list(2L, item(21))
        val sections = groupTodoLists(listOf(a, b, c))
        assertEquals(listOf(3L, 2L), sections.active.map { it.id })
        assertEquals(listOf(1L), sections.completed.map { it.id })
    }

    @Test
    fun `全部清单都完成时 active 为空`() {
        val sections = groupTodoLists(
            listOf(list(1L, item(11, done = true)), list(2L, item(21, done = true))),
        )
        assertTrue(sections.active.isEmpty())
        assertEquals(2, sections.completed.size)
    }
}

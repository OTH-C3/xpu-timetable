/*
 * NoteStateTest.kt —— 待办页的折叠状态判定（M12 需求五）
 *
 * 判定只有两行代码（isExpanded / Map.toggle），但它是需求 3「自动折叠」与
 * 「可再展开查看」之间唯一的仲裁点，写错的表现是"用户手动展开的清单，
 * 勾一项又自己折叠回去"，纯靠手点很难稳定复现，因此钉成单测。
 */
package com.gould.xputimetable.ui.note

import com.gould.xputimetable.domain.model.TodoItem
import com.gould.xputimetable.domain.model.TodoList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStateTest {

    private fun list(id: Long, allDone: Boolean) = TodoList(
        id = id,
        title = "清单$id",
        sortOrder = 0,
        items = if (allDone) {
            listOf(TodoItem(id = id * 10, text = "x", done = true, sortOrder = 0))
        } else {
            listOf(TodoItem(id = id * 10, text = "x", done = false, sortOrder = 0))
        },
    )

    // ---------- isExpanded ----------

    @Test
    fun `没手动管过且未完成则默认展开`() {
        assertTrue(isExpanded(list(1L, allDone = false), emptyMap()))
    }

    @Test
    fun `没手动管过且已整组完成则自动折叠`() {
        assertFalse(isExpanded(list(1L, allDone = true), emptyMap()))
    }

    @Test
    fun `手动折叠覆盖自动展开`() {
        val overrides = mapOf(1L to false)
        assertFalse(isExpanded(list(1L, allDone = false), overrides))
    }

    @Test
    fun `手动展开覆盖自动折叠`() {
        // 需求 3「可再展开查看」：整组完成后用户仍能手动打开
        val overrides = mapOf(1L to true)
        assertTrue(isExpanded(list(1L, allDone = true), overrides))
    }

    @Test
    fun `覆盖表只影响自己那一张清单`() {
        val overrides = mapOf(1L to false)
        assertFalse(isExpanded(list(1L, allDone = false), overrides))
        assertTrue(isExpanded(list(2L, allDone = false), overrides))
    }

    // ---------- Map.toggle ----------
    // 签名带 currentExpanded：以界面此刻的真实展开态为准取反。
    // 这么写是因为初始态有两个来源（自动判定 / 用户上次点过），
    // 函数内部无从得知当前显示的是什么，硬编码默认值会让「自动折叠的清单」第一下点击无效。

    @Test
    fun `已展开点一下变折叠`() {
        assertEquals(mapOf(1L to false), mapOf(1L to true).toggle(1L, currentExpanded = true))
    }

    @Test
    fun `已折叠点一下变展开`() {
        assertEquals(mapOf(1L to true), mapOf(1L to false).toggle(1L, currentExpanded = false))
    }

    @Test
    fun `空表点一下按当前显示态取反`() {
        // 自动判定为展开（未完成）→ 点一下折叠
        assertEquals(
            mapOf(1L to false),
            emptyMap<Long, Boolean>().toggle(1L, currentExpanded = true),
        )
    }

    @Test
    fun `自动折叠的清单第一下点击就能展开`() {
        // 回归：2026-10-04 真机实测「已完成」分组里那张卡片点一下没反应，要点两下。
        // 根因是 toggle 在表里没记录时假定当前是展开的，写出 false（仍折叠）。
        val list = list(1L, allDone = true)
        val before = emptyMap<Long, Boolean>()
        assertFalse(isExpanded(list, before)) // 初始：自动折叠

        val after = before.toggle(1L, currentExpanded = isExpanded(list, before))
        assertTrue(isExpanded(list, after)) // 点一下就该展开
    }

    @Test
    fun `取反不动其他条目`() {
        val before = mapOf(1L to true, 2L to false)
        assertEquals(mapOf(1L to false, 2L to false), before.toggle(1L, currentExpanded = true))
    }
}

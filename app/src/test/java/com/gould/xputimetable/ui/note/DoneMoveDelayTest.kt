/*
 * DoneMoveDelayTest.kt —— 「勾完最后一项要不要推迟落库」的判定测试（M13 需求 3）
 *
 * 需求原文：「答完后先出现彩带，然后再进入已完成」。
 *
 * 这条规则之所以要单测：判定错了有两种**都很难自查**的表现 ——
 *   漏判（该延迟没延迟）→ 彩带一帧都看不见，退化成 M12 那个"清单凭空消失"；
 *   误判（不该延迟却延迟）→ 用户连续勾选时每一下都粘 660ms，手感发木。
 * 两者都不崩溃、不报错，只有真机反复勾才能察觉。
 */
package com.gould.xputimetable.ui.note

import com.gould.xputimetable.domain.model.TodoItem
import com.gould.xputimetable.domain.model.TodoList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoneMoveDelayTest {

    /** 造一张清单：items 是 (id, done) 对。 */
    private fun list(id: Long, vararg items: Pair<Long, Boolean>) = TodoList(
        id = id,
        title = "清单$id",
        sortOrder = 0,
        items = items.map { (iid, done) ->
            TodoItem(id = iid, text = "x$iid", done = done, sortOrder = 0)
        },
    )

    // ---------- 典型场景 ----------

    @Test
    fun `勾最后一条会整组完成该延迟`() {
        val l = list(1L, 10L to true, 11L to true, 12L to false)
        assertTrue(wouldCompleteGroup(listOf(l), 12L))
    }

    @Test
    fun `勾中间一条不会整组完成不该延迟`() {
        val l = list(1L, 10L to true, 11L to false, 12L to false)
        assertFalse(wouldCompleteGroup(listOf(l), 11L))
    }

    @Test
    fun `单条清单勾它就是整组完成`() {
        val l = list(1L, 10L to false)
        assertTrue(wouldCompleteGroup(listOf(l), 10L))
    }

    @Test
    fun `还有其他未完成项时不该延迟`() {
        val l = list(1L, 10L to true, 11L to false, 12L to false)
        assertFalse(wouldCompleteGroup(listOf(l), 10L))
    }

    // ---------- 边界 ----------

    @Test
    fun `找不到的条目返回不延迟`() {
        // 兜底：宁可延迟错也别让彩带一帧不播
        assertFalse(wouldCompleteGroup(listOf(list(1L, 10L to false)), 99L))
    }

    @Test
    fun `空清单不延迟`() {
        // 空清单勾不出"整组完成"（与 allDone 判空的口径一致）
        assertFalse(wouldCompleteGroup(listOf(list(1L)), 10L))
    }

    @Test
    fun `多张清单时只认这条所在的那张`() {
        val a = list(1L, 10L to true, 11L to false)   // 勾 11 会完成
        val b = list(2L, 20L to false, 21L to false)  // 勾 20 不会完成
        assertTrue(wouldCompleteGroup(listOf(a, b), 11L))
        assertFalse(wouldCompleteGroup(listOf(a, b), 20L))
    }

    @Test
    fun `已完成区里的清单同样参与判定`() {
        // 需求 2 允许"取消完成"，取消后清单会回到进行中区。
        // 这时若再勾一条，判定必须看得到已完成区里的清单，不能只查 active。
        val done = list(1L, 10L to true, 11L to true)
        assertTrue(wouldCompleteGroup(listOf(done), 11L))
    }

    @Test
    fun `已全部完成的清单再勾一条也算整组完成`() {
        val l = list(1L, 10L to true, 11L to true)
        assertTrue(wouldCompleteGroup(listOf(l), 10L))
    }

    @Test
    fun `空列表不延迟`() {
        assertFalse(wouldCompleteGroup(emptyList(), 1L))
    }
}

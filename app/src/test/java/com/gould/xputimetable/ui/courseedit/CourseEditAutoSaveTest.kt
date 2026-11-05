/*
 * CourseEditAutoSaveTest.kt —— 即时保存的两个纯函数真值表
 *
 * 把"什么时候落库"抽成 shouldAutoSave / shouldFlushOnExit 后，这里穷举钉死它们的全部组合，
 * 免得以后有人改分支顺序或阈值导致"改到一半就落库""校验不过仍写入"之类回退。
 */
package com.gould.xputimetable.ui.courseedit

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEditAutoSaveTest {

    // ---------- shouldAutoSave：fieldChanged ∧ settled ∧ valid（2³ = 8 组合）----------

    @Test
    fun `shouldAutoSave 全部八种组合与真值表一致`() {
        for (fieldChanged in listOf(false, true)) {
            for (settled in listOf(false, true)) {
                for (valid in listOf(false, true)) {
                    val expected = fieldChanged && settled && valid
                    assertEquals(
                        "fieldChanged=$fieldChanged, settled=$settled, valid=$valid",
                        expected,
                        shouldAutoSave(fieldChanged, settled, valid),
                    )
                }
            }
        }
    }

    @Test
    fun `字段刚改还没停顿不保存`() {
        // 正在连续输入（settled=false），哪怕内容合法也不落库，避免半截值入库
        assertEquals(false, shouldAutoSave(fieldChanged = true, settled = false, valid = true))
    }

    @Test
    fun `停顿且校验通过才保存`() {
        assertEquals(true, shouldAutoSave(fieldChanged = true, settled = true, valid = true))
    }

    @Test
    fun `校验不过不保存且有提示`() {
        // settled=true 但 valid=false：不落库，错误由界面红框提示（不导航）
        assertEquals(false, shouldAutoSave(fieldChanged = true, settled = true, valid = false))
    }

    @Test
    fun `没改动不保存`() {
        assertEquals(false, shouldAutoSave(fieldChanged = false, settled = true, valid = true))
    }

    // ---------- shouldFlushOnExit：hasUnsavedChanges ∧ valid（2² = 4 组合）----------

    @Test
    fun `shouldFlushOnExit 全部四种组合与真值表一致`() {
        for (hasUnsaved in listOf(false, true)) {
            for (valid in listOf(false, true)) {
                assertEquals(hasUnsaved && valid, shouldFlushOnExit(hasUnsaved, valid))
            }
        }
    }

    @Test
    fun `退出时有脏改动且合法则强制刷库`() {
        // 无视"是否已停顿"，把 debounce 窗口内最后一笔也写进去，退出不丢改动
        assertEquals(true, shouldFlushOnExit(hasUnsavedChanges = true, valid = true))
    }

    @Test
    fun `退出时无脏改动不写库`() {
        assertEquals(false, shouldFlushOnExit(hasUnsavedChanges = false, valid = true))
    }

    @Test
    fun `校验不过退出不刷库停在页内`() {
        // 内容非法时退出也不落库、不导航，用户留在页内看到错误
        assertEquals(false, shouldFlushOnExit(hasUnsavedChanges = true, valid = false))
    }
}

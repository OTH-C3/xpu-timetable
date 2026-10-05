/*
 * BackgroundPrefsTest.kt —— 作用范围从单值改成集合后的**向后兼容**单测
 *
 * 这条迁移是全这次改动里唯一"老用户看不见任何提示、只会发现自己背景图没了"的地方，
 * 而没有真机可以逐个老版本验证，只能靠单测把三条分支都钉住：
 *   1. 只有老键 → 老值当单元素集合（老用户背景不消失）；
 *   2. 只有新键 → 以新键为准；
 *   3. 两个键都没有 → 缺省全局。
 */
package com.gould.xputimetable.data.prefs

import com.gould.xputimetable.ui.background.BackgroundScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPrefsTest {

    @Test
    fun `只有老的单值时当成单元素集合`() {
        // 2026-10-04 之前所有存档都长这样："TIMETABLE"
        val parsed = parseScopes(stored = null, legacy = BackgroundScope.TIMETABLE.name)
        assertEquals(setOf(BackgroundScope.TIMETABLE), parsed)
    }

    @Test
    fun `老值可能不是全局所以不能一律兜成全局`() {
        // 当年选「便签」的用户，升级后读出来的必须是「便签」，
        // 兜成全局等于偷偷给他把背景铺到了别的页面上
        assertEquals(
            setOf(BackgroundScope.NOTE),
            parseScopes(null, BackgroundScope.NOTE.name),
        )
    }

    @Test
    fun `新键存在时老键被无视`() {
        // 用户已经在新版里改过勾选 → 老键里的值已经过期，不能被翻旧账
        assertEquals(
            setOf(BackgroundScope.PROFILE, BackgroundScope.NOTE),
            parseScopes(setOf("PROFILE", "NOTE"), BackgroundScope.TIMETABLE.name),
        )
    }

    @Test
    fun `两个键都没有时是全局`() {
        assertEquals(DEFAULT_SCOPES, parseScopes(null, null))
        assertTrue(DEFAULT_SCOPES.contains(BackgroundScope.GLOBAL))
    }

    @Test
    fun `空集合表示一张都不画不会被当成没设置过`() {
        // 用户把四档全取消是合法状态，读回来必须是空集（shouldDrawBackground 会判 false）；
        // 只有"两个键都不存在"才走 DEFAULT_SCOPES
        assertEquals(emptySet<String>(), parseScopes(emptySet(), null))
    }

    @Test
    fun `存进去的集合读出来顺序不变`() {
        // DataStore 存的是 StringSet，不保证顺序；判定用的是 contains，本用例钉住这个契约
        assertEquals(
            setOf(BackgroundScope.GLOBAL, BackgroundScope.TIMETABLE),
            parseScopes(setOf("GLOBAL", "TIMETABLE"), null),
        )
    }

    @Test
    fun `认不出的枚举名被丢掉而不是炸掉`() {
        // 老版本删掉过枚举成员时，老数据里可能留下悬空的名字（如 "LEGACY_SOMETHING"）。
        // 两条要求同时成立：① 读取路径绝不能抛异常（抛了会把整个背景设置带崩，
        // 用户看到的是"进设置页就闪退"）；② 那个名字被丢掉 —— 结果是空集，
        // 含义是"一个页面都不画背景"，UI 上四项全没勾，一眼能看出发生了什么。
        //
        // 为什么不回落成 GLOBAL：那样会让用户的背景图**凭空出现在他没选过的页面上**。
        // "整张图消失"比"多出现在一个页面"更容易被察觉，也更容易在设置里改回来。
        assertEquals(
            emptySet<BackgroundScope>(),
            parseScopes(null, "LEGACY_SOMETHING"),
        )
    }

    @Test
    fun `新键里混着悬空名字时只保留合法的`() {
        // 多选之后更可能出现"部分有效"：用户勾了三档，其中一档的枚举后来被删。
        // 此时要保住**另外两档**（不能因为一个坏值把整个设置清空），
        // 只把那一个丢掉。
        assertEquals(
            setOf(BackgroundScope.TIMETABLE, BackgroundScope.NOTE),
            parseScopes(setOf("TIMETABLE", "GONE", "NOTE"), null),
        )
    }
}

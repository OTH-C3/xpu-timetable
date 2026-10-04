/*
 * TodoList.kt —— 待办领域模型与分组规则（M12 需求五）
 *
 * 需求 3：「当一组待办全部完成时，该清单自动折叠并归类到"已完成"分组，可再展开查看。」
 * 这句话拆成三个可判定的条件，全部收在 [groupTodoLists] 这一个**纯函数**里：
 *   1. 清单内**全部**条目都已完成 → 归入"已完成"分组；
 *   2. 全部完成的清单**默认折叠**（needsAutoCollapse = true）；
 *   3. 空清单（一条都没有）**不算完成** —— 否则刚点"新建清单"它就跳进已完成区，
 *      用户会以为创建失败（这是本项目最看重的一类"静默错误"）。
 *
 * 为什么把规则做成纯函数而不是写在 Composable 里：
 *   分组/折叠是需求里最容易出回归的一条（"勾最后一项时清单突然消失"），
 *   纯函数能直接用单测把这些边界钉死，不需要起 Compose 界面。
 */
package com.gould.xputimetable.domain.model

/** 一条待办条目。 */
data class TodoItem(
    val id: Long,
    val text: String,
    val done: Boolean,
    val sortOrder: Int,
)

/** 一个待办清单（一张"便签"）+ 它的条目。 */
data class TodoList(
    val id: Long,
    val title: String,
    val sortOrder: Int,
    val items: List<TodoItem>,
) {
    /** 已完成条数 / 总条数（空清单返回 0/0，界面上不画进度）。 */
    val doneCount: Int get() = items.count { it.done }

    /**
     * 「整组已完成」= 至少有一条、且全部完成。
     * 空清单刻意判 false（理由见文件头第 3 条）。
     */
    val allDone: Boolean get() = items.isNotEmpty() && items.all { it.done }
}

/** 列表页的两段结构：进行中 / 已完成。 */
data class TodoSections(
    val active: List<TodoList>,
    val completed: List<TodoList>,
)

/** 空清单时的界面占位名（不落库，只在显示时兜底）。 */
const val UNTITLED_LIST = "未命名清单"

/**
 * 按"是否整组完成"把清单分成两段（纯函数，单测覆盖）。
 *
 * 保持传入顺序：清单序来自 sortOrder，用户手动排过序，不该被这里重排。
 */
fun groupTodoLists(lists: List<TodoList>): TodoSections = TodoSections(
    active = lists.filter { !it.allDone },
    completed = lists.filter { it.allDone },
)

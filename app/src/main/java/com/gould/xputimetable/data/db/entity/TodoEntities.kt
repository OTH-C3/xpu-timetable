/*
 * TodoEntities.kt —— 待办清单与条目的 Room 实体（M12 需求五）
 *
 * 为什么是两张表而不是一张：
 *   需求 3 要求"当**一组**待办全部完成时，该清单自动折叠并归类到『已完成』分组"——
 *   "一组"必须是个能独立存在、独立排序的实体，也就是"清单"。如果只有一张条目表，
 *   就无法表达"这几条属于同一张清单"，自动折叠与分组都无从谈起。
 *   （对照小米便签：一张便签 = 一个清单 + 若干勾选项，本项目只做清单与勾选，
 *     不做便签下方的富文本笔记 —— 这是老大明确划出的范围。）
 *
 * 外键：todo_items.list_id → todo_lists.id，ON DELETE CASCADE
 *   删除清单时条目一并清掉，不留孤儿行。索引按 list_id 建：
 *   待办页每次只按清单取条目，这是唯一的查询入口。
 *
 * 排序：sortOrder 是**显式整数**而不是靠 createdAt 兜底。
 *   条目可以在完成后仍留在原位（需求 2 明确"不改变它在列表中的位置"），
 *   将来若加"上移/下移"，需要的是一个稳定可写的序号；用时间戳做不到。
 */
package com.gould.xputimetable.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** 待办清单（一张"便签"）。 */
@Entity(tableName = "todo_lists")
data class TodoListEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 清单标题（可为空串，空串时界面用"未命名清单"占位，不额外存一份默认值）。 */
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** 待办条目（清单里的一行勾选项）。 */
@Entity(
    tableName = "todo_items",
    foreignKeys = [
        ForeignKey(
            entity = TodoListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_todo_items_list_id", value = ["list_id"])],
)
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "list_id")
    val listId: Long,

    @ColumnInfo(name = "text")
    val text: String,

    /** 是否已完成。用 INTEGER(0/1) 而非 Boolean：SQLite 没有布尔类型，Room 自动转换。 */
    @ColumnInfo(name = "done")
    val done: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

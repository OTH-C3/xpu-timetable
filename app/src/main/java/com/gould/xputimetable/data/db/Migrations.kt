/*
 * Migrations.kt —— 数据库迁移（版本升级脚本集中处）
 *
 * 铁律：改实体字段 ⇒ 必须升 `AppDatabase.version` 并在此补一条迁移；否则已装旧版的设备会命中
 *   IllegalStateException: Room cannot verify the data integrity ...
 * （真机+旧库才会暴露，构建与单测都是绿的）
 *
 * Room 3 的迁移签名（与 Room 2 教程不同，已用 javap 核对）：
 *   迁移体是**挂起函数**，接收 `androidx.sqlite.SQLiteConnection`，用 `execSQL` 执行 DDL。
 */
package com.gould.xputimetable.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/**
 * 1 → 2：course_sessions 新增 `week_list`（显式周次列表，CSV；NULL 表示沿用区间+单双周判定）。
 * 加的是可空列，ALTER TABLE 不影响既有一行数据 —— 用户已录入的课程完整保留。
 */
val MIGRATION_1_2: Migration = Migration(1, 2) { connection ->
    connection.execSQL("ALTER TABLE course_sessions ADD COLUMN week_list TEXT")
}

/**
 * 2 → 3：新增待办清单与待办条目两张表（M12 需求五）。
 *
 * 为什么加表也必须写迁移：本项目的 AppContainer 同时挂着 fallbackToDestructiveMigration()，
 * 但那张"安全网"只在**找不到迁移**时兜底；一旦声明了 MIGRATION_2_3，Room 就会按它执行。
 * 建表语句必须与 TodoEntities.kt 的实体定义逐字对应（列名、类型、外键、索引），
 * 否则真机启动会命中 `Room cannot verify the data integrity`（构建与单测都是绿的）。
 *
 * ⚠️ 外键顺序：先建 todo_lists 再建 todo_items（被引用方必须先存在），
 * 且 todo_items 的外键在 SQLite 里是「表存在时才校验」，但保持正确顺序更利于排查。
 */
val MIGRATION_2_3: Migration = Migration(2, 3) { connection ->
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `todo_lists` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL,
            `sort_order` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `todo_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `list_id` INTEGER NOT NULL,
            `text` TEXT NOT NULL,
            `done` INTEGER NOT NULL,
            `sort_order` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            FOREIGN KEY(`list_id`) REFERENCES `todo_lists`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_todo_items_list_id` ON `todo_items` (`list_id`)",
    )
}

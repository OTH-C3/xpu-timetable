/*
 * AppDatabase.kt —— Room 数据库定义
 *
 * 作用：声明数据库包含哪些表（实体）、版本号，以及对外暴露的 DAO。
 *
 * Room 3 要点（与 Room 2 教程不同，已实测核实）：
 *   - 注解与类都来自 androidx.room3 包；
 *   - 建库必须显式提供 SQLiteDriver（Room 3 已移除 SupportSQLite），见 AppContainer 中的
 *     setDriver(BundledSQLiteDriver())；
 *   - exportSchema = true：把表结构导出到 app/schemas（架构 §7.4：Schema JSON 入库）。
 *
 * 迁移策略（架构 §7.4 原写「v2 起必须写 AutoMigration」，实际执行有偏差，如实记录）：
 *   - 手写迁移放在 `data/db/Migrations.kt`，在 AppContainer 用 addMigrations 注册；
 *   - 为什么不用 AutoMigration：Room 3.0.3 的 AutoMigration 处理器在 KSP 阶段会抛
 *     `ClassCastException: Integer cannot be cast to Boolean`（本项目实测），故走手写。
 *
 * 教训（2026-09-17 真机踩坑，值得逐字读）：
 *   改实体字段却**忘记升 version** 时，已装旧版的设备启动会命中
 *     IllegalStateException: Room cannot verify the data integrity ...
 *   —— 构建与单测全绿，只有「真机 + 已存在旧库」才会暴露，所以这是必须靠真机验证才能发现的缺陷。
 *   另：编辑本文件时务必保证 @Database 注解里**只有一个 version 参数**，且全文件只有一个
 *   @Database 注解——本项目曾因手改插入造成注解结构损坏，KSP 报出的正是上面那个
 *   ClassCastException，极具误导性，一度被误判为「Room 不支持升版本」。
 */
package com.gould.xputimetable.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.gould.xputimetable.data.db.dao.CourseDao
import com.gould.xputimetable.data.db.dao.CourseSessionDao
import com.gould.xputimetable.data.db.dao.ImportLogDao
import com.gould.xputimetable.data.db.dao.TermDao
import com.gould.xputimetable.data.db.dao.TimeSlotDao
import com.gould.xputimetable.data.db.dao.TodoDao
import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import com.gould.xputimetable.data.db.entity.ImportLogEntity
import com.gould.xputimetable.data.db.entity.TermEntity
import com.gould.xputimetable.data.db.entity.TimeSlotEntity
import com.gould.xputimetable.data.db.entity.TodoItemEntity
import com.gould.xputimetable.data.db.entity.TodoListEntity

@Database(
    // 版本历史：1 = M1 初版；2 = course_sessions 新增 week_list（2026-09-17）；
    //            3 = 新增 todo_lists / todo_items 待办两表（M12 需求五，脚本见 Migrations.kt）
    version = 3,
    entities = [
        CourseEntity::class,
        CourseSessionEntity::class,
        TermEntity::class,
        TimeSlotEntity::class,
        ImportLogEntity::class,
        TodoListEntity::class,
        TodoItemEntity::class,
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao

    abstract fun courseSessionDao(): CourseSessionDao

    abstract fun termDao(): TermDao

    abstract fun timeSlotDao(): TimeSlotDao

    abstract fun importLogDao(): ImportLogDao

    /** M12 需求五：待办清单 + 条目（同一个 DAO，两张表都是它的入口）。 */
    abstract fun todoDao(): TodoDao
}

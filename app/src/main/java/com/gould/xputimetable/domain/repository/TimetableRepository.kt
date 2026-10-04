/*
 * TimetableRepository.kt —— 课表数据仓库接口（领域层契约）
 *
 * 作用：这是 UI / ViewModel 与数据层之间唯一的"门面"接口。上层只通过它拿数据、
 * 改数据，绝不直接碰 Room / DAO。这样数据层（Room、SQLite）的替换或重构不会
 * 波及界面层。
 *
 * 签名来源：严格取自架构文档 §8.1 / Spec §5.1，改动等同破坏性 API 变更，
 * 必须先改文档再改代码（活规格纪律）。
 *
 * 实现归属：本文件只声明接口，实现位于 data/repository/TimetableRepositoryImpl.kt。
 * 本接口引用的 ParsedSchedule / ImportSummary 见 domain/model/ImportTypes.kt
 * 的占位定义（导入阶段会以 parser/api 的正式定义替换）。
 *
 * 几个返回类型说明：
 *   - 观察类方法返回 Flow<T>：数据流，数据库变化会主动推送给订阅者，周视图/小组件据此自动刷新；
 *   - 写操作是 suspend：在协程里同步等待落库完成。
 */
package com.gould.xputimetable.domain.repository

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.domain.model.WeekSchedule
import com.gould.xputimetable.data.transfer.ScheduleSnapshotDto
import kotlinx.coroutines.flow.Flow

/**
 * 课表数据访问的统一契约。上层（UI / ViewModel）只依赖此接口。
 */
interface TimetableRepository {
    /** 周视图数据源：给定学期与周次，观察该周全部课程安排。 */
    fun observeWeek(termId: Long, week: Int): Flow<WeekSchedule>

    /** 当前激活学期（无则 null，UI 显示引导页）。 */
    fun observeActiveTerm(): Flow<Term?>

    fun observeTimeSlots(): Flow<List<TimeSlot>>
    suspend fun updateTimeSlot(slot: TimeSlot)

    /**
     * 新增/更新学期，返回学期 id。
     *
     * 阶段限制：MVP 采用"单一激活学期"模型，因此当库中**还没有任何激活学期**时，
     * 本方法会把传入学期强制置为激活（保证首次使用能直接开始）；多学期管理属 Out-of-Scope
     * （见 Spec §3），届时需在设置页提供显式的"切换/停用"操作并补对应的 DAO 方法。
     */
    suspend fun upsertTerm(term: Term): Long

    /** 手动添加/编辑课程（含其全部上课安排，整体替换）。 */
    suspend fun upsertCourse(course: Course, sessions: List<CourseSession>)

    suspend fun deleteCourse(courseId: String)

    /**
     * 某门课的全部上课安排（编辑用）。
     *
     * 为什么编辑场景必须有它：upsertCourse 是"整体替换"——编辑页必须先取到该课程
     * 的**全部**安排，改其中被点开的那条、保留其余，再整体写回；否则只写一条会把
     * 这门课的其它安排（如周三 1-2 节 + 周五 3-4 节两处）静默丢掉。
     */
    suspend fun getSessionsByCourseId(courseId: String): List<CourseSession>

    /**
     * 确保作息表有预置数据（幂等：已有作息则不写入）。
     *
     * 用途：首次安装或清库后立即补齐西工程大标准作息，避免"时间轴空白 + 提醒永远排不出"。
     * 用户后续可在设置页修改，修改后本方法不会再覆盖（因为表不为空）。
     */
    suspend fun ensureDefaultTimeSlots()

    /** 按 id 取课程（编辑页回填表单用）。 */
    suspend fun getCourseById(courseId: String): Course?

    /**
     * 所有导入通道的统一入库入口。事务写入并记录 import_logs。
     *
     * [mode] 决定如何对待库里已有的课程（M7 需求 1）：
     *   - [ImportMode.REPLACE]（默认）：先清掉当前学期**全部非手动**来源的课程，再写入本次内容——
     *     课表变成「本次导入的内容 + 手动添加的课程」；
     *   - [ImportMode.APPEND]：不清任何东西，本次课程追加进现有课表——
     *     课表变成「原有内容 + 本次导入的内容」，代价是重复导入会产生重复课程。
     *
     * 两种模式下 MANUAL 来源的课程都永不删除（AC-20 双防线不随 mode 改变）。
     */
    suspend fun applyImport(
        schedule: ParsedSchedule,
        mode: ImportMode = ImportMode.REPLACE,
    ): ImportSummary

    /**
     * 某学期某来源的现有课程（M2-A 增补，导入预览页专用）。
     *
     * 用途：预览页在用户确认**之前**就要展示覆盖范围与疑似重复（AC-13/AC-21）：
     *   - source = WAKEUP_CSV → 计数「将替换 N 门同来源课程」；
     *   - source = MANUAL → 同名比对「疑似重复 N 门」。
     * 实现侧只读（courseDao.getAll 过滤），不写库、不新加 DAO 方法。
     */
    suspend fun getCoursesByTermAndSource(termId: Long, source: String): List<Course>

    /**
     * 删除某学期某来源的课程，返回删除数量（M2-C，AC-24「按来源清理」）。
     *
     * 用途：导入中心提供「清理导入的课程」入口，清掉误导入或测试数据
     * （真实场景：导错学期、导错文件）。
     *
     * 安全不变量：MANUAL 永不删除——代码层直接拒绝（source == MANUAL 返回 0），
     * DAO 层 SQL 还有 `source != 'MANUAL'` 硬兜底（与 applyImport 的双防线一致，AC-20）。
     * 安排由 course_sessions 的 ON DELETE CASCADE 级联清理。
     */
    suspend fun deleteCoursesByTermAndSource(termId: Long, source: String): Int

    /**
     * 导出当前激活学期的完整课表快照（M6 需求 6，文件与二维码共用）。
     * 无激活学期 → 返回 null（UI 提示"还没有课表"）。
     * 有学期但无课程 → 返回空快照（courses 为空列表），**不要**返回 null（可导出，便于清空后同步）。
     */
    suspend fun exportSnapshot(): ScheduleSnapshotDto?
}

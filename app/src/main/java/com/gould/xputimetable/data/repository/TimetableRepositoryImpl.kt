/*
 * TimetableRepositoryImpl.kt —— TimetableRepository 的唯一实现（数据层门面落地）
 *
 * 作用：把领域层约定的仓储方法真正接到 Room 上。UI / ViewModel 只依赖接口，
 * 本类是唯一"懂 Room / SQL"的地方；要换存储（如云端）只需换实现，不影响界面层。
 *
 * 关键实现纪律：
 *   1. 周视图过滤（周次区间 + 单双周 + 学期）放在 SQL 里做（CourseSessionDao.observeWeekSessions），
 *      不在 JVM 里逐条判断，省内存也省电；
 *   2. 写操作里的"多步成组"用 Room 3 的事务（TransactionRunner 包装 withWriteTransaction）。
 *      事务保证 upsertCourse / applyImport 要么全成、要么全败，避免"课程写进去了、安排却没写"的半成品；
 *   3. week_type 字符串 <-> WeekType 枚举的翻译只发生在边界（Mappers），本类不直接拼字符串。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.dao.CourseDao
import com.gould.xputimetable.data.db.dao.CourseSessionDao
import com.gould.xputimetable.data.db.dao.ImportLogDao
import com.gould.xputimetable.data.db.dao.TermDao
import com.gould.xputimetable.data.db.defaultTimeSlots
import com.gould.xputimetable.data.db.dao.TimeSlotDao
import com.gould.xputimetable.data.transfer.ScheduleCodec
import com.gould.xputimetable.data.transfer.ScheduleSnapshotDto
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.domain.model.isValidColorTag
import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.domain.model.WeekSchedule
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class TimetableRepositoryImpl(
    private val courseDao: CourseDao,
    private val courseSessionDao: CourseSessionDao,
    private val termDao: TermDao,
    private val timeSlotDao: TimeSlotDao,
    private val importLogDao: ImportLogDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val tx: TransactionRunner,
) : TimetableRepository {

    override fun observeWeek(termId: Long, week: Int): Flow<WeekSchedule> =
        courseSessionDao.observeWeekSessions(termId, week).map { it.toWeekSchedule(week) }

    override fun observeActiveTerm(): Flow<Term?> =
        termDao.observeActive().map { it?.toDomain() }

    override fun observeTimeSlots(): Flow<List<TimeSlot>> =
        timeSlotDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun updateTimeSlot(slot: TimeSlot) {
        require(slot.section >= 1) { "节次号必须大于 0，实际 ${slot.section}" }
        require(slot.startMinute in 0..1439 && slot.endMinute in 0..1439) {
            "节次时间的分钟数必须在 0..1439 之间（实际 ${slot.startMinute}..${slot.endMinute}）"
        }
        require(slot.startMinute < slot.endMinute) { "开始时间必须早于结束时间" }
        timeSlotDao.update(slot.toEntity())
    }

    override suspend fun upsertTerm(term: Term): Long {
        val hasActive = termDao.observeActive().first() != null
        val toSave = if (hasActive) term else term.copy(isActive = true)
        val entity = toSave.toEntity()
        val exists = entity.id != 0L && termDao.getById(entity.id) != null
        return if (exists) {
            // 已有学期走 UPDATE：裸 @Insert 默认 ABORT 会撞主键（SQLite 1555）；
            // 不能用 REPLACE——terms 被级联删除会把整学期课表清空（ON DELETE CASCADE）
            termDao.update(entity)
            entity.id
        } else {
            termDao.insert(entity)
        }
    }

    /**
     * 新增或整体替换一门课及其安排。
     *
     * 注意："整体替换"意味着调用方必须传入该课程的**全部**安排；编辑页因此要先
     * getSessionsByCourseId 取全量再改其中一条（见 CourseEditViewModel），否则会丢课。
     */
    override suspend fun upsertCourse(course: Course, sessions: List<CourseSession>) {
        // 输入校验（界面已校验一次，这里是数据层兜底：任何调用方传坏数据都在此明确失败，
        // 而不是写进库里变成"看起来正常但渲染不出来"的脏数据）
        require(course.name.isNotBlank()) { "课程名不能为空" }
        require(course.termId > 0) { "课程必须归属于某个学期（termId 必须大于 0）" }
        // 颜色这一列两种合法值：老数据的离散索引（0..11，**非负**）与取色器自取的 ARGB（满透明时
        // 写成 Int 是**负数**）。原来这里只判 >= 0，自取色的课会在数据层直接抛异常、根本存不进去。
        require(isValidColorTag(course.colorTag)) {
            "课程颜色值非法：${course.colorTag}（既不是色板索引也不是 ARGB）"
        }
        sessions.forEach { session ->
            require(session.courseId == course.id) {
                "上课安排的 courseId（${session.courseId}）与课程 id（${course.id}）不一致"
            }
            require(session.dayOfWeek in 1..7) { "星期必须在 1..7 之间，实际 ${session.dayOfWeek}" }
            require(session.startSection >= 1 && session.endSection >= session.startSection) {
                "节次区间不合法：${session.startSection}-${session.endSection}"
            }
            require(session.startWeek >= 1 && session.endWeek >= session.startWeek) {
                "周次区间不合法：${session.startWeek}-${session.endWeek}"
            }
        }
        tx.run {
            courseDao.insert(course.toEntity())
            // 先清掉该课程的全部旧安排，再写入新的（REPLACE 在外键 CASCADE 下不会替我们清理多余安排）
            courseSessionDao.deleteByCourseId(course.id)
            if (sessions.isNotEmpty()) courseSessionDao.insertAll(sessions.map { it.toEntity() })
        }
    }

    override suspend fun deleteCourse(courseId: String) {
        courseDao.deleteById(courseId) // 安排由 course_sessions 的外键 ON DELETE CASCADE 级联删除
    }

    override suspend fun getSessionsByCourseId(courseId: String): List<CourseSession> =
        courseSessionDao.observeByCourseId(courseId).first().map { it.toDomain() }

    override suspend fun ensureDefaultTimeSlots() {
        // 幂等且可**增量补齐**：按节次对齐，只补缺失的节；已存在的节一律不动
        // （用户改过的作息不会被覆盖）。
        // 为什么不能只在"表为空"时写入：默认作息从 10 节扩到 12 节后，
        // 老用户的库里已经有 10 条 → 表非空 → 永远补不上 11/12 节（即用户报告的
        // "11、12 节不显示时间"）。改成按节次差集补，才对新老库都成立。
        val existingSections = timeSlotDao.observeAll().first().map { it.section }.toSet()
        val missing = defaultTimeSlots.filter { it.section !in existingSections }
        if (missing.isNotEmpty()) timeSlotDao.insertAll(missing.map { it.toEntity() })
    }

    override suspend fun getCourseById(courseId: String): Course? =
        courseDao.getById(courseId)?.toDomain()

    override suspend fun getCoursesByTermAndSource(termId: Long, source: String): List<Course> =
        courseDao.getAll().filter { it.termId == termId && it.source == source }.map { it.toDomain() }

    override suspend fun deleteCoursesByTermAndSource(termId: Long, source: String): Int = tx.run {
        // 双防线第 1 层（代码）：MANUAL 直接拒绝；DAO SQL 的 `source != 'MANUAL'` 是第 2 层
        if (source == CourseSource.MANUAL) return@run 0
        val count = courseDao.countByTermAndSource(termId, source)
        if (count == 0) return@run 0
        courseDao.deleteImportedByTermAndSource(termId, source)
        count
    }

    /**
     * 导入入库（所有导入通道的统一入口）。
     *
     * 覆盖范围遵循 Spec AC-13 的裁决：**按「学期 + 来源」整体替换**，绝不整学期清空。
     * 保护手动数据（AC-20 / AC-22）的两道防线：
     *   ① 代码层：把 MANUAL 从可替换来源中过滤，并跳过与用户已有课程同 id 的导入课程
     *      （否则 insertAll 的 REPLACE 会静默覆盖用户修改）；
     *   ② SQL 层：CourseDao.deleteImportedByTermAndSource 内还有 `source != 'MANUAL'` 兜底。
     */
    override suspend fun applyImport(schedule: ParsedSchedule, mode: ImportMode): ImportSummary {
        return tx.run {
            val targetTermId = schedule.term?.id
                ?: termDao.observeActive().first()?.id
                ?: throw IllegalStateException("applyImport 无法确定目标学期：ParsedSchedule.term 为空且不存在激活学期")

            val courseEntities = schedule.toCourseEntities(targetTermId)
            val sessionEntities = schedule.toSessionEntities()

            // 防线一：找出已被用户编辑过（MANUAL）的课程 id，导入版本遇到同 id 时保留用户版本
            val userOwnedIds = courseDao.getIdsByTermAndSource(targetTermId, CourseSource.MANUAL).toSet()
            val (userOwned, writableCourses) = courseEntities.partition { it.id in userOwnedIds }

            // 按来源替换（不含 MANUAL）
            // M7 需求 1：「覆盖原课表」= 清掉当前学期**全部非手动**来源的课程，用本次内容重建。
            // 为什么不是"只清同来源"：用户选「覆盖」的语义就是"课表变成这份内容"；
            // 若只清同来源，别的通道导入的课程会留下来，与本次内容同名并列——正是"同课重复"的来源
            // （真机实测：教务导入 9 门 + 文件导入 9 门，内容相同却各占一份）。
            // MANUAL 仍受保护：它不在清理列表里，AC-20 的双防线不随 mode 改变。
            // APPEND 模式跳过整段，直接追加。
            var replacedCount = 0
            if (mode == ImportMode.REPLACE) {
                val existingSources = courseDao.getByTerm(targetTermId)
                    .map { it.source }
                    .filter { it != CourseSource.MANUAL }
                    .distinct()
                existingSources.forEach { source ->
                    replacedCount += courseDao.countByTermAndSource(targetTermId, source)
                    courseDao.deleteImportedByTermAndSource(targetTermId, source)
                }
            }

            if (writableCourses.isNotEmpty()) courseDao.insertAll(writableCourses)
            if (sessionEntities.isNotEmpty()) courseSessionDao.insertAll(sessionEntities)

            // AC-20 的机器证据：导入后对手动课程做真实计数（不推算）
            val preservedManualCount = courseDao.countByTermAndSource(targetTermId, CourseSource.MANUAL)

            // AC-21：需要提示的用户数据（同 id 被保留 + 同名冲突），只提示不自动合并
            val manualNames = courseDao.getNamesByTermAndSource(targetTermId, CourseSource.MANUAL).toSet()
            val idCollisionNames = userOwned.map { it.name }
            val nameCollisionNames = writableCourses.map { it.name }.filter { it in manualNames }
            val suspectedDuplicateNames = (idCollisionNames + nameCollisionNames).distinct()

            val courseCount = writableCourses.size
            val sessionCount = sessionEntities.size
            val success = writableCourses.isNotEmpty() || userOwned.isNotEmpty()
            val status = if (success) "SUCCESS" else "FAILED(empty)"

            importLogDao.insert(
                schedule.toImportLog(
                    status = status,
                    courseCount = courseCount,
                    createdAt = System.currentTimeMillis(),
                    replacedCount = replacedCount,
                    preservedManualCount = preservedManualCount,
                ),
            )

            schedule.toImportSummary(
                success = success,
                courseCount = courseCount,
                sessionCount = sessionCount,
                replacedCount = replacedCount,
                preservedManualCount = preservedManualCount,
                suspectedDuplicateNames = suspectedDuplicateNames,
            )
        }
    }


    /**
     * 导出当前激活学期的完整课表快照（M6 需求 6）。
     * 只取激活学期数据：课程走新增的 getByTerm（一次性读取）；
     * 安排复用 getAll 按本学期课程 id 过滤（9 门课/16 条安排量级，不新建 JOIN 查询）；
     * DTO 组装集中在 ScheduleCodec.fromDomain，本方法只负责取数。
     */
    override suspend fun exportSnapshot(): ScheduleSnapshotDto? {
        val term = termDao.observeActive().first() ?: return null
        val courseEntities = courseDao.getByTerm(term.id)
        val courseIds = courseEntities.map { it.id }.toSet()
        val sessionEntities = courseSessionDao.getAll().filter { it.courseId in courseIds }
        val slotEntities = timeSlotDao.observeAll().first()
        return ScheduleCodec.fromDomain(
            term = term.toDomain(),
            courses = courseEntities.map { it.toDomain() },
            sessions = sessionEntities.map { it.toDomain() },
            slots = slotEntities.map { it.toDomain() },
        )
    }
}

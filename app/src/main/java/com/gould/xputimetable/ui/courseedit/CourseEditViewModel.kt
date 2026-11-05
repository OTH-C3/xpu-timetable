/*
 * CourseEditViewModel.kt —— 课程编辑/新增的状态与保存逻辑
 *
 * 保存时遵守两条不变量：
 *   1. **不丢课**：upsertCourse 是整体替换，所以编辑时必须带上该课程的**全部**安排
 *      （其余安排保留，只改被编辑的那条）。
 *   2. **用户编辑即受保护（AC-22）**：保存既有课程时调用 Course.markEdited，
 *      把 source 置为 MANUAL —— 此后任何来源的导入都不会删掉或覆盖它。
 *
 * 性能约定（2026-09-17 优化）：**每个表单字段各有一个 setter 方法**，界面通过方法引用
 * （viewModel::setName）传入回调。原因：Kotlin 内联 lambda 每次重组都会新建实例，
 * 对 Compose 而言是"不稳定参数"，会导致子组件无法跳过重组——原来一个输入框击键会让
 * 整张表单（含 12 个色点、多组标签）全部重组，标签上浮动画因此掉帧。
 *
 * 健壮性约定：所有仓库调用都包在 runCatching 里，失败转成可读中文错误写入 draft.error，
 * 绝不让异常冒泡到 viewModelScope（那会直接崩溃）。校验在 save() 里集中做，失败给出明确原因。
 *
 * M12：CourseDraft / DraftField / 校验 / 长度上限已拆到同包 CourseEditDraft.kt
 * （本文件原为 297 行，加备注字段必然破 300 行门禁）。
 */
package com.gould.xputimetable.ui.courseedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.domain.model.isValidColorTag
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CourseEditViewModel(
    private val repository: TimetableRepository,
    /** 保存/删除属数据变更（M3 §4.5）：重排小组件闹钟 + 立即刷新桌面小组件。失败不影响主流程。默认空实现便于测试。 */
    private val onDataChanged: suspend () -> Unit = {},
) : ViewModel() {

    private val _draft = MutableStateFlow(CourseDraft())
    val draft: StateFlow<CourseDraft> = _draft.asStateFlow()

    private var otherSessions: List<CourseSession> = emptyList()
    private var originalCourse: Course? = null

    /** 是否有尚未落库的改动（setter 置 true，persist 成功后清 false）。 */
    private var dirty = false
    /** 即时落库的 debounce 任务（改动停顿后才写库，避免每个字符都写一次）。 */
    private var autosaveJob: Job? = null
    private val autosaveDelayMs = 350L

    companion object {
        // 保留既有调用点（表单里的 RangeRow 引用 CourseEditViewModel.SECTION_RANGE）
        val SECTION_RANGE = DraftLimits.SECTION_RANGE
        val WEEK_RANGE = DraftLimits.WEEK_RANGE
    }

    // ---------- 字段 setter（界面用方法引用传入，保证可跳过重组）----------

    /** 字段改动统一入口：标脏 + 更新 draft，由 debounce 在"停顿"后统一落库（不在 setter 逐字符写库，避免半截非法值入库）。 */
    private fun editField(block: (CourseDraft) -> CourseDraft) {
        dirty = true
        _draft.update(block)
        scheduleAutosave()
    }

    fun setName(value: String) = editField { it.copy(name = value, error = null, errorField = null) }

    fun setTeacher(value: String) = editField { it.copy(teacher = value, error = null, errorField = null) }

    fun setClassroom(value: String) = editField { it.copy(classroom = value, error = null, errorField = null) }

    /** M12 需求三：备注输入。 */
    fun setNote(value: String) = editField { it.copy(note = value, error = null, errorField = null) }

    fun setDayOfWeek(day: Int) {
        if (day !in 1..7) return
        editField { it.copy(dayOfWeek = day) }
    }

    fun setStartSection(section: Int) = editField {
        it.copy(startSection = section.coerceIn(SECTION_RANGE.first, SECTION_RANGE.last), error = null, errorField = null)
    }

    fun setEndSection(section: Int) = editField {
        it.copy(endSection = section.coerceIn(SECTION_RANGE.first, SECTION_RANGE.last), error = null, errorField = null)
    }

    fun setStartWeek(week: Int) = editField {
        it.copy(startWeek = week.coerceIn(WEEK_RANGE.first, WEEK_RANGE.last), error = null, errorField = null)
    }

    fun setEndWeek(week: Int) = editField {
        it.copy(endWeek = week.coerceIn(WEEK_RANGE.first, WEEK_RANGE.last), error = null, errorField = null)
    }

    fun setWeekType(type: WeekType) = editField { it.copy(weekType = type) }

    fun setColorTag(tag: Int) {
        // 注意 不能写 `if (tag < 0) return`：满不透明的 ARGB（0xFFRRGGBB）在 Int 里是**负数**，
        // 这行守卫会把取色器自取的每一个颜色都静默丢掉——面板关了、颜色没变、表单还停在旧色
        // （2026-10-02 模拟器实测复现）。色板索引和 ARGB 都是合法值，只拦两者都不是的脏数据。
        if (!isValidColorTag(tag)) return
        editField { it.copy(colorTag = tag) }
    }

    /**
     * 安排一次"停顿后落库"。
     *
     * 即时保存只针对「编辑既有课程」：新增课程还没有稳定 id，若每次停顿都建行会生成一堆重复课程，
     * 所以新增态（originalCourse == null）不自动落库，改成退出时由 save() 一次性提交。
     * debounce 到点即"已停顿"信号——等价于失焦语义，但无需改动表单文件 CourseEditForm.kt，
     * 避免与并行改动冲突。是否真的落库再交给 shouldAutoSave 真值表判定（脏 + 停顿 + 校验通过）。
     */
    private fun scheduleAutosave() {
        if (originalCourse == null) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(autosaveDelayMs)
            val valid = validateDraft(_draft.value) == null
            if (shouldAutoSave(fieldChanged = dirty, settled = true, valid = valid)) {
                persist(notifyDataChanged = true)
            }
        }
    }

    // ---------- 生命周期 ----------

    /**
     * 准备新增（带当前学期与周次范围）。
     *
     * 注意 这里的守卫条件是本项目修过的一个真实缺陷的根因：
     * 原写法 `if (_draft.value.courseId != null) return` 只判断"有没有 courseId"，
     * 而上一轮编辑过的课程会留下 courseId → 从"编辑课程"返回后再点"添加课程"，
     * 本方法**直接早退、什么都不重置**，于是表单里显示的是上一门课的数据。
     * 现在只有"当前本来就是新增态且学期相同"才早退（保留用户已填内容），
     * 其余情况一律重置。
     */
    fun prepareNew(termId: Long?, defaultWeek: Int, totalWeeks: Int) {
        if (_draft.value.courseId == null && _draft.value.termId == termId) return
        autosaveJob?.cancel()
        dirty = false
        otherSessions = emptyList()
        originalCourse = null
        _draft.value = CourseDraft(
            termId = termId,
            startWeek = 1,
            endWeek = if (totalWeeks > 0) totalWeeks.coerceIn(WEEK_RANGE.first, WEEK_RANGE.last) else 16,
        )
    }

    /** 载入既有课程用于编辑（失败不崩溃，写入可读错误）。 */
    fun load(courseId: String, sessionId: Long, defaultTotalWeeks: Int = 18) {
        autosaveJob?.cancel()
        dirty = false
        viewModelScope.launch {
            _draft.update { it.copy(courseId = courseId, sessionId = sessionId, loading = true) }
            runCatching {
                val course = repository.getCourseById(courseId)
                val sessions = repository.getSessionsByCourseId(courseId)
                course to sessions
            }.onSuccess { (course, sessions) ->
                if (course == null) {
                    _draft.update { it.copy(loading = false, error = "课程不存在或已被删除", errorField = DraftField.OTHER) }
                    return@onSuccess
                }
                val target = sessions.firstOrNull { it.id == sessionId } ?: sessions.firstOrNull()
                otherSessions = sessions.filter { it.id != target?.id }
                originalCourse = course
                _draft.update {
                    CourseDraft(
                        courseId = course.id,
                        sessionId = target?.id ?: 0L,
                        name = course.name,
                        teacher = course.teacher.orEmpty(),
                        classroom = target?.classroom.orEmpty(),
                        // M12 需求三：备注回填（courses.note 早就存在，只是此前没有输入入口）
                        note = course.note.orEmpty(),
                        dayOfWeek = (target?.dayOfWeek ?: 1).coerceIn(1, 7),
                        startSection = (target?.startSection ?: 1).coerceIn(SECTION_RANGE.first, SECTION_RANGE.last),
                        endSection = (target?.endSection ?: 2).coerceIn(SECTION_RANGE.first, SECTION_RANGE.last),
                        startWeek = (target?.startWeek ?: 1).coerceIn(WEEK_RANGE.first, WEEK_RANGE.last),
                        endWeek = (target?.endWeek ?: defaultTotalWeeks).coerceIn(WEEK_RANGE.first, WEEK_RANGE.last),
                        weekType = target?.weekType ?: WeekType.ALL,
                        hasExactWeeks = target?.weeks != null,
                        colorTag = course.colorTag.coerceAtLeast(0),
                        termId = course.termId,
                        loading = false,
                    )
                }
            }.onFailure { e ->
                _draft.update { it.copy(loading = false, error = e.toUserMessage("读取课程"), errorField = DraftField.OTHER) }
            }
        }
    }

    /**
     * 把当前 draft 落库（新增或编辑），返回是否成功。
     *
     * 抽出来供两条路径共用：① 改动停顿后的即时落库；② 退出/显式提交时强制落库。
     * 校验不过直接返回 false（不落库、不导航），错误留给界面红框提示。
     */
    private suspend fun persist(notifyDataChanged: Boolean): Boolean {
        val d = _draft.value
        validateDraft(d)?.let { (message, field) ->
            _draft.update { it.copy(error = message, errorField = field) }
            return false
        }
        val termId = d.termId ?: runCatching { repository.observeActiveTerm().first()?.id }.getOrNull()
        if (termId == null) {
            _draft.update { it.copy(error = "还没有配置学期，无法保存课程", errorField = DraftField.OTHER) }
            return false
        }
        val now = System.currentTimeMillis()
        val (course, sessions) = buildCourseAndSessions(d, originalCourse, termId, now)
        return runCatching { repository.upsertCourse(course, otherSessions + sessions) }
            .onSuccess {
                dirty = false
                _draft.update { it.copy(error = null, errorField = null) }
                if (notifyDataChanged) runCatching { onDataChanged() } // 数据变更：重排闹钟 + 刷新桌面小组件
            }
            .onFailure { e ->
                _draft.update { it.copy(error = e.toUserMessage("保存课程"), errorField = DraftField.OTHER) }
            }
            .isSuccess
    }

    /**
     * 退出 / 显式提交：强制落库并导航返回。
     *
     * 与即时落库的区别——即时落库只在"改动停顿"后触发（debounce 充当停顿信号），
     * 这里无视停顿，用户一点就立刻把缓冲里最后的改动刷进库（否则 debounce 窗口内退出会丢最后几笔）。
     * 校验不过则停在页内并展示错误，绝不导航，避免把非法数据存进去或被直接带离。
     */
    fun save() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            // 有未落库的改动才真正写库（即时保存可能已写好）；否则只是离开。
            // 校验不过则停在页内、展示错误，不导航。
            if (shouldFlushOnExit(dirty, validateDraft(_draft.value) == null) &&
                !persist(notifyDataChanged = true)
            ) {
                return@launch
            }
            _draft.update { it.copy(saved = true, error = null, errorField = null) }
        }
    }

    /** 删除整门课程（界面需先做二次确认，见 AC-06）。 */
    fun delete() {
        viewModelScope.launch {
            val id = _draft.value.courseId ?: return@launch
            runCatching { repository.deleteCourse(id) }
                .onSuccess {
                    _draft.update { it.copy(saved = true) }
                    runCatching { onDataChanged() } // 数据变更：重排闹钟 + 刷新桌面小组件
                }
                .onFailure { e ->
                    _draft.update { it.copy(error = e.toUserMessage("删除课程"), errorField = DraftField.OTHER) }
                }
        }
    }
}

/** 把异常转成可读的中文提示（不吞异常信息，便于排查）。 */
private fun Throwable.toUserMessage(action: String): String {
    val reason = when (this) {
        is IllegalArgumentException -> message ?: "输入不合法"
        else -> message ?: this::class.simpleName ?: "未知错误"
    }
    return "$action 失败：$reason"
}

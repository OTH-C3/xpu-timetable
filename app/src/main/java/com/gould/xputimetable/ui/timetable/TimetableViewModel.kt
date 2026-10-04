/*
 * TimetableViewModel.kt —— 周视图的界面状态持有者
 *
 * 作用：把三个数据流合成一个"界面能直接画"的状态：
 *   ① 激活学期（没有学期 → 引导态）
 *   ② 作息节次（决定网格行高与节次范围）
 *   ③ 本周课程安排（由学期 id + 周次从仓库查询）
 *
 * 周次的来源与切换（架构 §9.2）：
 *   - 默认＝自动计算（WeekCalc.currentWeek，纯函数，开学前返回 null）；
 *   - 用户手动切换后进入"覆盖模式"，顶栏出现「回到本周」；
 *   - 越界钳制在 1..totalWeeks：开学前显示"未开学"，超出总周数显示"本学期已结束"。
 *
 * M7：「覆盖模式」的判定与写入统一走 WeekOverridePolicy（纯函数）——
 *   目标周 == 本周时视作未覆盖，修掉「回到本周」按钮长亮。
 *
 * 健壮性约定（2026-09-17 加固）：
 *   1. 学期起始日期来自数据库字符串，可能被导入或手工改坏 —— 解析用 runCatching，
 *      解析失败**不崩溃**，改为按第 1 周展示并给出 termDateInvalid 提示；
 *   2. 创建学期等写操作失败转为 lastError（界面用 Snackbar 提示），绝不让异常冒泡到
 *      viewModelScope（那会直接崩溃）。
 */
package com.gould.xputimetable.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class TimetableUiState(
    val loading: Boolean = true,
    val term: Term? = null,
    val week: Int = 1,
    val totalWeeks: Int = 18,
    /**
     * 自动周（按学期起始日推算，已钳制在 1..totalWeeks）；起始日缺失/无效或未开学时为 null。
     *
     * M7 新增：`setWeek` 用它做覆盖值的归一化（目标周 == 自动周 → 视为未覆盖），
     * 修掉「回到本周」按钮长亮（详见 WeekOverridePolicy 的文件头）。
     */
    val autoWeek: Int? = null,
    val weekIsOverridden: Boolean = false,
    val beforeTermStart: Boolean = false,
    val afterTermEnd: Boolean = false,
    /** 学期起始日期无法解析（脏数据）：按第 1 周展示并提示，而不是崩溃。 */
    val termDateInvalid: Boolean = false,
    /**
     * 相邻周课程缓存（M4-UI-fix 缺陷 1）：键为周次，只含当前周 ±1（越界侧裁剪不存）。
     * 滑动过程中邻页按 week 取数渲染，不再是空网格。
     */
    val weekItems: Map<Int, List<SessionWithCourse>> = emptyMap(),
    val timeSlots: List<TimeSlot> = emptyList(),
    /** 一次性错误消息（界面用 Snackbar 展示后调用 consumeError 清除）。 */
    val lastError: String? = null,
)

class TimetableViewModel(
    private val repository: TimetableRepository,
    /**
     * M11：「显示老师姓名」开关的数据源（设置页与课程卡共用同一个 UiPrefs）。
     * 默认 flowOf(true)——不传时行为等同于"总是显示"，测试与单 VM 场景无需关心。
     */
    showTeacherFlow: Flow<Boolean> = flowOf(true),
) : ViewModel() {

    private val weekOverride = MutableStateFlow<Int?>(null)

    private val transientError = MutableStateFlow<String?>(null)

    init {
        // 首次安装后补齐预置作息（幂等）；失败只记录、不影响进入周视图
        viewModelScope.launch {
            runCatching { repository.ensureDefaultTimeSlots() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimetableUiState> = combine(
        repository.observeActiveTerm(),
        repository.observeTimeSlots(),
        weekOverride,
        transientError,
    ) { term, slots, override, error -> Quad(term, slots, override, error) }
        .flatMapLatest { (term, slots, override, error) ->
            if (term == null) {
                flowOf(TimetableUiState(loading = false, lastError = error))
            } else {
                val startDate = runCatching { LocalDate.parse(term.startDate) }.getOrNull()
                val auto = startDate?.let { WeekCalc.currentWeek(it, LocalDate.now()) }
                val isValidDate = startDate != null
                // 钳制后的自动周：放假期间 auto 可能超出总周数（如第 20 周），
                // 归一化与覆盖判定都以"实际能显示到的周"为准，否则第 18 周会被误判为覆盖
                val autoClamped = auto?.coerceIn(1, term.totalWeeks)
                val week = (override ?: auto ?: 1).coerceIn(1, term.totalWeeks)
                // 相邻周预取（M4-UI-fix 缺陷 1）：为 week-1/week/week+1 各订阅一次
                // observeWeek，越界周次裁剪不查不存；页面按 week 从 weekItems 取数
                val weeks = listOf(week - 1, week, week + 1)
                    .filter { it in 1..term.totalWeeks }
                    .distinct()
                val weekFlows = weeks.map { w ->
                    repository.observeWeek(term.id, w).map { schedule -> w to schedule.items }
                }
                combine(weekFlows) { pairs ->
                    val weekItems = pairs.toMap()
                    TimetableUiState(
                        loading = false,
                        term = term,
                        week = week,
                        totalWeeks = term.totalWeeks,
                        autoWeek = autoClamped,
                        weekIsOverridden = WeekOverridePolicy.isOverridden(override, autoClamped),
                        beforeTermStart = override == null && isValidDate && auto == null,
                        afterTermEnd = override == null && auto != null && auto > term.totalWeeks,
                        termDateInvalid = !isValidDate,
                        weekItems = weekItems,
                        timeSlots = slots,
                        lastError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    /**
     * 「显示老师姓名」当前值（设置页 ↔ 课表页共享同一个 DataStore 源）。
     *
     * 刻意**不放进 uiState**：它不参与周视图那个 combine（学期/作息/周次/错误），
     * 混进去会把一个和课程数据无关的开关拖进每次重算，属于无谓耦合。
     *
     * 启动策略必须是 [SharingStarted.Eagerly]，不是 WhileSubscribed：
     * 用户在「我的」页拨开关 → 课表页在 AnimatedContent 里被换出（composition 释放、
     * collectAsState 退订）→ 再切回时页面重新组合。WhileSubscribed 会在这段"没人订阅"
     * 的窗口里停掉 DataStore 上游，重新组合时读到的是旧快照，表现为
     * **"拨了开关切回课表、教师行却还在，要再切一次或重启才生效"**（真机实测复现）。
     * Eagerly 让 VM 一创建就持有最新值，切页重组合直接读到当次结果。
     * 代价几乎为零：上游只是一个常驻内存的小 protobuf，读一次即可。
     */
    val showTeacher: StateFlow<Boolean> = showTeacherFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 切换周次（正/负步）。越界自动钳制，不会切出 0 周或负周。 */
    fun moveWeek(delta: Int) {
        val state = uiState.value
        if (state.loading || state.term == null) return
        setWeek(state.week + delta)
    }

    /**
     * 滑动切周（M4-UI R5）：直接设定目标周次。
     * 与 moveWeek 同一覆盖通道（weekOverride），越界钳制在 1..totalWeeks，
     * 学期未开始/已结束时滑动仍受限在边界内。
     *
     * M7：写入前经 [WeekOverridePolicy.normalize] 归一化 —— 目标周若就是本周，
     * 视作"未覆盖"（存 null）。这是修掉「回到本周」按钮长亮的关键：
     * pager 的程序化滚动落定后会把当前页回写进来，若不归一化，
     * 回到本周后会被立刻重新标记成覆盖态。
     */
    fun setWeek(week: Int) {
        val state = uiState.value
        if (state.loading || state.term == null) return
        val target = week.coerceIn(1, state.totalWeeks)
        weekOverride.value = WeekOverridePolicy.normalize(target, state.autoWeek)
    }

    /** 回到本周（清除手动覆盖）。 */
    fun backToCurrentWeek() {
        weekOverride.value = null
    }

    /** 错误消息已展示，清除它（避免旋转屏幕后重复弹出）。 */
    fun consumeError() {
        transientError.value = null
    }

    /**
     * M11-第三批：课程详情弹层的「复制」——把整门课连着它名下**所有**节次复制成一份新的。
     *
     * 为什么必须重新取整门课的资料、不能拿 UI 行上的 [SessionWithCourse] 直接开干：
     * `SessionWithCourse` 是周视图 JOIN 出来的窄行（只带 课名/颜色/教师），
     * code / note / source / termId 这些都在 `courses` 表里 —— 凭那三列造不出一个能落库的 Course，
     * 存进去会变成一条残缺课程。所以这里回查一次 `getCourseById` 再整条复制。
     *
     * 复制出的新 id 用 UUID（与 CourseEditViewModel 同一套生成方式），
     * 旧 id 不复用：复用会让 `course_sessions.course_id` 的外键指向既老又脏的一行。
     */
    fun duplicateCourse(item: SessionWithCourse) {
        viewModelScope.launch {
            val source = repository.getCourseById(item.session.courseId) ?: return@launch
            val sessions = repository.getSessionsByCourseId(source.id)
            val newId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            repository.upsertCourse(
                course = source.copy(id = newId, createdAt = now, updatedAt = now),
                // 每个节次都换新主键（Room 的 @Insert 把 id=0 当"新行"），并改挂到新课上
                sessions = sessions.map { it.copy(id = 0L, courseId = newId) },
            )
        }
    }

    /**
     * M11-第三批：课程详情弹层要显示备注，但周视图 JOIN 出来的 [SessionWithCourse]
     * 只带 课名/颜色/教师，没有 `courses.note` —— 不回查一次就得放弃备注这一行。
     *
     * 用 StateFlow 而非常规 suspend：弹层里每帧都要读，若走 suspend 只能在
     * LaunchedEffect 里取一次快照，用户翻课表时备注会停在打开那一刻的旧值。
     */
    private val _detailNote = MutableStateFlow<String?>(null)
    val detailNote: StateFlow<String?> = _detailNote.asStateFlow()

    fun loadDetailNote(courseId: String) {
        viewModelScope.launch {
            _detailNote.value = repository.getCourseById(courseId)?.note?.takeIf { it.isNotBlank() }
        }
    }

    fun clearDetailNote() {
        _detailNote.value = null
    }

    /**
     * M11-第三批：课程详情弹层的「删除」——删整门课。
     *
     * 只按 courseId 删、`sessions` 不手动清：库里 `course_sessions.course_id` 是
     * `ON DELETE CASCADE`，删掉 courses 行后子表由 SQLite 级联带走（见已知的 P2 脆弱点备注）。
     */
    fun removeCourse(item: SessionWithCourse) {
        viewModelScope.launch { repository.deleteCourse(item.session.courseId) }
    }
}

/** 四元组（combine 最多支持 5 个流，这里用具名数据类提升可读性）。 */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

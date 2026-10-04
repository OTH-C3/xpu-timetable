/*
 * TimetableScreen.kt —— 周视图页面（应用主页，M4-UI 重构 + M5-UI 调整 + M6 修复）
 *
 * 结构：周次标题条 → 学期状态提示 → HorizontalPager（每页 = DayHeader + WeekGrid，
 * 容器拆至 components/WeekPager.kt，M6 为行数门禁）。
 * M5-UI：动效放缓（切周 450ms / 首屏 800ms，错峰 120ms）；亮色背景固定产品色
 *   LightPageBackground（深色保持现状）；FAB（导入/添加）迁至顶栏 WeekSelector 右侧。
 * M6 需求 4：today/nowMinute 改为分钟级自动推进（rememberMinuteTick）——
 *   原先只在重组时读一次快照，用户静置不动则跨过上下课时刻后"正在上"标记不更新。
 * M7 ①：移除首屏 EnterRise 渐入动画（每次进入都做整页 graphicsLayer 动画，掉帧明显）。
 */
package com.gould.xputimetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.ui.timetable.components.WeekPager
import com.gould.xputimetable.ui.timetable.components.WeekSelector
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    snackbarHostState: SnackbarHostState,
    onAddCourse: () -> Unit,
    onEditCourse: (courseId: String, sessionId: Long) -> Unit,
    onOpenImport: () -> Unit,
    /** M9：空态「创建本学期」不再写死"本周一 + 18 周"，改为进学期设置页由用户选起始日。 */
    onCreateTerm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    // M6 需求 4：分钟级推进的"当前时刻"，静置不动也能准时画出/消除"正在上"边框
    val (today, nowMinute) = rememberMinuteTick()
    // M11：「显示老师姓名」（设置页开关）→ 课表页 → WeekGrid → 课程卡
    val showTeacher by viewModel.showTeacher.collectAsState()
    // M11-第三批：课程详情弹层（点课程卡弹出，不再是直接跳编辑页）
    val detailNote by viewModel.detailNote.collectAsState()

    // 弹层目标 + 备注可见范围：与选中项同生命周期，故放在屏幕层而不是弹层内部
    var detailItem by remember { mutableStateOf<SessionWithCourse?>(null) }
    var noteThisWeekOnly by remember { mutableStateOf(false) }
    // 稳定引用：WeekPager/WeekGrid 靠它跳过重组（内联 lambda 每次重组都换实例）
    val onCourseClick = remember {
        { item: SessionWithCourse ->
            noteThisWeekOnly = item.session.startWeek == item.session.endWeek
            detailItem = item
        }
    }
    LaunchedEffect(detailItem) {
        val courseId = detailItem?.session?.courseId
        if (courseId == null) viewModel.clearDetailNote() else viewModel.loadDetailNote(courseId)
    }

    // 一次性错误提示（如创建学期失败）：展示后清除，避免旋转屏幕重复弹出
    LaunchedEffect(state.lastError) {
        val message = state.lastError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    // M7 ①：首屏入场动画（EnterRise）已移除——它每次进入都要做一轮整页 graphicsLayer 动画，
    // 是肉眼可见的掉帧来源，收益只是视觉点缀。系统"减少动画"分支也随之不再需要。
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // M12 需求一.2：原先这里显式给 LightPageBackground，而「我的」页没给 → 两页底色不同。
        // 现在底色与背景图统一由导航根的 PageBackground 提供（见 ui/background/PageBackground.kt），
        // 所有页面一律透明；pageBaseColor() 是底色的唯一来源。
        containerColor = Color.Transparent,
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            if (state.term != null) {
                WeekSelector(
                    week = state.week,
                    totalWeeks = state.totalWeeks,
                    overridden = state.weekIsOverridden,
                    startDate = state.term?.startDate,
                    onBackToCurrentWeek = viewModel::backToCurrentWeek,
                    onAddCourse = onAddCourse,
                    onOpenImport = onOpenImport,
                )
                TermStatusHint(state.beforeTermStart, state.afterTermEnd, state.termDateInvalid)
            }

            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.term == null -> {
                    NoTermState(onCreateTerm = onCreateTerm)
                }

                else -> {
                    WeekPager(
                        state = state,
                        viewModel = viewModel,
                        today = today,
                        nowMinute = nowMinute,
                        showTeacher = showTeacher,
                        onAddCourse = onAddCourse,
                        onOpenImport = onOpenImport,
                        onCourseClick = onCourseClick,
                    )
                }
            }
        }
    }

    // ---------- 课程详情弹层 ----------
    // 独立成块、不进 Scaffold 的 content：它是覆盖在整页之上的面板，不属于内容区。
    val active = detailItem
    if (active != null) {
        CourseDetailSheet(
            item = active,
            note = detailNote,
            noteThisWeekOnly = noteThisWeekOnly,
            timeSlots = state.timeSlots,
            onToggleNoteScope = {
                noteThisWeekOnly = !noteThisWeekOnly
            },
            onEdit = {
                onEditCourse(active.session.courseId, active.session.id)
                detailItem = null
            },
            onDuplicate = {
                viewModel.duplicateCourse(active)
                detailItem = null
            },
            onDeleteRequest = {
                viewModel.removeCourse(active)
                detailItem = null
            },
            onDismiss = { detailItem = null },
        )
    }
}

/**
 * 每分钟推进一次的"当前时刻"（M6 需求 4）。
 *
 * 原来 LocalDate.now()/LocalTime.now() 只在重组时读一次快照，用户静置不动则跨过
 * 上下课时刻后不会重算——表现为"有时候不标记正在上的课"。
 * 对齐到下一个整分钟边界再唤醒（不是固定 delay(60_000)），避免逐次累积漂移；
 * 协程随调用方离开组合而自动取消（LaunchedEffect(Unit) 的标准语义）。
 */
@Composable
private fun rememberMinuteTick(): Pair<LocalDate, Int> {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            // 对齐到"下一个整分钟"的毫秒数（含跨午夜：23:59:30 → 30000ms）
            val nextMinute = current.withSecond(0).withNano(0).plusMinutes(1)
            delay(Duration.between(current, nextMinute).toMillis())
            now = LocalDateTime.now()
        }
    }
    return now.toLocalDate() to (now.hour * 60 + now.minute)
}

/** 还没有学期时的引导（创建本学期后才能归属课程）。 */
@Composable
private fun NoTermState(onCreateTerm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "还没有学期", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先创建本学期：选好开学第一周的周一，之后才能按周显示课程",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateTerm) { Text("创建本学期") }
    }
}

/** 学期状态提示（PRD 边界要求：不出现负数周次，改用文字状态）。 */
@Composable
private fun TermStatusHint(beforeStart: Boolean, afterEnd: Boolean, dateInvalid: Boolean) {
    val text = when {
        dateInvalid -> "学期起始日期异常，已按第 1 周显示"
        beforeStart -> "未开学"
        afterEnd -> "本学期已结束"
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

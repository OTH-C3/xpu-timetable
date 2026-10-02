/*
 * WeekPager.kt —— 滑动切周容器（自 TimetableScreen.kt 搬出，M6 为行数门禁拆分）
 *
 * R5 滑动切周：每页 = DayHeader + WeekGrid，HorizontalPager 一次手势最多翻一周
 * （PagerSnapDistance.atMost(1)），表头随页滑动；竖直滚动位置提升到 pager 层共享，
 * 切周后保持。相邻周预取（M4-UI-fix）：VM 订阅当前周 ±1，滑动过程中邻页即有课程数据。
 * 回写守卫（M4-UI-fix）：pager 首次发射与程序化动画期间不写 weekOverride，
 * 启动不再被钉在覆盖状态，「返回本周」禁用态恢复正常。
 *
 * M7 ①：移除 EnterRise 渐入动画。它用 LaunchedEffect + animateFloatAsState 包裹整页
 *   （表头与网格错峰 120ms），每次进入都触发一轮整页 graphicsLayer 动画（alpha + translationY），
 *   在低端机/冷启动时肉眼可见掉帧，收益却只是"好看一下"。删掉后直接显示，无动画成本。
 * M7 ②：beyondViewportPageCount 0 → 1。原先为 0 时邻页不参与组合，翻周瞬间要**现场组合**
 *   整页（DayHeader + WeekGrid + 全部课程卡片），是滑动卡顿的主要来源。改为 1 后前后各预组合
 *   一页，翻周时直接复用；代价是多保留 2 页的组合结果（内存换流畅，本项目页内元素量很小）。
 * M7 ③：courseClick 用 remember 缓存为稳定引用。原先在 page lambda 内写内联 lambda，
 *   每次重组都新建实例（不稳定参数），使 WeekGrid 无法跳过重组——而 nowMinute 每分钟变化一次，
 *   等于每分钟让整张网格全量重算。
 */
package com.gould.xputimetable.ui.timetable.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.ui.components.EmptyState
import com.gould.xputimetable.ui.theme.Motion
import com.gould.xputimetable.ui.timetable.TimetableViewModel
import com.gould.xputimetable.ui.timetable.TimetableUiState
import java.time.LocalDate

/** 滑动切周容器（R5）：每页 = DayHeader + WeekGrid；落定后回写 VM 周次。 */
@Composable
internal fun WeekPager(
    state: TimetableUiState,
    viewModel: TimetableViewModel,
    today: LocalDate,
    nowMinute: Int,
    /** M11：「显示老师姓名」开关（透传到 WeekGrid → CourseCard）。 */
    showTeacher: Boolean = true,
    onAddCourse: () -> Unit,
    onOpenImport: () -> Unit,
    /**
     * M11-第三批：点课程卡 —— 交出整行 [SessionWithCourse]（不再只报两个 id）。
     * 原来这里直奔 `onEditCourse(courseId, sessionId)` 进编辑页；
     * 改交整行是因为详情弹层要画色条/教室/备注，光有 id 不够，
     * 而"进编辑页"这件事已上移到调用方（TimetableScreen 收到行后再决定弹层还是跳转）。
     */
    onCourseClick: (SessionWithCourse) -> Unit,
) {
    // 竖直滚动位置提升到 pager 层：切周后保持（规格 §4 R5）
    val scrollState = rememberScrollState()
    val pagerState = rememberPagerState(initialPage = state.week - 1, pageCount = { state.totalWeeks })

    // 双向同步（防抖）：手势/落定 → VM；外部变化（双击回本周）→ 程序化动画，
    // 动画期间的 currentPage 变化不回写（否则会把覆盖周写死、与回本周互抢）
    var programmaticTarget by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val target = page + 1
            // 守卫（缺陷 2）：只接受"与 VM 当前周不一致"的变化——既跳过首次发射
            // （否则启动即写成 weekOverride，按钮禁用态永远不出现、周次被钉死），
            // 也跳过程序化滚动期间的中间帧
            if (programmaticTarget == null && target != viewModel.uiState.value.week) {
                viewModel.setWeek(target)
            }
        }
    }
    LaunchedEffect(state.week) {
        val target = state.week - 1
        if (pagerState.currentPage != target) {
            programmaticTarget = target
            runCatching { pagerState.animateScrollToPage(target) }
            programmaticTarget = null
        }
    }

    HorizontalPager(
        state = pagerState,
        // M7 ②：前后各预组合一页——翻周时邻页已就绪，不再现场组合整页（原为 0）
        beyondViewportPageCount = 1,
        key = { it },
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),   // 一次手势最多翻一周
            snapAnimationSpec = tween(Motion.BaseMillis, easing = Motion.EaseOutStandard),
        ),
    ) { page ->
        val week = page + 1
        val isCurrentWeek = week == state.week
        // 缺陷 1 修复：按周次从预取缓存取数，滑动中邻页即有数据，不再依赖 isCurrentWeek
        val pageItems = state.weekItems[week].orEmpty()
        Column(Modifier.fillMaxSize()) {
            DayHeader(startDate = state.term?.startDate, week = week, today = today)
            // 空态仅限"该页就是当前周且该周确实为空"（邻页数据未到不误显示空态）
            if (isCurrentWeek && pageItems.isEmpty()) {
                EmptyState(
                    onAddManually = onAddCourse,
                    onImport = onOpenImport,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                WeekGrid(
                    items = pageItems,
                    timeSlots = state.timeSlots,
                    week = week,
                    startDate = state.term?.startDate,
                    today = today,
                    nowMinute = nowMinute,
                    showTeacher = showTeacher,
                    scrollState = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    onCourseClick = onCourseClick,
                )
            }
        }
    }
}

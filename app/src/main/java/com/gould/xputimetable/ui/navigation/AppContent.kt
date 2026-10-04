/*
 * AppContent.kt —— 导航根的内容区（M12 从 AppNav 拆出）
 *
 * 拆出来的原因：加了待办页（第三个底栏项）与背景层后，AppNav 会顶破单文件 300 行门禁。
 * 分工：**AppNav 管"栈怎么出栈"，本文件管"当前屏画什么"** —— 本文件不持有返回栈、
 * 不参与返回判定，只接收一个 AppScreen 与全部依赖。
 *
 * 层级（自下而上，正是需求一.2 与需求二要的顺序）：
 *   PageBackground（底色 + 可选背景图）
 *     └ Column
 *        ├ Box(weight 1) → AnimatedContent（页面）
 *        │                  └ BottomHint（跨页提示浮层，盖在页面之上）
 *        └ AppBottomBar（三个根页显示；M10 起不画背景，透出页面底色）
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.domain.repository.TodoRepository
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.ui.background.PageBackground
import com.gould.xputimetable.ui.components.BottomHint
import com.gould.xputimetable.ui.note.NoteScreen
import com.gould.xputimetable.ui.note.NoteViewModel
import com.gould.xputimetable.ui.timetable.TimetableScreen
import com.gould.xputimetable.ui.timetable.TimetableViewModel
import com.gould.xputimetable.ui.transfer.QrShareScreen

@Suppress("LongParameterList") // 装配型组件：参数就是它的依赖清单，拆散反而不清楚
@Composable
internal fun AppContent(
    screen: AppScreen,
    navForward: Boolean,
    animated: Boolean,
    repository: TimetableRepository,
    todoRepository: TodoRepository,
    jsonFileImporter: ScheduleImporter,
    xpuImporter: ScheduleImporter,
    canScheduleExact: () -> Boolean,
    uiPrefs: UiPrefs,
    backgroundPrefs: BackgroundPrefs,
    onDataChanged: suspend () -> Unit,
    hubNotice: String?,
    onHubNoticeShown: () -> Unit,
    onNotice: (String?) -> Unit,
    hintText: String?,
    showHint: (String) -> Unit,
    navigateTo: (AppScreen) -> Unit,
    goBack: () -> Unit,
    popToRoot: () -> Unit,
) {
    // M4-UI R7：底栏只在三个根页显示，二级页保持沉浸
    val showBottomBar = screen == AppScreen.Timetable ||
        screen == AppScreen.Profile ||
        screen == AppScreen.Note

    // M12 需求一.2 + 需求二：**背景层挂在导航根** —— 所有页面的 Scaffold 一律透明，
    // 底色与背景图统一由这一层提供。放这里（而不是每页各自处理）的原因：
    // 一是需求一.2 的病根就是"每页各自给底色"，收到一处才不会长出第二份；
    // 二是背景图必须盖在所有页面**之下**、底栏**之上**，导航根正好是这个位置。
    val background by backgroundPrefs.settings.collectAsState(initial = null)
    val density = LocalDensity.current.density
    val screenPx = with(LocalConfiguration.current) {
        maxOf(screenWidthDp, screenHeightDp) * density
    }.toInt()

    // 周视图自己有一处 Snackbar（学期创建失败等一次性错误）。它必须 remember 缓存：
    // 每次重组新建 SnackbarHostState 会让已排队的提示被丢弃，表现为"错误提示偶尔不弹"。
    val snackbarHostState = remember { SnackbarHostState() }

    PageBackground(
        settings = background,
        scope = screen.backgroundScope(),
        maxPixels = screenPx,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screen,
                    // M12：转场规格（纯淡入淡出，取消位移）见 ScreenTransition.kt
                    transitionSpec = { screenTransition(navForward, animated) },
                    label = "screenTransition",
                ) { current: AppScreen ->
                    when (current) {
                        AppScreen.Timetable -> {
                            val vm: TimetableViewModel = viewModel(
                                factory = simpleFactory { TimetableViewModel(repository, uiPrefs.showTeacher) },
                            )
                            val state = vm.uiState.value
                            TimetableScreen(
                                viewModel = vm,
                                snackbarHostState = snackbarHostState,
                                onAddCourse = {
                                    navigateTo(AppScreen.CourseEdit(EditTarget.New(state.term?.id, state.week, state.totalWeeks)))
                                },
                                onEditCourse = { courseId, sessionId ->
                                    navigateTo(AppScreen.CourseEdit(EditTarget.Edit(courseId, sessionId)))
                                },
                                onOpenImport = { navigateTo(AppScreen.ImportHub) },
                                // M9：空态「创建本学期」进学期设置页，由用户选起始日
                                onCreateTerm = { navigateTo(AppScreen.TermSetup(fromEmptyState = true)) },
                            )
                        }

                        // M12 需求五：待办清单（底栏第三项）
                        AppScreen.Note -> {
                            val vm: NoteViewModel = viewModel(
                                factory = simpleFactory { NoteViewModel(todoRepository) },
                            )
                            NoteScreen(viewModel = vm, onShowHint = showHint)
                        }

                        // M11：编辑/导入这一组的装配拆到 ImportFlowDestination.kt
                        is AppScreen.CourseEdit, is AppScreen.ImportHub, is AppScreen.Cleanup,
                        is AppScreen.CourseCapture, is AppScreen.ImportPreview ->
                            ImportFlowDestination(
                                screen = current,
                                repository = repository,
                                jsonFileImporter = jsonFileImporter,
                                xpuImporter = xpuImporter,
                                onDataChanged = onDataChanged,
                                hubNotice = hubNotice,
                                onHubNoticeShown = onHubNoticeShown,
                                onNotice = onNotice,
                                navigateTo = navigateTo,
                                onBack = goBack,
                                onDone = popToRoot,
                            )

                        AppScreen.QrShare -> QrShareScreen(repository = repository, onBack = goBack)

                        // M11：这一组（我的 / 学期设置 / 二级页）的装配拆到 ProfileFlowDestination.kt
                        is AppScreen.TermSetup, is AppScreen.ProfileSub, AppScreen.Profile ->
                            ProfileFlowDestination(
                                screen = current,
                                repository = repository,
                                canScheduleExact = canScheduleExact,
                                onDataChanged = onDataChanged,
                                uiPrefs = uiPrefs,
                                backgroundPrefs = backgroundPrefs,
                                showHint = showHint,
                                navigateTo = navigateTo,
                                onBack = goBack,
                                onDone = popToRoot,
                            )
                    }
                }

                // 底部提示浮层：挂在导航层这个 Box 上（**页面 Scaffold 之外**）→
                // ① 切页不重建宿主，提示不会被重放；② 作为最后一个子项盖在页面内容之上；
                // ③ 位于内容区底部，有底栏时浮在底栏**上方**，不挡三个 tab。
                BottomHint(
                    visible = hintText != null,
                    text = hintText.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 二级页不显示底栏，内容区直抵屏幕底 → 需自行避让系统导航栏
                        .then(if (showBottomBar) Modifier else Modifier.navigationBarsPadding()),
                )
            }

            // 底部导航（M12 增到三项：课表 / 待办 / 我的）
            if (showBottomBar) {
                AppBottomBar(
                    current = screen,
                    onOpenTimetable = popToRoot,
                    onOpenNote = { if (screen != AppScreen.Note) navigateTo(AppScreen.Note) },
                    onOpenProfile = { if (screen != AppScreen.Profile) navigateTo(AppScreen.Profile) },
                )
            }
        }
    }
}

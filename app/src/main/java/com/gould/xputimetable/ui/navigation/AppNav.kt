/*
 * AppNav.kt —— 应用内状态导航（M2-A：状态栈，不引入 Navigation 3）
 *
 * 决策依据（OPEN-DECISIONS.md 2026-09-17「M2 导航」）：navigation3 1.2.1 仍为 RC，
 * 沿用 MainActivity 的 sealed 状态导航模式并扩展（目标类型与 simpleFactory / ProfileDestination
 * 已拆至同包 Destinations.kt，M6 为守 300 行门禁）。
 *
 * M12（本轮）：内容区整体外移到同包 AppContent.kt。本文件现在只做三件事——
 *   1. 维护返回栈与转场方向；
 *   2. 持有跨页的全局提示通道（M7/M9 建立的 BottomHint 宿主）与系统返回键接线；
 *   3. 把「当前屏 + 依赖」交给 AppContent 渲染。
 * 这样拆的触发点是门禁：加了待办页（第三个底栏项）与背景层后，本文件会顶破 300 行，
 * 而"内容长什么样"与"栈怎么出栈"本就是两件事。
 *
 * 解析结果（NeedsConfirm）经导航状态传递给预览页；进程死亡会回到周视图，
 * 与既有状态导航的行为一致（未用 rememberSaveable 持久化）。
 */
package com.gould.xputimetable.ui.navigation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.domain.repository.TodoRepository
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.ui.theme.Hint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** 二次返回退出的提示文案（M7 起由 Snackbar 改为导航层浮层；M9 改为底部弹出）。 */
private const val EXIT_HINT = "再按一次退出应用"

@Composable
internal fun AppNav(
    repository: TimetableRepository,
    todoRepository: TodoRepository,
    jsonFileImporter: ScheduleImporter,
    xpuImporter: ScheduleImporter,
    canScheduleExact: () -> Boolean,
    /** M11：界面偏好（「显示老师姓名」）。课表页与设置页共用同一个实例。 */
    uiPrefs: UiPrefs,
    /** M12：背景偏好（图片 URI / 透明度 / 作用范围）。导航根读它来铺背景层。 */
    backgroundPrefs: BackgroundPrefs,
    /** M3：数据变更（编辑/导入/清理/学期）→ 重排提醒 + 立即刷新小组件（AC-18）。 */
    onDataChanged: suspend () -> Unit,
) {
    // 最小返回栈：栈底是首页；进入新页 push，返回 pop，导入完成回栈底（顺带修掉"导入中心→手动添加"的返回去向 bug）
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Timetable) }

    // M12：转场方向保留为显式状态（转场规格本身已在 ScreenTransition 里统一为纯淡入淡出，
    // 但保留方向意图，将来若要对某一向单独加效果，这里是唯一的接入点）。
    var navForward by remember { mutableStateOf(true) }

    fun navigateTo(next: AppScreen) {
        navForward = true
        backStack.add(next)
    }
    fun goBack() {
        if (backStack.size > 1) {
            navForward = false
            backStack.removeAt(backStack.lastIndex)
        }
    }
    fun popToRoot() {
        if (backStack.size > 1) {
            navForward = false
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        }
    }
    val screen: AppScreen = backStack.last()

    // 导航层持有的跨页提示（M2-C：清理完成后送回导入中心的 Snackbar）
    var hubNotice by remember { mutableStateOf<String?>(null) }

    // M9：**全局瞬时提示通道** —— 任何页面都能发一条底部提示
    // （退出提示 / 学期自动保存失败 / 学期已创建…）。宿主在导航层而非页面 Scaffold，
    // 因此切页不会重放（这正是原「保存学期」Snackbar 的缺陷来源）。
    var hintText by remember { mutableStateOf<String?>(null) }
    var hintNonce by remember { mutableIntStateOf(0) }
    val showHint: (String) -> Unit = remember {
        { message: String -> hintText = message; hintNonce++ }
    }
    // 显示 → 驻留 → 隐藏集中在这一处；发送方只管调用 showHint
    LaunchedEffect(hintNonce) {
        if (hintNonce == 0) return@LaunchedEffect
        delay(Hint.VisibleMillis)
        hintText = null
    }

    // 系统返回键接线（M9 抽到 BackKeyWiring.kt：纯接线 + 为 AppNav 腾行数）
    BackKeyWiring(
        backStackSize = { backStack.size },
        onPopBackStack = ::goBack,
        onShowExitHint = { showHint(EXIT_HINT) },
    )

    // 系统「减少动画」时直切（转场时长归零，读一次即可）
    val resolver = LocalContext.current.contentResolver
    val animated = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    AppContent(
        screen = screen,
        navForward = navForward,
        animated = animated,
        repository = repository,
        todoRepository = todoRepository,
        jsonFileImporter = jsonFileImporter,
        xpuImporter = xpuImporter,
        canScheduleExact = canScheduleExact,
        uiPrefs = uiPrefs,
        backgroundPrefs = backgroundPrefs,
        onDataChanged = onDataChanged,
        hubNotice = hubNotice,
        onHubNoticeShown = { hubNotice = null },
        onNotice = { hubNotice = it },
        hintText = hintText,
        showHint = showHint,
        navigateTo = { next -> navigateTo(next) },
        goBack = ::goBack,
        popToRoot = ::popToRoot,
    )
}

/*
 * ProfileFlowDestination.kt ——「我的」/ 学期设置 / 二级页 的路由装配
 *
 * 拆出来的实际原因（M11）：AppNav 里这三个目的地各占七八行，光这一组就三十多行，
 * 加转场与底部提示后 AppNav 顶到 333 行、破单文件 300 行门禁。它们同属"个人中心"
 * 支线，凑在一起反而更好读——以后加一个设置二级页，只改这个文件。
 *
 * 职责边界：本文件只负责"给当前屏幕补齐依赖"，**不持有返回栈、不参与返回判定**
 * （返回判定是 BackPolicy 里的纯函数，有单测覆盖）。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.runtime.Composable
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository

/**
 * 把「个人中心支线」的屏幕交给对应目的地；不属于本支线的屏幕直接不渲染。
 *
 * @param showHint   全局底部提示（导出结果 / 保存失败等瞬时反馈）
 * @param navigateTo 进下一屏（内部会把 navForward 置 true）
 * @param onDone     「完成即回栈底」（学期保存 / 导入完成）
 */
@Composable
internal fun ProfileFlowDestination(
    screen: AppScreen,
    repository: TimetableRepository,
    canScheduleExact: () -> Boolean,
    onDataChanged: suspend () -> Unit,
    uiPrefs: UiPrefs,
    /** M12：背景偏好（「我的」页的自定义背景入口要读写它）。 */
    backgroundPrefs: BackgroundPrefs,
    showHint: (String) -> Unit,
    navigateTo: (AppScreen) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    when (screen) {
        is AppScreen.Profile -> ProfileDestination(
            repository = repository,
            canScheduleExact = canScheduleExact,
            onDataChanged = onDataChanged,
            uiPrefs = uiPrefs,
            backgroundPrefs = backgroundPrefs,
            onShowHint = showHint,
            onOpenTermSetup = { navigateTo(AppScreen.TermSetup(fromEmptyState = false)) },
            onOpenSubPage = { page -> navigateTo(AppScreen.ProfileSub(page)) },
            onShareQr = { navigateTo(AppScreen.QrShare) },
        )

        is AppScreen.TermSetup -> TermSetupDestination(
            fromEmptyState = screen.fromEmptyState,
            repository = repository,
            canScheduleExact = canScheduleExact,
            onDataChanged = onDataChanged,
            uiPrefs = uiPrefs,
            onShowHint = showHint,
            onBack = onBack,
            onDone = onDone,
        )

        is AppScreen.ProfileSub -> ProfileSubDestination(
            page = screen.page,
            repository = repository,
            canScheduleExact = canScheduleExact,
            onDataChanged = onDataChanged,
            uiPrefs = uiPrefs,
            backgroundPrefs = backgroundPrefs,
            onShowHint = showHint,
            onBack = onBack,
        )

        else -> Unit
    }
}

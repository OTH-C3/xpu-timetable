/*
 * ScreenScope.kt —— AppScreen → 背景作用域 的映射（M12 需求二）
 *
 * 单独成文件而不是塞进 BackgroundScope.kt：那个文件属于 ui.background 底座层，
 * 不该知道导航层有哪些页面（反向依赖）。这里反向依赖 ui.navigation，是唯一允许
 * 认识 AppScreen 全集的地方 —— 将来加页面只改这一个 when，漏了会被编译发现。
 *
 * 判定口径见 BackgroundScope.kt 文件头（便签 / 我的 / 课表 三档）。
 */
package com.gould.xputimetable.ui.navigation

import com.gould.xputimetable.ui.background.BackgroundScope

/**
 * 当前屏幕属于哪个背景作用域。
 *
 * 「我的」支线（Profile / ProfileSub / TermSetup）里只有 Profile 与 ProfileSub 算"我的"；
 * 学期设置（TermSetup）虽然入口在「我的」，但它是填表页，归到"课表"档。
 */
internal fun AppScreen.backgroundScope(): BackgroundScope = when (this) {
    AppScreen.Timetable -> BackgroundScope.TIMETABLE
    AppScreen.Note -> BackgroundScope.NOTE
    is AppScreen.Profile, is AppScreen.ProfileSub -> BackgroundScope.PROFILE
    is AppScreen.TermSetup,
    is AppScreen.CourseEdit,
    is AppScreen.ImportHub,
    is AppScreen.CourseCapture,
    is AppScreen.Cleanup,
    is AppScreen.ImportPreview,
    AppScreen.QrShare,
    -> BackgroundScope.TIMETABLE
}

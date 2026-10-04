/*
 * MainActivity.kt —— 应用入口 Activity
 *
 * 作用：装配主题与页面导航。本文件只做装配，不含业务逻辑（架构 §10 约束，行数上限 100）。
 *
 * 导航说明（阶段性决策，见 ui/navigation/AppNav.kt 头注释）：四页面状态导航，
 * 暂不引入 Navigation 3（OPEN-DECISIONS「M2 导航」：navigation3 仍为 RC）。
 *
 * 依赖注入：从 TimetableApp（Application）拿到 AppContainer 并取出仓库与导入通道交给 ViewModel（ADR-004）。
 */
package com.gould.xputimetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.gould.xputimetable.ui.navigation.AppNav
import com.gould.xputimetable.ui.theme.XpuTimetableTheme
import com.gould.xputimetable.widget.WidgetRefresher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TimetableApp).container

        setContent {
            XpuTimetableTheme {
                AppNav(
                    repository = container.repository,
                    todoRepository = container.todoRepository,
                    jsonFileImporter = container.jsonFileImporter,
                    xpuImporter = container.xpuWebImporter,
                    canScheduleExact = container::canScheduleExactAlarms,
                    uiPrefs = container.uiPrefs,
                    backgroundPrefs = container.backgroundPrefs,
                    onDataChanged = container.onDataChanged,
                )
            }
        }
    }


    /**
     * M7 需求 4：回到前台时对齐一次桌面小组件。
     *
     * 小组件（Glance/RemoteViews）没有"变为可见"的回调，系统也无从告知 App 用户正在看桌面；
     * 原先刷新只由进程内事件驱动，导致"要看见新数据得先点进 App"。这里补上可控时机：
     * 只要用户与 App 有过交互（含从后台切回），就把小组件数据对齐一次。
     * 带 30 秒节流，避免频繁前后台切换造成无谓重绘（见 WidgetRefresher）。
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { WidgetRefresher.refreshOnForeground(this@MainActivity) }
        }
    }
}

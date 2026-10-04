/*
 * ProfileSubScreens.kt —— 「我的」页的两个二级页（M9）
 *
 *   - PERMISSIONS：精确闹钟授权说明 + 去授权（跳系统设置；授权状态在页面 ON_RESUME 时刷新）
 *   - ABOUT      ：关于与隐私声明
 * 两页共用同一壳（返回箭头 + 标题 + 可滚动内容），故放在一个文件里；
 * 学期设置页（含交互与自动保存）另起 TermSetupScreen.kt。
 *
 * 为什么权限要单独成页而不是页内一行按钮：与系统设置页一致（列表里是入口，说明与操作在详情页），
 * 也避免根页既有"按钮"又有"条目"两种形态混杂。
 */
package com.gould.xputimetable.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize

/** 「我的」页的二级页（M9；M12 增 BACKGROUND）。 */
enum class ProfileSubPage { PERMISSIONS, ABOUT, BACKGROUND }

// ---------- 文件级文案常量 ----------
private const val TITLE_PERMISSIONS = "精确闹钟授权"
private const val TITLE_ABOUT = "关于与隐私"
private const val CD_BACK = "返回"
private const val P_HINT_TITLE = "这个权限用来做什么"
private const val P_HINT_BODY =
    "桌面小组件要在两个时刻自动刷新：每天 00:05 跨天，以及每节课结束时。" +
        "这两个时刻由「精确闹钟」触发。未授权时系统会把它降级为不精确闹钟" +
        "（省电策略下最长延后约 1 小时），表现为小组件更新不及时。"
private const val P_HINT_UNAFFECTED = "不授权不影响课表本身：打开 App 看课表、编辑课程都正常。"
private const val P_STATE_OK = "当前状态：已授权"
private const val P_STATE_MISSING = "当前状态：未授权"
private const val P_ACTION = "去授权"
private const val P_SYSTEM_ONLY = "若系统未弹出授权页，请在系统设置 → 应用 → 本应用 → 其他权限 中手动开启。"
private const val A_LINE_1 = "本应用为非官方的校园工具，与学校无关；课表数据仅保存在本机。"
private const val A_LINE_2 = "不读取、不存储、不上传任何教务凭据；登录只发生在你自己的设备上。"
private const val A_LINE_3 = "开源协议 GPL-3.0。"

/** 两个二级页的共用壳：返回箭头 + 标题 + 可滚动内容。M12 起对同包的新页面也开放。 */
@Composable
internal fun ProfileSubScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        // M12 需求一.2：底色与背景图由导航根的 PageBackground 统一提供，这里必须透明
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(AppIcons.chevronLeft),
                        contentDescription = CD_BACK,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ProfileSubScreen(
    page: ProfileSubPage,
    viewModel: SettingsViewModel,
    /** M12 需求二：背景设置页要读写它（其余二级页不用，故为可空）。 */
    backgroundPrefs: BackgroundPrefs?,
    uiPrefs: UiPrefs,
    repository: TimetableRepository,
    onShowHint: (String) -> Unit,
    onBack: () -> Unit,
) {
    when (page) {
        ProfileSubPage.PERMISSIONS -> PermissionsSubScreen(viewModel = viewModel, onBack = onBack)
        ProfileSubPage.ABOUT -> AboutSubScreen(onBack = onBack)
        // 依赖缺失时安静降级为空白页：理论上导航层总会传（见 ProfileSubDestination），
        // 但让一个二级页把整个应用搞崩没有意义
        ProfileSubPage.BACKGROUND -> if (backgroundPrefs != null) {
            BackgroundScreen(
                backgroundPrefs = backgroundPrefs,
                uiPrefs = uiPrefs,
                repository = repository,
                onBack = onBack,
                onShowHint = onShowHint,
            )
        }
    }
}

@Composable
private fun PermissionsSubScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // 从系统设置页返回时刷新授权状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ProfileSubScaffold(title = TITLE_PERMISSIONS, onBack = onBack) {
        Text(P_HINT_TITLE, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = P_HINT_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = P_HINT_UNAFFECTED,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state.exactAlarmAllowed) P_STATE_OK else P_STATE_MISSING,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!state.exactAlarmAllowed && Build.VERSION.SDK_INT >= 31) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }) { Text(P_ACTION) }
            Spacer(Modifier.height(8.dp))
            Text(
                text = P_SYSTEM_ONLY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutSubScreen(onBack: () -> Unit) {
    ProfileSubScaffold(title = TITLE_ABOUT, onBack = onBack) {
        listOf(A_LINE_1, A_LINE_2, A_LINE_3).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

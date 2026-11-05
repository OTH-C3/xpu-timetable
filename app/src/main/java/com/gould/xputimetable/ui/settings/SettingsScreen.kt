/*
 * SettingsScreen.kt —— 「我的」页根页（M9 重构为分组列表 + 二级页）
 *
 * 结构参照系统设置页（产品负责人要求，见 M9 需求）：
 *   我的
 *     ── 学期 ──   学期设置            →
 *     ── 权限 ──   精确闹钟授权        →
 *     ── 数据 ──   导出课表文件 / 二维码分享课表
 *     ── 关于 ──   关于与隐私          →
 * 学期与总周数已移入二级页「学期设置」，且**去掉了手动保存按钮**（改为自动保存）。
 *
 * 本页不再持有 SnackbarHost：所有瞬时反馈都走导航层的底部提示通道（onShowHint）——
 * 原方案的 Snackbar 挂在页面 Scaffold 上，切页重挂会把未过期的提示重放一次。
 *
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；图标只经 AppIcons。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.DEFAULT_BACKGROUND_SETTINGS
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.background.label
import com.gould.xputimetable.ui.background.scopeSummary
import com.gould.xputimetable.ui.transfer.ExportSection

// ---------- 文件级文案常量 ----------
private const val TITLE = "我的"
private const val GROUP_TERM = "学期"
private const val ROW_TERM = "学期设置"
private const val TERM_NONE = "还没有学期，点此设置"
// M11-第三批：把原来的「权限」+「显示」合成一个多条目组、「数据」改叫「隐私」。
// 原因：原先 5 个组里 4 个组只有 1 条，组内横线永远只有一个"最后一条不画线"的空转，
// 老大道的"几种相同的设置用一个边框框起来、里面的几条用横线分开"根本看不出分组。
// 合成 4 组（其中 2 组是多条）后，"卡片 = 一组、横线 = 组内切分"这套版式才立得住；
// 组名也学参考截图那样取短词（功能 / 隐私），不再用"权限/数据/显示"这种功能自述。
private const val GROUP_APPEARANCE = "外观"
private const val ROW_BACKGROUND = "自定义背景"
private const val BG_NONE = "使用默认底色"
private const val BG_SET = "已设置 · 作用范围：%s"
private const val ROW_PERMISSION = "精确闹钟授权"
private const val PERMISSION_OK = "已授权"
private const val PERMISSION_MISSING = "未授权：桌面小组件刷新可能不精准"
private const val ROW_SHOW_TEACHER = "显示老师姓名"
private const val SHOW_TEACHER_SUBTITLE = "在课程卡上显示任课教师"
private const val GROUP_PRIVACY = "隐私"
private const val GROUP_ABOUT = "关于"
private const val ROW_ABOUT = "关于与隐私"
private const val ABOUT_SUBTITLE = "非官方校园工具 · 数据仅存本机"
private const val SEP = " · "
private const val LABEL_START_DATE = "起始日 "
private const val SUFFIX_WEEK = " 周"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /** 导出课表文件需要仓库读取激活学期快照。 */
    repository: TimetableRepository,
    /** M12 需求二：背景设置页与根页摘要行都要读它。 */
    backgroundPrefs: BackgroundPrefs,
    /** 进「学期设置」二级页。 */
    onOpenTermSetup: () -> Unit,
    /** 进其他二级页（权限 / 关于）。 */
    onOpenSubPage: (ProfileSubPage) -> Unit,
    /** 跳转二维码分享页。 */
    onShareQr: () -> Unit,
    /** 全局底部提示（导出结果 / 保存失败等）。 */
    onShowHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    // M11：「显示老师姓名」开关（课程卡据此增减教师行）
    val showTeacher by viewModel.showTeacher.collectAsState()
    // M12 需求二：背景设置摘要（根页这一行要显示"设了没、作用在哪"）
    val background by backgroundPrefs.settings
        .collectAsStateWithLifecycle(initialValue = DEFAULT_BACKGROUND_SETTINGS)
    val backgroundSummary = if (background.imageUri.isNullOrBlank()) {
        BG_NONE
    } else {
        BG_SET.format(scopeSummary(background.scopes))
    }

    Scaffold(
        // M12 需求一.2：底色与背景图由导航根的 PageBackground 统一提供，这里必须透明
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // M11：每组一个卡片容器（组标题在卡片外，组内横线不满宽）
            SettingsGroupHeader(GROUP_TERM)
            SettingsGroupCard {
                SettingsNavRow(
                    title = ROW_TERM,
                    subtitle = termSummary(state),
                    onClick = onOpenTermSetup,
                    showDivider = false,
                )
            }

            // M11-第三批：开关与权限合并进同一个卡片 —— 中间那条横线是这一组的分界线
            SettingsGroupHeader(GROUP_APPEARANCE)
            SettingsGroupCard {
                SettingsNavRow(
                    title = ROW_BACKGROUND,
                    subtitle = backgroundSummary,
                    onClick = { onOpenSubPage(ProfileSubPage.BACKGROUND) },
                    showDivider = true,
                )
                SettingsSwitchRow(
                    title = ROW_SHOW_TEACHER,
                    subtitle = SHOW_TEACHER_SUBTITLE,
                    checked = showTeacher,
                    onCheckedChange = viewModel::toggleShowTeacher,
                    showDivider = true, // 与下面的「精确闹钟授权」之间画线（组内有 2 条）
                )
                SettingsNavRow(
                    title = ROW_PERMISSION,
                    subtitle = if (state.exactAlarmAllowed) PERMISSION_OK else PERMISSION_MISSING,
                    onClick = { onOpenSubPage(ProfileSubPage.PERMISSIONS) },
                    showDivider = false,
                )
            }

            SettingsGroupHeader(GROUP_PRIVACY)
            SettingsGroupCard {
                ExportSection(
                    repository = repository,
                    onShowHint = onShowHint,
                    onShareQr = onShareQr,
                )
            }

            SettingsGroupHeader(GROUP_ABOUT)
            SettingsGroupCard {
                SettingsNavRow(
                    title = ROW_ABOUT,
                    subtitle = ABOUT_SUBTITLE,
                    onClick = { onOpenSubPage(ProfileSubPage.ABOUT) },
                    showDivider = false,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 学期一行摘要：没学期时给引导；有学期时把"起始日 / 总周数"这两个关键值露在行上。 */
private fun termSummary(state: SettingsUiState): String {
    if (!state.hasTerm) return TERM_NONE
    val namePart = if (state.termName.isBlank()) "" else state.termName + SEP
    return namePart + LABEL_START_DATE + state.startDate + SEP + state.totalWeeks + SUFFIX_WEEK
}

/*
 * BackgroundOpacitySheet.kt —— 不透明度的全屏实时预览（M12 需求二.2）
 *
 * 需求原文：「拖动滑块时全屏实时预览课表主页（只显示课表主页内容和透明度条），松手即应用当前透明度」
 *
 * 三个实现要点：
 *   1. **只显示课表主页内容和透明度条**：不套 Dialog（Dialog 会自带一层 scrim 与圆角，
 *      那就不是"全屏"了），而是用 Box 覆盖整个窗口 + 顶部一条返回/完成。
 *   2. **拖动时实时**：滑块值直接驱动 state，每一帧重绘；背景不透明度用同一份 state。
 *   3. **松手即应用**：onValueChangeFinished 才写库 —— 拖动过程中不落盘，
 *      否则一次拖动会写上百次 DataStore。
 *
 * 预览里画的是**真实周视图**（TimetableScreen），不是截图：所见即所得，
 * 也顺带让用户在这里就能确认"这个透明度下课程卡还读得清吗"。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gould.xputimetable.data.prefs.BackgroundSettings
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.background.BackgroundScope
import com.gould.xputimetable.ui.background.PageBackground
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.navigation.simpleFactory
import com.gould.xputimetable.ui.timetable.TimetableScreen
import com.gould.xputimetable.ui.timetable.TimetableViewModel
import com.gould.xputimetable.ui.theme.Hint
import com.gould.xputimetable.ui.theme.ListRow

// ---------- 文案 ----------
private const val TITLE = "调整不透明度"
private const val HINT = "拖动实时预览，松手即应用"
private const val CD_BACK = "返回"
private const val VALUE_FMT = "%.0f%%"

/** 滑块步长：1%，够细又不至于让值域变得难以拖准。 */
private const val STEPS = 99

/**
 * 全屏实时预览层。
 *
 * @param current 已保存的背景设置（图片 URI 与作用范围都取它；只有不透明度被 live 覆盖）
 * @param uiPrefs 「显示老师姓名」开关，预览的周视图要跟着真实设置走
 * @param onApply 松手时回调，把最终不透明度写库
 * @param onDismiss 退出预览
 * @param onNoTerm 预览里点「创建本学期」时回调（这层是只读演示，不能真的导航）
 */
@Composable
internal fun BackgroundOpacitySheet(
    current: BackgroundSettings,
    uiPrefs: UiPrefs,
    repository: TimetableRepository,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
    onNoTerm: () -> Unit = {},
) {
    // 拖动过程中的实时值；只有 onValueChangeFinished 才落到 onApply
    var live by remember { mutableFloatStateOf(current.opacity.coerceIn(0f, 1f)) }
    val density = LocalDensity.current.density
    val screenPx = with(LocalConfiguration.current) {
        maxOf(screenWidthDp, screenHeightDp) * density
    }.toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        // ---------- 背景 + 真实课表主页 ----------
        // 预览的是「这张图 + 这个不透明度，铺在课表主页上是什么效果」。
        //
        // ⚠️ 所以 settings 与 scope **都**要强制成 TIMETABLE，不能沿用用户选的作用范围：
        //   若沿用（用户选「便签」），shouldDrawBackground(NOTE, TIMETABLE) = false →
        //   预览里根本不画图，拖滑块毫无反应，用户以为功能坏了。
        // 强制成课表后，无论作用范围选哪一项，这里的预览都是有效的效果演示。
        PageBackground(
            settings = current.copy(
                opacity = live,
                // M13 起作用范围是集合（多选），这里强制补上课表档保证预览有图
                scopes = current.scopes + BackgroundScope.TIMETABLE,
            ),
            scope = BackgroundScope.TIMETABLE,
            maxPixels = screenPx,
        ) {
            val vm: TimetableViewModel = viewModel(
                factory = simpleFactory { TimetableViewModel(repository, uiPrefs.showTeacher) },
            )
            TimetableScreen(
                viewModel = vm,
                snackbarHostState = remember { SnackbarHostState() },
                onAddCourse = {},
                onEditCourse = { _, _ -> },
                onOpenImport = {},
                // 预览层里的周视图是**只读演示**：点「创建本学期」不该把用户带进填表页
                // （那层覆盖在设置页之上，导航语义是空的）。没学期时用回调提示一句，
                // 而不是让按钮点了没反应 —— 空回调是本项目明令禁止的"静默失效"。
                onCreateTerm = { onNoTerm() },
            )
        }

        // ---------- 顶部条：返回 + 标题 ----------
        // 必须垫一层底衬：这层是**全屏覆盖**，下面的周视图标题（「第 6 周」「周日」）
        // 还在原位，不垫底的话两行字直接叠在一起，谁也读不清（实测背景图上尤其明显）。
        // 底衬用提示浮层那套深色，与底部透明度条同语言。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hint.ContainerColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(AppIcons.chevronLeft),
                    contentDescription = CD_BACK,
                    // 底衬是深色，字必须用浅色（沿用底部透明度条的 Hint.ContentColor）
                    tint = Hint.ContentColor,
                )
            }
            Text(
                text = TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = Hint.ContentColor,
            )
        }

        // ---------- 底部：透明度条 ----------
        OpacityBar(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = {
                onApply(live)
                onDismiss()
            },
            // ⚠️ 这一层是全屏覆盖，没有 Scaffold 帮它吃系统栏内边距 →
            // 必须自己 navigationBarsPadding()，否则三按钮导航下整条滑块被压住。
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

/** 底部的透明度条（固定黑灰底，与提示浮层同一套语言）。 */
@Composable
private fun OpacityBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ListRow.CardCorner))
            .background(Hint.ContainerColor)
            .padding(horizontal = Hint.HorizontalPadding, vertical = Hint.VerticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = HINT,
                style = MaterialTheme.typography.bodySmall,
                color = Hint.ContentColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = VALUE_FMT.format(value * 100),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Hint.ContentColor,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            // steps = 99 → 共 101 个刻度（含两端），即 1% 一档
            steps = STEPS,
        )
    }
}

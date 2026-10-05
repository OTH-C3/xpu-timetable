/*
 * AppBottomBar.kt —— 底部导航（M4-UI R7 + M5 需求 3 + M6-fix）
 *
 * 三个根页（课表 / 待办 / 我的），不做日视图或其它 tab（规格 §5 明确不做日视图）。
 * M12 需求五从两项增到三项。
 * M5 需求 3：高度 40dp、背景 = 主页背景 50% 透明、tonalElevation 0、文字 10sp。
 * M6-fix ①：去掉图标，只保留文字 → NavigationBar 的条目项 icon 为必填且布局固定为
 *   「icon 上 / label 下」，无法只留 label，故改为自定义 Row + 可点 Text。
 * M6-fix ②：底部必须 navigationBarsPadding() 避让系统导航栏。
 *   原实现用 NavigationBar + 外层 .height(40.dp)，会把 NavigationBar 自带的
 *   windowInsets 压扁（底部 insets 48dp > 高度 40dp → 内容区被压到 0 → 底栏消失）；
 *   且本底栏直接放在 AppContent 的 Column 末尾，不在 Scaffold 内、拿不到 innerPadding。
 *   三按钮导航下被系统导航栏完全覆盖（实测点击会落到导航栏）。详见 docs/verify/M6/。
 * 显隐由 AppContent 控制：仅在三个根页显示；二级页保持沉浸。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gould.xputimetable.ui.components.clickableNoRipple

private const val LABEL_TIMETABLE = "课表"
/** M12 需求五：待办（对应「便签」那一档背景作用域）。 */
private const val LABEL_NOTE = "待办"
private const val LABEL_PROFILE = "我的"

/** M5 需求 3 的底栏高度（保持 40dp，不因去图标而改动既定尺寸）。 */
private val BarHeight = 40.dp

/**
 * M6-fix：无图标后文字是唯一元素，10sp 偏小 → 12sp；M10 产品要求再加大一号 → 13sp；
 * M11-第三批：老大要求"主页最下面的文字加大一个字好" → 14sp。
 * 只动字号，高度仍是 BarHeight(40dp)：加大后靠 Box 居中，不会挤压触摸目标宽度。
 */
private val LabelFontSize = 14.sp

@Composable
internal fun AppBottomBar(
    current: AppScreen,
    onOpenTimetable: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M10：**不再给底栏单独画背景**（原先是不透明底色的 50% 叠加，与页面底色并不完全一致）。
    // 不画背景 → 底栏自然透出页面背景，两者严格同色；
    // 也是 M12 需求二"自定义背景"能铺满全屏的前提：换背景图时底栏不会压出一条色带。
    Row(
        modifier = modifier
            .fillMaxWidth()
            // M6-fix ②：必须在 height **之前**（外层）—— 否则 insets 会被 40dp 压扁。
            .navigationBarsPadding()
            .height(BarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
    // 顺序：**待办 / 课表 / 我的**（M12 老大定稿）。
    // 课表放中间是这条产品线的惯例：它是使用频率最高的一页，落在拇指最容易够到的中位；
    // 两个低频页分列两侧。最初排的是「课表 / 待办 / 我的」（课表在左），
    // 老大看过实机后要求把课表挪到中间。
    BottomBarLabel(
        label = LABEL_NOTE,
        isSelected = current == AppScreen.Note,
        onClick = onOpenNote,
        modifier = Modifier.weight(1f),
    )
    BottomBarLabel(
        label = LABEL_TIMETABLE,
        isSelected = current == AppScreen.Timetable,
        onClick = onOpenTimetable,
        modifier = Modifier.weight(1f),
    )
    BottomBarLabel(
        label = LABEL_PROFILE,
        isSelected = current == AppScreen.Profile,
        onClick = onOpenProfile,
        modifier = Modifier.weight(1f),
    )
    }
}

/**
 * 底栏的单个文字项（M6-fix：无图标版本的 tab）。
 * 触摸区 = 整块（fillMaxHeight × weight），因此 40dp 高度即触摸目标高度。
 */
@Composable
private fun BottomBarLabel(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中态：唯一强调色 primary + 加粗；未选中：onSurfaceVariant + 常规
    // （与既有设计一致：全项目唯一强调色 = colorScheme.primary，只给关键状态）
    // M10：选中态文字改**黑色**（onSurface），不再用强调色 primary ——
    // 产品要求"所有蓝色选中字样改成黑色"。选中与未选中靠字重区分（SemiBold vs Normal）。
    val color = if (isSelected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxHeight()
            // M12 需求一：不能用 clickable —— 它默认带 indication（Material 水波纹），
            // 而底栏按 M10 的决定**没有自己的背景**（要透出页面底色），
            // 于是水波纹直接画在透明底上：按下时整块 40dp × 1/3 屏宽泛出深色方块，
            // 看起来就是"点文字闪一个黑框"。水波纹的语义是"这块浮在内容之上"，
            // 而底栏是贴在背景上的一层，不该有。
            .clickableNoRipple(role = Role.Tab, onClick = onClick)
            .semantics {
                role = Role.Tab           // 无障碍：告诉读屏这是 tab
                selected = isSelected     // 无障碍：暴露选中态（替代原 M3 底栏条目项的语义）
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = LabelFontSize,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}

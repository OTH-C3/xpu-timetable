/*
 * CardAlphaSheet.kt —— 卡片（白色方框）不透明度的全屏实时预览（M13）
 *
 * 需求原文：「为白色UI方框增加透明度调节选项」。
 *
 * 和 BackgroundOpacitySheet 是两套东西，别混：
 *   背景图不透明度 = 那张**图**铺在页面底色上的浓度（图有多淡）
 *   卡片不透明度  = 卡片**面**本身有多透（方框有多玻璃）
 * 两者叠在一起才是最终观感，所以必须单独一根滑块，不能拿背景图那根的复用。
 *
 * 预览为什么画"几张真卡片"而不是整页课表：
 * 玻璃面只长在 GroupCard 上（课表面的课程卡是自己的实心实现，不吃玻璃色）。
 * 把整个周视图挂上来，用户盯着课表拖半天，而拖动的这个值偏偏作用在别处
 * （设置页的方框、清单卡），等于给他看一个不会动的参照物。
 * 这里直接摆一组真实的 GroupCard —— 底色与色相仍由 PageBackground 下发，仍所见即所得。
 *
 * ⚠️ 全屏覆盖层没有 Scaffold 帮它吃系统栏内边距，顶栏自己 statusBarsPadding()、
 *   滑块条自己 navigationBarsPadding()，否则顶栏压在状态栏下、三按钮导航盖住滑块。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.data.prefs.BackgroundSettings
import com.gould.xputimetable.ui.background.BackgroundScope
import com.gould.xputimetable.ui.background.PageBackground
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.components.GroupCard
import com.gould.xputimetable.ui.theme.GlassCard
import com.gould.xputimetable.ui.theme.Hint
import com.gould.xputimetable.ui.theme.ListRow

// ---------- 文案 ----------
private const val TITLE = "调整卡片透明度"
private const val HINT = "拖动实时预览，松手即应用"
private const val CD_BACK = "返回"
private const val VALUE_FMT = "%.0f%%"
private const val PREVIEW_TIP = "这两张就是全项目方框的观感"
private const val PREVIEW_A_TITLE = "高等数学 · 乔峰"
private const val PREVIEW_A_SUB = "周一 1-2 节 · 教三 301"
private const val PREVIEW_A2_TITLE = "大学英语 · 段誉"
private const val PREVIEW_A2_SUB = "周二 3-4 节 · 教四 208"
private const val PREVIEW_B_TITLE = "体育 · 虚竹"
private const val PREVIEW_B_SUB = "周五 5-6 节 · 东操场"

/**
 * 卡片透明度的全屏实时预览层。
 *
 * @param current 已保存的背景设置（图片与作用范围都取它；只有卡片透明度被 live 覆盖）
 * @param onApply 松手时回调，把最终值写库
 * @param onDismiss 退出预览
 */
@Composable
internal fun CardAlphaSheet(
    current: BackgroundSettings,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // 与 BackgroundOpacitySheet 同一口径：拖动过程不落盘（一次拖动会写上百次 DataStore），
    // 只在 onValueChangeFinished 回调出去。
    var live by remember {
        mutableFloatStateOf(current.cardAlpha.coerceIn(GlassCard.MinAlpha, GlassCard.MaxAlpha))
    }
    val density = LocalDensity.current.density
    val screenPx = with(LocalConfiguration.current) {
        maxOf(screenWidthDp, screenHeightDp) * density
    }.toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        PageBackground(
            // ⚠️ 作用范围必须强制成"含课表"：沿用用户勾的那几档时，若他没勾课表，
            // 这里的背景图压根不画 → 卡片退到"没图"那档，拖滑块画面纹丝不动。
            // M13 起 scope 是集合（多选），所以是 setOf 而不是单值。
            settings = current.copy(
                cardAlpha = live,
                scopes = current.scopes + BackgroundScope.TIMETABLE,
            ),
            scope = BackgroundScope.TIMETABLE,
            // 必传屏幕尺寸：传 0 会被 BackgroundImageLoader 当成"不解码"，
            // 预览就变成纯色背景，卡片透明度少了一个可对比的参照。
            maxPixels = screenPx,
        ) {
            Column(
                // 竖向留白避开顶部返回条与底部滑块条，别让预览被两头压住
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = PREVIEW_TIP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ListRow.GroupSpacing))
                SampleCard(
                    firstTitle = PREVIEW_A_TITLE,
                    firstSub = PREVIEW_A_SUB,
                    secondTitle = PREVIEW_A2_TITLE,
                    secondSub = PREVIEW_A2_SUB,
                )
                Spacer(Modifier.height(12.dp))
                SampleCard(
                    firstTitle = PREVIEW_B_TITLE,
                    firstSub = PREVIEW_B_SUB,
                    secondTitle = null,
                    secondSub = null,
                )
            }
        }

        // 顶栏必须垫底衬：这层是全屏覆盖，预览的字还在原位，不垫就叠字。
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
                    // 底衬是深色（走 Hint 那套），字与图标都得用它的浅色
                    tint = Hint.ContentColor,
                )
            }
            Text(
                text = TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = Hint.ContentColor,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
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
                    text = VALUE_FMT.format(live * 100),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Hint.ContentColor,
                )
            }
            Slider(
                value = live,
                onValueChange = { live = it },
                onValueChangeFinished = {
                    onApply(live)
                    onDismiss()
                },
                valueRange = GlassCard.MinAlpha..GlassCard.MaxAlpha,
                // 与主背景那根滑块同口径（1% 一档），只是起点不是 0 而是可读性下限
                steps = GlassCard.Steps,
            )
        }
    }
}

/**
 * 预览用的样例方框（内部就是真实的 GroupCard）。
 *
 * @param secondTitle 为 null 时只画一行 —— 用来顺带看一眼"少一条目"的卡片高度感
 */
@Composable
private fun SampleCard(
    firstTitle: String,
    firstSub: String,
    secondTitle: String?,
    secondSub: String?,
) = GroupCard {
    Column {
        SampleRow(firstTitle, firstSub, showDivider = secondTitle != null)
        if (secondTitle != null) SampleRow(secondTitle, secondSub.orEmpty(), showDivider = false)
    }
}

/** 样例条目：标题 + 副标题 + 组内横线（版式对齐「我的」页的分组条目）。 */
@Composable
private fun SampleRow(title: String, subtitle: String, showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListRow.MinHeight)
            .padding(horizontal = ListRow.HorizontalPadding, vertical = ListRow.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(ListRow.IconGap))
    }
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListRow.DividerInset)
                .height(1.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = ListRow.DividerAlpha),
                ),
        )
    }
}

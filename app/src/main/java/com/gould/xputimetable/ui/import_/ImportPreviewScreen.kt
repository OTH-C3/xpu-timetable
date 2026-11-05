/*
 * ImportPreviewScreen.kt —— 导入预览确认页（AC-10/13/21 的落地界面）
 *
 * 铁律：确认前只读（计数/覆盖范围/异常/疑似重复全部来自 ViewModel 状态），
 * 唯一写入口是「确认导入」按钮 → viewModel.confirm()。入库成功自动返回周视图。
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding()；底部固定栏 navigationBarsPadding()；
 *   含输入框的页面 imePadding()；回调方法引用。
 */
package com.gould.xputimetable.ui.import_

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.import_.components.AnomalyCard
import com.gould.xputimetable.ui.import_.components.CoverageCard
import com.gould.xputimetable.ui.import_.components.DuplicateCard
import com.gould.xputimetable.ui.import_.components.ImportModeSelector
import com.gould.xputimetable.ui.import_.components.PreviewStatRow
import com.gould.xputimetable.ui.theme.IconSize

// ---------- 文件级文案常量 ----------

private const val PREVIEW_TITLE = "确认导入"
private const val PREVIEW_LOADING = "正在读取当前课表…"
private const val NO_TERM_MESSAGE = "还没有学期，无法导入。请先回到周视图创建本学期。"
private const val ACTION_CANCEL = "取消"
private const val ACTION_CONFIRM = "确认导入"
private const val ACTION_BACK = "返回"

@Composable
fun ImportPreviewScreen(
    viewModel: ImportPreviewViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // 入库成功 → 回周视图（消费在导航层，本页不再重复触发）
    LaunchedEffect(state.committed) {
        if (state.committed) onDone()
    }

    Scaffold(
        // M12 需求一.2：底色与背景图由导航根的 PageBackground 统一提供，这里必须透明
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(AppIcons.chevronLeft),
                        contentDescription = ACTION_BACK,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
                Text(text = PREVIEW_TITLE, style = MaterialTheme.typography.titleLarge)
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // M6-fix：M3 的 Scaffold 不会给 bottomBar 槽加窗口 insets，必须自己避让系统导航栏
                    // 顺序要求：navigationBarsPadding 在普通 padding **之前**（外层），
                    // 否则底部 insets 会挤进普通 padding 的空间
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(text = ACTION_CANCEL)
                }
                Button(
                    onClick = viewModel::confirm,
                    enabled = state.loading.not() && state.noTerm.not() && state.committing.not(),
                    modifier = Modifier.weight(2f),
                ) {
                    if (state.committing) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.Medium))
                    } else {
                        Text(text = ACTION_CONFIRM)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            when {
                state.loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.Large))
                        Spacer(Modifier.size(12.dp))
                        Text(text = PREVIEW_LOADING, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.noTerm -> {
                    Text(
                        text = NO_TERM_MESSAGE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    PreviewStatRow(
                        courseCount = state.courseCount,
                        sessionCount = state.sessionCount,
                    )
                    // M7 需求 1：入库方式二选一，选择结果随 CoverageCard 文案同步反映
                    ImportModeSelector(mode = state.mode, onModeChange = viewModel::setMode)
                    CoverageCard(
                        existingCount = state.existingCount,
                        sourceLabel = state.sourceLabel,
                        mode = state.mode,
                    )
                    DuplicateCard(duplicateNames = state.suspectedDuplicates)
                    AnomalyCard(anomalies = state.anomalies)
                }
            }
            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

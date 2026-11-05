/*
 * CleanupScreen.kt —— 按来源清理页（M2-C 任务 2，AC-24）
 *
 * 列出当前学期「教务导入 / 文件导入」的课程数（N=0 置灰）；选择来源后**二次确认**
 * （文案明确「将删除该来源的 N 门课程；手动添加的课程不会被删除」）；确认执行后
 * 回导入中心并 Snackbar 报「已清理 N 门」。
 *
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；回调方法引用。
 */
package com.gould.xputimetable.ui.import_

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize

// ---------- 文件级文案常量 ----------

private const val CLEANUP_TITLE = "清理导入的课程"
private const val CLEANUP_SUBTITLE = "删除某个来源导入的全部课程；手动添加的课程不会被删除"
private const val CLEANUP_MANUAL_NOTE = "手动添加的课程不会被删除"
private const val CLEANUP_WEB_LABEL = "教务导入"
private const val CLEANUP_CSV_LABEL = "文件导入"
private const val CLEANUP_COURSES_SUFFIX = "门"
private const val CLEANUP_LOADING = "正在统计课程数…"
private const val CLEANUP_RUNNING = "正在清理…"
private const val ACTION_BACK = "返回"
private const val ACTION_CANCEL = "取消"
private const val ACTION_DELETE = "删除"
private const val DIALOG_TITLE = "删除该来源的课程？"
private const val EMPTY_HINT = "本学期该来源没有课程"

@Composable
fun CleanupScreen(
    viewModel: CleanupViewModel,
    onBack: () -> Unit,
    onCleaned: (count: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // 清理完成 → 通知导航层（回导入中心 + Snackbar「已清理 N 门」），消费后清事件
    LaunchedEffect(state.cleanedCount) {
        state.cleanedCount?.let { count ->
            viewModel.consumeCleaned()
            onCleaned(count)
        }
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
                Text(text = CLEANUP_TITLE, style = MaterialTheme.typography.titleLarge)
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
            Text(
                text = CLEANUP_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(IconSize.Large))
                    Spacer(Modifier.size(12.dp))
                    Text(text = CLEANUP_LOADING, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                CleanupSourceRow(
                    label = CLEANUP_WEB_LABEL,
                    count = state.webCount,
                    onClick = remember { { viewModel.requestClean(CourseSource.WEB) } },
                )
                CleanupSourceRow(
                    label = CLEANUP_CSV_LABEL,
                    count = state.csvCount,
                    onClick = remember { { viewModel.requestClean(CourseSource.WAKEUP_CSV) } },
                )
                if (state.cleaning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.Large))
                        Spacer(Modifier.size(12.dp))
                        Text(text = CLEANUP_RUNNING, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 二次确认弹窗（AC-24：文案明确数量与 MANUAL 保护）
    state.confirmSource?.let { source ->
        val count = if (source == CourseSource.WEB) state.webCount else state.csvCount
        val label = if (source == CourseSource.WEB) CLEANUP_WEB_LABEL else CLEANUP_CSV_LABEL
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(text = DIALOG_TITLE) },
            text = {
                Text(text = "将删除${label}的 $count ${CLEANUP_COURSES_SUFFIX}课程；$CLEANUP_MANUAL_NOTE")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmClean) { Text(text = ACTION_DELETE) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(text = ACTION_CANCEL) }
            },
        )
    }
}

/** 单个来源行：名称 + 课程数；count = 0 时置灰不可点。 */
@Composable
private fun CleanupSourceRow(label: String, count: Int, onClick: () -> Unit) {
    val enabled = count > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = if (enabled) "$count $CLEANUP_COURSES_SUFFIX" else EMPTY_HINT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClick, enabled = enabled) { Text(text = ACTION_DELETE) }
    }
}

/*
 * ImportHubScreen.kt —— 导入中心页（M2-A）
 *
 * 通道：教务直连（内置 WebView 登录并自动识别课表）/ 从文件导入（本 App 导出的 .json）/
 * 扫二维码导入 / 手动添加。文件选择用 ActivityResultContracts.OpenDocument（SAF，无需存储权限）。
 * 解析失败在原地展示（AC-11），成功则导航到预览页确认（AC-10）。
 *
 * M9：**WakeUp CSV 通道已删除**（产品负责人要求）——文件类导入只剩本 App 导出的 JSON
 * 与二维码截图两条（后者在导航层解出文本后走同一入口）。
 *
 * 界面铁律（Spec §10）：Scaffold + 自绘顶栏 statusBarsPadding；文案全部文件级常量；
 * 回调用方法引用；图标只经 AppIcons；本页无文本输入故无需 imePadding。
 */
package com.gould.xputimetable.ui.import_

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.import_.components.ImportChannelCard
import com.gould.xputimetable.ui.import_.components.ParseFailureCard
import com.gould.xputimetable.ui.theme.IconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- 文件级文案常量（Spec §10 铁律 2：禁止组合期新建选项集合）----------

private const val HUB_TITLE = "导入课表"
private const val HUB_SUBTITLE = "从以下通道导入你的课表；文件导入不联网也能用"
private const val CHANNEL_WEB_TITLE = "教务直连"
private const val CHANNEL_WEB_SUBTITLE = "在学校教务系统页完成登录，自动识别课表"
private const val CHANNEL_JSON_TITLE = "从文件导入课表"
private const val CHANNEL_JSON_SUBTITLE = "选择本 App 导出的 .json 文件"
private const val CHANNEL_QR_TITLE = "扫二维码导入"
private const val CHANNEL_QR_SUBTITLE = "选择相册里的二维码截图"
private const val CHANNEL_MANUAL_TITLE = "手动添加"
private const val CHANNEL_MANUAL_SUBTITLE = "一门一门录入，适合只补几门课"
private const val CLEANUP_ENTRY = "清理导入的课程"
private const val PARSING_HINT = "正在解析文件…"

@Composable
fun ImportHubScreen(
    viewModel: ImportHubViewModel,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenCleanup: () -> Unit,
    onManualAdd: (termId: Long?, totalWeeks: Int) -> Unit,
    /** M6 需求 6-A：SAF 选择本 App 导出的 .json 文件（launcher 在导航层装配）。 */
    onPickJsonFile: () -> Unit,
    /** M6 需求 6-B：SAF 选择相册里的二维码截图（launcher 在导航层装配）。 */
    onPickQrImage: () -> Unit,
    onParsed: (ImportResult.NeedsConfirm) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onNoticeShown: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 导航层送来的提示（如「已清理 N 门」）：展示一次即回报消费
    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            onNoticeShown()
        }
    }

    // 手动添加：回调 hoist 成稳定引用（Spec §10 铁律 2，避免内联 lambda 破坏跳过）
    val termId = state.term?.id
    val totalWeeks = state.term?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
    val onManualAddClick: () -> Unit = remember(termId, totalWeeks, onManualAdd) {
        { onManualAdd(termId, totalWeeks) }
    }

    // 解析成功 → 导航到预览页（消费后清除，避免旋转屏重复触发）
    LaunchedEffect(state.pending) {
        state.pending?.let {
            onParsed(it)
            viewModel.consumePending()
        }
    }

    Scaffold(
        // M12 需求一.2：底色与背景图由导航根的 PageBackground 统一提供，这里必须透明
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            HubTopBar(onBack = onBack, modifier = Modifier.statusBarsPadding())
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Text(text = HUB_SUBTITLE, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ImportChannelCard(
                title = CHANNEL_WEB_TITLE,
                subtitle = CHANNEL_WEB_SUBTITLE,
                iconRes = AppIcons.globe,
                enabled = true,
                onClick = onOpenWeb,
            )
            // M6 需求 6-A：本 App 导出的 JSON 文件通道（与 CSV 各自独立 importer，避免串味）
            ImportChannelCard(
                title = CHANNEL_JSON_TITLE,
                subtitle = CHANNEL_JSON_SUBTITLE,
                iconRes = AppIcons.fileText,
                enabled = true,
                onClick = onPickJsonFile,
            )
            // M6 需求 6-B：二维码截图导入（无相机扫码；选图解码在 UI 层，零新权限）
            ImportChannelCard(
                title = CHANNEL_QR_TITLE,
                subtitle = CHANNEL_QR_SUBTITLE,
                iconRes = AppIcons.refresh,
                enabled = true,
                onClick = onPickQrImage,
            )
            ImportChannelCard(
                title = CHANNEL_MANUAL_TITLE,
                subtitle = CHANNEL_MANUAL_SUBTITLE,
                iconRes = AppIcons.pencil,
                enabled = true,
                onClick = onManualAddClick,
            )
            // 次要入口：按来源清理（M2-C，AC-24）
            TextButton(
                onClick = onOpenCleanup,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(text = CLEANUP_ENTRY)
            }
            if (state.parsing) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.Large))
                        Spacer(Modifier.size(12.dp))
                        Text(text = PARSING_HINT, style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            state.failure?.let { failure ->
                ParseFailureCard(
                    reason = failure.reason,
                    fileName = failure.fileName,
                    canRetry = failure.canRetry,
                    onRetry = viewModel::retry,
                    onPickAnother = onPickJsonFile,
                    onManualAdd = onManualAddClick,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 顶栏：返回 + 标题（自绘，须自吃状态栏内边距）。 */
@Composable
private fun HubTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(AppIcons.chevronLeft),
                contentDescription = "返回",
                modifier = Modifier.size(IconSize.Medium),
            )
        }
        Text(text = HUB_TITLE, style = MaterialTheme.typography.titleLarge)
    }
}

private const val DEFAULT_TOTAL_WEEKS = 18

/**
 * 读选中文件 → ImportPayload（UI 层装配，IO 在 Dispatchers.IO）。
 * 编码容错：先按 UTF-8 解码；若出现替换符（非法 UTF-8 字节），改按 GB18030 重解
 * （WakeUp 早期导出存在 GBK 编码，读错编码时表头匹配必然失败，这一步能救回来）。
 * 任何失败返回 null（由 ViewModel 转为可读的失败提示，不崩溃）。
 * internal：M6 起导航层（AppNav）的 JSON 文件通道复用本函数。
 */
internal suspend fun readImportPayload(context: Context, uri: Uri): ImportPayload? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            var text = String(bytes, Charsets.UTF_8)
            if (text.contains('\uFFFD')) {
                val gb = String(bytes, charset("GB18030"))
                if (gb.contains('\uFFFD').not()) text = gb
            }
            ImportPayload(text = text, uri = uri.toString(), displayName = queryDisplayName(context, uri))
        }.getOrNull()
    }

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()

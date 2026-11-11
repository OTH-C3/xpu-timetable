/*
 * CourseCaptureScreen.kt —— 教务直连捕获页
 *
 * 内置 WebView 走一网通办统一身份认证，登录完成后**自动**跳到课表页并抓取数据，
 * 不再要求学生手动点「服务篇 → 我的课表」。App 对凭据零接触零存储（AC-09 隐私红线）：
 * Cookie 由系统 WebView 自己持有，这里只决定"不清掉它、并 flush 落盘"。
 *
 * 2026-11-05 改版适配：
 *   - 请求判定放宽 + 按响应内容认课表（见 XpuRequestPolicy / CaptureWebViewClient）
 *   - 抓不到时显示学校实际请求的接口路径（CaptureDiagnostics），便于定位改版
 *   - WebView 宿主拆到 CaptureWebViewHost.kt（桌面视口、第三方 Cookie、会话持久化）
 *
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；回调方法引用。
 */
package com.gould.xputimetable.ui.web

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.parser.xpu.XpuEndpoints
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize

// ---------- 文件级文案常量 ----------

private const val CAPTURE_TITLE = "教务直连"
private const val CAPTURE_HINT = "登录后将自动打开课表并读取，无需手动点击；若长时间无反应请看下方接口提示"
private const val PARSING_HINT = "已识别到课表数据，正在解析…"
private const val ACTION_BACK = "返回"
private const val ACTION_RETRY = "重新解析"
private const val ACTION_LOGOUT = "退出登录"
private const val NEW_FORMAT_HINT =
    "已读到学校新版课表数据，但格式与旧版不同，暂无法解析。请把下方接口路径发给我，我按新格式适配；这期间可用「从文件导入」。"

@Composable
fun CourseCaptureScreen(
    viewModel: CourseCaptureViewModel,
    onBack: () -> Unit,
    onParsed: (ImportResult.NeedsConfirm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val webViewHolder = remember { mutableStateOf<android.webkit.WebView?>(null) }
    val context = LocalContext.current
    // 起始页优先级：上次走通的入口（可能是 WebVPN 地址）→ 直连课表页（有会话时）→ 门户首页。
    // 记住入口是为了省掉每次「门户 → 服务篇 → 我的课表」那串点击；
    // 入口过期时页面会加载失败，由兜底逻辑退回门户重走一次。
    // remember 包住：重组时不能重新判定，否则页面会被反复 reload。
    val startUrl = remember {
        CapturePrefs.lastCourseTableUrl(context)
            ?: if (hasJwglxtSession()) XpuEndpoints.COURSE_TABLE_PAGE else XpuEndpoints.PORTAL_HOME
    }

    BackHandler(enabled = true) {
        val view = webViewHolder.value
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    // 离开页面时把会话 Cookie 落盘 —— 下次进来不必重新登录。
    DisposableEffect(Unit) {
        onDispose { persistWebSession() }
    }

    // 登录完成 → 自动打开课表页（一次性事件，消费后清除，避免反复 loadUrl）
    LaunchedEffect(state.autoOpenUrl) {
        val target = state.autoOpenUrl ?: return@LaunchedEffect
        webViewHolder.value?.loadUrl(target)
        viewModel.consumeAutoOpen()
    }

    // 顶层页面打不开（多半年校外直连内网地址）→ 退回门户，让学校入口带去 WebVPN
    LaunchedEffect(state.fallbackUrl) {
        val target = state.fallbackUrl ?: return@LaunchedEffect
        webViewHolder.value?.loadUrl(target)
        viewModel.consumeFallback()
    }

    LaunchedEffect(state.pending) {
        state.pending?.let {
            onParsed(it)
            viewModel.consumePending()
        }
    }

    val onCaptured = remember { viewModel::onCaptured }
    val onNetworkError = remember { viewModel::onNetworkError }
    val onDiagnostic = remember { viewModel::onDiagnostic }
    val onLoginFinished = remember { viewModel::onLoginFinished }
    val onMainFrameFailed = remember { viewModel::onMainFrameFailed }
    val onUnsupportedFormat = remember { viewModel::onUnsupportedFormat }

    Scaffold(
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
                Text(text = CAPTURE_TITLE, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                // 换账号 / 会话失效时手动清一次；平时用不到，但缺了它用户就被锁死在旧会话里
                Button(onClick = {
                    clearWebSession()
                    webViewHolder.value?.loadUrl(XpuEndpoints.PORTAL_HOME)
                }) { Text(text = ACTION_LOGOUT) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = CAPTURE_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.parsing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.Medium))
                        Spacer(Modifier.size(12.dp))
                        Text(text = PARSING_HINT, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.unsupportedFormat) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(
                            text = NEW_FORMAT_HINT,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                state.failure?.let { reason ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::retry) { Text(text = ACTION_RETRY) }
                        }
                    }
                }
                // 只在"还没抓到课表"时显示诊断，抓到了就是噪音
                if (!state.parsing && state.pending == null) {
                    CaptureDiagnostics(
                        paths = state.diagnosticPaths,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 注意 weight(1f) 而不是 fillMaxSize()：上面有提示/诊断卡片，
            // 用 fillMaxSize 会按整屏高度测量，把页面底部挤出可视区（"显示不全"的另一半原因）。
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                factory = { context ->
                    buildCaptureWebView(
                        context = context,
                        startUrl = startUrl,
                        onCaptured = onCaptured,
                        onNetworkError = onNetworkError,
                        onDiagnostic = onDiagnostic,
                        onLoginFinished = onLoginFinished,
                        onUnsupportedFormat = onUnsupportedFormat,
                        onMainFrameFailed = onMainFrameFailed,
                    ).also { webViewHolder.value = it }
                },
                onRelease = { wv ->
                    persistWebSession()
                    wv.destroy()
                    webViewHolder.value = null
                },
            )
        }
    }
}

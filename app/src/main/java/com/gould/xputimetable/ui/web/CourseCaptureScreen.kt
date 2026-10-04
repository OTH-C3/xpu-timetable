/*
 * CourseCaptureScreen.kt —— 教务直连捕获页（M2-B §4.4，策略 A 的界面壳）
 *
 * 内置 WebView 加载 jwglxt 课表页；未登录时页面自行跳登录页，**由用户在
 * WebView 内完成登录（含滑块）**——App 对凭据零接触零存储（AC-09 隐私红线，
 * 本页没有任何读取密码框 / 注入 JS 的代码）。
 *
 * 命中 print-data 后：解析中覆盖提示 → 成功经 onParsed 跳预览（AC-10）；
 * 失败原地展示（AC-11/AC-12），已拦截数据保留可重试。
 *
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；回调方法引用。
 */
package com.gould.xputimetable.ui.web

import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.parser.xpu.XpuEndpoints
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize

// ---------- 文件级文案常量 ----------

private const val CAPTURE_TITLE = "教务直连"
private const val CAPTURE_HINT = "请在下方页面完成登录并打开课表，系统会在课表出现时自动识别"
private const val PARSING_HINT = "已识别到课表数据，正在解析…"
private const val ACTION_BACK = "返回"
private const val ACTION_RETRY = "重新解析"

@Composable
fun CourseCaptureScreen(
    viewModel: CourseCaptureViewModel,
    onBack: () -> Unit,
    onParsed: (ImportResult.NeedsConfirm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    // 本屏优先消费返回键：网页有历史先回退网页，否则交给上层栈（子 Composable 回调天然优先于 AppNav 全局回调）
    BackHandler(enabled = true) {
        val view = webViewHolder.value
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    // 解析成功 → 导航到预览页（消费后清除，避免重组重复触发）
    LaunchedEffect(state.pending) {
        state.pending?.let {
            onParsed(it)
            viewModel.consumePending()
        }
    }

    // 拦截回调 hoist 成稳定引用（CaptureWebViewClient 无状态，随 WebView 重建）
    val onCaptured = remember { viewModel::onCaptured }
    val onNetworkError = remember { viewModel::onNetworkError }

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
                Text(text = CAPTURE_TITLE, style = MaterialTheme.typography.titleLarge)
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
                Spacer(Modifier.height(8.dp))
            }
            WebViewSection(
                startUrl = XpuEndpoints.PORTAL_HOME,
                onCaptured = onCaptured,
                onNetworkError = onNetworkError,
                webViewHolder = webViewHolder,
            )
        }
    }
}

/** WebView 宿主：AndroidView 包 WebView；DisposableEffect 负责销毁，防泄漏。 */
@Composable
private fun WebViewSection(
    startUrl: String,
    onCaptured: (String, String) -> Unit,
    onNetworkError: () -> Unit,
    webViewHolder: MutableState<WebView?>,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 4.dp),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true   // 强智系前端是 SPA，无 JS 课表页不出数据
                settings.domStorageEnabled = true
                // 「服务篇 → 我的课表」是以**新标签页**打开的：默认行为下新窗口要么被忽略、
                // 要么行为不一致，课表页就不会经过本 WebView，print-data 也就永远捕获不到。
                // 这里显式接管：把新窗口的 URL 在**同一个 WebView** 里打开（用户无感）。
                settings.setSupportMultipleWindows(true)
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?,
                    ): Boolean {
                        val parent = view ?: return false
                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        // 用一个临时 WebView 接住 URL，再交给主 WebView 加载
                        val bridge = WebView(parent.context)
                        bridge.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                v: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                parent.loadUrl(url)
                                v?.destroy()
                                return true
                            }
                        }
                        transport.webView = bridge
                        resultMsg?.sendToTarget()
                        return true
                    }
                }
                // 不注入任何 JS 去读凭据（AC-09 隐私红线）
                webViewClient = CaptureWebViewClient(
                    onCaptured = onCaptured,
                    onError = { onNetworkError() },
                )
                CookieManager.getInstance().setAcceptCookie(true)
                loadUrl(startUrl)
            }
        },
        update = { wv -> webViewHolder.value = wv },
        onRelease = { it.destroy() },
    )
}

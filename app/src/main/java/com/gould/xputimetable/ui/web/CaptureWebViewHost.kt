/*
 * CaptureWebViewHost.kt —— 教务直连的 WebView 宿主（从 CourseCaptureScreen 拆出）
 *
 * 四件事，都是 2026-11-05「登录后不自动读 / 页面显示不全 / 记不住登录」的根因：
 *
 * 1. **显示不全**：强智教务是 PC 版页面，手机上默认按窄视口渲染，右边被裁掉。
 *    `useWideViewPort` + `loadWithOverviewMode` 让页面按自身宽度排版再整体缩到屏宽；
 *    同时 WebView 在 Column 里必须用 `weight(1f)` 占**剩余**高度 —— 原来用
 *    `fillMaxSize()` 会按整屏高度测量，把上面的提示文字挤出可视区。
 *
 * 2. **SSO 登录态丢失**：门户 `sz.xpu.edu.cn` 与教务 `jwglxt.xpu.edu.cn` 是不同子域，
 *    WebView 默认不接受第三方 Cookie，CAS 换票后的会话带不过去 → 课表页又被弹回登录。
 *    `setAcceptThirdPartyCookies` 必须显式开。
 *
 * 3. **进程被杀后要重新登录**：Cookie 只在内存里不够，`flush()` 才会落盘。
 *    在页面释放、以及每次导航前显式 flush（不读内容、不导出、不上传）。
 *
 * 4. **新标签页丢登录**：「我的课表」以 `window.open` 打开，默认会在另一个 WebView 里
 *    打开从而丢掉父窗口状态。`onCreateWindow` 把 URL 接回**同一个** WebView。
 *
 * 隐私红线：Cookie 全程由系统 WebView 的 CookieManager 自己持有。App 不读取内容、
 * 不写日志、不落自己的库、不上传；「记住登录」= 不主动清除 + 显式 flush，仅此而已。
 */
package com.gould.xputimetable.ui.web

import android.content.Context
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gould.xputimetable.parser.xpu.XpuEndpoints

/**
 * 是否已经带着教务域的会话 Cookie。
 * 只判断"有没有"，**不读内容** —— 这是隐私红线允许的最大限度。
 */
fun hasJwglxtSession(): Boolean =
    CookieManager.getInstance().getCookie(XpuEndpoints.COURSE_TABLE_PAGE)?.isNotBlank() == true

/** 退出登录：清掉本 App WebView 的会话（用户换账号时用）。 */
fun clearWebSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}

/** 把内存中的会话 Cookie 落盘，进程被杀后不必重新登录。 */
fun persistWebSession() {
    CookieManager.getInstance().flush()
}

/**
 * 建一个配置完整的 WebView。
 *
 * @param startUrl 已登录时直接给课表页，未登录给门户首页
 */
internal fun buildCaptureWebView(
    context: Context,
    startUrl: String,
    onCaptured: (String, String) -> Unit,
    onNetworkError: () -> Unit,
    onDiagnostic: (String) -> Unit,
    onLoginFinished: () -> Unit,
    onUnsupportedFormat: () -> Unit,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    settings.apply {
        javaScriptEnabled = true          // 强智前端是 SPA，无 JS 课表页不出数据
        domStorageEnabled = true          // 现代 SPA 把鉴权态放 localStorage/sessionStorage
        databaseEnabled = true
        // ⚠️ 不覆盖 User-Agent。门户抓包里有 auth2/img/android.png、ios.png，
        // 说明学校登录页**自带移动端适配**；强行设桌面 UA 会拿回 PC 固定宽布局，
        // 在手机上右侧留白边（2026-11-05 模拟器实测：内容止于 890/1080px）。
        // 用默认移动 UA + wide viewport，让响应式页面按 device-width 铺满。
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false       // 不显示那条丑陋的 +/- 浮层，双指缩放仍可用
        @Suppress("DEPRECATION")
        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        setSupportMultipleWindows(true)
    }

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    // ⚠️ 跨域 SSO 的关键：门户与教务是不同子域，不开这个 CAS 换票后的会话带不过去
    cookies.setAcceptThirdPartyCookies(this, true)

    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            val parent = view ?: return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            // 用临时 WebView 接住 URL，再交回主 WebView 加载 —— 保证会话在同一窗口内
            val bridge = WebView(parent.context)
            bridge.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    parent.post { parent.loadUrl(url) }
                    return true
                }
            }
            transport.webView = bridge
            resultMsg?.sendToTarget()
            return true
        }
    }

    webViewClient = CaptureWebViewClient(
        onCaptured = onCaptured,
        onError = { onNetworkError() },
        onDiagnostic = onDiagnostic,
        onLoginFinished = onLoginFinished,
        onUnsupportedFormat = onUnsupportedFormat,
    )
    loadUrl(startUrl)
}

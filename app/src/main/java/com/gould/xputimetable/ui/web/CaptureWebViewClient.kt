/*
 * CaptureWebViewClient.kt —— 课表数据拦截 + 登录状态识别
 *
 * 原理：教务课表页自己会发带 Cookie 的 XHR。我们在 shouldInterceptRequest 里
 * 把"看起来像数据请求"的 GET 用 HttpURLConnection 代发一遍，拿到响应体后做两份副本：
 *   【副本 1】包装成 WebResourceResponse 还给 WebView —— 页面照常渲染，用户无感知；
 *   【副本 2】若响应体结构确实是课表（looksLikeScheduleJson），
 *            经 [onCaptured] 交给 ViewModel 解析 → 预览确认。
 *
 * 注意 2026-11-05 改版适配：旧版只认一条精确 URL 正则，学校一改路径就**静默失效**。
 * 现在改成"URL 宽匹配 + 响应体按内容确认"，路径怎么变都能抓到；没抓到时
 * [onDiagnostic] 会把学校实际请求过的路径报给界面，方便定位而不是干等。
 *
 * 【注意】 shouldInterceptRequest 在**后台线程**被调用：这里可以做同步网络请求，
 * 但绝不触碰任何 View / 主线程 UI；回调只更新 StateFlow（线程安全）。
 *
 * 隐私红线（Spec §8）：鉴权只靠 Cookie，且 Cookie 只由系统 WebView 的 CookieManager
 * 持有，我们仅在代发请求头里附上它。**不读取账号密码、不导出 Cookie、不写日志、不上传**。
 *
 * 失败兜底：代发失败时返回 null（交回 WebView 自己发，页面仍可用）。
 */
package com.gould.xputimetable.ui.web

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gould.xputimetable.parser.xpu.courseTableUrl
import com.gould.xputimetable.parser.xpu.diagnosticPage
import com.gould.xputimetable.parser.xpu.diagnosticPath
import com.gould.xputimetable.parser.xpu.isCourseTablePage
import com.gould.xputimetable.parser.xpu.isInsideJwglxt
import com.gould.xputimetable.parser.xpu.isLoginDone
import com.gould.xputimetable.parser.xpu.isLoginPage
import com.gould.xputimetable.parser.xpu.looksLikeKbList
import com.gould.xputimetable.parser.xpu.looksLikeScheduleJson
import com.gould.xputimetable.parser.xpu.responseContentType
import com.gould.xputimetable.parser.xpu.shouldInspect
import com.gould.xputimetable.parser.xpu.structuralSignature
import com.gould.xputimetable.parser.xpu.webVpnPrefixOf
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class CaptureWebViewClient(
    /** 命中课表数据：响应体字符串 + 完整 URL（含 semesterId）。可能在后台线程调用。 */
    private val onCaptured: (body: String, url: String) -> Unit,
    /** 代发请求失败（网络不通/学校服务器异常）。 */
    private val onError: (reason: String) -> Unit = {},
    /** 学校实际请求的数据接口路径（诊断用，不含 Cookie / token）。 */
    private val onDiagnostic: (path: String) -> Unit = {},
    /**
     * 观察到页面离开登录页、落到学校页面 → 通知界面自动跳课表页。
     *
     * 参数是**算好的目标地址**：走 WebVPN 时要带上从当前页面抄来的前缀，
     * 否则会跳到只在校园网可达的直连地址，校外直接打不开。
     */
    private val onLoginFinished: (targetUrl: String) -> Unit = {},
    /** 抓到了新版 kbList 结构（解析器暂不支持）→ 给出明确提示，而不是静默无反应。 */
    private val onUnsupportedFormat: () -> Unit = {},
    /**
     * 顶层页面加载失败（打不开 / 超时）。
     *
     * 校外直连 `jwglxt.xpu.edu.cn` 一定失败——那是内网地址。此时界面要自动退回
     * 门户首页，让学校自己的入口把我们送去 WebVPN，而不是把用户留在错误页上。
     */
    private val onMainFrameFailed: () -> Unit = {},
    /**
     * 课表页真的打开了（含 WebVPN 包裹形态）→ 把这个地址记下来当下次的入口。
     * 记的是**实际走通的 URL**，密钥段才不会被我们猜错。
     */
    private val onCourseTableShown: (url: String) -> Unit = {},
) : WebViewClient() {

    private var sawLoginPage = false

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val u = url ?: return
        onDiagnostic(diagnosticPage(u))
        // 课表页打开成功就记下入口（含 WebVPN 前缀），下次直接进，不用再走门户
        if (isCourseTablePage(u)) onCourseTableShown(u)
        if (isLoginPage(u)) {
            sawLoginPage = true
            return
        }
        // 两个放行条件：
        //   ① 确实经历过登录页（正常手动登录）
        //   ② 已经进到教务站内（SSO 静默换票时一路看不到登录页）
        // 只靠 ① 会把静默换票的人卡在教务首页上，又变成"得自己再进一次"。
        if (isLoginDone(u) && (sawLoginPage || isInsideJwglxt(u))) {
            sawLoginPage = false       // 只触发一次，避免重定向链反复回调
            onLoginFinished(courseTableUrl(webVpnPrefixOf(u)))
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        // 只关心顶层文档：子资源失败（图片、字体）不该弹提示，更不该触发兜底跳转
        if (request?.isForMainFrame == true) onMainFrameFailed()
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        // 顶层导航必须交回 WebView：代发会丢响应头，整页会被当纯文本渲染成源码。
        // iframe 里的文档同理，靠 Accept 头认出来（见 shouldInspect 注释）。
        val accept = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value.orEmpty()
        if (!shouldInspect(url, request.method, request.isForMainFrame, accept)) return null
        onDiagnostic(diagnosticPath(url))
        return runCatching { fetchAndDispatch(url) }
            .getOrElse { e ->
                onError(e.message ?: e::class.simpleName ?: "网络请求失败")
                null // 交回 WebView 自己发，页面仍然可用
            }
    }

    /** 代发 GET：带上 WebView 的 Cookie 与 XHR 头。 */
    private fun fetchAndDispatch(url: String): WebResourceResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            CookieManager.getInstance().getCookie(url)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            // Referer 跟着请求自己走：走 WebVPN 时如果硬写直连地址，网关会认为
            // 来源页面不匹配（跨站），轻则判非法请求、重则回登录页。
            setRequestProperty("Referer", courseTableUrl(webVpnPrefixOf(url)))
        }
        try {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            // 只有结构确实像课表才上报；get-data 这类辅助响应原样还给页面即可。
            // 两者都不是时，把**响应的第一层字段名**记进诊断（只记键名，不记值）——
            // 学校若换成我们完全没见过的结构，这是唯一能让我们事后精确适配的线索。
            when {
                looksLikeScheduleJson(body) -> onCaptured(body, url)
                looksLikeKbList(body) -> onUnsupportedFormat()
                else -> onDiagnostic("未识别 ${diagnosticPath(url)}  ${structuralSignature(body)}")
            }
            return WebResourceResponse(
                // 服务器没给 Content-Type 时按响应体形状猜（见 responseContentType 注释）。
                responseContentType(connection.contentType, body),
                "utf-8",
                ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
    }
}

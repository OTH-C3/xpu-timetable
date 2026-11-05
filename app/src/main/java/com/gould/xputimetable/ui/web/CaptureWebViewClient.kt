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
import com.gould.xputimetable.parser.xpu.XpuEndpoints
import com.gould.xputimetable.parser.xpu.diagnosticPath
import com.gould.xputimetable.parser.xpu.isLoginPage
import com.gould.xputimetable.parser.xpu.isPortalAfterLogin
import com.gould.xputimetable.parser.xpu.diagnosticPage
import com.gould.xputimetable.parser.xpu.looksLikeKbList
import com.gould.xputimetable.parser.xpu.looksLikeScheduleJson
import com.gould.xputimetable.parser.xpu.shouldInspect
import com.gould.xputimetable.parser.xpu.structuralSignature
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
    /** 观察到页面离开登录页、回到门户首页 → 通知界面自动跳课表页。 */
    private val onLoginFinished: () -> Unit = {},
    /** 抓到了新版 kbList 结构（解析器暂不支持）→ 给出明确提示，而不是静默无反应。 */
    private val onUnsupportedFormat: () -> Unit = {},
) : WebViewClient() {

    private var sawLoginPage = false

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val u = url ?: return
        onDiagnostic(diagnosticPage(u))
        if (isLoginPage(u)) {
            sawLoginPage = true
            return
        }
        // 只有当"确实经历过登录页"才自动跳 —— 否则用户第一次打开门户首页
        // （还没登录）就会被硬拽到课表页、又被弹回登录，来回抖。
        if (sawLoginPage && isPortalAfterLogin(u)) {
            sawLoginPage = false       // 只触发一次，避免重定向链反复回调
            onLoginFinished()
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (!shouldInspect(url, request.method)) return null
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
            setRequestProperty("Referer", XpuEndpoints.COURSE_TABLE_PAGE)
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
                connection.contentType ?: "application/json",
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

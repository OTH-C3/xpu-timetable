/*
 * CourseCaptureViewModel.kt —— 教务直连捕获页的状态机（M2-B §4.4）
 *
 * 状态：页面加载中 →（未命中：页面里引导登录）→ 命中解析中 → 成功跳预览 / 失败给提示。
 *
 * 健壮性约定（AC-11）：拦截到的响应体字符串**保留在内存**（retainedBody），
 * 解析失败可原地重试、不丢数据；失败文案区分「教务改版」（解析失败）与
 * 「网络不通」（代发请求失败，AC-12）。
 *
 * 线程说明：onCaptured / onNetworkError 由 WebView 的后台线程回调，这里只更新
 * StateFlow（线程安全），解析在 viewModelScope 里做，不碰 View。
 */
package com.gould.xputimetable.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.parser.api.ParseError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CourseCaptureUiState(
    /** 已拦截到数据、正在解析。 */
    val parsing: Boolean = false,
    /** 解析成功待确认：界面观察后导航到预览页并 consumePending。 */
    val pending: ImportResult.NeedsConfirm? = null,
    /** 失败提示（解析失败 = AC-11；网络不通 = AC-12）。 */
    val failure: String? = null,
    /**
     * 学校实际请求过的数据接口路径（诊断用）。
     *
     * 2026-11-05 改版反馈后加：旧实现在"没抓到课表"时什么都不说，用户只看到
     * 一个正常显示的网页和永远不出现的导入结果，完全无从判断是登录没成、
     * 接口改名还是网络不通。把路径列出来，一眼能看出学校现在发的是什么。
     * 只存路径，不存响应体、不存 Cookie、不上传。
     */
    val diagnosticPaths: List<String> = emptyList(),
    /** 登录完成后需要自动跳到课表页（一次性事件，界面消费后清除）。 */
    val autoOpenCourseTable: Boolean = false,
    /** 抓到了新版 kbList 结构：解析器暂不支持，需要明确告知而不是干等。 */
    val unsupportedFormat: Boolean = false,
)

class CourseCaptureViewModel(
    private val importer: ScheduleImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(CourseCaptureUiState())
    val state: StateFlow<CourseCaptureUiState> = _state.asStateFlow()

    /** AC-11：失败不丢已拦截数据，保留原始响应体字符串供重试。 */
    private var retainedBody: String? = null
    private var retainedUrl: String? = null

    /** CaptureWebViewClient 命中课表数据接口（后台线程回调，线程安全）。 */
    fun onCaptured(body: String, url: String) {
        val current = _state.value
        if (current.parsing || current.pending != null) return // 防页面重发导致重复解析
        retainedBody = body
        retainedUrl = url
        submit()
    }

    /** 代发请求失败（网络不通）：AC-12 提示；保留已拦截数据（若有）供重试。 */
    fun onNetworkError() {
        _state.update { it.copy(failure = NETWORK_FAILURE) }
    }

    /** 原地重试：用保留的响应体再解析一次（AC-11 不丢数据）。 */
    fun retry() {
        if (retainedBody != null) submit()
    }

    /** 界面已消费 pending（已导航到预览页）。 */
    fun consumePending() = _state.update { it.copy(pending = null) }

    /** 记录学校实际请求的数据接口路径；去重、只留最近若干条，避免无限增长。 */
    fun onDiagnostic(path: String) {
        if (path.isBlank()) return
        _state.update { s ->
            val next = (s.diagnosticPaths + path).distinct().takeLast(DIAGNOSTIC_LIMIT)
            s.copy(diagnosticPaths = next)
        }
    }

    /** 登录完成 → 界面自动把 WebView 导航到课表页（不再让学生手点）。 */
    fun onLoginFinished() = _state.update { it.copy(autoOpenCourseTable = true) }

    /** 界面已执行自动跳转。 */
    fun consumeAutoOpen() = _state.update { it.copy(autoOpenCourseTable = false) }

    /** 学校已换成新版强智结构。只提示一次，避免每个请求都刷同一句话。 */
    fun onUnsupportedFormat() {
        if (_state.value.unsupportedFormat) return
        _state.update { it.copy(unsupportedFormat = true) }
    }

    private fun submit() {
        val body = retainedBody ?: return
        val url = retainedUrl
        _state.update { it.copy(parsing = true, failure = null, pending = null) }
        viewModelScope.launch {
            runCatching { importer.import(ImportPayload(text = body, uri = url)) }
                .onSuccess { result ->
                    when (result) {
                        is ImportResult.NeedsConfirm ->
                            _state.update { it.copy(parsing = false, pending = result) }
                        is ImportResult.Failure ->
                            _state.update { it.copy(parsing = false, failure = result.error.toReason()) }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            parsing = false,
                            failure = "解析失败：教务系统可能已改版（${e.message ?: e::class.simpleName ?: "未知错误"}）",
                        )
                    }
                }
        }
    }

    /** ParseError → 用户可读的结构化原因（AC-11：统一以「教务系统可能已改版」收尾引导兜底）。 */
    private fun ParseError.toReason(): String = when (this) {
        is ParseError.EmptyPayload -> "解析失败：教务系统可能已改版（响应内容为空），可使用文件导入"
        is ParseError.SchemaMismatch -> "解析失败：教务系统可能已改版（$detail），可使用文件导入"
        is ParseError.InvalidData -> "解析失败：$detail"
    }
}

private const val NETWORK_FAILURE = "无法连接学校服务器，请在校园网环境下重试或使用文件导入"

/** 诊断列表上限：够看出接口形态即可，多了反而刷屏。 */
private const val DIAGNOSTIC_LIMIT = 8

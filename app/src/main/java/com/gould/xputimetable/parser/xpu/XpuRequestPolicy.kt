/*
 * XpuRequestPolicy.kt —— 教务直连的请求判定（纯函数，可单测）
 *
 * 为什么从 CaptureWebViewClient / XpuEndpoints 里抽出来：
 *   2026-11-05 用户反馈「学校更新后不会自动读取了」。旧实现把希望全押在**一条精确
 *   URL 正则**（semester/{id}/print-data）上——学校只要改路径、改参数名、换接口，
 *   这条正则立刻失效，而且失效得**毫无提示**：WebView 正常显示页面，App 只是永远
 *   等不到那个回调，用户看到的就是"登录了但没反应"。
 *
 *   所以判定改成两层：
 *     ① URL 只要"像教务数据请求"就放行到代发（宽，容忍改版）；
 *     ② 拿到响应体后**按内容**认课表（响应里有 studentTableVms 才是课表）。
 *   这样路径怎么变都不影响，只有数据结构真变了才会失败，而那种情况会被明确报出来。
 *
 * 隐私红线：这里只处理 URL 与响应体结构，**不碰 Cookie、不碰账号密码**。
 */
package com.gould.xputimetable.parser.xpu

/**
 * 静态资源后缀：这些绝不该被代发（代发一次就要多一次网络往返，且拿到的是 JS/CSS 不是课表）。
 */
private val STATIC_SUFFIX = listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".woff", ".woff2", ".ico", ".map")

/**
 * 是否值得代发并"看一眼响应体"。
 *
 * 口径刻意放宽：只要是学校域（`xpu.edu.cn` 及子域）、非静态资源、且路径里出现课表相关关键字即可。
 * 目的就是**不怕学校改路径** —— 少拦比错拦好，因为错拦（把 HTML 当课表）会在
 * [looksLikeScheduleJson] 那一层被挡掉，而漏拦是静默失败、用户完全无从判断。
 *
 * @param url  完整请求 URL
 * @param method HTTP 方法（只代发 GET；POST 往往带副作用，重复发会出事）
 * @param isForMainFrame 是否是顶层文档请求（地址栏那一次导航）
 * @param acceptHeader 请求的 Accept 头（用来识别"这是不是要一份文档"）
 */
fun shouldInspect(
    url: String,
    method: String = "GET",
    isForMainFrame: Boolean = false,
    acceptHeader: String = "",
): Boolean {
    // 顶层文档一律不碰。代发会丢掉原响应头（Content-Type / 编码等），
    // 2026-11-05 真机实测：拦住 `/student/for-std/course-table` 这一次导航后，
    // 整页 HTML 被当纯文本渲染成源码、页面 JS 不执行，结果什么都抓不到。
    // 我们要的只是页面自己发的数据请求（XHR/fetch），那些不是顶层文档。
    if (isForMainFrame) return false
    // 同一条道理再兜一层：**iframe 里的文档**不是顶层导航，isForMainFrame 为 false，
    // 但它仍然是一份要渲染的 HTML。浏览器给文档发的 Accept 一定含 text/html，
    // 给 XHR/fetch 发的通常是 application/json 或 */*，据此就能分开。
    // 只认 text/html 这一条：宁可漏拦（诊断卡片会留痕），也不能把页面拦成源码。
    if (wantsDocument(acceptHeader)) return false
    if (!method.equals("GET", ignoreCase = true)) return false
    val host = runCatching { java.net.URL(url).host }.getOrDefault("")
    if (!isCampusHost(host)) return false
    // 用**真实路径**判定：走 WebVPN 时 URL 是
    // `https://webvpn.xpu.edu.cn/https/<hex>/student/for-std/course-table/...`，
    // 关键字被加密段隔在前面，直接看整条 path 会把它们漏掉。
    val path = effectivePath(url).lowercase()
    if (STATIC_SUFFIX.any { path.endsWith(it) }) return false
    return KEYWORDS.any { path.contains(it) }
}

/**
 * 是不是学校自己的主机。
 *
 * 2026-11-05 再放宽一次：原来写死 `jwglxt.xpu.edu.cn`。学校若把课表数据挪到
 * `jwglxt2.` / `jwglxt-ng.` / API 网关之类的新子域，旧判定会**连请求都看不见**——
 * 诊断卡片空白，比"抓错了"更难查。现在认 `xpu.edu.cn` 及其任意子域。
 *
 * 注意 `endsWith(".xpu.edu.cn")` 不能写成 `contains`，否则
 * `jwglxt.xpu.edu.cn.evil.com` 也会被当成校内主机。
 */
fun isCampusHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.')
    return h == CAMPUS_DOMAIN || h.endsWith(".$CAMPUS_DOMAIN")
}

/** 学校主域；[isCampusHost] 以此为界。 */
private const val CAMPUS_DOMAIN = "xpu.edu.cn"

/** 课表相关路径关键字；新增候选只改这里，不动拦截逻辑。 */
private val KEYWORDS = listOf("course-table", "print-data", "get-data", "semester", "lesson", "kb")

/**
 * 这个请求是不是在要一份**可渲染的文档**（页面 / iframe 页面）。
 *
 * 依据是 Accept 头：文档导航由浏览器自动带 `text/html`，而脚本发的数据请求
 * 要么是 application/json，要么是通配的星号斜杠。**只认 text/html 与 xhtml**，
 * 空 Accept（拿不到头）按「不是文档」处理，保持宽松。
 *
 * 【写法约束】本段是块注释，里面绝不能出现「斜杠 + 星号」连写——Kotlin 的块注释
 * 可以嵌套，那两个字符会把注释又开一层，整文件从这一行起全部报「Expecting a
 * top level declaration」，且真正的错因被埋在几百条假错误里。
 */
fun wantsDocument(acceptHeader: String): Boolean {
    val a = acceptHeader.lowercase()
    return a.contains("text/html") || a.contains("application/xhtml")
}

/**
 * 代发回来的响应该用什么 Content-Type 交给 WebView。
 *
 * 服务器给了就用服务器的；没给才猜。**为什么必须猜**：`WebResourceResponse`
 * 的 MIME 决定 WebView 是"渲染"还是"当文本显示"。2026-11-05 的故障就是
 * 服务器没带 Content-Type、代码兜底成 `application/json`，于是整页 HTML
 * 被当 JSON 源码铺在屏幕上。猜错方向的代价不对称：把 JSON 当 text/plain
 * 顶多是一段文本，把 HTML 当 json 就是白屏般的源码——所以这里对 `<` 开头
 * 一律判 text/html。
 *
 * @param serverType `HttpURLConnection.contentType`（可能为 null）
 * @param body 响应体（只看开头几个字符）
 */
fun responseContentType(serverType: String?, body: String): String {
    val given = serverType?.trim().orEmpty()
    if (given.isNotEmpty()) return given
    val head = body.trimStart()
    return when {
        head.startsWith("<") -> "text/html; charset=utf-8"
        head.startsWith("{") || head.startsWith("[") -> "application/json; charset=utf-8"
        else -> "text/plain; charset=utf-8"
    }
}

/**
 * 响应体是不是我们要的课表 JSON。
 *
 * 用**结构特征**而不是 URL 判断：强智系课表响应的顶层字段是 `studentTableVms`，
 * 里面每个学生带 `activities`。同一个页面还会发 `get-data`（只有
 * `lessonId2SeatNum` / `noPublishLessonIds` 等辅助字段），那个绝不能交给
 * `XpuJsonParser` —— 它会在 `studentTableVms.isEmpty()` 处报"教务可能已改版"，
 * 把真正的课表请求淹没在假错误里。
 *
 * 只做字符串包含判断而不是 `JSONObject` 解析：这一层跑在 WebView 的
 * `shouldInterceptRequest` 后台线程、每帧可能调几十次，完整 JSON 解析太重；
 * 真正的解析交给下游 `XpuJsonParser`（它跑在协程里，只跑一次）。
 */
fun looksLikeScheduleJson(body: String): Boolean =
    body.contains("\"studentTableVms\"") && body.contains("\"activities\"")

/**
 * 是不是**新版强智**的课表结构（`kbList`）。
 *
 * 2026-11-05 联网调研：强智系不同部署的字段名并不统一，除了 XPU 现在用的
 * `studentTableVms`，还有一批学校用扁平的 `kbList`（字段 kcmc/xm/zcd/jcor/xqjmc）。
 * 学校若升级到那一套，响应里就没有 `studentTableVms` 了，旧判定会**静默放过**——
 * 正是这次用户遇到的"登录了但没反应"。
 *
 * 这里单独认出来，是为了让界面给出**明确的**「识别到新版格式」提示，
 * 而不是把一个解析器根本不认的 body 硬塞给 XpuJsonParser 制造一句含糊的解析失败。
 * 解析器兼容 kbList 属于另一件事（要重写 DTO 与映射），不在本次"先别静默失败"的范围内。
 */
fun looksLikeKbList(body: String): Boolean =
    body.contains("\"kbList\"") || (body.contains("\"kcmc\"") && body.contains("\"kcsj\""))

/**
 * 这个 URL 是不是登录页。
 * 用于判断"用户刚刚从登录页出来了没"——出来了就自动跳课表页，不再让人手点。
 *
 * 2026-11-11 补：走 WebVPN 时登录页长成 `webvpn.xpu.edu.cn/https/<hex>/student/login`，
 * 它既不是 `/auth2/login` 也不含 `login?`，旧判定会把它当成"已登录的门户页"，
 * 于是用户还停在账号密码表单上就被 App 拽走了。所以再认一条：路径以 `/login` 结尾。
 */
fun isLoginPage(url: String): Boolean {
    val path = effectivePath(url).lowercase()
    if (path.endsWith("/login") || path.contains("/login/")) return true
    val lower = url.lowercase()
    return lower.contains("/auth2/login") || lower.contains("/auth2/cloud/login") ||
        lower.contains("cas/login") || lower.contains("/login?")
}

/**
 * 是不是已经进到**教务系统内部**的页面（如 `/student/home`）。
 *
 * 为什么要单独判：`sawLoginPage` 守卫只在"确实看见过登录页"时放行自动跳转，
 * 目的是防止刚打开门户还没登录就被拽走。但 SSO 会话还在时换票是**静默**的，
 * 一路看不到登录页就落到教务首页 —— 那种场景下守卫会一直挡着，用户又得手点。
 * 进到教务站内本身就说明已登录，用它来补这一条。
 */
fun isInsideJwglxt(url: String): Boolean {
    val host = runCatching { java.net.URL(url).host }.getOrDefault("")
    if (!isCampusHost(host)) return false
    val path = effectivePath(url).lowercase()
    return path.contains("/student/") || path.endsWith("/student")
}

/**
 * 是不是"已经登录、落到学校页面"了。登录完成就自动跳课表页，不再让人手点。
 *
 * 判定口径（2026-11-11 重写，原实现只认门户主机 `sz.xpu.edu.cn`）：
 *   - 必须是我校域名（含任意子域）
 *   - 不能是登录页
 *   - 不能已经就是课表页（否则会白跳一次、还可能触发重复请求）
 *   - WebVPN 网关自己的根页不算（那里没有包裹前缀，拼不出教务地址）
 *
 * 为什么放宽到任意子域：门户点「我的课表」会跳到
 * `webvpn.xpu.edu.cn/https/<hex>/student/home`，主机既不是门户也不是 jwglxt，
 * 旧判定在这里返回 false，于是**登录成功却没有自动跳转**，用户只能自己退出再进一次。
 * 配合 `sawLoginPage` 守卫（确实经历过登录页）不会误触发。
 */
fun isLoginDone(url: String): Boolean {
    val u = runCatching { java.net.URL(url) }.getOrNull() ?: return false
    if (!isCampusHost(u.host)) return false
    if (isLoginPage(url)) return false
    if (isCourseTablePage(url)) return false
    if (u.host.equals(HOST_WEBVPN, ignoreCase = true) && webVpnPrefixOf(url) == null) return false
    return effectivePath(url).isNotBlank()
}

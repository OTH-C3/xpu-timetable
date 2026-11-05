/*
 * XpuRequestPolicy.kt —— 教务直连的请求判定（纯函数，可单测）
 *
 * 为什么从 CaptureWebViewClient / XpuEndpoints 里抽出来：
 *   2026-11-05 老大反馈「学校更新后不会自动读取了」。旧实现把希望全押在**一条精确
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
 */
fun shouldInspect(url: String, method: String = "GET"): Boolean {
    if (!method.equals("GET", ignoreCase = true)) return false
    val host = runCatching { java.net.URL(url).host }.getOrDefault("")
    if (!isCampusHost(host)) return false
    val path = runCatching { java.net.URL(url).path }.getOrDefault("").lowercase()
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
 * 这个 URL 是不是统一身份认证的登录页。
 * 用于判断"用户刚刚从登录页出来了没"——出来了就自动跳课表页，不再让人手点。
 */
fun isLoginPage(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/auth2/login") || lower.contains("/auth2/cloud/login") ||
        lower.contains("cas/login") || lower.contains("/login?")
}

/**
 * 是不是"已经登录、回到门户"的页面。
 *
 * 只认根路径太窄：SSO 换票后可能落到 `/portal`、`/index`、`/#/` 之类，
 * 那样自动跳转就永远不触发，用户又得手点一次。放宽成"门户域 + 不是登录页"，
 * 配合 sawLoginPage 守卫（确实经历过登录页）就不会误触发。
 */
fun isPortalAfterLogin(url: String): Boolean {
    val u = runCatching { java.net.URL(url) }.getOrNull() ?: return false
    return u.host == XpuEndpoints.HOST_PORTAL && !isLoginPage(url)
}

/** 当前页面的一句话诊断（只留主机+路径，丢掉全部 query 参数）。 */
fun diagnosticPage(url: String): String {
    val u = runCatching { java.net.URL(url) }.getOrNull() ?: return "(无法解析的页面)"
    return "当前页面：${u.host}${u.path}"
}

/**
 * 给界面看的诊断摘要：把学校实际请求的数据接口路径留下来。
 *
 * 为什么需要：学校改版时我们无从得知新路径（进不了校园网、也没账号），
 * 与其猜，不如把**它自己发出来的请求**显示给用户，一眼就能看出该改哪个关键字。
 * 只保留路径与 semesterId 这类非敏感参数，**丢掉全部 query 里可能带 token 的部分**，
 * 也不记录响应体、不记录 Cookie。
 */
fun diagnosticPath(url: String): String {
    val u = runCatching { java.net.URL(url) }.getOrNull() ?: return url.take(120)
    val semester = Regex("semester/(\\d+)").find(u.path)?.groupValues?.get(1)
    return buildString {
        append(u.path)
        if (semester != null) append("  [学期 ").append(semester).append("]")
    }
}

/**
 * 未知响应的**结构签名**：只留字段名，绝不留字段值。
 *
 * 为什么需要：宽匹配能保证"请求被看见"，但如果学校换了一套我们完全不认识的结构
 * （既不是 studentTableVms，也不是 kbList），现在的代码只会**什么都不做**——
 * 用户在真机上永远等不到结果，我们在本地也永远猜不到该适配什么。
 *
 * 把响应里第一层的字段名（如 `code, msg, data`）显示到诊断卡片上，下一次失败
 * 就能直接看出学校实际返回的是什么形状，**不需要账号、不需要抓包**。
 *
 * 隐私边界：仅提取**键名**。值、Cookie、token 一律不进入返回值，也不落盘。
 * 因此这块截图可以安全地发给开发者。
 *
 * 只扫前 [SCAN_LIMIT] 个字符：这一层跑在 WebView 拦截线程上，可能一秒调几十次；
 * 键名都在开头，没必要为诊断遍历几十 MB。
 *
 * @param body 响应体
 * @param limit 最多列几个键（默认 8，够看出形状又不刷屏）
 */
fun structuralSignature(body: String, limit: Int = SIGNATURE_KEY_LIMIT): String {
    val head = body.trimStart()
    if (head.isEmpty()) return "空响应"
    if (head.startsWith("<")) return "HTML 页面"
    if (!head.startsWith("{")) return if (head.startsWith("[")) "JSON 数组" else "非 JSON 文本"

    val text = if (head.length > SCAN_LIMIT) head.take(SCAN_LIMIT) else head
    val keys = ArrayList<String>(limit)
    var depth = 0
    var inString = false
    var escaped = false
    val buffer = StringBuilder()
    var i = 0
    while (i < text.length && keys.size < limit) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> {
                    inString = false
                    // 字符串结束：只有"第 1 层 + 紧跟冒号"才是键名，嵌套层的字段不要
                    var j = i + 1
                    while (j < text.length && text[j].isWhitespace()) j++
                    val key = buffer.toString()
                    if (depth == 1 && j < text.length && text[j] == ':' &&
                        key.isNotEmpty() && key.length <= SIGNATURE_KEY_MAX && key !in keys
                    ) {
                        keys.add(key)
                    }
                }
                else -> buffer.append(c)
            }
        } else {
            when (c) {
                '"' -> { inString = true; buffer.setLength(0) }
                '{', '[' -> depth++
                '}', ']' -> if (depth > 0) depth--
            }
        }
        i++
    }
    return if (keys.isEmpty()) "JSON 对象（未取到字段名）" else "字段：" + keys.joinToString(", ")
}

/** 诊断只扫开头这么多字符：键名都在最前面，扫全文纯属浪费。 */
private const val SCAN_LIMIT = 4096

/** 最多显示几个键名。 */
private const val SIGNATURE_KEY_LIMIT = 8

/** 单个键名长度上限：防御畸形响应里塞超长字符串。 */
private const val SIGNATURE_KEY_MAX = 32

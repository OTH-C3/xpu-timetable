/*
 * XpuDiagnostics.kt —— 教务直连的**诊断文本**（纯函数，可单测）
 *
 * 从 XpuRequestPolicy.kt 拆出来：那里是"要不要代发"的判定，这里是"抓不到时怎么
 * 告诉用户线索"。两块生命周期不同（判定几乎不动，诊断每改版都要扩），拆开后
 * 也在 300 行门禁之内。
 *
 * 隐私边界（Spec §8 / AC-09）：只输出**路径**与**字段名**。
 * 不输出 Cookie、token、字段值，也不落盘 —— 因此这块截图可以安全外发。
 */
package com.gould.xputimetable.parser.xpu

/**
 * 当前页面的一句话诊断（只留主机+真实路径，丢掉全部 query 参数）。
 *
 * 经 WebVPN 时只打 `[VPN]` 标记而不打印那 56 个 hex 字符 —— 那串既看不出信息，
 * 又会把诊断卡片挤成三行看不懂的乱码，截图发出去也没法读。
 */
fun diagnosticPage(url: String): String {
    val u = runCatching { java.net.URL(url) }.getOrNull() ?: return "(无法解析的页面)"
    val tag = if (webVpnPrefixOf(url) != null) "[VPN] " else ""
    return "当前页面：$tag${u.host}${effectivePath(url)}"
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
    val path = effectivePath(url)
    if (path.isBlank()) return url.take(120)
    val semester = Regex("semester/(\\d+)").find(path)?.groupValues?.get(1)
    val tag = if (webVpnPrefixOf(url) != null) "[VPN] " else ""
    return buildString {
        append(tag).append(path)
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

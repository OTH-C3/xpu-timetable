/*
 * WebVpnUrl.kt —— 学校 WebVPN 包装地址的解析（纯函数，可单测）
 *
 * 2026-11-11 真机反馈：门户「我的课表」这个入口**不是直连教务**，而是走
 *   https://webvpn.xpu.edu.cn/https/<hex>/student/home
 * 其中 <hex> 是深信服 WebVPN 的定长头（前 16 字节解出来是 wrdvpnisthebest!）
 * 后面接加密过的「内网地址:端口」；hex 之后才是教务自己的真实路径。
 *
 * 这一段同时是两个问题的根因：
 *   ① 「校外打不开」—— App 里写死的直连地址 jwglxt.xpu.edu.cn 只在校园网可达，
 *      学校实际给学生的通路是 VPN 代理；
 *   ② 「登录完不自动跳课表」—— 登录后落地的页面主机是 webvpn.xpu.edu.cn，
 *      而旧代码只认门户主机，于是判定不到"登录完成"。
 *
 * 加密段**不能硬编码**（不同部署/不同内网地址的密文不同），也不能解密（没必要）。
 * 正确做法是：从当前页面地址里原样抄下 `https/<hex>` 这一段，再拼上教务的真实路径。
 *
 * 隐私边界：只处理 URL 字符串。不碰 Cookie、不碰响应体、不含任何凭据。
 */
package com.gould.xputimetable.parser.xpu

/** WebVPN 网关主机。 */
const val HOST_WEBVPN = "webvpn.xpu.edu.cn"

/** 深信服 WebVPN 的定长标记；hex 段的前 16 字节固定是它。 */
private const val VPN_MARK = "wrdvpnisthebest!"

/** 课表页在教务站内的路径（VPN 内外的路径部分完全一致）。 */
private const val COURSE_TABLE_PATH = "/student/for-std/course-table"

/** 把定长标记转成小写 hex，用来判断某段是不是真的 VPN 前缀（挡掉恰好像 hex 的普通段）。 */
private val VPN_MARK_HEX: String = VPN_MARK.map { "%02x".format(it.code) }.joinToString("")

/**
 * 取出 VPN 路径前缀，形如 `https/<hex>`；不是 WebVPN 包裹地址时返回 null。
 */
fun webVpnPrefixOf(url: String): String? = parseVpnParts(url)?.prefix

/** VPN 包裹地址里的真实路径；非 VPN 地址返回 null。 */
fun webVpnInnerPath(url: String): String? = parseVpnParts(url)?.innerPath

/** 不论是否被 VPN 包裹，都拿到真实路径（查询串已丢掉）。 */
fun effectivePath(url: String): String =
    webVpnInnerPath(url) ?: runCatching { java.net.URL(url).path }.getOrDefault("")

/** 是不是课表页；直连形态与 VPN 包裹形态都认。 */
fun isCourseTablePage(url: String): Boolean =
    effectivePath(url).lowercase().contains(COURSE_TABLE_PATH)

/**
 * 拼出课表页地址。
 *
 * @param vpnPrefix 已知的 VPN 前缀（从当前页面地址里抄来的）；为 null 时退回校园网直连地址
 */
fun courseTableUrl(vpnPrefix: String?): String = if (vpnPrefix == null) {
    XpuEndpoints.COURSE_TABLE_PAGE
} else {
    "https://$HOST_WEBVPN/$vpnPrefix$COURSE_TABLE_PATH"
}

/** 拆解结果：前缀 + 真实路径。 */
private class VpnParts(val prefix: String, val innerPath: String)

/**
 * 解析 WebVPN 地址。三道判据缺一不可：主机对、首段是 `https`、第二段确实以定长标记开头。
 * 少任何一条都会把普通地址误当 VPN 地址，进而拼出打不开的链接。
 */
private fun parseVpnParts(url: String): VpnParts? {
    val u = runCatching { java.net.URL(url) }.getOrNull() ?: return null
    if (!u.host.equals(HOST_WEBVPN, ignoreCase = true)) return null
    val segments = u.path.split('/').filter { it.isNotEmpty() }
    // 至少要 https / hex / 一个路径段：光到网关根或只到 hex 都不够定位教务页面
    if (segments.size < 3) return null
    if (!segments[0].equals("https", ignoreCase = true)) return null
    val hex = segments[1]
    if (hex.length < VPN_MARK_HEX.length || hex.length % 2 != 0) return null
    if (!hex.lowercase().startsWith(VPN_MARK_HEX)) return null
    return VpnParts(
        prefix = "https/$hex",
        innerPath = "/" + segments.drop(2).joinToString("/"),
    )
}

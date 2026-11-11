/*
 * XpuRequestPolicyTest.kt —— 教务改版后的"宽匹配 + 内容确认"判定
 *
 * 这里钉住的是 2026-11-05 那次静默失效的教训：旧实现只认一条精确 URL，
 * 学校一改路径就再也不回调，界面毫无提示。新判定的两条性质必须同时成立：
 *   ① 改路径/改参数仍能放行（宽）
 *   ② 辅助响应（get-data）绝不能被当成课表上报（准）
 */
package com.gould.xputimetable.parser.xpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XpuRequestPolicyTest {

    /**
     * 真机截图里抄下来的 WebVPN 地址：`wrdvpnisthebest!` 的 hex + 加密后的内网地址。
     * 加密段在不同部署下不同，所以代码里只能"抄"，不能硬编码。
     */
    private val vpnHex = "77726476706e69737468656265737421fae046903f2426486e1dc7a99c406d3650"
    private val vpnHome = "https://webvpn.xpu.edu.cn/https/$vpnHex/student/home"
    private val vpnLogin = "https://webvpn.xpu.edu.cn/https/$vpnHex/student/login"
    private val vpnCourseTable = "https://webvpn.xpu.edu.cn/https/$vpnHex/student/for-std/course-table"
    private val vpnData =
        "https://webvpn.xpu.edu.cn/https/$vpnHex/student/for-std/course-table/semester/147/print-data?semesterId=147"

    private val oldUrl =
        "https://jwglxt.xpu.edu.cn/student/for-std/course-table/semester/147/print-data?semesterId=147&hasExperiment=true"

    @Test
    fun `旧的精确路径仍然放行`() {
        assertTrue(shouldInspect(oldUrl))
    }

    @Test
    fun `学校换了路径也能放行`() {
        // 这正是本次修复的核心：只要还在课表相关路径上，就别因为字符串不匹配而静默放弃
        listOf(
            "https://jwglxt.xpu.edu.cn/student/for-std/course-table/get-data?semesterId=147",
            "https://jwglxt.xpu.edu.cn/api/v2/kb/print-data?semesterId=200",
            "https://jwglxt.xpu.edu.cn/new/course-table/semester/200/data",
            "https://jwglxt.xpu.edu.cn/student/for-std/lesson/list",
        ).forEach { u ->
            assertTrue("改版路径应放行：$u", shouldInspect(u))
        }
    }

    @Test
    fun `静态资源与外域不放行`() {
        listOf(
            "https://jwglxt.xpu.edu.cn/student/static/coursetable/js/new-course-table.js?v=12",
            "https://jwglxt.xpu.edu.cn/student/static/coursetable/css/course-table-print.css",
            "https://sz.xpu.edu.cn/gate/lobby/client/api/v1/auth/info",
            "https://jwglxt.xpu.edu.cn/logo.png",
        ).forEach { u ->
            assertFalse("不该代发：$u", shouldInspect(u))
        }
    }

    @Test
    fun `学校换了主机名也能放行`() {
        // 学校把数据挪到新子域时，若还写死 jwglxt，会连请求都看不见（诊断卡片空白）
        listOf(
            "https://jwglxt2.xpu.edu.cn/student/for-std/course-table/print-data?semesterId=200",
            "https://jwglxt-ng.xpu.edu.cn/api/v2/kb/print-data?semesterId=200",
            "https://xpu.edu.cn/student/course-table/data",
        ).forEach { u ->
            assertTrue("校内新子域应放行：$u", shouldInspect(u))
        }
    }

    @Test
    fun `仿冒域不能因为后缀相似被当成校内`() {
        listOf(
            "https://jwglxt.xpu.edu.cn.evil.com/course-table/print-data",
            "https://notxpu.edu.cn/course-table/print-data",
            "https://evil.com/jwglxt.xpu.edu.cn/course-table",
        ).forEach { u ->
            assertFalse("外域绝不放行：$u", shouldInspect(u))
            assertFalse(u, isCampusHost(java.net.URL(u).host))
        }
    }

    @Test
    fun `POST 一律不代发`() {
        // 代发是重复请求，POST 可能有副作用
        assertFalse(shouldInspect(oldUrl, "POST"))
    }

    @Test
    fun `顶层文档请求一律不拦`() {
        // 2026-11-05 真机事故：放宽路径匹配后，课表页这次导航被拦下来代发，
        // 响应头丢失 → 整页 HTML 被当纯文本渲染成源码，页面 JS 不执行，
        // 结果比"抓不到"更糟：用户屏幕上是源代码。
        val page = "https://jwglxt.xpu.edu.cn/student/for-std/course-table"
        assertFalse("顶层导航不能代发", shouldInspect(page, "GET", isForMainFrame = true))
        // 页面自己发起的 XHR 才算
        assertTrue("子资源数据请求照常放行", shouldInspect(oldUrl, "GET", isForMainFrame = false))
    }

    @Test
    fun `iframe 里的文档同样不拦`() {
        // isForMainFrame 只覆盖地址栏那一次导航。教务系统常把课表塞进 iframe，
        // 那里的文档 isForMainFrame = false，照样会丢 Content-Type 变成源码。
        // 兜底依据是 Accept：要文档的一定含 text/html。
        val page = "https://jwglxt.xpu.edu.cn/student/for-std/course-table"
        assertFalse(
            "iframe 文档不能代发",
            shouldInspect(page, "GET", isForMainFrame = false, acceptHeader = "text/html,application/xhtml+xml,*/*"),
        )
        assertFalse(
            "只要 xhtml 也算文档",
            shouldInspect(page, "GET", acceptHeader = "application/xhtml+xml"),
        )
        expectLooseness(
            url = page,
            accept = "application/json, text/plain, */*",
            shouldPass = true,
            label = "XHR 的 json Accept 要放行",
        )
        expectLooseness(
            url = page,
            accept = "*/*",
            shouldPass = true,
            label = "fetch 默认 */* 要放行",
        )
        expectLooseness(
            url = page,
            accept = "",
            shouldPass = true,
            label = "拿不到 Accept 时保持宽松",
        )
    }

    private fun expectLooseness(url: String, accept: String, shouldPass: Boolean, label: String) {
        val got = shouldInspect(url, "GET", isForMainFrame = false, acceptHeader = accept)
        assertEquals(label, shouldPass, got)
    }

    @Test
    fun `服务器没给 Content-Type 时按响应体形状兜底`() {
        // 事故根因：兜底写死 application/json，导致整页 HTML 以源码形式铺满屏幕。
        assertTrue(
            "HTML 必须判成能渲染的类型",
            responseContentType(null, "  <!DOCTYPE html><html>...").startsWith("text/html"),
        )
        assertTrue(
            "JSON 仍然判成 json",
            responseContentType(null, """{"studentTableVms":[]}""").startsWith("application/json"),
        )
        assertTrue(
            "其他文本兜底成 text/plain",
            responseContentType(null, "ok").startsWith("text/plain"),
        )
        assertEquals(
            "服务器给了就照抄，不猜",
            "application/json;charset=UTF-8",
            responseContentType("application/json;charset=UTF-8", "<html>"),
        )
        assertEquals(
            "空白头按没给处理",
            "text/html; charset=utf-8",
            responseContentType("   ", "<html>"),
        )
    }

    @Test
    fun `课表响应按结构认出来`() {
        val body = """{"studentTableVms":[{"id":1,"activities":[{"courseName":"高数"}]}]}"""
        assertTrue(looksLikeScheduleJson(body))
    }

    @Test
    fun `辅助数据不会被误当成课表`() {
        // get-data 的响应：有 lessonId2SeatNum 但没有 studentTableVms / activities
        val aux = """{"noPublishLessonIds":[],"lessonId2SeatNum":{"219844":0}}"""
        assertFalse(looksLikeScheduleJson(aux))
        // 只有 studentTableVms 没有 activities（空课表/异常响应）也不算完整课表
        assertFalse(looksLikeScheduleJson("""{"studentTableVms":[]}"""))
    }

    @Test
    fun `新版强智 kbList 结构能被单独认出来`() {
        // 调研结论：部分强智部署用扁平 kbList，字段名 kcmc/xm/zcd/jcor。
        // 认出来是为了给明确提示，而不是当成旧版结构硬塞给解析器。
        val kb = """{"kbList":[{"kcmc":"高等数学","xm":"张三","zcd":"1-16","jcor":"1-2","kcsj":"周一"}]}"""
        assertTrue(looksLikeKbList(kb))
        assertFalse("新版结构不该被当成旧版课表", looksLikeScheduleJson(kb))
    }

    @Test
    fun `登录页与登录完成的判定`() {
        assertTrue(isLoginPage("https://sz.xpu.edu.cn/auth2/login"))
        assertTrue(isLoginPage("https://sz.xpu.edu.cn/auth2/cloud/login"))
        assertTrue(isLoginDone("https://sz.xpu.edu.cn/"))
        assertTrue("SSO 换票后落到子路径也算登录完成", isLoginDone("https://sz.xpu.edu.cn/portal/index"))
        assertFalse(isLoginDone("https://sz.xpu.edu.cn/auth2/login"))
        assertFalse(isLoginDone("https://jwglxt.xpu.edu.cn/student/for-std/course-table"))
        assertEquals("当前页面：sz.xpu.edu.cn/auth2/login", diagnosticPage("https://sz.xpu.edu.cn/auth2/login?token=SECRET"))
    }

    @Test
    fun `WebVPN 上登录完也要自动跳课表`() {
        // 2026-11-11 真机：门户「我的课表」跳到 webvpn 代理页，旧判定只认门户主机，
        // 于是登录成功却不自动跳，用户得退出再进一次才行。
        assertTrue("VPN 包裹的教务首页算登录完成", isLoginDone(vpnHome))
        assertFalse("VPN 的账号登录表单不能算登录完成", isLoginDone(vpnLogin))
        assertFalse("已经在课表页就别再跳一次", isLoginDone(vpnCourseTable))
        assertFalse("VPN 网关根页不算", isLoginDone("https://webvpn.xpu.edu.cn/"))
        assertFalse("校外域名不算", isLoginDone("https://jwglxt.xpu.edu.cn.evil.com/student/home"))
    }

    @Test
    fun `SSO 静默换票时靠教务站内页放行`() {
        // 会话还在时换票不经过登录页，sawLoginPage 守卫会一直挡着；
        // 进到教务站内本身就等于已登录，用它补这一条。
        assertTrue(isInsideJwglxt(vpnHome))
        assertTrue(isInsideJwglxt("https://jwglxt.xpu.edu.cn/student/home"))
        assertFalse("门户首页不算进到教务系统", isInsideJwglxt("https://sz.xpu.edu.cn/"))
        assertFalse(isInsideJwglxt("https://jwglxt.xpu.edu.cn.evil.com/student/home"))
    }

    @Test
    fun `WebVPN 前缀原样抄下来拼课表页`() {
        // 加密段每次部署都不同，不能硬编码，只能从当前页面抄
        assertEquals("https/$vpnHex", webVpnPrefixOf(vpnHome))
        assertEquals("/student/home", webVpnInnerPath(vpnHome))
        assertEquals("/student/for-std/course-table/semester/147/print-data", effectivePath(vpnData))
        assertEquals(
            "https://webvpn.xpu.edu.cn/https/$vpnHex/student/for-std/course-table",
            courseTableUrl(webVpnPrefixOf(vpnHome)),
        )
        assertEquals(
            "拿到前缀就用前缀，没有才退回直连",
            "https://jwglxt.xpu.edu.cn/student/for-std/course-table",
            courseTableUrl(null),
        )
    }

    @Test
    fun `长得像 hex 但标记不对的不能当 VPN 前缀`() {
        // 挡掉"恰好像 hex"的普通路径段：前缀里必须真的含 wrdvpnisthebest!
        assertNull(webVpnPrefixOf("https://webvpn.xpu.edu.cn/https/${"ab".repeat(28)}/student/home"))
        assertNull("网关根页没有前缀", webVpnPrefixOf("https://webvpn.xpu.edu.cn/"))
        assertNull("非 VPN 主机不解析", webVpnPrefixOf("https://jwglxt.xpu.edu.cn/student/home"))
        // 不是 VPN 地址时，真实路径就是它自己的路径
        assertEquals("/student/home", effectivePath("https://jwglxt.xpu.edu.cn/student/home"))
    }

    @Test
    fun `VPN 下的数据请求也要能被认出并去重显示`() {
        // 关键字被加密段隔在前面，用整条 path 匹配会漏掉
        assertTrue(shouldInspect(vpnData))
        assertTrue(isCourseTablePage(vpnCourseTable))
        val diag = diagnosticPath(vpnData)
        assertEquals("[VPN] /student/for-std/course-table/semester/147/print-data  [学期 147]", diag)
        assertFalse("诊断里不该出现那串加密 hex", diag.contains(vpnHex))
        assertEquals("当前页面：[VPN] webvpn.xpu.edu.cn/student/home", diagnosticPage(vpnHome))
    }
}

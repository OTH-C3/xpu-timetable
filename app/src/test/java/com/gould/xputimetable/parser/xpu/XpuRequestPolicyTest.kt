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
import org.junit.Assert.assertTrue
import org.junit.Test

class XpuRequestPolicyTest {

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
    fun `登录页与门户首页的判定`() {
        assertTrue(isLoginPage("https://sz.xpu.edu.cn/auth2/login"))
        assertTrue(isLoginPage("https://sz.xpu.edu.cn/auth2/cloud/login"))
        assertTrue(isPortalAfterLogin("https://sz.xpu.edu.cn/"))
        assertTrue("SSO 换票后落到子路径也算登录完成", isPortalAfterLogin("https://sz.xpu.edu.cn/portal/index"))
        assertFalse(isPortalAfterLogin("https://sz.xpu.edu.cn/auth2/login"))
        assertFalse(isPortalAfterLogin("https://jwglxt.xpu.edu.cn/student/for-std/course-table"))
        assertEquals("当前页面：sz.xpu.edu.cn/auth2/login", diagnosticPage("https://sz.xpu.edu.cn/auth2/login?token=SECRET"))
    }

    @Test
    fun `诊断摘要不带 token 类参数`() {
        val u = oldUrl + "&token=SECRET&ticket=ABC"
        val s = diagnosticPath(u)
        assertFalse("诊断文本不能泄露 token：$s", s.contains("SECRET"))
        assertFalse("诊断文本不能泄露 ticket：$s", s.contains("ABC"))
        assertTrue("应保留学期号便于定位", s.contains("147"))
        assertEquals(
            "/student/for-std/course-table/semester/147/print-data  [学期 147]",
            s,
        )
    }

    // ---- 未知响应的结构签名（2026-11-05 补：让"抓不到"变成可诊断的线索）----

    @Test
    fun `未知结构只留第一层字段名不留值`() {
        // 值里的 token 绝不能出现在签名里——这块截图是要发给开发者看的
        val body = """{"code":0,"msg":"登录已过期","data":{"token":"SECRET","list":[]}}"""
        val sig = structuralSignature(body)
        assertEquals("字段：code, msg, data", sig)
        assertFalse("签名不能泄露字段值", sig.contains("SECRET"))
        assertFalse("嵌套层字段不该混进来", sig.contains("token"))
    }

    @Test
    fun `字段值里的引号与花括号不会打乱识别`() {
        // JSON 里的转义引号如果处理错，后面的键名会整段丢掉
        val body = """{"a":"say \"hi\" {x}","b":1}"""
        assertEquals("字段：a, b", structuralSignature(body))
    }

    @Test
    fun `非 JSON 响应给出可读类型`() {
        assertEquals("HTML 页面", structuralSignature("<!DOCTYPE html><html><body>登录</body></html>"))
        assertEquals("空响应", structuralSignature("   \n  "))
        assertEquals("JSON 数组", structuralSignature("[1,2,3]"))
        assertEquals("非 JSON 文本", structuralSignature("ok"))
    }

    @Test
    fun `字段名数量有上限`() {
        val body = (1..20).joinToString(",", prefix = "{", postfix = "}") { "\"k$it\":$it" }
        val sig = structuralSignature(body)
        assertEquals(8, sig.removePrefix("字段：").split(", ").size)
        assertFalse("超出上限的键不该出现", sig.contains("k9"))
    }
}

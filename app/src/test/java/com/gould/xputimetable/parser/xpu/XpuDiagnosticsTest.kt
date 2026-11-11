/*
 * XpuDiagnosticsTest.kt —— 诊断文本的隐私与可读性（纯函数）
 *
 * 从 XpuRequestPolicyTest 拆出：这些断言管的是"抓不到时告诉用户什么"，
 * 与"要不要代发"是两件事。拆开后两份都在 300 行门禁内。
 */
package com.gould.xputimetable.parser.xpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XpuDiagnosticsTest {

    private val oldUrl =
        "https://jwglxt.xpu.edu.cn/student/for-std/course-table/semester/147/print-data?semesterId=147&hasExperiment=true"

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

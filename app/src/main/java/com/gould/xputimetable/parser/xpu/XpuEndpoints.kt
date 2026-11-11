/*
 * XpuEndpoints.kt —— 西工程大教务系统（强智系）端点常量与请求识别
 *
 * 来源：Spec M2-B §4.1（照抄，勿改）。semesterId 会随学期变化（本次抓包为 147），
 * 必须从拦截到的 URL 正则提取，**不得硬编码**。
 */
package com.gould.xputimetable.parser.xpu

object XpuEndpoints {
    const val HOST_JWGLXT = "jwglxt.xpu.edu.cn"
    /** 一网通办域名 */
    const val HOST_PORTAL = "sz.xpu.edu.cn"

    /**
     * 起始页：一网通办首页（**不是**教务课表页）。
     *
     * 为什么改（2026-09-17 真机实测，用户反馈）：直接打开 jwglxt 课表页会进它自己的「账号登录」，
     * 而学校的正确入口是统一身份认证；从 sz.xpu.edu.cn 登录后选「服务篇 → 我的课表」才是
     * 教务课表的正确路径（该入口会以新标签页打开，见 CourseCaptureScreen 的新窗口接管）。
     */
    const val PORTAL_HOME = "https://sz.xpu.edu.cn/"
    const val COURSE_TABLE_PAGE = "https://jwglxt.xpu.edu.cn/student/for-std/course-table"
    /**
     * 课表数据接口（GET，鉴权仅靠 Cookie）：semester/{id}/print-data
     *
     * 只匹配路径、不锚定主机：走 WebVPN 时同一条请求会被包成
     * `https://webvpn.xpu.edu.cn/https/<hex>/student/for-std/course-table/semester/...`，
     * 锚了主机就提取不到 semesterId（只影响稳定 id 的种子，不影响解析）。
     */
    val PRINT_DATA_REGEX = Regex("""/semester/(\d+)/print-data""")
    fun semesterIdOf(url: String): String? = PRINT_DATA_REGEX.find(url)?.groupValues?.get(1)
}

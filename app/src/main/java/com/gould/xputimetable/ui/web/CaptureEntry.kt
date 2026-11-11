/*
 * CaptureEntry.kt —— 记住上次走通的教务课表入口
 *
 * 为什么需要：走 WebVPN 时课表地址长这样
 *   https://webvpn.xpu.edu.cn/https/<hex>/student/for-std/course-table
 * 中间那串 hex 是加密过的内网地址，**算不出来，只能从实际页面里拿到**。
 * 不记下来的话，每次导入都得重新走一遍「门户 → 登录 → 服务篇 → 我的课表」，
 * 校外用户尤其难受。
 *
 * 失效兜底：入口可能过期（学校换网关、会话被清）。真过期时页面加载失败，
 * CaptureWebViewClient 的 onReceivedError 会把界面退回门户，再走一遍学校入口，
 * 不会把用户卡在错误页上。
 *
 * 隐私边界：只存 URL 字符串。不含账号、密码、Cookie、token，也不上传。
 */
package com.gould.xputimetable.ui.web

import android.content.Context

internal object CapturePrefs {

    private const val FILE = "capture_entry"
    private const val KEY_COURSE_TABLE = "course_table_url"

    /** 上次成功打开的课表页地址；没记录过返回 null。 */
    fun lastCourseTableUrl(context: Context): String? =
        prefs(context).getString(KEY_COURSE_TABLE, null)

    /** 记下这次走通的课表页地址（含 WebVPN 前缀时一并保存）。 */
    fun rememberCourseTableUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_COURSE_TABLE, url).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

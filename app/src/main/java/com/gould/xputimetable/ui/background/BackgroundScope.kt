/*
 * BackgroundScope.kt —— 自定义背景的作用域（M12 需求二）
 *
 * 为什么要有「作用域」：同一张背景图，铺在全部页面上会让编辑表单的输入框、导入预览的
 * 卡片全部压上花纹，可读性下降；而只铺课表页又太少用。于是把"这张图作用在哪些页"做成
 * 一个可枚举的选择（全局 / 课表 / 我的 / 便签），由用户在设置页二选其一。
 *
 * 判定为什么放在这里而不是各页面自报家门：
 *   AppScreen 是 sealed interface，每加一个页面都必须在这里补一条 when 分支（无 else），
 *   这正是我们要的"漏了新页面会被编译发现"；而若让每个页面自己写 scope，
 *   漏写时编译不报错，只会静默不显示背景图。
 *
 * 归类口径（页面上会照此写文案，不含糊）：
 *   - 便签   ：待办清单页（M12 需求五新增的第三个底栏项）
 *   - 我的   ：「我的」根页及其二级页（权限 / 关于 / 自定义背景）
 *   - 课表   ：其余全部页面（周视图、添加/编辑课程、导入中心、清理、分享、学期设置）
 *     —— 学期设置虽从「我的」进，但它是学期数据编辑页（与课程编辑同属"填表"场景），
 *        归到"课表"这一侧在可读性上更合理。
 */
package com.gould.xputimetable.ui.background

/** 背景作用域（存进 DataStore 用 name()，故枚举名不可随意改）。 */
enum class BackgroundScope {
    /** 全局：所有页面。 */
    GLOBAL,

    /** 课表：周视图 + 一切填表 / 导入页。 */
    TIMETABLE,

    /** 我的：「我的」根页与它的二级页。 */
    PROFILE,

    /** 便签：待办清单页。 */
    NOTE,
}

/** 界面上展示的分组名（设置页"作用范围"一栏的四个选项）。 */
val BackgroundScope.label: String
    get() = when (this) {
        BackgroundScope.GLOBAL -> "全局"
        BackgroundScope.TIMETABLE -> "课表"
        BackgroundScope.PROFILE -> "我的"
        BackgroundScope.NOTE -> "便签"
    }

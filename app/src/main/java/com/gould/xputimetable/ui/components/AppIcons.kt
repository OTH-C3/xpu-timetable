/*
 * AppIcons.kt —— 图标门面（ADR-002 规则 1）
 *
 * 作用：全项目唯一的图标引用入口。界面代码禁止直接写 R.drawable.lucide_*，
 * 一律引用 AppIcons.xxx —— 这样未来若换图标库，改动面被压缩到本文件一处。
 *
 * 命名规则（2026-09-16 经构件逐个核对）：Lucide Android 构件提供 1666 个
 * res/drawable/lucide_ic_*.xml 资源，名字 = 官方图标名转 snake_case 加 lucide_ic_ 前缀。
 * 本文件只声明项目实际用到的图标（开 R8 资源收缩后其余不进包）。
 *
 * 注意：gradle.properties 中的 android.nonTransitiveRClass 必须为 false，
 * 否则库资源不并入 app 的 R，这里会全部解析失败（见 pitfalls）。
 */
package com.gould.xputimetable.ui.components

import androidx.annotation.DrawableRes
import com.gould.xputimetable.R

object AppIcons {

    // ---------- 通用动作 ----------

    /** 新增（手动添加课程的「+」按钮） */
    @DrawableRes
    val plus: Int = R.drawable.lucide_ic_plus

    /** 关闭/取消 */
    @DrawableRes
    val close: Int = R.drawable.lucide_ic_x

    /** 确认/保存 */
    @DrawableRes
    val check: Int = R.drawable.lucide_ic_check

    /** 编辑 */
    @DrawableRes
    val pencil: Int = R.drawable.lucide_ic_pencil

    /** 删除（需二次确认） */
    @DrawableRes
    val trash: Int = R.drawable.lucide_ic_trash_2

    /** 数值步进的减号（M9「我的」页总周数步进器） */
    @DrawableRes
    val minus: Int = R.drawable.lucide_ic_minus

    /** 二级页条目的右箭头（M9「我的」页改为分组列表 + 二级页结构） */
    @DrawableRes
    val chevronRight: Int = R.drawable.lucide_ic_chevron_right

    // ---------- 周视图 ----------

    /** 返回（各二级页顶栏返回箭头；周视图上下周按钮已随 M4-UI 删除） */
    @DrawableRes
    val chevronLeft: Int = R.drawable.lucide_ic_chevron_left

    /** 回到本周 */
    @DrawableRes
    val calendarToday: Int = R.drawable.lucide_ic_calendar

    /** 当前无使用者；如后续页面需要用户图标可直接引用。 */
    @DrawableRes
    val user: Int = R.drawable.lucide_ic_circle_user

    // ---------- 导入/设置（M2 用，先占好语义名）----------

    @DrawableRes
    val bell: Int = R.drawable.lucide_ic_bell

    @DrawableRes
    val settings: Int = R.drawable.lucide_ic_settings

    /** 信息说明 */
    @DrawableRes
    val info: Int = R.drawable.lucide_ic_info

    // ---------- 导入中心（M2-A）----------

    /** 唤起导入（周视图进入导入中心的入口） */
    @DrawableRes
    val upload: Int = R.drawable.lucide_ic_upload

    /** 教务直连通道 */
    @DrawableRes
    val globe: Int = R.drawable.lucide_ic_globe

    /** WakeUp CSV 文件通道 */
    @DrawableRes
    val fileText: Int = R.drawable.lucide_ic_file_text

    /** 解析失败警示 */
    @DrawableRes
    val circleAlert: Int = R.drawable.lucide_ic_circle_alert

    /** 失败后重试 */
    @DrawableRes
    val refresh: Int = R.drawable.lucide_ic_refresh_cw

    // ---------- 课程颜色（M11-第三批 取色器）----------

    /** 自取色入口（打开 HSV 取色面板；课程色板之外的"自定义"按钮） */
    @DrawableRes
    val palette: Int = R.drawable.lucide_ic_palette

    // ---------- 待办清单（M12 需求五）----------

    /** 展开（清单头部折叠箭头，展开时指向上） */
    @DrawableRes
    val chevronUp: Int = R.drawable.lucide_ic_chevron_up

    /** 折叠（清单头部折叠箭头，收起时指向下） */
    @DrawableRes
    val chevronDown: Int = R.drawable.lucide_ic_chevron_down

    /** 待办（底栏第三项 + 空态图标） */
    @DrawableRes
    val listTodo: Int = R.drawable.lucide_ic_list_todo

    // ---------- 自定义背景（M12 需求二）----------

    /** 选择背景图（设置页「选择图片」按钮） */
    @DrawableRes
    val image: Int = R.drawable.lucide_ic_image

    /** 移除背景（清除已选的图） */
    @DrawableRes
    val imageOff: Int = R.drawable.lucide_ic_image_off
}

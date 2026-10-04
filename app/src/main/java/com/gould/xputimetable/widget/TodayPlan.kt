/*
 * TodayPlan.kt —— 桌面小组件「今日课程」的判定纯函数（M4-W 改版）
 *
 * 口径（M4-W §3.3，取代 M3 三态语义）：
 *   - 输入 dayItems 由调用方按「今天星期几」筛好，周次/单双周过滤已在 SQL 层完成，
 *     本文件不做周次判定（重复过滤是本项目踩过的坑）；
 *   - 列表只装「未结束」课程（nowMinute < endMinute 严格小于，== 视为已结束）：
 *     每上完一节即从列表消失、下一节上移（推进时机由 EndOfClassRefreshScheduler 保证）；
 *   - 无激活学期 → NO_TERM（优先级最高）；有学期但今日无课或课程已全部上完
 *     → ALL_DONE_OR_NONE（两类统一走「今天没有课啦」空态）。
 *
 * 本文件只允许 Kotlin 标准库，时间一律由参数注入 —— 否则无法单测（规格 §4.1）。
 */
package com.gould.xputimetable.widget

import androidx.compose.ui.unit.Dp
import com.gould.xputimetable.ui.theme.Widget

/** 今日一节课（小组件只读视图所需的最小字段）。 */
data class TodayItem(
    val startSection: Int,
    val endSection: Int,
    val courseName: String,
    val classroom: String?,
    val startMinute: Int,
    val endMinute: Int,
    val colorTag: Int,
)

/** 空态原因（M4-W：收敛为两类空态文案来源）。 */
enum class EmptyReason {
    /** 今日存在未结束课程。 */
    NONE,

    /** 无激活学期：引导用户打开 App 导入。优先级高于 ALL_DONE_OR_NONE。 */
    NO_TERM,

    /** 有学期但今日无课，或今日课程已全部结束：统一走颜文字空态。 */
    ALL_DONE_OR_NONE,
}

/**
 * 小组件渲染所需的完整视图数据。
 *
 * @param remaining      未结束课程（nowMinute < endMinute，按开始时间升序，
 *                       至多 [TodayPlanBuilder.MAX_ITEMS] 条；首条即"正在上/下一节"）
 * @param remainingTotal 未结束课程总数（「今天还有 x 节课」的 x，含被折叠的）
 * @param overflowCount  超出展示上限被折叠的门数（remainingTotal - 可见数）
 * @param emptyReason    空态原因
 */
data class TodayPlan(
    val remaining: List<TodayItem>,
    val remainingTotal: Int,
    val overflowCount: Int,
    val emptyReason: EmptyReason,
)

/**
 * 头部右侧信息的详细程度（M10）：宽度不够时**按优先级砍内容**，而不是缩字号
 * （M5 需求 7/8 明确要求"右上与校名同字号"，缩字号会破坏该决定）。
 * 砍的顺序：先「日期」，再「第 N 周」；「周几」最后保留。
 */
enum class HeaderDetail {
    /** 日期 + 第 N 周 + 周几。 */
    FULL,

    /** 第 N 周 + 周几（砍日期）。 */
    NO_DATE,

    /** 仅周几。 */
    WEEKDAY_ONLY,
}

/** 按可用宽度决定详细程度（纯函数，阈值见 ui/theme/Tokens.kt 的 Widget）。 */
fun headerDetailFor(width: Dp): HeaderDetail = when {
    width >= Widget.HeaderFullMinWidth -> HeaderDetail.FULL
    width >= Widget.HeaderWeekMinWidth -> HeaderDetail.NO_DATE
    else -> HeaderDetail.WEEKDAY_ONLY
}

/**
 * 组装头部右上文案（纯函数，便于单测）。
 *
 * @param dateText    形如 "10.2"
 * @param weekNumber  第几周；null = 当前不在学期周内（假期越界）→ 不显示周次
 * @param weekdayText 形如 "周五"；null = 无学期 → 不显示周几
 * @param detail      详细程度；null 项一律不显示
 */
fun headerRightText(
    dateText: String,
    weekNumber: Int?,
    weekdayText: String?,
    detail: HeaderDetail,
): String = buildList {
    if (detail == HeaderDetail.FULL) add(dateText)
    if (detail != HeaderDetail.WEEKDAY_ONLY) weekNumber?.let { add("第 $it 周") }
    weekdayText?.let { add(it) }
}.joinToString("  ")

object TodayPlanBuilder {

    /** 桌面卡片最多完整列出的门数（M5 需求 5：88dp 高度放不下 4 行，按规格 §5.1 裁决降为 3）。 */
    const val MAX_ITEMS = 3

    /**
     * 装配今日计划（纯函数）。
     *
     * @param dayItems  调用方已按今天星期几筛好的今日课程
     * @param nowMinute 当前时间（当天 0 点起的分钟数）
     * @param hasTerm   是否存在激活学期；false 时直接返回 NO_TERM 空态
     */
    fun build(
        dayItems: List<TodayItem>,
        nowMinute: Int,
        hasTerm: Boolean = true,
    ): TodayPlan {
        if (!hasTerm) {
            return TodayPlan(
                remaining = emptyList(),
                remainingTotal = 0,
                overflowCount = 0,
                emptyReason = EmptyReason.NO_TERM,
            )
        }
        val sorted = dayItems.sortedWith(
            compareBy<TodayItem> { it.startMinute }.thenBy { it.startSection },
        )
        // 只保留未结束课程：已结束的不进列表（"上完一节就消失、下一节上移"）
        val remainingAll = sorted.filter { nowMinute < it.endMinute }
        if (remainingAll.isEmpty()) {
            // 今日无课与全部上完统一空态（产品裁决，M4-W §3.3）
            return TodayPlan(
                remaining = emptyList(),
                remainingTotal = 0,
                overflowCount = 0,
                emptyReason = EmptyReason.ALL_DONE_OR_NONE,
            )
        }
        return TodayPlan(
            remaining = remainingAll.take(MAX_ITEMS),
            remainingTotal = remainingAll.size,
            overflowCount = (remainingAll.size - MAX_ITEMS).coerceAtLeast(0),
            emptyReason = EmptyReason.NONE,
        )
    }
}

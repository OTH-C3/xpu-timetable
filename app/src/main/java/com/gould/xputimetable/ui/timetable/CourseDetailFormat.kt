/*
 * CourseDetailFormat.kt —— 课程详情里的文本换算（纯函数，M11-第三批）
 *
 * 作用：把 CourseSession 里那几个"能存但不好读"的数字（起始/结束周、起始/结束节次）
 * 翻成给人看的中文短句。
 *
 * 为什么单独成文件而不是塞进 CourseDetailSheet：
 *   1. 单文件 ≤300 行是项目硬门禁，弹层本身已经 300 行上下；
 *   2. 这三个都是纯函数（无 Compose、无 IO），独立成文件才能被 UnitTest 直接点名测试 ——
 *      塞在 Composable 文件里，测试要么反射、要么为了跑测试把整个 UI 文件拉起来。
 *
 * 口径说明：项目里同一件事的标准做法是"纯函数 + 单测"（WeekCalc / CellLayout / WeekOverridePolicy
 * 都这么来），这里保持一致。
 */
package com.gould.xputimetable.ui.timetable

import com.gould.xputimetable.domain.model.TimeSlot

/** 「1-2 节」：单节退化成「1 节」。 */
internal fun sectionSpan(start: Int, end: Int): String =
    if (start == end) "$start 节" else "$start-$end 节"

/**
 * 周次文本：优先用逐周列表 `weeks`（教务导入的单周/双周排课最准）；
 * 没有逐周列表时退回 `startWeek..endWeek` 区间。
 *
 * 相邻周要合并成「2-4 周」，中间断开才另起一段——逐周全列会把半屏写成数字，
 * 合并后一眼能看出这是"连着三周"还是"第 2、6 周"。
 */
internal fun formatWeeks(startWeek: Int, endWeek: Int, weeks: List<Int>?): String {
    val target = weeks?.takeIf { it.isNotEmpty() }?.sorted() ?: (startWeek..endWeek).toList()
    val ranges = ArrayList<IntRange>()
    for (week in target) {
        val last = ranges.lastOrNull()
        if (last != null && week == last.last + 1) ranges[ranges.lastIndex] = last.first..week
        else ranges.add(week..week)
    }
    return ranges.joinToString("、") { range ->
        if (range.first == range.last) "第 ${range.first} 周"
        else "第 ${range.first}-${range.last} 周"
    }
}

/** 把作息表里的节次换算成「8:00 - 9:50」；表缺失就退回节次，不显示假的钟点。 */
internal fun timeSpan(slots: List<TimeSlot>, startSection: Int, endSection: Int): String {
    val start = slots.firstOrNull { it.section == startSection }
    val end = slots.firstOrNull { it.section == endSection }
    return if (start != null && end != null) {
        "${clock(start.startMinute)} - ${clock(end.endMinute)}"
    } else {
        "$startSection-$endSection"
    }
}

/** 分钟 → 「H:MM」（不补前导零，8:00 而不是 08:00，省一格宽度）。 */
private fun clock(minute: Int): String = "${minute / 60}:${"%02d".format(minute % 60)}"

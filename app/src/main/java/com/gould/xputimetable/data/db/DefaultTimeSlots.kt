/*
 * DefaultTimeSlots.kt —— 西工程大标准作息的预置数据
 *
 * 为什么需要它：架构 §7.2 与 Spec §6 都写明 time_slots「预置西工程大作息，设置页可编辑」。
 * 但代码此前完全没有预置，导致首次安装后作息表为空 —— 后果不只是时间轴不显示，
 * 更严重的是**课前提醒会永久失效**：每条安排换算出不 startMinute，
 * WeekCalc.nextSessionTime 直接返回 null，提醒永远排不出来。
 *
 * 数据来源（已核对，非臆测）：
 *   - 西安工程大学教务处官网（jw.xpu.edu.cn）「作息时间」栏目：10 节课、每节 50 分钟；
 *   - 校党政办《关于调整临潼校区作息时间(试行)的通知》（2019-04-26）：自 2019-05-01 起
 *     临潼校区每节课由 45 分钟调整为 50 分钟。
 *
 * 注意：不同校区/学期可能微调（金花校区与临潼校区曾不同），因此这里是**可编辑的默认值**，
 * 设置页（M2）应允许用户改；如发现与实际不符，改本文件即可，不必动业务代码。
 */
package com.gould.xputimetable.data.db

import com.gould.xputimetable.domain.model.TimeSlot

/** 一天 24 小时 = 1440 分钟，用于把"时:分"换算成当天 0 点起的分钟数。 */
private fun minutes(hour: Int, minute: Int): Int = hour * 60 + minute

/**
 * 西工程大标准作息（12 节，每节 50 分钟）。
 *
 * 1-10 节**取自教务处官网**：08:00-08:50 / 09:00-09:50 / 10:10-11:00 / 11:10-12:00 /
 * 14:00-14:50 / 15:00-15:50 / 16:00-16:50 / 17:00-17:50 / 19:00-19:50 / 20:00-20:50
 *
 * 注意 11/12 节的作息**不是官网数据**：周视图网格兜底渲染 12 行（见 WeekGrid 的
 * defaultSectionCount），而旧表只有 10 节 → 第 11、12 行显示不出时间（用户报告的缺陷）。
 * 这里按 9/10 节的规律外推为 21:00-21:50 / 22:00-22:50；**如与学校实际作息不符，
 * 改本文件两行即可**（该值只在建库时写入一次，老库靠 ensureDefaultTimeSlots 增量补齐）。
 */
internal val defaultTimeSlots: List<TimeSlot> = listOf(
    TimeSlot(id = 0L, section = 1, startMinute = minutes(8, 0), endMinute = minutes(8, 50)),
    TimeSlot(id = 0L, section = 2, startMinute = minutes(9, 0), endMinute = minutes(9, 50)),
    TimeSlot(id = 0L, section = 3, startMinute = minutes(10, 10), endMinute = minutes(11, 0)),
    TimeSlot(id = 0L, section = 4, startMinute = minutes(11, 10), endMinute = minutes(12, 0)),
    TimeSlot(id = 0L, section = 5, startMinute = minutes(14, 0), endMinute = minutes(14, 50)),
    TimeSlot(id = 0L, section = 6, startMinute = minutes(15, 0), endMinute = minutes(15, 50)),
    TimeSlot(id = 0L, section = 7, startMinute = minutes(16, 0), endMinute = minutes(16, 50)),
    TimeSlot(id = 0L, section = 8, startMinute = minutes(17, 0), endMinute = minutes(17, 50)),
    TimeSlot(id = 0L, section = 9, startMinute = minutes(19, 0), endMinute = minutes(19, 50)),
    TimeSlot(id = 0L, section = 10, startMinute = minutes(20, 0), endMinute = minutes(20, 50)),
    TimeSlot(id = 0L, section = 11, startMinute = minutes(21, 0), endMinute = minutes(21, 50)),
    TimeSlot(id = 0L, section = 12, startMinute = minutes(22, 0), endMinute = minutes(22, 50)),
)

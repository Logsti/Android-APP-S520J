package com.s520j.app.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId = ZoneId.systemDefault()
private val monthTitle = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val dayTitle = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val shortDate = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)
private val heroDate = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val monthOnly = DateTimeFormatter.ofPattern("M月", Locale.CHINA)
private val weekdayNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
private val fullWeekdayNames = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

fun nowMillis(): Long = System.currentTimeMillis()

fun today(): LocalDate = LocalDate.now(zone)

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun Long.toDayTitle(): String = toLocalDate().format(dayTitle)

fun LocalDate.toDayTitle(): String = format(dayTitle)

fun Long.toShortDate(): String = toLocalDate().format(shortDate)

fun LocalDate.monthTitle(): String = format(monthTitle)

fun LocalDate.weekdayName(): String = weekdayNames[dayOfWeek.value % 7]

/** 首页问候卡：11月5日 · 星期四 */
fun LocalDate.heroDateText(): String = "${format(heroDate)} · ${fullWeekdayNames[dayOfWeek.value % 7]}"

/** 心愿卡日期标签：12月 */
fun Long.toMonthTitle(): String = toLocalDate().format(monthOnly)

fun LocalDate.epochMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun daysBetween(from: Long, to: Long = System.currentTimeMillis()): Long {
    if (from <= 0L) return 0L
    val start = from.toLocalDate()
    val end = to.toLocalDate()
    return java.time.temporal.ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
}

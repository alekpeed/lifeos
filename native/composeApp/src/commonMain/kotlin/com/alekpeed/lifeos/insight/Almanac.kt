package com.alekpeed.lifeos.insight

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// The Almanac's arithmetic, and the floors under it (§7 D-4).
//
// Two different things live on that screen and only one of them is trustworthy. A
// correlation describes what happened and claims nothing about what happens next. A
// forecast draws a straight line through past points and extends it — and renders a
// precise decimal while doing so, which is how a fit on six points comes to look like
// a finding.
//
// D-4's answer was both halves of the same idea: raise the floors so a number only
// appears once it could mean something, and then always show the sample it came from,
// so the trust judgment happens where the number is read rather than in a comment
// nobody sees. `r = 0.6 · 34 days` says what `r = 0.6` hides.
//
// Pulled out of the screen so it can be tested: the floors are the kind of constant
// that gets quietly lowered to make a demo look better, and a test is what notices.

// Minimum sample sizes. Raised 2026-08-22 — the previous floors (5/5/3/14) let a
// number reach the screen long before it carried any information, and a precise
// decimal on six days of self-reported data reads as authoritative when it isn't.
const val CORR_MIN = 21
const val TREND_MIN = 21
const val MONTHS_MIN = 6
const val WEEKDAY_MIN_DAYS = 42

// A straight line through two points is not a forecast, it is the line between them
// extended. Nothing fits under this.
const val LINREG_MIN = 6
const val READING_MIN = 4

data class Lin(val slope: Double, val intercept: Double)

fun pearson(pairs: List<Pair<Double, Double>>): Double? {
    if (pairs.size < LINREG_MIN) return null
    val mx = pairs.map { it.first }.average(); val my = pairs.map { it.second }.average()
    var sxy = 0.0; var sxx = 0.0; var syy = 0.0
    for ((x, y) in pairs) { val dx = x - mx; val dy = y - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
    if (sxx == 0.0 || syy == 0.0) return null
    return sxy / sqrt(sxx * syy)
}

fun linregress(pts: List<Pair<Double, Double>>): Lin? {
    if (pts.size < LINREG_MIN) return null
    val mx = pts.map { it.first }.average(); val my = pts.map { it.second }.average()
    var sxy = 0.0; var sxx = 0.0
    for ((x, y) in pts) { val dx = x - mx; sxy += dx * (y - my); sxx += dx * dx }
    if (sxx == 0.0) return null
    val slope = sxy / sxx
    return Lin(slope, my - slope * mx)
}

fun strength(r: Double): String {
    val a = abs(r)
    val s = when { a >= 0.7 -> "strong"; a >= 0.4 -> "moderate"; a >= 0.2 -> "weak"; else -> "little" }
    return "$s ${if (r >= 0) "positive" else "negative"}"
}

fun fmt1(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

// The sample, rendered to sit directly after the figure it belongs to. Always shown —
// D-4's second half is not "show it when it's thin", because a reader cannot tell a
// missing sample size from a large one.
fun sample(n: Int, unit: String): String = " · $n $unit${if (n == 1) "" else "s"}"

// A correlation between sleep and tasks completed is fitted over every day with a sleep
// figure, counting a day with no completed task as a zero — which is real data, but a
// hundred zeros and five real days is not a hundred-day finding. Both numbers are shown
// so the reader can see the difference rather than infer it.
fun sampleWithActive(n: Int, active: Int, unit: String): String =
    sample(n, unit) + ", $active with a task"

// Which weekday a habit is likeliest to be skipped, and the count that says so — the
// weekday alone is a claim, "kept 3 of 12" is the evidence for it.
data class WeekdayGap(val weekday: Int, val kept: Int, val elapsed: Int)

fun worstWeekday(perWeekday: IntArray, spanDays: Int): WeekdayGap? {
    if (perWeekday.size != 7 || spanDays < WEEKDAY_MIN_DAYS) return null
    val worst = perWeekday.indices.minByOrNull { perWeekday[it] } ?: return null
    // How many of that weekday have gone by. Integer division on purpose: a partial
    // week has not produced another Monday to have skipped.
    val elapsed = spanDays / 7
    if (elapsed < 1) return null
    return WeekdayGap(worst, perWeekday[worst], elapsed)
}

// Carriers, so a figure and its sample cannot get separated on the way to the screen.
data class ReadingForecast(val title: String, val finishDate: String, val logs: Int)
data class SpendForecast(val projected: Double, val slope: Double, val months: Int)

val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

package com.alekpeed.lifeos.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.finance.financeSeries
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.health.healthSeries
import kotlinx.datetime.daysUntil
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// The Almanac — long-horizon statistics over real logged history: Pearson
// correlations between curated pairs, a Forecasts section, and a What-If
// sandbox, each with a "not enough data yet" floor so a number never shows on
// too thin a sample. Pure computation — no AI, nothing invented.

private const val CORR_MIN = 5
private const val TREND_MIN = 5
private const val MONTHS_MIN = 3
private const val WEEKDAY_MIN_DAYS = 14

private data class Lin(val slope: Double, val intercept: Double)

private fun pearson(pairs: List<Pair<Double, Double>>): Double? {
    if (pairs.size < 2) return null
    val mx = pairs.map { it.first }.average(); val my = pairs.map { it.second }.average()
    var sxy = 0.0; var sxx = 0.0; var syy = 0.0
    for ((x, y) in pairs) { val dx = x - mx; val dy = y - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
    if (sxx == 0.0 || syy == 0.0) return null
    return sxy / sqrt(sxx * syy)
}

private fun linregress(pts: List<Pair<Double, Double>>): Lin? {
    if (pts.size < 2) return null
    val mx = pts.map { it.first }.average(); val my = pts.map { it.second }.average()
    var sxy = 0.0; var sxx = 0.0
    for ((x, y) in pts) { val dx = x - mx; sxy += dx * (y - my); sxx += dx * dx }
    if (sxx == 0.0) return null
    val slope = sxy / sxx
    return Lin(slope, my - slope * mx)
}

private fun strength(r: Double): String {
    val a = abs(r)
    val s = when { a >= 0.7 -> "strong"; a >= 0.4 -> "moderate"; a >= 0.2 -> "weak"; else -> "little" }
    return "$s ${if (r >= 0) "positive" else "negative"}"
}

private fun fmt1(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

@Composable
fun AlmanacScreen() {
    val model = remember {
        val health = healthSeries()
        // Latest reading per (metric, date).
        fun byDate(metric: String): Map<String, Double> =
            health.filter { it.metric.equals(metric, ignoreCase = true) && it.date.isNotBlank() }
                .associate { it.date to it.value }
        val sleep = byDate("Sleep")
        val workout = byDate("Workout")

        val habits = loadHabits()
        fun habitsKept(date: String): Int {
            val d = parseDateOrNull(date) ?: return 0
            return habits.count { d in it.checkins }
        }

        // Correlations
        val sleepVsHabits = sleep.keys.map { it to (sleep[it]!! to habitsKept(it).toDouble()) }
            .map { it.second }
        val workoutVsSleep = workout.keys.filter { sleep.containsKey(it) }
            .map { workout[it]!! to sleep[it]!! }

        // Sleep vs. tasks completed that day (completedDate stamps make this real).
        val tasksByDate = com.alekpeed.lifeos.tasks.loadTasks()
            .filter { it.done && it.completedDate.isNotBlank() }
            .groupingBy { it.completedDate }.eachCount()
        val sleepVsTasks = sleep.keys.map { sleep[it]!! to (tasksByDate[it] ?: 0).toDouble() }

        val corrSleepHabits = if (sleepVsHabits.size >= CORR_MIN) pearson(sleepVsHabits) else null
        val corrWorkoutSleep = if (workoutVsSleep.size >= CORR_MIN) pearson(workoutVsSleep) else null
        val corrSleepTasks = if (sleepVsTasks.count { it.second > 0 } >= CORR_MIN) pearson(sleepVsTasks) else null
        val sleepHabitsLin = if (sleepVsHabits.size >= CORR_MIN) linregress(sleepVsHabits) else null

        // Sleep trend (value over ordered day index)
        val sleepOrdered = sleep.entries.sortedBy { it.key }
        val sleepTrend = if (sleepOrdered.size >= TREND_MIN)
            linregress(sleepOrdered.mapIndexed { i, e -> i.toDouble() to e.value })?.let { it to sleepOrdered.size } else null

        // Reading pace → est finish for in-progress books
        val books = loadBooks().books
        val readingForecasts = books.filter { it.status == "reading" && (it.totalPages ?: 0) > 0 && it.logs.size >= 2 }
            .mapNotNull { b ->
                val logs = b.logs.mapNotNull { l -> parseDateOrNull(l.date)?.let { it to l.pagesRead } }.sortedBy { it.first }
                if (logs.size < 2) return@mapNotNull null
                val spanDays = logs.first().first.daysUntilCompat(logs.last().first).coerceAtLeast(1)
                val totalLogged = logs.sumOf { it.second }
                val perDay = totalLogged.toDouble() / spanDays
                if (perDay <= 0) return@mapNotNull null
                val remaining = (b.totalPages!! - (b.currentPage ?: 0)).coerceAtLeast(0)
                val daysLeft = (remaining / perDay).roundToInt()
                b.title.ifBlank { "(untitled)" } to today().plusDays(daysLeft).toString()
            }

        // Spending trend (monthly total of spending, negative amounts) → next month
        val fin = financeSeries()
        val byMonth = fin.filter { it.amount < 0 && it.date.length >= 7 }
            .groupBy { it.date.take(7) }.mapValues { e -> e.value.sumOf { abs(it.amount) } }
            .toSortedMap()
        val spendForecast = if (byMonth.size >= MONTHS_MIN)
            linregress(byMonth.values.mapIndexed { i, v -> i.toDouble() to v })?.let { lin ->
                (lin.slope * byMonth.size + lin.intercept).coerceAtLeast(0.0) to lin.slope
            } else null

        // Habit weekday-skip
        val weekdaySkips = habits.mapNotNull { h ->
            val dates = h.checkins.toList()
            val first = dates.minOrNull() ?: return@mapNotNull null
            val span = first.daysUntilCompat(today())
            if (span < WEEKDAY_MIN_DAYS) return@mapNotNull null
            // check-ins per weekday vs weeks elapsed
            val perWd = IntArray(7)
            dates.forEach { perWd[it.dayOfWeek.ordinal] += 1 }
            val worst = perWd.indices.minByOrNull { perWd[it] } ?: return@mapNotNull null
            h.name to WEEKDAY_NAMES[worst]
        }

        // Recurring (subscription-like) costs for the what-if
        val recurring = fin.filter { it.recurring && it.amount < 0 }
            .distinctBy { it.desc }.map { it.desc to abs(it.amount) }

        AlmanacModel(
            corrSleepHabits, corrWorkoutSleep, corrSleepTasks, sleepHabitsLin,
            sleepTrend, readingForecasts, spendForecast, weekdaySkips,
            recurring, sleep.values.toList(),
        )
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("The Almanac", style = MaterialTheme.typography.headlineMedium)
        Text("Patterns over your real logged history — nothing shows until there's enough data to mean something.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Head("Correlations") }
            item {
                val lines = buildList {
                    model.corrSleepHabits?.let { add("Sleep vs. habits kept: ${strength(it)} (r = ${fmt1(it)})") }
                    model.corrWorkoutSleep?.let { add("Workout minutes vs. sleep: ${strength(it)} (r = ${fmt1(it)})") }
                    model.corrSleepTasks?.let { add("Sleep vs. tasks completed: ${strength(it)} (r = ${fmt1(it)})") }
                }
                if (lines.isEmpty()) Muted("Log sleep, workouts, and habit check-ins on the same days to see how they move together (need $CORR_MIN+ days).")
                else Column { lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } }
            }

            item { Head("Forecasts") }
            item {
                val f = buildList {
                    model.sleepTrend?.let { (lin, _) ->
                        val dir = if (lin.slope > 0.02) "trending up" else if (lin.slope < -0.02) "trending down" else "holding steady"
                        add("Sleep is $dir (${fmt1(lin.slope)}h per night, per day logged).")
                    }
                    model.spendForecast?.let { (proj, slope) ->
                        add("Spending trend: next month ≈ $${proj.roundToInt()} (${if (slope >= 0) "rising" else "falling"}).")
                    }
                    model.readingForecasts.forEach { (title, date) -> add("At your pace, you'll finish \"$title\" around $date.") }
                    model.weekdaySkips.forEach { (habit, wd) -> add("You're most likely to skip \"$habit\" on $wd.") }
                }
                if (f.isEmpty()) Muted("Forecasts appear once there's enough logged history — a couple weeks of habits, a few months of spending, a reading log in progress.")
                else Column { f.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } }
            }

            item { Head("What if…") }
            item { WhatIf(model) }
        }
    }
}

@Composable
private fun WhatIf(model: AlmanacModel) {
    Column {
        // Sleep slider → projected habits/day
        val lin = model.sleepHabitsLin
        if (lin != null && model.sleepValues.isNotEmpty()) {
            val avg = model.sleepValues.average()
            var sleep by remember { mutableStateOf(avg.toFloat()) }
            val projected = (lin.slope * sleep + lin.intercept).coerceAtLeast(0.0)
            Text("If I slept ${fmt1(sleep.toDouble())}h a night…", style = MaterialTheme.typography.bodyMedium)
            Slider(value = sleep, onValueChange = { sleep = it }, valueRange = (avg - 2).toFloat()..(avg + 2).toFloat())
            Text("…you'd average about ${fmt1(projected)} habits kept per day (from your own sleep↔habits fit).", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        } else {
            Muted("Log more sleep and habits to unlock the sleep slider.")
            Spacer(Modifier.height(12.dp))
        }

        // Recurring-cost checklist → yearly savings
        if (model.recurring.isNotEmpty()) {
            Text("Cancel recurring charges to see yearly savings:", style = MaterialTheme.typography.bodyMedium)
            val checked = remember { mutableStateMapOf<String, Boolean>() }
            model.recurring.forEach { (desc, monthly) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked[desc] ?: false, onCheckedChange = { checked[desc] = it })
                    Text(desc, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("$${monthly.roundToInt()}/mo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val savings = model.recurring.filter { checked[it.first] == true }.sumOf { it.second } * 12
            Text("Yearly savings: $${savings.roundToInt()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        } else {
            Muted("Mark some Finance entries recurring to model cancelling them here.")
        }
    }
}

private data class AlmanacModel(
    val corrSleepHabits: Double?,
    val corrWorkoutSleep: Double?,
    val corrSleepTasks: Double?,
    val sleepHabitsLin: Lin?,
    val sleepTrend: Pair<Lin, Int>?,
    val readingForecasts: List<Pair<String, String>>,
    val spendForecast: Pair<Double, Double>?,
    val weekdaySkips: List<Pair<String, String>>,
    val recurring: List<Pair<String, Double>>,
    val sleepValues: List<Double>,
)

private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
private fun Head(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun kotlinx.datetime.LocalDate.daysUntilCompat(other: kotlinx.datetime.LocalDate): Int =
    this.daysUntil(other)

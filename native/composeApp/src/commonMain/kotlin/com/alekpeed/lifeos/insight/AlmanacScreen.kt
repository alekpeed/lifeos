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

// The Almanac — long-horizon statistics over real logged history: Pearson
// correlations between curated pairs, a Forecasts section, and a What-If
// sandbox, each with a "not enough data yet" floor so a number never shows on
// too thin a sample. Pure computation — no AI, nothing invented.
//
// Every figure carries the sample it came from (§7 D-4). The arithmetic and the floors
// live in Almanac.kt; this file is the screen that has to say what they rest on.

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

        // Each figure travels with the number of pairs it was fitted on, because the
        // screen has to print it beside the figure and there is no recovering it later.
        val activeTaskDays = sleepVsTasks.count { it.second > 0 }
        val corrSleepHabits = if (sleepVsHabits.size >= CORR_MIN) pearson(sleepVsHabits)?.let { it to sleepVsHabits.size } else null
        val corrWorkoutSleep = if (workoutVsSleep.size >= CORR_MIN) pearson(workoutVsSleep)?.let { it to workoutVsSleep.size } else null
        // Gated on days that actually had a completed task, fitted over every sleep day
        // with a zero for the rest. Both numbers reach the screen; see sampleWithActive.
        val corrSleepTasks = if (activeTaskDays >= CORR_MIN) pearson(sleepVsTasks)?.let { it to sleepVsTasks.size } else null
        val sleepHabitsLin = if (sleepVsHabits.size >= CORR_MIN) linregress(sleepVsHabits)?.let { it to sleepVsHabits.size } else null

        // Sleep trend (value over ordered day index)
        val sleepOrdered = sleep.entries.sortedBy { it.key }
        val sleepTrend = if (sleepOrdered.size >= TREND_MIN)
            linregress(sleepOrdered.mapIndexed { i, e -> i.toDouble() to e.value })?.let { it to sleepOrdered.size } else null

        // Reading pace → est finish for in-progress books
        val books = loadBooks().books
        val readingForecasts = books.filter { it.status == "reading" && (it.totalPages ?: 0) > 0 && it.logs.size >= READING_MIN }
            .mapNotNull { b ->
                val logs = b.logs.mapNotNull { l -> parseDateOrNull(l.date)?.let { it to l.pagesRead } }.sortedBy { it.first }
                if (logs.size < READING_MIN) return@mapNotNull null
                val spanDays = logs.first().first.daysUntilCompat(logs.last().first).coerceAtLeast(1)
                val totalLogged = logs.sumOf { it.second }
                val perDay = totalLogged.toDouble() / spanDays
                if (perDay <= 0) return@mapNotNull null
                val remaining = (b.totalPages!! - (b.currentPage ?: 0)).coerceAtLeast(0)
                val daysLeft = (remaining / perDay).roundToInt()
                ReadingForecast(b.title.ifBlank { "(untitled)" }, today().plusDays(daysLeft).toString(), logs.size)
            }

        // Spending trend (monthly total of spending, negative amounts) → next month
        val fin = financeSeries()
        val byMonth = fin.filter { it.amount < 0 && it.date.length >= 7 }
            .groupBy { it.date.take(7) }.mapValues { e -> e.value.sumOf { abs(it.amount) } }
            .toSortedMap()
        val spendForecast = if (byMonth.size >= MONTHS_MIN)
            linregress(byMonth.values.mapIndexed { i, v -> i.toDouble() to v })?.let { lin ->
                SpendForecast((lin.slope * byMonth.size + lin.intercept).coerceAtLeast(0.0), lin.slope, byMonth.size)
            } else null

        // Habit weekday-skip
        val weekdaySkips = habits.mapNotNull { h ->
            val dates = h.checkins.toList()
            val first = dates.minOrNull() ?: return@mapNotNull null
            val span = first.daysUntilCompat(today())
            val perWd = IntArray(7)
            dates.forEach { perWd[it.dayOfWeek.ordinal] += 1 }
            // The weekday alone is a claim; the count behind it is the evidence, and
            // "kept 3 of 12" is what tells you whether to believe it.
            worstWeekday(perWd, span)?.let { h.name to it }
        }

        // Recurring (subscription-like) costs for the what-if
        val recurring = fin.filter { it.recurring && it.amount < 0 }
            .distinctBy { it.desc }.map { it.desc to abs(it.amount) }

        AlmanacModel(
            corrSleepHabits, corrWorkoutSleep, corrSleepTasks, activeTaskDays, sleepHabitsLin,
            sleepTrend, readingForecasts, spendForecast, weekdaySkips,
            recurring, sleep.values.toList(),
        )
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Patterns over your real logged history — nothing shows until there's enough data to mean something.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Head("Correlations") }
            item {
                val lines = buildList {
                    model.corrSleepHabits?.let { (r, n) ->
                        add("Sleep vs. habits kept: ${strength(r)} (r = ${fmt1(r)})${sample(n, "day")}")
                    }
                    model.corrWorkoutSleep?.let { (r, n) ->
                        add("Workout minutes vs. sleep: ${strength(r)} (r = ${fmt1(r)})${sample(n, "day")}")
                    }
                    model.corrSleepTasks?.let { (r, n) ->
                        add("Sleep vs. tasks completed: ${strength(r)} (r = ${fmt1(r)})${sampleWithActive(n, model.activeTaskDays, "day")}")
                    }
                }
                if (lines.isEmpty()) Muted("Log sleep, workouts, and habit check-ins on the same days to see how they move together (need $CORR_MIN+ days).")
                else Column { lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } }
            }

            item { Head("Forecasts") }
            item {
                val f = buildList {
                    model.sleepTrend?.let { (lin, n) ->
                        val dir = if (lin.slope > 0.02) "trending up" else if (lin.slope < -0.02) "trending down" else "holding steady"
                        add("Sleep is $dir (${fmt1(lin.slope)}h per night, per day logged)${sample(n, "night")}.")
                    }
                    model.spendForecast?.let { sf ->
                        add("Spending trend: next month ≈ $${sf.projected.roundToInt()} (${if (sf.slope >= 0) "rising" else "falling"})${sample(sf.months, "month")}.")
                    }
                    model.readingForecasts.forEach { r ->
                        add("At your pace, you'll finish \"${r.title}\" around ${r.finishDate}${sample(r.logs, "reading log")}.")
                    }
                    model.weekdaySkips.forEach { (habit, gap) ->
                        add(
                            "You're most likely to skip \"$habit\" on ${WEEKDAY_NAMES[gap.weekday]} " +
                                "— kept ${gap.kept} of ${gap.elapsed}.",
                        )
                    }
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
        val fit = model.sleepHabitsLin
        if (fit != null && model.sleepValues.isNotEmpty()) {
            val (lin, n) = fit
            val avg = model.sleepValues.average()
            var sleep by remember { mutableStateOf(avg.toFloat()) }
            val projected = (lin.slope * sleep + lin.intercept).coerceAtLeast(0.0)
            Text("If I slept ${fmt1(sleep.toDouble())}h a night…", style = MaterialTheme.typography.bodyMedium)
            Slider(value = sleep, onValueChange = { sleep = it }, valueRange = (avg - 2).toFloat()..(avg + 2).toFloat())
            // The slider is where a thin fit does the most damage: it refits nothing, it
            // just reads off the same line, live, and returns a confident number for any
            // input you drag to. So it says what the line was drawn through.
            Text(
                "…you'd average about ${fmt1(projected)} habits kept per day — read off your own " +
                    "sleep↔habits fit${sample(n, "day")}.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

// Every figure that can appear carries the sample it rests on, and the types say so:
// there is no way to render one of these without the count beside it (§7 D-4).
private data class AlmanacModel(
    val corrSleepHabits: Pair<Double, Int>?,
    val corrWorkoutSleep: Pair<Double, Int>?,
    val corrSleepTasks: Pair<Double, Int>?,
    val activeTaskDays: Int,
    val sleepHabitsLin: Pair<Lin, Int>?,
    val sleepTrend: Pair<Lin, Int>?,
    val readingForecasts: List<ReadingForecast>,
    val spendForecast: SpendForecast?,
    val weekdaySkips: List<Pair<String, WeekdayGap>>,
    val recurring: List<Pair<String, Double>>,
    val sleepValues: List<Double>,
)

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

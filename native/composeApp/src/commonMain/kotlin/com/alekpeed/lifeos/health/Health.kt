package com.alekpeed.lifeos.health

import com.alekpeed.lifeos.Storage
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Health — two shapes side by side, one storage key ("Health"):
//  • Reading  — free-form dated metric points (the original native model; any
//    metric name, quick logging, trend bars).
//  • DailyLog — the web app's structured one-row-per-day log (sleep, workout,
//    water, weight, notes), which the Apple Health / Garmin importers merge into.
// healthSeries() flattens BOTH into (metric, value, date) points so The Almanac's
// correlations ("Sleep", "Workout", …) see structured logs and free-form readings
// alike without knowing the difference.

@Serializable
data class Reading(val id: Long, val metric: String, val value: Double, val unit: String = "", val date: String = "")

@Serializable
data class DailyLog(
    val date: String,                    // ISO, the row key
    val sleepHours: Double? = null,
    val workoutType: String = "",
    val workoutMinutes: Double? = null,
    val waterOz: Double? = null,
    val weightLb: Double? = null,
    val notes: String = "",
) {
    val isEmpty: Boolean
        get() = sleepHours == null && workoutType.isBlank() && workoutMinutes == null &&
            waterOz == null && weightLb == null && notes.isBlank()
}

// One logged workout session. Distance is optional and unit-tagged (meters for
// rowing/swimming, miles for road work) so pace math knows what it's looking at.
@Serializable
data class Workout(
    val id: Long,
    val date: String,
    val type: String,                    // Rowing | Run | Walk | Cycle | Swim | Lift | Yoga | Other…
    val minutes: Double? = null,
    val distance: Double? = null,
    val distanceUnit: String = "",       // "m" | "mi" | ""
    val notes: String = "",
)

@Serializable
data class HealthData(
    val readings: List<Reading> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
    val workouts: List<Workout> = emptyList(),
)

val WORKOUT_TYPES = listOf("Rowing", "Run", "Walk", "Cycle", "Swim", "Lift", "Yoga", "Other")

// The natural distance unit per type ("" = distance not usually tracked).
fun defaultDistanceUnit(type: String): String = when (type) {
    "Rowing", "Swim" -> "m"
    "Run", "Walk", "Cycle" -> "mi"
    else -> ""
}

// "m:ss" from decimal minutes (7.53 → "7:32").
fun mmss(minutes: Double): String {
    val totalSeconds = (minutes * 60).toLong().coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

// The headline pace for a session, in the unit that sport actually uses:
// rowing → /500m split, swimming → /100m, run/walk → min/mi, cycling → mph.
fun paceLabel(w: Workout): String? {
    val mins = w.minutes ?: return null
    val dist = w.distance ?: return null
    if (mins <= 0 || dist <= 0) return null
    return when {
        w.type == "Rowing" && w.distanceUnit == "m" -> "${mmss(mins / (dist / 500.0))} /500m"
        w.type == "Swim" && w.distanceUnit == "m" -> "${mmss(mins / (dist / 100.0))} /100m"
        w.type == "Cycle" && w.distanceUnit == "mi" -> "${((dist / (mins / 60.0)) * 10).toLong() / 10.0} mph"
        w.distanceUnit == "mi" -> "${mmss(mins / dist)} /mi"
        else -> null
    }
}

// Numeric pace for best-of comparisons (lower is better; cycling inverted to
// minutes-per-mile so "lower wins" holds for every type).
fun paceValue(w: Workout): Double? {
    val mins = w.minutes ?: return null
    val dist = w.distance ?: return null
    if (mins <= 0 || dist <= 0) return null
    return when {
        w.type == "Rowing" && w.distanceUnit == "m" -> mins / (dist / 500.0)
        w.type == "Swim" && w.distanceUnit == "m" -> mins / (dist / 100.0)
        w.distanceUnit == "mi" -> mins / dist
        else -> null
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadHealth(): HealthData {
    val raw = Storage.read("Health")
    if (raw.isNullOrBlank()) return HealthData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<HealthData>(raw) }.getOrElse { HealthData() }
    }
    // Migrate old "metric\tvalue\tunit" lines (undated).
    val readings = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val p = line.split("\t")
        Reading(i + 1L, p.getOrElse(0) { line }, p.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0, p.getOrElse(2) { "" })
    }
    return HealthData(readings = readings)
}

fun saveHealth(data: HealthData) {
    Storage.write("Health", json.encodeToString(data))
}

// Public read-only accessor for the stats layer (The Almanac). Structured daily
// logs surface under the same metric names the free-form quick chips use, so
// existing correlations pick them up unchanged.
data class HealthPoint(val metric: String, val value: Double, val date: String)

fun healthSeries(): List<HealthPoint> {
    val data = loadHealth()
    val fromReadings = data.readings.map { HealthPoint(it.metric, it.value, it.date) }
    // Workout-log sessions are the strongest signal — sum minutes per day.
    val workoutByDate = data.workouts.filter { (it.minutes ?: 0.0) > 0 }
        .groupBy { it.date }.mapValues { e -> e.value.sumOf { it.minutes ?: 0.0 } }
    val fromWorkouts = workoutByDate.map { (date, mins) -> HealthPoint("Workout", mins, date) }
    val fromLogs = data.logs.flatMap { log ->
        buildList {
            log.sleepHours?.let { add(HealthPoint("Sleep", it, log.date)) }
            // The daily log's workout minutes yield to workout-log sessions on the
            // same day, so a session logged in both places isn't double-counted.
            if (!workoutByDate.containsKey(log.date)) {
                log.workoutMinutes?.let { add(HealthPoint("Workout", it, log.date)) }
            }
            log.waterOz?.let { add(HealthPoint("Water", it, log.date)) }
            log.weightLb?.let { add(HealthPoint("Weight", it, log.date)) }
        }
    }
    // A structured log wins over a same-day free-form reading of the same metric,
    // so an Apple import followed by Almanac math doesn't double-count the day.
    val structured = fromLogs + fromWorkouts
    val logKeys = structured.map { it.metric to it.date }.toSet()
    return fromReadings.filterNot { (it.metric to it.date) in logKeys } + structured
}

// ---- Import: shared merge ---------------------------------------------------

data class ImportResult(val created: Int, val updated: Int)

// Merge imported days into the stored logs: fields present on the import
// overwrite the same fields on an existing log for that date (never silently —
// callers show a preview + confirm first, same as the web app); missing fields
// are left alone; new dates become new logs.
fun mergeImportedDays(existing: List<DailyLog>, imported: List<DailyLog>): Pair<List<DailyLog>, ImportResult> {
    val byDate = existing.associateBy { it.date }.toMutableMap()
    var created = 0
    var updated = 0
    imported.filter { !it.isEmpty }.forEach { day ->
        val cur = byDate[day.date]
        if (cur == null) {
            byDate[day.date] = day
            created += 1
        } else {
            byDate[day.date] = cur.copy(
                sleepHours = day.sleepHours ?: cur.sleepHours,
                workoutType = day.workoutType.ifBlank { cur.workoutType },
                workoutMinutes = day.workoutMinutes ?: cur.workoutMinutes,
                waterOz = day.waterOz ?: cur.waterOz,
                weightLb = day.weightLb ?: cur.weightLb,
            )
            updated += 1
        }
    }
    return byDate.values.sortedByDescending { it.date } to ImportResult(created, updated)
}

// ---- Apple Health export parser --------------------------------------------

// The substrings the platform's streaming file reader keeps lines for — a raw
// export.xml is routinely hundreds of MB, but the sleep/water/weight/workout
// lines are a tiny fraction of it.
val APPLE_HEALTH_FILTER = listOf("SleepAnalysis", "DietaryWater", "BodyMass", "<Workout")

private val WORKOUT_TYPE_LABELS = mapOf(
    "HKWorkoutActivityTypeRunning" to "Running",
    "HKWorkoutActivityTypeWalking" to "Walking",
    "HKWorkoutActivityTypeCycling" to "Cycling",
    "HKWorkoutActivityTypeSwimming" to "Swimming",
    "HKWorkoutActivityTypeYoga" to "Yoga",
    "HKWorkoutActivityTypeFunctionalStrengthTraining" to "Strength training",
    "HKWorkoutActivityTypeTraditionalStrengthTraining" to "Strength training",
    "HKWorkoutActivityTypeHiking" to "Hiking",
    "HKWorkoutActivityTypeElliptical" to "Elliptical",
    "HKWorkoutActivityTypeRowing" to "Rowing",
    "HKWorkoutActivityTypeHighIntensityIntervalTraining" to "HIIT",
    "HKWorkoutActivityTypeCoreTraining" to "Core training",
    "HKWorkoutActivityTypeMixedCardio" to "Cardio",
)

private fun friendlyWorkoutType(raw: String): String {
    WORKOUT_TYPE_LABELS[raw]?.let { return it }
    val stripped = raw.removePrefix("HKWorkoutActivityType")
    if (stripped.isBlank()) return "Workout"
    return stripped.replace(Regex("([a-z])([A-Z])"), "$1 $2")
}

private fun attr(line: String, name: String): String? =
    Regex("$name=\"([^\"]*)\"").find(line)?.groupValues?.get(1)

// "2026-06-01 23:04:11 -0700" → epoch seconds (null if malformed). The date part
// of the same string is already local time, so day attribution reads it directly.
private fun appleEpochSeconds(s: String): Long? = try {
    val ldt = LocalDateTime.parse(s.take(10) + "T" + s.substring(11, 19))
    val off = s.substring(20)
    val sign = if (off.startsWith("-")) -1 else 1
    val hh = off.substring(1, 3).toInt()
    val mm = off.substring(3, 5).toInt()
    ldt.toInstant(UtcOffset(sign * hh, sign * mm)).epochSeconds
} catch (e: Exception) {
    null
}

private fun appleDay(s: String?): String? = s?.takeIf { it.length >= 10 }?.take(10)

private class DayAgg {
    var sleepMinutes = 0.0
    var workoutMinutes = 0.0
    val workoutTypes = LinkedHashSet<String>()
    var waterOz = 0.0
    var weightLb: Double? = null
}

// Parse the (pre-filtered) lines of an Apple Health export.xml into one row per
// day. Sleep counts only time actually asleep (the "Asleep*" values, not InBed/
// Awake) and is credited to the wake-up day. Water normalizes mL/L → oz; weight
// normalizes kg → lb. Best-effort: malformed lines are skipped, never fatal.
fun parseAppleHealth(text: String): List<DailyLog> {
    val days = HashMap<String, DayAgg>()
    fun agg(day: String) = days.getOrPut(day) { DayAgg() }

    text.lineSequence().forEach { line ->
        when {
            line.contains("SleepAnalysis") -> {
                val value = attr(line, "value") ?: return@forEach
                if (!value.contains("Asleep", ignoreCase = true)) return@forEach
                val start = attr(line, "startDate") ?: return@forEach
                val end = attr(line, "endDate") ?: return@forEach
                val s = appleEpochSeconds(start) ?: return@forEach
                val e = appleEpochSeconds(end) ?: return@forEach
                val mins = (e - s) / 60.0
                if (mins <= 0 || mins > 24 * 60) return@forEach
                val day = appleDay(end) ?: return@forEach
                agg(day).sleepMinutes += mins
            }
            line.contains("HKQuantityTypeIdentifierDietaryWater") -> {
                val v = attr(line, "value")?.toDoubleOrNull() ?: return@forEach
                val unit = attr(line, "unit").orEmpty().lowercase()
                val oz = when {
                    unit.startsWith("ml") -> v / 29.5735
                    unit == "l" -> v * 1000 / 29.5735
                    else -> v // fl_oz_us and friends
                }
                val day = appleDay(attr(line, "startDate")) ?: return@forEach
                agg(day).waterOz += oz
            }
            line.contains("HKQuantityTypeIdentifierBodyMass\"") -> {
                // The trailing quote keeps BodyMassIndex / LeanBodyMass out.
                val v = attr(line, "value")?.toDoubleOrNull() ?: return@forEach
                val unit = attr(line, "unit").orEmpty().lowercase()
                val lb = if (unit.startsWith("kg")) v * 2.20462 else v
                val day = appleDay(attr(line, "startDate")) ?: return@forEach
                agg(day).weightLb = lb
            }
            line.contains("<Workout") -> {
                val type = attr(line, "workoutActivityType") ?: return@forEach
                val dur = attr(line, "duration")?.toDoubleOrNull() ?: 0.0
                val unit = attr(line, "durationUnit").orEmpty().lowercase()
                val mins = if (unit.startsWith("h")) dur * 60 else dur
                val day = appleDay(attr(line, "startDate")) ?: return@forEach
                val a = agg(day)
                a.workoutMinutes += mins
                a.workoutTypes.add(friendlyWorkoutType(type))
            }
        }
    }

    return days.entries.sortedBy { it.key }.map { (day, a) ->
        DailyLog(
            date = day,
            sleepHours = (a.sleepMinutes / 60.0).takeIf { it > 0 }?.let { (it * 10).toLong() / 10.0 },
            workoutType = a.workoutTypes.joinToString(", "),
            workoutMinutes = a.workoutMinutes.takeIf { it > 0 }?.let { (it * 10).toLong() / 10.0 },
            waterOz = a.waterOz.takeIf { it > 0 }?.let { (it * 10).toLong() / 10.0 },
            weightLb = a.weightLb?.let { (it * 10).toLong() / 10.0 },
        )
    }.filter { !it.isEmpty }
}

// ---- Garmin Connect CSV parser ---------------------------------------------

// Header-driven, best-effort. Understands the common Garmin Connect exports:
//  • Activities.csv — "Activity Type", "Date", "Time" (h:mm:ss) → workouts
//  • sleep exports — a "Date" column plus a sleep/duration column ("7:32",
//    "7h 32min", or decimal hours)
//  • weight exports — a "Date" column plus a "Weight" column (lb or kg-ish)
// Anything it can't recognize simply yields no rows; the preview shows exactly
// what was picked up before anything is written.
fun parseGarminCsv(text: String): List<DailyLog> {
    val rows = parseCsv(text)
    if (rows.size < 2) return emptyList()
    val header = rows[0].map { it.trim().lowercase() }
    fun idx(vararg keys: String) = header.indexOfFirst { h -> keys.any { h.contains(it) } }

    val dateIdx = idx("date")
    if (dateIdx < 0) return emptyList()
    val typeIdx = idx("activity type")
    val timeIdx = header.indexOfFirst { it == "time" || it.contains("duration") || it.contains("elapsed") }
    val sleepIdx = idx("sleep")
    val weightIdx = idx("weight")

    val days = LinkedHashMap<String, DailyLog>()
    for (r in rows.drop(1)) {
        val rawDate = r.getOrElse(dateIdx) { "" }.trim()
        val day = Regex("""\d{4}-\d{2}-\d{2}""").find(rawDate)?.value ?: continue
        var log = days[day] ?: DailyLog(date = day)

        if (typeIdx >= 0) {
            val type = r.getOrElse(typeIdx) { "" }.trim()
            val mins = if (timeIdx >= 0) parseDurationMinutes(r.getOrElse(timeIdx) { "" }) else null
            if (type.isNotEmpty()) {
                log = log.copy(
                    workoutType = if (log.workoutType.isBlank()) type else if (type in log.workoutType) log.workoutType else "${log.workoutType}, $type",
                    workoutMinutes = ((log.workoutMinutes ?: 0.0) + (mins ?: 0.0)).takeIf { it > 0 } ?: log.workoutMinutes,
                )
            }
        }
        if (sleepIdx >= 0 && typeIdx < 0) {
            parseDurationHours(r.getOrElse(sleepIdx) { "" })?.let { log = log.copy(sleepHours = (it * 10).toLong() / 10.0) }
        }
        if (weightIdx >= 0) {
            val w = r.getOrElse(weightIdx) { "" }.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            if (w != null && w > 0) {
                // Values under 130 with kg in the header (or plausibly metric) pass through as-is
                // only when the header says kg; otherwise assume lb.
                val lb = if (header.getOrElse(weightIdx) { "" }.contains("kg")) w * 2.20462 else w
                log = log.copy(weightLb = (lb * 10).toLong() / 10.0)
            }
        }
        days[day] = log
    }
    return days.values.filter { !it.isEmpty }.sortedBy { it.date }
}

// "1:02:33" → 62.6, "34:56" → 35.0 (mm:ss), "45" → 45.0 (already minutes).
private fun parseDurationMinutes(raw: String): Double? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val parts = s.split(":")
    return when (parts.size) {
        3 -> {
            val h = parts[0].toDoubleOrNull() ?: return null
            val m = parts[1].toDoubleOrNull() ?: return null
            val sec = parts[2].toDoubleOrNull() ?: 0.0
            ((h * 60 + m + sec / 60) * 10).toLong() / 10.0
        }
        2 -> {
            val m = parts[0].toDoubleOrNull() ?: return null
            val sec = parts[1].toDoubleOrNull() ?: 0.0
            ((m + sec / 60) * 10).toLong() / 10.0
        }
        else -> s.toDoubleOrNull()
    }
}

// "7:32" → 7.53, "7h 32min" → 7.53, "7.5" → 7.5. Values over 24 are junk.
private fun parseDurationHours(raw: String): Double? {
    val s = raw.trim().lowercase()
    if (s.isEmpty()) return null
    Regex("""(\d+)\s*h(?:rs?)?\s*(\d+)?\s*(?:m|min)?""").find(s)?.let {
        val h = it.groupValues[1].toDoubleOrNull() ?: return null
        val m = it.groupValues[2].toDoubleOrNull() ?: 0.0
        return (h + m / 60).takeIf { v -> v in 0.0..24.0 }
    }
    val parts = s.split(":")
    if (parts.size == 2) {
        val h = parts[0].toDoubleOrNull() ?: return null
        val m = parts[1].toDoubleOrNull() ?: return null
        return (h + m / 60).takeIf { v -> v in 0.0..24.0 }
    }
    return s.toDoubleOrNull()?.takeIf { it in 0.0..24.0 }
}

// Quote-aware CSV → rows of fields (same dialect Finance's importer accepts).
private fun parseCsv(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var field = StringBuilder()
    var row = ArrayList<String>()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
            c == '"' -> inQuotes = true
            c == ',' -> { row.add(field.toString()); field = StringBuilder() }
            c == '\n' || c == '\r' -> {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row.add(field.toString()); field = StringBuilder()
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = ArrayList()
            }
            else -> field.append(c)
        }
        i++
    }
    row.add(field.toString())
    if (row.any { it.isNotBlank() }) rows.add(row)
    return rows
}

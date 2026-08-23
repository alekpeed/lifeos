package com.alekpeed.lifeos.timemachine

import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.Change
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.history.Mutation
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// §4 — the Time Machine, rebuilt on the mutation log.
//
// The old screen reconstructed history from creation dates, because creation dates were
// the only history there was. It could say a record existed on a Tuesday; it could not
// say what the record said, what changed it, or that three others were deleted that
// week. Those were not UI shortcomings — the data was never captured.
//
// It is captured now (R-02/R-03), so scrubbing to a date becomes replaying the log to
// that point. What this file adds is the honest boundary around that: the log started
// when it was built and is capped, so replay is exact back to its first event and
// guessing before it. A screen that blurred the two would be the old problem in nicer
// clothes.

// The end of a day, which is the moment "as of that date" means. Not midnight: asked
// what a record said on Tuesday, the answer is what it said when Tuesday ended.
fun endOf(date: LocalDate): Long = epochMillisAt(date, 23, 59)

fun dateOf(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

// How far back the log actually reaches.
data class Horizon(val from: LocalDate?, val events: Int) {
    val hasLog: Boolean get() = from != null && events > 0

    // Can this date be replayed, or only counted?
    fun covers(date: LocalDate): Boolean = from != null && date >= from
}

fun horizon(): Horizon {
    val first = History.earliestEventAt()
    return Horizon(from = first?.let { dateOf(it) }, events = History.size())
}

// One field that moved.
data class FieldDiff(val field: String, val before: String, val after: String)

// One thing that happened, on the day being looked at. A view of a Mutation with its
// field values already made readable — the screen should not be parsing JSON.
data class Entry(
    val seq: Long,
    val at: Long,
    val key: String,
    val coll: String,
    val rec: String,
    val label: String,
    val kind: Change,
    val fields: List<FieldDiff>,
    val remote: Boolean,
    val reversible: Boolean,
)

// Field values are stored as raw JSON so nested shapes survive a round trip. For
// reading, a plain string should look like a plain string.
fun readable(raw: String): String {
    val t = raw.trim()
    val body = if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
        t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\n", " ")
    } else {
        t
    }
    return when {
        body.isBlank() || body == "null" -> "(empty)"
        body.length > 120 -> body.take(120) + "…"
        else -> body
    }
}

private fun diffsOf(m: Mutation): List<FieldDiff> = when (m.change) {
    Change.UPDATE ->
        m.before.keys.map { f -> FieldDiff(f, readable(m.before[f].orEmpty()), readable(m.after[f].orEmpty())) }
    else -> emptyList()
}

private fun toEntry(m: Mutation) = Entry(
    seq = m.seq,
    at = m.at,
    key = m.key,
    coll = m.coll,
    rec = m.rec,
    label = m.label.ifBlank { "(untitled)" },
    kind = m.change,
    fields = diffsOf(m),
    remote = m.remote,
    reversible = m.reversible,
)

// Everything the log recorded on one day, newest first. This is the half the old screen
// could not do at all: what changed, in which direction, and what was deleted.
fun changesOn(date: LocalDate): List<Entry> =
    History.all()
        .filter { dateOf(it.at) == date }
        .sortedByDescending { it.at }
        .map { toEntry(it) }

// ---- comparing two days -------------------------------------------------------------

// One record's difference between two dates, at field level.
data class RecordDiff(
    val key: String,
    val coll: String,
    val rec: String,
    val label: String,
    // ADDED between the dates, REMOVED between them, or CHANGED.
    val kind: Change,
    val fields: List<FieldDiff>,
)

// What changed in one module between two dates, read off the log rather than off the
// records. Collapses a burst of edits into one before-and-after: asked what changed
// between March and today, "the title went from A to D" is the answer, not three hops.
fun diffBetween(key: String, from: LocalDate, to: LocalDate): List<RecordDiff> {
    val start = endOf(from)
    val end = endOf(to)
    if (end <= start) return emptyList()

    val touched = History.all()
        .filter { it.key == key && it.at > start && it.at <= end }
        .sortedBy { it.at }
    if (touched.isEmpty()) return emptyList()

    val out = mutableListOf<RecordDiff>()
    touched.groupBy { it.coll to it.rec }.forEach { (id, events) ->
        val (coll, rec) = id
        val was = History.recordAt(key, coll, rec, start)
        val now = History.recordAt(key, coll, rec, end)
        val label = events.lastOrNull { it.label.isNotBlank() }?.label ?: rec

        when {
            was == null && now == null -> Unit // created and deleted between the two dates
            was == null -> out.add(RecordDiff(key, coll, rec, label, Change.CREATE, emptyList()))
            now == null -> out.add(RecordDiff(key, coll, rec, label, Change.DELETE, emptyList()))
            else -> {
                val fields = (was.keys + now.keys)
                    .filter { it != "id" }
                    .mapNotNull { f ->
                        val a = was[f]?.toString() ?: "null"
                        val b = now[f]?.toString() ?: "null"
                        if (a == b) null else FieldDiff(f, readable(a), readable(b))
                    }
                if (fields.isNotEmpty()) {
                    out.add(RecordDiff(key, coll, rec, label, Change.UPDATE, fields))
                }
            }
        }
    }
    return out.sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))
}

// Every module the log has anything for, so a compare view offers real choices.
fun modulesWithHistory(): List<String> = History.keysTouched()

// ---- putting one back ----------------------------------------------------------------

// Restore a record to how it read at the end of a day. Returns false when there is
// nothing to do, or when the date is outside what the log covers — before its first
// event there is no "how it read", only "it existed".
fun restoreRecordTo(entry: Entry, date: LocalDate): Boolean {
    if (!horizon().covers(date)) return false
    return History.restoreTo(entry.key, entry.coll, entry.rec, endOf(date))
}

// The first day the timeline can speak about honestly. The log's own start, since every
// record that has changed since has a first event in it.
fun replayStart(): LocalDate? = horizon().from

// A day in the future is not history.
fun clampToPast(date: LocalDate): LocalDate = if (date > today()) today() else date

package com.alekpeed.lifeos.calendar

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.push.subjectOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Standalone reminders — the half of the Notifications screen that was genuinely its
// own (§2 Group A).
//
// The screen is gone: its due / overdue / expiring feed was a second implementation of
// what Briefing already renders, and both now read the shared dated query (§12.1.1).
// What could not go with it is this: a persisted list of reminders attached to no
// record anywhere in the app, plus the only clock-aware code in the codebase — "in an
// hour", "this evening", "tomorrow morning", real alarms at real times, when every
// other module is date-granular.
//
// §2 called that a partial, accidental implementation of M-01 and said to promote it
// rather than delete it. M-01 landed, so this is where it lands: reminders are dated
// records like any other now, they appear on the Calendar and in the Briefing through
// the same query as everything else, and the quick-time chips live on the Calendar
// screen where a time of day belongs.
//
// THE STORAGE KEY IS UNCHANGED, deliberately. "Notifications" is what every install
// and every synced row already holds; renaming it would strand every reminder anyone
// has written, which is the exact hazard §2 flagged. The shape inside it is upgraded
// from tab-delimited lines to JSON, the same way Tasks was, and the old format still
// reads.
const val REMINDERS_KEY = "Notifications"

@Serializable
data class Reminder(
    val id: Long,
    val text: String,
    // When it fires. Null means it was posted immediately and kept as a note — those
    // have no place on a calendar and do not appear on one.
    val atEpochMillis: Long? = null,
    // A reminder that has been dealt with. The old screen had only a delete, which
    // meant the notification's own button had nothing non-destructive to do; marking
    // one done from the lock screen should not throw the text away.
    val done: Boolean = false,
) {
    fun dateTime(): Pair<LocalDate, LocalTime>? {
        val at = atEpochMillis ?: return null
        val ldt = Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.currentSystemDefault())
        return ldt.date to ldt.time
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadReminders(): List<Reminder> {
    val raw = Storage.read(REMINDERS_KEY)
    if (raw.isNullOrBlank()) return emptyList()
    if (raw.trimStart().startsWith("[")) {
        return runCatching { json.decodeFromString<List<Reminder>>(raw) }.getOrElse { emptyList() }
    }
    // The old format: one "text<TAB>millis" line per reminder, no ids. Ids are assigned
    // by position, which is stable for as long as the lines are — and they are, since
    // this runs once per device and the first save writes JSON.
    return raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        Reminder(
            id = i + 1L,
            text = parts.getOrElse(0) { line },
            atEpochMillis = parts.getOrNull(1)?.trim()?.toLongOrNull(),
        )
    }
}

fun saveReminders(items: List<Reminder>) {
    Storage.write(REMINDERS_KEY, json.encodeToString(items))
}

fun nextReminderId(existing: List<Reminder> = loadReminders()): Long =
    (existing.maxOfOrNull { it.id } ?: 0L) + 1L

// Distinct by construction from the task block at 800_000 and the capsule block at
// 900_000: two modules sharing an AlarmManager id would silently cancel each other.
private const val REMINDER_ALARM_BASE = 700_000

fun reminderAlarmId(id: Long): Int = REMINDER_ALARM_BASE + (id % 90_000).toInt()

fun reminderSubject(id: Long): String = subjectOf(REMINDERS_KEY, id)

fun scheduleReminderAlarm(r: Reminder, now: Long = Clock.System.now().toEpochMilliseconds()) {
    if (!Native.supportsNotifications) return
    val at = r.atEpochMillis ?: return
    if (r.done || at <= now) return
    runCatching {
        Native.scheduleReminder(
            id = reminderAlarmId(r.id),
            title = "Reminder",
            body = r.text,
            atEpochMillis = at,
            subject = reminderSubject(r.id),
        )
    }
}

// Re-arm at app open, like the capsule and task alarms: an alarm does not survive a
// reinstall, an OS upgrade or a new phone, and the record outlives it.
fun rescheduleReminderAlarms() {
    if (!Native.supportsNotifications) return
    runCatching { loadReminders().forEach { scheduleReminderAlarm(it) } }
}

fun completeReminder(id: Long) {
    val items = loadReminders()
    if (items.none { it.id == id }) return
    saveReminders(items.map { if (it.id == id) it.copy(done = true) else it })
    runCatching { Native.cancelReminder(reminderAlarmId(id)) }
}

// Push it a day out, keeping the time of day. A reminder with no time gets tomorrow
// morning, since "later" has to mean something specific to be schedulable.
fun snoozeReminder(id: Long) {
    val items = loadReminders()
    val target = items.firstOrNull { it.id == id } ?: return
    val at = target.atEpochMillis?.let { it + 24L * 60 * 60 * 1000 }
        ?: epochMillisAt(today().plusDays(1), 9, 0)
    val moved = target.copy(atEpochMillis = at, done = false)
    saveReminders(items.map { if (it.id == id) moved else it })
    runCatching { Native.cancelReminder(reminderAlarmId(id)) }
    scheduleReminderAlarm(moved)
}

fun deleteReminder(id: Long) {
    val items = loadReminders()
    if (items.none { it.id == id }) return
    saveReminders(items.filterNot { it.id == id })
    runCatching { Native.cancelReminder(reminderAlarmId(id)) }
}

// The ongoing "next up" ticker. It used to be held in a screen-local variable, so the
// app forgot what was pinned the moment you navigated away while the notification
// itself stayed up. A reserved key: which line this device is showing is a fact about
// the device, not a record, and it has no business on your other machines.
private const val PINNED_KEY = "__pinned_next_up"

fun pinnedNextUp(): String? = Storage.read(PINNED_KEY)?.ifBlank { null }

fun setPinned(text: String?) {
    Storage.write(PINNED_KEY, text.orEmpty())
    runCatching { Native.setPinnedNextUp(text) }
}

package com.alekpeed.lifeos.push

import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import com.alekpeed.lifeos.tasks.scheduleTask
import com.alekpeed.lifeos.tasks.taskReminderId
import com.alekpeed.lifeos.timecapsules.loadCapsules
import com.alekpeed.lifeos.timecapsules.markCapsuleRead
import com.alekpeed.lifeos.timecapsules.saveCapsules

// §7 D-5 phase 2 — what the notification's buttons actually do.
//
// The Done and Snooze buttons already existed on every reminder, and both did the same
// thing: dismiss the notification. The comment in NotificationActionReceiver said as
// much ("a real timed snooze via AlarmManager is a follow-up"), which made the stated
// reason for building phase 2 — resolving something without opening the app — the one
// part that was not true.
//
// A notification therefore carries a SUBJECT: which record it is about, as
// "<storage key>|<record id>". The action resolves that record and changes it. Empty
// means the notification is not about a record — a wake-word notice, a pinned line —
// and its buttons go on dismissing, which is the honest thing for them to do.
//
// This lives in commonMain rather than in the Android receiver so the same resolution
// serves an FCM action later, and so it can be tested without a device.

private const val SEP = "|"

fun subjectOf(storageKey: String, recordId: Long): String = "$storageKey$SEP$recordId"

data class Subject(val key: String, val id: Long)

fun parseSubject(raw: String): Subject? {
    if (raw.isBlank()) return null
    val key = raw.substringBefore(SEP).trim()
    val id = raw.substringAfter(SEP, "").trim().toLongOrNull() ?: return null
    return if (key.isEmpty()) null else Subject(key, id)
}

// What the buttons should say for a given subject. A capsule is not "done", it is read;
// and nothing that has no record to change should offer to change one.
data class ActionLabels(val done: String?, val snooze: String?)

fun labelsFor(raw: String): ActionLabels {
    val s = parseSubject(raw) ?: return ActionLabels(null, null)
    return when (s.key) {
        "Tasks" -> ActionLabels("Done", "Tomorrow")
        "Time Capsules" -> ActionLabels("Mark read", null)
        else -> ActionLabels(null, null)
    }
}

// Resolve the thing. Returns what happened, for a toast or a log — and false when the
// record has gone, which is normal: a notification can outlive what it was about.
fun applyDone(raw: String): Boolean {
    val s = parseSubject(raw) ?: return false
    return when (s.key) {
        "Tasks" -> {
            val tasks = loadTasks()
            if (tasks.none { it.id == s.id }) return false
            saveTasks(
                tasks.map {
                    if (it.id == s.id) it.copy(status = "done", completedDate = today().toString()) else it
                },
            )
            // Nothing left to nudge about. Left armed, the alarm would fire on a task
            // the notification already finished.
            runCatching { Native.cancelReminder(taskReminderId(s.id)) }
            true
        }
        "Time Capsules" -> {
            val data = loadCapsules()
            if (data.capsules.none { it.id == s.id }) return false
            saveCapsules(markCapsuleRead(data, s.id))
            true
        }
        // Bills are deliberately absent. Marking one paid writes a payment into the
        // ledger with an amount and a date; doing that from a lock screen, without
        // seeing the figure, is not a convenience.
        else -> false
    }
}

fun applySnooze(raw: String): Boolean {
    val s = parseSubject(raw) ?: return false
    return when (s.key) {
        "Tasks" -> {
            val tasks = loadTasks()
            if (tasks.none { it.id == s.id }) return false
            val moved = tasks.map {
                if (it.id == s.id) it.copy(snoozedUntil = today().plusDays(1).toString()) else it
            }
            saveTasks(moved)
            // Re-arm for the new day. Without this "Tomorrow" would mean "never again",
            // since the app-open pass may not run before tomorrow morning.
            runCatching { Native.cancelReminder(taskReminderId(s.id)) }
            moved.firstOrNull { it.id == s.id }?.let { runCatching { scheduleTask(it) } }
            true
        }
        else -> false
    }
}

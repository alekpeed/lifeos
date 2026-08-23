package com.alekpeed.lifeos.tasks

import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.push.subjectOf
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

// §7 D-5 phase 2 — a task with a due date says so on the day, and the notification's
// buttons finish it.
//
// Bills and time capsules already had alarms; tasks, the module most likely to hold
// something you meant to do today, had none. That also left the Done / Tomorrow
// buttons with nothing to act on: they exist for exactly this notification.
//
// Same shape as the capsule alarms (§5.4): re-armed at app open rather than written
// at every save, because alarms do not survive a reinstall or a new device, and
// re-arming is two reads and a handful of cheap AlarmManager calls.

private const val TASK_ALARM_BASE = 800_000

// Distinct by construction from the capsule block at 900_000.
fun taskReminderId(taskId: Long): Int = TASK_ALARM_BASE + (taskId % 90_000).toInt()

// A snooze past the due date moves the nudge; a snooze before it does not, since the
// task is still owed on the day it is owed.
fun taskAlarmDate(t: Task): LocalDate? {
    if (t.done) return null
    val due = t.dueDate() ?: return null
    val snooze = t.snoozeDate()
    return if (snooze != null && snooze > due) snooze else due
}

// Far enough out to cover anything you would want warning of, near enough that a list
// with years of dated tasks does not arm hundreds of alarms the OS will drop anyway.
private const val HORIZON_DAYS = 60

fun scheduleTask(t: Task, now: Long = Clock.System.now().toEpochMilliseconds()) {
    if (!Native.supportsNotifications) return
    val date = taskAlarmDate(t) ?: return
    if (date > today().plusDays(HORIZON_DAYS)) return
    val at = epochMillisAt(date, 9, 0)
    // A time already past would fire the instant it is set — and again at every app
    // open after that. An overdue task is the Briefing's job, not an alarm's.
    if (at <= now) return
    runCatching {
        Native.scheduleReminder(
            id = taskReminderId(t.id),
            title = "Due today: ${t.title}",
            body = t.notes.ifBlank { "Task due ${date}" },
            atEpochMillis = at,
            subject = subjectOf("Tasks", t.id),
        )
    }
}

fun rescheduleTaskAlarms() {
    if (!Native.supportsNotifications) return
    runCatching { loadTasks().forEach { scheduleTask(it) } }
}

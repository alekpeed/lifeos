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
// Alarms are re-armed at app open and at boot rather than only written at save,
// because they do not survive a reboot, an app update, a reinstall or a new device,
// while the record does. They are *also* written at save — see `taskAlarmPlan` — for
// the opposite failure: a task created and then left alone had no alarm at all until
// the next launch, which for something due tomorrow morning is too late.

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

// What one save changes about the alarms, decided without touching the platform.
//
// Separated from the doing because `Native.supportsNotifications` is false on desktop,
// which is the only target that runs tests: applying alarms cannot be tested, choosing
// them can. Everything that could get this wrong lives on this side of the line.
data class AlarmPlan(val cancel: List<Int>, val arm: List<Task>)

// Only what actually changed. A save rewrites the whole Tasks blob — every edit, every
// checkbox — so re-arming all of them on each one would put dozens of AlarmManager
// calls behind a keystroke.
//
// Cancels come first and are computed against ids, not tasks, because the three ways an
// alarm stops being owed all look different in the data: the task was finished, its due
// date was cleared, or it was deleted outright and is simply not in the new list.
fun taskAlarmPlan(before: List<Task>, after: List<Task>): AlarmPlan {
    val armedBefore = before.filter { taskAlarmDate(it) != null }.map { it.id }.toSet()
    val stillOwed = after.filter { taskAlarmDate(it) != null }
    val stillOwedIds = stillOwed.map { it.id }.toSet()

    val cancel = armedBefore.filterNot { it in stillOwedIds }.map { taskReminderId(it) }

    val prior = before.associateBy { it.id }
    val arm = stillOwed.filter { t ->
        val was = prior[t.id]
        // New, or something an alarm depends on moved. Comparing the whole record would
        // re-arm on a renamed tag; comparing nothing at all would miss a changed date.
        was == null || was.due != t.due || was.snoozedUntil != t.snoozedUntil || was.done != t.done
    }
    return AlarmPlan(cancel = cancel, arm = arm)
}

// Applied on every save, from inside `saveTasks` — the one choke point every writer
// goes through. Nine screens call `saveTasks` (Tasks, Today, Briefing, Ideas, the voice
// capture path, Smart Scan, automations, projects, the notification buttons); patching
// each would have been nine chances to forget the tenth.
fun applyTaskAlarms(before: List<Task>, after: List<Task>) {
    if (!Native.supportsNotifications) return
    runCatching {
        val plan = taskAlarmPlan(before, after)
        plan.cancel.forEach { Native.cancelReminder(it) }
        plan.arm.forEach { scheduleTask(it) }
    }
}

package com.alekpeed.lifeos.alarms

// Everything in the app that owns an alarm, in one list.
//
// Android clears every pending alarm on reboot, on an app update, and on a force-stop.
// The records survive all three; the alarms do not. Until this existed the only re-arm
// was at app open, which means a phone restarted at bedtime silently dropped the nudge
// for a task due the next morning — the app was wrong in the one way you would never
// notice, because a notification that does not arrive looks exactly like a day with
// nothing due.
//
// The reason this is a list in one file rather than four calls at the call site: there
// are now two callers (app open, and the boot receiver), and a third is likely. A
// module that gains an alarm has to be added here once; the alternative is remembering
// every place a sweep is run, which is how bills came to be re-armed nowhere at all.
//
// Each sweep already no-ops where there is no notification transport and already
// swallows its own failures. They are wrapped again individually anyway, so one module
// with an unreadable blob cannot take the other three down with it — at boot there is
// nobody watching a log, and a silent partial re-arm is the exact failure this fixes.
fun rearmAllAlarms() {
    runCatching { com.alekpeed.lifeos.timecapsules.rescheduleCapsuleAlarms() }
    runCatching { com.alekpeed.lifeos.tasks.rescheduleTaskAlarms() }
    runCatching { com.alekpeed.lifeos.calendar.rescheduleReminderAlarms() }
    runCatching { com.alekpeed.lifeos.finance.rescheduleBillAlarms() }
}

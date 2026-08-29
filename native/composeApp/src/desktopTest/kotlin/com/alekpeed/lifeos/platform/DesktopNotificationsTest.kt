package com.alekpeed.lifeos.platform

import kotlin.test.Test
import kotlin.test.assertFalse

// The invariant that keeps the test suite honest.
//
// Turning `supportsNotifications` on for desktop switched on code paths that had
// been dormant everywhere the tests run: task alarms reconcile inside `saveTasks`,
// the app-open sweep arms timers, bills and capsules schedule. If that happened
// during `desktopTest`, the suite would start spawning real scheduled futures as a
// side effect of saving a task — slow, leaky, and dependent on wall-clock time.
//
// It does not, because availability is computed from what actually works and a test
// JVM is headless. This test pins that: if someone later hardcodes the flag to true,
// this fails and says why rather than the suite mysteriously getting flaky.
class DesktopNotificationsTest {

    @Test
    fun `a headless jvm reports no notification support`() {
        assertFalse(
            DesktopNotifications.available,
            "Tests run headless; notifications must stay unavailable so alarm paths stay dormant.",
        )
        assertFalse(Native.supportsNotifications)
    }

    @Test
    fun `scheduling while unsupported is a no-op rather than a thrown timer`() {
        // Callers do not check the flag before every call — Reminders.kt does, but
        // this is the backstop, and it must not throw on a machine with no tray.
        Native.scheduleReminder(1, "t", "b", System.currentTimeMillis() + 60_000, "")
        Native.cancelReminder(1)
        Native.postReminder("t", "b", "")
        Native.setPinnedNextUp("something")
        Native.setPinnedNextUp(null)
    }
}

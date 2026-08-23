package com.alekpeed.lifeos.push

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import com.alekpeed.lifeos.tasks.taskAlarmDate
import com.alekpeed.lifeos.tasks.taskReminderId
import com.alekpeed.lifeos.timecapsules.TimeCapsule
import com.alekpeed.lifeos.timecapsules.TimeCapsulesData
import com.alekpeed.lifeos.timecapsules.capsuleReminderId
import com.alekpeed.lifeos.timecapsules.loadCapsules
import com.alekpeed.lifeos.timecapsules.saveCapsules
import com.alekpeed.lifeos.timecapsules.unreadCapsules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §7 D-5 phase 2. The buttons on a reminder notification used to both do the same
// thing — dismiss it — so what has to hold now is that pressing one actually changes
// the record, that a notification with nothing behind it says so rather than offering
// a Done that does nothing, and that a stale notification cannot corrupt anything.
class ActionsTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun capsule(id: Long, sealedUntil: String, readAt: String = "") =
        TimeCapsule(id = id, title = "Note $id", body = "b", sealedUntil = sealedUntil, readAt = readAt)

    @Test
    fun `a subject round-trips through its string form`() {
        val raw = subjectOf("Time Capsules", 42)
        assertEquals(Subject("Time Capsules", 42), parseSubject(raw))
    }

    @Test
    fun `anything that does not name a record parses to nothing`() {
        // A wake-word notice, a geofence arrival, an old notification from a build that
        // predates subjects — all of them arrive here, and none may resolve a record.
        for (bad in listOf("", "   ", "Tasks", "Tasks|", "Tasks|abc", "|7", "7")) {
            assertNull(parseSubject(bad), "expected no subject from \"$bad\"")
            assertFalse(applyDone(bad))
            assertFalse(applySnooze(bad))
        }
    }

    @Test
    fun `the buttons are labelled for what they will do`() {
        assertEquals(ActionLabels("Done", "Tomorrow"), labelsFor(subjectOf("Tasks", 1)))
        // A capsule is read, not done, and snoozing one means nothing.
        assertEquals(ActionLabels("Mark read", null), labelsFor(subjectOf("Time Capsules", 1)))
        // No record behind it, so no button that claims to change one.
        assertEquals(ActionLabels(null, null), labelsFor(""))
        assertEquals(ActionLabels(null, null), labelsFor(subjectOf("Finance", 1)))
    }

    @Test
    fun `done finishes the task and stamps the day`() {
        saveTasks(listOf(Task(id = 7, title = "Pay the plumber", due = today().toString())))
        assertTrue(applyDone(subjectOf("Tasks", 7)))
        val t = loadTasks().first { it.id == 7L }
        assertEquals("done", t.status)
        assertEquals(today().toString(), t.completedDate)
    }

    @Test
    fun `tomorrow moves the task without finishing it`() {
        saveTasks(listOf(Task(id = 7, title = "Pay the plumber", due = today().toString())))
        assertTrue(applySnooze(subjectOf("Tasks", 7)))
        val t = loadTasks().first { it.id == 7L }
        assertEquals(today().plusDays(1).toString(), t.snoozedUntil)
        assertEquals("not_started", t.status)
        // And the nudge moves with it — a snooze that silently meant "never again" is
        // the failure this button exists to avoid.
        assertEquals(today().plusDays(1), taskAlarmDate(t))
    }

    @Test
    fun `pressing a button only touches the task it names`() {
        saveTasks(
            listOf(
                Task(id = 1, title = "Mine", due = today().toString()),
                Task(id = 2, title = "Someone else's", due = today().toString()),
            ),
        )
        applyDone(subjectOf("Tasks", 1))
        assertEquals("done", loadTasks().first { it.id == 1L }.status)
        assertEquals("not_started", loadTasks().first { it.id == 2L }.status)
    }

    @Test
    fun `a notification can outlive its record, and says so by doing nothing`() {
        saveTasks(listOf(Task(id = 1, title = "Still here")))
        assertFalse(applyDone(subjectOf("Tasks", 999)))
        assertFalse(applySnooze(subjectOf("Tasks", 999)))
        // The store is untouched rather than gaining a phantom row.
        assertEquals(listOf(1L), loadTasks().map { it.id })
    }

    @Test
    fun `mark read stops the capsule surfacing`() {
        saveCapsules(TimeCapsulesData(capsules = listOf(capsule(3, today().plusDays(-1).toString()))))
        assertEquals(listOf(3L), unreadCapsules().map { it.id })
        assertTrue(applyDone(subjectOf("Time Capsules", 3)))
        assertTrue(unreadCapsules().isEmpty())
        assertEquals(today().toString(), loadCapsules().capsules.first().readAt)
    }

    @Test
    fun `a capsule cannot be snoozed`() {
        saveCapsules(TimeCapsulesData(capsules = listOf(capsule(3, today().plusDays(-1).toString()))))
        assertFalse(applySnooze(subjectOf("Time Capsules", 3)))
        assertEquals(listOf(3L), unreadCapsules().map { it.id })
    }

    @Test
    fun `a bill cannot be paid from a lock screen`() {
        // Deliberate: marking a bill paid writes a payment with an amount and a date,
        // and doing that without seeing the figure is not a convenience.
        assertFalse(applyDone(subjectOf("Finance", 1)))
        assertFalse(applySnooze(subjectOf("Finance", 1)))
    }

    @Test
    fun `an action is an ordinary edit and lands in the log`() {
        saveTasks(listOf(Task(id = 7, title = "Pay the plumber", due = today().toString())))
        val before = History.size()
        applyDone(subjectOf("Tasks", 7))
        assertTrue(History.size() > before, "a change made from a notification is still a change")
    }

    @Test
    fun `task and capsule alarms cannot land in the same slot`() {
        // Two modules arming the same AlarmManager id would silently cancel each other.
        val taskIds = (0L..2_000L).map { taskReminderId(it) }.toSet()
        val capsuleIds = (0L..2_000L).map { capsuleReminderId(it) }.toSet()
        assertTrue(taskIds.intersect(capsuleIds).isEmpty())
        assertNotEquals(taskReminderId(1), taskReminderId(2))
    }

    @Test
    fun `a done or undated task has no alarm date`() {
        assertNull(taskAlarmDate(Task(id = 1, title = "t", due = today().toString(), status = "done")))
        assertNull(taskAlarmDate(Task(id = 1, title = "t")))
    }

    @Test
    fun `a snooze past the due date moves the alarm, one before it does not`() {
        val due = today().plusDays(5)
        assertEquals(
            due.plusDays(2),
            taskAlarmDate(Task(id = 1, title = "t", due = due.toString(), snoozedUntil = due.plusDays(2).toString())),
        )
        assertEquals(
            due,
            taskAlarmDate(Task(id = 1, title = "t", due = due.toString(), snoozedUntil = today().toString())),
        )
    }
}

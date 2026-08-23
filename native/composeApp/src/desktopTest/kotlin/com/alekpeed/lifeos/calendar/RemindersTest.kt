package com.alekpeed.lifeos.calendar

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.push.applyDone
import com.alekpeed.lifeos.push.applySnooze
import com.alekpeed.lifeos.push.labelsFor
import com.alekpeed.lifeos.tasks.taskReminderId
import com.alekpeed.lifeos.timecapsules.capsuleReminderId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §2 Group A — retiring the Notifications screen without taking its data with it.
//
// The screen owned a persisted list of reminders attached to no record anywhere in the
// app, in a tab-delimited format, under a key that every install and every synced row
// already holds. §2 called deleting the screen without migrating that key destroying
// user data, so what has to hold here is: the old lines still read, the new shape
// round-trips, and a reminder is now an ordinary dated record that the shared query
// and the notification buttons both understand.
class RemindersTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun at(days: Int, hour: Int) = epochMillisAt(today().plusDays(days), hour, 0)

    @Test
    fun `the old tab-delimited lines still read`() {
        // Exactly what the retired screen wrote: text, a tab, epoch millis — and a
        // trailing tab with nothing after it for one that had no time.
        val when1 = at(1, 9)
        Storage.write(REMINDERS_KEY, "Bins go out\t$when1\nBuy stamps\t")

        val loaded = loadReminders()
        assertEquals(listOf("Bins go out", "Buy stamps"), loaded.map { it.text })
        assertEquals(when1, loaded[0].atEpochMillis)
        assertNull(loaded[1].atEpochMillis)
        // Ids by position, since the old format carried none.
        assertEquals(listOf(1L, 2L), loaded.map { it.id })
    }

    @Test
    fun `the first save converts the store to JSON and reads back the same`() {
        Storage.write(REMINDERS_KEY, "Bins go out\t${at(1, 9)}")
        val migrated = loadReminders()
        saveReminders(migrated)

        assertTrue(Storage.read(REMINDERS_KEY)!!.trimStart().startsWith("["))
        assertEquals(migrated, loadReminders())
    }

    @Test
    fun `an empty store is empty, not a phantom reminder`() {
        assertTrue(loadReminders().isEmpty())
        Storage.write(REMINDERS_KEY, "")
        assertTrue(loadReminders().isEmpty())
        Storage.write(REMINDERS_KEY, "not json and not lines either{")
        // A blob that parses as neither yields one line-shaped reminder rather than
        // throwing; what must not happen is losing the rest of the app to it.
        assertEquals(1, loadReminders().size)
    }

    @Test
    fun `a reminder with a moment lands on the calendar at that moment`() {
        saveReminders(listOf(Reminder(id = 1, text = "Call the vet", atEpochMillis = at(2, 15))))
        val item = datedItems(today(), today().plusDays(7)).single { it.key == "reminder-1" }
        assertEquals(today().plusDays(2), item.date)
        assertEquals(15, item.time?.hour)
        assertEquals(DatedKind.DUE, item.kind)
        assertEquals("calendar", item.moduleId)
        assertEquals(1L, item.recordId)
    }

    @Test
    fun `a reminder with no moment is a note, and stays off the calendar`() {
        // The old screen's "Now" button: it fired immediately and left a line behind.
        // Putting that on a day would mean inventing one.
        saveReminders(listOf(Reminder(id = 1, text = "Posted just now")))
        assertTrue(datedItems(today().plusDays(-30), today().plusDays(30)).none { it.key == "reminder-1" })
    }

    @Test
    fun `a done reminder shows on the month but not on the worklist`() {
        // The same split every other source honours: a calendar shows a finished thing
        // on the day it was due, a briefing does not.
        saveReminders(listOf(Reminder(id = 1, text = "Sorted", atEpochMillis = at(0, 23), done = true)))
        assertTrue(datedItems(today(), today()).any { it.key == "reminder-1" })
        assertTrue(datedItems(today(), today(), includeDone = false).none { it.key == "reminder-1" })
        assertTrue(datedWorklist().none { it.key == "reminder-1" })
    }

    @Test
    fun `an overdue reminder is owed, like anything else that is`() {
        saveReminders(listOf(Reminder(id = 1, text = "Was yesterday", atEpochMillis = at(-1, 9))))
        val item = datedWorklist().single { it.key == "reminder-1" }
        assertTrue(item.isOverdue())
    }

    @Test
    fun `done keeps the text and clears the nudge`() {
        saveReminders(listOf(Reminder(id = 1, text = "Bins go out", atEpochMillis = at(1, 9))))
        completeReminder(1)
        val r = loadReminders().single()
        assertTrue(r.done)
        // Marked, not deleted: the old screen had only a delete, which is not something
        // a lock-screen button should do.
        assertEquals("Bins go out", r.text)
    }

    @Test
    fun `snooze moves it a day and keeps the time of day`() {
        saveReminders(listOf(Reminder(id = 1, text = "Bins go out", atEpochMillis = at(0, 18))))
        snoozeReminder(1)
        assertEquals(at(1, 18), loadReminders().single().atEpochMillis)
    }

    @Test
    fun `snoozing a note gives it a moment rather than nothing`() {
        saveReminders(listOf(Reminder(id = 1, text = "No time on it")))
        snoozeReminder(1)
        assertEquals(at(1, 9), loadReminders().single().atEpochMillis)
    }

    @Test
    fun `snoozing a finished one brings it back`() {
        saveReminders(listOf(Reminder(id = 1, text = "Again", atEpochMillis = at(0, 9), done = true)))
        snoozeReminder(1)
        assertFalse(loadReminders().single().done)
    }

    @Test
    fun `deleting takes it out and leaves the rest`() {
        saveReminders(listOf(Reminder(1, "One"), Reminder(2, "Two")))
        deleteReminder(1)
        assertEquals(listOf(2L), loadReminders().map { it.id })
        // A reminder that has gone is not an error; a stale notification can ask twice.
        deleteReminder(1)
        assertEquals(listOf(2L), loadReminders().map { it.id })
    }

    @Test
    fun `ids do not repeat, even after the middle of the list is deleted`() {
        saveReminders(listOf(Reminder(1, "One"), Reminder(2, "Two"), Reminder(3, "Three")))
        deleteReminder(2)
        assertEquals(4L, nextReminderId())
    }

    @Test
    fun `the notification's buttons resolve the reminder`() {
        saveReminders(listOf(Reminder(id = 1, text = "Bins go out", atEpochMillis = at(0, 18))))
        val subject = reminderSubject(1)
        assertEquals("Done", labelsFor(subject).done)
        assertEquals("Tomorrow", labelsFor(subject).snooze)

        assertTrue(applySnooze(subject))
        assertEquals(at(1, 18), loadReminders().single().atEpochMillis)
        assertTrue(applyDone(subject))
        assertTrue(loadReminders().single().done)
    }

    @Test
    fun `a notification for a reminder that has gone changes nothing`() {
        saveReminders(listOf(Reminder(id = 1, text = "Still here")))
        assertFalse(applyDone(reminderSubject(99)))
        assertFalse(applySnooze(reminderSubject(99)))
        assertEquals(listOf(1L), loadReminders().map { it.id })
    }

    @Test
    fun `no two modules can claim the same alarm slot`() {
        val reminders = (0L..2_000L).map { reminderAlarmId(it) }.toSet()
        val tasks = (0L..2_000L).map { taskReminderId(it) }.toSet()
        val capsules = (0L..2_000L).map { capsuleReminderId(it) }.toSet()
        assertTrue(reminders.intersect(tasks).isEmpty())
        assertTrue(reminders.intersect(capsules).isEmpty())
        assertNotEquals(reminderAlarmId(1), reminderAlarmId(2))
    }

    @Test
    fun `what is pinned survives leaving the screen`() {
        // It used to be a screen-local variable: navigate away and the app forgot,
        // while the notification itself stayed up saying otherwise.
        assertNull(pinnedNextUp())
        setPinned("Bins go out")
        assertEquals("Bins go out", pinnedNextUp())
        setPinned(null)
        assertNull(pinnedNextUp())
    }

    @Test
    fun `reminders are recorded in the log like any other record`() {
        val before = History.size()
        saveReminders(listOf(Reminder(id = 1, text = "Bins go out")))
        assertTrue(History.size() > before)
        assertNotNull(History.historyOf(REMINDERS_KEY, "1").firstOrNull())
    }
}

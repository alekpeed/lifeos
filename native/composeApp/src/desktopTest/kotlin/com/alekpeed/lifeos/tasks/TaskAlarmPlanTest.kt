package com.alekpeed.lifeos.tasks

import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What a save does to the alarms.
//
// This is the half that can be tested: `Native.supportsNotifications` is false on
// desktop, which is the only target that runs tests, so arming an alarm is
// unobservable here and choosing one is not. Every way this could be wrong — a
// finished task that keeps nagging, a new task that never arms, a rename that
// re-arms forty alarms behind one keystroke — is a decision, so it lives in
// `taskAlarmPlan` and is checked here.
class TaskAlarmPlanTest {

    private val soon = today().plusDays(3).toString()
    private val later = today().plusDays(9).toString()

    private fun task(
        id: Long,
        due: String = soon,
        status: String = "not_started",
        snoozedUntil: String = "",
        title: String = "T$id",
    ) = Task(id = id, title = title, status = status, due = due, snoozedUntil = snoozedUntil)

    @Test
    fun `a new dated task arms`() {
        // The gap this closes: alarms used to be written only by the app-open sweep, so
        // a task added and then left alone had none until the next launch — which for
        // something due tomorrow morning is after the fact.
        val plan = taskAlarmPlan(emptyList(), listOf(task(1)))
        assertEquals(listOf(1L), plan.arm.map { it.id })
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `a task with no due date arms nothing`() {
        val plan = taskAlarmPlan(emptyList(), listOf(task(1, due = "")))
        assertTrue(plan.arm.isEmpty())
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `finishing a task cancels its alarm`() {
        // Otherwise the app nags you at nine in the morning about something you ticked
        // off last night, which is the fastest way to teach someone to swipe away
        // everything the app sends.
        val plan = taskAlarmPlan(listOf(task(1)), listOf(task(1, status = "done")))
        assertEquals(listOf(taskReminderId(1)), plan.cancel)
        assertTrue(plan.arm.isEmpty())
    }

    @Test
    fun `deleting a task cancels its alarm`() {
        // A deleted task is simply absent from the new list — it cannot be inspected for
        // a due date, which is why cancels are computed from ids rather than records.
        val plan = taskAlarmPlan(listOf(task(1), task(2)), listOf(task(2)))
        assertEquals(listOf(taskReminderId(1)), plan.cancel)
    }

    @Test
    fun `clearing the due date cancels the alarm`() {
        val plan = taskAlarmPlan(listOf(task(1)), listOf(task(1, due = "")))
        assertEquals(listOf(taskReminderId(1)), plan.cancel)
        assertTrue(plan.arm.isEmpty())
    }

    @Test
    fun `an untouched task is left alone`() {
        // The point of the whole plan. A save rewrites every task, so re-arming the lot
        // would put dozens of AlarmManager calls behind a single keystroke in a note.
        val before = listOf(task(1), task(2), task(3))
        val plan = taskAlarmPlan(before, before)
        assertTrue(plan.arm.isEmpty())
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `editing something an alarm does not depend on does not re-arm it`() {
        val plan = taskAlarmPlan(listOf(task(1)), listOf(task(1, title = "Renamed")))
        assertTrue(plan.arm.isEmpty())
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `moving the due date re-arms`() {
        val plan = taskAlarmPlan(listOf(task(1)), listOf(task(1, due = later)))
        assertEquals(listOf(1L), plan.arm.map { it.id })
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `snoozing past the due date re-arms`() {
        // taskAlarmDate takes the later of the two, so the nudge actually moves.
        val plan = taskAlarmPlan(listOf(task(1)), listOf(task(1, snoozedUntil = later)))
        assertEquals(listOf(1L), plan.arm.map { it.id })
    }

    @Test
    fun `reopening a finished task arms it again`() {
        val done = task(1, status = "done")
        val plan = taskAlarmPlan(listOf(done), listOf(task(1)))
        assertEquals(listOf(1L), plan.arm.map { it.id })
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `one save can cancel one and arm another`() {
        val before = listOf(task(1), task(2))
        val after = listOf(task(1, status = "done"), task(2), task(3))
        val plan = taskAlarmPlan(before, after)
        assertEquals(listOf(taskReminderId(1)), plan.cancel)
        assertEquals(listOf(3L), plan.arm.map { it.id })
    }

    @Test
    fun `a task that never had an alarm is not cancelled when it goes`() {
        // Cancelling an id that was never armed is harmless but noisy, and on a phone
        // with the id-collision space this uses it is not automatically harmless.
        val plan = taskAlarmPlan(listOf(task(1, due = "")), emptyList())
        assertTrue(plan.cancel.isEmpty())
    }
}

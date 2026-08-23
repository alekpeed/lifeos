package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.tasks.loadTasks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §2 Group A — one box that answers and creates.
//
// The risk in folding Command into Ask is the classifier: a question filed as a record
// leaves something you did not write, and a note answered instead of kept is lost.
// Nothing is written without a Confirm, so a misread costs a tap — but the reading
// itself is worth pinning down, and it is pure, so it can be.
class CaptureTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun asks(vararg lines: String) = lines.forEach {
        assertEquals(Intent.QUESTION, classify(it), "expected a question: \"$it\"")
    }

    private fun keeps(vararg lines: String) = lines.forEach {
        assertEquals(Intent.COMMAND, classify(it), "expected a capture: \"$it\"")
    }

    @Test
    fun `a question mark settles it`() {
        asks("rent?", "pay the rent?", "buy milk?")
    }

    @Test
    fun `interrogative openings are questions`() {
        asks(
            "when is the rent due",
            "what did I spend on food last month",
            "how many books did I finish",
            "who lent me the drill",
            "is the passport still valid",
            "show me everything about the Berlin trip",
        )
    }

    @Test
    fun `imperatives and fragments are captures`() {
        keeps(
            "remind me to call the landlord tomorrow",
            "buy milk",
            "pay the electric bill",
            "call mum",
            "milk",
            "dentist tuesday",
            "note down the wifi password",
        )
    }

    @Test
    fun `the same verb reads both ways, and the pronoun is what separates them`() {
        // "did my workout" is a check-in. "did I pay the rent" is a question — filing it
        // would leave a task that says you already did the thing you were asking about.
        keeps("did my workout", "did 30 minutes on the bike")
        asks("did I pay the rent", "did we book the flights")
    }

    @Test
    fun `an empty box asks rather than captures`() {
        assertEquals(Intent.QUESTION, classify(""))
        assertEquals(Intent.QUESTION, classify("   "))
    }

    @Test
    fun `a trailing date sets the due date and leaves the title`() {
        assertEquals("call the plumber" to today().plusDays(1).toString(), extractDue("call the plumber tomorrow"))
        assertEquals("file taxes" to today().plusDays(7).toString(), extractDue("file taxes next week"))
        assertEquals("bins out" to today().toString(), extractDue("bins out today"))
        assertEquals("renew the passport" to today().plusDays(10).toString(), extractDue("renew the passport in 10 days"))
        assertEquals("buy milk" to "", extractDue("buy milk"))
    }

    @Test
    fun `the keyless read guesses a type and cleans the line`() {
        // What the old screen's "→ Task" / "→ Idea" buttons were asking, answered — and
        // still changeable on the confirm card.
        assertEquals(CaptureCmd("task", "call the landlord", today().plusDays(1).toString()), guessCapture("remind me to call the landlord tomorrow"))
        assertEquals("task", guessCapture("buy milk").type)
        assertEquals("habit", guessCapture("did my workout").type)
        assertEquals("workout", guessCapture("did my workout").title)
        // No verb, no date: something you thought, not something you owe.
        assertEquals(CaptureCmd("idea", "a museum of failed products"), guessCapture("a museum of failed products"))
    }

    @Test
    fun `a bare date makes it a task even with no verb`() {
        val g = guessCapture("dentist tomorrow")
        assertEquals("task", g.type)
        assertEquals("dentist", g.title)
        assertEquals(today().plusDays(1).toString(), g.due)
    }

    @Test
    fun `confirming a task writes it with its date`() {
        val result = createRecord(guessCapture("remind me to call the landlord tomorrow"))
        assertEquals("tasks", result.moduleId)
        val task = loadTasks().single()
        assertEquals("call the landlord", task.title)
        assertEquals(today().plusDays(1).toString(), task.due)
    }

    @Test
    fun `confirming a check-in ticks the habit that already exists`() {
        saveHabits(listOf(com.alekpeed.lifeos.habits.Habit("workout", emptySet())))
        val result = createRecord(CaptureCmd("habit", "workout"))
        assertEquals("habits", result.moduleId)
        // One habit, not a second one called "workout" beside the first.
        assertEquals(1, loadHabits().size)
        assertTrue(today() in loadHabits().single().checkins)
    }

    @Test
    fun `a contact becomes a contact record`() {
        createRecord(CaptureCmd("contact", "Sam from the climbing gym"))
        assertEquals("Sam from the climbing gym", loadContacts().contacts.single().name)
    }

    @Test
    fun `an empty line writes nothing at all`() {
        assertEquals(CaptureResult("", ""), createRecord(CaptureCmd("task", "   ")))
        assertTrue(loadTasks().isEmpty())
    }

    @Test
    fun `an unknown type falls back to an idea rather than vanishing`() {
        val result = createRecord(CaptureCmd("something else", "keep this somewhere"))
        assertEquals("ideas", result.moduleId)
    }

    @Test
    fun `the model's reply is read into a proposal`() {
        val cmd = parseAction("TYPE: bill\nTITLE: Electric\nDUE: 2026-09-01\nAMOUNT: $84.20")
        assertEquals(CaptureCmd("bill", "Electric", "2026-09-01", 84.20), cmd)
    }

    @Test
    fun `a reply that is not a proposal is not one`() {
        // The screen falls back to the local read when this returns null; what it must
        // not do is invent a record out of prose.
        assertNull(parseAction("I'm not sure what you mean."))
        assertNull(parseAction("TYPE: task"))
        assertNull(parseAction("TYPE: nonsense\nTITLE: something"))
    }

    @Test
    fun `capture is an ordinary write and lands in the log`() {
        val before = History.size()
        createRecord(CaptureCmd("task", "buy milk"))
        assertTrue(History.size() > before)
    }
}

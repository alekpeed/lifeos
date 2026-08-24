package com.alekpeed.lifeos.people

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §11.1 — the Contacts expansion, built as one pass.
//
// Three of the four parts are storage and a screen. The fourth is derived, and derived
// things about people are where this gets uncomfortable: a cadence that nags about
// somebody you never said you wanted to keep up with, or an anniversary surfaced on the
// morning of it, are both worse than nothing.
class CadenceTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private val anchor = LocalDate(2026, 6, 15)
    private fun ago(days: Int) = anchor.minusDays(days).toString()

    private fun person(
        id: Long = 1,
        name: String = "Sam",
        birthday: String = "",
        cadence: Int? = null,
        dates: List<RecurringDate> = emptyList(),
        interactions: List<Interaction> = emptyList(),
        gifts: List<Gift> = emptyList(),
    ) = Contact(
        id = id, name = name, birthday = birthday, cadenceDays = cadence,
        dates = dates, interactions = interactions, gifts = gifts,
    )

    // ---- cadence --------------------------------------------------------------------

    @Test
    fun `days since last contact reads the latest interaction, not the newest row`() {
        // Rows are added at the front, but a back-dated one is still the latest date.
        val c = person(
            interactions = listOf(
                Interaction(2, ago(40), "call", "caught up"),
                Interaction(1, ago(9), "text", "quick one"),
            ),
        )
        assertEquals(9, daysSinceContact(c, anchor))
    }

    @Test
    fun `somebody you have never logged has no cadence at all`() {
        assertNull(daysSinceContact(person(), anchor))
        assertNull(lastInteraction(person()))
    }

    @Test
    fun `a contact with no target is never overdue, however long it has been`() {
        // The rule that keeps this from becoming a screen of guilt about acquaintances.
        val c = person(interactions = listOf(Interaction(1, ago(900), "call", "")))
        assertTrue(overdueContacts(listOf(c), anchor).isEmpty())
    }

    @Test
    fun `a target you set is what makes it overdue`() {
        val c = person(cadence = 30, interactions = listOf(Interaction(1, ago(31), "call", "")))
        val o = overdueContacts(listOf(c), anchor).single()
        assertEquals(31, o.days)
        assertEquals(30, o.target)
        // One day short is not overdue.
        assertTrue(overdueContacts(listOf(person(cadence = 30, interactions = listOf(Interaction(1, ago(29), "call", "")))), anchor).isEmpty())
    }

    @Test
    fun `a target with nothing logged is not overdue either`() {
        // Nothing to measure from. Guessing "you have never spoken" from an empty log
        // would be wrong for every contact imported from a phone.
        assertTrue(overdueContacts(listOf(person(cadence = 30)), anchor).isEmpty())
    }

    @Test
    fun `the most overdue person comes first`() {
        val a = person(1, "A", cadence = 30, interactions = listOf(Interaction(1, ago(40), "call", "")))
        val b = person(2, "B", cadence = 30, interactions = listOf(Interaction(1, ago(200), "call", "")))
        assertEquals(listOf("B", "A"), overdueContacts(listOf(a, b), anchor).map { it.name })
    }

    @Test
    fun `by-neglect skips people with nothing logged rather than ranking them worst`() {
        val logged = person(1, "Logged", interactions = listOf(Interaction(1, ago(5), "text", "")))
        assertEquals(listOf("Logged"), byNeglect(listOf(person(2, "Never"), logged), anchor).map { it.name })
    }

    @Test
    fun `logging an interaction puts it at the front and dated today`() {
        val c = logInteraction(person(), "call", "chatted")
        assertEquals(today().toString(), c.interactions.first().date)
        assertEquals("call", c.interactions.first().kind)
    }

    // ---- occasions ------------------------------------------------------------------

    @Test
    fun `a yearly date reads in both stored shapes`() {
        assertEquals(3 to 7, monthDayOf("1994-03-07"))
        assertEquals(3 to 7, monthDayOf("03-07"))
        assertNull(monthDayOf(""))
        assertNull(monthDayOf("March 7th"))
    }

    @Test
    fun `today is today, not eleven months away`() {
        // The bug this exists to prevent: projecting forward a year on the morning of
        // somebody's birthday.
        assertEquals(anchor, nextOccurrence(6 to 15, anchor))
        assertEquals(0, occasionsFor(person(birthday = "1990-06-15"), anchor).single().daysAway)
    }

    @Test
    fun `a date already past this year rolls to next year`() {
        assertEquals(LocalDate(2027, 1, 4), nextOccurrence(1 to 4, anchor))
    }

    @Test
    fun `the 29th of February lands on the next leap year rather than moving`() {
        // Silently sliding somebody's date to the 28th would be inventing a fact.
        assertEquals(LocalDate(2028, 2, 29), nextOccurrence(2 to 29, anchor))
    }

    @Test
    fun `an occasion is due only inside its own lead time`() {
        val soon = person(dates = listOf(RecurringDate(1, "anniversary", anchor.plusDays(10).toString(), leadDays = 14)))
        val later = person(dates = listOf(RecurringDate(1, "anniversary", anchor.plusDays(20).toString(), leadDays = 14)))
        val onTheDay = person(dates = listOf(RecurringDate(1, "anniversary", anchor.plusDays(3).toString(), leadDays = 0)))
        assertTrue(occasionsFor(soon, anchor).single().due)
        assertFalse(occasionsFor(later, anchor).single().due)
        assertFalse(occasionsFor(onTheDay, anchor).single().due)
    }

    @Test
    fun `a birthday and a recurring date both count as occasions`() {
        val c = person(
            birthday = "06-20",
            dates = listOf(RecurringDate(1, "work anniversary", "06-18")),
        )
        assertEquals(listOf("birthday", "work anniversary"), occasionsFor(c, anchor).map { it.label })
    }

    @Test
    fun `due occasions come soonest first`() {
        val c = person(
            birthday = "06-25",
            dates = listOf(RecurringDate(1, "anniversary", "06-17")),
        )
        assertEquals(listOf("anniversary", "birthday"), dueOccasions(listOf(c), anchor).map { it.label })
    }

    // ---- gifts ----------------------------------------------------------------------

    @Test
    fun `a given gift stays on the list and stops being open`() {
        // Reusable across years is the point: "the thing I nearly bought last year".
        val c = person(
            gifts = listOf(
                Gift(1, "A book", "birthday", status = GIFT_GIVEN, givenYear = "2025"),
                Gift(2, "Boots", "birthday", status = GIFT_IDEA),
            ),
        )
        assertEquals(listOf("Boots"), openGifts(c, "birthday").map { it.idea })
        assertEquals(2, c.gifts.size)
    }

    @Test
    fun `the summary says whether anything is actually ready`() {
        val ideas = person(gifts = listOf(Gift(1, "Boots", "birthday"), Gift(2, "A book", "birthday")))
        assertEquals("2 ideas", giftSummary(ideas, "birthday"))
        val ready = person(gifts = listOf(Gift(1, "Boots", "birthday", status = GIFT_WRAPPED)))
        assertEquals("1 ready", giftSummary(ready, "birthday"))
        assertEquals("no gift yet", giftSummary(person(), "birthday"))
    }

    @Test
    fun `gifts are matched to their occasion, not to every occasion`() {
        val c = person(gifts = listOf(Gift(1, "Boots", "birthday"), Gift(2, "Wine", "anniversary")))
        assertEquals(listOf("Wine"), openGifts(c, "anniversary").map { it.idea })
    }

    @Test
    fun `everything new round-trips through the store`() {
        val c = person(
            cadence = 30,
            dates = listOf(RecurringDate(1, "anniversary", "06-18", 7)),
            gifts = listOf(Gift(1, "Boots", "birthday")),
            interactions = listOf(Interaction(1, ago(2), "call", "long one")),
        )
        saveContacts(ContactsData(listOf(c)))
        assertEquals(c, loadContacts().contacts.single())
    }
}

package com.alekpeed.lifeos.finance

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// §11.4 — the unused-subscription flag.
//
// The money question this answers is "what am I paying for and not using", and the way
// to get it wrong is to answer it when you don't know: a subscription nobody has marked
// has no last-used date, and treating that as "unused since forever" would put every
// row you own in the Briefing on day one.
class UnusedSubsTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun ago(days: Int) = today().minusDays(days).toString()

    // Written as the JSON the module itself writes, so a field rename fails here.
    private fun subs(vararg rows: String) {
        Storage.write("Finance", """{"entries":[],"bills":[],"subscriptions":[${rows.joinToString(",")}]}""")
    }

    private fun sub(
        id: Int,
        name: String,
        amount: Double = 10.0,
        cycle: String = "monthly",
        active: Boolean = true,
        lastUsed: String = "",
    ) = """{"id":$id,"name":"$name","amount":$amount,"cycle":"$cycle","active":$active,""" +
        """"category":"Subscriptions","renewalDate":"","lastUsedDate":"$lastUsed","notes":""}"""

    @Test
    fun `an unmarked subscription is unknown, not unused`() {
        // The commonest state, and the one that would otherwise flood the Briefing.
        subs(sub(1, "Streaming"))
        assertTrue(financeUnusedSubscriptions().isEmpty())
    }

    @Test
    fun `sixty days is the line`() {
        subs(sub(1, "Gym", lastUsed = ago(59)))
        assertTrue(financeUnusedSubscriptions().isEmpty())
        subs(sub(1, "Gym", lastUsed = ago(60)))
        assertEquals(listOf("Gym"), financeUnusedSubscriptions().map { it.name })
    }

    @Test
    fun `a cancelled subscription is not nagged about`() {
        // You already dealt with it; it stays on the list for the history, not the guilt.
        subs(sub(1, "Gym", active = false, lastUsed = ago(300)))
        assertTrue(financeUnusedSubscriptions().isEmpty())
    }

    @Test
    fun `the worst-neglected one comes first`() {
        subs(
            sub(1, "Streaming", lastUsed = ago(70)),
            sub(2, "Gym", lastUsed = ago(300)),
            sub(3, "Storage", lastUsed = ago(120)),
        )
        assertEquals(listOf("Gym", "Storage", "Streaming"), financeUnusedSubscriptions().map { it.name })
    }

    @Test
    fun `the monthly cost arrives formatted, whatever the cycle`() {
        // What it costs you to keep not using it is the point of the row.
        subs(sub(1, "Yearly thing", amount = 120.0, cycle = "yearly", lastUsed = ago(90)))
        assertEquals("$10.00", financeUnusedSubscriptions().single().monthly)
    }

    @Test
    fun `marking it used resets the clock`() {
        subs(sub(1, "Gym", lastUsed = ago(300)))
        assertEquals(1, financeUnusedSubscriptions().size)
        financeMarkSubscriptionUsed(1)
        assertTrue(financeUnusedSubscriptions().isEmpty())
    }

    @Test
    fun `cancelling keeps the row and drops it from the list`() {
        subs(sub(1, "Gym", lastUsed = ago(300)))
        financeCancelSubscription(1)
        assertTrue(financeUnusedSubscriptions().isEmpty())
        // Still there — cancelled, not deleted.
        assertEquals(listOf(1L to "Gym"), financeSubStubs())
    }

    @Test
    fun `acting on a subscription that has gone changes nothing`() {
        subs(sub(1, "Gym", lastUsed = ago(300)))
        financeMarkSubscriptionUsed(99)
        financeCancelSubscription(99)
        assertEquals(1, financeUnusedSubscriptions().size)
    }

    @Test
    fun `a last-used date in the future does not read as negative neglect`() {
        subs(sub(1, "Gym", lastUsed = today().minusDays(-5).toString()))
        assertTrue(financeUnusedSubscriptions().isEmpty())
    }
}

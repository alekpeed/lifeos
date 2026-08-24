package com.alekpeed.lifeos.people

import com.alekpeed.lifeos.data.StaleRule
import com.alekpeed.lifeos.data.daysSinceDate
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.data.worstFirst
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

// The derived half of the Contacts expansion (§11.1): how long since you spoke, and
// what is coming up for whom.
//
// Cadence is the same neglect computation Entropy runs over modules, applied per
// person — so it goes through `data/Staleness.kt` (§12.1.2) rather than growing its own
// arithmetic. What is different, and has to be, is the threshold: a module untouched
// for a month is neglected, a friend you speak to twice a year is not. So the target is
// per contact, opt-in, and a contact without one is never overdue.

// Latest interaction date, or null if there has never been one.
fun lastInteraction(c: Contact): String? =
    c.interactions.mapNotNull { it.date.ifBlank { null } }.maxOrNull()

fun daysSinceContact(c: Contact, from: LocalDate = today()): Int? =
    daysSinceDate(lastInteraction(c), from)

// Everyone with an interaction logged, worst first. The list view's sort — it answers
// "who have I not spoken to" without accusing anybody of anything.
fun byNeglect(contacts: List<Contact>, from: LocalDate = today()): List<Contact> =
    worstFirst(contacts.filter { lastInteraction(it) != null }) { daysSinceContact(it, from) }

// Overdue against this person's own target. Null target, or nothing logged, means not
// overdue — the same rule as an unmarked subscription (§11.4): unknown is not stale.
data class OverdueContact(val id: Long, val name: String, val days: Int, val target: Int)

fun overdueContacts(contacts: List<Contact>, from: LocalDate = today()): List<OverdueContact> {
    val out = contacts.mapNotNull { c ->
        val target = c.cadenceDays?.takeIf { it > 0 } ?: return@mapNotNull null
        val days = daysSinceContact(c, from) ?: return@mapNotNull null
        if (days < target) return@mapNotNull null
        OverdueContact(c.id, c.name, days, target)
    }
    return worstFirst(out) { it.days }
}

// A per-contact rule built from that person's own target: overdue at the target, badly
// overdue at twice it. Used for colour, not for whether to nag.
fun cadenceRule(target: Int): StaleRule = StaleRule(staleAfter = target, neglectedAfter = target * 2)

fun logInteraction(c: Contact, kind: String, note: String, date: String = today().toString()): Contact {
    val id = (c.interactions.maxOfOrNull { it.id } ?: 0L) + 1
    return c.copy(interactions = listOf(Interaction(id, date, kind, note)) + c.interactions)
}

// ---- occasions ------------------------------------------------------------------

// A birthday or a recurring date, projected onto its next occurrence.
data class Occasion(
    val contactId: Long,
    val contactName: String,
    val label: String,
    val date: LocalDate,
    val daysAway: Int,
    val leadDays: Int,
) {
    // Whether it is inside its own lead time — which is what makes a date do something
    // rather than sit on a calendar you did not open.
    val due: Boolean get() = daysAway <= leadDays
}

// Both storage shapes a yearly date uses: "1994-03-07" and bare "03-07".
fun monthDayOf(raw: String): Pair<Int, Int>? {
    val t = raw.trim()
    val parts = t.split("-")
    return when (parts.size) {
        2 -> (parts[0].toIntOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
        3 -> (parts[1].toIntOrNull() ?: return null) to (parts[2].toIntOrNull() ?: return null)
        else -> null
    }
}

// The next time this month-day comes round, counting today as today rather than as a
// year away — a birthday is not eleven months off on the morning of it.
fun nextOccurrence(monthDay: Pair<Int, Int>, from: LocalDate = today()): LocalDate? {
    val thisYear = runCatching { LocalDate(from.year, monthDay.first, monthDay.second) }.getOrNull()
    if (thisYear != null && thisYear >= from) return thisYear
    // 29 February in a common year lands on the next leap year, which is the honest
    // answer rather than silently moving somebody's birthday to the 28th.
    for (y in (from.year + 1)..(from.year + 8)) {
        runCatching { LocalDate(y, monthDay.first, monthDay.second) }.getOrNull()?.let { return it }
    }
    return null
}

// Every dated occasion for a contact — the birthday plus each recurring date.
fun occasionsFor(c: Contact, from: LocalDate = today()): List<Occasion> {
    val out = mutableListOf<Occasion>()
    monthDayOf(c.birthday)?.let { md ->
        nextOccurrence(md, from)?.let { d ->
            out.add(Occasion(c.id, c.name, "birthday", d, from.daysUntil(d), BIRTHDAY_LEAD_DAYS))
        }
    }
    c.dates.forEach { r ->
        val md = monthDayOf(r.date) ?: return@forEach
        val d = nextOccurrence(md, from) ?: return@forEach
        out.add(Occasion(c.id, c.name, r.label.ifBlank { "occasion" }, d, from.daysUntil(d), r.leadDays.coerceAtLeast(0)))
    }
    return out
}

// A birthday has no lead field of its own — it predates §11.1 and lives in a plain
// string — so it gets the same two weeks a recurring date defaults to.
const val BIRTHDAY_LEAD_DAYS = 14

// What is inside its lead time right now, soonest first. The Briefing's source.
fun dueOccasions(contacts: List<Contact>, from: LocalDate = today()): List<Occasion> =
    contacts.flatMap { occasionsFor(it, from) }.filter { it.due }.sortedBy { it.daysAway }

// ---- gifts ------------------------------------------------------------------------

// Gift ideas still in play for an occasion: anything not yet given, plus anything given
// in a previous year, which is the reusable half of the list.
fun openGifts(c: Contact, occasion: String): List<Gift> =
    c.gifts.filter { it.occasion.equals(occasion, ignoreCase = true) && it.status != GIFT_GIVEN }

fun giftSummary(c: Contact, occasion: String): String {
    val open = openGifts(c, occasion)
    if (open.isEmpty()) return "no gift yet"
    val ready = open.count { it.status == GIFT_BOUGHT || it.status == GIFT_WRAPPED }
    return if (ready > 0) "$ready ready" else "${open.size} idea${if (open.size == 1) "" else "s"}"
}

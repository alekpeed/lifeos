package com.alekpeed.lifeos.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

// Days since last touched, and whether that is too long (§12.1.2).
//
// Entropy's module neglect, Rabbit Holes going cold, Contacts cadence (§11.1) and
// Finance's unused-subscription flag (§11.4) are one computation wearing four names:
// how long since this was last touched, is that past a threshold, and show me the
// worst first. Three of those were heading for three implementations in three domains,
// which is how "34 days" and "35 days" end up on screen for the same record depending
// which module you opened.
//
// So it lives here, in `data/`, and takes its threshold from the caller. The thresholds
// genuinely differ — three weeks is a cold thread, sixty days is an unused subscription,
// and a contact you speak to twice a year is not neglected — and pretending otherwise
// would be the opposite mistake.
//
// `skilltrees/Decay.kt` stays separate, deliberately. Its rung ladder advances on
// success and is anchored to the session that set it; that is a different model, not a
// special case of a flat threshold, and forcing them together would distort both.

enum class StaleLevel { UNKNOWN, FRESH, STALE, NEGLECTED }

// Two thresholds, in days. `staleAfter` is when it starts to want attention;
// `neglectedAfter` is when it has been left.
data class StaleRule(val staleAfter: Int, val neglectedAfter: Int)

data class Staleness(val days: Int?, val level: StaleLevel) {
    val known: Boolean get() = days != null
    val isStale: Boolean get() = level == StaleLevel.STALE || level == StaleLevel.NEGLECTED
    val isNeglected: Boolean get() = level == StaleLevel.NEGLECTED
}

// Null means "no idea", which is not the same as "touched today" and must never be
// rendered as it — a record with no timestamp yet is the commonest case on a fresh
// install, and calling it fresh would make the whole dashboard read green.
fun daysSinceDate(iso: String?, from: LocalDate = today()): Int? {
    val last = parseDateOrNull(iso?.trim().orEmpty()) ?: return null
    // A date in the future is a clock skew or a typo, not negative neglect.
    return last.daysUntil(from).coerceAtLeast(0)
}

fun daysSinceMillis(epochMillis: Long?, now: Long = Clock.System.now().toEpochMilliseconds()): Int? {
    if (epochMillis == null) return null
    return ((now - epochMillis) / 86_400_000L).toInt().coerceAtLeast(0)
}

fun levelFor(days: Int?, rule: StaleRule): StaleLevel = when {
    days == null -> StaleLevel.UNKNOWN
    days >= rule.neglectedAfter -> StaleLevel.NEGLECTED
    days >= rule.staleAfter -> StaleLevel.STALE
    else -> StaleLevel.FRESH
}

fun stalenessOf(days: Int?, rule: StaleRule): Staleness = Staleness(days, levelFor(days, rule))

fun stalenessSince(iso: String?, rule: StaleRule, from: LocalDate = today()): Staleness =
    stalenessOf(daysSinceDate(iso, from), rule)

// "today", "1 day ago", "34 days ago" — the same phrasing wherever a last-touched date
// is shown, so two screens cannot describe the same gap differently.
fun agoLabel(days: Int?, unknown: String = "no timestamp yet"): String = when {
    days == null -> unknown
    days == 0 -> "today"
    days == 1 -> "1 day ago"
    else -> "$days days ago"
}

// Worst first, with the unknowns last rather than first. A record nobody has stamped
// yet is not the most neglected thing you own; it is the thing we cannot say anything
// about, and sorting it to the top would bury the answers under the questions.
fun <T> worstFirst(items: List<T>, days: (T) -> Int?): List<T> =
    items.sortedWith(compareBy({ days(it) == null }, { -(days(it) ?: 0) }))

package com.alekpeed.lifeos.skilltrees

import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.LocalDate

// Interval ladder, salvaged from the removed Recall module (insight/Recall.kt).
//
// Recall used it for spaced repetition: answer correctly and the interval advances a
// rung, forget and it drops back to the start. That is the same shape skill atrophy
// takes — practice extends how long a skill holds, neglect collapses it — so the
// ladder is kept here rather than rewritten when Skill Trees gains practice tracking.
//
// Deliberately a fixed ladder rather than tracked ease factors: the rungs are legible,
// and nobody looks at an ease factor.
val PRACTICE_LADDER = listOf(1, 3, 7, 14, 30, 90)

// How long a skill holds before it should resurface, and when that falls due.
data class Freshness(val intervalDays: Int, val staleAfter: LocalDate)

fun Freshness.isStale(): Boolean = staleAfter <= today()

// A logged practice session advances the rung — the skill holds longer each time.
fun Freshness.practiced(): Freshness {
    val next = PRACTICE_LADDER.firstOrNull { it > intervalDays } ?: PRACTICE_LADDER.last()
    return Freshness(next, today().plusDays(next))
}

// Gone cold: back to the bottom rung. Recovering a lapsed skill starts over.
fun Freshness.lapsed(): Freshness =
    Freshness(PRACTICE_LADDER.first(), today().plusDays(PRACTICE_LADDER.first()))

fun freshnessStart(): Freshness =
    Freshness(PRACTICE_LADDER.first(), today().plusDays(PRACTICE_LADDER.first()))

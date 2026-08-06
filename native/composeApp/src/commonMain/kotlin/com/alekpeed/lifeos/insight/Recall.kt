package com.alekpeed.lifeos.insight

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.LocalDate

// Interval ladder in days: Know It advances a rung, Forgot drops back to the start.
// A simple, honest stand-in for full SM-2 — good enough spaced repetition without
// tracking ease factors most people never look at.
private val LADDER = listOf(1, 3, 7, 14, 30, 90)

data class Fact(val text: String, val intervalDays: Int, val nextReview: LocalDate)

fun Fact.isDue(): Boolean = nextReview <= today()

fun Fact.knowIt(): Fact {
    val next = LADDER.firstOrNull { it > intervalDays } ?: LADDER.last()
    return copy(intervalDays = next, nextReview = today().plusDays(next))
}

fun Fact.forgot(): Fact = copy(intervalDays = LADDER.first(), nextReview = today().plusDays(LADDER.first()))

// text \t intervalDays \t nextReview(ISO)
private fun Fact.toLine(): String = "$text\t$intervalDays\t$nextReview"

private fun parseLine(line: String): Fact {
    val p = line.split("\t")
    val text = p.getOrElse(0) { line }
    val interval = p.getOrElse(1) { "1" }.toIntOrNull() ?: 1
    val next = p.getOrNull(2)?.let { parseDateOrNull(it) } ?: today()
    return Fact(text, interval, next)
}

fun loadFacts(): List<Fact> =
    Storage.read("Recall")?.lines()?.filter { it.isNotBlank() }?.map { parseLine(it) } ?: emptyList()

fun saveFacts(facts: List<Fact>) {
    Storage.write("Recall", facts.joinToString("\n") { it.toLine() })
}

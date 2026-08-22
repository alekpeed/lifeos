package com.alekpeed.lifeos.calendar

import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.parseTimeOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.timeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.finance.financeBills
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.timecapsules.loadCapsules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// The dated-items query (§12.1.1).
//
// Today, Briefing, Daily Paper and Notifications each grew their own walk over Tasks,
// Finance, Education and Documents, with subtly different rules — one honours snooze,
// one gives bills their own horizon, one bakes in a seven-day window. Four answers to
// the same question, drifting apart every time a module is added.
//
// This is the one answer: everything dated inside a range, from every module that has
// dates, in one shape. A calendar is an arbitrary range; Today is today's range; a
// briefing is overdue-and-due-soon. They become filters over this rather than four
// separate implementations, and a new module gets surfaced everywhere by being added
// once, here.

enum class DatedKind {
    DUE,        // owed by a moment: tasks, bills, assignments
    EXPIRY,     // stops being valid: documents
    EVENT,      // happened or will happen: milestones
    RECURRING,  // returns every year: birthdays
    UNSEAL,     // becomes readable: time capsules
}

data class DatedItem(
    val key: String,
    val icon: String,
    val title: String,
    val date: LocalDate,
    // Set only when the record carries a time of day (M-01a). Null means "that day",
    // not midnight — the distinction matters for both sorting and display.
    val time: LocalTime?,
    val moduleId: String,
    val kind: DatedKind,
    val note: String = "",
    val done: Boolean = false,
) {
    // "3:00 PM · autopay", "autopay", or "".
    fun meta(): String = listOfNotNull(time?.let { timeLabel(it) }, note.ifBlank { null }).joinToString(" · ")

    fun isOverdue(from: LocalDate = today()): Boolean =
        !done && kind in setOf(DatedKind.DUE, DatedKind.EXPIRY) && date < from
}

// Everything dated in [from, to] inclusive.
//
// `includeDone` exists because the two audiences differ: a calendar shows a finished
// task on the day it was due, a briefing does not.
fun datedItems(from: LocalDate, to: LocalDate, includeDone: Boolean = true): List<DatedItem> {
    val out = mutableListOf<DatedItem>()
    fun inRange(d: LocalDate) = d >= from && d <= to

    // Tasks — the only source carrying a time of day today.
    loadTasks().forEach { t ->
        if (!includeDone && t.done) return@forEach
        val d = parseDateOrNull(t.due) ?: return@forEach
        if (!inRange(d)) return@forEach
        out.add(
            DatedItem(
                key = "task-${t.id}", icon = "✅", title = t.title, date = d,
                time = parseTimeOrNull(t.due), moduleId = "tasks", kind = DatedKind.DUE,
                done = t.done,
            ),
        )
    }

    financeBills().forEach { b ->
        if (!includeDone && b.settled) return@forEach
        val d = parseDateOrNull(b.dueDate) ?: return@forEach
        if (!inRange(d)) return@forEach
        out.add(
            DatedItem(
                key = "bill-${b.name}", icon = "💵", title = b.name, date = d,
                time = parseTimeOrNull(b.dueDate), moduleId = "finance", kind = DatedKind.DUE,
                note = if (b.autopay) "autopay" else "", done = b.settled,
            ),
        )
    }

    loadEducation().assignments.forEach { a ->
        if (!includeDone && a.done) return@forEach
        val d = parseDateOrNull(a.dueDate) ?: return@forEach
        if (!inRange(d)) return@forEach
        out.add(
            DatedItem(
                key = "assign-${a.id}", icon = "🎓", title = a.title, date = d,
                time = parseTimeOrNull(a.dueDate), moduleId = "education", kind = DatedKind.DUE,
                done = a.done,
            ),
        )
    }

    loadDocuments().documents.forEach { doc ->
        val d = parseDateOrNull(doc.expiryDate) ?: return@forEach
        if (!inRange(d)) return@forEach
        out.add(
            DatedItem(
                key = "doc-${doc.id}", icon = "📄", title = doc.title, date = d,
                time = null, moduleId = "documents", kind = DatedKind.EXPIRY, note = "expires",
            ),
        )
    }

    // Time capsules. Surfacing an unseal date is the module's one job and was never
    // wired anywhere (§5.4); a calendar is the honest place for it to show up.
    loadCapsules().capsules.forEach { c ->
        val d = parseDateOrNull(c.sealedUntil) ?: return@forEach
        if (!inRange(d)) return@forEach
        out.add(
            DatedItem(
                key = "capsule-${c.id}", icon = "⏳", title = c.title, date = d,
                time = null, moduleId = "time-capsules", kind = DatedKind.UNSEAL, note = "opens",
            ),
        )
    }

    loadMilestones().milestones.forEach { m ->
        val d = parseDateOrNull(m.date) ?: return@forEach
        if (!inRange(d)) return@forEach
        out.add(
            DatedItem(
                key = "milestone-${m.id}", icon = "🏆", title = m.title, date = d,
                time = null, moduleId = "milestones", kind = DatedKind.EVENT,
            ),
        )
    }

    // Birthdays recur, so they are projected into the range rather than matched against
    // the year they were stored with. They are also the one field stored two ways —
    // "1994-03-07" or bare "03-07" — so both are read.
    loadContacts().contacts.forEach { c ->
        val md = birthdayMonthDay(c.birthday) ?: return@forEach
        yearsSpanned(from, to).forEach { year ->
            val d = runCatching { LocalDate(year, md.first, md.second) }.getOrNull() ?: return@forEach
            if (!inRange(d)) return@forEach
            out.add(
                DatedItem(
                    key = "bday-${c.id}-$year", icon = "🎂", title = c.name, date = d,
                    time = null, moduleId = "contacts", kind = DatedKind.RECURRING, note = "birthday",
                ),
            )
        }
    }

    // Undated first within a day, then by clock, then alphabetical so the order is
    // stable between reads rather than following storage order.
    return out.sortedWith(
        compareBy({ it.date }, { it.time?.toSecondOfDay() ?: -1 }, { it.title.lowercase() }),
    )
}

// "1994-03-07" or "03-07" -> (month, day).
private fun birthdayMonthDay(raw: String): Pair<Int, Int>? {
    val s = raw.trim()
    if (s.isBlank()) return null
    parseDateOrNull(s)?.let { return it.monthNumber to it.dayOfMonth }
    val parts = s.split("-")
    if (parts.size != 2) return null
    val m = parts[0].toIntOrNull() ?: return null
    val d = parts[1].toIntOrNull() ?: return null
    return if (m in 1..12 && d in 1..31) m to d else null
}

private fun yearsSpanned(from: LocalDate, to: LocalDate): List<Int> = (from.year..to.year).toList()

// ---- The filters the other surfaces become (§12.1.1) --------------------------------

// Today's range.
fun datedToday(): List<DatedItem> = today().let { datedItems(it, it, includeDone = false) }

// Overdue and due-soon, which is what a briefing is. Anything already past shows up
// however far back it goes, so an overdue item can't quietly fall out of the window.
fun datedDueSoon(days: Int = 7, from: LocalDate = today()): List<DatedItem> =
    datedItems(from.plusDays(-3650), from.plusDays(days), includeDone = false)
        .filter { it.kind != DatedKind.EVENT }

package com.alekpeed.lifeos.archive

import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.timecapsules.loadCapsules

// The shape Archive's two single-moment records share (§12.1.4).
//
// Milestones and Time Capsules are the only single-moment records left in Archive now
// that Ghost Days and Museum are gone, and both are the same four things: a date, a
// title, a note, and optionally a photo. They were reaching the Calendar through two
// hand-written blocks that differed only in which field held the date and which held
// the note — which is two places to remember when a third moment type turns up.
//
// **This is not a module merge, and the interface is why it does not become one.** One
// is retrospective and visible immediately; the other is future-facing and hidden until
// it unseals. What they share is a shape, not semantics — so the shape is an interface
// the two data classes implement, with no change to how either is stored. A moment
// declares its own kind, and everything that differs hangs off that.

enum class MomentKind(val icon: String, val moduleId: String, val calendarNote: String) {
    // Something that happened. Visible the moment it is written.
    HAPPENED("🏆", "milestones", ""),

    // Something that becomes readable on a day. Hidden until then (§5.4).
    OPENS("⏳", "time-capsules", "opens"),
}

// Implemented by the record itself rather than copied into a wrapper, so there is no
// second version of a milestone to get out of step with the first. Every member is
// either an existing field or a getter over one — nothing here is serialized, so no
// stored blob changes shape.
interface Moment {
    val id: Long
    val title: String

    // The day this moment belongs to. For a milestone that is when it happened; for a
    // capsule it is when it opens, which is the day it is about.
    val date: String
    val note: String
    val photoBlob: String
    val kind: MomentKind
}

// Both, in one list. The Calendar's single source for Archive moments, and what a third
// moment type would join by implementing the interface rather than by another block.
fun archiveMoments(): List<Moment> =
    loadMilestones().milestones + loadCapsules().capsules

fun momentsOn(date: String): List<Moment> =
    archiveMoments().filter { it.date == date && date.isNotBlank() }

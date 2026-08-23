package com.alekpeed.lifeos.timecapsules

import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native

// §5.4 — making a capsule actually surface.
//
// The module's one job is to bring a note back on a date years away, and until now
// nothing did that. It sat in a list you would have to remember to open, which is the
// one thing a person writing to their future self is guaranteed not to do.
//
// Two mechanisms, deliberately redundant, because they fail differently:
//
//   1. A scheduled alarm at seal time. Fires with the app closed, costs nothing — the
//      alarm infrastructure already exists and Finance already uses it for bills.
//   2. A Briefing row on and after the unseal date. The durable fallback: alarms do not
//      survive a reinstall, an OS upgrade or a new phone, and a capsule sealed for five
//      years will very likely outlive its alarm. A Briefing row is computed from the
//      record itself and cannot be lost.
//
// Both need to know whether you have actually read the thing, or they nag forever — which
// is why `readAt` had to exist before either could be built.

// Distinct from Finance's bill ids by construction: a large offset rather than a hash, so
// two modules cannot collide on the same alarm slot.
private const val CAPSULE_ALARM_BASE = 900_000

fun capsuleReminderId(capsuleId: Long): Int = CAPSULE_ALARM_BASE + (capsuleId % 90_000).toInt()

// A capsule that has opened and has not been read. What both mechanisms surface.
fun isUnread(c: TimeCapsule): Boolean = !isSealed(c) && c.readAt.isBlank()

fun unreadCapsules(data: TimeCapsulesData = loadCapsules()): List<TimeCapsule> =
    data.capsules.filter { isUnread(it) }
        .sortedBy { it.sealedUntil.ifBlank { it.createdAt } }

fun unreadCapsuleCount(): Int = runCatching { unreadCapsules().size }.getOrDefault(0)

// Stamped when the body is actually revealed, not when the row is drawn — a capsule you
// scrolled past is not a capsule you read.
fun markCapsuleRead(data: TimeCapsulesData, id: Long): TimeCapsulesData =
    data.copy(
        capsules = data.capsules.map {
            if (it.id == id && it.readAt.isBlank()) it.copy(readAt = today().toString()) else it
        },
    )

// Mechanism 1. Nine in the morning on the unseal day: a capsule is not urgent, and being
// woken at midnight by a note from your past self is not the experience.
fun scheduleCapsule(c: TimeCapsule) {
    if (!Native.supportsNotifications) return
    val d = parseDateOrNull(c.sealedUntil) ?: return
    if (d < today()) return
    runCatching {
        Native.scheduleReminder(
            id = capsuleReminderId(c.id),
            title = "A time capsule has opened",
            body = c.title.ifBlank { "Something you sealed for today" },
            atEpochMillis = epochMillisAt(d, 9, 0),
            // The notification's one button marks it read, so it stops surfacing (§7 D-5).
            subject = com.alekpeed.lifeos.push.subjectOf("Time Capsules", c.id),
        )
    }
}

// Re-arm every still-sealed capsule at app open. Mechanism 1 is the one that gets lost,
// and this is the cheapest way to get it back after a reinstall or a new device — two
// reads and one alarm per sealed capsule, of which there are never many.
fun rescheduleCapsuleAlarms() {
    if (!Native.supportsNotifications) return
    runCatching {
        loadCapsules().capsules.filter { isSealed(it) }.forEach { scheduleCapsule(it) }
    }
}

package com.alekpeed.lifeos.settings

import com.alekpeed.lifeos.Storage

// How far ahead "due soon" reaches, in days. Every screen that flags an upcoming bill
// reads this instead of hardcoding a week, so changing it in Settings actually moves
// what Notifications, the Briefing and the Daily Paper call urgent.
//
// The document-expiry counterpart lives with Documents (docExpiryDays) because the
// Documents screen owns its own control for it; Settings edits the same value.
private const val DEFAULT_BILL_DUE_SOON = 7

fun billDueSoonDays(): Int =
    Storage.read("BillDueSoonDays")?.trim()?.toIntOrNull()?.coerceIn(1, 90) ?: DEFAULT_BILL_DUE_SOON

fun setBillDueSoonDays(days: Int) =
    Storage.write("BillDueSoonDays", days.coerceIn(1, 90).toString())

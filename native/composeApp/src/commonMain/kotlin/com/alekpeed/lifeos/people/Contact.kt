package com.alekpeed.lifeos.people

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Contacts — a real address book (ported from the web app's Contacts): each
// person has phones, emails, company/title, relationship, birthday, tags, and
// notes. The single source of truth for people. Persists as one JSON blob under
// "Contacts"; old plain-line stubs (incl. "Name — detail" from phone import)
// migrate so existing entries survive. Each may carry an optional attached photo
// (blob-store id).

@Serializable
data class Contact(
    val id: Long,
    val name: String,
    // Each entry is "value" or "label: value" (e.g. "mobile: 555-1234") — labeled
    // without a schema change, so older saved data reads back unchanged.
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val company: String = "",
    val title: String = "",
    val relationship: String = "",
    val address: String = "",
    val birthday: String = "",           // YYYY-MM-DD or MM-DD
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val photoBlob: String = "",          // blob-store id of an attached photo, if any
    // §11.1, built as one pass because all three extend this same record.
    val dates: List<RecurringDate> = emptyList(),
    val gifts: List<Gift> = emptyList(),
    val interactions: List<Interaction> = emptyList(),
    // How often you mean to be in touch, in days. Null means no target, and a contact
    // with no target is never overdue — you do not owe your dentist a monthly call, and
    // one global number for every person would be wrong for nearly all of them.
    val cadenceDays: Int? = null,
)

// A date that comes round every year and is not a birthday: an anniversary, a work
// anniversary, the day you adopted the dog. Stored the same two ways a birthday is —
// "1994-03-07" or bare "03-07" — so the year is optional where nobody remembers it.
@Serializable
data class RecurringDate(
    val id: Long,
    val label: String,
    val date: String,
    // How far ahead it should start surfacing. This is what turns a date into
    // something that does something: a wedding anniversary you learn about on the day
    // is a date you have already missed.
    val leadDays: Int = 14,
)

// A gift idea for an occasion. The idea list is reusable across years, which is why
// `givenYear` exists rather than deleting a gift once it has been given: "the thing I
// nearly bought last year" is the most useful entry on the list.
@Serializable
data class Gift(
    val id: Long,
    val idea: String,
    val occasion: String = "",
    val budget: Double? = null,
    val status: String = GIFT_IDEA,
    val givenYear: String = "",
    val notes: String = "",
)

const val GIFT_IDEA = "idea"
const val GIFT_BOUGHT = "bought"
const val GIFT_WRAPPED = "wrapped"
const val GIFT_GIVEN = "given"
val GIFT_STATUSES = listOf(GIFT_IDEA, GIFT_BOUGHT, GIFT_WRAPPED, GIFT_GIVEN)

// A dated note about a call, a text or a meetup. Deliberately not Milestones (that is
// for what mattered) and not journaling (that is about you, not about them).
@Serializable
data class Interaction(
    val id: Long,
    val date: String,
    val kind: String = "",
    val note: String = "",
)

val INTERACTION_KINDS = listOf("call", "text", "meetup", "email", "letter")

// Split "label: value" into label + value ("" label when bare).
fun splitLabeled(entry: String): Pair<String, String> {
    val i = entry.indexOf(": ")
    return if (i > 0) entry.substring(0, i) to entry.substring(i + 2) else "" to entry
}

fun joinLabeled(label: String, value: String): String =
    if (label.isBlank()) value.trim() else "${label.trim()}: ${value.trim()}"

@Serializable
data class ContactsData(val contacts: List<Contact> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadContacts(): ContactsData {
    val raw = Storage.read("Contacts")
    if (raw.isNullOrBlank()) return ContactsData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<ContactsData>(raw) }.getOrElse { ContactsData() }
    }
    // Migrate old plain lines: "Name — detail" (detail = phone if it has digits).
    val contacts = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split(" — ", limit = 2)
        val name = parts[0].trim()
        val detail = parts.getOrElse(1) { "" }.trim()
        when {
            detail.isBlank() -> Contact(i + 1L, name)
            detail.any { it.isDigit() } -> Contact(i + 1L, name, phones = listOf(detail))
            else -> Contact(i + 1L, name, notes = detail)
        }
    }
    return ContactsData(contacts)
}

fun saveContacts(data: ContactsData) {
    Storage.write("Contacts", json.encodeToString(data))
}

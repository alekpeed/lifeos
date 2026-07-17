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
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val company: String = "",
    val title: String = "",
    val relationship: String = "",
    val birthday: String = "",           // YYYY-MM-DD or MM-DD
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val photoBlob: String = "",          // blob-store id of an attached photo, if any
)

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

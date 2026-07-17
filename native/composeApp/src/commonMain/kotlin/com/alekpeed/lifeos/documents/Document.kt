package com.alekpeed.lifeos.documents

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Documents — ported from the web app's Documents view: a registry of important
// papers (lease, insurance, warranty…) with category, issuer, policy/account
// number, an expiry date that flags when it's expiring soon or expired, notes,
// and a linked contact. Persists as one JSON blob under "Documents"; old note
// stubs migrate. (Photo attachments + AI camera-scan wait on the attachment /
// vision layers.)

@Serializable
data class Document(
    val id: Long,
    val title: String,
    val category: String = "",
    val issuer: String = "",
    val policyNumber: String = "",
    val expiryDate: String = "",     // ISO date or ""
    val transcription: String = "",  // verbatim text (from a scan), user-editable
    val summary: String = "",        // short plain-language summary (from a scan)
    val notes: String = "",          // your own free notes
    val linkedContact: String = "",
)

@Serializable
data class DocumentsData(val documents: List<Document> = emptyList())

const val EXPIRY_SOON_DAYS = 30

enum class ExpiryState { NONE, OK, SOON, EXPIRED }

fun expiryState(d: Document): ExpiryState {
    val date = parseDateOrNull(d.expiryDate) ?: return ExpiryState.NONE
    val now = today()
    return when {
        date < now -> ExpiryState.EXPIRED
        date <= now.plusDays(EXPIRY_SOON_DAYS) -> ExpiryState.SOON
        else -> ExpiryState.OK
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadDocuments(): DocumentsData {
    val raw = Storage.read("Documents")
    if (raw.isNullOrBlank()) return DocumentsData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<DocumentsData>(raw) }.getOrElse { DocumentsData() }
    }
    val docs = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        Document(id = i + 1L, title = parts[0].trim(), notes = parts.getOrElse(1) { "" })
    }
    return DocumentsData(docs)
}

fun saveDocuments(data: DocumentsData) {
    Storage.write("Documents", json.encodeToString(data))
}

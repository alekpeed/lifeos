package com.alekpeed.lifeos.system

import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.books.Book
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.books.saveBooks
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.Document
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.documents.saveDocuments
import com.alekpeed.lifeos.ideas.Idea
import com.alekpeed.lifeos.ideas.loadIdeas
import com.alekpeed.lifeos.ideas.saveIdeas
import com.alekpeed.lifeos.links.Link
import com.alekpeed.lifeos.links.loadLinks
import com.alekpeed.lifeos.links.parseYouTubeId
import com.alekpeed.lifeos.links.saveLinks
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpGetImageBase64
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The universal scanner: point the camera at anything, and Life OS works out what it
// is and files it. One camera pass reads any code (QR, EAN-13/UPC, …); if there's no
// code it photographs the thing instead and asks the vision model what it's looking
// at. Either way you end up with a real record in the right module, not a raw string.
//
//   a link            -> a Link
//   a book's barcode  -> a Book, with title/author/cover from Open Library
//   any other code    -> an Idea holding the text
//   a receipt / doc   -> a Document, photo attached, with transcription + summary
//   anything else     -> an Idea

private val json = Json { ignoreUnknownKeys = true }

private const val CLASSIFY_SYSTEM =
    "You look at one photo and say what it is so an app can file it. Respond with ONLY a JSON " +
        "object, no prose and no code fences: " +
        "{\"kind\":\"receipt|document|note|other\",\"title\":\"short label, max 60 chars\"," +
        "\"text\":\"all readable text, verbatim\",\"summary\":\"one or two plain sentences\"}. " +
        "Use \"receipt\" for a shop or restaurant receipt, \"document\" for anything official " +
        "(a letter, bill, policy, ID, form, contract), \"note\" for handwriting or a whiteboard, " +
        "and \"other\" if it is none of those."

// Entry point. Runs the whole flow; every step is guarded so a cancel or a failure is
// quiet rather than fatal.
fun smartScan(scope: CoroutineScope) {
    Native.scanAnyCode { code ->
        if (!code.isNullOrBlank()) {
            scope.launch { runCatching { fileCode(code.trim()) } }
        } else {
            // No code found (or the scanner was dismissed) — photograph it instead.
            Native.takePhoto { b64 ->
                if (b64.isNullOrBlank()) return@takePhoto
                SaveToast.show("Reading…")
                scope.launch { runCatching { filePhoto(b64) } }
            }
        }
    }
}

// ---- codes ----

private fun looksLikeUrl(s: String): Boolean {
    val low = s.lowercase()
    if (low.startsWith("http://") || low.startsWith("https://")) return true
    return !s.contains(' ') && s.contains('.') && s.length > 3 && !s.all { it.isDigit() || it == '.' }
}

private fun looksLikeIsbn(s: String): Boolean {
    val core = s.filter { it.isDigit() || it == 'X' || it == 'x' }
    return core.length == s.replace("-", "").replace(" ", "").length &&
        (core.length == 8 || core.length == 10 || core.length == 12 || core.length == 13)
}

private suspend fun fileCode(code: String) {
    when {
        looksLikeUrl(code) -> {
            val url = if (code.lowercase().startsWith("http")) code else "https://$code"
            val data = loadLinks()
            val vid = parseYouTubeId(url)
            val next = (data.links.maxOfOrNull { it.id } ?: 0L) + 1
            saveLinks(
                data.copy(
                    links = data.links + Link(
                        id = next, url = url,
                        type = if (vid.isNotEmpty()) "video" else "article",
                        videoId = vid,
                    ),
                ),
            )
            SaveToast.show("Saved a link")
            Nav.open("links")
        }

        looksLikeIsbn(code) -> {
            val draft = lookupIsbn(code)
            if (draft == null) {
                fileIdea("Scanned code: $code")
                return
            }
            val data = loadBooks()
            val next = (data.books.maxOfOrNull { it.id } ?: 0L) + 1
            val cover = downloadCover(code)
            saveBooks(data.copy(books = data.books + draft.copy(id = next, photoBlob = cover)))
            SaveToast.show("Added ${draft.title}")
            Nav.open("books")
        }

        else -> fileIdea(code)
    }
}

// Open Library, keyless and public: an ISBN to a draft Book.
private suspend fun lookupIsbn(isbn: String): Book? {
    val clean = isbn.trim().filter { it.isDigit() || it == 'X' || it == 'x' }
    if (clean.isEmpty()) return null
    val resp = httpGet("https://openlibrary.org/api/books?bibkeys=ISBN:$clean&jscmd=data&format=json")
    if (!resp.ok) return null
    return runCatching {
        val entry = json.parseToJsonElement(resp.body).jsonObject["ISBN:$clean"]?.jsonObject ?: return null
        val title = entry["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return null
        val author = runCatching {
            entry["authors"]?.let { arr ->
                (arr as kotlinx.serialization.json.JsonArray).firstOrNull()
                    ?.jsonObject?.get("name")?.jsonPrimitive?.content
            }
        }.getOrNull().orEmpty()
        Book(id = 0, title = title, author = author)
    }.getOrNull()
}

private suspend fun downloadCover(isbn: String): String {
    val clean = isbn.trim().filter { it.isDigit() || it == 'X' || it == 'x' }
    if (clean.isEmpty()) return ""
    val b64 = httpGetImageBase64("https://covers.openlibrary.org/b/isbn/$clean-L.jpg?default=false") ?: return ""
    return saveBlob(b64) ?: ""
}

// ---- photos ----

private suspend fun filePhoto(b64: String) {
    val reply = AiClient.askWithImage(CLASSIFY_SYSTEM, "What is this? Classify and extract.", b64, 700)
    if (reply.isError) {
        SaveToast.show("Couldn't read that — saved the photo instead")
        fileDocument(b64, "Scan", "", "", "")
        return
    }
    val raw = reply.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    fun field(name: String) = obj?.get(name)?.jsonPrimitive?.content?.trim().orEmpty()

    val kind = field("kind").lowercase()
    val title = field("title").ifBlank { "Scan" }
    val text = field("text")
    val summary = field("summary")

    when (kind) {
        "receipt" -> fileDocument(b64, title, "Receipt", text, summary)
        "document" -> fileDocument(b64, title, "", text, summary)
        else -> {
            val body = summary.ifBlank { text }.ifBlank { title }
            fileIdea(body)
        }
    }
}

private fun fileDocument(b64: String, title: String, category: String, text: String, summary: String) {
    val blob = saveBlob(b64).orEmpty()
    val data = loadDocuments()
    val next = (data.documents.maxOfOrNull { it.id } ?: 0L) + 1
    saveDocuments(
        data.copy(
            documents = data.documents + Document(
                id = next, title = title, category = category,
                transcription = text, summary = summary, photoBlob = blob,
            ),
        ),
    )
    SaveToast.show(if (category == "Receipt") "Filed a receipt" else "Filed a document")
    Nav.open("documents")
}

private fun fileIdea(text: String) {
    if (text.isBlank()) return
    val data = loadIdeas()
    val next = (data.ideas.maxOfOrNull { it.id } ?: 0L) + 1
    saveIdeas(data.copy(ideas = data.ideas + Idea(id = next, text = text, created = today().toString())))
    SaveToast.show("Saved to Ideas")
    Nav.open("ideas")
}

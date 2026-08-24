package com.alekpeed.lifeos.books

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.today
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Books — ported from the web app's Books view: a library with reading status,
// per-day reading log (which advances your current page), ratings, page counts
// and word estimates, a reading streak + genre/author stats, and a spine shelf.
// Persists as one JSON blob under "Books"; old status-stub lines migrate.
// Scanning an ISBN auto-downloads the cover into photoBlob; importing an EPUB/TXT
// extracts its text into a device-local text blob (textBlob) for the in-app
// reader, which remembers your place (readFrac).

const val WORDS_PER_PAGE = 275

@Serializable
data class ReadLog(val id: Long, val date: String, val pagesRead: Int)

// One readable file belonging to a book. A book is often several files — the EPUB and
// the PDF of the same title, a companion workbook, the errata — and each one keeps its
// own place, so switching between them doesn't lose where you were in either.
//
// `kind` picks the reader: "text" reads a text blob (EPUB/TXT already extracted to
// plain text), "pdf" reads a PDF blob page by page. `frac` is the text reader's scroll
// position (0..1) and `page` the PDF reader's last page; each is ignored by the other.
@Serializable
data class BookFile(
    val id: Long,
    val name: String,
    val kind: String,          // text | pdf
    val blobId: String,
    val frac: Float = 0f,
    val page: Int = 0,
) {
    val icon: String get() = if (kind == "pdf") "📕" else "📗"
}

@Serializable
data class Book(
    val id: Long,
    val title: String,
    val author: String = "",
    val genre: String = "",
    val status: String = "to_read",   // to_read | reading | finished
    val totalPages: Int? = null,
    val currentPage: Int? = null,
    val startedDate: String = "",
    val finishedDate: String = "",
    val rating: Int = 0,
    val notes: String = "",
    val logs: List<ReadLog> = emptyList(),
    val photoBlob: String = "",        // blob-store id of an attached photo, if any
    val textBlob: String = "",         // text-blob id of an imported ebook's extracted text
    val readFrac: Float = 0f,          // reader scroll position, 0..1, so you resume where you left off
    val pdfBlob: String = "",          // blob-store id of an imported PDF, read in-app page by page
    val pdfPage: Int = 0,              // last page read in the PDF, so you resume where you left off
    val attachments: List<com.alekpeed.lifeos.attach.Attachment> = emptyList(), // extra files: the PDF, notes, etc.
    // Readable files, any number of them. The single textBlob/pdfBlob fields above are
    // the older one-file-per-kind shape; loadBooks() folds them in here on read, so
    // existing books keep their file and their place in it.
    val files: List<BookFile> = emptyList(),
    // Passages worth keeping (§11.5). The reader had no way to capture one, which made
    // the whole library read-only in the way that matters least — you can finish a book
    // and keep nothing out of it.
    val highlights: List<Highlight> = emptyList(),
)

// One saved passage, and where in the book it came from.
//
// `where` is a label rather than a number because a text file and a PDF measure
// position differently and neither can be converted into the other's units: an EPUB has
// no pages, a PDF has no percentage of the whole. Storing what each one honestly knows
// beats storing a page number invented from a fraction.
@Serializable
data class Highlight(
    val id: Long,
    val text: String,
    val note: String = "",
    val fileId: Long = 0,
    val fileName: String = "",
    val where: String = "",
    val date: String = "",
)

@Serializable
data class BooksData(val books: List<Book> = emptyList())

val BOOK_STATUSES = listOf("to_read" to "To read", "reading" to "Reading", "finished" to "Finished")

fun estimatedWords(pages: Int?): Int = if (pages == null) 0 else pages * WORDS_PER_PAGE

// Consecutive days up to today (or yesterday if today isn't logged) with a session.
fun readingStreak(allDates: Set<String>): Int {
    var cursor = today()
    if (!allDates.contains(cursor.toString())) cursor = cursor.minusDays(1)
    var streak = 0
    while (allDates.contains(cursor.toString())) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}

fun addHighlight(b: Book, text: String, note: String, file: BookFile?, where: String): Book {
    val clean = text.trim()
    if (clean.isEmpty()) return b
    val id = (b.highlights.maxOfOrNull { it.id } ?: 0L) + 1
    return b.copy(
        highlights = listOf(
            Highlight(
                id = id, text = clean, note = note.trim(),
                fileId = file?.id ?: 0L, fileName = file?.name.orEmpty(),
                where = where, date = today().toString(),
            ),
        ) + b.highlights,
    )
}

// §11.5, build two: every highlight in one document.
//
// Plain text with light Markdown, because the useful destinations for this are a note
// app, an email and a text file, and all three take Markdown as prose without rendering
// it. Grouped by the file it came from and ordered oldest-captured first — reading
// order, near enough, and better than newest-first for something meant to be read
// straight through.
fun exportHighlights(b: Book): String {
    if (b.highlights.isEmpty()) return ""
    val head = buildList {
        add("# ${b.title.ifBlank { "(untitled)" }}")
        if (b.author.isNotBlank()) add("*${b.author}*")
        add("${b.highlights.size} highlight${if (b.highlights.size == 1) "" else "s"}")
    }
    val body = b.highlights
        .sortedBy { it.id }
        .groupBy { it.fileName }
        .flatMap { (file, hs) ->
            buildList {
                if (file.isNotBlank() && b.highlights.map { it.fileName }.distinct().size > 1) add("## $file")
                hs.forEach { h ->
                    add("> ${h.text.trim().replace("\n", "\n> ")}")
                    val meta = listOf(h.where, h.date).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) add("— $meta")
                    if (h.note.isNotBlank()) add(h.note.trim())
                }
            }
        }
    return (head + "" + body).joinToString("\n\n").trim() + "\n"
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// Fold a book's legacy single ebook / single PDF into the files list, keeping the place
// it was left at. Runs on every read and is idempotent: once the fields are empty (they
// clear on the next save) there is nothing left to fold.
private fun withFiles(b: Book): Book {
    if (b.textBlob.isBlank() && b.pdfBlob.isBlank()) return b
    var next = b.files.maxOfOrNull { it.id } ?: 0L
    val folded = buildList {
        addAll(b.files)
        if (b.textBlob.isNotBlank() && b.files.none { it.blobId == b.textBlob }) {
            add(BookFile(++next, "Ebook", "text", b.textBlob, frac = b.readFrac))
        }
        if (b.pdfBlob.isNotBlank() && b.files.none { it.blobId == b.pdfBlob }) {
            add(BookFile(++next, "PDF", "pdf", b.pdfBlob, page = b.pdfPage))
        }
    }
    return b.copy(files = folded, textBlob = "", pdfBlob = "", readFrac = 0f, pdfPage = 0)
}

fun loadBooks(): BooksData {
    val raw = Storage.read("Books")
    if (raw.isNullOrBlank()) return BooksData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<BooksData>(raw) }
            .map { d -> d.copy(books = d.books.map(::withFiles)) }
            .getOrElse { BooksData() }
    }
    // Old StatusListScreen stub ("<title>\t<statusIndex>"): 0 Want, 1 Reading, 2 Read.
    val books = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        val status = when (parts.getOrNull(1)?.toIntOrNull() ?: 0) { 1 -> "reading"; 2 -> "finished"; else -> "to_read" }
        Book(id = i + 1L, title = parts[0].trim(), status = status)
    }
    return BooksData(books)
}

fun saveBooks(data: BooksData) {
    Storage.write("Books", json.encodeToString(data))
}

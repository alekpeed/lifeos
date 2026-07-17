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
// (EPUB/PDF reader, cover images, and book files wait on the attachment layer.)

const val WORDS_PER_PAGE = 275

@Serializable
data class ReadLog(val id: Long, val date: String, val pagesRead: Int)

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

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadBooks(): BooksData {
    val raw = Storage.read("Books")
    if (raw.isNullOrBlank()) return BooksData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<BooksData>(raw) }.getOrElse { BooksData() }
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

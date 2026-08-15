package com.alekpeed.lifeos.ai

import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.places.loadPlaces
import com.alekpeed.lifeos.tasks.loadTasks

// The words a transcriber has no way to guess: the names of the people, places,
// projects and habits that only exist in your own data. "Meet Siobhan at Tsukiji"
// is three chances to be wrong, and none of them are fixable by a better model —
// they're proper nouns it has never seen. The transcription endpoint takes a
// `keywords` list for exactly this, so the mic sends your own vocabulary along
// with the audio.
//
// Hints, not instructions: the API's own wording is that a keyword should appear
// in the transcript "only when the audio contains it", so an unspoken name here
// doesn't get invented into what you said.

// Contacts first, because names are what gets mangled most and the list is
// ordered — everything after this competes for what's left of the cap.
private const val MAX_KEYWORDS = 60

// Long enough to be worth hinting, short enough to be one spoken term. A
// one-character "name" is noise, and a 40-character one is a sentence.
private const val MIN_TERM = 3
private const val MAX_TERM = 40

// The docs ask for literal terms without special characters, so anything that
// isn't a letter, digit, space, hyphen or apostrophe is dropped rather than sent
// and ignored. A term has to keep at least one letter to still mean anything.
private fun clean(raw: String): String? {
    val t = raw.trim().filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '\'' }
        .replace(Regex("\\s+"), " ")
        .trim()
    if (t.length < MIN_TERM || t.length > MAX_TERM) return null
    if (t.none { it.isLetter() }) return null
    return t
}

// Each source is read defensively: a store that fails to parse costs its own
// keywords, never the transcription itself.
private fun sourceTerms(source: () -> List<String>): List<String> =
    runCatching { source() }.getOrDefault(emptyList())

fun dictationKeywords(): List<String> {
    val out = LinkedHashSet<String>()

    val sources = listOf(
        { loadContacts().contacts.map { it.name } },
        { loadPlaces().places.map { it.name } },
        { loadTasks().map { it.project } },
        { loadHabits().map { it.name } },
    )

    for (source in sources) {
        for (raw in sourceTerms(source)) {
            clean(raw)?.let { out.add(it) }
            if (out.size >= MAX_KEYWORDS) return out.toList()
        }
    }
    return out.toList()
}

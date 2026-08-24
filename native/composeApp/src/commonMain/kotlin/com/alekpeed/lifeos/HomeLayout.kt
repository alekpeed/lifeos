package com.alekpeed.lifeos

// What the home screen decides, separated from how it draws.
//
// There is one way to find a module and one way to browse them: type a few letters, or
// open the domain it lives in. Pinned and Recent rows were tried and removed — with a
// search box and an expandable list of all eight domains already on the screen, they
// were a third and fourth route to the same place, and two rows of duplicated navigation
// pushed the actual content down a phone screen that has a notch eating the top of it.
//
// Search is pure and tested because it fails quietly: drop a module out of the results
// over a stray capital and nobody files a bug, they just stop trusting the box.

// Case-insensitive, matches anywhere in the name, and also matches the domain so "arch"
// finds everything in Archive. Not fuzzy: a launcher that guesses is a launcher that
// shows you the wrong thing confidently.
fun moduleMatches(m: Module, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return m.label.lowercase().contains(q) ||
        m.id.lowercase().contains(q) ||
        m.group.lowercase().contains(q)
}

// Best matches first: something that starts with what you typed beats something that
// merely contains it, so "ta" puts Tasks above Time Capsules and Rabbit Holes.
fun searchModules(modules: List<Module>, query: String): List<Module> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return modules.filter { moduleMatches(it, q) }
        .sortedWith(
            compareBy(
                { !it.label.lowercase().startsWith(q) },
                { !it.label.lowercase().contains(q) },
                { it.label.lowercase() },
            ),
        )
}

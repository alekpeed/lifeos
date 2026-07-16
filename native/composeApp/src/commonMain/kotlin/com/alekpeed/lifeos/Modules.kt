package com.alekpeed.lifeos

import androidx.compose.runtime.Composable
import com.alekpeed.lifeos.habits.HabitsScreen
import com.alekpeed.lifeos.ideas.IdeasScreen
import com.alekpeed.lifeos.tasks.TasksScreen
import com.alekpeed.lifeos.ui.NoteListScreen
import com.alekpeed.lifeos.ui.Placeholder
import com.alekpeed.lifeos.ui.SimpleListScreen
import com.alekpeed.lifeos.ui.StatusListScreen

// One entry per module. `ready` = genuinely functional (persists); otherwise a
// reachable placeholder until it's ported. Icons are emoji stand-ins for now.
data class Module(
    val icon: String,
    val label: String,
    val group: String,
    val ready: Boolean,
    val content: @Composable () -> Unit,
)

val MODULE_GROUPS = listOf("Core", "People", "Memory", "Health", "Insight", "System")

fun lifeOsModules(): List<Module> = listOf(
    // Core
    Module("✅", "Tasks", "Core", true) { TasksScreen() },
    Module("💡", "Ideas", "Core", true) { IdeasScreen() },
    Module("📍", "Places", "Core", true) { SimpleListScreen("Places", "New place", listOf("The ramen place", "That trail")) },
    Module("🔗", "Links", "Core", true) { SimpleListScreen("Links", "Paste a link", listOf("Article to read")) },
    Module("🗓", "Today", "Core", false) { Placeholder("Today") },
    Module("⌘", "Command", "Core", false) { Placeholder("Command") },
    Module("💵", "Finance", "Core", false) { Placeholder("Finance") },
    Module("🎓", "Education", "Core", false) { Placeholder("Education") },
    Module("📰", "Daily Paper", "Core", false) { Placeholder("Daily Paper") },
    Module("🪐", "Orrery", "Core", false) { Placeholder("Orrery") },
    // People
    Module("👤", "Contacts", "People", true) { SimpleListScreen("Contacts", "New contact") },
    Module("🍳", "Recipes", "People", true) { NoteListScreen("Recipes", "Recipe name", "Key ingredients / notes") },
    Module("📄", "Documents", "People", true) { NoteListScreen("Documents", "Document name", "Where it lives / reference #") },
    Module("📦", "Quartermaster", "People", false) { Placeholder("Quartermaster") },
    Module("🧳", "Packing Lists", "People", true) { SimpleListScreen("Packing", "Add an item") },
    Module("🤝", "Sharebox", "People", false) { Placeholder("Sharebox") },
    // Memory
    Module("📚", "Books", "Memory", true) {
        StatusListScreen(
            "Books", "Add a book", listOf("Want", "Reading", "Read"),
            seed = listOf("Life OS design notes" to 1),
        )
    },
    Module("🖼", "Photos", "Memory", false) { Placeholder("Photos") },
    Module("🏆", "Milestones", "Memory", true) { NoteListScreen("Milestones", "What you achieved", "When") },
    Module("🏛", "Museum", "Memory", false) { Placeholder("Museum") },
    Module("⏳", "Time Capsules", "Memory", true) { NoteListScreen("Time Capsules", "Message to future you", "Open on") },
    Module("🗂", "Collections", "Memory", true) { SimpleListScreen("Collections", "Add an item") },
    Module("👻", "Ghost Days", "Memory", false) { Placeholder("Ghost Days") },
    Module("🕳", "Rabbit Holes", "Memory", true) { SimpleListScreen("Rabbit Holes", "New rabbit hole") },
    // Health
    Module("🔥", "Habits", "Health", true) { HabitsScreen() },
    Module("❤", "Health", "Health", false) { Placeholder("Health") },
    Module("🌳", "Skill Trees", "Health", false) { Placeholder("Skill Trees") },
    Module("📊", "The Almanac", "Health", false) { Placeholder("The Almanac") },
    // Insight
    Module("📋", "Briefing", "Insight", false) { Placeholder("Briefing") },
    Module("🔎", "Ask", "Insight", false) { Placeholder("Ask") },
    Module("🤖", "AI Assistant", "Insight", false) { Placeholder("AI Assistant") },
    Module("🕸", "Knowledge Graph", "Insight", false) { Placeholder("Knowledge Graph") },
    Module("♻", "Recall", "Insight", false) { Placeholder("Recall") },
    Module("🔔", "Notifications", "Insight", false) { Placeholder("Notifications") },
    Module("🌀", "Entropy", "Insight", false) { Placeholder("Entropy") },
    Module("⏰", "Time Machine", "Insight", false) { Placeholder("Time Machine") },
    // System
    Module("🔍", "Search", "System", false) { Placeholder("Search") },
    Module("🧰", "Tools", "System", false) { Placeholder("Tools") },
    Module("🔳", "QR Sync", "System", false) { Placeholder("QR Sync") },
    Module("🎨", "Theme from Photo", "System", false) { Placeholder("Theme from Photo") },
    Module("🐱", "Station Cat", "System", false) { Placeholder("Station Cat") },
    Module("⚙", "Settings", "System", false) { Placeholder("Settings") },
)

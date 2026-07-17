package com.alekpeed.lifeos

import androidx.compose.runtime.Composable
import com.alekpeed.lifeos.core.CommandScreen
import com.alekpeed.lifeos.core.TodayScreen
import com.alekpeed.lifeos.documents.DocumentsScreen
import com.alekpeed.lifeos.education.EducationScreen
import com.alekpeed.lifeos.links.LinksScreen
import com.alekpeed.lifeos.packing.PackingScreen
import com.alekpeed.lifeos.places.PlacesScreen
import com.alekpeed.lifeos.quartermaster.QuartermasterScreen
import com.alekpeed.lifeos.recipes.RecipesScreen
import com.alekpeed.lifeos.finance.LedgerScreen
import com.alekpeed.lifeos.habits.HabitsScreen
import com.alekpeed.lifeos.health.HealthScreen
import com.alekpeed.lifeos.ideas.IdeasScreen
import com.alekpeed.lifeos.insight.AskScreen
import com.alekpeed.lifeos.insight.AssistantScreen
import com.alekpeed.lifeos.insight.BriefingScreen
import com.alekpeed.lifeos.insight.EntropyScreen
import com.alekpeed.lifeos.insight.NotificationsScreen
import com.alekpeed.lifeos.insight.RecallScreen
import com.alekpeed.lifeos.people.ContactsScreen
import com.alekpeed.lifeos.settings.SettingsScreen
import com.alekpeed.lifeos.system.QrSyncScreen
import com.alekpeed.lifeos.system.StationCatScreen
import com.alekpeed.lifeos.system.ToolsScreen
import com.alekpeed.lifeos.tasks.TasksScreen
import com.alekpeed.lifeos.ui.NoteListScreen
import com.alekpeed.lifeos.ui.SearchScreen
import com.alekpeed.lifeos.ui.SimpleListScreen
import com.alekpeed.lifeos.ui.StatsScreen
import com.alekpeed.lifeos.ui.StatusListScreen

// One entry per module. `id` is a stable slug used to route through the interface
// layer (a graphical interface registers screens against these ids). `content` is
// the built-in functional screen; every one persists to / reads from local storage.
// `ready` = genuinely functional. Icons are emoji stand-ins; a graphical interface
// supplies real art.
data class Module(
    val id: String,
    val icon: String,
    val label: String,
    val group: String,
    val ready: Boolean,
    val content: @Composable () -> Unit,
)

val MODULE_GROUPS = listOf("Core", "People", "Memory", "Health", "Insight", "System")

fun lifeOsModules(): List<Module> = listOf(
    // Core
    Module("tasks", "✅", "Tasks", "Core", true) { TasksScreen() },
    Module("ideas", "💡", "Ideas", "Core", true) { IdeasScreen() },
    Module("places", "📍", "Places", "Core", true) { PlacesScreen() },
    Module("links", "🔗", "Links", "Core", true) { LinksScreen() },
    Module("today", "🗓", "Today", "Core", true) { TodayScreen() },
    Module("command", "⌘", "Command", "Core", true) { CommandScreen() },
    Module("finance", "💵", "Finance", "Core", true) { LedgerScreen() },
    Module("education", "🎓", "Education", "Core", true) { EducationScreen() },
    Module("daily-paper", "📰", "Daily Paper", "Core", true) { NoteListScreen("Daily Paper", "Headline", "Source / link") },
    Module("orrery", "🪐", "Orrery", "Core", true) { NoteListScreen("Orrery", "Celestial note or event", "When") },
    // People
    Module("contacts", "👤", "Contacts", "People", true) { ContactsScreen() },
    Module("recipes", "🍳", "Recipes", "People", true) { RecipesScreen() },
    Module("documents", "📄", "Documents", "People", true) { DocumentsScreen() },
    Module("quartermaster", "📦", "Quartermaster", "People", true) { QuartermasterScreen() },
    Module("packing", "🧳", "Packing Lists", "People", true) { PackingScreen() },
    Module("sharebox", "🤝", "Sharebox", "People", true) { NoteListScreen("Sharebox", "Something to share", "With whom") },
    // Memory
    Module("books", "📚", "Books", "Memory", true) {
        StatusListScreen("Books", "Add a book", listOf("Want", "Reading", "Read"), seed = listOf("Life OS design notes" to 1))
    },
    Module("photos", "🖼", "Photos", "Memory", true) { NoteListScreen("Photos", "Caption", "Where it's stored") },
    Module("milestones", "🏆", "Milestones", "Memory", true) { NoteListScreen("Milestones", "What you achieved", "When") },
    Module("museum", "🏛", "Museum", "Memory", true) { NoteListScreen("Museum", "Artifact", "Note") },
    Module("time-capsules", "⏳", "Time Capsules", "Memory", true) { NoteListScreen("Time Capsules", "Message to future you", "Open on") },
    Module("collections", "🗂", "Collections", "Memory", true) { SimpleListScreen("Collections", "Add an item") },
    Module("ghost-days", "👻", "Ghost Days", "Memory", true) { NoteListScreen("Ghost Days", "A quiet or lost day", "Date") },
    Module("rabbit-holes", "🕳", "Rabbit Holes", "Memory", true) { SimpleListScreen("Rabbit Holes", "New rabbit hole") },
    // Health
    Module("habits", "🔥", "Habits", "Health", true) { HabitsScreen() },
    Module("health", "❤", "Health", "Health", true) { HealthScreen() },
    Module("skill-trees", "🌳", "Skill Trees", "Health", true) {
        StatusListScreen("Skill Trees", "Add a skill", listOf("Locked", "Learning", "Mastered"))
    },
    Module("almanac", "📊", "The Almanac", "Health", true) { StatsScreen("The Almanac") },
    // Insight
    Module("briefing", "📋", "Briefing", "Insight", true) { BriefingScreen() },
    Module("ask", "🔎", "Ask", "Insight", true) { AskScreen() },
    Module("ai-assistant", "🤖", "AI Assistant", "Insight", true) { AssistantScreen() },
    Module("knowledge-graph", "🕸", "Knowledge Graph", "Insight", true) { NoteListScreen("Knowledge Graph", "Node", "Links to…") },
    Module("recall", "♻", "Recall", "Insight", true) { RecallScreen() },
    Module("notifications", "🔔", "Notifications", "Insight", true) { NotificationsScreen() },
    Module("entropy", "🌀", "Entropy", "Insight", true) { EntropyScreen() },
    Module("time-machine", "⏰", "Time Machine", "Insight", true) { NoteListScreen("Time Machine", "On this day…", "Date") },
    // System
    Module("search", "🔍", "Search", "System", true) { SearchScreen("Search", "Search everything…") },
    Module("tools", "🧰", "Tools", "System", true) { ToolsScreen() },
    Module("qr-sync", "🔳", "QR Sync", "System", true) { QrSyncScreen() },
    Module("theme-from-photo", "🎨", "Theme from Photo", "System", true) { NoteListScreen("Theme from Photo", "Theme name", "Hex colors") },
    Module("station-cat", "🐱", "Station Cat", "System", true) { StationCatScreen() },
    Module("settings", "⚙", "Settings", "System", true) { SettingsScreen() },
)

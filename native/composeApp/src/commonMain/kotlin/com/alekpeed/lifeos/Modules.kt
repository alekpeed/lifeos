package com.alekpeed.lifeos

import androidx.compose.runtime.Composable
import com.alekpeed.lifeos.books.BooksScreen
import com.alekpeed.lifeos.collections.CollectionsScreen
import com.alekpeed.lifeos.core.CommandScreen
import com.alekpeed.lifeos.core.TodayScreen
import com.alekpeed.lifeos.ghostdays.GhostDaysScreen
import com.alekpeed.lifeos.documents.DocumentsScreen
import com.alekpeed.lifeos.education.EducationScreen
import com.alekpeed.lifeos.links.LinksScreen
import com.alekpeed.lifeos.milestones.MilestonesScreen
import com.alekpeed.lifeos.museum.MuseumScreen
import com.alekpeed.lifeos.orrery.OrreryScreen
import com.alekpeed.lifeos.packing.PackingScreen
import com.alekpeed.lifeos.paper.DailyPaperScreen
import com.alekpeed.lifeos.places.PlacesScreen
import com.alekpeed.lifeos.quartermaster.QuartermasterScreen
import com.alekpeed.lifeos.rabbitholes.RabbitHolesScreen
import com.alekpeed.lifeos.recipes.RecipesScreen
import com.alekpeed.lifeos.skilltrees.SkillTreesScreen
import com.alekpeed.lifeos.sharebox.ShareboxScreen
import com.alekpeed.lifeos.timecapsules.TimeCapsulesScreen
import com.alekpeed.lifeos.finance.FinanceScreen
import com.alekpeed.lifeos.habits.HabitsScreen
import com.alekpeed.lifeos.health.HealthScreen
import com.alekpeed.lifeos.ideas.IdeasScreen
import com.alekpeed.lifeos.insight.AskScreen
import com.alekpeed.lifeos.insight.AssistantScreen
import com.alekpeed.lifeos.insight.BriefingScreen
import com.alekpeed.lifeos.insight.EntropyScreen
import com.alekpeed.lifeos.insight.NotificationsScreen
import com.alekpeed.lifeos.insight.AlmanacScreen
import com.alekpeed.lifeos.insight.RecallScreen
import com.alekpeed.lifeos.knowledge.KnowledgeGraphScreen
import com.alekpeed.lifeos.timemachine.TimeMachineScreen
import com.alekpeed.lifeos.people.ContactsScreen
import com.alekpeed.lifeos.photos.PhotosScreen
import com.alekpeed.lifeos.settings.SettingsScreen
import com.alekpeed.lifeos.system.QrSyncScreen
import com.alekpeed.lifeos.system.ToolsScreen
import com.alekpeed.lifeos.tasks.TasksScreen
import com.alekpeed.lifeos.ui.SearchScreen

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

// The eight domains, in wheel order. These group the modules on the home screen
// and in any graphical interface's navigation.
val MODULE_GROUPS = listOf(
    "Operations", "Archive", "Navigation", "Discovery",
    "Management", "Intelligence", "People", "System",
)

fun lifeOsModules(): List<Module> = listOf(
    // Operations — run your day
    Module("today", "🗓", "Today", "Operations", true) { TodayScreen() },
    Module("daily-paper", "📰", "Daily Paper", "Operations", true) { DailyPaperScreen() },
    Module("tasks", "✅", "Tasks", "Operations", true) { TasksScreen() },
    Module("command", "⌘", "Command", "Operations", true) { CommandScreen() },
    Module("briefing", "📋", "Briefing", "Operations", true) { BriefingScreen() },
    Module("notifications", "🔔", "Notifications", "Operations", true) { NotificationsScreen() },
    // Archive — what you keep
    Module("documents", "📄", "Documents", "Archive", true) { DocumentsScreen() },
    Module("links", "🔗", "Links", "Archive", true) { LinksScreen() },
    Module("books", "📚", "Books", "Archive", true) { BooksScreen() },
    Module("photos", "🖼", "Photos", "Archive", true) { PhotosScreen() },
    Module("museum", "🏛", "Museum", "Archive", true) { MuseumScreen() },
    Module("collections", "🗂", "Collections", "Archive", true) { CollectionsScreen() },
    Module("time-capsules", "⏳", "Time Capsules", "Archive", true) { TimeCapsulesScreen() },
    Module("milestones", "🏆", "Milestones", "Archive", true) { MilestonesScreen() },
    Module("ghost-days", "👻", "Ghost Days", "Archive", true) { GhostDaysScreen() },
    // Navigation — places & things in motion
    Module("places", "📍", "Places", "Navigation", true) { PlacesScreen() },
    Module("orrery", "🪐", "Orrery", "Navigation", true) { OrreryScreen() },
    Module("quartermaster", "📦", "Quartermaster", "Navigation", true) { QuartermasterScreen() },
    Module("packing", "🧳", "Packing Lists", "Navigation", true) { PackingScreen() },
    // Discovery — learning & curiosity
    Module("education", "🎓", "Education", "Discovery", true) { EducationScreen() },
    Module("skill-trees", "🌳", "Skill Trees", "Discovery", true) { SkillTreesScreen() },
    Module("ideas", "💡", "Ideas", "Discovery", true) { IdeasScreen() },
    Module("rabbit-holes", "🕳", "Rabbit Holes", "Discovery", true) { RabbitHolesScreen() },
    Module("almanac", "📊", "The Almanac", "Discovery", true) { AlmanacScreen() },
    // Management — body, home & money
    Module("habits", "🔥", "Habits", "Management", true) { HabitsScreen() },
    Module("health", "❤", "Health", "Management", true) { HealthScreen() },
    Module("recipes", "🍳", "Recipes", "Management", true) { RecipesScreen() },
    Module("finance", "💵", "Finance", "Management", true) { FinanceScreen() },
    // Intelligence — the app thinking about you
    Module("ask", "🔎", "Ask", "Intelligence", true) { AskScreen() },
    Module("ai-assistant", "🤖", "AI Assistant", "Intelligence", true) { AssistantScreen() },
    Module("knowledge-graph", "🕸", "Knowledge Graph", "Intelligence", true) { KnowledgeGraphScreen() },
    Module("recall", "♻", "Recall", "Intelligence", true) { RecallScreen() },
    Module("entropy", "🌀", "Entropy", "Intelligence", true) { EntropyScreen() },
    Module("time-machine", "⏰", "Time Machine", "Intelligence", true) { TimeMachineScreen() },
    // People — connecting with others & devices
    Module("contacts", "👤", "Contacts", "People", true) { ContactsScreen() },
    Module("sharebox", "🤝", "Sharebox", "People", true) { ShareboxScreen() },
    Module("qr-sync", "🔳", "QR Sync", "People", true) { QrSyncScreen() },
    // System — running the OS
    Module("search", "🔍", "Search", "System", true) { SearchScreen("Search", "Search everything…") },
    Module("tools", "🧰", "Tools", "System", true) { ToolsScreen() },
    Module("settings", "⚙", "Settings", "System", true) { SettingsScreen() },
)

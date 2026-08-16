package com.alekpeed.lifeos.interfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alekpeed.lifeos.Storage

typealias ModuleContent = @Composable () -> Unit

// The interface layer. Every module page is rendered through `Interfaces.Render`,
// which looks up whether the *active* interface supplies a custom (e.g. graphical)
// screen for that module id; if not, it falls back to the built-in functional
// screen. This is what keeps interfaces interchangeable: Alek designs a graphical
// interface, registers its per-module screens under an interface id, and every
// page can accept it without touching module logic or the data it persists.
object Interfaces {
    const val DEFAULT = "default"

    // interfaceId -> (moduleId -> screen)
    private val registry = mutableMapOf<String, MutableMap<String, ModuleContent>>()

    // interfaceId -> its own home/launcher screen. An interface can replace just the
    // home (its navigation art) and leave every module on the functional default.
    private val homes = mutableMapOf<String, ModuleContent>()

    // Active interface id, observable so switching it in Settings recomposes pages.
    // Persisted, so the interface you picked is still there after a restart.
    private const val K_ACTIVE = "ActiveInterface"
    private const val K_NOCTURNE_HOME_MIGRATED = "NocturneHomeMigrated"

    // Nocturne is the baseline interface. Other graphical interfaces, including
    // NEXUS, remain registered and selectable in Settings.
    const val BASELINE = "nocturne"

    // Promote Nocturne once on existing installs so the new canonical home actually
    // becomes visible. After this one-time migration, user interface choices persist
    // normally and are not overwritten again.
    private var activeState by mutableStateOf(
        if (Storage.read(K_NOCTURNE_HOME_MIGRATED) != "1") {
            Storage.write(K_ACTIVE, BASELINE)
            Storage.write(K_NOCTURNE_HOME_MIGRATED, "1")
            BASELINE
        } else {
            Storage.read(K_ACTIVE)?.ifBlank { null } ?: BASELINE
        },
    )
    val active: String get() = activeState

    // "default" plus every interface anyone has registered a screen or home under.
    val available: List<String>
        get() = (listOf(DEFAULT) + (registry.keys + homes.keys).sorted()).distinct()

    fun setActive(id: String) {
        activeState = id
        Storage.write(K_ACTIVE, id)
    }

    fun register(interfaceId: String, moduleId: String, content: ModuleContent) {
        require(interfaceId != DEFAULT) { "The default interface is the built-in functional layer." }
        registry.getOrPut(interfaceId) { mutableMapOf() }[moduleId] = content
    }

    // Register an interface's home/launcher. Idempotent — safe to call on every open.
    fun registerHome(interfaceId: String, content: ModuleContent) {
        require(interfaceId != DEFAULT) { "The default interface is the built-in functional layer." }
        homes[interfaceId] = content
    }

    // The active interface's own home, or null to use the built-in launcher.
    fun home(): ModuleContent? = if (activeState == DEFAULT) null else homes[activeState]

    // True if the active interface supplies its own screen for this module.
    fun hasCustom(moduleId: String): Boolean =
        activeState != DEFAULT && registry[activeState]?.containsKey(moduleId) == true

    @Composable
    fun Render(moduleId: String, default: ModuleContent) {
        val custom = if (activeState == DEFAULT) null else registry[activeState]?.get(moduleId)
        (custom ?: default)()
    }
}

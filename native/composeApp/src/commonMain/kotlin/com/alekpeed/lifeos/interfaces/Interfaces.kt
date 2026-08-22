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
// screen. This is what keeps interfaces interchangeable: a graphical interface
// registers its per-module screens under an interface id, and every page can accept
// it without touching module logic or the data it persists.
//
// No graphical interfaces ship today (NEXUS, Nocturne and Machiya were removed
// 2026-08-22). This layer is retained deliberately so new ones can be attached later
// without touching any module. Every module must keep rendering through `Render`.
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
    private const val K_RESET_2026_08 = "InterfaceResetToDefault"

    // The functional interface is the baseline and, for now, the only one.
    const val BASELINE = DEFAULT

    // One-time reset. Existing installs hold "nocturne" or "machiya" in K_ACTIVE from
    // the removed graphical interfaces; without this they would start up pointing at an
    // interface that no longer exists. Runs once, then user choices persist normally.
    private var activeState by mutableStateOf(
        if (Storage.read(K_RESET_2026_08) != "1") {
            Storage.write(K_ACTIVE, DEFAULT)
            Storage.write(K_RESET_2026_08, "1")
            DEFAULT
        } else {
            Storage.read(K_ACTIVE)?.ifBlank { null } ?: DEFAULT
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

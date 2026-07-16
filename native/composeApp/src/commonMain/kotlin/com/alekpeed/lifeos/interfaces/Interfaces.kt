package com.alekpeed.lifeos.interfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

typealias ModuleContent = @Composable () -> Unit

// The interface layer. Every module page is rendered through `Interfaces.Render`,
// which looks up whether the *active* interface supplies a custom (e.g. graphical)
// screen for that module id; if not, it falls back to the built-in functional
// screen. This is what keeps interfaces interchangeable: Alek designs a graphical
// interface, registers its per-module screens under an interface id, and every
// page can accept it without touching module logic or the data it persists.
//
// Registering a graphical interface later looks like:
//
//   Interfaces.register("spatial-1", "tasks") { MySpatialTasks() }
//   Interfaces.register("spatial-1", "habits") { MySpatialHabits() }
//
// It then appears in Settings and can be switched on live. Any module without a
// custom screen for the active interface still renders its functional default, so
// interfaces can be partial and filled in over time.
object Interfaces {
    const val DEFAULT = "default"

    // interfaceId -> (moduleId -> screen)
    private val registry = mutableMapOf<String, MutableMap<String, ModuleContent>>()

    // Active interface id, observable so switching it in Settings recomposes pages.
    private var activeState by mutableStateOf(DEFAULT)
    val active: String get() = activeState

    // "default" plus every interface anyone has registered a screen under.
    val available: List<String> get() = (listOf(DEFAULT) + registry.keys.sorted()).distinct()

    fun setActive(id: String) {
        activeState = id
    }

    fun register(interfaceId: String, moduleId: String, content: ModuleContent) {
        require(interfaceId != DEFAULT) { "The default interface is the built-in functional layer." }
        registry.getOrPut(interfaceId) { mutableMapOf() }[moduleId] = content
    }

    // True if the active interface supplies its own screen for this module.
    fun hasCustom(moduleId: String): Boolean =
        activeState != DEFAULT && registry[activeState]?.containsKey(moduleId) == true

    @Composable
    fun Render(moduleId: String, default: ModuleContent) {
        val custom = if (activeState == DEFAULT) null else registry[activeState]?.get(moduleId)
        (custom ?: default)()
    }
}

package com.alekpeed.lifeos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// A tiny navigation bus so platform code (deep links, app shortcuts, an NFC tag,
// a shared item) can ask the UI to open a specific module by id. The Shell observes
// `pendingModuleId`; when it's set, it opens that module and clears it.
object Nav {
    var pendingModuleId: String? by mutableStateOf(null)
        private set

    fun open(moduleId: String) {
        pendingModuleId = moduleId
    }

    fun consume(): String? {
        val id = pendingModuleId
        pendingModuleId = null
        return id
    }
}

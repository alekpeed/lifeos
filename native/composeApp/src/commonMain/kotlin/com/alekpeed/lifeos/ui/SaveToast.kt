package com.alekpeed.lifeos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// A tiny app-wide "it saved" signal. Screens auto-save as you type, then call
// SaveToast.show(); the Shell shows a brief pill at the bottom (debounced, so it
// doesn't flash on every keystroke). Observable so any screen can trigger it
// without wiring a callback down the tree.
object SaveToast {
    var tick by mutableStateOf(0)
        private set
    var message by mutableStateOf("Saved")
        private set

    fun show(msg: String = "Saved") {
        message = msg
        tick += 1
    }
}

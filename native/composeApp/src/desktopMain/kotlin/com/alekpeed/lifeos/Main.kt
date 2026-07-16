package com.alekpeed.lifeos

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// Native Windows (and other desktop) entry point.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Life OS") {
        App()
    }
}

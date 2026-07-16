// Root build for the native Life OS app (Kotlin + Compose Multiplatform).
// One codebase -> a native Android APK and a native Windows desktop app, no
// WebView. This is the real-native foundation replacing the Capacitor shell.
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.android.application") version "8.2.1" apply false
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.time.Instant

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.application")
}

// CI passes the workflow's own run number (LIFEOS_BUILD_NUMBER) — a plain integer
// that goes up by one every time build-native.yml runs, which is exactly what a
// build number should be. 0 for a local/PR/fork build with no CI env var, matching
// what versionCode/versionName/packageVersion below defaulted to before this existed.
val buildNumber = (System.getenv("LIFEOS_BUILD_NUMBER") ?: "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

// Both jpackage's packageVersion and Android's versionName below are hand-set
// constants — they don't change per build, so two builds a month apart can look
// identical in Settings with no way to tell which one is actually installed. This
// stamps the real commit and build time into a generated source file every build,
// read on the Settings screen, so that ambiguity has an actual answer.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    outputs.dir(outputDir)
    // Must reflect the commit actually being built, not a cached prior result.
    outputs.upToDateWhen { false }
    doLast {
        val sha = runCatching {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(project.projectDir)
                .start()
            proc.waitFor()
            proc.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("").ifBlank { "dev" }
        val time = Instant.now().toString()
        val pkgDir = outputDir.get().dir("com/alekpeed/lifeos").asFile
        pkgDir.mkdirs()
        File(pkgDir, "BuildInfo.kt").writeText(
            "package com.alekpeed.lifeos\n\n" +
                "// Generated at build time by :composeApp:generateBuildInfo — do not edit.\n" +
                "const val BUILD_SHA = \"$sha\"\n" +
                "const val BUILD_TIME = \"$time\"\n",
        )
    }
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateBuildInfo.map { it.outputs.files.singleFile })
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.animation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        // Code shared between exactly Android and desktop, but not expressible as
        // commonMain because it's plain java.io / java.util.zip — APIs the common
        // metadata compilation can't see even though both actual targets are JVM.
        // Ebook (EPUB/TXT) parsing and the Apple Health export's streamed-and-filtered
        // read live here so neither platform has its own copy of the same regex and
        // zip-walking logic.
        val jvmShared by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                // WindowCompat / WindowInsetsControllerCompat — immersive full screen for
                // graphical interfaces that supply their own status row.
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("com.google.android.gms:play-services-location:21.0.1")
                // WebSocket client for Supabase Realtime (Phoenix channels). minSdk 24
                // rules out java.net.http.WebSocket (API 34+), so OkHttp carries it on
                // both JVM targets.
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                // Offline speech engine: lightweight on-device keyword spotting +
                // speaker identification, no cloud, far lighter than looping the
                // system SpeechRecognizer. Bundles its own native libs (JNA + libvosk).
                implementation("com.alphacephei:vosk-android:0.3.75")
                // QR: pure-Java encoder (both platforms) + camera scanner (Android).
                implementation("com.google.zxing:core:3.5.3")
                implementation("com.journeyapps:zxing-android-embedded:4.3.0")
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("com.google.zxing:core:3.5.3")
                // Same Realtime WebSocket client as Android (see androidMain note).
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
            }
        }
    }
}

android {
    namespace = "com.alekpeed.lifeos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alekpeed.lifeos"
        minSdk = 24
        targetSdk = 34
        // versionCode must strictly increase for Android's own installer to update
        // the app in place rather than refusing with "app not installed" — a fixed
        // "1" forever meant every install after the first needed an uninstall first,
        // the same failure mode the checked-in debug keystore below fixes for
        // mismatched signing keys. +1 so a local build (buildNumber 0) still gets a
        // valid versionCode >= 1, matching what this was hardcoded to before.
        versionCode = buildNumber + 1
        versionName = "1.0.$buildNumber"
        // Baked-in default OpenAI key, injected from the OPENAI_API_KEY build
        // environment (a GitHub Actions secret in CI) — never committed to source.
        // Empty for local/desktop/PR builds, where the app falls back to a
        // user-entered key. Escape any double-quote defensively.
        val bakedKey = (System.getenv("OPENAI_API_KEY") ?: "").trim().replace("\"", "\\\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$bakedKey\"")
    }
    buildFeatures {
        buildConfig = true
    }
    // A checked-in debug keystore (standard well-known debug credentials, not a
    // secret) so every CI build is signed with the same key. Without this, each
    // GitHub Actions run generates a throwaway debug key and Android refuses to
    // upgrade the app in place ("app not installed") — forcing an uninstall first.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Vosk ships prebuilt native libs (JNA + libvosk) for each ABI; pick-first
    // avoids duplicate-file merge failures if another dep carries the same names.
    packaging {
        jniLibs {
            pickFirsts += listOf("**/libvosk.so", "**/libjnidispatch.so")
        }
        resources {
            pickFirsts += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.alekpeed.lifeos.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LifeOS"
            // Plain major.minor.build — the format both jpackage targets accept
            // (Windows MSI's ProductVersion is fussy about anything else). 0 for a
            // local build with no CI env var, same as this was hardcoded to before.
            packageVersion = "1.0.$buildNumber"
        }
    }
}

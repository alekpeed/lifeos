import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("com.google.android.gms:play-services-location:21.0.1")
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
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("com.google.zxing:core:3.5.3")
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
        versionCode = 1
        versionName = "1.0"
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
            packageVersion = "1.0.0"
        }
    }
}

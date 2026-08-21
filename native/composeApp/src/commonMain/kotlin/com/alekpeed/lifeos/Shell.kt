package com.alekpeed.lifeos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.core.runAutomations
import com.alekpeed.lifeos.insight.rearmScheduledReminders
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.interfaces.nexus.registerNexusCommandRoom
import com.alekpeed.lifeos.interfaces.nocturne.registerNocturne
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.SystemBackHandler
import com.alekpeed.lifeos.settings.LockScreen
import com.alekpeed.lifeos.settings.appLockEnabled
import com.alekpeed.lifeos.system.ScanConfirmSheet
import com.alekpeed.lifeos.timemachine.recordBirths
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

// Home launcher <-> module detail navigation.
@Composable
fun Shell() {
    val modules = remember { lifeOsModules() }
    var current by remember { mutableStateOf<Module?>(null) }
    // Same fix as HomeScreen's header: FullscreenApplication forces edge-to-edge
    // everywhere, so an ordinary module's back-arrow row has to clear the status
    // bar itself whenever it's actually showing, not assume it never is.
    val density = LocalDensity.current
    val topInset = remember(density) { with(density) { Native.statusBarTopPx().toDp() } }

    // The PIN gate, when one is set: nothing else renders until it's entered. Checked
    // before any module so a deep link can't route around it.
    var locked by remember { mutableStateOf(appLockEnabled()) }
    if (locked) {
        LockScreen { locked = false }
        return
    }

    // Make the graphical interfaces available for selection in Settings.
    remember {
        registerNexusCommandRoom()
        registerNocturne()
    }

    // Run the opt-in automation rules once on app open (no-op unless enabled).
    LaunchedEffect(Unit) { runAutomations() }

    // Note the arrival date of any record the census hasn't seen. Done at app open so a
    // record's "added on" date is the day it actually turned up, not the day the Time
    // Machine happens to get opened. Writes only when something is new.
    LaunchedEffect(Unit) { runCatching { recordBirths() } }

    // Belt-and-braces re-arm of scheduled reminders. BootReceiver handles the normal
    // reboot; some OEM battery managers never deliver that broadcast, so re-arming on
    // open too means a reminder can't quietly go missing. Idempotent — a re-arm
    // replaces the same alarm id rather than stacking another one.
    LaunchedEffect(Unit) {
        runCatching { rearmScheduledReminders(Clock.System.now().toEpochMilliseconds()) }
    }

    // A deep link / app shortcut / NFC tag / shared item can request a module by id.
    LaunchedEffect(Nav.pendingModuleId) {
        val id = Nav.consume() ?: return@LaunchedEffect
        if (id == Nav.HOME) {
            current = null
            return@LaunchedEffect
        }
        modules.firstOrNull { it.id == id }?.let { current = it }
    }

    // The "Saved" pill, shown app-wide and debounced so per-keystroke saves don't
    // flash it. LaunchedEffect(tick) restarts on each save, so it only fires once
    // typing settles.
    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(SaveToast.tick) {
        if (SaveToast.tick == 0) return@LaunchedEffect
        delay(700)
        snackHost.currentSnackbarData?.dismiss()
        snackHost.showSnackbar(SaveToast.message, duration = SnackbarDuration.Short)
    }

    Box(Modifier.fillMaxSize()) {
        val c = current
        if (c == null) {
            // An interface can supply its own home (its navigation art); otherwise
            // the built-in functional launcher.
            val customHome = Interfaces.home()
            if (customHome != null) customHome() else HomeScreen(modules) { current = it }
        } else {
            // Android edge-swipe / back button pops to Home instead of leaving the app.
            SystemBackHandler(enabled = true) { current = null }
            if (c.immersive) {
                Box(Modifier.fillMaxSize()) {
                    Interfaces.Render(c.id, c.content)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(top = maxOf(6.dp, topInset), bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackArrow { current = null }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${c.icon}  ${c.label}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Interfaces.Render(c.id, c.content)
                    }
                }
            }
        }
        // A finished scan asks where it goes, over whatever interface is active.
        ScanConfirmSheet()
        SnackbarHost(snackHost, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

// A persistent, obvious back control pinned at the top of every module screen.
@Composable
private fun BackArrow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "‹",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package com.alekpeed.lifeos.interfaces.nocturne

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.loadBase64ImageAsset
import com.alekpeed.lifeos.platform.loadImageAsset

private val OPERATIONS_ART_PARTS = listOf(
    "nocturne-operations-room-0.b64",
    "nocturne-operations-room-1.b64",
    "nocturne-operations-room-2.b64",
)

private data class RailTarget(val label: String, val glyph: String, val moduleId: String)
private data class QuickTarget(val label: String, val glyph: String, val moduleId: String)

private val railTargets = listOf(
    RailTarget("OPS", "✦", "operations"),
    RailTarget("ARCHIVE", "▤", "documents"),
    RailTarget("LOGISTICS", "⌖", "places"),
    RailTarget("DISCOVERY", "◎", "education"),
    RailTarget("MANAGE", "◇", "habits"),
    RailTarget("INTEL", "◉", "ask"),
    RailTarget("PEOPLE", "◌", "contacts"),
    RailTarget("SYSTEM", "⚙", "settings"),
)

private val quickTargets = listOf(
    QuickTarget("TODAY", "◫", "today"),
    QuickTarget("TASKS", "✓", "tasks"),
    QuickTarget("COMMAND", "⌘", "command"),
    QuickTarget("BRIEF", "▥", "briefing"),
    QuickTarget("ALERTS", "!", "notifications"),
)

/**
 * Nocturne Operations is a room, not a conventional page. The artwork is the
 * environment; every changeable datum and every tappable control is rendered as
 * a Compose overlay in fixed, documented zones so nothing important is baked into
 * the background image.
 */
@Composable
fun NocturneOperationsRoom() {
    val art = remember {
        loadBase64ImageAsset(OPERATIONS_ART_PARTS) ?: loadImageAsset("nocturne-home.png")
    }
    val modules = remember { lifeOsModules() }
    val ready = remember(modules) { modules.count { it.ready } }

    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }

    Box(Modifier.fillMaxSize().background(NocturneColors.Void)) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = "Nocturne Operations room",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Box(Modifier.fillMaxSize().background(Color(0x19000000)))

        DomainRail(
            modifier = Modifier.align(Alignment.CenterStart),
            onOpen = { Nav.open(it) },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 92.dp, end = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusStrip(ready = ready, total = modules.size)
            QuickActions(onOpen = { Nav.open(it) })
        }
    }
}

@Composable
private fun DomainRail(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    Column(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(Color(0xD906090D))
            .padding(top = 22.dp, bottom = 18.dp, start = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NocturneColors.Panel, RoundedCornerShape(10.dp))
                .clickable { Nav.open(Nav.HOME) }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("⌂  HOME", color = NocturneColors.Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        railTargets.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item.moduleId) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.glyph, color = NocturneColors.Gold, fontSize = 19.sp)
                Text(
                    item.label,
                    color = NocturneColors.Ivory,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(ready: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xDD090D11), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("OPERATIONS", color = NocturneColors.Gold, fontSize = 8.sp, letterSpacing = 1.5.sp)
            Text("SYSTEM READY", color = NocturneColors.Ivory, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("MODULES", color = NocturneColors.Muted, fontSize = 8.sp)
            Text("$ready / $total ONLINE", color = NocturneColors.Ivory, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickActions(onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xDD090D11), RoundedCornerShape(12.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        quickTargets.forEach { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xB512171C), RoundedCornerShape(9.dp))
                    .clickable { onOpen(item.moduleId) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.glyph, color = NocturneColors.Gold, fontSize = 16.sp)
                Text(item.label, color = NocturneColors.Ivory, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

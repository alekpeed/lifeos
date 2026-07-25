package com.alekpeed.lifeos.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val INK = Color(0xF20A0C11)
private val PANEL = Color(0xFF141821)
private val TEXT = Color(0xFFEDEFF2)
private val MUTED = Color(0xFF8D95A1)
private val ACCENT = Color(0xFFE0708F)
private val HAIR = Color(0x1AFFFFFF)

// Shown after a scan: what was read, where it's going, and everything it found — so you
// accept a known result instead of discovering later that it guessed wrong. Rendered
// app-wide from the Shell, so it appears over whatever interface is active.
@Composable
fun ScanConfirmSheet() {
    val p = ScanFlow.proposal ?: return
    var dest by remember(p) { mutableStateOf(p.suggested) }

    val kindLabel = when (p.kind) {
        "tasklist" -> "A list of things to do"
        "shoppinglist" -> "A shopping list"
        "recipe" -> "A recipe"
        "contact" -> "A business card"
        "book" -> "A book"
        "receipt" -> "A receipt"
        "document" -> "A document"
        "note" -> "A note"
        else -> "A scan"
    }
    val count = p.items.size

    Box(Modifier.fillMaxSize().background(INK), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(18.dp))
                .background(PANEL).border(1.dp, HAIR, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text(kindLabel.uppercase(), color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(p.title, color = TEXT, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)

            if (count > 0) {
                Text(
                    if (count == 1) "1 item" else "$count items",
                    color = MUTED, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(p.items) { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text("·", color = ACCENT, fontSize = 14.sp, modifier = Modifier.width(14.dp))
                            Text(line, color = TEXT, fontSize = 14.sp)
                        }
                    }
                }
            } else if (p.summary.isNotBlank()) {
                Text(
                    p.summary, color = TEXT, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp), maxLines = 5,
                )
            }

            Text(
                "Save to",
                color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            // Suggested destination first, then the alternatives — a wrong guess is one tap to fix.
            val options = listOf(dest) + ScanDest.values().filter { it != dest }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                items(options) { d ->
                    val on = d == dest
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Color(0x2AE0708F) else Color(0x0DFFFFFF))
                            .clickable { dest = d }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (on) "●" else "○", color = if (on) ACCENT else MUTED, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                        Text(d.label, color = if (on) TEXT else MUTED, fontSize = 14.sp)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0DFFFFFF)).clickable { ScanFlow.dismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Discard", color = MUTED, fontSize = 15.sp) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(ACCENT).clickable { commitScan(p, dest) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Save", color = Color(0xFF14121A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

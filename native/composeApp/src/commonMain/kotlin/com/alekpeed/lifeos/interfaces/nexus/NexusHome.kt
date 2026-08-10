package com.alekpeed.lifeos.interfaces.nexus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.HomeScreen
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.loadBase64ImageAsset
import com.alekpeed.lifeos.platform.loadImageAsset
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseSync
import com.alekpeed.lifeos.sync.SyncEngine
import com.alekpeed.lifeos.sync.SyncMeta
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.priorityRank
import com.alekpeed.lifeos.tasks.statusLabel
import com.alekpeed.lifeos.data.today
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val NEXUS = "nexus"
private const val ART = "nexus-home.png"
private const val CANONICAL_WIDTH = 1080f
private const val CANONICAL_HEIGHT = 2400f
private val GENERATED_ART = (0..8).map { "nexus-home-$it.b64" }
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val DOMAINS = listOf(
    "Operations",
    "Archive",
    "Logistics",
    "Discovery",
    "Management",
    "Intelligence",
    "People",
    "System",
)

private data class NexusLiveState(
    val score: String,
    val scoreCaption: String,
    val syncMain: String,
    val syncLine1: String,
    val syncLine2: String,
    val nextWhen: String,
    val nextTitle: String,
    val nextDetail: String,
)

@Composable
fun NexusHome() {
    val art = remember {
        loadBase64ImageAsset(GENERATED_ART) ?: loadImageAsset(ART)
    }
    if (art == null) {
        HomeScreen(remember { lifeOsModules() }) { Nav.open(it.id) }
        return
    }

    val modules = remember { lifeOsModules() }
    val scope = rememberCoroutineScope()
    var openDomain by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF05060A)),
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        val safeH = (viewportH - topInset - bottomInset).coerceAtLeast(1f)

        // The interaction frame is always exactly 1080x2400 in normalized geometry.
        // Device aspect-ratio differences are letterboxed; the artwork is fitted to
        // this canonical frame rather than allowed to redefine it.
        val scale = minOf(viewportW / CANONICAL_WIDTH, safeH / CANONICAL_HEIGHT)
        val drawW = CANONICAL_WIDTH * scale
        val drawH = CANONICAL_HEIGHT * scale
        val originX = (viewportW - drawW) / 2f
        val originY = topInset + (safeH - drawH) / 2f
        val density = LocalDensity.current

        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(
                    with(density) { originX.toDp() },
                    with(density) { originY.toDp() },
                )
                .size(
                    with(density) { drawW.toDp() },
                    with(density) { drawH.toDp() },
                ),
        )

        // Live text is painted over only the static text pixels in the artwork.
        // The generated design, borders, icons, wheel, glow and hit geometry stay intact.
        LiveNexusOverlay(originX, originY, drawW, drawH)

        // Invisible interaction layer. Coordinates below are the canonical JSON
        // frame values and intentionally do not alter the artwork.
        Box(
            Modifier.fillMaxSize().pointerInput(art, originX, originY, drawW, drawH) {
                detectTapGestures { tap ->
                    when (val hit = hitRegion(tap, originX, originY, drawW, drawH)) {
                        null, "core" -> Unit
                        "bell" -> Nav.open("notifications")
                        "voice" -> Nav.open("command")
                        "quick_note" -> Nav.open("ideas")
                        "camera" -> scanWithCamera(scope)
                        "barcode" -> scanCode(scope)
                        "ai_assist" -> Nav.open("ai-assistant")
                        else -> if (hit.startsWith("domain:")) {
                            openDomain = hit.removePrefix("domain:")
                        }
                    }
                }
            },
        )

        if (openDomain.isNotBlank()) {
            DomainSheet(
                domain = openDomain,
                modules = modules.filter { it.group == openDomain },
                onPick = { Nav.open(it); openDomain = "" },
                onDismiss = { openDomain = "" },
            )
        }
    }
}

@Composable
private fun LiveNexusOverlay(originX: Float, originY: Float, drawW: Float, drawH: Float) {
    var clockText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var live by remember { mutableStateOf(readLiveState(syncing = false)) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val hour = when {
                now.hour == 0 -> 12
                now.hour > 12 -> now.hour - 12
                else -> now.hour
            }
            clockText = "$hour:${now.minute.toString().padStart(2, '0')} ${if (now.hour < 12) "AM" else "PM"}"
            dateText = "${MONTHS[now.monthNumber - 1]} ${now.dayOfMonth}, ${now.year}"
            delay(1_000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            live = readLiveState(syncing)
            delay(15_000)
        }
    }

    // While this home screen is open, a signed-in device performs a real sync on
    // entry and then every five minutes. The status card reflects the actual local
    // pending count and the last successful SyncMeta timestamp.
    LaunchedEffect(Unit) {
        while (true) {
            if (SupabaseAuth.isSignedIn()) {
                syncing = true
                live = readLiveState(syncing = true)
                SupabaseSync.syncNow()
                syncing = false
                live = readLiveState(syncing = false)
            }
            delay(5 * 60 * 1_000L)
        }
    }

    val density = LocalDensity.current
    val panel = Color(0xFF08060B)
    val card = Color(0xFF09050B)
    val pink = Color(0xFFFF77AC)
    val pale = Color(0xFFFFD7E5)
    val white = Color(0xFFF9F3F6)
    val muted = Color(0xFFD5BFC8)
    val green = Color(0xFF55D36A)

    fun Modifier.artRect(x: Float, y: Float, w: Float, h: Float): Modifier =
        this.offset(
            with(density) { (originX + drawW * x).toDp() },
            with(density) { (originY + drawH * y).toDp() },
        ).size(
            with(density) { (drawW * w).toDp() },
            with(density) { (drawH * h).toDp() },
        )

    // Header clock + date. This mask sits entirely inside the header's flat dark fill.
    Column(
        modifier = Modifier.artRect(0.355f, 0.035f, 0.29f, 0.045f).background(panel),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            clockText,
            color = white,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            dateText,
            color = pink,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }

    // Life Score: today's completion across tasks due today + today's habit check-ins.
    Box(
        Modifier.artRect(0.085f, 0.771f, 0.13f, 0.035f).background(card),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            live.score,
            color = pale,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    Box(
        Modifier.artRect(0.075f, 0.813f, 0.15f, 0.026f).background(card),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            live.scoreCaption,
            color = pink,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }

    // Sync card. The cloud icon remains artwork; only its status copy is live.
    Column(
        modifier = Modifier.artRect(0.385f, 0.806f, 0.255f, 0.047f).background(card),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            live.syncMain,
            color = pink,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            live.syncLine1,
            color = muted,
            fontSize = 7.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            live.syncLine2,
            color = if (SupabaseAuth.isSignedIn() && SyncEngine.pendingCount() == 0) green else muted,
            fontSize = 7.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }

    // Up Next. The calendar glyph remains artwork; its value and labels come from Tasks.
    Box(
        Modifier.artRect(0.755f, 0.754f, 0.19f, 0.034f).background(card),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            live.nextWhen,
            color = pink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Column(
        modifier = Modifier.artRect(0.685f, 0.796f, 0.27f, 0.052f).background(card),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            live.nextTitle,
            color = pale,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            live.nextDetail,
            color = muted,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun readLiveState(syncing: Boolean): NexusLiveState {
    val now = Clock.System.now().toEpochMilliseconds()
    val today = today()
    val tasks = loadTasks()
    val habits = loadHabits()

    val dueToday = tasks.filter { it.dueDate() == today }
    val totalDaily = dueToday.size + habits.size
    val completedDaily = dueToday.count { it.done } + habits.count { it.checkedInToday }
    val score = if (totalDaily == 0) "—" else ((completedDaily * 100f) / totalDaily).roundToInt().toString()
    val scoreCaption = if (totalDaily == 0) "NO ITEMS TODAY" else "$completedDaily / $totalDaily COMPLETE"

    val signedIn = SupabaseAuth.isSignedIn()
    val pending = if (signedIn) SyncEngine.pendingCount() else 0
    val lastSync = SyncMeta.lastSyncAt
    val syncMain = when {
        syncing -> "SYNCING"
        !signedIn -> "LOCAL ONLY"
        pending == 0 -> "SYNCED"
        else -> "$pending PENDING"
    }
    val syncLine1 = when {
        !signedIn -> "Cloud sync is off"
        lastSync <= 0L -> "Not synced yet"
        else -> relativeSyncTime(now - lastSync)
    }
    val syncLine2 = when {
        !signedIn -> "Sign in from Settings"
        pending == 0 -> "All systems up to date"
        else -> "Changes waiting to sync"
    }

    val next = nextTask(tasks, today)
    val nextWhen = when (val due = next?.dueDate()) {
        null -> if (next == null) "CLEAR" else "ANYTIME"
        today -> "TODAY"
        else -> due.toString()
    }
    val nextTitle = next?.title ?: "Nothing queued"
    val nextDetail = next?.project?.ifBlank { statusLabel(next.status) } ?: "Tasks are clear"

    return NexusLiveState(
        score = score,
        scoreCaption = scoreCaption,
        syncMain = syncMain,
        syncLine1 = syncLine1,
        syncLine2 = syncLine2,
        nextWhen = nextWhen,
        nextTitle = nextTitle,
        nextDetail = nextDetail,
    )
}

private fun nextTask(tasks: List<Task>, today: kotlinx.datetime.LocalDate): Task? =
    tasks.asSequence()
        .filter { !it.done }
        .filter { it.snoozeDate()?.let { snoozed -> snoozed <= today } ?: true }
        .sortedWith(
            compareBy<Task>(
                { it.dueDate() == null },
                { it.dueDate()?.toString().orEmpty() },
                { priorityRank(it.priority) },
            ),
        )
        .firstOrNull()

private fun relativeSyncTime(ageMillis: Long): String = when {
    ageMillis < 60_000L -> "Last synced just now"
    ageMillis < 3_600_000L -> "Last synced ${ageMillis / 60_000L}m ago"
    ageMillis < 86_400_000L -> "Last synced ${ageMillis / 3_600_000L}h ago"
    else -> "Last synced ${ageMillis / 86_400_000L}d ago"
}

private fun hitRegion(
    tap: Offset,
    originX: Float,
    originY: Float,
    drawW: Float,
    drawH: Float,
): String? {
    val x = (tap.x - originX) / drawW
    val y = (tap.y - originY) / drawH
    if (x !in 0f..1f || y !in 0f..1f) return null

    if (x in 0.82f..0.92f && y in 0.035f..0.09f) return "bell"

    if (x in 0.045f..0.205f && y in 0.845f..0.955f) return "voice"
    if (x in 0.205f..0.385f && y in 0.845f..0.955f) return "quick_note"
    if (x in 0.385f..0.615f && y in 0.825f..0.965f) return "camera"
    if (x in 0.615f..0.795f && y in 0.845f..0.955f) return "barcode"
    if (x in 0.795f..0.955f && y in 0.845f..0.955f) return "ai_assist"

    val dxPx = (x - 0.5f) * drawW
    val dyPx = (y - 0.365f) * drawH
    val radiusPx = sqrt(dxPx * dxPx + dyPx * dyPx)

    val coreRadius = 0.105f * drawW
    if (radiusPx <= coreRadius) return "core"

    val innerRadius = 0.17f * drawW
    val outerRadius = 0.405f * drawW
    if (radiusPx < innerRadius || radiusPx > outerRadius) return null

    var angleDeg = atan2(dyPx, dxPx) * 180f / PI.toFloat()
    if (angleDeg < 0f) angleDeg += 360f
    val clockwiseFromTop = (angleDeg + 90f) % 360f
    val index = (((clockwiseFromTop + 22.5f) % 360f) / 45f).toInt()
    return "domain:${DOMAINS[index]}"
}

@Composable
private fun DomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE605070B))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                domain.uppercase(),
                color = Color(0xFFFF88B9),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            modules.forEach { module ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0x14FFFFFF),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onPick(module.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text(
                        "${module.icon}   ${module.label}",
                        color = Color(0xFFEDEFF2),
                        fontSize = 16.sp,
                    )
                }
            }
            Text(
                "tap anywhere to close",
                color = Color(0x8899A0AA),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

fun registerNexus() {
    com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS) { NexusHome() }
}

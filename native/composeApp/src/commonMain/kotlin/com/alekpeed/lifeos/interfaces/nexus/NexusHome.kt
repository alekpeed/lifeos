package com.alekpeed.lifeos.interfaces.nexus

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.HomeScreen
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.loadImageAsset
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import com.alekpeed.lifeos.tasks.loadTasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

// NEXUS — a graphical home for Life OS. The artwork is a single bundled image; every
// interactive area is a region mapped onto it (traced in Figma against the 852x1846
// artwork), and the values that change are printed into empty "slots" left in the art.
//
// Tapping one of the eight wheel petals opens that domain; tapping a module in the
// domain list routes through Nav, exactly like the built-in launcher. The artwork is
// fitted into the safe area (below the cutout, above the gesture lane) and every region
// goes through that same transform, so hit areas stay aligned on any screen shape.

const val NEXUS = "nexus"
private const val ART = "nexus-home.png"

// The artwork's own pixel space. All regions below are in these coordinates.
private const val ART_W = 852f
private const val ART_H = 1846f

// Wheel petals, as traced polygons (x,y pairs) in artwork space.
private val PETALS: List<Pair<String, FloatArray>> = listOf(
    "Operations" to floatArrayOf(351f,535.5f, 285.5f,390f, 285.5f,384.5f, 311f,373.5f, 329f,367.5f, 351f,362.5f, 382.5f,355.5f, 407.5f,354f, 426f,352.5f, 443f,354f, 453f,354f, 470f,355.5f, 485.5f,358f, 508f,362.5f, 524.5f,367.5f, 541f,373.5f, 556f,379f, 566f,384.5f, 566f,390f, 502f,535.5f, 483f,527f, 464f,521.5f, 448.5f,519f, 426f,518f, 404f,519f, 382.5f,523f),
    "Archive" to floatArrayOf(502f,535f, 571.5f,388f, 590f,395f, 605.5f,404f, 621.5f,415.5f, 642f,431f, 657f,445.5f, 671f,459.5f, 681.5f,472f, 695f,488.5f, 708f,508.5f, 720f,530f, 729.5f,551.5f, 586f,623.5f, 572.5f,598.5f, 554.5f,574.5f, 541.5f,562f, 526f,549f),
    "Logistics" to floatArrayOf(729f,553.5f, 588f,623f, 588f,633f, 593f,647f, 596.5f,661.5f, 599.5f,696.5f, 596.5f,727.5f, 593f,742f, 588f,757f, 584f,771f, 731f,843.5f, 739.5f,823.5f, 745f,803f, 752f,782f, 755f,757f, 759.5f,720.5f, 759.5f,696.5f, 757.5f,669.5f, 755f,647f, 752f,625f, 745f,600.5f, 737.5f,578f),
    "Discovery" to floatArrayOf(502f,860.5f, 571.5f,1007.5f, 590f,1000.5f, 605.5f,991.5f, 621.5f,980f, 642f,964.5f, 657f,950f, 671f,936f, 681.5f,923.5f, 695f,907f, 708f,887f, 720f,865.5f, 729.5f,844f, 586f,772f, 572.5f,797f, 554.5f,821f, 541.5f,833.5f, 526f,846.5f),
    "Management" to floatArrayOf(348.784f,858f, 282f,1003.5f, 282f,1009f, 308f,1020f, 326.353f,1026f, 348.784f,1031f, 380.902f,1038f, 406.392f,1039.5f, 425.255f,1041f, 442.588f,1039.5f, 452.784f,1039.5f, 470.118f,1038f, 485.922f,1035.5f, 508.863f,1031f, 525.686f,1026f, 542.51f,1020f, 557.804f,1014.5f, 568f,1009f, 568f,1003.5f, 502.745f,858f, 483.373f,866.5f, 464f,872f, 448.196f,874.5f, 425.255f,875.5f, 402.824f,874.5f, 380.902f,870.5f),
    "Intelligence" to floatArrayOf(347.5f,860.5f, 278f,1007.5f, 259.5f,1000.5f, 244f,991.5f, 228f,980f, 207.5f,964.5f, 192.5f,950f, 178.5f,936f, 168f,923.5f, 154.5f,907f, 141.5f,887f, 129.5f,865.5f, 120f,844f, 263.5f,772f, 277f,797f, 295f,821f, 308f,833.5f, 323.5f,846.5f),
    "People" to floatArrayOf(122.5f,554f, 263.5f,623.5f, 263.5f,633.5f, 258.5f,647.5f, 255f,662f, 252f,697f, 255f,728f, 258.5f,742.5f, 263.5f,757.5f, 267.5f,771.5f, 120.5f,844f, 112f,824f, 106.5f,803.5f, 99.5f,782.5f, 96.5f,757.5f, 92f,721f, 92f,697f, 94f,670f, 96.5f,647.5f, 99.5f,625.5f, 106.5f,601f, 114f,578.5f),
    "System" to floatArrayOf(352.5f,535.5f, 283f,388.5f, 264.5f,395.5f, 249f,404.5f, 233f,416f, 212.5f,431.5f, 197.5f,446f, 183.5f,460f, 173f,472.5f, 159.5f,489f, 146.5f,509f, 134.5f,530.5f, 125f,552f, 268.5f,624f, 282f,599f, 300f,575f, 313f,562.5f, 328.5f,549.5f),
)

// Rectangular tap regions: id to (x, y, w, h).
private val RECTS: List<Pair<String, FloatArray>> = listOf(
    "bell" to floatArrayOf(657f, 17f, 49f, 51f),
    "btn-voice" to floatArrayOf(60f, 1601f, 106f, 103f),
    "btn-note" to floatArrayOf(208f, 1601f, 106f, 103f),
    "btn-scandoc" to floatArrayOf(534f, 1601f, 106f, 103f),
    "btn-ai" to floatArrayOf(680f, 1601f, 106f, 103f),
)

// Elliptical tap regions: id to (cx, cy, rx, ry).
private val ELLIPSES: List<Pair<String, FloatArray>> = listOf(
    "btn-scan-center" to floatArrayOf(423.5f, 1652f, 63.5f, 68f),
    "ring" to floatArrayOf(783f, 43f, 41f, 43f),
    "core" to floatArrayOf(426f, 697f, 149f, 154f),
)

// Text slots left empty in the art: (x, y, w, h) in trace space.
private val SLOT_CLOCK = floatArrayOf(359f, 17f, 138f, 35f)
private val SLOT_DATE = floatArrayOf(359f, 52f, 138f, 35f)
private val SLOT_RING = floatArrayOf(695f, 17f, 55f, 55f)

// The Figma trace and the artwork disagree slightly: the image layer had moved (and
// scaled ~1%) between tracing and reading its position, so every mapped shape sat
// ~41px high at the top of the art and ~24px high at the bottom. Measured against
// the artwork's own pixels (the top-right ring and the scan-button ring):
//   art = trace * MAP_S + MAP_T, per axis.
private const val MAP_SX = 0.9936f
private const val MAP_TX = 1.1f
private const val MAP_SY = 0.98993f
private const val MAP_TY = 41.13f

private fun traceToArt(r: FloatArray) = floatArrayOf(
    r[0] * MAP_SX + MAP_TX, r[1] * MAP_SY + MAP_TY, r[2] * MAP_SX, r[3] * MAP_SY,
)

private val SLOT_CLOCK_ART = traceToArt(SLOT_CLOCK)
private val SLOT_DATE_ART = traceToArt(SLOT_DATE)
private val SLOT_RING_ART = traceToArt(SLOT_RING)

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

// Ray-casting point-in-polygon over the flat (x,y) pair array.
private fun inPolygon(poly: FloatArray, px: Float, py: Float): Boolean {
    var inside = false
    val n = poly.size / 2
    var j = n - 1
    for (i in 0 until n) {
        val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
        val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
        if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

// ── Light ────────────────────────────────────────────────────────────────────────
// The artwork is a still image, so everything that moves is drawn over it in the same
// coordinate space as the hit testing. Light is the whole vocabulary: a region lights up
// when you touch it, the wheel lights up petal by petal on open, the core breathes, and
// the ring fills to the real number. Nothing here changes the art — pull the overlay and
// the screen still works.

private val LIGHT = Color(0xFFE0708F) // the art's accent — glow has to look native to it
private val LIGHT_HOT = Color(0xFFFFC2D6) // the brighter core of a fresh tap

// All timing in milliseconds, measured off the frame clock.
private const val BOOT_DELAY_MS = 350f
private const val BOOT_MS = 2000f
private const val BREATH_MS = 4200f // a full breath, in and back out
private const val TAP_HOLD_MS = 210
private const val TAP_FADE_MS = 420f

// Glow is additive: layered strokes from wide-and-faint to tight-and-bright, so the edge
// reads as light bleeding off the shape rather than a flat outline sitting on top of it.
private val HALO_W = floatArrayOf(20f, 13f, 7f, 3f)
private val HALO_A = floatArrayOf(0.13f, 0.21f, 0.34f, 0.80f)

private fun DrawScope.glow(path: Path, amount: Float, scale: Float, fill: Float = 0.20f) {
    if (amount <= 0.01f) return
    val a = amount.coerceAtMost(1f)
    if (fill > 0f) {
        drawPath(path, LIGHT.copy(alpha = fill * a), blendMode = BlendMode.Plus)
    }
    for (i in HALO_W.indices) {
        drawPath(
            path,
            (if (i == HALO_W.lastIndex) LIGHT_HOT else LIGHT).copy(alpha = HALO_A[i] * a),
            style = Stroke(width = HALO_W[i] * scale),
            blendMode = BlendMode.Plus,
        )
    }
}

// Trace-space point -> screen, via the same art mapping every region uses.
private fun sx(x: Float, ox: Float, scale: Float) = ox + (x * MAP_SX + MAP_TX) * scale
private fun sy(y: Float, oy: Float, scale: Float) = oy + (y * MAP_SY + MAP_TY) * scale

private fun polyPath(poly: FloatArray, ox: Float, oy: Float, scale: Float): Path {
    val p = Path()
    for (i in 0 until poly.size / 2) {
        val x = sx(poly[i * 2], ox, scale)
        val y = sy(poly[i * 2 + 1], oy, scale)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun rectPath(r: FloatArray, ox: Float, oy: Float, scale: Float): Path {
    val a = traceToArt(r)
    val p = Path()
    p.addRoundRect(
        RoundRect(
            Rect(
                Offset(ox + a[0] * scale, oy + a[1] * scale),
                Size(a[2] * scale, a[3] * scale),
            ),
            CornerRadius(18f * scale, 18f * scale),
        ),
    )
    return p
}

private fun ovalPath(e: FloatArray, ox: Float, oy: Float, scale: Float, inset: Float = 1f): Path {
    val cx = sx(e[0], ox, scale)
    val cy = sy(e[1], oy, scale)
    val rx = e[2] * MAP_SX * scale * inset
    val ry = e[3] * MAP_SY * scale * inset
    val p = Path()
    p.addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
    return p
}

private val RING_E = ELLIPSES.first { it.first == "ring" }.second

// The top-right ring, lit clockwise from 12 o'clock to today's fraction. The art draws the
// ring itself; this is the lit part of it, so the number inside and the light agree.
private fun DrawScope.drawRingArc(frac: Float, ox: Float, oy: Float, scale: Float) {
    if (frac <= 0.005f) return
    val cx = sx(RING_E[0], ox, scale)
    val cy = sy(RING_E[1], oy, scale)
    val rx = RING_E[2] * MAP_SX * scale * 0.84f
    val ry = RING_E[3] * MAP_SY * scale * 0.84f
    val topLeft = Offset(cx - rx, cy - ry)
    val size = Size(rx * 2f, ry * 2f)
    val sweep = 360f * frac.coerceIn(0f, 1f)
    val widths = floatArrayOf(14f, 8f, 4f)
    val alphas = floatArrayOf(0.22f, 0.40f, 0.95f)
    for (i in widths.indices) {
        drawArc(
            if (i == widths.lastIndex) LIGHT_HOT else LIGHT,
            -90f, sweep, false, topLeft, size,
            alpha = alphas[i],
            style = Stroke(width = widths[i] * scale, cap = StrokeCap.Round),
            blendMode = BlendMode.Plus,
        )
    }
}

// The boot sweep: each petal gets a short window of light as the sweep passes it, so the
// wheel comes on clockwise from the top instead of all at once.
private fun sweepAt(t: Float, i: Int, n: Int): Float {
    if (t <= 0f || t >= 1f) return 0f
    val center = (i + 0.5f) / n * 0.76f
    val d = abs(t - center)
    val w = 0.16f
    return (1f - d / w).coerceAtLeast(0f)
}

@Composable
fun NexusHome() {
    val art = remember { loadImageAsset(ART) }
    if (art == null) {
        // No artwork bundled (or desktop): fall back to the functional launcher.
        HomeScreen(remember { lifeOsModules() }) { Nav.open(it.id) }
        return
    }

    val modules = remember { lifeOsModules() }
    val scope = rememberCoroutineScope()

    // The artwork has its own clock, date and status row, so the system bars would sit
    // on top of it. Go full screen while this home is showing, and restore on the way out.
    DisposableEffect(Unit) {
        Native.setImmersive(true)
        onDispose { Native.setImmersive(false) }
    }
    var openDomain by remember { mutableStateOf("") }

    // Live clock — the art leaves these two slots empty on purpose.
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    // The ring: how much of today you've actually cleared — everything due today or
    // overdue, plus today's habits. "—" when there's nothing to measure.
    var ringText by remember { mutableStateOf("") }
    // Same number as the ring text, as a 0..1 fraction, so the arc drawn around the ring
    // and the printed percentage can never disagree.
    var ringPct by remember { mutableStateOf(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val h12 = when {
                    now.hour == 0 -> 12
                    now.hour > 12 -> now.hour - 12
                    else -> now.hour
                }
                val mm = now.minute.toString().padStart(2, '0')
                clock = "$h12:$mm ${if (now.hour < 12) "AM" else "PM"}"
                date = "${MONTHS[now.monthNumber - 1]} ${now.dayOfMonth}, ${now.year}"
            }
            // Read the day's real state on the same beat as the clock. Guarded: a bad
            // record must never keep the home from drawing.
            runCatching {
                val allTasks = loadTasks()
                val due = allTasks.filter { !it.done }
                    .count { val d = it.dueDate(); d != null && d <= today() }
                val doneToday = allTasks.count { it.done && it.completedDate == today().toString() }
                val habits = loadHabits()
                val habitsDone = habits.count { it.checkedInToday }
                val total = due + doneToday + habits.size
                val cleared = doneToday + habitsDone
                ringText = if (total <= 0) "—" else "${cleared * 100 / total}%"
                ringPct = if (total <= 0) -1f else (cleared.toFloat() / total).coerceIn(0f, 1f)
            }
            delay(20_000)
        }
    }

    // Milliseconds since this home appeared, ticked from the raw frame callback.
    //
    // Every light effect is computed from this number instead of from Compose's animation
    // API on purpose: animateTo and infiniteRepeatable are multiplied by the OS animator
    // duration scale, so on a device with animations switched off they jump straight to
    // their end value — the sweep is over before the first frame, the breath is pinned,
    // the ring snaps. A tap still lights up, because setting a value isn't an animation.
    // Frame callbacks are not scaled, so time-driven light runs either way. Reading the
    // clock here, in composition, is also what forces the canvas to redraw each frame.
    val clock = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) {
            withFrameNanos { t -> clock.value = (t - t0) / 1_000_000f }
        }
    }
    val nowMs = clock.value

    // Tap light: which region was hit, and when. Held in MutableState (not a delegated var)
    // because the gesture lambda is remembered — it has to read the live clock, not the
    // value from the composition that created it.
    val litId = remember { mutableStateOf("") }
    val litAt = remember { mutableStateOf(-1e9f) }

    // The wheel lighting up petal by petal, once, held off a moment so it isn't spent
    // behind the launch animation.
    val bootT = ((nowMs - BOOT_DELAY_MS) / BOOT_MS).coerceIn(0f, 1f)

    // The core breathing: a cosine so it eases at both ends instead of ping-ponging.
    val breathT = run {
        val p = (nowMs % BREATH_MS) / BREATH_MS
        0.22f + 0.58f * (0.5f - 0.5f * cos(2f * PI.toFloat() * p))
    }

    // The ring counts up to today's real number rather than appearing at it.
    val ringT = ringPct.coerceAtLeast(0f) * ((nowMs - 500f) / 1100f).coerceIn(0f, 1f)

    // Tap: full brightness while the screen changes underneath, then fade.
    val litNow = litId.value
    val litAge = nowMs - litAt.value
    val litT = when {
        litNow.isEmpty() -> 0f
        litAge < TAP_HOLD_MS -> 1f
        else -> (1f - (litAge - TAP_HOLD_MS) / TAP_FADE_MS).coerceAtLeast(0f)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF07080C))) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        // The artwork carries its own status row at its very top, so it must clear the
        // display cutout: fill the width, TOP-ALIGN just below the punch hole, and let
        // any overflow crop off the bottom (empty dais margin) — never the top. No
        // vertical centering, so nothing drifts with screen shape. Re-read each
        // recomposition: insets can report 0 on the very first frame.
        val topInset = Native.cutoutTopPx().toFloat()
        val bottomInset = Native.navBottomPx().toFloat()
        // Fit the WHOLE artwork between the punch hole and the system gesture lane, so
        // the bottom action bar sits clear of the home swipe and the top row stays clear
        // of the camera. Fitting (not filling) means a thin letterbox at the sides — the
        // art's edges are near-black, so it reads as bezel rather than a gap.
        val safeH = (vh - topInset - bottomInset).coerceAtLeast(1f)
        val scale = minOf(vw / ART_W, safeH / ART_H)
        val ox = (vw - ART_W * scale) / 2f
        val oy = topInset + (safeH - ART_H * scale) / 2f
        val density = LocalDensity.current

        // Every lightable region as a screen-space path, built from the same mapped shapes
        // the taps test against — so what lights up is exactly what you pressed. Rebuilt
        // only when the fit changes (rotation, insets settling), not per frame.
        val paths = remember(scale, ox, oy) {
            buildMap<String, Path> {
                PETALS.forEach { (id, poly) -> put(DOMAIN_PREFIX + id, polyPath(poly, ox, oy, scale)) }
                RECTS.forEach { (id, r) -> put(id, rectPath(r, ox, oy, scale)) }
                ELLIPSES.forEach { (id, e) -> put(id, ovalPath(e, ox, oy, scale, inset = 0.94f)) }
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(scale, ox, oy) {
                detectTapGestures { tap ->
                    val hit = regionAt(tap, ox, oy, scale) ?: return@detectTapGestures
                    // The ring and the core are readouts, not buttons — don't light them
                    // on touch and promise something that isn't there.
                    if (hit == "ring" || hit == "core") return@detectTapGestures
                    litId.value = hit
                    litAt.value = clock.value
                    scope.launch {
                        // Let the light actually land before the screen changes underneath
                        // it — a button that opens a module is gone too fast to see at 70ms.
                        delay(TAP_HOLD_MS.toLong())
                        if (hit.startsWith(DOMAIN_PREFIX)) {
                            openDomain = hit.removePrefix(DOMAIN_PREFIX)
                        } else {
                            act(hit, scope)
                        }
                    }
                }
            },
        ) {
            // Drawn with the exact same transform the hit testing uses, so the tap
            // zones can never drift from the pixels.
            drawImage(
                image = art,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(art.width, art.height),
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize((ART_W * scale).roundToInt(), (ART_H * scale).roundToInt()),
            )

            // Opening sweep: light runs clockwise around the wheel once, then stops.
            if (bootT < 1f) {
                PETALS.forEachIndexed { i, (id, _) ->
                    val a = sweepAt(bootT, i, PETALS.size)
                    if (a > 0.01f) paths[DOMAIN_PREFIX + id]?.let { glow(it, a, scale, fill = 0.16f) }
                }
            }

            // The core breathing, always.
            paths["core"]?.let { glow(it, breathT, scale, fill = 0.09f) }

            // The ring: filling to today's real number, or breathing on standby when there
            // is nothing to measure yet, so it never reads as a dead circle.
            if (ringPct >= 0f) {
                drawRingArc(ringT, ox, oy, scale)
            } else {
                paths["ring"]?.let { glow(it, breathT * 0.55f, scale, fill = 0f) }
            }

            // Whatever you just pressed, brightest and on top.
            if (litNow.isNotEmpty()) paths[litNow]?.let { glow(it, litT, scale, fill = 0.30f) }
        }

        // Live values printed into the slots the art leaves empty.
        SlotText(clock, SLOT_CLOCK_ART, ox, oy, scale, density, Color(0xFFF2F4F6), 26f, FontWeight.Medium)
        SlotText(date, SLOT_DATE_ART, ox, oy, scale, density, Color(0xFFE0708F), 19f, FontWeight.Normal)
        SlotText(ringText, SLOT_RING_ART, ox, oy, scale, density, Color(0xFFF2F4F6), 21f, FontWeight.SemiBold)

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

private const val DOMAIN_PREFIX = "domain:"

// Screen point -> which mapped region it landed in, or null. Converts screen -> artwork
// px -> trace space (undoing the trace's offset and ~1% scale) so every mapped region
// tests unchanged. Shared by tap and long-press so they can never disagree.
private fun regionAt(
    tap: androidx.compose.ui.geometry.Offset,
    ox: Float,
    oy: Float,
    scale: Float,
): String? {
    val ax = ((tap.x - ox) / scale - MAP_TX) / MAP_SX
    val ay = ((tap.y - oy) / scale - MAP_TY) / MAP_SY
    PETALS.firstOrNull { inPolygon(it.second, ax, ay) }?.let { return DOMAIN_PREFIX + it.first }
    for ((id, e) in ELLIPSES) {
        val nx = (ax - e[0]) / e[2]
        val ny = (ay - e[1]) / e[3]
        if (nx * nx + ny * ny <= 1f) return id
    }
    for ((id, r) in RECTS) {
        if (ax >= r[0] && ax <= r[0] + r[2] && ay >= r[1] && ay <= r[1] + r[3]) return id
    }
    return null
}

// Where a non-petal region goes. The wheel's center is left for a future easter egg.
private fun act(id: String, scope: kotlinx.coroutines.CoroutineScope) {
    when (id) {
        "bell" -> Nav.open("notifications")
        "btn-voice" -> Nav.open("command")
        "btn-note" -> Nav.open("ideas")
        // SCAN DOC: the precise scanner — decodes a QR or barcode exactly and files it.
        "btn-scandoc" -> scanCode(scope)
        "btn-ai" -> Nav.open("ai-assistant")
        // The big round button IS the camera: shoot anything, and Life OS reads what is
        // on it and proposes where it goes.
        "btn-scan-center" -> scanWithCamera(scope)
        else -> {} // ring, core: not wired yet
    }
}

@Composable
private fun SlotText(
    text: String,
    slot: FloatArray,
    ox: Float,
    oy: Float,
    scale: Float,
    density: androidx.compose.ui.unit.Density,
    color: Color,
    designSize: Float,
    weight: FontWeight,
) {
    if (text.isBlank()) return
    // Give the text room either side of the slot and center it on the slot's middle, so a
    // long value (a full date) is never clipped by the traced box.
    val pad = slot[2] * 1.2f
    with(density) {
        Text(
            text,
            color = color,
            fontSize = (designSize * scale).toSp(),
            fontWeight = weight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .offset(
                    x = (ox + (slot[0] - pad / 2f) * scale).toDp(),
                    y = (oy + slot[1] * scale).toDp(),
                )
                .width(((slot[2] + pad) * scale).toDp()),
        )
    }
}

// The modules inside a tapped domain. Deliberately plain — the artwork carries the
// look; this is the functional list that gets you into a module.
@Composable
private fun DomainSheet(
    domain: String,
    modules: List<com.alekpeed.lifeos.Module>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE605070B)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                domain.uppercase(),
                color = Color(0xFFE0708F),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            modules.forEach { m ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .clickable { onPick(m.id) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    Text("${m.icon}   ${m.label}", color = Color(0xFFEDEFF2), fontSize = 16.sp)
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

// Register NEXUS as an available interface. Idempotent; called on app open.
fun registerNexus() {
    com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS) { NexusHome() }
}

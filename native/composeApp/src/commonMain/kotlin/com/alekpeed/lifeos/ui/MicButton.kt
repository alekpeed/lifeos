package com.alekpeed.lifeos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.ai.Whisper
import com.alekpeed.lifeos.platform.Native
import kotlinx.coroutines.launch

// The mic, wherever text gets typed. One composable, so every field behaves the same
// and none of them has to know which engine is doing the listening.
//
// Two paths, decided per device:
//   * Whisper — hold the mic open until you say you're done, then transcribe. The
//     default anywhere it's possible, and on desktop it's the only dictation there is.
//   * The system recognizer — Android's own dialog, used when Whisper is switched off
//     in Settings or there's no OpenAI key. Offline and free, but it cuts you off at
//     the first pause and won't punctuate.
//
// Nothing renders at all where neither is available, so a caller can drop this in
// without guarding it.

private const val PHASE_LISTENING = "listening"
private const val PHASE_WORKING = "working"
private const val PHASE_FAILED = "failed"
private const val PHASE_DENIED = "denied"

@Composable
fun MicButton(
    label: String = "🎤 Dictate",
    hint: String = "",
    onText: (String) -> Unit,
) {
    val useWhisper = Whisper.enabled()
    if (!useWhisper && !Native.supportsDictation) return

    var sheet by remember { mutableStateOf(false) }

    TextButton(onClick = {
        if (useWhisper) sheet = true
        else Native.dictate { spoken -> if (!spoken.isNullOrBlank()) onText(spoken) }
    }) { Text(label) }

    if (sheet) DictateSheet(hint = hint, onClose = { sheet = false }, onText = onText)
}

// A compact variant for a toolbar, where a labelled button doesn't fit.
@Composable
fun MicIconButton(hint: String = "", onText: (String) -> Unit) = MicButton("🎤", hint, onText)

// The listening panel: opens the mic, shows that it's hearing you, and stops only when
// you say so — a pause mid-sentence is a pause, not the end of the take.
@Composable
private fun DictateSheet(hint: String, onClose: () -> Unit, onText: (String) -> Unit) {
    var phase by remember { mutableStateOf(PHASE_LISTENING) }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }
    var seconds by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!Native.startRecording()) phase = PHASE_DENIED
    }

    // Frame-driven rather than an animation: the meter has to keep moving with the
    // device's animation scale turned off, and it's reading a value that changes
    // outside composition anyway.
    LaunchedEffect(phase) {
        if (phase != PHASE_LISTENING) return@LaunchedEffect
        val t0 = withFrameNanos { it }
        while (true) {
            withFrameNanos { t ->
                level = Native.micLevel()
                seconds = ((t - t0) / 1_000_000_000L).toInt()
            }
        }
    }

    fun finish() {
        val wav = Native.stopRecording()
        if (wav == null) {
            // Too short to be speech — say so and keep listening rather than closing.
            error = "Didn't catch that. Try again — the mic is still on."
            if (!Native.startRecording()) phase = PHASE_DENIED
            return
        }
        error = null
        phase = PHASE_WORKING
        scope.launch {
            Whisper.transcribe(wav, hint)
                .onSuccess { text -> onText(text); onClose() }
                .onFailure { e ->
                    error = e.message ?: "Transcription failed."
                    phase = PHASE_FAILED
                }
        }
    }

    fun bail() {
        Native.cancelRecording()
        onClose()
    }

    AlertDialog(
        onDismissRequest = { bail() },
        title = {
            Text(
                when (phase) {
                    PHASE_WORKING -> "Transcribing…"
                    PHASE_DENIED -> "No microphone"
                    PHASE_FAILED -> "Couldn't transcribe"
                    else -> "Listening"
                },
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when (phase) {
                    PHASE_DENIED -> Text(
                        "The app couldn't open the microphone. On a phone, allow the mic " +
                            "permission and tap the mic again; on a computer, check that an " +
                            "input device is connected and selected.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PHASE_WORKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Sending the recording…", style = MaterialTheme.typography.bodyMedium)
                    }
                    PHASE_FAILED -> Text(
                        "The recording didn't come back as text.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {
                        Text(
                            "Talk for as long as you like — it won't cut you off. Tap Done when you've finished.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        LevelMeter(level)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (phase) {
                PHASE_LISTENING -> TextButton(onClick = { finish() }) { Text("Done") }
                PHASE_WORKING -> Unit
                else -> TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = {
            if (phase == PHASE_LISTENING) TextButton(onClick = { bail() }) { Text("Cancel") }
        },
    )
}

// A row of bars that rises with your voice — the only honest signal that the mic is
// picking you up rather than sitting mute.
@Composable
private fun LevelMeter(level: Float) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val bars = 24
        val gap = 3f
        val w = (size.width - gap * (bars - 1)) / bars
        if (w <= 0f) return@Canvas
        // A little compression, so ordinary speech uses most of the meter instead of
        // hugging the bottom of it.
        val lit = (level * 2.4f).coerceIn(0f, 1f) * bars
        for (i in 0 until bars) {
            val frac = (i + 1) / bars.toFloat()
            val h = size.height * (0.25f + 0.75f * frac)
            drawRect(
                color = if (i < lit) on else off,
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
            )
        }
    }
}

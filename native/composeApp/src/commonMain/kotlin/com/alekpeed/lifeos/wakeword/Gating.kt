package com.alekpeed.lifeos.wakeword

import com.alekpeed.lifeos.Storage
import kotlinx.datetime.LocalTime

// When the wake word is allowed to listen (§7 D-2).
//
// The constraint the decision set is that battery cost must not materially exceed the
// system assistant's, and the reason that is hard is worth restating where the code
// lives: Google runs hotword detection on a dedicated low-power DSP core in the audio
// hardware and the CPU stays asleep until the phrase hits. No third-party app can
// reach that DSP. Any app-level wake word is CPU-side by definition, so parity is
// reachable only by gating WHEN it listens — never by optimising HOW.
//
// Hence this file. The policy is pure and lives in commonMain: it is the only part of
// a microphone service that can be tested without a device, and it is the part where
// being wrong costs a day of battery rather than a crash.
//
// The other levers, for the record, since two of them turned out to need no work:
//   · Smallest viable model — already vosk-model-small-en-us-0.15 at 16 kHz, which is
//     the smallest English model Vosk publishes and the rate it is trained at.
//   · Release the wake lock between detections — there is no wake lock to release; the
//     service never took one. The foreground service is what keeps the process alive.
//   · Speaker ID off the always-on path — the x-vector is computed per decoded speech
//     segment, so it costs nothing while nothing is being said, and now costs nothing
//     at all while a gate is closed, because the decoder is stopped rather than idling.

// What has to be true of the device before it listens at all.
enum class WakePower {
    // The cheapest, and the case D-2 names: hands-free while cooking, or in a car dock.
    CHARGING,

    // Also listen while you are using the phone. The screen being on means the CPU is
    // already awake and the display is costing more than the microphone.
    CHARGING_OR_SCREEN,

    // Ungated. D-2 is explicit that this costs more than the system assistant and that
    // this is a hardware limit, not an implementation defect — so it stays available
    // and stays labelled.
    ALWAYS,
}

data class WakeGates(
    val power: WakePower = WakePower.CHARGING_OR_SCREEN,
    val hoursEnabled: Boolean = true,
    val from: LocalTime = LocalTime(7, 0),
    val until: LocalTime = LocalTime(22, 0),
)

data class DeviceState(val charging: Boolean, val screenOn: Boolean)

// Why it is not listening, when it is not.
enum class WakeBlock { NONE, POWER, HOURS }

data class WakeDecision(
    val listening: Boolean,
    val block: WakeBlock,
    // When the hours gate next opens, for a notification that says so rather than
    // leaving "Life OS is listening" up over a microphone that is closed.
    val opensAt: LocalTime? = null,
)

// Inclusive of `from`, exclusive of `until`, and it wraps: 22:00–07:00 is a night
// window, not an empty one. A zero-width window is read as all day rather than as
// never, because "07:00 to 07:00" is nobody's way of turning listening off — the
// switch above it is.
fun inWindow(from: LocalTime, until: LocalTime, now: LocalTime): Boolean = when {
    from == until -> true
    from < until -> now >= from && now < until
    else -> now >= from || now < until
}

fun wakeDecision(gates: WakeGates, state: DeviceState, now: LocalTime): WakeDecision {
    // Hours first, so a phone charging overnight reports the honest reason.
    if (gates.hoursEnabled && !inWindow(gates.from, gates.until, now)) {
        return WakeDecision(false, WakeBlock.HOURS, gates.from)
    }
    val powered = when (gates.power) {
        WakePower.ALWAYS -> true
        WakePower.CHARGING -> state.charging
        WakePower.CHARGING_OR_SCREEN -> state.charging || state.screenOn
    }
    return if (powered) WakeDecision(true, WakeBlock.NONE) else WakeDecision(false, WakeBlock.POWER)
}

// The next moment the hours gate flips, so the service can set one alarm instead of
// waking up to check. Null when hours are off — then only power events move anything,
// and those arrive as broadcasts.
fun nextHoursFlip(gates: WakeGates, now: LocalTime): LocalTime? {
    if (!gates.hoursEnabled || gates.from == gates.until) return null
    return if (inWindow(gates.from, gates.until, now)) gates.until else gates.from
}

fun blockReason(d: WakeDecision): String = when (d.block) {
    WakeBlock.NONE -> ""
    WakeBlock.POWER -> "Paused — plug in or wake the screen"
    WakeBlock.HOURS -> "Paused until ${d.opensAt?.let { timeText(it) } ?: "morning"}"
}

private fun timeText(t: LocalTime): String =
    "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

// ---- stored settings ------------------------------------------------------------

private const val K_POWER = "WakeGatePower"
private const val K_HOURS = "WakeGateHours"
private const val K_FROM = "WakeGateFrom"
private const val K_UNTIL = "WakeGateUntil"

private fun parseTime(raw: String?, fallback: LocalTime): LocalTime {
    val parts = raw?.trim()?.split(":") ?: return fallback
    if (parts.size != 2) return fallback
    val h = parts[0].toIntOrNull() ?: return fallback
    val m = parts[1].toIntOrNull() ?: return fallback
    if (h !in 0..23 || m !in 0..59) return fallback
    return LocalTime(h, m)
}

fun loadWakeGates(): WakeGates {
    val defaults = WakeGates()
    val power = when (Storage.read(K_POWER)?.trim()) {
        "charging" -> WakePower.CHARGING
        "always" -> WakePower.ALWAYS
        "screen" -> WakePower.CHARGING_OR_SCREEN
        else -> defaults.power
    }
    return WakeGates(
        power = power,
        // Absent means the default, not off: a phone that has never opened this screen
        // should still get the gating, which is the whole point of D-2.
        hoursEnabled = Storage.read(K_HOURS)?.trim()?.let { it == "1" } ?: defaults.hoursEnabled,
        from = parseTime(Storage.read(K_FROM), defaults.from),
        until = parseTime(Storage.read(K_UNTIL), defaults.until),
    )
}

fun saveWakeGates(g: WakeGates) {
    Storage.write(
        K_POWER,
        when (g.power) {
            WakePower.CHARGING -> "charging"
            WakePower.CHARGING_OR_SCREEN -> "screen"
            WakePower.ALWAYS -> "always"
        },
    )
    Storage.write(K_HOURS, if (g.hoursEnabled) "1" else "0")
    Storage.write(K_FROM, timeText(g.from))
    Storage.write(K_UNTIL, timeText(g.until))
}

fun wakePowerLabel(p: WakePower): String = when (p) {
    WakePower.CHARGING -> "Only while charging"
    WakePower.CHARGING_OR_SCREEN -> "Charging or screen on"
    WakePower.ALWAYS -> "Always"
}

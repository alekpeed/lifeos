package com.alekpeed.lifeos.home

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The Home module (§13.3 · FUTURE_FEATURES §14): every light and switch in the house on
// one list, whatever radio it came with.
//
// **The architecture decision is not to build N vendor integrations.** Alek's gear is
// three incompatible worlds — Tuya Wi-Fi, a walled-off DayBetter cloud, and BrMesh
// lights that are Bluetooth broadcasts with no cloud at all — and nothing this app could
// write would unify them by talking to each directly. Home Assistant already does: it
// speaks all three to the hardware and one REST API to us. So this module knows about
// exactly one thing, and it is not a light bulb.
//
// The transport wall that made this awkward is gone. A browser PWA served over HTTPS
// cannot reach a local `http://` hub — mixed content, and CORS on top. A native app has
// neither problem, which is what moved this from "needs a cloud relay" to "reads one
// endpoint". Android still blocks cleartext by default, so the manifest opts in; the
// note there says why and what it costs.
//
// What is Alek's to stand up, not the repo's: the Home Assistant box itself, the ESP32
// bridge for the BrMesh lights, and the remote-access choice (Tailscale or Nabu Casa).
// Until a hub answers, this module says so plainly rather than pretending.

private const val K_URL = "HomeUrl"
private const val K_TOKEN = "HomeToken"
private const val K_FAVOURITES = "HomeFavourites"
private const val K_ARRIVAL = "HomeArrivalScene"

data class HomeConfig(val baseUrl: String, val token: String) {
    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

fun loadHomeConfig(): HomeConfig =
    HomeConfig(Storage.read(K_URL)?.trim().orEmpty(), Storage.read(K_TOKEN)?.trim().orEmpty())

fun saveHomeConfig(baseUrl: String, token: String) {
    Storage.write(K_URL, normalizeBaseUrl(baseUrl))
    Storage.write(K_TOKEN, token.trim())
}

// What people actually type is "192.168.1.40:8123" or "homeassistant.local", pasted
// with a trailing slash, or the full URL of the page they were looking at. All of those
// mean the same hub, and refusing three of them would be the app being difficult about
// something it can work out.
fun normalizeBaseUrl(raw: String): String {
    var t = raw.trim()
    if (t.isEmpty()) return ""
    if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) {
        t = "http://$t"
    }
    t = t.trimEnd('/')
    // Pasting the API root, or a page inside the UI, still names the hub.
    listOf("/api", "/lovelace", "/config").forEach { suffix ->
        if (t.endsWith(suffix, ignoreCase = true)) t = t.dropLast(suffix.length).trimEnd('/')
    }
    // A bare host with no port is almost always the default install — but only over
    // http, which is the local-hub case. An https address is a Nabu Casa or Tailscale
    // hostname on 443, and appending 8123 to one breaks the only route that works when
    // you are not at home.
    val afterScheme = t.substringAfter("://")
    if (t.startsWith("http://", ignoreCase = true) && !afterScheme.contains(':') && !afterScheme.contains('/')) {
        t = "$t:8123"
    }
    return t
}

// ---- what the hub tells us -------------------------------------------------------

data class HomeEntity(
    val entityId: String,
    val name: String,
    val state: String,
    // 0–100 where the entity reports one, null where brightness is not a thing it has.
    val brightness: Int? = null,
    val unit: String = "",
) {
    val domain: String get() = entityId.substringBefore('.')
    val on: Boolean get() = state.equals("on", ignoreCase = true) || state.equals("open", ignoreCase = true) ||
        state.equals("unlocked", ignoreCase = true) || state.equals("playing", ignoreCase = true)

    // A hub that has lost sight of a device reports it rather than dropping it, and the
    // difference between "off" and "not answering" is the whole point of looking.
    val unavailable: Boolean get() = state == "unavailable" || state == "unknown"
}

// Domains this module will switch. Deliberately short: an entity it cannot act on is
// still worth showing (a sensor, a battery level), but offering a toggle that does
// nothing is worse than offering none.
val CONTROLLABLE = setOf("light", "switch", "fan", "input_boolean", "cover", "lock", "media_player")

// Domains that are a single button rather than a state — pressing one runs something.
val RUNNABLE = setOf("scene", "script", "automation")

// Which service call flips this entity, given where it should end up. Locks and covers
// have their own verbs; everything else answers to turn_on / turn_off.
fun serviceFor(entityId: String, on: Boolean): Pair<String, String>? {
    val domain = entityId.substringBefore('.')
    return when {
        domain in RUNNABLE -> domain to "turn_on"
        domain == "lock" -> domain to if (on) "unlock" else "lock"
        domain == "cover" -> domain to if (on) "open_cover" else "close_cover"
        domain == "media_player" -> domain to if (on) "media_play" else "media_pause"
        domain in CONTROLLABLE -> "homeassistant" to if (on) "turn_on" else "turn_off"
        else -> null
    }
}

private val json = Json { ignoreUnknownKeys = true }

// HA's /api/states, which is an array of entities with everything the hub knows about
// each one. Written defensively: this is somebody's home network answering, and one
// malformed entity must not cost the rest of the list.
fun parseStates(raw: String): List<HomeEntity> {
    val root = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
    return root.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o["entity_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (id.isEmpty() || !id.contains('.')) return@mapNotNull null
        val attrs = o["attributes"] as? JsonObject
        val friendly = attrs?.get("friendly_name")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        HomeEntity(
            entityId = id,
            // Falling back to the entity id rather than to "(unnamed)": "light.porch" is
            // still recognisable, and a list of "(unnamed)" is not a list.
            name = friendly.ifBlank { prettyId(id) },
            state = o["state"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            brightness = attrs?.get("brightness")?.jsonPrimitive?.doubleOrNull?.let {
                // HA reports 0–255; a percentage is what a person sets.
                ((it / 255.0) * 100).toInt().coerceIn(0, 100)
            },
            unit = attrs?.get("unit_of_measurement")?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}

// Sentence case, not title case: this stands in for a name somebody would have written,
// and "Hall Lamp" reads like a headline where "Hall lamp" reads like a lamp.
fun prettyId(entityId: String): String =
    entityId.substringAfter('.').replace('_', ' ').trim()
        .split(" ").filter { it.isNotBlank() }.joinToString(" ")
        .replaceFirstChar { it.uppercase() }

// What to show, in the order it is worth seeing: favourites first, then the things you
// can act on, then everything else, alphabetical inside each band. A house has hundreds
// of entities and four you touch.
fun ordered(entities: List<HomeEntity>, favourites: Set<String>): List<HomeEntity> =
    entities.sortedWith(
        compareBy(
            { it.entityId !in favourites },
            { it.domain !in CONTROLLABLE && it.domain !in RUNNABLE },
            { it.name.lowercase() },
        ),
    )

// ---- favourites and the arrival hook ---------------------------------------------

fun favourites(): Set<String> =
    Storage.read(K_FAVOURITES)?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

fun toggleFavourite(entityId: String) {
    val now = favourites()
    val next = if (entityId in now) now - entityId else now + entityId
    Storage.write(K_FAVOURITES, next.joinToString("\n"))
}

// The scene to run when an arrival geofence fires (§13.3: "ties into arrival
// geofences"). Blank means the geofence keeps doing exactly what it did before, which
// is the right default for something that turns lights on in a house.
fun arrivalScene(): String = Storage.read(K_ARRIVAL)?.trim().orEmpty()

fun setArrivalScene(entityId: String) {
    Storage.write(K_ARRIVAL, entityId.trim())
}

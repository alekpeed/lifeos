package com.alekpeed.lifeos.home

import com.alekpeed.lifeos.net.NetResponse
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpPostJson

// The I/O half: four calls against Home Assistant's REST API.
//
// One endpoint for everything on the list, one for acting on any of it. That is the
// dividend of bridging through a hub — there is no Tuya code here, no Bluetooth, no
// vendor cloud, because HA is holding all of that on the other side.
//
// Everything returns a Result-ish shape rather than throwing: this talks to a machine in
// somebody's house over a home network, and "the hub is asleep" is an ordinary Tuesday
// rather than an exception.

private fun headers(cfg: HomeConfig) = mapOf(
    "Authorization" to "Bearer ${cfg.token}",
    "Content-Type" to "application/json",
)

// What went wrong, in words that say what to do about it. A raw status code on a screen
// about your lights is a support ticket to yourself.
fun explain(res: NetResponse): String = when {
    res.status == -1 -> "Couldn't reach the hub. Check the address, and that you're on the same network or VPN."
    res.status == 401 -> "The hub refused the token. Create a long-lived access token in Home Assistant and paste it again."
    res.status == 404 -> "Reached something, but it isn't a Home Assistant API. Check the address."
    res.ok -> ""
    else -> "The hub answered ${res.status}."
}

data class HomeResult<T>(val value: T?, val error: String) {
    val ok: Boolean get() = error.isEmpty()
}

// Is anything there, and does it accept the token? The setup screen's Test button, and
// worth its own call: "no entities" and "wrong token" look identical from a list.
suspend fun homePing(cfg: HomeConfig = loadHomeConfig()): String {
    if (!cfg.configured) return "Add the hub's address and a long-lived token first."
    val res = httpGet("${cfg.baseUrl}/api/", headers(cfg))
    return explain(res)
}

suspend fun homeStates(cfg: HomeConfig = loadHomeConfig()): HomeResult<List<HomeEntity>> {
    if (!cfg.configured) return HomeResult(null, "Add the hub's address and a long-lived token first.")
    val res = httpGet("${cfg.baseUrl}/api/states", headers(cfg))
    if (!res.ok) return HomeResult(null, explain(res))
    return HomeResult(parseStates(res.body), "")
}

// Flip one entity. `homeassistant.turn_on` handles lights, switches and fans alike,
// which is why most domains resolve to it rather than to their own service.
suspend fun homeToggle(entityId: String, on: Boolean, cfg: HomeConfig = loadHomeConfig()): String {
    if (!cfg.configured) return "Not connected to a hub."
    val (domain, service) = serviceFor(entityId, on) ?: return "Nothing to switch on that one."
    val res = httpPostJson(
        "${cfg.baseUrl}/api/services/$domain/$service",
        headers(cfg),
        """{"entity_id":"${entityId.replace("\"", "")}"}""",
    )
    return explain(res)
}

// Dim a light. Sent as a percentage rather than 0–255, because HA accepts it and every
// conversion between the two is a place to be off by one.
suspend fun homeBrightness(entityId: String, percent: Int, cfg: HomeConfig = loadHomeConfig()): String {
    if (!cfg.configured) return "Not connected to a hub."
    val pct = percent.coerceIn(0, 100)
    // Zero is off, not a very dim light — turn_on with 0% is a no-op on most hardware.
    if (pct == 0) return homeToggle(entityId, on = false, cfg = cfg)
    val res = httpPostJson(
        "${cfg.baseUrl}/api/services/light/turn_on",
        headers(cfg),
        """{"entity_id":"${entityId.replace("\"", "")}","brightness_pct":$pct}""",
    )
    return explain(res)
}

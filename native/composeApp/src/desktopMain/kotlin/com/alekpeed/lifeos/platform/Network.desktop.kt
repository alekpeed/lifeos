package com.alekpeed.lifeos.platform

import java.net.NetworkInterface

// Desktop has no metered-connection concept to consult, and a laptop on a tethered
// phone is rare enough that guessing METERED would just block syncing for everyone
// else. Any live non-loopback interface counts as unmetered.
actual fun networkKind(): NetworkKind = try {
    val up = NetworkInterface.getNetworkInterfaces().toList().any { ni ->
        ni.isUp && !ni.isLoopback && ni.inetAddresses.hasMoreElements()
    }
    if (up) NetworkKind.UNMETERED else NetworkKind.NONE
} catch (e: Exception) {
    // Unknown beats blocked: a failed probe should not silently stop syncing.
    NetworkKind.UNMETERED
}

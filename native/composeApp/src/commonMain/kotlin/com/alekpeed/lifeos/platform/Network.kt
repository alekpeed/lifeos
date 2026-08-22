package com.alekpeed.lifeos.platform

// What kind of connection this device has right now.
//
// Auto-sync needs the metered/unmetered distinction, not just online/offline: syncing
// every edit is cheap on wifi and expensive on a roaming SIM, which is exactly where
// the data most needs to leave the device.
enum class NetworkKind {
    NONE,       // no usable connection
    UNMETERED,  // wifi or ethernet
    METERED,    // mobile data, or a hotspot the OS has flagged as metered
}

expect fun networkKind(): NetworkKind

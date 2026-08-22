package com.alekpeed.lifeos.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

actual fun networkKind(): NetworkKind = try {
    val ctx = NativeHost.ctx()
    val cm = ctx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    when {
        caps == null -> NetworkKind.NONE
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.NONE
        // NOT_METERED is the OS's own answer, so a phone hotspot the user marked as
        // metered is treated as mobile data even though it presents as wifi.
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.UNMETERED
        else -> NetworkKind.METERED
    }
} catch (e: Exception) {
    NetworkKind.NONE
}

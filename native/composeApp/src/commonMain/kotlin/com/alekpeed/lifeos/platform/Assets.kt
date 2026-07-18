package com.alekpeed.lifeos.platform

// Read a bundled text asset (androidMain/assets on Android, desktopMain/resources
// on desktop), or null if missing. First consumer: the Places map's land-polygon
// data (world_land.txt, Natural Earth via world-atlas — public domain).
expect fun loadTextAsset(name: String): String?

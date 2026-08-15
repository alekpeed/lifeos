package com.alekpeed.lifeos.platform

import androidx.compose.ui.graphics.ImageBitmap

// Read a bundled text asset (androidMain/assets on Android, desktopMain/resources
// on desktop), or null if missing. First consumer: the Places map's land-polygon
// data (world_land.txt, Natural Earth via world-atlas — public domain).
expect fun loadTextAsset(name: String): String?

// Read a bundled image asset (androidMain/assets) as an ImageBitmap, or null if
// missing / undecodable. Backs a graphical interface's full-screen artwork. Only
// Android supplies real bitmaps; desktop returns null and callers fall back to the
// built-in functional screen.
expect fun loadImageAsset(name: String): ImageBitmap?

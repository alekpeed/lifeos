package com.alekpeed.lifeos.realtime
actual fun openShareboxRealtime(spaceId: String, onChange: () -> Unit): RealtimeHandle = firebaseEvents(spaceId, onChange)

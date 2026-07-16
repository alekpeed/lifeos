package com.alekpeed.lifeos.platform

import android.app.Activity
import android.content.Context
import android.speech.tts.TextToSpeech

// Holds the Android bits the capability layer needs: the current Activity (for
// window flags, share chooser, permission-gated calls) and a shared TextToSpeech
// engine. MainActivity wires these on create/resume.
object NativeHost {
    var activity: Activity? = null
    var appContext: Context? = null

    @Volatile var tts: TextToSpeech? = null
    @Volatile var ttsReady = false

    fun ctx(): Context? = activity ?: appContext

    fun ensureTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
}

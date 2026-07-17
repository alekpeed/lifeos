package com.alekpeed.lifeos.platform

import android.app.Activity
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanOptions

// Holds the Android bits the capability layer needs: the current Activity (for
// window flags, share chooser, permission-gated calls) and a shared TextToSpeech
// engine. MainActivity wires these on create/resume.
object NativeHost {
    var activity: Activity? = null
    var appContext: Context? = null

    @Volatile var tts: TextToSpeech? = null
    @Volatile var ttsReady = false

    // QR scanner (zxing-android-embedded): MainActivity registers the launcher;
    // Native.scanQr sets the pending callback and launches it.
    var qrLauncher: ActivityResultLauncher<ScanOptions>? = null
    @Volatile var qrCallback: ((String?) -> Unit)? = null

    // Photo picker (GetContent "image/*"): MainActivity registers the launcher and
    // does the decode→downscale→base64 on the result; Native.capturePhoto sets the
    // pending callback and launches it. The callback receives base64 JPEG or null.
    var photoLauncher: ActivityResultLauncher<String>? = null
    @Volatile var photoCallback: ((String?) -> Unit)? = null

    fun ctx(): Context? = activity ?: appContext

    fun ensureTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
}

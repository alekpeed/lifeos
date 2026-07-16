package com.alekpeed.lifeos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.alekpeed.lifeos.platform.NativeHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Storage.appContext = applicationContext
        NativeHost.activity = this
        NativeHost.appContext = applicationContext
        NativeHost.ensureTts(applicationContext)
        requestNeededPermissions()
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        NativeHost.activity = this
    }

    override fun onDestroy() {
        if (NativeHost.activity === this) NativeHost.activity = null
        super.onDestroy()
    }

    // Ask up front for the runtime permissions the UI-triggered capabilities need.
    // Anything denied simply makes that capability a no-op; nothing here blocks.
    private fun requestNeededPermissions() {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            wanted.add(Manifest.permission.READ_CONTACTS)
        }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 9001)
    }
}

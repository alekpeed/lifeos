package com.alekpeed.lifeos

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.alekpeed.lifeos.platform.ImageEncode
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.NativeHost
import com.journeyapps.barcodescanner.ScanContract
import java.io.File

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    // QR scanner result → the pending Native.scanQr callback. Registered before the
    // activity starts (field initializer), as ActivityResult requires.
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val cb = NativeHost.qrCallback
        NativeHost.qrCallback = null
        cb?.invoke(result.contents)
    }

    // Photo picker result → the pending Native.capturePhoto callback. The decode
    // and base64 happen here (off the picked Uri) so the capability layer stays
    // platform-agnostic. A null Uri means the user cancelled; a non-null Uri that
    // won't decode returns "" so the caller can show a "couldn't read that" message
    // instead of failing silently.
    private val photoPickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = NativeHost.photoCallback
        NativeHost.photoCallback = null
        if (uri == null) {
            cb?.invoke(null)
        } else {
            val b64 = try {
                ImageEncode.uriToDownscaledJpegBase64(this, uri)
            } catch (e: Exception) {
                null
            }
            cb?.invoke(b64 ?: "")
        }
    }

    // Camera capture. The temp file the camera writes into, held between launch and
    // result.
    private var pendingCameraUri: android.net.Uri? = null

    // TakePicture returns true when a photo was saved to pendingCameraUri. Decode it
    // the same way as a picked image; "" on decode failure, null on cancel.
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val cb = NativeHost.cameraCallback
        NativeHost.cameraCallback = null
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) {
            val b64 = try { ImageEncode.uriToDownscaledJpegBase64(this, uri) } catch (e: Exception) { null }
            cb?.invoke(b64 ?: "")
        } else {
            cb?.invoke(null)
        }
    }

    // Document picker → the pending Native.pickTextFile callback. Read the file's
    // bytes as UTF-8 text (capped) in the result callback. Null uri = cancelled.
    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = NativeHost.fileCallback
        NativeHost.fileCallback = null
        if (uri == null) {
            cb?.invoke(null)
        } else {
            val text = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.let { if (it.size > 4_000_000) null else it.decodeToString() }
            } catch (e: Exception) {
                null
            }
            cb?.invoke(text)
        }
    }

    // Camera runtime permission (declared in the manifest, so it's enforced). On
    // grant, launch the camera; on deny, report cancel.
    private val cameraPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            val cb = NativeHost.cameraCallback
            NativeHost.cameraCallback = null
            cb?.invoke(null)
        }
    }

    private fun requestCameraCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val file = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            pendingCameraUri = null
            val cb = NativeHost.cameraCallback
            NativeHost.cameraCallback = null
            cb?.invoke(null)
        }
    }

    // Fires the evening ritual when the phone is plugged in while the app is alive.
    private val chargingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                Native.postReminder("Plugged in for the night?", "Evening ritual: glance at tomorrow's tasks.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Storage.appContext = applicationContext
        NativeHost.activity = this
        NativeHost.appContext = applicationContext
        NativeHost.qrLauncher = qrScanLauncher
        NativeHost.photoLauncher = photoPickLauncher
        NativeHost.cameraRequest = { requestCameraCapture() }
        NativeHost.filePickLauncher = openDocumentLauncher
        NativeHost.ensureTts(applicationContext)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        requestNeededPermissions()
        registerShortcuts()
        handleIntent(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        NativeHost.activity = this
        enableNfcDispatch()
        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(chargingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(chargingReceiver, filter)
        }
    }

    override fun onPause() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
        }
        try {
            unregisterReceiver(chargingReceiver)
        } catch (e: Exception) {
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (NativeHost.activity === this) NativeHost.activity = null
        super.onDestroy()
    }

    // Route inbound shares, deep links, and scanned NFC tags into the app.
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { captureToInbox(it) }
                }
            }
            Intent.ACTION_VIEW -> {
                // lifeos://module/<id>
                intent.data?.let { uri ->
                    if (uri.scheme == "lifeos") {
                        uri.lastPathSegment?.let { Nav.open(it) }
                    }
                }
            }
            Intent.ACTION_ASSIST -> Nav.open("command")
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED -> {
                readNfcText(intent)?.let { captureToInbox(it) }
            }
        }
    }

    private fun captureToInbox(text: String) {
        // Route through the Ideas model so a shared/NFC/clipboard capture appends a
        // real record instead of clobbering the JSON blob.
        com.alekpeed.lifeos.ideas.appendIdea(text)
        Nav.open("ideas")
    }

    @Suppress("DEPRECATION")
    private fun readNfcText(intent: Intent): String? {
        val raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return null
        for (p in raw) {
            val msg = p as? NdefMessage ?: continue
            for (record in msg.records) {
                val payload = record.payload ?: continue
                if (payload.isEmpty()) continue
                // Well-known Text record: first byte is a status byte whose low 6 bits
                // are the language-code length; the text follows.
                val langLen = payload[0].toInt() and 0x3F
                val start = (1 + langLen).coerceAtMost(payload.size)
                val text = String(payload, start, payload.size - start, Charsets.UTF_8)
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    private fun enableNfcDispatch() {
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, intent, flags)
        try {
            adapter.enableForegroundDispatch(this, pi, null, null)
        } catch (e: Exception) {
        }
    }

    // Long-press launcher shortcuts that deep-link straight into a module.
    private fun registerShortcuts() {
        if (Build.VERSION.SDK_INT < 25) return
        val mgr = getSystemService(ShortcutManager::class.java) ?: return
        fun shortcut(id: String, label: String, icon: Int): ShortcutInfo {
            val view = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("lifeos://module/$id")
            }
            return ShortcutInfo.Builder(this, id)
                .setShortLabel(label)
                .setIcon(Icon.createWithResource(this, icon))
                .setIntent(view)
                .build()
        }
        try {
            mgr.dynamicShortcuts = listOf(
                shortcut("command", "Capture", android.R.drawable.ic_menu_edit),
                shortcut("today", "Today", android.R.drawable.ic_menu_my_calendar),
                shortcut("tasks", "Tasks", android.R.drawable.ic_menu_agenda),
            )
        } catch (e: Exception) {
        }
    }

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

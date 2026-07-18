package com.alekpeed.lifeos.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.view.WindowManager

private const val CHANNEL_REMINDERS = "lifeos_reminders"
private const val CHANNEL_PINNED = "lifeos_pinned"
private const val PINNED_ID = 4201

private fun ensureChannel(nm: NotificationManager, id: String, name: String, importance: Int) {
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(id) == null) {
        nm.createNotificationChannel(NotificationChannel(id, name, importance))
    }
}

@Suppress("DEPRECATION")
private fun notifBuilder(ctx: Context, channelId: String): Notification.Builder =
    if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channelId) else Notification.Builder(ctx)

private fun actionPending(ctx: Context, action: String, notifId: Int): PendingIntent {
    val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
        this.action = action
        putExtra(NotificationActionReceiver.EXTRA_ID, notifId)
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(ctx, (action + notifId).hashCode(), intent, flags)
}

// Real Android capabilities. Each degrades quietly if a permission is missing or
// the system service is unavailable — nothing here throws into the UI.
actual object Native {
    actual val supportsTts = true
    actual val supportsNotifications = true
    actual val supportsContacts = true
    actual val supportsKeepAwake = true
    actual val supportsWakeWord = true
    actual val supportsGeofence = true
    actual val supportsSpeakerId = true
    actual val supportsQrScan = true
    actual val supportsLocation = true
    actual val supportsCamera = true
    actual val supportsFilePick = true
    actual val supportsPdfExport = true

    actual fun speak(text: String) {
        val ctx = NativeHost.ctx() ?: return
        NativeHost.ensureTts(ctx)
        if (NativeHost.ttsReady) NativeHost.tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lifeos")
    }

    actual fun stopSpeaking() {
        NativeHost.tts?.stop()
    }

    actual fun shareText(text: String) {
        val ctx = NativeHost.ctx() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Share").apply {
            if (NativeHost.activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        (NativeHost.activity ?: ctx).startActivity(chooser)
    }

    actual fun readClipboard(): String? {
        val ctx = NativeHost.ctx() ?: return null
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(ctx)?.toString()?.ifBlank { null }
    }

    actual fun keepScreenAwake(on: Boolean) {
        val act = NativeHost.activity ?: return
        act.runOnUiThread {
            if (on) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    actual fun importContacts(): List<PhoneContact> {
        val ctx = NativeHost.ctx() ?: return emptyList()
        return try {
            val out = mutableListOf<PhoneContact>()
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val seen = HashSet<String>()
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                    val num = if (numIdx >= 0) c.getString(numIdx) else null
                    if (!name.isNullOrBlank() && seen.add(name)) out.add(PhoneContact(name, num ?: ""))
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    actual fun postReminder(title: String, body: String) {
        val ctx = NativeHost.ctx() ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm, CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        val id = (title + body).hashCode()
        val n = notifBuilder(ctx, CHANNEL_REMINDERS)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .addAction(0, "Done", actionPending(ctx, NotificationActionReceiver.ACTION_DONE, id))
            .addAction(0, "Snooze", actionPending(ctx, NotificationActionReceiver.ACTION_SNOOZE, id))
            .build()
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; ignore.
        }
    }

    actual fun setPinnedNextUp(text: String?) {
        val ctx = NativeHost.ctx() ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (text == null) {
            nm.cancel(PINNED_ID)
            return
        }
        ensureChannel(nm, CHANNEL_PINNED, "Next up", NotificationManager.IMPORTANCE_LOW)
        val n = notifBuilder(ctx, CHANNEL_PINNED)
            .setContentTitle("Next up")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        try {
            nm.notify(PINNED_ID, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; ignore.
        }
    }

    actual fun setWakeWordEnabled(on: Boolean) {
        val ctx = NativeHost.ctx() ?: return
        val svc = Intent(ctx, WakeWordService::class.java)
        if (on) {
            NativeHost.activity?.let { act ->
                if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 9002)
                }
            }
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc) else ctx.startService(svc)
        } else {
            ctx.stopService(svc)
        }
    }

    actual fun armArrivalHere(label: String) {
        Geofences.armHere(NativeHost.ctx(), label)
    }

    actual fun clearArrivals() {
        Geofences.clear(NativeHost.ctx())
    }

    private fun reminderPendingIntent(ctx: Context, id: Int, title: String, body: String): PendingIntent {
        val intent = Intent(ctx, ReminderFireReceiver::class.java).apply {
            putExtra(ReminderFireReceiver.EXTRA_TITLE, title)
            putExtra(ReminderFireReceiver.EXTRA_BODY, body)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, id, intent, flags)
    }

    // Uses setAndAllowWhileIdle rather than an exact alarm: no SCHEDULE_EXACT_ALARM
    // permission needed, and Android may still shift it by a few minutes under
    // Doze — an honest tradeoff for a personal reminder, not a deadline-critical one.
    actual fun scheduleReminder(id: Int, title: String, body: String, atEpochMillis: Long) {
        val ctx = NativeHost.ctx() ?: return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = reminderPendingIntent(ctx, id, title, body)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, pi)
        } catch (e: SecurityException) {
            // no exact-alarm-adjacent permission on this OEM/version; ignore
        }
    }

    actual fun cancelReminder(id: Int) {
        val ctx = NativeHost.ctx() ?: return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(reminderPendingIntent(ctx, id, "", ""))
    }

    actual fun enrollVoice(onStatus: (String) -> Unit, onResult: (Boolean) -> Unit) {
        val ctx = NativeHost.ctx()
        if (ctx == null) { onResult(false); return }
        // Enrollment needs the mic; request it if the activity is around.
        NativeHost.activity?.let { act ->
            if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 9003)
            }
        }
        VoiceEnroller.enroll(ctx, onStatus, onResult)
    }

    actual fun hasVoiceprint(): Boolean = VoiceId.hasVoiceprint()

    actual fun clearVoiceprint() = VoiceId.clearVoiceprint()

    actual fun setOnlyMyVoice(on: Boolean) = VoiceId.setOnlyMyVoice(on)

    actual fun onlyMyVoiceEnabled(): Boolean = VoiceId.isOnlyMyVoiceEnabled()

    actual fun scanQr(onResult: (String?) -> Unit) {
        val launcher = NativeHost.qrLauncher
        if (launcher == null) { onResult(null); return }
        NativeHost.qrCallback = onResult
        val opts = com.journeyapps.barcodescanner.ScanOptions().apply {
            setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
            setPrompt("Scan a Life OS code")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        try {
            launcher.launch(opts)
        } catch (e: Exception) {
            NativeHost.qrCallback = null
            onResult(null)
        }
    }

    actual fun scanBarcode(onResult: (String?) -> Unit) {
        val launcher = NativeHost.qrLauncher
        if (launcher == null) { onResult(null); return }
        NativeHost.qrCallback = onResult
        val opts = com.journeyapps.barcodescanner.ScanOptions().apply {
            setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.PRODUCT_CODE_TYPES)
            setPrompt("Scan a book barcode")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        try {
            launcher.launch(opts)
        } catch (e: Exception) {
            NativeHost.qrCallback = null
            onResult(null)
        }
    }

    actual fun getCurrentLocation(onResult: (Double?, Double?) -> Unit) {
        Geofences.currentLocation(NativeHost.ctx(), onResult)
    }

    actual fun capturePhoto(onResult: (String?) -> Unit) {
        val launcher = NativeHost.photoLauncher
        if (launcher == null) { onResult(null); return }
        NativeHost.photoCallback = onResult
        try {
            launcher.launch("image/*")
        } catch (e: Exception) {
            NativeHost.photoCallback = null
            onResult(null)
        }
    }

    actual fun takePhoto(onResult: (String?) -> Unit) {
        val request = NativeHost.cameraRequest
        if (request == null) { onResult(null); return }
        NativeHost.cameraCallback = onResult
        try {
            request.invoke()
        } catch (e: Exception) {
            NativeHost.cameraCallback = null
            onResult(null)
        }
    }

    actual fun pickTextFile(onResult: (String?) -> Unit) {
        val launcher = NativeHost.filePickLauncher
        if (launcher == null) { onResult(null); return }
        NativeHost.fileCallback = onResult
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            NativeHost.fileCallback = null
            onResult(null)
        }
    }

    actual fun pickFilteredTextFile(substrings: List<String>, onResult: (String?) -> Unit) {
        val launcher = NativeHost.filePickLauncher
        if (launcher == null) { onResult(null); return }
        NativeHost.fileCallback = onResult
        NativeHost.fileFilter = substrings
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            NativeHost.fileCallback = null
            NativeHost.fileFilter = null
            onResult(null)
        }
    }

    actual fun exportTextAsPdf(title: String, text: String) {
        val ctx = NativeHost.ctx() ?: return
        try {
            val doc = android.graphics.pdf.PdfDocument()
            val pageW = 595; val pageH = 842; val margin = 40           // A4 @ 72dpi
            val width = pageW - margin * 2
            val body = android.text.TextPaint().apply { textSize = 12f; color = android.graphics.Color.BLACK; isAntiAlias = true }
            val titlePaint = android.text.TextPaint().apply { textSize = 18f; color = android.graphics.Color.BLACK; isFakeBoldText = true; isAntiAlias = true }
            val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, body, width).build()
            val totalH = layout.height

            var drawn = 0
            var pageNum = 1
            while (drawn < totalH && pageNum <= 300) {
                val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                val c = page.canvas
                var top = margin
                if (pageNum == 1) { c.drawText(title, margin.toFloat(), (margin + 14).toFloat(), titlePaint); top = margin + 34 }
                val avail = (pageH - margin) - top
                // Snap the page break to a whole line so nothing gets sliced.
                val bottomLine = layout.getLineForVertical(drawn + avail)
                var pageH2 = layout.getLineTop(bottomLine) - drawn
                if (pageH2 <= 0) pageH2 = avail
                c.save()
                c.translate(margin.toFloat(), top.toFloat())
                c.clipRect(0, 0, width, minOf(pageH2, avail))
                c.translate(0f, -drawn.toFloat())
                layout.draw(c)
                c.restore()
                doc.finishPage(page)
                drawn += pageH2
                pageNum++
            }

            val safe = title.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(40).ifBlank { "lifeos" }
            val file = java.io.File(ctx.cacheDir, "$safe.pdf")
            java.io.FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Print / Save PDF").apply {
                if (NativeHost.activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            (NativeHost.activity ?: ctx).startActivity(chooser)
        } catch (e: Exception) {
            // best-effort export
        }
    }

    actual fun pickEbook(onResult: (String?) -> Unit) {
        val launcher = NativeHost.filePickLauncher
        if (launcher == null) { onResult(null); return }
        NativeHost.fileCallback = onResult
        NativeHost.ebookMode = true
        try {
            launcher.launch(arrayOf("application/epub+zip", "text/plain", "*/*"))
        } catch (e: Exception) {
            NativeHost.fileCallback = null
            NativeHost.ebookMode = false
            onResult(null)
        }
    }
}

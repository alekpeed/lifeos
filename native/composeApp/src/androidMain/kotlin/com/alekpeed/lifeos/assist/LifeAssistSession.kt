package com.alekpeed.lifeos.assist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

// What happens when the assist gesture fires (long-press home / power, or the
// assistant swipe): Life OS opens straight to quick-capture instead of drawing any
// assistant overlay of its own. A graphical assistant surface can replace this
// later; today it makes the gesture genuinely land in the app.
class LifeAssistSession(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lifeos://module/command")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startAssistantActivity(intent)
        } catch (e: Exception) {
            // `context` here is VoiceInteractionSession.getContext(), not the ctor arg.
            try {
                context.startActivity(intent)
            } catch (ignored: Exception) {
            }
        }
        hide()
    }
}

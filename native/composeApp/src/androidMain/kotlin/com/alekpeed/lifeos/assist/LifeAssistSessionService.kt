package com.alekpeed.lifeos.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

// Hands the system a fresh session each time the assistant is summoned.
class LifeAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LifeAssistSession(this)
}

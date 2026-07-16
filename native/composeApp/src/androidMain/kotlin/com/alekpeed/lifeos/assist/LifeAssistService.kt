package com.alekpeed.lifeos.assist

import android.service.voice.VoiceInteractionService

// Declares Life OS as a voice-interaction (digital assistant) provider. Its mere
// presence — plus the interaction_service.xml descriptor referenced from the
// manifest — is what makes "LifeOS" appear in Settings → Default apps → Digital
// assistant app, so it can be set as the phone's assistant. The real work happens
// in the session (LifeAssistSession) when the assist gesture fires.
class LifeAssistService : VoiceInteractionService()

package com.alekpeed.lifeos;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

// Hosts assist sessions for the Life OS assistant. Each invocation (assist
// gesture, or later a hotword) creates a LifeOSInteractionSession.
public class LifeOSInteractionSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new LifeOSInteractionSession(this);
    }
}

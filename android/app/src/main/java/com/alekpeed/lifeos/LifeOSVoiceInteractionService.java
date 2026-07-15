package com.alekpeed.lifeos;

import android.service.voice.VoiceInteractionService;

// The root of Life OS acting as the phone's digital assistant (§13 wake word,
// stage 1). Being the active assistant is also what later unlocks the
// green-dot-exempt HotwordDetectionService for a "Hey Life OS" hotword. For now
// this is intentionally minimal — the assist gesture routes through the session.
public class LifeOSVoiceInteractionService extends VoiceInteractionService {
}

package com.alekpeed.lifeos;

import android.content.Intent;
import android.speech.RecognitionService;

// Minimal recognition service — required as the voice-interaction service's
// companion. Life OS captures speech in-app (the Command screen) rather than
// through this service, so the callbacks are intentional no-ops for now.
public class LifeOSRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
    }

    @Override
    protected void onCancel(Callback listener) {
    }

    @Override
    protected void onStopListening(Callback listener) {
    }
}

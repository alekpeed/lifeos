package com.alekpeed.lifeos;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

// When the assistant is invoked (assist gesture now; a "Hey Life OS" hotword
// later), we don't draw our own overlay — we open Life OS straight to the
// Command voice-capture screen and dismiss the session. The existing deep-link
// router (native-boot.js) turns lifeos://open/command into the Command module.
public class LifeOSInteractionSession extends VoiceInteractionSession {

    public LifeOSInteractionSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        try {
            Intent i = new Intent(getContext(), MainActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse("lifeos://open/command"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            getContext().startActivity(i);
        } catch (Exception e) {
            // never let an assist invocation crash
        }
        hide();
    }
}

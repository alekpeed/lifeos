package com.alekpeed.lifeos;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import org.json.JSONObject;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Custom local plugin — must be registered before super.onCreate().
        registerPlugin(GeofencePlugin.class);
        super.onCreate(savedInstanceState);
        // Cold start: the app may have been launched from Android's Share sheet.
        handleSendIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Warm: a share arrived while the app was already running.
        handleSendIntent(intent);
    }

    // Inbound share sheet (FUTURE_FEATURES.md §13). When another app shares text
    // or a link to LifeOS (ACTION_SEND, text/*), forward the payload to the web
    // layer as a window event; js/native/native-boot.js files it into Links or
    // Ideas. Best-effort: a malformed share must never crash the app.
    private void handleSendIntent(Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("text/")) return;

        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (text == null && subject == null) return;

        try {
            JSONObject payload = new JSONObject();
            payload.put("text", text == null ? "" : text);
            payload.put("subject", subject == null ? "" : subject);
            // JSONObject.toString() is valid JS and escapes the strings safely,
            // so there's no injection risk from arbitrary shared content.
            final String js = "window.__lifeosSharedIntent = " + payload.toString()
                + "; window.dispatchEvent(new CustomEvent('lifeosshared'));";

            if (getBridge() == null) return;
            final WebView webView = getBridge().getWebView();
            if (webView == null) return;
            // Post to the WebView thread; a short delay lets a cold-start page
            // finish loading before we set the global (the web side also
            // re-checks the global a few times after boot as a backstop).
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(js, null);
                }
            }, 300);
        } catch (Exception e) {
            // ignore -- never let a bad share intent crash the activity
        }
    }
}

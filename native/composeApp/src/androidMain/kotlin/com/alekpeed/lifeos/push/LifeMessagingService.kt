package com.alekpeed.lifeos.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.platform.Native
import kotlinx.coroutines.*

class LifeMessagingService : FirebaseMessagingService() {
    override fun onCreate() { super.onCreate(); Storage.appContext = applicationContext }
    override fun onMessageReceived(message: RemoteMessage) {
        if (!com.alekpeed.lifeos.sync.FirebaseAuth.isSignedIn() || message.data["uid"] != com.alekpeed.lifeos.sync.FirebaseAuth.userId()) return
        Native.postReminder(message.data["title"] ?: "LifeOS", message.data["body"].orEmpty(), message.data["subject"].orEmpty())
    }
    override fun onNewToken(token: String) {
        PushRegistration.forget()
        CoroutineScope(Dispatchers.IO).launch { PushRegistration.registerIfNeeded() }
    }
}

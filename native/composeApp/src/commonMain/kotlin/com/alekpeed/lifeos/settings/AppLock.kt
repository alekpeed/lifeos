package com.alekpeed.lifeos.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage

// A PIN gate on the app. Life OS holds documents, contacts and money — handing someone
// your unlocked phone shouldn't hand them all of it.
//
// Deliberately modest about what it is: the PIN is stored as a hash so the number isn't
// sitting in the settings file in the clear, but the data itself is not encrypted, and
// anyone with real access to the device's storage can read it. This keeps a person who
// picks up your phone out of the app; it is not protection against a forensic tool. The
// Settings copy says exactly that rather than implying more.

private const val K_HASH = "AppLockHash"

// A small, deterministic string hash (FNV-1a, 64-bit, then hex). Not a cryptographic
// digest and not claimed to be one — enough that the stored value isn't the PIN itself.
private fun hashPin(pin: String): String {
    var h = -0x340d631b7bdddcdbL // FNV offset basis
    for (c in pin) {
        h = h xor c.code.toLong()
        h *= 0x100000001b3L
    }
    // Mix again so short PINs don't map to visibly similar values.
    var x = h
    x = x xor (x ushr 33); x *= -0xae502812aa7333L
    x = x xor (x ushr 29); x *= -0x3b314601e57a13adL
    x = x xor (x ushr 32)
    return x.toULong().toString(16)
}

fun appLockEnabled(): Boolean = !Storage.read(K_HASH).isNullOrBlank()

fun setAppLockPin(pin: String): Boolean {
    val clean = pin.filter { it.isDigit() }
    if (clean.length < 4) return false
    Storage.write(K_HASH, hashPin(clean))
    return true
}

fun clearAppLock() = Storage.write(K_HASH, "")

fun checkAppLockPin(pin: String): Boolean {
    val stored = Storage.read(K_HASH)?.ifBlank { null } ?: return true
    return hashPin(pin.filter { it.isDigit() }) == stored
}

// Shown in place of the app until the PIN is entered. One session unlock — locking again
// happens when the app is next started, which is the point at which someone else would
// be holding the phone.
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    fun tryUnlock() {
        if (checkAppLockPin(pin)) {
            onUnlocked()
        } else {
            msg = "Wrong PIN"
            pin = ""
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Life OS", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter your PIN to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(12); msg = "" },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                label = { Text("PIN") },
            )
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { tryUnlock() }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock")
            }
        }
    }
}

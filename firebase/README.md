# LifeOS Firebase backend

Project `lifeos-501716`; all backend services are in Firebase/Google Cloud. The native app remains local first, with Android and desktop sharing the same authenticated HTTP gateway.

- Firebase Authentication: email/password and Google; existing Supabase UIDs, bcrypt hashes, and Google identities imported.
- Firestore: named Enterprise Native database `lifeos`, `us-east1`, realtime enabled. User modules are split into record documents under `users/{uid}/modules/{keyHash}/records`. Metadata preserves each module's JSON shape.
- Cloud Storage: `lifeos-501716.firebasestorage.app`, `us-east1`. The gateway enforces account ownership for attachments and membership for shared files.
- Cloud Functions (Node 22): `api`, `telegram`, `scheduledNotifications` in `us-east1`. API authenticates every request with Firebase ID tokens. Firestore and Storage client rules deny all direct reads/writes; the Admin SDK gateway checks authorization.
- FCM: native Android messaging service receives data messages. Hourly Cloud Scheduler sends due reminders and one Telegram digest per UTC day. No service-account private key is packaged in the app.
- Shared spaces: existing invitation IDs work; a short-lived SSE connection observes Firestore changes while the space is open.

## Sync behavior

The client exchanges local text and its last acknowledged server baseline. The server merges objects and record arrays by stable `id` inside a transaction. Independent fields and different records survive concurrent edits; when the same field is changed on both devices, the incoming edit wins. Existing numeric IDs can collide when two offline devices create a record from the same counter; this is not a CRDT. Non-record arrays are merged as whole values. Local edits made during a request are merged back before acknowledgement. Local file writes use a synced temporary file and rename.

The map tile index stays device-local. Other non-reserved module/settings keys sync, matching the previous app (including configured integration keys); direct cloud access remains blocked. Encrypted vault data stays encrypted. Attachments upload/download in batches of 25, with a 20 MB per-file gateway limit. Oversized individual records or root metadata over 800 KB are rejected, leaving local data available.

Sign in with the existing email/password after upgrading. Accounts previously using Google without a password can use the native app’s **Set/reset password** action. Old Supabase session tokens are not Firebase sessions. The installation is bound to its first Firebase UID to prevent uploading one person's local library to another account. To change accounts, export local data and clear the installation first.

Telegram: use Settings to connect the bot. The app registers its webhook and creates a one-time 10-minute chat-link token. Existing source had no linked chats or device tokens. Device delivery and Telegram conversations still require a real phone/bot to verify.

## Deployment and checks

From the repository root:

```
cd firebase/functions
npm ci
npm test
cd ../..
npx -y firebase-tools@latest deploy --only functions,firestore,storage --project lifeos-501716
```

Firebase CLI must be signed in as an authorized project administrator. CI runs backend tests; deployment is local through that login. No CI credential has been added. Old Supabase workflow definitions are archived in `legacy-workflows/` and cannot execute.

The Android app initializes Firebase explicitly in `FullscreenApplication` and includes Firebase Messaging. `google-services.json` contains public app identifiers only. Local Android builds run `assembleDebug`, `compileKotlinDesktop`, and `desktopTest` with `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`.

## Migration completed 2026-09-16

Imported and verified 4 auth accounts, 21 native key/value modules, 4 profiles, 4 shared spaces, 4 memberships, and 1 shared item. Three older web-format records (two projects and one task) were also converted to native records with stable numeric IDs; their originals remain losslessly preserved in `legacyRecords`. Three Google identities were linked to their original account UIDs. Source attachment storage, Telegram links, and FCM registrations were empty. The old Supabase project is paused and retained for recovery; the updated app makes no Supabase calls.

`tools/import-supabase.mjs` records a completion marker and refuses a second import. Exports contain private data and password hashes and must never be committed. Firebase preserves bcrypt credentials using the [official Admin import API](https://firebase.google.com/docs/auth/admin/import-users#import_users_with_bcrypt_hashed_passwords).

Validation: backend unit tests; Android and desktop builds and desktop tests; live disposable-account tests for sign-in, refresh, account isolation, merge, shared-space invitation and item lifecycle, binary file round trip, unauthorized access. Test accounts and records were removed after validation.

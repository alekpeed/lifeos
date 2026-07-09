# Sharebox v2 + Backend — planning doc

**Status: scaffolding only, nothing live yet.** The existing Picker/Drive-based
Sharebox (`js/data/sharebox-sync.js`, `js/data/picker.js`) keeps working
untouched until this is built out and proven — nothing gets removed until the
replacement actually works end to end.

## Why

Triggered by a mobile-only "invalid API key" error on the Google Picker (very
likely a fixable referrer-restriction quirk, not a fundamental problem) —
but the underlying interest is broader: a small backend is genuinely useful
beyond just fixing that, since push notifications and Telegram integration
(both already on the roadmap) need one regardless. Building it once to serve
Sharebox *and* those features is better than building it twice.

## Sign-in method: DECIDED — Google

We're using **Google sign-in** via Supabase Auth's Google provider (not
magic-link email), so signing into Sharebox feels identical to the rest of the
app's Google integrations. The tradeoff accepted: Supabase's dashboard needs
the OAuth **Client Secret** added (a first — previously the app only ever used
the public Client ID + API key). That secret lives only in the Supabase
dashboard, never in this repo.

## What's already done (inert — not imported by the running app yet)

- `vendor/supabase/supabase.umd.js` — the Supabase JS client, vendored
  (self-contained UMD build, same pattern as Leaflet/fflate — no CDN at
  runtime, works offline once cached).
- `js/data/supabase-config.js` — `SUPABASE_URL` / `SUPABASE_ANON_KEY`
  placeholders. **Needs filling in** from the Supabase dashboard (Settings →
  API) before anything here can actually run.
- `js/data/supabase-client.js` — lazy singleton client loader, now configured
  for **PKCE** OAuth (`flowType: 'pkce'`), persisted sessions, and auto
  token refresh. PKCE returns the OAuth response as a `?code=` query param
  rather than a URL fragment, which matters because the app uses hash routing
  (`#/chords`) — a fragment-based response would collide with the router.
- `js/data/supabase-auth.js` — **built.** Google sign-in / sign-out, current
  user + session getters (no-throw when unconfigured), an `onAuthChange`
  subscription, and a `displayNameOf` helper. All gated behind
  `isSupabaseConfigured()`.
- `js/data/sharebox-supabase.js` — **built.** The v2 data layer: spaces +
  membership (`getMySpaces` / `createSpace` / `joinSpace` / `getMembers`),
  items (`listItems` / `addItem` / `updateItem` / `removeItem`, normalized to
  the shape the existing Sharebox view already renders), file upload +
  signed-URL download against the Storage bucket, and a `subscribeToItems`
  Realtime subscription that replaces v1's manual "Sync now". No snapshot /
  tombstone / last-write-wins engine — Postgres is the source of truth and RLS
  is the access control.
- `sql/supabase-schema.sql` — ready-to-run schema: `sharebox_spaces` /
  `sharebox_members` (a join table, not hardcoded to 2 people — friend-mesh
  Sharebox later needs zero schema change) / `sharebox_items`, Row Level
  Security policies (a space's members are the only ones who can read/write
  it — this replaces "share the Drive folder" as the access-control
  mechanism), and a Storage bucket + policies for file attachments. A second
  section sketches `push_subscriptions` and `telegram_links` for later.

Verified inert: importing any of these changes nothing about the running app,
and the Drive-based Sharebox keeps working untouched. `getSupabaseClient()`
throws a clear "not configured" error until credentials are filled in; the
no-throw getters answer null; the subscription helpers return no-op
unsubscribers. (Live auth/DB behavior can't be tested from here — it needs
your real Supabase project.)

## What's left before this is real

Steps 1–3 are yours (dashboard actions I can't do from here); step 4 is the
remaining code work; step 5 is the cutover.

1. Fill in `supabase-config.js` with the real project URL + anon key
   (Supabase dashboard → Settings → API).
2. Enable the Google provider (Authentication → Providers → Google) with the
   OAuth Client ID + **Secret**, and add your app's URL to the allowed
   redirect URLs.
3. Run `sql/supabase-schema.sql` in the SQL Editor.
4. Wire the Sharebox view to the new modules: a "Sign in with Google" state,
   create-or-join-a-space flow, and swap the add/list/delete calls from the
   Drive path to `sharebox-supabase.js`, with `subscribeToItems` for live
   updates. (New UI work — the data/auth layer it calls is already built.)
5. Test side by side with the existing Drive-based Sharebox before cutting
   over; only remove the old code once the new path is proven live with both
   you and your friend.

This is Tier 2 work (new auth model, new data model, security-rule design) —
worth doing on Opus rather than rushed.

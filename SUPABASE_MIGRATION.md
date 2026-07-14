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

## Status update — configured and wired

Steps 1–4 are **done**:

1. ✅ `supabase-config.js` filled in with the real project URL + anon key.
2. ✅ Google provider enabled (existing app Client ID + a freshly generated
   Client Secret), with `https://<project>.supabase.co/auth/v1/callback` added
   to the OAuth client's redirect URIs.
3. ✅ `sql/supabase-schema.sql` run in the SQL Editor (all five tables + RLS +
   the Storage bucket).
4. ✅ Sharebox view wired to the new modules. The v2 surface is exposed through
   `ctx.data.ShareboxV2` (grouped in `api.js`, so nothing imports the
   `supabase-*` files directly), and the view now has: a backend toggle,
   Google sign-in/out, a create-or-join-a-space flow, an invite (copy the space
   id), the add/list/delete calls routed to `sharebox-supabase.js`, file
   upload + signed-URL download against Storage, and a `subscribeToItems`
   Realtime subscription so a friend's post appears live.

**Supabase is now the primary backend** — once configured it's the default and
what you land on. **Drive stays as a permanent one-click fallback** (the
"Drive (fallback)" toggle) — a working backup path costs nothing to keep. Both
were smoke-tested in a headless browser: each renders with zero console errors
and the toggle switches cleanly.

### The one thing left: a live two-person test

Automated tests can't sign a real Google account in or prove a post syncs to a
second human. So the remaining validation is manual:

- Sign in with Google on the Sharebox tab (you should bounce to Google and land
  back signed in — if the redirect errors, the OAuth redirect URI may still be
  propagating; give it a few minutes).
- Create a space, copy its ID, have your friend sign in and join with it.
- Post a link/note/file from each side and confirm it appears on the other
  **without** a manual refresh (that's Realtime working).
- Confirm file upload + download round-trips.

Once that's confirmed, v2 is proven live. The Drive path stays regardless as
the fallback.

---

# Personal-data sync — Supabase migration (the rest of the app, not Sharebox)

**Status (2026-07-13): built alongside Drive, not yet live-verified.** This is
the bigger migration — moving *all* the app's personal data (Tasks, Places,
Finance, Books… everything except Sharebox, which is already on Supabase) off
the Drive snapshot sync and onto Postgres. Decided direction: keep local-first
(IndexedDB stays the source of truth, offline unchanged), swap the *sync
transport* from Drive to Supabase. Same side-by-side rollout: Drive sync is
untouched and keeps working until this is proven.

## Design (locked)

- **One generic table**, not ~57 per-module tables: `sync_records`
  (`user_id`, `store`, `record_id`, `data jsonb`, `updated_at`, `deleted_at`),
  a near-mechanical port of the IndexedDB object-store model. Still queryable
  server-side later via JSONB operators. RLS scopes every row to `auth.uid()`.
- **Last-write-wins**, identical semantics to the Drive `mergeState`: newest
  `updatedAt` wins; soft-delete tombstones (`deleted_at`) win only if `>=` the
  record's `updatedAt` (so an edit after a delete resurrects). No CRDT — that
  stays a separate future item.
- **Attachments** → a private `lifeos-attachments` Storage bucket, one folder
  per user; only metadata rides in the JSONB row.

## What's built (inert until you apply the SQL + turn it on)

- `sql/supabase-personal-sync-schema.sql` — the `sync_records` table, its RLS
  policies, and the attachments Storage bucket + policies. **You apply this.**
- `js/data/supabase-sync.js` — the engine. Pure `reconcile()` (unit-tested
  headlessly across the full LWW matrix, incl. the `Z` vs `+00:00` timestamp-
  format trap) + full pull/apply/push IO + attachment binaries to Storage.
- `js/data/api.js` — exposes `connectSupabaseSync` / `syncSupabaseNow` /
  `disconnectSupabaseSync` / `getSupabaseSyncState` through `ctx.data`.
- `js/interfaces/default/views/settings.js` — a **"Cloud sync (Supabase)"**
  block beside the Drive one. Shows "sign in to enable" when signed out;
  otherwise a Turn-on / Sync-now / Turn-off control. Smoke-tested: renders
  with zero console errors in the signed-out state.

**No separate Drive→Supabase import needed.** Because local IndexedDB is the
source of truth, your existing data seeds Supabase automatically on the first
"Turn on cloud sync" (reconcile pushes every local record up). Even the
failure mode is safe: if you click it before applying the SQL, the fetch throws
and surfaces in the status line *before* any local data is touched.

## The live test — your runbook

1. **Apply the schema.** Run `sql/supabase-personal-sync-schema.sql` in the
   Supabase SQL Editor (or via the Management API's
   `POST /v1/projects/{ref}/database/query`), same as the Sharebox schema was
   run. It creates one table + policies + one Storage bucket.
2. **Sign in.** Settings → Account → sign in (the section only offers cloud
   sync once you're signed in).
3. **Turn on cloud sync** in the new "Cloud sync (Supabase)" block. First run
   pushes your local data up; the status line reports what changed.
4. **Verify round-trip on a second device** (or a second browser profile):
   sign in as the same account, turn on cloud sync, confirm your data appears.
   Edit a record on one, sync both, confirm the newer edit wins. Delete on one,
   sync both, confirm it's gone on the other (and doesn't resurrect).
5. **Attachments:** add a photo/PDF on one device, sync, confirm it downloads
   on the other.

Once that's confirmed, personal-data sync is proven live. Only *then* does the
Drive engine (`js/data/sync.js` + its Settings block) get retired — a clean,
separate follow-up, not part of this change.

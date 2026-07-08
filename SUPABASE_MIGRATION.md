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

## What's already done (inert — not imported by the running app yet)

- `vendor/supabase/supabase.umd.js` — the Supabase JS client, vendored
  (self-contained UMD build, same pattern as Leaflet/fflate — no CDN at
  runtime, works offline once cached).
- `js/data/supabase-config.js` — `SUPABASE_URL` / `SUPABASE_ANON_KEY`
  placeholders. **Needs filling in** from the Supabase dashboard (Settings →
  API) before anything here can actually run.
- `js/data/supabase-client.js` — lazy singleton client loader.
- `sql/supabase-schema.sql` — ready-to-run schema: `sharebox_spaces` /
  `sharebox_members` (a join table, not hardcoded to 2 people — friend-mesh
  Sharebox later needs zero schema change) / `sharebox_items`, Row Level
  Security policies (a space's members are the only ones who can read/write
  it — this replaces "share the Drive folder" as the access-control
  mechanism), and a Storage bucket + policies for file attachments. A second
  section sketches `push_subscriptions` and `telegram_links` for later.

## The one open decision: how do people sign in?

| | **Google sign-in** (via Supabase Auth's Google provider) | **Magic-link email** (Supabase's own) |
|---|---|---|
| Setup | Reuses the existing Google Cloud OAuth Client, but needs the **Client Secret** added to Supabase's dashboard (a new use for it — previously never needed) | Nothing to configure in Google Cloud at all |
| User experience | Same "Sign in with Google" as the rest of the app | A different flow — click a link emailed to you |
| Best if | You want signing in to feel consistent everywhere | You'd rather avoid more Google Cloud Console steps for now |

Both options work fine with the schema above unchanged — Supabase's
`auth.users` table is identical either way. Decide whenever convenient; it
doesn't block anything else.

## What's left before this is real

1. Fill in `supabase-config.js` with the real project URL + anon key.
2. Decide the sign-in method (above) and configure it in the Supabase
   dashboard.
3. Run `sql/supabase-schema.sql` in the SQL Editor.
4. Build the actual Sharebox v2 sync logic against these tables (new work —
   doesn't reuse the Drive-sync merge engine, since Postgres + Realtime
   subscriptions replace last-write-wins snapshot merging entirely).
5. Test side by side with the existing Drive-based Sharebox before cutting
   over; only remove the old code once the new path is proven live with both
   you and your friend.

This is Tier 2 work (new auth model, new data model, security-rule design) —
worth doing on Opus rather than rushed.

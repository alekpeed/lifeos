# Life OS — Everything Not Yet Built

A single consolidated list of every feature, integration, and idea that's
been discussed but isn't built yet — pulled out of `PROJECT_SPEC.md` (which
also tracks what *is* built) so this can be read on its own as a pure
"what's left / what's possible" reference. Nothing here is committed to;
this is a living wishlist, not a roadmap with dates.

Tier legend:
- 🔧 **Routine** — no architecture decision, no new account/API, buildable
  in a normal session
- 🔑 **New integration** — needs a new OAuth flow or a third-party account,
  usually free but sometimes paid; each gets its own go/no-go before
  building
- 🏗️ **Tier 2** — needs a real design decision before writing code
  (architecture, security, or a novel rendering/sync approach)
- 🌙 **Moonshot** — a bigger swing than a "feature"; closer to its own
  sub-project, some genuinely change what kind of app this is
- 🪐 **Far tier** — moonshot-plus: unusually heavy on engineering, storage,
  or compute; the most speculative tier

---

## 0. ⏸️ Parked — deferred by Alek's own call

Don't resurface these in status recaps / "what's open" summaries unless
Alek asks about one by name or says the word to un-park it. Fully scoped
already where relevant (see the ⏸️ marker at each one's full entry below) —
parked means "not now," not "forgotten" or "cut."

- **Financial Center (Plaid)** — bank/investment linking. Full pricing/
  architecture detail in section 4.
- **A separate ChatGPT panel** — needs a backend proxy first (OpenAI's API
  doesn't support direct browser calls). Full detail in section 1.
- **Bio-futurism/jungle interface theme** — a third, from-scratch interface
  concept ("40th century meets the Amazon meets the far reaches of the
  galaxy"), unrelated to Vespera. Mid-exploration: a typography/identity
  round (Claude Design) and an environment-art round (GPT) were both
  started, not finished or picked. Full detail in section 5.
- **Spotify listening stats** — fully scoped, not started. Full detail in
  section 4, including what's actually buildable given real API limits.
- **A true 3D Memory Palace** — a fully rendered, walkable 3D space
  (Three.js/WebGL), game-engine-scale work. Shelved 2026-07-13, not cut —
  full detail in section 9.
- **Android Auto voice capture** — in-car capture surface. Parked 2026-07-15
  (Alek isn't driving yet + it needs Google's app review, which doesn't fit
  the sideloaded-APK flow). Full detail in section 13.

## 1. The near-term core: accounts, AI, and notifications

- ✅ **Multi-user accounts (phase 1)** — DONE and live-verified: email/password
  sign-up/sign-in, password reset, and Google sign-in, sharing one Supabase
  auth session with Sharebox v2. A `profiles` table gives every account a
  global display name. Lives in Settings' new Account section. Fixed one real
  bug the live test caught: the email confirmation link 404'd (missing
  emailRedirectTo). Per-user data isolation for the REST of the app (Tasks,
  Places, Finance...) is deliberately not part of this phase — see the open
  architecture
  question in section 11.
- ✅ **AI-powered Daily Paper** — DONE: an actual AI-written editorial (3-5
  sentences, grounded only in that day's real LifeOS facts), generated with
  whichever provider is active in the AI Assistant's Settings toggle below.
- ✅ **Per-user notifications** — DONE (2026-07-13), scoped down from the
  original framing: a new **Notifications** module (`#/notifications`)
  aggregates the existing due-soon/overdue feed (same `getDueSoonFeed` the
  Dashboard already uses) plus, if you're signed in and in a Sharebox
  space, activity posted by OTHER members since you last checked — the one
  genuinely per-account signal the app has, since the rest of the app's
  data is local-first with no real second "user" to notify. Viewing the
  page marks Sharebox activity as seen; there's no separate unread badge
  in the nav (that's real background-push territory, see below), so this
  is a page you check rather than a push alert. See
  `js/interfaces/default/views/notifications.js`.
- ✅ **AI Assistant (provider-switchable) + Telegram (send-only)** — DONE: a
  new AI Assistant module, a chat called directly from the browser with your
  own API key (Settings, device-local, never synced). Originally Claude-only;
  a Settings > AI Assistant toggle (`aiProvider`) now switches between
  Gemini and Claude, with both providers' keys able to stay filled in at
  once so flipping it costs nothing (see `AI_PROVIDERS` in `js/data/api.js`).
  The toggle is gated behind tapping the "AI Assistant" section label 10
  times, in-memory only (resets on reload) -- everyday key/model fields
  stay unconditionally visible, only the provider switch itself is hidden.
  OpenAI isn't a toggle option -- its API sends no CORS headers for
  browser-origin requests at all (unlike Anthropic's documented
  direct-browser-access opt-in), so a GPT entry would need a proxy server in
  front of it, not just a key. Telegram is send-only: the app
  messages you through a bot you create yourself (@BotFather), triggered by
  your own action (a Settings test button, Daily Paper's "Send to
  Telegram"). No listener for incoming messages by design. Still open:
  - ⏸️ PARKED — A separate ChatGPT panel/toggle option -- needs that proxy
    server first, since OpenAI can't be called directly from the browser. (An
    automated cross-LLM relay chaining one question across all three in
    sequence was considered and struck from the plan — trades real signal
    for compounding drift/hedging without a human deciding what to keep at
    each hop. Could still be built as a separate standalone project later
    if wanted; just not part of the core app.)
  - Full two-way Telegram chat — needs a real backend webhook (a Supabase
    Edge Function) since a static PWA can't listen for incoming messages
    when it's not open; deliberately out of scope for the send-only pass.
- ✅ **AI-written yearly recap** — DONE: Milestones' Yearly Recap now has an
  AI-drafted narrative (whichever provider is active) on top of the
  numbers-driven aggregation, grounded only in that year's real stats and
  named milestones, cached per year and signed-in account.
- ✅ **Passkey/biometric app lock** — DONE: a Settings > App Lock section
  enrolls a WebAuthn platform passkey (Face ID/Touch ID/fingerprint/Windows
  Hello/device PIN); once on, the whole app is gated behind it before any
  data renders on boot. Purely a local device gate, not a remote-auth
  system or encryption — there's no server to verify against, so a
  successful `navigator.credentials.get()` ceremony is itself the proof,
  and the private key never leaves the device's secure enclave/TPM either
  way. Off by default; gracefully hides the option on devices/browsers with
  no platform authenticator. See `js/data/applock.js`.
- ✅ **AI with continuity** — DONE: the Daily Paper's editorial is no longer
  stateless day to day. Each finalized issue is saved to a new `paperIssues`
  store (one per local date + owner), and the last few (oldest first) are
  handed back to the AI as part of its source packet the next time it
  writes, with explicit instructions to reference one only when a genuine
  callback adds value — never forced, never inventing what a past entry
  said beyond what's shown. See `saveEditorialIssue`/`getRecentEditorials`
  in `js/data/api.js`.
- ✅ **App-wide spaced repetition** — DONE: a new **Recall** module
  generalizes the Languages module's flashcard SRS engine to resurface any
  record in the app — a task, a book, a contact, a place, anything Search
  can find. Reuses the Knowledge Graph's own foundations rather than
  duplicating them: schedulable = searchable (same `globalSearch` picker
  grammar as the graph's "add a connection"), and titles resolve live via
  `resolveGraphNode` instead of a second lookup table. Grading uses the
  identical interval scheme as language cards (again/good/easy). See
  `js/interfaces/default/views/recall.js`.
- ✅ **Predictive forecasting** — DONE: The Almanac's correlation section now
  has a companion Forecasts section, same "not enough data yet" honesty,
  three genuinely computed forecasts over real logged history (no AI, no
  invention): a linear-regression bill-spend trend + next-month projection
  (BillPayments), per-habit weekday breakpoint detection flagging the
  weekday you're statistically most likely to skip (HabitLogs), and a
  reading-pace extrapolation to an estimated finish date for in-progress
  books (ReadingLogs). Each requires its own minimum sample before showing
  anything. See `js/interfaces/default/views/almanac.js`.
- ✅ **What-if simulation sandbox** — DONE: a companion "What If" section on
  the Almanac forks a forecast instead of just stating it. A sleep slider
  refits the real sleep-vs-habits regression live as you drag it (+/-2h in
  15-min steps) and projects habits/day at that sleep level; a subscription
  checklist sums selected subscriptions' yearly cost live as you check them
  off. Both computed from real data on every input change, nothing
  precomputed or AI-generated.
- ✅ **AI-suggested knowledge-graph edges** — DONE: an "AI-suggested
  connections" section on the Knowledge Graph proposes non-obvious links
  from the focused record. Same anti-hallucination discipline as the Daily
  Paper/Milestones narrative: the AI only ever gets a closed, numbered
  candidate list of real records (never free text) and can only pick
  indices from it, so it can't invent a connection to something that
  doesn't exist. Manual/button-triggered, not automatic, to keep the API
  call opt-in. See `suggestGraphEdges` in `js/data/api.js`.
- ✅ **Camera-to-data capture** — DONE, scoped to Documents (not Finance, for
  this pass): a "📷 Scan a document" photo/file input sends the image to
  the active AI provider's vision input, which drafts
  title/category/issuer/policy-number/expiry/notes — never guessing, null
  for anything not clearly legible — and opens the result as a normal
  editable record (the photo attaches automatically) rather than a
  silently-trusted final save. Required extending the multimodal `content`
  shape (text + image segments) into both `gemini-client.js` and
  `claude-client.js` so either active provider works. See
  `extractDocumentFromImage` in `js/data/api.js`.
- ✅ **Rules & automation engine** — DONE, scoped to a small fixed set of
  built-in rules rather than a general rule-builder/DSL: (1) log a
  Milestone when a habit streak crosses 7/30/100/365 days, and (2) create a
  "Renew: <title>" Task when a Document is expiring or expired and no
  renewal task exists yet for that specific expiry date. Both off by
  default (Settings > Automations) since they mutate your data on your own
  behalf, and both idempotent — re-running the check on every app open
  never double-fires. Deliberately does NOT include an automatic Telegram
  send for the classic "bill due soon and unpaid" example:
  `telegram-client.js` is explicitly user-triggered-only by design, and
  Dashboard already surfaces due-soon unpaid bills unconditionally, so
  automating that display again would add nothing new — the document-
  renewal rule is the "surface + act" example instead, since it creates a
  genuinely new record rather than re-showing something already shown. See
  `runAutomations` in `js/data/api.js`.
- ✅ **Health-device ingestion** — DONE, Apple Health only (Garmin/Fitbit
  each need their own OAuth/API research pass first, same treatment
  already given Spotify/YouTube/Google Photos — not attempted). A one-time
  import: pick a raw `export.xml` or the `.zip` Apple's Health app
  produces (unzipped client-side with the already-vendored fflate), parses
  the real, publicly documented Apple Health XML schema, and aggregates
  the many fine-grained `<Record>`/`<Workout>` elements down to Health's
  one-row-per-day shape. Always previews a day count before writing
  anything, and merges field-by-field into any existing log for that date
  rather than overwriting the whole record. See
  `js/data/apple-health-import.js`.

## 2. ~~The four original Tier-2 architecture items~~ — ALL BUILT ✅

Knowledge Graph, The Orrery, Time Machine, and QR Airgap Sync all shipped —
see PROJECT_SPEC.md's Built section for what each became. (Time Machine
shipped as the agreed lightweight approximation; the event-sourced core in
section 7 below still upgrades it to true any-point-in-time reconstruction
when it lands.)

## 3. Remaining routine builds (no new accounts/APIs needed)

All five routine builds identified here have shipped:

- ✅ **Geofenced notes-to-self on Places** — a short note attached to a place
  that resurfaces the next time "Check nearby places" finds you within range.
- ✅ **Practice Log (Chords)** — a freeform date/duration/notes log on its
  own Log tab, separate from the auto-tracked drill-accuracy stats.
- ✅ **Live currency conversion** — Tools' Currency tab now pulls live rates
  from Frankfurter/ECB, auto-refreshing when stale, with a clean offline
  fallback to the last-cached/manual values.
- ✅ **Weather context** — Dashboard and Daily Paper show current conditions
  from Open-Meteo, opt-in via "Use my location," cached for an hour.
- ✅ **Price tickers (crypto)** — a new Crypto tab in Finance: a watchlist of
  CoinGecko coin IDs with live price and 24h change, cached for five minutes.

## 4. External integrations (new OAuth flows or paid third parties)

Each of these is its own 🔑 go/no-go, not a routine add.

- ⏸️ PARKED — **Financial Center** — bank/investment linking via **Plaid**,
  plus a live price-ticker panel.
  - Plaid Sandbox (fake data, for building) is free forever.
  - Plaid Production is usage-based: roughly **$0.30–$3 per connected
    account/month** for subscription-style products (Transactions,
    Investments), or **$0.10–$0.60 per call** for one-time-fee products
    (Auth, Identity, Income). Plaid doesn't publish one flat price list;
    meaningful scale usually means a negotiated contract with a monthly
    minimum. Needs its own backend token exchange (rides on the Supabase
    backend — the secret key can never touch the browser). The most
    security-sensitive item on this whole document.
  - Crypto tickers: CoinGecko, free/keyless.
- ⏸️ PARKED — **Spotify listening stats** — needs its own new OAuth flow (a
  third, alongside Google and Supabase). Scoped in detail (2026-07) before
  parking, so this doesn't need re-researching later:
  - As of Feb 2026, Development Mode requires a **Spotify Premium
    account** on the developer's own account just to use the API at all.
  - Real-time API access is thinner than it used to be. Available: Recently
    Played (last 50 tracks only, rolling window), Top Items (ranked
    artists/tracks across 3 time windows — ~4wk/~6mo/years — but ranking
    only, no counts attached), Currently Playing, saved/liked
    tracks+albums, own playlists (read).
  - **Dead, no workaround**: Audio Features/Analysis (danceability,
    energy, valence, tempo) — killed for all new apps in Nov 2024. No
    "your music got sadder in March" style analysis is buildable by
    anyone anymore, not a LifeOS-specific gap.
  - **No raw totals via API at all** — no total minutes listened, no
    per-track play counts, no full listening history. This has been a
    top-requested, permanently-denied feature on Spotify's own developer
    forum for years.
  - The real path to genuine "all-time" stats (confirmed by how stats.fm/
    Spotistats actually does it, not speculation): a ONE-TIME manual
    import of Spotify's own "Extended Streaming History" data export
    (Privacy settings → Download your data → up to 30 days to arrive as a
    ZIP) for real historical backfill, same shape as LifeOS's existing
    manual JSON import elsewhere, PLUS ongoing periodic polling of
    Recently Played to keep it current going forward. Live API alone can
    never give more than rankings + a thin recent window.
- ✅ **Google Photos import** — DONE: a "📥 Import from Google Photos"
  option per album (Photos/Gallery), via the Photos Picker API. Reuses the
  existing Google sign-in with a new scope. Not a live sync — Google
  retired third-party bulk read access to a user's whole library in March
  2025, so this opens Google's own picker UI and downloads only what you
  explicitly select each time. See `js/data/photos-picker.js`.

*(Ruled out — dead, not parked, don't resurface: WhatsApp and Instagram
personal DMs (their Business/Messaging APIs only handle new messages sent
to a business-designated presence, don't expose existing personal chat
history, and Meta's App Review screens out "personal archiving" as a use
case anyway). YouTube real watch history / Watch Later sync (both
deprecated from the API in 2016 for privacy reasons, no workaround exists;
Liked Videos is a different, noisier signal than the manual watch-later
queue already in Links, not worth building instead). Stock tickers
(Alpha Vantage/Finnhub) — not wanted, killed outright, independent of
Plaid/Financial Center staying parked above.)*

## 5. Additional interfaces

- ✅ **Vespera** — STARTED and well underway (not a future idea anymore): a
  spatial interface, Life OS as an orbital station you navigate through, not
  a dashboard. Hub (Grand Concourse) with 9 district plaques, district rooms,
  hotspot/portal geometry actively being refined. See `VESPERA_SPEC.md` and
  the Built ✅ section of `PROJECT_SPEC.md` for current state. **Desktop
  only** — not part of the mobile/APK experience, see the device
  philosophy note below.
- ✅ **mobile-1** — the first of what's meant to be several interchangeable
  mobile interfaces (no permanent "name brand" — see
  `MOBILE_INTERFACES_SPEC.md`'s naming decision). Mobile-first, a new
  dedicated interface (not a filtered Equator), built from Alek's own
  mockup image with real click regions mapped onto it, a curated
  on-the-go module set, real motion + sound, alert-styling tied to actual
  due/overdue data. See `MOBILE_INTERFACES_SPEC.md` for the draft module
  list and remaining open questions (mobile-only gating, full module
  coverage not yet reconciled).
- ⏸️ PARKED — **Bio-futurism/jungle interface** — a third, from-scratch
  interface concept: a futuristic tropical/jungle theme ("40th century meets
  the Amazon meets the far reaches of the galaxy"), unrelated to Vespera or
  the mobile interface(s) project. Mid-exploration when parked: a
  typography/identity round (via Claude Design, one direction returned so
  far) and an environment-art round (via GPT, spec/prompt drafted) were
  both started but not finished or committed to. Pick back up only when
  Alek brings it up by name.
- Any others that come to mind along the way.

### Device philosophy — desktop is the full app, mobile is a remote (revised 2026-07-12)

Desktop is the complete app — every module, through either Equator
(rail+canvas default) or Vespera (the spatial alternative). Mobile is
deliberately **not** a second complete copy of it: a genuinely
stripped-down "controller in your pocket," built around what's actually
useful away from a desk — quick capture, on-the-go actions, glanceable
status — not full parity. No Vespera, no spatial "living world" on
mobile; that register stays desktop's. Mobile gets its own purpose-built
interface(s) (`MOBILE_INTERFACES_SPEC.md`), not a filtered version of
Equator.

Sync model: the same underlying data everywhere (Drive/Supabase, exactly
as it already works today) — this is a UI-surface decision, not a
data-sync restriction. Every device stays fully synced regardless of
which modules its interface chooses to show.

This replaces the earlier "mobile is fully capable standalone, same UI at
a smaller breakpoint, unlocks casting/remote-input extras when desktop's
live" framing below it used to have — superseded after further
conversation. The presence-based casting idea isn't part of the current
plan, but nothing rules out revisiting it later once the mobile remote
itself exists.

## 6. Sharebox, beyond the in-app module

The in-app Sharebox module is built (Supabase v2, RLS fixed, working).
✅ **Friend-mesh already works, no build needed** — `sharebox_members` is a
plain many-to-many table (space_id + user_id), `joinSpace()` just adds
whoever calls it with a known space ID, and RLS scopes by membership, not
by pair. Nothing in the schema or code assumes exactly 2 people; it's just
never been exercised with 3+ real people yet.

(Ruled out — dead, not parked: a separate lightweight companion app for a
friend who doesn't run full Life OS. Alek's call: anyone he'd actually
share with already has the full app anyway, so there's no real audience
for a stripped-down version.)

## 7. Moonshot tier — round 1: AI, rearchitecture, capture

🌙 unless noted.

**AI & intelligence**
- ✅ **Semantic memory across your whole life** — DONE (2026-07-13). A new
  **Ask** module (Insight group): type a natural-language question and get
  the actual records ranked by meaning. Every indexable record is embedded
  (Gemini `text-embedding-004` — Gemini-only, since Anthropic has no
  embeddings API) into a device-local vector index (`embeddings` store, not
  synced), and a query embeds the same way for client-side cosine-similarity
  ranking. The index builds/refreshes on demand and incrementally (only
  changed/new records re-embed; gone records prune). See
  `buildSemanticIndex`/`semanticSearch` in `js/data/api.js`. (v1 ranks +
  links to the matching module; a generative "here's the answer" summary on
  top is a natural follow-up.)
- ✅ **Chief-of-staff (v1)** — DONE (2026-07-13). A new **Briefing** module
  (Insight group): a PRIORITIZED worklist of what actually needs you now,
  distinct from the Daily Paper's read-only editorial. It triages by urgency
  and catches non-obvious risks — overdue/due-soon bills·tasks·assignments,
  expiring documents, habit streaks about to break (established habit, not
  checked in today), rabbit holes gone cold (untouched 21+ days) — each with
  a one-tap suggested action where it helps (check in the habit, snooze the
  task, create a renew task). All real computed data, no AI. See
  `getBriefing` in `js/data/api.js`.
  **Still open — the "autonomous" half:** acting *without asking*, for
  pre-approved categories. v1 is propose-and-approve; true autonomy is a
  trust/design follow-up (what it's allowed to touch on its own).
**Foundational rearchitecture** — moved to their own section (§12,
"Conditional rearchitecture") 2026-07-13. Both event sourcing and CRDT sync
are deferred-with-a-trigger, not pending work; see there.

**Capture & reach**
- ✅ **Real background push** — DONE (2026-07-13), proven live end to end.
  Web Push: the client subscribes (`js/data/push.js`, Settings toggle) and
  stores the subscription in Supabase; a scheduled Supabase Edge Function
  (`supabase/functions/send-push`) reads each user's due-soon items from
  `sync_records`, signs a VAPID payload, and sends it; the service worker's
  `push` handler shows the notification even with the app closed. Deployed
  from GitHub Actions (no local tooling). Verified: a due task synced to the
  cloud + push on -> the function returned `{"sent":1}`. See
  SUPABASE_MIGRATION.md for the setup. (v1 sends a daily digest without
  cross-day dedup -- a later refinement.)

*(Ruled out 2026-07-13, dead, not parked, don't resurface: a fully local
in-browser LLM (WebGPU/WebLLM). Alek's call — the multi-GB model download
changes the app's whole size class for value that only lands in a scenario
he's rarely in (offline), and the offline AI garnish isn't a deal-breaker
when it is. Offline storage already works without it; the API-key AI
features simply resume on reconnect. The privacy angle (AI calls never
leaving the device) was weighed and judged too narrow to justify it, since
data already lives local-first and only the AI calls reach out.)*

## 8. Moonshot tier — round 2: capture, privacy, self-awareness, platform

- ✅ **Natural-language command bar** — DONE (2026-07-13): the **Command**
  module (Core group, on the mobile remote too). Type or speak a plain command
  ("remind me to call mom Friday", "note: try the new ramen place", "add rent
  bill $1200 due the 1st") and the active AI provider parses it into one
  structured action — task / idea / contact / bill / habit check-in — resolving
  relative dates from today. Same closed-output discipline as the vision scans;
  the interpretation is shown for a one-tap Confirm before anything's written.
  Voice reuses the browser's SpeechRecognition (a 🎤 button when supported) —
  NOT in-browser Whisper, which is the same multi-GB size-class problem that
  killed the local LLM. See `parseCommand` in `js/data/api.js`.
- ✅ **Real financial ingestion & reconciliation** — DONE (2026-07-13),
  CSV only for this pass — OFX is a real second parser (SGML-like, not
  delimited text) not worth the added surface until CSV proves useful.
  Finance's new **Import** tab parses a bank/card CSV export (tolerant of
  a few common header conventions, incl. separate debit/credit columns),
  suggests a Bill/Subscription match per row by description + amount
  (plain string/number comparison, no AI, works fully offline), flags
  likely re-imports of the same rows, and on confirm logs a `BillPayment`
  + marks the matched bill paid, same effect as checking it off by hand.
  See `parseTransactionsCsv`/`suggestTransactionMatches`/
  `confirmTransactionImport` in `js/data/api.js`.
- **Garmin/Fitbit health ingestion** — Apple Health export import shipped
  (see Built ✅ below); Garmin/Fitbit would each need their own OAuth/API
  research pass first (same "verify before building" treatment already
  given to Spotify/YouTube/Google Photos) — not attempted yet.
- **Zero-knowledge encrypted vault** — end-to-end encryption at rest, so
  data synced to Drive/Supabase is stored as ciphertext only — even the
  server can't read it. Pairs naturally with multi-user accounts.
- **Personal local API + plugin SDK** — expose your own data through a
  documented local interface so you (or scripts) can build on it without
  touching core — the same registry philosophy that already governs
  interfaces, extended to data. What turns this from an app into a platform.
- **Generative "Year in Review" film** — an auto-produced montage from your
  photos + milestones + stats, scored by the Life-as-Music synth engine
  you already have.
- ✅ **Camera-vision cataloging (Quartermaster)** — the core flow shipped
  2026-07-13: a "📷 Catalog from a photo" input on Quartermaster sends a
  shelf/pantry/garage photo to the active AI provider's vision input,
  which drafts a list of distinct items (name + rough location in frame);
  the result opens as an editable list — remove anything wrong, fix names
  — before bulk-creating InventoryItems, never silently trusted. As
  planned, no attempt at detecting *quantity* or "running low" from the
  photo automatically (fill-level estimation from a single image is a
  genuinely harder vision problem, and "low" is subjective anyway); that
  stays a manual flag on the item. See `catalogItemsFromImage` in
  `js/data/api.js`.
  **✅ Few-shot low-stock detection DONE (2026-07-13).** On each Quartermaster
  item, a "📦 Stock" panel: add labeled reference photos ("low", "full", your
  own words), then "Check stock from photo" sends a new photo + your 5 most
  recent labeled references to the vision model, which places the new photo
  relative to them and sets the item's stock status. All images are
  compressed client-side first (`compressImageForVision`, ~1200px long edge),
  and that compression was also retrofitted onto the Documents scan and the
  catalog scan. See `judgeStockFromImage`/`createStockReference` in
  `js/data/api.js`. Original design notes below, now built:
  Not model retraining — a labeled-example
  flow. When logging an item, you can tag a reference photo with your
  own label ("low," "full," whatever vocabulary makes sense to you).
  Judging a later photo of that item sends the new photo *plus* your 5
  most recent labeled examples to the vision model as calibration
  references, asking it to place the new photo relative to them. 5 is a
  flat cap — the cost difference between 3 and 5 reference images is
  negligible, especially compressed/low-res. Approximate placement
  ("low" vs. "full") is the goal, not precise quantity, which is a
  harder problem and not what this is for. Accuracy improves as your
  label library grows — not because the model changed, but because each
  judgment has more of your own examples to work from. Also more
  buildable than the far-tier framing implied: it's an extension of the
  multimodal vision-AI pattern already shipped for Documents'
  camera-to-data scan (`extractDocumentFromImage` in `js/data/api.js`),
  not a new capability — "what items are in this photo" instead of "what
  fields are on this document." Once built, pairs naturally with a third
  Rules & Automation rule: an item flagged low auto-creates a "buy X"
  task, same off-by-default/opt-in shape as the habit-milestone and
  document-renewal rules already shipped. **Confirmed cost approach
  (2026-07):** resize/compress client-side before sending to the vision
  API — cap the long edge around 1000-1500px, no reason to send a
  full-resolution native photo when object recognition doesn't need
  anywhere near that much detail. Worth retrofitting onto the existing
  Documents camera-to-data scan too, which currently sends full
  resolution.
- ~~Academic pacing check~~ — DONE (2026-07, see FEATURE_LIST.md's Built
  section, Education entry).

## 9. Far tier 🪐 — round 3: scale-and-space swings

*(Excluded from default "what's on our list" recaps as of 2026-07 — Alek's
call, see CLAUDE.md. Stays fully written here; just not surfaced unasked.)*

Each of these is closer to its own product than a feature — heavy on
engineering, storage, or compute, and genuinely speculative.

- ⏸️ PARKED (2026-07-13) — **A true 3D Memory Palace** — not flat districts
  (spatial-1) but a fully rendered, walkable 3D space (Three.js/WebGL):
  rooms per module, physical objects representing records, real
  lighting/physics. Game-engine-scale work; 3D assets/textures push
  storage into the hundreds of MB+. Shelved, not cut — Alek still plans to
  use the concept, just not now.
- **A true digital twin / life-simulation engine** — beyond the what-if
  sandbox's simple parameter nudges: an actual agent-based model of you,
  with internal state and feedback loops, run forward from your real data
  to project "future you" — potentially rendered as a visual avatar that
  visibly evolves over months.
- **A full bidirectionally-linked personal wiki, built natively** — a real
  Obsidian/Roam-style note system inside the OS: markdown, backlinks,
  embeds, transclusion, a living graph view. Its own large, ever-growing
  note archive.
- **A real trained ML pattern engine, not just correlation** — The Almanac
  does simple Pearson correlation on curated pairs; this would be an actual
  model that continuously retrains on your entire history and surfaces
  genuinely non-obvious, non-linear patterns (multi-variable interactions a
  human would never manually think to check).
- **A generative "personal mythology"** — an illustrated, ever-growing
  storybook of your life: the app periodically composes and illustrates
  (local image generation) an evolving fantasy chronicle where real
  milestones and habit streaks become "quests completed" and "trials
  overcome." Skill Trees taken to its most maximalist extreme — exportable
  as an actual book.

*(Ruled out 2026-07-13, dead, not parked, don't resurface: a local
continuous "life recorder" (screen capture + OCR) and a local
Whisper-powered audio diary with auto-routing — both cut as part of the
same scope-tightening pass as Languages/Chords/Dream Journal/Conversation
Starters. Generative dream visualization is also cut — it was explicitly
built on Dream Journal entries, and that module no longer exists. A full
multi-generational family archive is also cut.)*

## 10. Explicitly out of scope

- A standalone music-practice app (progressions, play-along, melody-aware
  voicing) — deliberately kept separate from Life OS.

## 11. The one open architecture question underneath all of this

Whether the rest of the app's modules (Tasks, Places, Finance, etc. —
currently local-first in IndexedDB) eventually move to the same Supabase
backend as Sharebox v2/accounts ("full cloud"), stay local with just
accounts+Sharebox+notifications on Supabase ("hybrid"), or that decision
gets deferred further (the phased approach, currently in progress: prove
accounts+AI Paper first, decide the rest later). Several items above
(encrypted vault, event sourcing, CRDT sync) would look different
depending on which way this goes, so it's worth resolving before going too
deep into any one of them.

**Update (2026-07-13):** largely settled toward **hybrid** — the personal-data
Supabase sync shipped (see `SUPABASE_MIGRATION.md`), so any device can now
sync the whole app through Supabase, but **IndexedDB stays the source of
truth and offline is unchanged** (Supabase is the sync transport, not the
home of record). The remaining "depends on this" items are the §12
rearchitectures below.

## 12. Conditional rearchitecture — someday, only if a real need appears

*(Not on the active list — treated like the Parked section and the Far tier:
excluded from default "what's on our list" / status-recap answers. These are
foundational data-layer REWRITES, not features — high blast radius, dangerous
half-done, and each solves a problem that isn't pressing today. Each has a
concrete TRIGGER that would justify taking it on; until that trigger fires,
building it speculatively is high-risk work for a maybe. Deferring costs
almost nothing — the current architecture works and there's no lock-in
penalty to waiting, because when a trigger does fire you'd design against
real requirements instead of guessing.)*

- **Event-sourced core** — every change becomes an immutable event instead of
  an in-place edit; current state is derived by replaying events. Unlocks real
  undo/redo everywhere, per-record history, and true point-in-time Time
  Machine. *Cost:* rewrites the entire data layer (~40 modules' write paths +
  everything that reads records + sync), unbounded event-log growth, a
  permanent complexity tax. *Trigger:* you genuinely want undo/history
  everywhere as a priority. *Cheaper 80% first:* a scoped per-record
  change-log on 1–2 high-value modules (Tasks, notes) gives history/undo
  where it actually matters without rearchitecting anything — do that before
  ever considering the full version.
- **CRDT-based sync** — replace last-write-wins with conflict-free merge, so
  concurrent edits to the SAME record combine automatically instead of one
  silently winning. *Cost:* every field becomes a CRDT type carrying merge
  metadata (bloats every record), likely a heavyweight library (Yjs/Automerge,
  WASM — cuts against the no-build lightweight ethos, same size-class concern
  that killed the local LLM), and it reworks the just-proven LWW sync.
  *Why deferred:* the problem barely exists here — single user, two devices,
  and even a handful of users each have their OWN data (nobody co-edits the
  same records; Sharebox, the only shared surface, already uses a
  server-authoritative model, not LWW). *Trigger:* the app gains **real-time
  multi-user editing of the same records** (e.g. Sharebox grows into shared
  editable documents). Until then LWW is correct.

## 13. Native platform tier — Capacitor (decided 2026-07-15)

> **Build status (2026-07-15):** the Capacitor Android foundation is BUILT and
> CI-verified — the app packages into an installable APK via GitHub Actions with
> no local machine (see `CAPACITOR_BUILD.md` for the web-only build/install
> runbook). Also built: the capability-detection layer (`js/native/`) every
> feature gates through, plus these native features (all CI-verified, all a
> no-op on web/iOS): **device reminders** (local notifications), **outbound
> share**, **keep-awake / cooking mode**, **read-aloud briefing**, **home-screen
> app shortcuts + deep links**, **actionable notification buttons** (Mark paid /
> Mark done / Snooze / Renew straight from the reminder), a pinned **"next up"
> ticker** (persistent notification of the top Briefing item, opt-in), and the
> **inbound system share sheet** (LifeOS in Android's Share menu → links to
> Links, text to Ideas). The rest of the wishlist below is staged, wake word
> included (needs Alek's Picovoice key + on-device tuning). On-device receive/
> behavior tuning for the share sheet is the next iteration point.

**The platform strategy, settled by Alek:** the same vanilla-JS codebase
ships as (a) a **native Android app via Capacitor** — first and the
priority; (b) a **full Windows `.exe`** and (c) a **macOS app** later
(Capacitor's Electron platform or equivalent wrapper — same "web app in a
native shell" trick, desktop edition); (d) **iOS stays the browser
PWA** — no Apple Developer account, no native iOS build. iOS gets the
graceful-degradation version: native-only features hide behind capability
checks rather than breaking.

**The headline feature, and part of why Capacitor was chosen at all: a
true always-on wake word.** "Hey LifeOS" (or whatever you pick) from
across the room, screen off, app closed — a foreground service holding
the mic, on-device keyword spotting (Picovoice Porcupine or openWakeWord;
both support **custom, user-chosen wake phrases**, so it's customizable
per person and changeable later — a settings choice, not a hardcode),
flowing into the Command module's existing parse-confirm-create pipeline.
Explicitly chosen over the "borrow Google Assistant's ear" shortcut —
that shortcut is dead (see ruled-out note below), we're building our own.

The rest of the native wishlist, curated 2026-07-15 (everything here
survived Alek's cut):

**Voice & audio**
- Continuous ambient transcription sessions (meeting/walk → transcript →
  Ideas/Tasks; browsers kill a backgrounded mic, native doesn't)
- Voice memos with screen off, auto-attached to records
- Offline text-to-speech Briefing ("read me my morning" — native TTS is
  free, offline, keyless)
- Fully offline speech-to-text for Command (on-device model via plugin —
  no cloud, no API key, airplane-mode voice)

**Capture**
- **System share sheet** — LifeOS in Android's Share menu from any app →
  Links/Ideas/Places/Photos. The single biggest capture-friction killer.
- Clipboard catcher — copy anything anywhere, LifeOS offers to file it
- Screenshot reflex — screenshot detected → "file this?" → OCR into
  Ideas/Links/Finance
- Volume-button / hardware-button quick voice capture, phone in pocket
- App shortcuts (long-press icon → New Task / New Idea / Scan / Speak)
- Native document scanner (live edge-detection, auto-crop, multi-page →
  Documents)
- Barcode/ISBN scanner (books → Books with metadata; pantry →
  Quartermaster)
- Text-selection menu — highlight text in ANY app → Android's selection
  popup offers "Send to LifeOS" (finer-grained than the share sheet:
  works on any selectable text, sentence-level capture)
- **Auto-sorting file inbox** — watch the Downloads folder (and similar
  landing zones); new files get classified and offered to the RIGHT
  destination, not just dumped in Documents: airline confirmations /
  travel docs → Documents' travel category, bills/statements → Finance +
  Documents, receipts, IDs, tickets. Classification via the existing
  vision/AI extraction pipeline where needed, plain heuristics
  (filename/sender/type) where they suffice.

**Location & presence**
- Background geofencing (grocery store → Quartermaster low-stock list)
- Passive on-device location journal feeding Ghost Days / Time Machine
- Arrive/leave triggers (leave work → evening Briefing; arrive home →
  habit check-in)
- Wi-Fi network triggers — "joined home/work Wi-Fi" as a presence signal:
  cheaper and more private than GPS for the home/work cases
- Offline map tiles for Places — your places on a real map with zero
  connectivity

**Notifications & background**
- True local scheduled notifications — bills, time capsule unlocks, habit
  nudges fire on-device, offline, no Supabase round-trip
- Full-screen alarm-class alerts for the big moments (time capsule opens)
- Background sync on a real schedule, app closed
- Notification harvesting (with permission, read other apps'
  notifications: bank alert → Finance draft, delivery → package note)
- Actionable notifications — buttons on the notification itself ("Habit
  reminder — ✓ Done / Snooze", "Rent due — Mark paid"): act on LifeOS
  without opening it
- Persistent "next up" ticker — an optional pinned notification always
  showing the top Briefing item
- Charging-cable ritual — plugging in at night is the end-of-day trigger:
  run sync, fire the evening review prompt, write the nightly local backup

**Passive intelligence**
- Photo auto-ingest (new camera photos considered automatically: EXIF →
  Places, dates/faces → Milestones suggestions, best → Photos/Museum)
- Screen-time mirror (phone usage stats → Entropy/Health — your attention
  data in your own vault)

**Physical world & devices**
- Home-screen widgets + quick-capture tile (Briefing widget, habit-streak
  widget, two-tap camera-to-Quartermaster)
- NFC tags (tag on the pantry shelf → tap → that shelf's restock list;
  tag on the door → packing list)
- Bluetooth item beacons — cheap BLE tags on physical things in
  Collections/Museum, so "where is my X" answers with real proximity
- Nearby/Wi-Fi Direct device-to-device sync — full-bandwidth local sync
  with no internet at all (QR Sync's concept, grown up)
- Wall/kiosk mode — an old tablet as a permanently-mounted always-on
  LifeOS panel (Briefing, habits, low stock)
- Cooking mode keep-awake in Recipes (screen stays on, voice "next")

**Trust & reliability**
- Native biometric gate (strengthens the existing WebAuthn app lock)
- Real installed-app storage durability (no browser eviction anxiety)
- Boot persistence — wake word + geofences restart when the phone reboots
- Phone contacts / calendar read → one-tap import into Contacts /
  Milestones / Briefing

⏸️ PARKED — **Android Auto voice capture** (see §0): a real in-car
dashboard presence. Needs Google's app review + Play distribution, which
doesn't fit the sideloaded-APK flow, and Alek isn't driving yet. The wake
word covers in-car capture whenever it matters anyway.

*(Ruled out 2026-07-15, dead, not parked, don't resurface: bank-SMS
ingestion (parsing "you spent $X" texts into Finance), call-log awareness
("you haven't called Dad in 5 weeks" nudges), activity-recognition
auto-logging (walking/cycling detection auto-checking habits), Google
Assistant App Actions ("Hey Google, add X to LifeOS" — superseded by
going straight to our own full wake word instead of bridging through
Google's), floating capture bubble (chat-head overlay), alarm-clock
handoff (alarm dismissal → TTS briefing), `.lifeos` file association +
auto local backups to SD/USB, Chromecast casting, a Wear OS companion,
Tasker/intents automation hooks, haptic feedback patterns, and step
counter / raw motion-sensor access feeding Health.)*

## 14. Smart-home / home-automation tier (logged 2026-07-15)

**The vision:** every light and smart device Alek owns — regardless of
which app or radio it came with — on **one list inside LifeOS**, controlled
**locally**, from home or 500 miles away. Ties into §13: geofence "arrived
home" → lights on; the charging-cable bedtime ritual → whole house to night
mode; Briefing surfaces "you left the garage light on."

**The core architecture decision: don't build N vendor integrations —
bridge through one hub.** LifeOS talks to a single **Home Assistant** local
API (REST + WebSocket, long-lived token); Home Assistant does the dirty work
of speaking every device's language. LifeOS speaks one clean language to HA;
HA speaks Tuya, Bluetooth, Matter, Zigbee, etc. to the gear.

**Why a hub at all (and why it enables remote control of local-only
devices):** the hub is a small always-home machine (Raspberry Pi / old
laptop / ~$100 mini PC) that stays physically in the house, in range of the
short-range radios. You reach the *hub* over the internet; the hub performs
the short-range hop locally. So even **Bluetooth-only lights are
controllable from anywhere** — you → internet → home hub → local Bluetooth →
bulb. The hub is your "hands" in the house; remote access only needs to
reach the hub, never the individual device. Remote reach via **Tailscale/
WireGuard** (free, private, phone + hub on one encrypted network — LifeOS
sees the hub as if on the home LAN; the local-first choice) or **Home
Assistant Cloud / Nabu Casa** (~$6.50/mo, zero-config). Conditions: hub +
any BLE bridge stay powered/in-range at home, and home internet is up.

**Alek's real-world gear (the test case), and each one's local path:**
- **Smart Life → Tuya, Wi-Fi.** The easy one. Fully local via HA's
  LocalTuya / tuya-local (extract each device's local key once, then LAN
  control, no cloud). ([tuya-local](https://github.com/make-all/tuya-local),
  [localtuya](https://github.com/rospogrigio/localtuya))
- **DayBetter → Wi-Fi, split personality.** Older units are plain Tuya
  (pair with Smart Life → same easy local path). Post-2021 units use
  DayBetter's **own app on a walled-off slice of the Tuya cloud** — NOT
  visible to Smart Life or the standard Tuya dev API, so those are the
  awkward wildcard; verify in-hand which kind. Some DayBetter strips are
  Bluetooth/IR-only.
- **BrMesh → Bluetooth Mesh, not Wi-Fi.** Uses the Broadlink **"Fastcon"**
  BLE protocol — commands are broadcast as Bluetooth advertisements, no
  cloud, no Wi-Fi, no pairing state (fire-and-forget, which makes it robust
  behind a bridge). Driven by a cheap **ESP32 (~$5)** running a Fastcon→MQTT
  bridge into HA. ([HA thread](https://community.home-assistant.io/t/brmesh-app-bluetooth-lights/473486),
  [ESPHome guide](https://stephencross.site/posts/brmesh-esphome-homeassistant/),
  [brMeshMQTT](https://github.com/ArcadeMachinist/brMeshMQTT))

**The honest truth about "one list":** three different radios (Wi-Fi Tuya,
walled-cloud DayBetter, BLE-broadcast Fastcon) mean LifeOS can NOT unify
them by talking to each directly. The one-list outcome is real but routes
through the HA bridge — HA absorbs all three, LifeOS reads one endpoint.

**How to identify an unknown device (when home):** Wi-Fi setup asking for a
2.4GHz network + works from anywhere → Tuya-class. Only works within
Bluetooth range, no internet needed → BLE mesh (BrMesh). Definitive:
`tinytuya scan` finds Tuya devices + local keys on the LAN; a plain
Bluetooth scanner app shows the BrMesh ones advertising.

**LifeOS side, when built:** a "Home" module reading the HA API — one list
of all entities, toggle/dim/color, scenes tied to routines. Note the
transport wall: a browser PWA on HTTPS GitHub Pages can't reach a local
`http://` hub (mixed-content/CORS), so this pairs naturally with either the
**§13 Capacitor native shell** (native HTTP, no CORS) or a Tailscale/HTTPS
path to HA. Buildable-today alternative for any pure cloud-token brand
(e.g. LIFX): a direct bearer-token integration, device-local key, no hub —
but the hub is the right answer for Alek's actual mixed, local-first gear.

*Prereqs that are Alek's to stand up (not buildable from the repo): the
Home Assistant box itself, the ESP32 BrMesh bridge, and the remote-access
choice (Tailscale vs Nabu Casa). LifeOS's part is the "Home" module +
capability wiring once the hub exists.*

---

Nothing here is fixed in stone. This is meant to be read on its own, but
`PROJECT_SPEC.md` remains the source of truth for what's actually *built* —
check there first if you're not sure whether something in this file already
shipped.

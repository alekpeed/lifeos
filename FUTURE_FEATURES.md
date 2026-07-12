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
- 🏗️ **Per-user notifications** — depends on accounts existing first.
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
- **An LCARS-inspired mode** (Star Trek control-panel aesthetic, original
  execution, no copyrighted assets) — now confirmed as **the mobile
  remote's** visual language specifically, not a desktop reskin. Scoped
  (2026-07): mobile-first v1, a new dedicated interface (not a filtered
  Equator — that fork is resolved), a curated on-the-go module set rather
  than the whole app, real motion + sound, alert-styling tied to actual
  due/overdue data. See `LCARS_SPEC.md` for the draft module list and
  remaining open questions.
- ⏸️ PARKED — **Bio-futurism/jungle interface** — a third, from-scratch
  interface concept: a futuristic tropical/jungle theme ("40th century meets
  the Amazon meets the far reaches of the galaxy"), unrelated to Vespera or
  LCARS. Mid-exploration when parked: a typography/identity round (via
  Claude Design, one direction returned so far) and an environment-art round
  (via GPT, spec/prompt drafted) were both started but not finished or
  committed to. Pick back up only when Alek brings it up by name.
- Any others that come to mind along the way.

### Device philosophy — desktop is the full app, mobile is a remote (revised 2026-07-12)

Desktop is the complete app — every module, through either Equator
(rail+canvas default) or Vespera (the spatial alternative). Mobile is
deliberately **not** a second complete copy of it: a genuinely
stripped-down "controller in your pocket," built around what's actually
useful away from a desk — quick capture, on-the-go actions, glanceable
status — not full parity. No Vespera, no spatial "living world" on
mobile; that register stays desktop's. Mobile gets its own purpose-built
interface, styled LCARS (`LCARS_SPEC.md`), not a filtered version of
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
- **Fully local LLM, zero API key** — a small model running in-browser
  (WebGPU/WebLLM) so the AI Daily Paper, smarter search, and any assistant
  work fully offline, free, with data never leaving the device. The one
  idea that changes the app's *size class* (a bundled/downloaded model is
  hundreds of MB to multiple GB).
- **Semantic memory across your whole life** — embed every record from
  every module into a local vector index, so "when did I last see Sarah?"
  or "what was bugging me in March?" returns a real answer from your actual
  history, not keyword matching.
- **Autonomous daily chief-of-staff** — an agent that reviews everything
  each morning, drafts a prioritized brief (bills at risk, streaks about to
  break, rabbit holes gone cold), and can act on pre-approved simple tasks
  itself.
**Foundational rearchitecture**
- **Event-sourced core** — every change becomes an immutable event instead
  of an in-place edit. Unlocks real undo/redo everywhere and per-record
  history, and makes Time Machine (above) trivial once it exists.
- **CRDT-based sync** — replace last-write-wins Drive sync with a real
  conflict-free merge engine, so simultaneous edits on two devices combine
  automatically instead of one silently clobbering the other.

**Capture & reach**
- **Real background push** — true background sync/Web Push (not just
  foreground-only PWA behavior) so alerts land with the app closed, on
  platforms that genuinely support it.

## 8. Moonshot tier — round 2: capture, privacy, self-awareness, platform

- **Natural-language command bar** — type or speak "remind me to call mom
  Friday" and it parses intent, picks the right module, and creates the
  record. In-browser Whisper handles voice, fully offline.
- **Real financial ingestion & reconciliation** — import bank/card
  statements (CSV/OFX), auto-categorize transactions, reconcile against
  logged bills/subscriptions.
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

## 9. Far tier 🪐 — round 3: scale-and-space swings

Each of these is closer to its own product than a feature — heavy on
engineering, storage, or compute, and genuinely speculative.

- **A true 3D Memory Palace** — not flat districts (Vespera) but a fully
  rendered, walkable 3D space (Three.js/WebGL): rooms per module, physical
  objects representing records, real lighting/physics. Game-engine-scale
  work; 3D assets/textures push storage into the hundreds of MB+.
- **A local, continuous "life recorder"** — periodic screen capture + local
  OCR, indexed and searchable (a fully private, self-hosted Rewind.ai).
  Massive ongoing storage growth (easily GB/month), a real local vision
  pipeline, and the most privacy-sensitive idea here — has to be 100%
  local, never synced, by design.
- **A local Whisper-powered audio diary with auto-routing** — talk instead
  of type; a large local speech model transcribes and classifies what you
  said, filing it into the right module itself (a dream to Dream Journal, a
  tangent to Rabbit Holes). Requires a multi-GB local model plus real
  classification logic.
- **A true digital twin / life-simulation engine** — beyond the what-if
  sandbox's simple parameter nudges: an actual agent-based model of you,
  with internal state and feedback loops, run forward from your real data
  to project "future you" — potentially rendered as a visual avatar that
  visibly evolves over months.
- **A full bidirectionally-linked personal wiki, built natively** — a real
  Obsidian/Roam-style note system inside the OS: markdown, backlinks,
  embeds, transclusion, a living graph view. Its own large, ever-growing
  note archive.
- **Generative dream visualization** — feed Dream Journal entries into a
  local generative image/video pipeline and render what you dreamed — short
  animated scenes or stills per entry, with generated ambient audio. Heavy
  compute and storage (every dream becomes a media file).
- **Camera-vision auto-cataloging** — point your phone at a bookshelf or a
  garage full of stuff; on-device vision recognizes and catalogs everything
  into Books or Quartermaster automatically, no manual entry.
- **A real trained ML pattern engine, not just correlation** — The Almanac
  does simple Pearson correlation on curated pairs; this would be an actual
  model that continuously retrains on your entire history and surfaces
  genuinely non-obvious, non-linear patterns (multi-variable interactions a
  human would never manually think to check).
- **A full multi-generational family archive** — Contacts evolves into real
  genealogy: an ancestry graph, embedded oral-history video/audio
  interviews, photos across generations.
- **A generative "personal mythology"** — an illustrated, ever-growing
  storybook of your life: the app periodically composes and illustrates
  (local image generation) an evolving fantasy chronicle where real
  milestones and habit streaks become "quests completed" and "trials
  overcome." Skill Trees taken to its most maximalist extreme — exportable
  as an actual book.

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

---

Nothing here is fixed in stone. This is meant to be read on its own, but
`PROJECT_SPEC.md` remains the source of truth for what's actually *built* —
check there first if you're not sure whether something in this file already
shipped.

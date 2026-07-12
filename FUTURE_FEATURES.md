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
  - Stock tickers need a paid key:
    - **Alpha Vantage** — free tier 25 requests/day; paid from **$49.99/mo**
      (75 req/min) up to **$249.99/mo** (1,200 req/min).
    - **Finnhub** — free tier is a genuinely usable 60 calls/min; paid tiers
      roughly **$50–$100+/mo** depending on data bundle/coverage.
- **Spotify listening stats** — a "recently played / top artists /
  listening time" page via Spotify's free Web API. Needs its own new OAuth
  flow (a third, alongside Google and Supabase).
- **YouTube real watch history** — upgrades the current manual "watch
  later" links to real watch history/liked videos via the YouTube Data
  API. Reuses the existing Google sign-in, but needs a new scope added.
- **Google Photos import** — same deal: reuses existing Google auth, needs
  its own new scope.

*(Ruled out: WhatsApp and Instagram personal DMs. Their Business/Messaging
APIs only handle new messages sent to a business-designated presence —
they don't expose existing personal chat history, converting your account
to Business doesn't change that, and Meta's App Review process actively
screens out "personal archiving" as a use case anyway.)*

## 5. Additional interfaces

- ✅ **Vespera** — STARTED and well underway (not a future idea anymore): a
  spatial interface, Life OS as an orbital station you navigate through, not
  a dashboard. Hub (Grand Concourse) with 9 district plaques, district rooms,
  hotspot/portal geometry actively being refined. See `VESPERA_SPEC.md` and
  the Built ✅ section of `PROJECT_SPEC.md` for current state.
- **An LCARS-inspired mode** (Star Trek control-panel aesthetic, original
  execution, no copyrighted assets).
- ⏸️ PARKED — **Bio-futurism/jungle interface** — a third, from-scratch
  interface concept: a futuristic tropical/jungle theme ("40th century meets
  the Amazon meets the far reaches of the galaxy"), unrelated to Vespera or
  LCARS. Mid-exploration when parked: a typography/identity round (via
  Claude Design, one direction returned so far) and an environment-art round
  (via GPT, spec/prompt drafted) were both started but not finished or
  committed to. Pick back up only when Alek brings it up by name.
- Any others that come to mind along the way.

### Device philosophy — desktop-immersive, mobile-companion 🏗️

Desktop is the primary immersive canvas (Vespera's real home); mobile is a
genuinely different, lighter surface, not the same UI at a smaller
breakpoint.
- **Baseline (standalone):** mobile is fully capable on its own, using the
  same offline-first synced data as everything else — no dependency on
  desktop being open or reachable.
- **Enhanced (progressive, when desktop is live):** mobile detects an
  active desktop session via a presence signal (Supabase Realtime presence
  — already wired in for Sharebox) and unlocks extras: casting a view to
  the big screen, using the phone as a remote input (e.g. camera capture
  with the result shown on desktop), and live handoff (desktop picks up
  exactly where you left off on mobile, not just "synced eventually").

This reframes the existing "mobile = occasional check-in, tablet/desktop =
real future target" device-priority note into a deliberate two-tier design.

## 6. Sharebox, beyond the in-app module

The in-app Sharebox module is built (Supabase v2, RLS fixed, working).
What's left:
- A separate, lightweight **companion app** for a friend who doesn't run
  full Life OS — reading/writing the same shared space.
- A possible **friend-mesh** version: several friends, not just one.

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
- **AI with continuity** — the Daily Paper stops being stateless day to
  day; it remembers what it told you last week and writes with callbacks.
- **AI-suggested knowledge-graph edges** — beyond manual linking: a model
  reads across every module and proposes non-obvious connections you'd
  never have drawn yourself.
- **Predictive forecasting** — real trend modeling on your own data:
  subscription cost creep, likely habit-streak breakpoints, when something
  will actually need attention based on your own patterns, not a static date.

**Foundational rearchitecture**
- **Event-sourced core** — every change becomes an immutable event instead
  of an in-place edit. Unlocks real undo/redo everywhere and per-record
  history, and makes Time Machine (above) trivial once it exists.
- **CRDT-based sync** — replace last-write-wins Drive sync with a real
  conflict-free merge engine, so simultaneous edits on two devices combine
  automatically instead of one silently clobbering the other.

**Capture & reach**
- **Camera-to-data capture** — photograph a receipt, bill, or ID and have
  vision extraction auto-fill Documents/Finance fields instead of typing.
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
- **Health-device ingestion** — parse Apple Health/Garmin/Fitbit exports
  (and clean APIs where they exist) so Health stops being hand-typed — also
  gives The Almanac real signal instead of sparse manual entries.
- **Zero-knowledge encrypted vault** — end-to-end encryption at rest, so
  data synced to Drive/Supabase is stored as ciphertext only — even the
  server can't read it. Pairs naturally with multi-user accounts.
- **Passkey/biometric app lock** — unlock the whole vault with Face ID /
  fingerprint / hardware key via WebAuthn, no password typed.
- **Rules & automation engine — "IFTTT for your own life"** — "when a bill
  is 3 days out AND unpaid → surface it + notify"; "when a habit streak
  hits 30 → log a milestone." Connective tissue between modules.
- **App-wide spaced repetition** — generalize the SRS engine beyond
  language flashcards to resurface *anything* — a book highlight, a person
  you haven't contacted in months, a place you meant to revisit.
- **What-if simulation sandbox** — model decisions against your own data:
  "cancel these 3 subscriptions → save $X/year," "sleep +30 min →
  projected effect on habit adherence."
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

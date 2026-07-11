# Life OS — Feature List

Alek's personal all-in-one life management app — a command center, not a
scrapbook. Serious, data-forward, utilitarian. Installable, works fully
offline, your data lives on your own device first (Google Drive is a
sync/backup relay between your two devices, not a requirement to use the
app, and not a company server in the middle).

This document has two parts: **Part 1** is everything built and working
today, with an explanation of each feature. **Part 2** is everything talked
about but not yet built, from the next obvious step all the way out to
genuine moonshots.

---

# PART 1 — Built ✅

## The foundation

- **Installable, offline-first app** — works with no internet connection
  day to day; nothing requires a live connection to function.
- **Swappable interfaces** — the whole app can be redecorated without a
  rebuild. The default ("Equator," a calm sidebar + content layout) is
  built; a structurally different mode and a Star-Trek-style LCARS mode are
  planned (Part 2).
- **Light/dark mode, accent colors, density toggle** — independent of which
  interface is active.

## Core productivity

- **Tasks** — projects/areas, priority, due dates, recurring tasks,
  subtasks, snooze, "waiting on someone," tags, list or kanban view.
- **Places** — bars/restaurants/trails/friends' houses, photos, a map pin,
  people linked to real Contacts entries, ratings, visit history, a
  revisit flag, a map view, and a separate bucket-list section.
- **Links** — YouTube watch-later and Articles-to-read as two separate
  lanes, auto-thumbnails for YouTube, tags, watched/read tracking, a "share
  with a note" flag.
- **Education** — Semesters → Courses → Assignments, grades and a running
  GPA, % complete + time spent per assignment, a time-vs-grade view, course
  notes and key dates.
- **Finance** — a Bills tab (recurring or one-time, amount/due
  date/paid/autopay, PDF attachments, payment history, a configurable
  "remind me N days before"), a Subscriptions tab (monthly/yearly/weekly
  billing normalized to a combined monthly total), and a Yearly Spend tab
  combining bill payments with subscriptions by category.
- **Documents vault** — leases, insurance, warranties, category/issuer/
  policy-number fields, attachments, and an expiry-soon/expired alert
  surfaced on the Dashboard.
- **Contacts** — a full address book: phones, emails, company/title,
  relationship, birthday, tags, notes, a photo, search and tag filtering.
  The single source of truth for people app-wide — other modules link to
  (or quick-create) a real Contacts entry rather than duplicating one.
- **Milestones** — a life-events timeline grouped by year, plus a Yearly
  Recap tab aggregating stats from every other module for a given year.
- **Global Search** — one query across every module, results grouped by
  module, click jumps you there.
- **Backup** — manual JSON export/import, round-trips attachments
  (photos/PDFs) too, independent of Drive.
- **Tools** — a unit converter (length/weight/volume/temperature), a
  timezone helper, and a currency converter (currently a manual/editable
  rate table — live rates are on deck, see Part 2).

## Life & home

- **Books** — currently reading / to read / finished, reading streaks,
  pages and word counts, genre breakdown, author tracking, ratings/notes.
  Also a real **library**: attach an actual EPUB/PDF/text file to a book
  and read it in-app — a built-in EPUB reader (chapters, table of
  contents, font sizing, day/night mode, resumes where you left off, fully
  offline), PDFs in the browser's native viewer, plain text inline, every
  file downloadable. Plus a **Shelf view**: books rendered as colored
  spines on shelves, grouped by status, taller for longer books.
- **Recipes** — ingredients with scalable servings, steps, a "made it" cook
  log, grocery list generator.
- **Photos/Gallery** — albums with a grid + lightbox (prev/next/close).
- **Time Capsules** — write a note to your future self, sealed until a date
  you choose; hidden ("🔒 Sealed" with a countdown) until then, then
  surfaces on its own.
- **Collections Tracker** — track any freeform collection (records, cards,
  whatever) and its items — deliberately generic, no custom fields per
  collection.
- **Trip Packing Lists** — one checklist per trip, with built-in templates
  (weekend / beach / ski / international) that bulk-add common items, plus
  freeform items and a packed-count tally.
- **Quartermaster** — a physical inventory with a lending ledger: what you
  own, and who has it right now if you lent it out.
- **Museum of Finished Things** — a trophy-case view over completions
  already scattered across other modules: done tasks/assignments, finished
  books (with covers), milestones, recipes ranked by times cooked, archived
  projects, and each habit's longest-ever streak.
- **Ghost Days** — a fuller "on this day across the years" view: contact
  birthdays, recipes cooked, and tasks/assignments completed on this date
  in past years, on top of milestones/places/books.
- **Conversation Starters** — pick a contact, get openers drawn from their
  relationship, shared tags, an upcoming birthday, your own notes on them,
  plus a general fallback list.
- **Theme-from-Photo** — pick a gallery photo or upload one, extract a
  small accent palette from it, click a swatch to apply it as the app's
  accent color.

## Health & habits

- **Habits** — a shared streak mechanic (daily check-in, current streak,
  total check-ins) usable for anything — workouts, practice, water intake.
- **Health** — manual sleep/workout/water/weight log with a 7-day rolling
  average.
- **Skill Trees** — an RPG-style character sheet computed entirely from
  real activity (tasks completed, habit check-ins, books finished, chord
  concepts mastered, language reviews done) — five skills, each with a
  level and an XP bar.
- **Entropy** — a neglect score per module (and one overall), based on how
  long it's been since each area was last touched, sorted most-neglected
  first.
- **The Station Cat** — a small rule-based companion whose mood reflects
  recent activity — purely cosmetic.
- **The Almanac** — long-horizon correlations between curated stat pairs
  (sleep vs. habits kept, sleep vs. tasks completed, workout minutes vs.
  sleep) via real Pearson correlation, with a "not enough data yet" floor
  so a number never shows on too thin a sample.

## Journaling & reflection

- **The Daily Paper** — a newspaper-style one-page daily brief: your
  due-soon agenda (overdue items flagged), an "On This Day" section, a
  tickable habit checklist, an Editor's Pick (a random want-to-go
  place/unread book/untried recipe), a small Almanac of quick counts, and
  a 🖨️ Print button that lays it into a print-only sheet for browser
  print-to-PDF. With your device-local Gemini key, Gemini also writes a
  concise daily editorial grounded only in those LifeOS facts. One issue is
  cached per local date and signed-in account, with retry/regenerate controls.
- **Dream Journal** — entries plus recurring-pattern detection: a
  word-frequency scan across every entry's body/tags surfacing what keeps
  recurring across your dreams.
- **Rabbit Hole Journal** — research tangents with freeform notes, a
  running list of links, and active/resolved status.

## Study & music

- **Languages** — plug-and-play "packs" (name, code, TTS locale), two tabs
  each: **Decks** (spaced-repetition flashcards, Again/Good/Easy, browser
  text-to-speech, a study streak) and **Library** (a story-based reading
  library — write or paste in short graded stories with an optional
  translation/gloss, read in-app, mark as read). Japanese ships with a
  starter hiragana deck. Adding a new language is just a form, no rebuild.
- **Chords / Harmony study** — a deliberate *study* tool (not a
  sequencer/practice-along app). Seven tabs: a chord **Dictionary** (any
  root × 35 qualities across 7 voicing families, each playable on
  keyboard/fretboard diagrams); a full **Barry Harris** 6th-diminished
  analysis; a key **Calculator** (diatonic chords, secondary dominants,
  tritone subs, borrowed chords, reverse lookup); a walkable **Harmony
  Map** — a radial graph where clicking any chord explains *why* the move
  sounds the way it does (common tones, voice leading, bass motion), plays
  it, and lets you keep walking — trails save as named progressions; an
  **Atlas** zoomed-out circle-of-fifths view; 17 **theory lessons**; a
  **Practice** tab with self-graded spaced-repetition drills and a
  printable practice sheet; and a fully synthesized **sound engine**
  (oscillators/FM/ADSR/EQ, Piano/Rhodes/Organ/Pad presets, custom presets),
  no samples, works offline.
- **Life as Music** — a short ambient pad progression generated from your
  own numbers: one chord per life area (tasks done, habit check-ins, books
  finished, recipes cooked, places visited, contacts), reusing the Chords
  module's synth engine.

## Sharing & sync

- **Sharebox** — a small space shared with a friend, on two backends
  side by side: **v2 (Supabase, primary)** — real accounts, Postgres +
  Row Level Security, Realtime live updates, multiple spaces supported;
  and **v1 (Drive, fallback)** — the original Drive-folder sync, kept
  until v2 is fully trusted.
- **Google Drive sync** — the two-device backbone for your personal data.
  Last-write-wins merge by timestamp, tombstones so deletes propagate,
  photos/PDFs sync as their own files. Connect/Sync now/Disconnect in
  Settings.
- **Google Calendar sync** — a one-way, push-only mirror of your due-soon
  items into a dedicated "Life OS" calendar. Idempotent re-syncing (no
  duplicates), never reads or changes your other calendars.

## Cross-module polish

- **Cross-module contact links** — Documents and Bills can each link to a
  real Contacts entry via a shared picker/quick-create widget.
- **"On this day"** — a Dashboard section surfacing anything dated with
  today's month/day in a past year.
- **"Surprise me"** — a Dashboard button offering one random nudge with a
  one-click jump to that module.

---

# PART 2 — On Deck 📋

Everything below is discussed but not built. Tier legend:
🔧 routine (buildable anytime, no new decision) · 🔑 new integration
(new OAuth/account, usually free, sometimes paid) · 🏗️ Tier 2 (needs a
real design decision first) · 🌙 moonshot (its own sub-project) ·
🪐 far tier (moonshot-plus: unusually heavy on engineering/storage/compute)

## The near-term core

- ✅ **Multi-user accounts (phase 1)** — email/password + Google sign-in,
  password reset, a `profiles` table for a global display name. Lives in
  Settings' new Account section, shares one auth session with Sharebox v2.
  Live-verified end to end (SQL run, Google + email sign-up/sign-in all
  confirmed working).
- ✅ **AI-powered Daily Paper (Gemini)** — a 3–5 sentence editorial generated
  from a bounded packet of the day's real LifeOS facts. It uses the same
  device-local Gemini key as the AI Assistant, caches one issue per local
  date and signed-in account, and exposes retry/regenerate states.
- 🏗️ **Per-user notifications** — depends on accounts.
- ✅ **AI Assistant (provider-switchable) + Telegram (send-only)** — a chat
  called directly from the browser, no backend, using your own API key per
  provider (device-local, never synced). A toggle in Settings > AI Assistant
  switches between Gemini and Claude — both providers' keys can stay filled
  in at once so flipping the toggle costs nothing. The toggle is gated
  behind tapping the "AI Assistant" label 10 times (everyday key/model
  fields aren't gated, only the switch itself). Originally Claude-only;
  Gemini added because it's the only other provider confirmed to support a
  direct browser-to-API call with no backend — OpenAI's API sends no CORS
  headers for browser requests at all, so a GPT panel needs a proxy server,
  not just a key. Telegram sends you a digest via a bot you create yourself,
  triggered by your own action (Settings test button, Daily Paper's "Send
  to Telegram"). Still open: a separate ChatGPT panel (would need that
  proxy first) and full two-way Telegram chat (needs a backend webhook).
- 🏗️ **AI-written yearly recap** — once an AI module exists, have it draft
  the actual narrative instead of just tallying numbers.

## ~~Four architecture-decision features~~ — ALL BUILT ✅

All four shipped (see Part 1 additions below): **Knowledge Graph** (radial,
walkable, Search-defined linkability), **The Orrery** (solar-system
alternate dashboard where orbit = neglect, size = volume, speed = activity,
pulsing ring = overdue), **Time Machine** (scrub any past date: existence
counts, added-that-day, genuinely-dated lived-that-day, with an on-screen
honesty note about its limits), and **QR Airgap Sync** (QR = WebRTC pairing
handshake scannable by a phone's native camera, data peer-to-peer over the
LAN, LWW + tombstone merge, verified end-to-end across two isolated browser
profiles — building it also fixed a latent duplicate-language-pack sync
bug).

## ~~Five routine builds~~ — ALL BUILT ✅

- ✅ **Geofenced notes-to-self on Places** — resurfaces via the existing
  "Check nearby places" nudge, distinct from the freeform notes textarea.
- ✅ **Practice Log (Chords)** — a freeform date/duration/notes log on its
  own Log tab, separate from the auto-tracked drill stats.
- ✅ **Live currency conversion** — Tools' Currency tab now pulls live rates
  from Frankfurter/ECB, with a clean offline fallback.
- ✅ **Weather context** — Open-Meteo on the Dashboard/Daily Paper, opt-in,
  cached for offline use.
- ✅ **Price tickers (crypto)** — a Crypto tab in Finance, CoinGecko
  watchlist with live price + 24h change.

## External integrations (new OAuth or paid third parties)

- **Financial Center** — bank/investment linking via **Plaid** (Sandbox
  free; Production ~$0.30-$3/connected account/month or $0.10-$0.60/call
  depending on product) plus live price tickers (crypto free via
  CoinGecko; stocks need a paid key — **Alpha Vantage** $0-$249.99/mo or
  **Finnhub** free-60-calls/min up to ~$50-100+/mo). Needs its own backend
  token exchange — the most security-sensitive item here.
- **Spotify listening stats** — recently played/top artists/listening
  time, via Spotify's free Web API. Needs its own new OAuth flow.
- **YouTube real watch history** — upgrades manual "watch later" links to
  real history via the YouTube Data API, reusing Google sign-in with a new
  scope.
- **Google Photos import** — same pattern, another new scope.
- *(Ruled out: WhatsApp/Instagram personal DMs — their Business APIs only
  cover new messages to a business presence, not existing personal chat
  history, and converting your account doesn't change that.)*

## Additional interfaces

- ✅ **Vespera (v1)** — the spatial interface (Life OS as an orbital
  station you navigate, not a dashboard). Hub with nine district plaques
  matching the generated concourse image, district door screens, travel
  transitions, and every module hosted inside station chrome via the
  shared view library. Switch in Settings → Interface. Hub art loads from
  `js/interfaces/vespera/img/hub.png` (CSS starfield until uploaded).
  Still open from `VESPERA_SPEC.md`: per-district room art, richer
  per-space chrome.
- **LCARS-inspired mode** — Star Trek control-panel aesthetic, original
  execution.

## Device philosophy: desktop-immersive, mobile-companion 🏗️

A named architecture direction, not yet built: **desktop is the primary,
immersive canvas** (Vespera-style spatial navigation, rich graphics,
interaction) — **mobile is a genuinely different, lighter surface**, not
just the same interface at a smaller breakpoint.

- **Baseline (mobile standalone):** fully capable on its own, using the
  same offline-first, locally-synced data every other module already has.
  No dependency on desktop being open or online — mobile never degrades if
  desktop isn't reachable.
- **Enhanced (progressive, when desktop is live):** mobile detects an
  active desktop session via a presence signal (Supabase Realtime already
  has a presence feature built for exactly this, and it's already wired
  into the app for Sharebox) and unlocks extra capability on top:
  - **Cast a view to the big screen** — pull up the Harmony Map or a book
    on your phone, send it to display on desktop, phone becomes the
    controller.
  - **Phone as remote input** — camera-based features (Theme-from-Photo,
    future camera-vision cataloging) capture on the phone, result appears
    live on the desktop screen.
  - **Live handoff** — reading in Library of Babel on the couch on your
    phone, desktop picks up at the same spot the moment you sit down —
    genuinely live, not just "synced eventually."

This reframes the existing "mobile = occasional check-in, tablet/desktop =
real future target" device-priority note into a deliberate two-tier
design, and gives Vespera's immersive spatial concept a clear home (desktop)
rather than needing to also work as a small-screen experience.

## Sharebox, further out

- A lightweight **companion app** for a friend who doesn't run full Life OS.
- A possible **friend-mesh** version (several friends, not just one).

## Moonshot tier 🌙 — AI, rearchitecture, capture, privacy, platform

- **Fully local LLM, zero API key** — a small model running in-browser so
  AI features work fully offline and free, data never leaving the device.
- **Semantic memory across your whole life** — a local vector index so
  natural-language questions return real answers from your own history.
- **Autonomous daily chief-of-staff** — an agent that reviews everything
  each morning and drafts a prioritized brief.
- **AI with continuity** — the Daily Paper remembers past issues and
  writes with callbacks.
- **AI-suggested knowledge-graph edges** — a model proposes non-obvious
  connections across modules.
- **Predictive forecasting** — real trend modeling on your own data.
- **Event-sourced core** — every change becomes an immutable event,
  unlocking full undo/redo and making Time Machine trivial.
- **CRDT-based sync** — conflict-free merges replacing last-write-wins.
- **Camera-to-data capture** — photograph a receipt/bill/ID, vision
  extraction auto-fills the record.
- **Real background push** — true background sync/Web Push, not just
  foreground PWA behavior.
- **Natural-language command bar** — type or speak a command, it parses
  intent and creates the record itself (offline Whisper for voice).
- **Real financial ingestion & reconciliation** — import bank/card
  statements, auto-categorize, reconcile against Finance.
- **Health-device ingestion** — parse Apple Health/Garmin/Fitbit exports.
- **Zero-knowledge encrypted vault** — end-to-end encryption at rest.
- **Passkey/biometric app lock** — WebAuthn, no password typed.
- **Rules & automation engine** — "IFTTT for your own life," modules
  reacting to each other.
- **App-wide spaced repetition** — the SRS engine generalized to resurface
  anything, not just flashcards.
- **What-if simulation sandbox** — fork projected outcomes against your
  own data.
- **Personal local API + plugin SDK** — a documented interface to build on
  without touching core.
- **Generative "Year in Review" film** — an auto-produced montage scored
  by the Life-as-Music engine.

## Far tier 🪐 — scale-and-space swings

- **A true 3D Memory Palace** — a walkable, rendered 3D space, not flat
  districts.
- **A local, continuous "life recorder"** — periodic screen capture + OCR,
  fully private, indexed and searchable.
- **A local Whisper-powered audio diary with auto-routing** — talk instead
  of type; it files itself into the right module.
- **A true digital twin / life-simulation engine** — an agent-based model
  of you, run forward from your real data.
- **A full bidirectionally-linked personal wiki** — a native
  Obsidian/Roam-style note system with backlinks and a graph view.
- **Generative dream visualization** — Dream Journal entries rendered as
  actual generated images/video/audio.
- **Camera-vision auto-cataloging** — point a camera at a shelf, it
  catalogs everything automatically.
- **A real trained ML pattern engine** — beyond simple correlation, a
  model that continuously retrains to surface non-linear patterns.
- **A full multi-generational family archive** — real genealogy, oral
  history, embedded media.
- **A generative "personal mythology"** — an illustrated, ever-growing
  storybook of your life, milestones as "quests completed."

## Explicitly out of scope

- A standalone music-practice app (progressions, play-along, melody-aware
  voicing) — deliberately kept separate from Life OS.

## The one open question underneath all of this

Whether the rest of the app's modules (currently local-first in IndexedDB)
eventually move to the same Supabase backend as accounts ("full cloud"),
stay local with just accounts+Sharebox+notifications on Supabase
("hybrid"), or that decision gets deferred further. Several moonshot items
(encrypted vault, event sourcing, CRDT sync) look different depending on
which way this goes.

---

Nothing in Part 2 is fixed in stone — this is a living list, not a roadmap
with dates. `PROJECT_SPEC.md` and `FUTURE_FEATURES.md` remain the more
detailed working documents this was distilled from.

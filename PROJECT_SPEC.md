# Life OS — Project Spec

**What this is:** Alek's personal all-in-one life management app — a command
center, not a scrapbook. Serious, data-forward, utilitarian. Works offline,
installed like an app on both a Ubuntu desktop and an Android phone, with your
own data staying on your own device (synced between your two devices via your
own Google Drive — no company server in the middle).

Status legend: ✅ built &nbsp; · &nbsp; 🔜 next up &nbsp; · &nbsp; 📋 planned

---

## 1. The foundation

✅ **Built.** The app shell, offline storage, and the "interface" system that
lets the whole app be redecorated later without rebuilding it:

- Installable app (works offline, no internet required day-to-day)
- Your data lives on your device first; Drive sync is a backup/relay between
  your two devices, not a requirement to use the app
- Multiple complete interfaces to choose from — not just color themes, but
  genuinely different layouts. The first one ("Equator," a calm sidebar +
  content layout) is built. A structurally different mode and an LCARS
  (Star-Trek-inspired) mode are still to come — see Section 3.
- Light/dark mode, a few accent color choices, and a compact/comfortable
  density toggle, independent of which interface you're using

## 2. Modules

### Built ✅
- **Tasks** — projects/areas, priority, due dates, recurring tasks, subtasks,
  snooze, "waiting on someone," tags, list or kanban view
- **Places** — bars/restaurants/trails/friends' houses, photos, map pin,
  linked people (linked to real Contacts entries, not a separate record),
  ratings, visit history, revisit flag, a map view, and a separate
  bucket-list section
- **Links** — YouTube watch-later and Articles-to-read, kept as two separate
  lanes, auto-thumbnails for YouTube, tags, watched/read tracking, a "share
  with a note" flag
- **Education** — Semesters → Courses → Assignments, grades and a running
  GPA, % complete + time spent per assignment, a time-invested-vs-grade view,
  course notes and key dates
- **Books** — currently reading / to read / finished, reading streaks, pages
  and estimated word counts, genre breakdown, author tracking, ratings/notes.
  Also a real **library**: attach the actual book file (EPUB / PDF / text) to
  a book and read it right in the app — a built-in EPUB reader (chapters,
  table of contents, font sizing, day/night, resumes where you left off,
  images and the book's own styling preserved, all offline via a tiny
  vendored unzip lib), PDFs in the browser's native viewer, and plain text
  inline; every file also downloads. Plus a **Shelf** view: your books as
  colored spines standing on shelves, grouped by status, taller for longer
  books — click a spine to open it
- **Recipes** — ingredients with scalable servings, steps, a "made it" cook
  log, grocery list generator
- **Finance** (formerly "Bills") — Bills tab (recurring or one-time, amount/
  due date/paid/autopay, PDF attachments, payment history, a configurable
  "remind me N days before" threshold), a Subscriptions tab (monthly/yearly/
  weekly billing normalized to a combined monthly total, active/cancelled),
  and a Yearly Spend tab combining logged bill payments with active
  subscriptions (annualized) by category. Net worth and savings goals were
  prototyped and then shelved for now — may return later.
- **Documents vault** — leases, insurance, warranties, category/issuer/
  policy-number fields, PDF/image attachments, and an expiry-soon/expired
  alert (configurable window) surfaced on the Dashboard's due-soon feed
- **Contacts** — a full address book, not just a service-people list: phones,
  emails, company/job title, relationship, birthday, tags, notes, and a
  photo, with search and tag filtering. This is the single source of truth
  for people app-wide — Places' "linked people" links to (or quick-creates)
  a real Contacts entry rather than keeping its own separate record.
- **Milestones** — a life-events timeline (grouped by year) plus a Yearly
  Recap tab that aggregates stats from every other module (tasks completed,
  places visited, books finished, bills paid, habit check-ins, etc.) for a
  given year. The recap is numbers-driven for now; an AI-written narrative
  version is still on the list, waiting on an AI module.
- **Search** — one query across every module with a title-like field
  (tasks, places, links, books, recipes, bills, contacts, milestones,
  habits, decks...), results grouped by module, click jumps you there
- **Backup** — manual JSON export/import from Settings, a Drive-independent
  backup that round-trips attachments (photos/PDFs) too
- **Tools** — currency converter (manual/editable rate table, not a live
  feed, so it stays useful fully offline), unit converter (length/weight/
  volume/temperature), a timezone helper (saved locations vs. your local time)
- **Habits** — a shared streak mechanic (daily check-in, current streak,
  total check-ins) usable for any habit — workouts, practice, water intake —
  instead of separate one-offs per module
- **Health** — manual-entry sleep/workout/water/weight log with a 7-day
  rolling average. No live Garmin API (none exists cleanly) — hand-entered,
  possibly from a CSV export down the road
- **Photos/Gallery** — albums with a grid + lightbox (prev/next/close)
- **Languages** — plug-and-play: language learning is built as installable
  "packs" (name, code, TTS locale). Two tabs per pack: **Decks** (spaced-
  repetition flashcards, Again/Good/Easy, browser TTS, a study streak) and
  **Library** (Library of Babel — a story-based reading library: write or
  paste in short graded stories with an optional translation/gloss, read
  them in-app, mark as read). Japanese ships with a starter hiragana deck —
  a starting set, not a full curriculum. Adding a second language (Spanish,
  etc.) later is just a new pack via the in-app "+ Add language" form, no
  rebuild needed. The old Lessons tab (grammar/syntax explainers) was
  retired outright and Library of Babel — originally its own separate
  module — was folded into Languages as the Library tab, since "flashcards"
  and "reading practice" are both just "how you learn a language" and don't
  need to live in two different places in the nav.
- **Geolocation nudges in Places** — a manual "check nearby places" action
  surfaces want-to-go spots and stale revisit-flagged places within 1km.
  Deliberately not passive/background — a plain PWA (especially on iOS
  Safari) can't reliably run background geofencing, so this is
  foreground/user-triggered by design, not a missing feature.
- **Cross-module contact links** — Documents and Bills can each link to a
  real Contacts entry (an insurance agent, a utility rep) via a shared
  picker/quick-create widget; unlinking removes the reference only, never
  the contact itself
- **"On this day"** — a Dashboard section surfacing anything dated with
  today's month/day in a past year, pulled from Milestones, Places visits,
  and Books started/finished
- **"Surprise me"** — a Dashboard button offering one random nudge (a
  want-to-go place, a to-read book, an untried recipe, an open bucket-list
  goal) with a one-click jump to that module
- **Chords / harmony study** — deliberately a *study* tool (diagrams,
  theory, sound — no sequencing, tempo, or play-along; a real music-practice
  app stays a separate future project). Seven tabs: a chord Dictionary (any
  root × 35 qualities, correctly spelled, with shell / Root-Shell-Pretty /
  drop 2-3-2&4 / rootless Bill Evans A-B / Kenny Barron / "So What" quartal /
  upper-structure voicings drawn on piano-keyboard and guitar-fretboard
  diagrams, each playable); a full Barry Harris 6th-diminished analysis
  (major/minor scales of chords harmonized into 8 playable positions, the
  maj7→6 / m7→relative-6 / ø→m6 / dominant→"m6 on the 5th" translations,
  and the shared-dim7 family of four dominants as a substitution wheel); a
  key Calculator (diatonic chords, secondary dominants + tritone subs,
  borrowed chords, reverse key lookup); a walkable Harmony Map — a radial
  graph with the current chord at the hub and curated, strength-weighted
  next-chord spokes (resolutions / continuations / approaches / stand-ins /
  color moves behind an "Adventurous" toggle, incl. negative-harmony
  mirrors), where clicking any chord explains WHY the move sounds the way
  it does from the actual notes (common tones, half-step pulls, tritone
  release, guide-tone threads, bass motion, total voice travel), plays the
  move with minimal-motion voice leading so you hear the smoothness being
  described, and lets you walk there and keep going — the walked trail is
  playable and saves as a named progression; optional key context adds
  roman numerals and diatonic quick-jumps; nodes are color-coded by chord
  quality (major/minor/dominant/dim/aug — an Illustrated-Harmony-style
  read of "what kind of chord" at a glance, kept separate from the line
  colors that mean "relationship to the center"); an Atlas mode zooms out
  to the whole territory — a circle-of-fifths wheel with the relative-minor
  ring, your key's six-chord wedge highlighted, plus the three diminished
  "engines" (every dominant's dim7 core with its four minor-third-family
  substitutes), all clickable back into the walkable map; 17 theory
  lessons; a Practice tab — self-graded spaced-repetition drills (spell a
  chord, name it by its notes, identify a voicing from its diagram, each
  playable) that track how well you know each concept (chord-quality family
  or voicing type) and rebuild each day's routine around your weak spots,
  Again/Good/Easy scheduling like the Languages flashcards, with a "needs
  work / solid" readout and a printable practice sheet (browser print-to-PDF,
  prompts up top and an answer key at the bottom — no new library); and an
  adjustable fully-synthesized sound engine (osc/FM/ADSR/EQ with Piano,
  Rhodes EP, Organ, Pad presets plus saveable custom presets — no samples,
  so nothing to license and it works offline).
- **Google Drive sync** — the two-device backbone. Data reconciles between
  your devices through your own Drive (drive.file scope, visible LifeOS/
  folder — no company server). Each device owns one snapshot file and can't
  clobber the other's; records merge last-write-wins by timestamp; deletes
  are tracked with tombstones so they propagate instead of resurrecting;
  photos/PDFs sync as their own Drive files. Device preferences (theme,
  density, active interface) stay local, not synced. Connect / Sync now /
  Disconnect live in Settings. The merge core and full IO path are
  extensively tested; the only unverifiable-without-you piece was the live
  Google sign-in, which you completed (OAuth Client ID created and wired in).
- **Google Calendar sync** — a one-way mirror of your due-soon items (open
  tasks, unpaid bills, assignments, and document expiries within a
  configurable horizon) into a dedicated "Life OS" calendar, so they show up
  on your phone and desktop calendar with reminders. Push-only: Life OS
  writes just the calendar it creates (calendar.app.created scope — the
  Calendar twin of drive.file — plus a second read-only scope that only sees
  your calendars' names, never their events, so it can find its own instead
  of making a duplicate) and never reads or changes your other calendars.
  Re-syncing is idempotent — each event is matched back to its
  source record, so there are no duplicates; paying/completing/deleting an
  item removes its event; all your devices push into one shared calendar.
  Connect / Sync now / Disconnect sit in Settings next to Drive sync and work
  independently of it, reusing the same Google sign-in. The reconciliation
  core and full IO path are tested; the one unverifiable-without-you piece is
  the live Calendar consent grant (same as Drive was — you approve a Calendar
  permission the first time you connect).
- **Sharebox** — a small space shared with a friend, now on **two backends
  side by side**, switchable in-app:
  - **v2 (Supabase, primary)** — real accounts (Google sign-in today), a
    "space" you're both members of, Postgres as the source of truth with Row
    Level Security for access control, and Realtime subscriptions so a
    friend's post appears live — no manual "Sync now." Supports more than one
    space (create/join anytime, a picker when you have several). Confirmed
    working end-to-end (creating spaces, posting links/notes/files, live
    updates) after a Row-Level-Security bug was root-caused and fixed — the
    fix is documented in `sql/supabase-sharebox-rls-fix.sql`.
  - **v1 (Drive, fallback)** — the original: syncs through a Google Drive
    folder you both pick, same proven per-device-snapshot/last-write-wins/
    tombstone engine as personal Drive sync. Kept only until v2 is fully
    trusted; the plan is to retire it once that's certain.
- **The Daily Paper** — a newspaper-style one-page daily brief, composed
  entirely from data the app already has: your due-soon agenda (overdue items
  flagged), an "On This Day" section, a tickable habit checklist for today, an
  Editor's Pick (a random want-to-go place / unread book / untried recipe),
  and a small Almanac of quick counts. A 🖨️ Print button lays the same
  content into a print-only sheet and hands it to the browser's
  print-to-PDF — no new library, reuses the same approach as the Chords
  practice sheet. When an Anthropic key is configured, Claude writes a short
  editorial from a bounded packet of the day's actual LifeOS facts (due and
  overdue items, habits, weather, sleep, history, and the Editor's Pick).
  The prompt explicitly forbids invention; one issue is cached per local date
  and signed-in account, with visible setup, retry, and regenerate states.
- **Museum of Finished Things** — a trophy-case view over completions already
  scattered across other modules: done tasks/assignments, finished books
  (with covers), milestones, recipes ranked by times cooked, archived
  projects, and each habit's longest-ever streak (not just the current one).
- **Time Capsules** — write a note to your future self and seal it until a
  date you choose; it stays hidden (shown as "🔒 Sealed" with a countdown)
  until that date passes, then surfaces on its own.
- **Collections Tracker** — track any freeform collection you keep (records,
  cards, whatever) and the items in it — deliberately generic, no
  per-collection custom fields.
- **Trip Packing Lists** — one checklist per trip, with built-in templates
  (weekend / beach / ski / international) that bulk-add common items in one
  click, plus freeform items and a packed-count tally.
- **Quartermaster** — a physical inventory with a lending ledger: what you
  own, and who has it right now if you lent it out (with a one-click "mark
  returned").
- **Skill Trees** — an RPG-style character sheet computed entirely from real
  activity (tasks/assignments completed, habit check-ins, books finished,
  chord concepts mastered, language reviews done) — five skills, each with a
  level and an XP bar, no new storage.
- **Entropy** — a neglect score per module (and one overall), based on how
  long it's been since each area's data was last touched — sorted most-
  neglected first, so you can see at a glance what's been ignored.
- **The Station Cat** — a small rule-based companion whose mood reflects
  recent activity (tasks, habits, health logs) — purely cosmetic, no new
  storage.
- **Ghost Days** — a fuller "on this day across the years" view than the
  Dashboard/Daily Paper snippet: adds contact birthdays, recipes cooked, and
  tasks/assignments completed on this date in past years, on top of the
  original milestones/places/books sources.
- **Conversation Starters** — pick a contact, get openers drawn from several
  contexts (relationship, shared tags, an upcoming birthday, your own notes
  on them), plus a general fallback list.
- **Theme-from-Photo** — pick a gallery photo or upload one, extract a small
  accent palette via canvas pixel quantization, click a swatch to apply it as
  the app's accent color (alongside the built-in brass/teal/garnet presets).
- **Dream Journal** — entries plus recurring-pattern detection: a
  word-frequency scan across every entry's body/tags surfacing what keeps
  recurring across your dreams.
- **Rabbit Hole Journal** — research tangents with freeform notes and a
  running list of links, active/resolved status.
- **The Almanac** — long-horizon correlations between curated stat pairs
  (sleep vs. habits kept, sleep vs. tasks completed, workout minutes vs.
  sleep) via Pearson correlation, with a "not enough data yet" floor so a
  number never shows on too thin a sample.
- **Life as Music** — a short ambient pad progression generated from your own
  numbers: one chord per life area (tasks done, habit check-ins, books
  finished, recipes cooked, places visited, contacts), root/quality derived
  deterministically from each area's count. Reuses the existing Web Audio
  synth engine (Chords module) — no new audio code.
- **Library of Babel** — a story-based reading library (see the Languages
  entry above — it's the "Library" tab there now, not a separate module).
  Originally shipped standalone and replaced the old Lessons tab; then, at
  your request, folded into Languages alongside Decks so both ways of
  learning a language live in one place instead of two. The
  `languageLessons` data store, its API exports, and its global-search
  entries were all removed together with Lessons (verified via a
  fresh-profile test that search doesn't break for anyone who never had
  that store).

- **Knowledge Graph** — link anything to anything (a task to a contact, a
  book to a milestone…) and walk it as a radial web borrowing the Harmony
  Map's interaction grammar: the focused record at the center, connections
  as spokes, click a spoke to refocus and keep walking. "What is linkable"
  is defined as "what Search can find" (one shared record-addressing table).
  Edges are undirected and title-free — endpoints resolve live so renames
  never leave stale labels, and a deleted record shows an honest
  "(deleted)" tombstone with an unlink offer. Duplicate links dedupe in
  either direction; self-links are rejected.
- **The Orrery** — the dashboard as a solar system, shipped as an alternate
  view (its own nav entry; the Dashboard stays the default). Every visual
  property encodes a real signal: orbit radius = neglect (fresh areas hug
  the sun, stale ones drift outward, no-data areas park dimmed on the outer
  ring), planet size = record count (log-scaled), orbital speed = this
  week's activity, a pulsing red ring = something overdue. Click a planet
  to fly to its module. The animation loop provably stops on navigation,
  and prefers-reduced-motion gets a fully static layout.
- **Time Machine** — scrub a slider (or pick a date) and see what the app
  knew on that day: per-module "existed then → now" counts from createdAt
  timestamps, what was added that day, and — genuinely historical — what
  was *lived* that day from the dated log stores (habit check-ins, health,
  cooking, reading). An on-screen honesty note states what it can't show
  (deletions since then, old field values); the event-sourced core on the
  moonshot list upgrades this in place when it lands.
- **QR Airgap Sync** — sync two devices with no server, no account, no
  internet: the QR code is the pairing handshake (it carries a WebRTC
  offer as a URL the other phone's NATIVE camera app can scan — zero
  in-app camera code), the reply code comes back by QR/paste, and data
  then flows peer-to-peer over the local network. Merge mirrors Drive
  sync: last-write-wins by updatedAt, deletions propagate via tombstones.
  Verified end-to-end across two isolated browser profiles: bidirectional
  transfer, edit propagation, delete propagation. Building this also
  surfaced and fixed a latent sync bug: boot-seeded language packs used
  random per-device ids (so any sync duplicated "Japanese"); they're now
  deterministic ("languagepack-ja") with self-healing dedupe for installs
  that already duplicated. Attachments (photo/PDF blobs) don't ride this
  path yet — noted in the UI.

- **Geofenced notes-to-self on Places** — a short note attached to a place
  that resurfaces the next time "Check nearby places" finds you within
  range, distinct from the place's freeform notes textarea.
- **Practice Log (Chords)** — a freeform date/duration/notes log of actual
  instrument time, on its own Log tab, separate from the Practice tab's
  auto-tracked drill-accuracy stats.
- **Live currency conversion** — Tools' Currency tab now pulls live rates
  from Frankfurter/ECB (free, keyless), auto-refreshing once per session
  when the cache is >24h stale, plus a manual Refresh button. Falls back
  cleanly to the last-cached or manually-edited rates when offline — the
  converter never breaks, it just stops updating.
- **Weather context** — Dashboard and Daily Paper both show current
  conditions from Open-Meteo (free, keyless) once you opt in via "Use my
  location." Cached for an hour so it still shows something offline.
- **Price tickers (crypto)** — a new Crypto tab in Finance: a watchlist of
  CoinGecko coin IDs with live USD price and 24h change, cached for five
  minutes so it still shows something offline.

- **Multi-user accounts (phase 1)** — email/password sign-up/sign-in,
  password reset, and Google sign-in, all sharing one Supabase auth session
  with Sharebox v2 (sign in either place, you're signed in everywhere). A new
  `profiles` table (`sql/supabase-accounts-schema.sql`) gives every account a
  global display name, auto-created via a trigger on signup. Lives in
  Settings' new "Account" section. **Fully verified live, not just
  headlessly:** SQL migration run, Google sign-in + display name save
  confirmed, email sign-up + confirmation email + email sign-in all confirmed
  working end-to-end. (Along the way, fixed a real bug the live test caught:
  the confirmation email link 404'd because signUpWithEmail() wasn't passing
  emailRedirectTo — it now reuses the same redirect helper Google sign-in
  already used correctly.) "Forgot password" is built but not yet live-tested.
  Per-user data isolation for the rest of the app (Tasks, Places, Finance,
  ...) is deliberately NOT part of this phase — the phased architecture
  approach (see "Additional interfaces" section) keeps those modules
  local-first for now.

- **AI Assistant (Claude) + Telegram (send-only)** — a new "AI Assistant"
  module: a chat with Claude, called directly from the browser using your
  own Anthropic API key (Settings, device-local, never synced). Confirmed
  both api.anthropic.com and Gemini's API are actually reachable from this
  environment's network (unlike Supabase and OpenAI, both blocked) — scoped
  to Claude only for this pass per your call. Conversations/messages are
  regular synced data (`aiConversations`/`aiMessages`). Telegram is
  send-only by design — the app messages you through a bot you create
  yourself (@BotFather), triggered by your own action (a "Send test
  message" button in Settings, and a "Send to Telegram" button on the Daily
  Paper that sends a plain-text digest). No listener for incoming
  messages — a static PWA can't run one when it's not open; full two-way
  chat would need a real backend webhook, deliberately out of scope here.
  Verified headlessly with both APIs mocked (message send/receive, digest
  send, gated "no key" state) — a live call needs your real API key/bot
  token, which can't be tested from this environment.

### Still to build 📋
- **Per-user notifications** — depends on accounts existing first.
- **AI-written yearly recap** — needs a working AI module first (see above)

## 3. Additional interfaces 📋
- **Vespera** — a spatial interface (LifeOS as an orbital station you
  navigate through, not a dashboard). Fully planned in `VESPERA_SPEC.md`,
  including the district/space-to-module mapping and v1 scope; not
  started. Building it is Tier 2 (interface-registry design).
- An LCARS-inspired mode (Star Trek control-panel aesthetic, original
  execution — no copyrighted assets)
- Any others that come to mind along the way

**Device philosophy — desktop-immersive, mobile-companion 🏗️:** desktop is
the primary immersive canvas (this is Vespera's real home); mobile is a
genuinely different, lighter surface, not the same UI at a smaller
breakpoint. Baseline: mobile is fully capable standalone, using the same
offline-first synced data as everything else — no dependency on desktop
being open. Enhanced (progressive, when desktop is live): mobile detects an
active desktop session via a presence signal (Supabase Realtime presence,
already wired in for Sharebox) and unlocks extras — cast a view to the big
screen, use the phone as a remote input (camera capture → result shown on
desktop), live handoff (pick up on desktop exactly where you left off on
mobile). Reframes the existing device-priority note into a deliberate
two-tier design rather than an afterthought.

## 4. New ideas from later conversations 📋

Everything below came out of talking through what would actually feel
"you" rather than generic — added to the plan, not built yet:

- **Three separate AI assistant modules** — Claude, ChatGPT, and Gemini each
  get their *own* themed panel (not one generic "AI" tab), each with your own
  API key stored only on your device. Each can be given permission to read
  and act on your actual Life OS data (tasks, notes, etc.) on a per-question,
  opt-in basis. (Considered and struck from this plan: an automated cross-LLM
  relay chaining a question across all three in sequence — it trades real
  signal for compounding drift/hedging without a human deciding what to keep
  at each hop. Not built into the core app; nothing stops it from being a
  separate standalone project later if wanted.)
- **Telegram integration** — both as a chat surface and as a notification
  channel (due bills/tasks pushed to Telegram, since that's more reliable
  than browser push notifications, especially on iPhone). Chosen deliberately
  over WhatsApp/Google Messages/iMessage, which don't have a clean, personal,
  automatable API.
- **Sharebox** — the in-app module is now BUILT (see Built ✅ above); the
  remaining idea is a separate *lightweight companion app* for a friend who
  doesn't run full Life OS, reading/writing the same shared folder — plus a
  possible friend-mesh version (several friends, pairwise shared folders).
- **AI-written yearly recap** — once an AI module can read your data, have it
  draft the actual recap narrative, not just tally up numbers (the recap's
  numbers-driven aggregation already exists in Milestones)

## 5. Rough order of what's left

1. ~~Multi-user accounts (phase 1)~~ — DONE and live-verified: email/password
   + Google sign-in, password reset, profiles table (see Built ✅).
2. ~~AI-powered Daily Paper~~ — DONE (Claude, device-local Anthropic key,
   daily/account-scoped cache); per-user notifications remain open.
3. ~~The four Tier-2 features~~ — DONE: Knowledge Graph, The Orrery,
   Time Machine, and QR Airgap Sync all shipped (see Built ✅)
4. ~~AI Assistant (Claude) + Telegram (send-only)~~ — DONE (see Built ✅).
   ChatGPT/Gemini panels + full two-way Telegram chat are still open, out of
   scope for this pass. (Cross-LLM relay chaining was considered and
   dropped — see section 4 above.)
5. AI-written yearly recap (needs an AI module first)
6. Remaining routine-build ideas (Dream Journal, Rabbit Hole Journal,
   Conversation Starters, Ghost Days, The Almanac, Life as Music, Library of
   Babel, Theme-from-photo)
7. Additional interfaces (Vespera, LCARS)
8. Sharebox companion app / friend-mesh (the in-app Sharebox itself is built)
9. Someday: a standalone music-practice app (progressions, play-along,
   melody-aware voicing) — deliberately out of LifeOS scope

**Open architecture decision:** whether the rest of the app's modules (tasks,
places, finance, etc. — currently local-first in IndexedDB) eventually move
to the same Supabase backend as Sharebox v2/accounts ("full cloud"), stay
local with just accounts+Sharebox+notifications on Supabase ("hybrid"), or
that decision gets deferred further (the phased approach, currently in
progress: prove accounts+AI Paper first, decide the rest later).

## 6. Ambitious / moonshot tier 🌙

Bigger swings — each is closer to its own sub-project than a feature, and
several are foundational (they change how the whole app works underneath,
not just what it can do). All Tier 2+; subject to change. Grouped loosely.

**AI & intelligence**
- **Fully local LLM, zero API key** — a small model running in-browser
  (WebGPU / WebLLM) so the AI Daily Paper, smarter search, and any assistant
  work fully offline, free, and with data never leaving the device. Extends
  the app's local-first ethos to AI itself. This is the one idea that changes
  the app's *size class* (a bundled/downloaded model is hundreds of MB to
  multiple GB).
- **Semantic memory across your whole life** — embed every record from every
  module into a local vector index, so "when did I last see Sarah?" or "what
  was bugging me in March?" returns a real answer from your actual history,
  not keyword matching.
- **Autonomous daily chief-of-staff** — an agent that reviews everything each
  morning, drafts a prioritized brief (bills at risk, streaks about to break,
  rabbit holes gone cold), and can act on pre-approved simple tasks itself.
- **AI with continuity** — the Daily Paper stops being stateless day-to-day;
  it remembers what it told you last week and writes with callbacks ("did you
  end up going to that place you bookmarked?").
- **AI-suggested knowledge-graph edges** — beyond manual linking: a model
  reads across every module and proposes non-obvious connections you'd never
  have drawn yourself.
- **Predictive forecasting** — real trend modeling on your own data:
  subscription cost creep, likely habit-streak breakpoints, when something
  will actually need attention based on your past patterns, not a static date.

**Foundational rearchitecture**
- **Event-sourced core** — every change becomes an immutable event instead of
  an in-place edit. Unlocks real undo/redo everywhere and per-record history,
  and makes the already-planned Time Machine trivial once it exists.
- **CRDT-based sync** — replace last-write-wins Drive sync with a real
  conflict-free merge engine, so simultaneous edits on two devices combine
  automatically instead of one silently clobbering the other.

**Capture & reach**
- **Camera-to-data capture** — photograph a receipt, bill, or ID and have
  vision extraction auto-fill Documents/Finance fields instead of typing.
- **Real background push** — true background sync / Web Push (not just
  foreground-only PWA behavior) so bill-due and streak-at-risk alerts land
  with the app closed, on platforms that genuinely support it.
- **Natural-language command bar** — type or speak "remind me to call mom
  Friday" and it parses intent, picks the right module, and creates the
  record — no navigating. In-browser Whisper handles voice, fully offline.
- **Real financial ingestion & reconciliation** — import bank/card
  statements (CSV/OFX), auto-categorize transactions, reconcile against
  logged bills/subscriptions. Turns Finance from all-manual into something
  that mirrors reality.
- **Health-device ingestion** — parse Apple Health / Garmin / Fitbit
  exports (and clean APIs where they exist) so Health stops being hand-typed
  — also gives The Almanac real signal to correlate instead of sparse
  manual entries.

**Privacy & security**
- **Zero-knowledge encrypted vault** — end-to-end encryption at rest, so
  data synced to Drive/Supabase is stored as ciphertext only — even the
  server can't read it. Pairs naturally with the multi-user accounts work.
- **Passkey / biometric app lock** — unlock the whole vault with Face ID /
  fingerprint / hardware key via WebAuthn, no password typed. Natural
  companion to the encrypted vault.

**Making the app smarter about itself**
- **Rules & automation engine — "IFTTT for your own life"** — "when a bill
  is 3 days out AND unpaid → surface it + notify"; "when a habit streak
  hits 30 → log a milestone." Connective tissue so modules can react to
  each other instead of sitting in silos.
- **App-wide spaced repetition** — generalize the SRS engine beyond
  language flashcards to resurface *anything*: a book highlight, a person
  you haven't contacted in months, a place you meant to revisit.
- **What-if simulation sandbox** — model decisions against your own data:
  "cancel these 3 subscriptions → save $X/year," "sleep +30 min →
  projected effect on habit adherence." Forecasting (above) predicts the
  default future; this lets you fork it.

**Platform & payoff**
- **Personal local API + plugin SDK** — expose your own data through a
  documented local interface so you (or scripts) can build on it without
  touching core — the same registry philosophy that already governs
  interfaces, extended to data. What turns this from an app into a platform.
- **Generative "Year in Review" film** — an auto-produced montage from your
  photos + milestones + stats, scored by the Life-as-Music synth engine
  you already have. The emotional payoff piece.

**Far tier — scale-and-space swings (each closer to its own product than a
feature; heavy on either engineering, storage, or compute)**
- **A true 3D Memory Palace** — not flat districts (Vespera) but a fully
  rendered, walkable 3D space (Three.js/WebGL): rooms per module, physical
  objects representing records, real lighting/physics. Game-engine-scale
  work; 3D assets/textures push storage into the hundreds of MB+.
- **A local, continuous "life recorder"** — periodic screen capture + local
  OCR, indexed and searchable (a fully private, self-hosted Rewind.ai).
  Massive ongoing storage growth (easily GB/month), a real local vision
  pipeline, and the most privacy-sensitive idea on this list — has to be
  100% local, never synced, by design.
- **A local Whisper-powered audio diary with auto-routing** — talk instead
  of type; a large local speech model transcribes and classifies what you
  said, filing it into the right module itself (a dream to Dream Journal, a
  tangent to Rabbit Holes). Requires a multi-GB local model plus real
  classification logic.
- **A true digital twin / life-simulation engine** — beyond the what-if
  sandbox's simple parameter nudges: an actual agent-based model of you,
  with internal state and feedback loops, run forward from your real data to
  project "future you" — potentially rendered as a visual avatar that
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
  interviews, photos across generations — a family history preservation
  project living inside the OS.
- **A generative "personal mythology"** — an illustrated, ever-growing
  storybook of your life: the app periodically composes and illustrates
  (local image generation) an evolving fantasy chronicle where real
  milestones and habit streaks become "quests completed" and "trials
  overcome." Skill Trees taken to its most maximalist extreme — exportable
  as an actual book.

**External integrations (new OAuth flows / paid third parties — each is its
own Tier 2 flag, not a routine add)**
- **Financial Center** — bank/investment linking via **Plaid** (Sandbox is
  free; Production is usage-based, roughly $0.30-$3/connected account/month
  depending on product, with per-call fees on top for some products and
  negotiated enterprise minimums at scale — see pricing note below) plus a
  live price-ticker panel (crypto via CoinGecko is free/keyless; stocks need
  a paid key — Alpha Vantage or Finnhub, both priced below). Needs its own
  backend token exchange (rides on the Supabase backend, can't be
  client-only) — this is the most security-sensitive item on either list.
- **Spotify listening stats** — a "recently played / top artists / listening
  time" page via Spotify's Web API. Free API; needs its own new OAuth flow
  (a third, alongside Google and Supabase).
- **YouTube real watch history** — upgrades the current manual "watch later"
  links to real watch history/liked videos via the YouTube Data API. Reuses
  the existing Google sign-in, but needs a new scope added to it.
- **Google Photos import** — same deal: reuses existing Google auth, needs
  its own new scope.

**Paid API pricing, as researched (verify at signup — these change often):**
- **Alpha Vantage** (stock tickers): free tier is 25 requests/day; paid
  tiers start at $49.99/mo (75 req/min) up to $249.99/mo (1,200 req/min).
- **Finnhub** (stock tickers): free tier is a generous 60 calls/min; paid
  tiers run roughly $50-$100+/mo depending on data bundle/coverage.
- **Plaid** (bank/investment linking): Sandbox (fake data, for building) is
  free. Production is usage-based — commonly cited around $0.30-$3 per
  connected account/month for subscription-style products (Transactions,
  Investments), or $0.10-$0.60 per call for one-time-fee products (Auth,
  Identity, Income); Plaid doesn't publish one flat price list, and
  meaningful scale typically means a negotiated contract.

Nothing here is fixed in stone — the plan has already flexed a few times
this week, and that's expected. This doc is meant to be a snapshot you can
hand to a future session (or future you) to get back up to speed fast.

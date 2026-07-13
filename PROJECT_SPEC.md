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
  genuinely different layouts. Built so far: "Equator" (a calm sidebar +
  content layout, the default), "Vespera" (a spatial, desktop-only
  alternative), and a first mobile interface ("mobile-1," built from Alek's
  own mockup — see `MOBILE_INTERFACES_SPEC.md`). More mobile interfaces are
  planned over time, deliberately not tied to a single visual identity —
  see Section 3.
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
  course notes and key dates. Plus an **academic pacing check**: an
  optional target + unit (pages/words) and self-set pacing checkpoints
  ("6 pages by March 3" — you state your own intention, the app never
  invents a curve) on any assignment, with a dated progress log
  (`assignmentProgressLogs`). Past a checkpoint, a real gap between what
  you logged and what you said you'd have done surfaces as a "behind
  pace" chip on the assignment row and, when genuinely relevant, as a
  grounded fact in the Daily Paper's AI editorial — never an invented
  number, always your own logged total vs. your own stated checkpoint.
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
  alert (configurable window) surfaced on the Dashboard's due-soon feed.
  Plus **camera-to-data capture**: a "📷 Scan a document" photo/file input
  sends the image to the active AI provider's vision input, which drafts
  title/category/issuer/policy-number/expiry/notes — never guessing, using
  null for anything not clearly legible — and opens the result as a normal
  editable record (the photo itself attaches automatically) rather than a
  silently-trusted final save. Extends the multimodal `content` shape (text
  + image segments) down into both `gemini-client.js` and
  `claude-client.js`, so either active provider can be used. Scoped to
  Documents only for this pass, not Finance.
- **Contacts** — a full address book, not just a service-people list: phones,
  emails, company/job title, relationship, birthday, tags, notes, and a
  photo, with search and tag filtering. This is the single source of truth
  for people app-wide — Places' "linked people" links to (or quick-creates)
  a real Contacts entry rather than keeping its own separate record.
- **Milestones** — a life-events timeline (grouped by year) plus a Yearly
  Recap tab that aggregates stats from every other module (tasks completed,
  places visited, books finished, bills paid, habit check-ins, etc.) for a
  given year, now with an AI-written narrative on top of the numbers
  (whichever provider is active in AI Assistant's Settings toggle) —
  grounded only in that year's real stats/milestones, cached per year and
  signed-in account, with setup/loading/error/retry states matching the
  Daily Paper editorial's pattern.
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
  possibly from a CSV export down the road. Plus a one-time **Apple Health
  import**: pick a raw `export.xml` or the `.zip` Apple's Health app
  produces (unzipped client-side with the already-vendored fflate), parses
  the real, publicly documented Apple Health XML schema, and aggregates
  the many fine-grained `<Record>`/`<Workout>` elements down to this
  module's one-row-per-day shape (sleep from actual-asleep time, not
  in-bed; workouts summed + type-labeled; water and weight unit-converted).
  Always previews a day count before writing anything, and merges
  field-by-field into any existing log for that date rather than
  overwriting the whole record, so a manually-added note survives. Garmin/
  Fitbit "clean APIs where they exist" would each need their own OAuth/API
  research pass first — not attempted. See `js/data/apple-health-import.js`.
- **Photos/Gallery** — albums with a grid + lightbox (prev/next/close), plus
  a "📥 Import from Google Photos" option per album via the Photos Picker
  API: opens Google's own hosted picker UI in a new tab, downloads exactly
  what you select, no live sync and no visibility into anything you don't
  pick. (Not a full-library sync — Google retired third-party bulk read
  access to a user's whole Photos library in March 2025; the Picker is
  Google's sanctioned replacement, the only option left. See
  `js/data/photos-picker.js`.)
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
  practice sheet. When a Gemini key is configured, Gemini writes a short
  editorial from a bounded packet of the day's actual LifeOS facts (due and
  overdue items, habits, weather, sleep, history, and the Editor's Pick).
  The prompt explicitly forbids invention; one issue is cached per local date
  and signed-in account, with visible setup, retry, and regenerate states.
- **Passkey/biometric app lock** — a Settings > App Lock section enrolls a
  WebAuthn platform passkey (Face ID/Touch ID/fingerprint/Windows Hello/
  device PIN); once on, the whole app is gated behind it before any data
  renders on boot. A pure local device gate, not encryption or a remote-auth
  system — there's no server to verify a signature against, so a successful
  `navigator.credentials.get()` ceremony is itself the proof, and the
  private key never leaves the device's secure enclave/TPM either way. Off
  by default; gracefully hides the option on devices/browsers with no
  platform authenticator. See `js/data/applock.js`.
- **AI with continuity** — the Daily Paper's editorial is no longer stateless
  day-to-day: each finalized issue is saved to a new `paperIssues` store (one
  per local date + owner), and the last few (oldest first) are handed back
  to the AI as part of its source packet the next time it writes, with
  explicit instructions to reference one only when a genuine callback adds
  value — never forced, never inventing what a past entry said beyond what's
  shown. See `saveEditorialIssue`/`getRecentEditorials` in `js/data/api.js`.
- **App-wide spaced repetition** — a new **Recall** module generalizes the
  Languages module's flashcard SRS engine to resurface any record in the
  app — a task, a book, a contact, a place, anything Search can find.
  Reuses the Knowledge Graph's own foundations rather than duplicating
  them: schedulable = searchable (same `globalSearch` picker grammar as
  the graph's "add a connection"), and titles resolve live via
  `resolveGraphNode` instead of a second lookup table, so a renamed or
  deleted record never leaves a stale label here either. Grading uses the
  identical interval scheme as language cards (again resets to 1 day, good
  doubles, easy triples). See `js/interfaces/default/views/recall.js`.
- **Predictive forecasting** — The Almanac's correlation section now has a
  companion Forecasts section, same "not enough data yet" honesty, with
  three genuinely computed forecasts over real logged history (no AI, no
  invention): a linear-regression bill-spend trend + next-month projection
  (BillPayments), per-habit weekday breakpoint detection flagging the
  weekday you're statistically most likely to skip (HabitLogs), and a
  reading-pace extrapolation to an estimated finish date for in-progress
  books (ReadingLogs). Each requires its own minimum sample before showing
  anything. See `js/interfaces/default/views/almanac.js`.
- **What-if simulation sandbox** — a companion "What If" section on the
  Almanac forks a forecast instead of just stating it: a sleep slider
  refits the real sleep-vs-habits regression live as you drag it (+/-2h in
  15-min steps) and projects habits/day at that sleep level; a subscription
  checklist sums selected subscriptions' yearly cost live as you check them
  off. Both computed from real data on every input change, nothing
  precomputed or AI-generated.
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
  either direction; self-links are rejected. An "AI-suggested connections"
  section proposes non-obvious links from the focused record: the AI is
  only ever given a closed, numbered candidate list of real records
  (excluding the focus and anything already linked) and can only pick
  indices from it, never free text, so it can't invent a connection to
  something that doesn't exist. Manual/button-triggered rather than
  automatic, to keep the API call opt-in. See `suggestGraphEdges` in
  `js/data/api.js`.
- **Rules & automation engine** — scoped to a small fixed set of built-in
  rules rather than a general rule-builder/DSL: (1) log a Milestone when a
  habit streak crosses 7/30/100/365 days, and (2) create a "Renew: <title>"
  Task when a Document is expiring or expired and no renewal task exists
  yet for that specific expiry date. Both off by default (Settings >
  Automations) since they mutate your data on your own behalf, and both
  idempotent — re-running the check on every app open never double-fires.
  Deliberately does NOT include an automatic Telegram send for the classic
  "bill due soon and unpaid" example: `telegram-client.js` is explicitly
  user-triggered-only by design, and Dashboard already surfaces due-soon
  unpaid bills unconditionally, so automating that display again would add
  nothing new — the document-renewal rule is the "surface + act" example
  instead, since it creates a genuinely new record rather than re-showing
  something already shown. See `runAutomations` in `js/data/api.js`.
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

- **AI Assistant (Gemini) + Telegram (send-only)** — a new "AI Assistant"
  module: a chat with Gemini, called directly from the browser using your
  own Gemini API key (Settings, device-local, never synced). Originally
  built on Claude; switched to Gemini so it can keep calling straight from
  the browser with no backend -- confirmed OpenAI's API sends no CORS
  headers for browser-origin requests at all (unlike Anthropic's documented
  direct-browser-access opt-in), so a GPT panel would need a proxy server in
  front of it, while Gemini works the same direct-call way Claude did. The
  Claude path (`claude-client.js`, the `anthropicApiKey`/`anthropicModel`
  settings) is kept dormant, not deleted, in case of a future switch back or
  a second panel. Conversations/messages are
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

## 3. Additional interfaces 📋
- **Vespera** — a spatial interface (LifeOS as an orbital station you
  navigate through, not a dashboard). Started and well underway — see the
  Built ✅ section above and `VESPERA_SPEC.md` for current state.
  **Desktop only** — not part of the mobile/APK experience (see the device
  philosophy note below).
- **Mobile interface(s)** — deliberately not a single named product (see
  `MOBILE_INTERFACES_SPEC.md`, naming decision at the top): Alek's plan is
  several interchangeable mobile interfaces over time, each its own
  registry entry, sharing one functional/plumbing layer (device detection,
  curated module set, packaging) but free to look completely different.
  First one built: **`mobile-1`**, from Alek's own mockup — a real
  dedicated interface (not a filtered Equator), real motion/sound,
  functional alert-styling. Not yet gated mobile-only, and its own
  navigation reaches a subset of the curated module list — see
  `MOBILE_INTERFACES_SPEC.md`'s "Still open" for exactly what's left.
- Any others that come to mind along the way

**Device philosophy — desktop is the full app, mobile is a remote
(revised 2026-07-12):** Desktop is the complete app — every module,
through either Equator (rail+canvas default) or Vespera (the spatial
alternative). Mobile is deliberately **not** a second complete copy of it:
a genuinely stripped-down "controller in your pocket," built around what's
actually useful away from a desk — quick capture, on-the-go actions,
glanceable status — not full parity. No Vespera, no spatial "living
world" on mobile; that register stays desktop's. Mobile gets its own
purpose-built interface(s) (`MOBILE_INTERFACES_SPEC.md`), not a filtered
version of Equator.

Sync model: the same underlying data everywhere (Drive/Supabase, exactly
as it already works today) — this is a UI-surface decision, not a
data-sync restriction. Every device stays fully synced regardless of which
modules its interface chooses to show.

This replaces the earlier "mobile is fully capable standalone, same UI at
a smaller breakpoint, unlocks casting/remote-input extras when desktop's
live" framing — superseded after further conversation. The
presence-based casting idea isn't part of the current plan, but nothing
rules out revisiting it later once the mobile remote itself exists.

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
  separate standalone project later if wanted.) Current state: only one AI
  Assistant module is built (not three separate panels yet), but it's
  provider-switchable — a toggle in Settings > AI Assistant (`aiProvider`)
  picks between Gemini and Claude, with both providers' keys able to stay
  filled in at once so switching costs nothing (see `AI_PROVIDERS` in
  `js/data/api.js`). The toggle itself is gated: tap the "AI Assistant"
  section label 10 times to reveal it (Android-hidden-developer-options
  style, in-memory only -- resets on reload). The active provider's own
  key/model fields are NOT gated, so everyday setup never needs the tap
  gesture, only actually switching providers does. OpenAI isn't offered as
  a toggle option: its API sends no CORS headers for browser-origin
  requests at all, so it can't be called directly from the browser the way
  Gemini/Claude can -- adding it would need a proxy server standing in
  front of it first. The
  three-separate-panels idea above is still the eventual shape; for now
  it's one panel with a provider switch, not three panels at once.
- **Telegram integration** — both as a chat surface and as a notification
  channel (due bills/tasks pushed to Telegram, since that's more reliable
  than browser push notifications, especially on iPhone). Chosen deliberately
  over WhatsApp/Google Messages/iMessage, which don't have a clean, personal,
  automatable API.
- **Sharebox** — the in-app module is now BUILT (see Built ✅ above).
  Friend-mesh (several friends in one space, not just a pair) already works
  with zero extra code — `sharebox_members` is a plain many-to-many table
  and nothing assumes exactly 2 people, just never exercised with 3+ real
  people yet. *(Ruled out — dead: a separate lightweight companion app for
  a friend who doesn't run full Life OS. Alek's call: anyone he'd actually
  share with already has the full app.)*
- ~~AI-written yearly recap~~ — DONE (see Built ✅, Milestones entry).
- ~~Academic pacing check~~ — DONE (see Built ✅, Education entry).

## 5. Rough order of what's left

1. ~~Multi-user accounts (phase 1)~~ — DONE and live-verified: email/password
   + Google sign-in, password reset, profiles table (see Built ✅).
2. ~~AI-powered Daily Paper~~ — DONE (Gemini, device-local Gemini key,
   daily/account-scoped cache); per-user notifications remain open.
3. ~~The four Tier-2 features~~ — DONE: Knowledge Graph, The Orrery,
   Time Machine, and QR Airgap Sync all shipped (see Built ✅)
4. ~~AI Assistant (Gemini) + Telegram (send-only)~~ — DONE (see Built ✅,
   switched from Claude to Gemini). A separate ChatGPT panel + full two-way
   Telegram chat are still open, out of scope for this pass. (Cross-LLM
   relay chaining was considered and dropped — see section 4 above.)
5. ~~AI-written yearly recap~~ — DONE (see Built ✅, Milestones entry).
6. ~~Passkey/biometric app lock~~ — DONE (see Built ✅).
7. ~~AI with continuity~~ — DONE (see Built ✅).
8. ~~App-wide spaced repetition~~ — DONE (see Built ✅, Recall).
9. ~~Predictive forecasting~~ — DONE (see Built ✅, Almanac Forecasts).
10. ~~What-if simulation sandbox~~ — DONE (see Built ✅, Almanac What If).
11. ~~AI-suggested knowledge-graph edges~~ — DONE (see Built ✅, Knowledge
    Graph).
12. ~~Camera-to-data capture~~ — DONE, scoped to Documents (see Built ✅).
13. ~~Rules & automation engine~~ — DONE, scoped to 2 built-in rules (see
    Built ✅).
14. ~~Health-device ingestion~~ — DONE, Apple Health only (see Built ✅).
15. Remaining routine-build ideas (Dream Journal, Rabbit Hole Journal,
    Conversation Starters, Ghost Days, The Almanac, Life as Music, Library
    of Babel, Theme-from-photo)
16. Additional interfaces (Vespera; more mobile interfaces beyond `mobile-1`)
17. Someday: a standalone music-practice app (progressions, play-along,
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

**Foundational rearchitecture**
- **Event-sourced core** — every change becomes an immutable event instead of
  an in-place edit. Unlocks real undo/redo everywhere and per-record history,
  and makes the already-planned Time Machine trivial once it exists.
- **CRDT-based sync** — replace last-write-wins Drive sync with a real
  conflict-free merge engine, so simultaneous edits on two devices combine
  automatically instead of one silently clobbering the other.

**Capture & reach**
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
- **Garmin/Fitbit health ingestion** — Apple Health export import shipped
  (see Built ✅, Health module entry). Garmin/Fitbit would each need their
  own OAuth/API research pass first (same "verify before building"
  treatment already given to Spotify/YouTube/Google Photos) — not
  attempted yet.

**Privacy & security**
- **Zero-knowledge encrypted vault** — end-to-end encryption at rest, so
  data synced to Drive/Supabase is stored as ciphertext only — even the
  server can't read it. Pairs naturally with the multi-user accounts work.

**Platform & payoff**
- **Personal local API + plugin SDK** — expose your own data through a
  documented local interface so you (or scripts) can build on it without
  touching core — the same registry philosophy that already governs
  interfaces, extended to data. What turns this from an app into a platform.
- **Generative "Year in Review" film** — an auto-produced montage from your
  photos + milestones + stats, scored by the Life-as-Music synth engine
  you already have. The emotional payoff piece.
- **Camera-vision cataloging (Quartermaster)** — confirmed direction
  (2026-07), not yet built, moved down from the far tier: photograph a
  shelf/pantry/garage and have it catalog items into Quartermaster.
  Simpler than the original framing — no attempt at detecting *quantity*
  or "running low" from the photo (fill-level estimation is a genuinely
  harder vision problem, and "low" is subjective anyway); that stays a
  manual flag on the item, same as everything else in Quartermaster
  today, UNLESS labeled via the few-shot flow below.
  **Confirmed in scope (2026-07): a few-shot/in-context-learning version
  of low-stock detection.** Not model retraining — a labeled-example
  flow. When logging an item, you can tag a reference photo with your own
  label ("low," "full," whatever vocabulary makes sense to you). Judging
  a later photo of that item sends the new photo *plus* your 5 most
  recent labeled examples to the vision model as calibration references,
  asking it to place the new photo relative to them. 5 is a flat cap, not
  a range — the cost difference between 3 and 5 reference images is
  negligible, especially once they're compressed/low-res (see below), so
  there's no reason to skimp. Approximate placement ("low" vs. "full") is
  the goal, not precise quantity ("how much milk is in the gallon") —
  that's a genuinely harder vision problem and not what this is for.
  Accuracy improves as your label library grows — not because the model
  changed, but because each judgment has more of your own examples to
  work from. More buildable than the far-tier framing implied either
  way: it's an extension of the multimodal vision-AI pattern already
  shipped for Documents' camera-to-data scan (`extractDocumentFromImage`
  in `js/data/api.js`), not a new capability. Once built, pairs naturally
  with a third Rules & Automation rule: an item flagged low auto-creates
  a "buy X" task, same off-by-default/opt-in shape as the
  habit-milestone and document-renewal rules already shipped.
  **Confirmed cost approach (2026-07):** resize/compress client-side
  before sending to the vision API — cap the long edge around
  1000-1500px, no reason to send a full-resolution native photo when
  object recognition doesn't need anywhere near that much detail. Real
  cost savings (vision API pricing scales with image resolution/tiles).
  Worth retrofitting onto the existing Documents camera-to-data scan too
  — it currently sends the photographed file as-is, full resolution,
  same easy win available there.

**Far tier — scale-and-space swings (each closer to its own product than a
feature; heavy on either engineering, storage, or compute)** *(excluded from
default "what's on our list" recaps as of 2026-07 — Alek's call, see
CLAUDE.md; stays fully written here, just not surfaced unasked)*
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
  negotiated enterprise minimums at scale — see pricing note below), plus a
  live crypto price-ticker panel (CoinGecko, free/keyless). Needs its own
  backend token exchange (rides on the Supabase backend, can't be
  client-only) — this is the most security-sensitive item on either list.
- ⏸️ PARKED — **Spotify listening stats** — fully scoped (real API limits,
  Premium requirement, the export+polling path to real history) but not
  started. See `FUTURE_FEATURES.md` §4 for the full writeup.
- ~~Google Photos import~~ — DONE (see Built ✅, Photos/Gallery entry).
- ~~Passkey/biometric app lock~~ — DONE (see Built ✅, Milestones-adjacent
  entry above).
- ~~AI with continuity~~ — DONE (see Built ✅, Daily Paper entry above).
- ~~App-wide spaced repetition~~ — DONE (see Built ✅, Recall entry above).
- ~~Predictive forecasting~~ — DONE (see Built ✅, Almanac Forecasts entry
  above).
- ~~What-if simulation sandbox~~ — DONE (see Built ✅, Almanac What If entry
  above).
- ~~AI-suggested knowledge-graph edges~~ — DONE (see Built ✅, Knowledge
  Graph entry above).
- ~~Camera-to-data capture~~ — DONE, scoped to Documents (see Built ✅,
  Documents vault entry above).
- ~~Rules & automation engine~~ — DONE, scoped to 2 built-in rules (see
  Built ✅, Knowledge-Graph-adjacent entry above).
- ~~Health-device ingestion~~ — DONE, Apple Health only (see Built ✅,
  Health entry above).

*(Ruled out — dead, not parked, don't resurface: YouTube real watch
history / Watch Later sync — both deprecated from the YouTube Data API in
2016 for privacy reasons, no path back (empty placeholder playlists, no
OAuth scope, no third-party workaround); Liked Videos is a different,
messier signal than the manual watch-later queue already in Links, not
worth building instead. Stock tickers (Alpha Vantage/Finnhub) — not
wanted, killed outright, independent of Plaid/Financial Center staying
parked above.)*

**Paid API pricing, as researched (verify at signup — these change often):**
- **Plaid** (bank/investment linking): Sandbox (fake data, for building) is
  free. Production is usage-based — commonly cited around $0.30-$3 per
  connected account/month for subscription-style products (Transactions,
  Investments), or $0.10-$0.60 per call for one-time-fee products (Auth,
  Identity, Income); Plaid doesn't publish one flat price list, and
  meaningful scale typically means a negotiated contract.

Nothing here is fixed in stone — the plan has already flexed a few times
this week, and that's expected. This doc is meant to be a snapshot you can
hand to a future session (or future you) to get back up to speed fast.

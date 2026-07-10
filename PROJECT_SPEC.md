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
  "packs" (name, code, TTS locale), each owning its own decks/cards
  (spaced-repetition flashcards, Again/Good/Easy, browser TTS, a study
  streak) and Lessons (grammar/syntax/morphology explainers with example
  sentences). Japanese ships as the default pack with a starter hiragana
  deck and four starter lessons (sentence structure, particles, verb
  forms, adjective types) — a starting set, not a full curriculum. Adding
  a second language (Spanish, etc.) later is just a new pack via the
  in-app "+ Add language" form, no rebuild needed.
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
  practice sheet. Not yet AI-written (see "Still to build" below).
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

### Still to build 📋
- **Multi-user accounts** — email + password sign-up/sign-in (alongside the
  existing Google sign-in), password recovery, and per-user data isolation —
  the foundation for every person having their own private space, AI Daily
  Paper, and notifications. Sharebox v2's Supabase backend and RLS pattern is
  the proven foundation this builds on.
- **AI-powered Daily Paper** — turn the current list-based brief into an
  actual AI-written editorial (Claude/GPT/Gemini), once accounts exist to
  scope it per user.
- **Per-user notifications** — depends on accounts existing first.
- **AI assistant modules** (Claude/ChatGPT/Gemini panels) + cross-LLM relay +
  **Telegram integration** — plumbing/UI can be built without your real API
  keys/bot token, but live testing needs them, and direct browser-to-API
  calls may hit CORS restrictions that won't be known until tested live.
  Treat as "needs a follow-up session with you present," not a solo build.
- **AI-written yearly recap** — needs a working AI module first (see above)
- **Knowledge Graph** — link anything to anything (a task to a contact, a
  book to a milestone...) and browse it as a radial web of connections.
  Needs a real graph-rendering/layout design decision before building —
  same category as the Harmony Map.
- **The Orrery** — the dashboard reimagined as a solar system, each life area
  a planet whose size/speed/wobble reflects how that area is doing. Needs an
  orbital-layout design decision before building.
- **Time Machine** — scrub a slider back through time and see the app as it
  looked on a past day. Needs a real design decision about reconstructing
  historical snapshots from data that was never versioned.
- **QR Airgap Sync** — sync between your two devices via QR codes, no
  internet or account needed. Needs a sync-protocol design decision, same
  category as Drive sync.
- **Dream Journal** (with recurring-pattern detection), **Rabbit Hole
  Journal** (research tangents), **Conversation Starter generator**, **Ghost
  Days** (ambient "on this day" expansion), **The Almanac** (long-horizon
  personal stat correlations), **Life as Music** (generative soundtrack from
  your data, reusing the Chords synth engine), **Library of Babel**
  (story-based reading library, replacing the Lessons tab), **Theme-from-
  photo** (generate an accent palette from a gallery photo) — all planned,
  none started; all look like routine builds (no architecture decision
  needed) except where noted above.

## 3. Additional interfaces 📋
- **Vespera** — a spatial interface (LifeOS as an orbital station you
  navigate through, not a dashboard). Fully planned in `VESPERA_SPEC.md`,
  including the district/space-to-module mapping and v1 scope; not
  started. Building it is Tier 2 (interface-registry design).
- An LCARS-inspired mode (Star Trek control-panel aesthetic, original
  execution — no copyrighted assets)
- Any others that come to mind along the way

## 4. New ideas from later conversations 📋

Everything below came out of talking through what would actually feel
"you" rather than generic — added to the plan, not built yet:

- **Three separate AI assistant modules** — Claude, ChatGPT, and Gemini each
  get their *own* themed panel (not one generic "AI" tab), each with your own
  API key stored only on your device. Each can be given permission to read
  and act on your actual Life OS data (tasks, notes, etc.) on a per-question,
  opt-in basis.
- **Cross-LLM relay** — chain a single question across more than one AI in
  sequence (e.g. Claude answers, GPT reacts to that, Gemini synthesizes both)
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

1. Multi-user accounts (email/password + password recovery), building on
   Sharebox v2's now-fixed Supabase/RLS foundation
2. AI-powered Daily Paper + per-user notifications (depend on accounts)
3. The four Tier-2 "needs a design decision first" features: Knowledge Graph,
   The Orrery, Time Machine, QR Airgap Sync
4. AI modules (Claude/GPT/Gemini) + relay + Telegram (needs you present for real API keys)
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

Nothing here is fixed in stone — the plan has already flexed a few times
this week, and that's expected. This doc is meant to be a snapshot you can
hand to a future session (or future you) to get back up to speed fast.

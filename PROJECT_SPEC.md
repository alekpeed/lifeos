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
  genuinely different layouts. The first one ("Meridian," a calm sidebar +
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
  and estimated word counts, genre breakdown, author tracking, ratings/notes
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
- **Japanese learning** — decks and cards, a starter hiragana deck, a
  simple spaced-repetition review session (Again/Good/Easy), browser
  text-to-speech playback, and a study streak
- **Geolocation nudges in Places** — a manual "check nearby places" action
  surfaces want-to-go spots and stale revisit-flagged places within 1km.
  Deliberately not passive/background — a plain PWA (especially on iOS
  Safari) can't reliably run background geofencing, so this is
  foreground/user-triggered by design, not a missing feature.

### Next up 🔜
- **Google Drive sync** — the two-device backbone. Blocked on you creating a
  one-time Google Cloud "Client ID" (walkthrough already given); this is the
  next real build task once that's ready.

### Still to build 📋
- **Piano/guitar chord tool** — type a chord progression, get real voicings
  for guitar or piano (melody-aware voicing comes in a later phase). Tier 2
  work (chord-voicing logic) — needs a model switch before building.
- **AI assistant modules** (Claude/ChatGPT/Gemini panels) + cross-LLM relay +
  **Telegram integration** — plumbing/UI can be built without your real API
  keys/bot token, but live testing needs them, and direct browser-to-API
  calls may hit CORS restrictions that won't be known until tested live.
  Treat as "needs a follow-up session with you present," not a solo build.
- **Google Calendar sync** — reuses the same Google sign-in as Drive sync,
  so it's blocked on the same OAuth Client ID.
- **AI-written yearly recap** — needs a working AI module first (see above)
- **Documents/Contacts/Finance cross-links, "on this day," "surprise me"** —
  smaller polish items, not yet built
- **A friend-sharing "Sharebox"** — see Section 4

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
- **A friend-sharing "Sharebox"** — a small module inside Life OS for you,
  plus a separate lightweight companion app for a friend, both reading/writing
  the same shared Google Drive folder (links, notes, urgency flags, files)
- **Google Calendar sync** — push the due-soon feed into an actual calendar,
  reusing the same Google sign-in as Drive sync
- **AI-written yearly recap** — once an AI module can read your data, have it
  draft the actual recap narrative, not just tally up numbers (the recap's
  numbers-driven aggregation already exists in Milestones)

## 5. Rough order of what's left

1. Google Drive sync (step 0: you create the Google OAuth Client ID)
2. Piano/guitar chord tool (Tier 2)
3. AI modules (Claude/GPT/Gemini) + relay + Telegram (needs you present for real API keys)
4. Google Calendar sync (blocked on the same OAuth Client ID as #1), AI-written recap
5. Additional interfaces (Vespera, LCARS)
6. Sharebox companion app for your friend

Nothing here is fixed in stone — the plan has already flexed a few times
this week, and that's expected. This doc is meant to be a snapshot you can
hand to a future session (or future you) to get back up to speed fast.

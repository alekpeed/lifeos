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

### Next up 🔜
- **Google Drive sync** — the two-device backbone. Blocked on you creating a
  one-time Google Cloud "Client ID" (walkthrough already given); this is the
  next real build task once that's ready.

### Still to build 📋 (original spec)
- **Japanese learning** — kana drills, kanji/vocabulary flashcards with
  spaced repetition, JLPT preset decks, streaks, browser text-to-speech
- **Piano/guitar chord tool** — type a chord progression, get real voicings
  for guitar or piano (melody-aware voicing comes in a later phase)
- **Utility tools** — currency converter, unit converter, a timezone helper
  for people/places abroad
- **Milestones / yearly recap** — a life-events timeline and an
  auto-generated year-in-review pulled from every other module
- **Cross-cutting** — global search across everything, and a manual
  JSON export/import as a Drive-independent backup

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

- **Photos/Gallery module** — albums, grid + lightbox view, tags
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
- **A unified habit/streak tracker** — one shared streak mechanic (instead of
  separate one-offs for Books and Japanese) for things like workouts, practice,
  water intake
- **Geolocation nudges in Places** — passive "you're near a Want-to-Go spot"
  or "haven't been back here in a while" prompts
- **AI-written yearly recap** — once an AI module can read your data, have it
  draft the actual recap narrative, not just tally up numbers
- **Health/sleep tracker** — workouts, sleep hours, hydration, etc., entered
  manually (no live Garmin API — no clean personal API exists, so this is
  hand-entered data, possibly from a Garmin CSV export down the road)

## 5. Rough order of what's left

1. Google Drive sync (step 0: you create the Google OAuth Client ID)
2. Milestones/recap
3. Utility tools (currency/unit/timezone), "on this day," "surprise me"
4. Japanese learning module
5. Piano/guitar chord tool
6. Photos/Gallery
7. AI modules (Claude/GPT/Gemini) + relay + Telegram
8. Calendar sync, habit engine, geolocation nudges, AI recap, health tracker
9. Additional interfaces (Vespera, LCARS)
10. Sharebox companion app for your friend

Nothing here is fixed in stone — the plan has already flexed a few times
this week, and that's expected. This doc is meant to be a snapshot you can
hand to a future session (or future you) to get back up to speed fast.

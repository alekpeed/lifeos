# Life OS

A personal life-management application. One place for the whole of a life — what you
have to do, what you own, what you've read, where you've been, what you spent, who you
know, what you learned — held as records you own, on a machine you own, in an app that
notices things about them.

**37 modules across 8 domains, in one native application.**

---

## What it's for

Most software of this kind holds one slice of a life and holds it in someone else's
database. The premise here is the opposite: one application, every slice, on your own
disk, and an app that gets more useful as more of your life goes into it — because the
parts can see each other.

That cross-visibility is the actual point. A habit streak writes itself into Milestones.
Finishing a book levels a branch in Skill Trees. A document nearing expiry becomes a
task. The Almanac correlates your sleep against your
spending. Time Machine can say how much of today's record existed on a day two years
ago. None of that is possible when the pieces live in different products.

## What it runs on

One Kotlin / Compose Multiplatform codebase. The Linux desktop build is the target this
document describes; the same source also builds for Android and Windows. There is no web
app and no browser involved.

- **Records** are JSON under a named key on disk (`~/.lifeos/<Key>.txt`).
- **Files** — photos, PDFs, ebooks — live in a local blob store, never uploaded.
- **Sync** is optional: sign in and each record key syncs through Supabase, so the same
  account on another machine sees the same data. Signed out, everything still works.
- **AI** runs on your own API key (OpenAI, Anthropic or Gemini), and is always given
  your records as context rather than being asked to invent anything.
- **The network is needed for four things only**: AI, weather, market and currency
  rates, and the one shared feature. Everything else is local and works offline.

## The principles it's actually built on

These aren't aspirations — they're enforced in the code, and anything proposed against
this document should hold to them.

1. **It never invents.** Where the app can't know something, it says so. Time Machine
   states plainly that it can't see deleted records or old titles. A scan that found
   nothing separable says "nothing itemized" instead of quietly filing one guessed
   record. An AI answer is grounded in your data or it doesn't answer.
2. **Local first.** No account is required to use it. Sync is a feature, not a gate.
3. **No save button.** Edits persist as they're made.
4. **Degrade, don't lie.** Where a machine lacks a capability, the control isn't drawn —
   no dead buttons.
5. **Nothing is engagement-driven.** There are no streaks-for-streaks'-sake, no
   notifications designed to pull you back, no feed. The Briefing exists to be emptied.
6. **Your data leaves only when you say so.** Sharing is one explicit module.

## Where it stands

All 37 modules are built and compile; the desktop build runs. What remains is short:

- **Two features blocked on credentials** — importing from Google Photos, and pushing
  to a calendar. Both need an OAuth client the owner has to create.
- **One visualization awaiting artwork** — the Knowledge Graph's radial view, which
  currently works as a functional list.
- **Two open product decisions** — whether Notifications adds a shared activity feed,
  and when an animated character planned for the app gets built.
- **Two small gaps** — projects in Tasks are a text field rather than first-class
  records, and weather is by named city.
- **Six platform gaps**, all on the desktop side and all listed with their causes in
  `NATIVE_FEATURES.md` — the notable ones being no ebook import, no Apple Health
  import, and no notifications when the app is closed.


## Across every module

Behaviour that isn't any one module's, implemented once and present everywhere:

- **Multi-select on every list.** Long-press a row — press-and-hold works with a mouse —
  or hit Select, tick as many as you want, then act on all of them at once. Delete asks
  for confirmation and frees the records' photos and files exactly as a single delete
  does. Some lists add a second bulk action where one is obviously useful: Archive in
  Ideas, Watched/Read in Links, Mark done for assignments, Packed for packing items,
  moving between the two lists in Places, Done on the bucket list.
- **A microphone wherever text is typed.** One button, which records until you say
  you're finished and then transcribes with Whisper — a pause mid-sentence is a pause,
  not the end of the take, and the transcript comes back punctuated. On the phone the
  system recognizer is available instead (offline, free, cuts you off at the first pause),
  switchable in Settings; on a computer Whisper is the only dictation there is. Needs a
  network connection and an OpenAI key, and the audio leaves the device.
- **Any number of files on any record.** Images as a photo grid you can tap to enlarge,
  everything else as a named list. Bytes live in a local blob store that never syncs and
  never lands in a backup export.
- **Dates are typed or picked**, the same field everywhere.
- **Every write is confirmed** with a brief toast, so nothing saves silently.
- **Nothing ships with example data.** An empty module says it's empty and says what
  goes in it; it doesn't seed placeholder records that look like your own.

---

# The sections

Each entry gives what the module is and what a record in it holds. Field names are the
real ones the application stores.

## Operations
*Run your day.*  (7 modules)

### Operations
A full-screen graphical hub for the day: the module's artwork is the interface, with
tap regions mapped onto it that open Today, Tasks, Daily Paper, Command, Briefing and
Notifications, plus the quick-capture controls — dictation, a quick note, the camera
and a barcode scan. Runs immersive, so it takes the whole display.

- **HitRect** — `id`, `x`, `y`, `width`, `height`
- On Linux: no camera, no barcode scanning.

### Today
The landing page. Everything today asks of you, gathered from every other module: what's
overdue, what's due, which habits are unchecked, what happened on this date in past
years, and one thing you once said you wanted to get to.

- **DueLine** — `icon`, `title`, `meta`, `moduleId`, `urgent`
- **Pick** — `kind`, `title`, `moduleId`
- **ParsedCmd** — `type`, `title`, `due`, `amount`
- Uses: AI, Weather
- On Linux: no microphone, no speech.

### Daily Paper
A newspaper about your own life, written each morning. An AI editorial grounded strictly
in the day's real facts, a docket of what's coming, the weather, and a re-rollable
suggestion.

- **Docket** — `kind`, `title`, `date`, `overdue`, `key`
- **OnThisDay** — `kind`, `title`, `year`
- **TaskLite** — `id`, `title`, `due`, `done`
- Uses: AI, Telegram, Weather
- On Linux: no PDF export.

### Tasks
The working list. Four states (not started / in progress / waiting / done),
priority, any due date, projects, tags, repeats that spawn the next occurrence when you
complete one, snoozing, subtask checklists, and a board view you can drag cards across.

- **Subtask** — `id`, `text`, `done`
- **Task** — `id`, `title`, `status`, `priority`, `due`, `project`, `tags`, `notes`, `waitingOn`, `subtasks`, `recur`, `snoozedUntil`, `completedDate`
- The board is a column per status. Press and hold a card to lift it, drag it over
  another column, let go — the target lights up and the board auto-scrolls near either
  edge. ‹ / › on each card do the same one step at a time.
- The row checkbox *selects* rather than completes, so completing and deleting both act
  on a whole selection from one bar. Completing flips to Reopen when everything picked
  is already done.

### Command
One input. Type an instruction in plain language and it proposes a record and a
destination module; you confirm before anything is written.

- **ParsedCmd** — `type`, `title`, `due`, `amount`
- **DueLine** — `icon`, `title`, `meta`, `moduleId`, `urgent`
- **Pick** — `kind`, `title`, `moduleId`
- Uses: AI, Weather
- On Linux: no microphone, no speech.

### Briefing
Everything wanting attention in one column, each row carrying its own action — complete,
check in, snooze, renew. The page exists to be emptied.

- **BriefLine** — `key`, `text`, `note`, `moduleId`, `action`, `action2`, `resolve2`
- **Lin** — `slope`, `intercept`
- **AlmanacModel** — `corrSleepHabits`, `corrWorkoutSleep`, `corrSleepTasks`, `sleepHabitsLin`, `sleepTrend`, `readingForecasts`, `spendForecast`, `weekdaySkips`, `recurring`, `sleepValues`
- **Entry** — `s`, `m`, `t`, `v`
- **Index** — `hash`, `entries`
- **Ranked** — `source`, `text`, `moduleId`, `score`
- **Area** — `label`, `days`
- **ChatMsg** — `fromUser`, `text`
- **Conversation** — `id`, `name`, `msgs`
- **Attention** — `icon`, `title`, `meta`, `moduleId`, `urgent`, `sortKey`
- **Reminder** — `text`, `atEpochMillis`
- Uses: AI
- On Linux: no background alerts, no speech.

### Notifications
What's overdue, due soon, or expiring, and a place to set a reminder.

- **Attention** — `icon`, `title`, `meta`, `moduleId`, `urgent`, `sortKey`
- **Reminder** — `text`, `atEpochMillis`
- **Lin** — `slope`, `intercept`
- **AlmanacModel** — `corrSleepHabits`, `corrWorkoutSleep`, `corrSleepTasks`, `sleepHabitsLin`, `sleepTrend`, `readingForecasts`, `spendForecast`, `weekdaySkips`, `recurring`, `sleepValues`
- **Entry** — `s`, `m`, `t`, `v`
- **Index** — `hash`, `entries`
- **Ranked** — `source`, `text`, `moduleId`, `score`
- **BriefLine** — `key`, `text`, `note`, `moduleId`, `action`, `action2`, `resolve2`
- **Area** — `label`, `days`
- **ChatMsg** — `fromUser`, `text`
- **Conversation** — `id`, `name`, `msgs`
- Uses: AI
- On Linux: no background alerts, no speech.

---

## Archive
*What you keep.*  (7 modules)

### Documents
Anything with an expiry date — IDs, policies, warranties. Import a scan and AI reads the
title, issuer, number and expiry out of it.

- **Document** — `id`, `title`, `category`, `issuer`, `policyNumber`, `expiryDate`, `transcription`, `notes`, `linkedContact`, `photoBlob`
- Uses: AI
- On Linux: no camera.

### Links
Saved videos and articles, with cached thumbnails, tags and read state.

- **Link** — `id`, `url`, `type`, `title`, `tags`, `status`, `shareWith`, `videoId`, `thumbBlob`

### Books
The reading list, the shelf, and a real in-app reader. A book holds any number of
readable files, each remembering its own place.

- **ReadLog** — `id`, `date`, `pagesRead`
- **BookFile** — `id`, `name`, `kind`, `blobId`, `frac`, `page`
- **Book** — `id`, `title`, `author`, `genre`, `status`, `totalPages`, `currentPage`, `startedDate`, `finishedDate`, `rating`, `notes`, `logs`, `photoBlob`, `readFrac`
- On Linux: no camera, no ebook parsing.

### Photos
Albums of captioned photos.

- **Caption** — `id`, `text`, `note`, `blob`
- **Album** — `id`, `name`, `description`, `captions`
- On Linux: no camera.

### Collections
Things you collect, with per-item acquired dates, tags and notes.

- **CollItem** — `id`, `name`, `acquiredDate`, `tags`, `notes`
- **Collection** — `id`, `name`, `description`, `items`, `photoBlob`
- On Linux: no camera.

### Time Capsules
A sealed note to your future self, genuinely hidden until its date.

- **TimeCapsule** — `id`, `title`, `body`, `sealedUntil`, `createdAt`, `photoBlob`
- On Linux: no camera.

### Milestones
What's worth remembering, on a timeline — plus a yearly recap that counts your year
across every module and writes a narrative of it.

- **Milestone** — `id`, `title`, `date`, `category`, `notes`, `photoBlob`
- Uses: AI
- On Linux: no camera.

---

## Logistics
*Places, supply and trips.*  (3 modules)

### Places
Where you've been and where you want to go: ratings, visit dates, private notes-to-self,
photos, a real street map, and a bucket list.

- **Place** — `id`, `name`, `listType`, `category`, `rating`, `address`, `lat`, `lng`, `notes`, `visitDates`, `notesToSelf`, `photoBlob`, `contacts`
- **BucketItem** — `id`, `title`, `done`, `targetDate`
- The Map tab draws OpenStreetMap tiles with your places pinned on top — real streets
  and labels, pinch to zoom, panning that wraps at the date line. Tiles are cached on
  the device as you look at them, so places you've already seen work with no
  connection. Selecting a pin offers Directions, which hands off to whatever the
  machine uses for maps; a place with coordinates has the same action in its editor.
  Coordinates come from "Use my location" or from geocoding the name/address.
- Uses: Weather
- On Linux: no location.

### Quartermaster
What you own, where it is, what's running out, and what you've lent. Photograph a shelf
and it catalogues the contents.

- **InventoryItem** — `id`, `name`, `location`, `tags`, `lentTo`, `lentSince`, `photoBlob`, `stockCheckedAt`
- **StockRef** — `id`, `label`, `blob`
- Uses: AI
- On Linux: no camera.

### Packing Lists
One checklist per trip, from four starting templates.

- **PackItem** — `id`, `name`, `category`, `packed`
- **PackingList** — `id`, `name`, `tripDate`, `items`

---

## Discovery
*Learning and curiosity.*  (5 modules)

### Education
Semesters, courses and assignments — with grades, GPA, time spent, and a pacing plan: a
target, dated checkpoints, and logs of what you actually did against it.

- **KeyDate** — `label`, `date`
- **Checkpoint** — `date`, `targetByThen`
- **ProgressLog** — `id`, `date`, `unitsAdded`
- **Semester** — `id`, `name`, `startDate`, `endDate`
- **Course** — `id`, `semesterId`, `name`, `credits`, `grade`, `readingListTag`, `notes`, `keyDates`
- **Assignment** — `id`, `courseId`, `title`, `dueDate`, `status`, `percentComplete`, `timeSpentMinutes`, `grade`, `pacingTarget`, `pacingUnit`, `paceCheckpoints`, `progressLogs`

### Skill Trees
Your real activity expressed as levels across three branches. Nothing is entered here;
it reads what you did elsewhere.

- **Skill** — `name`, `icon`, `xp`, `blurb`

### Ideas
Fast capture, tagged and searchable, promotable to a task.

- **Idea** — `id`, `text`, `tags`, `archived`, `created`
- On Linux: no microphone.

### Rabbit Holes
What you went down a hole researching — running notes and links, active or resolved.
An open thread you haven't touched in three weeks is marked cold on its row and surfaces
in the Briefing with a one-tap Resolve, because an abandoned thread should be closed
rather than nagged about forever.

- **HoleLink** — `id`, `url`, `title`
- **RabbitHole** — `id`, `topic`, `notes`, `links`, `status`, `startedDate`, `touchedDate`, `photoBlob`
- `touchedDate` is stamped on every edit; it's what "gone cold" is measured against.
  Records written before it existed fall back to their start date.
- On Linux: no camera.

### The Almanac
Patterns computed from your own data: correlations, forecasts, and what-ifs.

- **Lin** — `slope`, `intercept`
- **AlmanacModel** — `corrSleepHabits`, `corrWorkoutSleep`, `corrSleepTasks`, `sleepHabitsLin`, `sleepTrend`, `readingForecasts`, `spendForecast`, `weekdaySkips`, `recurring`, `sleepValues`
- **Entry** — `s`, `m`, `t`, `v`
- **Index** — `hash`, `entries`
- **Ranked** — `source`, `text`, `moduleId`, `score`
- **BriefLine** — `key`, `text`, `note`, `moduleId`, `action`, `action2`, `resolve2`
- **Area** — `label`, `days`
- **ChatMsg** — `fromUser`, `text`
- **Conversation** — `id`, `name`, `msgs`
- **Attention** — `icon`, `title`, `meta`, `moduleId`, `urgent`, `sortKey`
- **Reminder** — `text`, `atEpochMillis`
- Uses: AI
- On Linux: no background alerts, no speech.

---

## Management
*Body, home and money.*  (4 modules)

### Habits
Streaks that reset honestly. A day is checked in or it isn't, and missing one resets;
checking in twice can't inflate it, and a mis-tap today can be undone. Habits you
haven't checked in yet appear on Today, and one with a live streak surfaces in the
Briefing.

- **Habit** — `name`, `checkins`, `notes`
- Starts empty. No seeded example habits — an empty list says so rather than shipping
  two placeholders that look like records you chose.

### Health
Daily log, workouts with pace maths, metric trends, and imports from Garmin and Apple
Health exports.

- **Reading** — `id`, `metric`, `value`, `unit`, `date`
- **DailyLog** — `date`, `workoutType`, `workoutMinutes`, `waterOz`, `weightLb`, `notes`
- **Workout** — `id`, `date`, `type`, `minutes`, `distance`, `distanceUnit`, `notes`
- On Linux: no streamed file import.

### Recipes
Cook from them, rescale every quantity live to the servings you want, and log each time
you cooked it.

- **GroceryTotal** — `name`, `unit`, `qty`, `exact`, `raw`
- **Ingredient** — `id`, `name`, `qty`, `unit`
- **Step** — `id`, `text`
- **CookLog** — `id`, `date`, `notes`
- **Recipe** — `id`, `title`, `baseServings`, `tags`, `notes`, `ingredients`, `steps`, `cookLogs`, `photoBlob`
- On Linux: no camera, no screen wake-lock.

### Finance
Ledger, bills with payment history, subscriptions, and holdings priced live. Photograph
a receipt and it reads it.

- **Entry** — `id`, `desc`, `amount`, `category`, `recurring`, `date`, `photoBlob`
- **Payment** — `date`, `amount`
- **Bill** — `id`, `name`, `amount`, `dueDate`, `cadence`, `autopay`, `remindDays`, `category`, `paymentHistory`, `contact`, `attachments`
- **Subscription** — `id`, `name`, `amount`, `cycle`, `active`, `category`, `renewalDate`
- Uses: AI, Markets
- On Linux: no background alerts, no camera.

---

## Intelligence
*The app thinking about you.*  (5 modules)

### Ask
A question about your own records — either an AI answer with its sources, or semantic
search with no AI at all.

- **Lin** — `slope`, `intercept`
- **AlmanacModel** — `corrSleepHabits`, `corrWorkoutSleep`, `corrSleepTasks`, `sleepHabitsLin`, `sleepTrend`, `readingForecasts`, `spendForecast`, `weekdaySkips`, `recurring`, `sleepValues`
- **Entry** — `s`, `m`, `t`, `v`
- **Index** — `hash`, `entries`
- **Ranked** — `source`, `text`, `moduleId`, `score`
- **BriefLine** — `key`, `text`, `note`, `moduleId`, `action`, `action2`, `resolve2`
- **Area** — `label`, `days`
- **ChatMsg** — `fromUser`, `text`
- **Conversation** — `id`, `name`, `msgs`
- **Attention** — `icon`, `title`, `meta`, `moduleId`, `urgent`, `sortKey`
- **Reminder** — `text`, `atEpochMillis`
- Uses: AI
- On Linux: no background alerts, no speech.

### AI Assistant
Named conversations grounded in your data and the current time.

- **ChatMsg** — `fromUser`, `text`
- **Conversation** — `id`, `name`, `msgs`
- **Lin** — `slope`, `intercept`
- **AlmanacModel** — `corrSleepHabits`, `corrWorkoutSleep`, `corrSleepTasks`, `sleepHabitsLin`, `sleepTrend`, `readingForecasts`, `spendForecast`, `weekdaySkips`, `recurring`, `sleepValues`
- **Entry** — `s`, `m`, `t`, `v`
- **Index** — `hash`, `entries`
- **Ranked** — `source`, `text`, `moduleId`, `score`
- **BriefLine** — `key`, `text`, `note`, `moduleId`, `action`, `action2`, `resolve2`
- **Area** — `label`, `days`
- **Attention** — `icon`, `title`, `meta`, `moduleId`, `urgent`, `sortKey`
- **Reminder** — `text`, `atEpochMillis`
- Uses: AI
- On Linux: no background alerts, no speech.

### Knowledge Graph
Labelled links between any two records in the app, resolved live so a renamed record
doesn't leave a stale label.

- **Node** — `source`, `label`
- **Edge** — `aSource`, `aLabel`, `bSource`, `bLabel`
- Uses: AI

### Entropy
What you've been neglecting, module by module.

- **Area** — `label`, `days`
- **Lin** — `slope`, `intercept`
- **AlmanacModel** — `corrSleepHabits`, `corrWorkoutSleep`, `corrSleepTasks`, `sleepHabitsLin`, `sleepTrend`, `readingForecasts`, `spendForecast`, `weekdaySkips`, `recurring`, `sleepValues`
- **Entry** — `s`, `m`, `t`, `v`
- **Index** — `hash`, `entries`
- **Ranked** — `source`, `text`, `moduleId`, `score`
- **BriefLine** — `key`, `text`, `note`, `moduleId`, `action`, `action2`, `resolve2`
- **ChatMsg** — `fromUser`, `text`
- **Conversation** — `id`, `name`, `msgs`
- **Attention** — `icon`, `title`, `meta`, `moduleId`, `urgent`, `sortKey`
- **Reminder** — `text`, `atEpochMillis`
- Uses: AI
- On Linux: no background alerts, no speech.

### Time Machine
What the app knew on a past day: how much of today's record existed then, what arrived
that day, and what you actually did.

- **Event** — `icon`, `text`, `source`
- **Stub** — `key`, `title`
- **StoreSnapshot** — `label`, `stubs`
- **Births** — `seeded`, `born`

---

## People
*Others, and other devices.*  (3 modules)

### Contacts
People and everything you know about them, linked to the places and documents they
appear in.

- **Contact** — `id`, `name`, `emails`, `company`, `title`, `relationship`, `address`, `birthday`, `tags`, `notes`, `photoBlob`
- On Linux: no camera, no phone address book.

### Sharebox
A shared feed between you and someone else, over your own account, arriving live without
a refresh.

- **ShareItem** — `id`, `kind`, `url`, `title`, `body`, `urgency`, `postedBy`, `createdAt`
- **SpaceRow** — `id`, `name`
- **ItemRow** — `id`, `kind`, `url`, `title`, `body`, `urgency`
- Uses: account, file transfer, shared spaces

### QR Sync
Pair another device by scanning a code. Phone-only — the desktop build omits it.

- **UnitDef** — `name`, `toBase`
- **QrMatrix** — `size`, `modules`
- **ScanProposal** — `kind`, `title`, `items`, `text`, `summary`, `fields`, `photoB64`, `suggested`
- Uses: AI, Currency, Markets, Weather, account
- On Linux: no camera.

---

## System
*Running the app.*  (3 modules)

### Search
One box across every record in the app, grouped by module.

- **StatusItem** — `text`, `status`
- **NoteItem** — `title`, `note`
- On Linux: no speech.

### Tools
Currency across ~160 currencies with live rates, time zones, unit conversion, weather,
and market prices.

- **UnitDef** — `name`, `toBase`
- **QrMatrix** — `size`, `modules`
- **ScanProposal** — `kind`, `title`, `items`, `text`, `summary`, `fields`, `photoB64`, `suggested`
- Uses: AI, Currency, Markets, Weather, account
- On Linux: no camera.

### Settings
AI keys and provider, account and sync, Telegram, alert thresholds, the app lock, and
backup.

- Uses: Telegram, TelegramLink, account
- On Linux: no location, no screen wake-lock.

---

# Questions worth thinking about

If you're reading this to generate ideas, these are the seams where the application is
most likely to be missing something. They're offered as directions, not as a brief.

1. **The parts can see each other, but mostly don't.** Three modules feed Skill Trees,
   yet most pairs never meet. Which connections would earn their keep, and which would
   just be clever?
2. **It records well and notices rarely.** The Almanac, Entropy and Time Machine are the
   only places the app draws a conclusion. What else is knowable from records this
   complete?
3. **Capture is still mostly typing.** Scanning reads a photo into structured records.
   What else should be capturable in one gesture?
4. **Nothing decays.** Records live forever at equal weight. Should old ones fade,
   archive, or resurface?
5. **It is single-player by design, with one shared feature.** Where, if anywhere, does
   another person genuinely belong?
6. **What is missing entirely** — not a better version of one of these 40, but a section
   a life plainly has and this app has no room for.

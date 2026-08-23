# Life OS — Android

Everything in the Android application as it stands: how you get into it, the phone
hardware it uses, and all 41 modules with what each one is and what a record in it
holds. Written from the Kotlin source.

**41 modules · 8 domains · one Kotlin / Compose Multiplatform codebase.**

---

## The interface

**A graphical home, not a list.** The default home is artwork: a wheel of eight petals,
one per domain, around a centre. The clock, date and a status ring print into blank slots
the artwork leaves for them. Tapping a petal opens that domain's modules. A bottom bar
holds voice capture, a quick note, a code scanner and the assistant, with a large camera
button in the middle. Every region is a traced polygon mapped onto the image, so what
lights up under your thumb is the exact shape drawn.

**Light is the interaction vocabulary.** A ray sweeps the wheel when the app opens, the
core breathes continuously, and whatever you press flashes and fades. It's drawn over the
artwork rather than baked into it, and it's timed off the frame clock so it runs even
with device animations switched off.

**A plain launcher is also there**, and the interface is switchable in Settings.

**Every module page** sits under a persistent back control and the module's name, and
saves as you type — there is no save button anywhere in the app.

## The camera is a primary input

The centre button shoots anything and the app works out what it is:

- a photographed to-do list → many tasks, not one
- a shelf or pantry → many inventory items
- a business card → a contact
- a receipt → a ledger entry
- a document → a filed document with its text, issuer and expiry read out of it
- a book's barcode → an ISBN lookup with cover art

What comes back is a **proposal** — what it thinks it saw, itemized, with a suggested
destination among seven modules. You confirm or redirect it. Nothing is written silently,
and if it found nothing separable it says so rather than filing one guessed record.

## What the phone provides

| Capability | Used by |
|---|---|
| Camera and gallery | Documents, Photos, Places, Quartermaster, Finance, Recipes, Milestones, Collections, the scanner |
| QR / barcode / ISBN scanning | Books, QR Sync, the scanner |
| Alarms that fire with the app closed | Notifications, bill due dates, document expiry |
| Pinned ongoing notification | the next thing due |
| Location and arrival geofences | Places |
| Microphone, driven by the app itself | the mic button in Command, Ideas, Ask, the Assistant |
| System dictation and an always-on wake word | the offline fallback; the wake word listens for a trigger phrase |
| Speaker verification | gates the wake word to your voice |
| Read aloud | Today, Briefing, Daily Paper |
| Phone address book import | Contacts |
| Screen wake-lock | Recipes while cooking |
| PDF export | Daily Paper |
| Share sheet, deep links, app shortcuts | throughout |

**Where data lives.** Records are JSON per module in app storage, synced per key through
Supabase when signed in. Photos, PDFs and ebooks live in a local blob store that is never
uploaded and never included in a backup export. AI runs on your own key and is always
given your records — and the current date and time — as context.


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

# The modules

Field names are the real ones the app stores. **On the phone** lists the hardware each
module actually reaches for.

## Operations
*Run your day.* — 9 modules

### ◈  Operations
A full-screen graphical hub for the day: the module's artwork is the interface, with
tap regions mapped onto it that open Today, Tasks, Daily Paper, Command, Briefing and
Notifications, plus the quick-capture controls — dictation, a quick note, the camera
and a barcode scan. Runs immersive, so it takes the whole display.

- **HitRect** — `id`, `x`, `y`, `width`, `height`
- On the phone: camera, barcode/QR scanning, immersive full-screen, dictation.

### 🗓  Calendar
Every dated record in the app on one agenda — tasks and assignments due, bills, document
expiries, milestones, birthdays and time capsules coming unsealed. A month at a time, with
the picked day's items listed underneath; tapping one opens the module it came from.

It owns the dated-items query the rest of the app reads: Today is that query for today's
range, so a new dated module surfaces everywhere by being added here once.

- **DatedItem** — `key`, `icon`, `title`, `date`, `time`, `moduleId`, `kind`, `note`, `done`

### 🗓  Today
The landing page. Everything today asks of you, gathered from every other module: what's
overdue, what's due, which habits are unchecked, what happened on this date in past
years, and one thing you once said you wanted to get to.

- **DueLine** — `icon`, `title`, `meta`, `moduleId`, `urgent`
- **Pick** — `kind`, `title`, `moduleId`
- **ParsedCmd** — `type`, `title`, `due`, `amount`
- Uses: AI, Weather
- On the phone: dictation, read aloud.

### 📰  Daily Paper
A newspaper about your own life, written each morning. An AI editorial grounded strictly
in the day's real facts, a docket of what's coming, the weather, and a re-rollable
suggestion.

- **Docket** — `kind`, `title`, `date`, `overdue`, `key`
- **OnThisDay** — `kind`, `title`, `year`
- **TaskLite** — `id`, `title`, `due`, `done`
- Uses: AI, Telegram, Weather
- On the phone: PDF export.

### ✅  Tasks
The working list. Four states (not started / in progress / waiting / done),
priority, any due date, projects, tags, repeats that spawn the next occurrence when you
complete one, snoozing, subtask checklists, and a board view you can drag cards across.

- **Subtask** — `id`, `text`, `done`
- **Task** — `id`, `title`, `status`, `priority`, `due`, `projectId`, `tags`, `notes`, `waitingOn`, `subtasks`, `recur`, `snoozedUntil`, `completedDate`
- The board is a column per status. Press and hold a card to lift it, drag it over
  another column, let go — the target lights up and the board auto-scrolls near either
  edge. ‹ / › on each card do the same one step at a time.
- The row checkbox *selects* rather than completes, so completing and deleting both act
  on a whole selection from one bar. Completing flips to Reopen when everything picked
  is already done.

### 🗂  Projects
A project is a record, not a word typed into a task. It has dates, a status, notes and
tags, and it gathers what belongs to it: its tasks, plus links to the documents, saved
links, people and milestones already held elsewhere. Progress is the share of its tasks
that are done, and a target date puts it in the Calendar.

- **Project** — `id`, `name`, `description`, `status`, `startDate`, `targetDate`, `completedDate`, `notes`, `tags`, `documentIds`, `linkIds`, `contactIds`, `milestoneIds`
- Deleting a project releases its tasks rather than deleting them.
- Free-text project names on existing tasks turn into records automatically on first run.

### ⌘  Command
One input. Type an instruction in plain language and it proposes a record and a
destination module; you confirm before anything is written.

- **ParsedCmd** — `type`, `title`, `due`, `amount`
- **DueLine** — `icon`, `title`, `meta`, `moduleId`, `urgent`
- **Pick** — `kind`, `title`, `moduleId`
- Uses: AI, Weather
- On the phone: dictation, read aloud.

### 📋  Briefing
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
- On the phone: notifications, pinned notification, read aloud, scheduled alarms.

### 🔔  Notifications
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
- On the phone: notifications, pinned notification, read aloud, scheduled alarms.

---

## Archive
*What you keep.* — 7 modules

### 📄  Documents
Anything with an expiry date — IDs, policies, warranties. Import a scan and AI reads the
title, issuer, number and expiry out of it.

- **Document** — `id`, `title`, `category`, `issuer`, `policyNumber`, `expiryDate`, `transcription`, `notes`, `linkedContact`, `photoBlob`
- Uses: AI
- On the phone: camera, gallery.

### 🔗  Links
Saved videos and articles, with cached thumbnails, tags and read state.

- **Link** — `id`, `url`, `type`, `title`, `tags`, `status`, `shareWith`, `videoId`, `thumbBlob`
- On the phone: share sheet.

### 📚  Books
The reading list, the shelf, and a real in-app reader. A book holds any number of
readable files, each remembering its own place.

- **ReadLog** — `id`, `date`, `pagesRead`
- **BookFile** — `id`, `name`, `kind`, `blobId`, `frac`, `page`
- **Book** — `id`, `title`, `author`, `genre`, `status`, `totalPages`, `currentPage`, `startedDate`, `finishedDate`, `rating`, `notes`, `logs`, `photoBlob`, `readFrac`
- On the phone: barcode scanning, camera, ebook import, file picker, gallery, open in another app.

### 🖼  Photos
Albums of captioned photos.

- **Caption** — `id`, `text`, `note`, `blob`
- **Album** — `id`, `name`, `description`, `captions`
- On the phone: camera, gallery.

### 🗂  Collections
Things you collect, with per-item acquired dates, tags and notes.

- **CollItem** — `id`, `name`, `acquiredDate`, `tags`, `notes`
- **Collection** — `id`, `name`, `description`, `items`, `photoBlob`
- On the phone: camera, gallery.

### ⏳  Time Capsules
A sealed note to your future self, genuinely hidden until its date.

- **TimeCapsule** — `id`, `title`, `body`, `sealedUntil`, `createdAt`, `photoBlob`
- On the phone: camera, gallery.

### 🏆  Milestones
What's worth remembering, on a timeline — plus a yearly recap that counts your year
across every module and writes a narrative of it.

- **Milestone** — `id`, `title`, `date`, `category`, `notes`, `photoBlob`
- Uses: AI
- On the phone: camera, gallery.

---

## Logistics
*Places, supply and trips.* — 3 modules

### 📍  Places
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
- On the phone: arrival geofence, location.

### 📦  Quartermaster
What you own, where it is, what's running out, and what you've lent. Photograph a shelf
and it catalogues the contents.

- **InventoryItem** — `id`, `name`, `location`, `tags`, `lentTo`, `lentSince`, `photoBlob`, `stockCheckedAt`
- **StockRef** — `id`, `label`, `blob`
- Uses: AI
- On the phone: camera, gallery.

### 🧳  Travel
Trips, and everything hanging off one. Bookings — flights, lodging, rail, cars, tours,
tables — each with its provider, confirmation number, cost, attachments and a link back
to where it was booked, timed to the minute so a 07:25 departure reads as one. Plus the
packing lists (absorbed from their own module), a budget of estimated against booked and
paid, the people coming, and the documents that have to still be valid when you get back.

Travel documents link to Documents records rather than copying them, so an expiry date
has one owner; the trip flags anything lapsing before it ends. A Places & photos tab links
a Photos album to the trip, suggests want-to-go places and bucket-list entries matching the
destinations, and lists places you actually logged between the dates. Trips and bookings
appear on the Calendar automatically.

Once the end date has passed a Recap tab appears: days away, photos in the linked album,
places visited inside the range, bookings by kind, and spend against budget — every
figure counted from records that already exist. With an AI key it will write the trip up
from exactly those numbers and nothing else.

Foreign-currency bookings convert through the Tools rates, but the native amounts are kept
and shown alongside — a rate is today's and a booking was paid at the rate on the day, so
the converted total is labelled an estimate rather than merged in silently.

- **Trip** — `id`, `name`, `destinations`, `startDate`, `endDate`, `statusOverride`, `notes`, `coverPhotoBlob`, `travelerIds`, `documentIds`, `budgetEstimate`, `currency`, `albumId`
- **PackTemplate** — `id`, `name`, `groups` (saved from any list)
- **Reservation** — `id`, `tripId`, `type`, `provider`, `confirmationNumber`, `status`, `startDateTime`, `endDateTime`, `location`, `lat`, `lng`, `placeId`, `contactId`, `externalLink`, `cost`, `currency`, `paid`, `attachments`, `notes`
- **PackItem** — `id`, `name`, `category`, `packed`
- **PackingList** — `id`, `name`, `tripDate`, `items`, `tripId`

---

## Discovery
*Learning and curiosity.* — 5 modules

### 🎓  Education
Semesters, courses and assignments — with grades, GPA, time spent, and a pacing plan: a
target, dated checkpoints, and logs of what you actually did against it.

- **KeyDate** — `label`, `date`
- **Checkpoint** — `date`, `targetByThen`
- **ProgressLog** — `id`, `date`, `unitsAdded`
- **Semester** — `id`, `name`, `startDate`, `endDate`
- **Course** — `id`, `semesterId`, `name`, `credits`, `grade`, `readingListTag`, `notes`, `keyDates`
- **Assignment** — `id`, `courseId`, `title`, `dueDate`, `status`, `percentComplete`, `timeSpentMinutes`, `grade`, `pacingTarget`, `pacingUnit`, `paceCheckpoints`, `progressLogs`

### 🌳  Skill Trees
Two tiers, two vocabularies, deliberately walled apart.

**Standings** count what you have been doing and advance in *ranks*. Each one is a record
now rather than a hardcoded branch: name it, pick which countable events feed it — tasks
completed, habit check-ins, recipes cooked, places visited, practice hours — and what each
is worth. Executor, Discipline and Scholar ship preconfigured at the weights they always
used, and all three can be renamed, reweighted or deleted. A standing cannot show that you
got better at anything, only that you did more; the module says so.

**Skills** are what you declared you are learning, and they move in *levels*. A level only
changes when a benchmark you wrote is met — "play the F barre chord cleanly at 80bpm", not
a progress bar. Each skill carries its own level scale (A1→C2, belts, grades), sub-skills
that branch off it, practice sessions with a focus and a self-rated quality, and a decay
rung salvaged from the removed Recall module: practise and the skill holds longer, leave it
and it goes cold. A paused skill is exempt.

Practice hours may feed a standing. A standing may never feed a skill's level. Linked
habits and courses contribute real hours, and a day that already has a session logged
ignores the habit check-in rather than counting it twice.

- **Standing** — `id`, `name`, `icon`, `blurb`, `sources`, `rankNames`
- **SourceWeight** — `kind`, `xp`
- **Skill** — `id`, `name`, `domain`, `parentId`, `startedDate`, `notes`, `photoBlob`, `currentLevel`, `levelScale`, `targetLevel`, `targetDate`, `active`, `habitNames`, `courseIds`, `bookIds`, `minutesPerCheckin`
- **PracticeLog** — `id`, `skillId`, `date`, `minutes`, `focus`, `quality`, `notes`, `attachments`
- **Benchmark** — `id`, `skillId`, `label`, `targetLevel`, `achieved`, `achievedDate`

### 💡  Ideas
Fast capture, tagged and searchable, promotable to a task.

- **Idea** — `id`, `text`, `tags`, `archived`, `created`
- On the phone: dictation.

### 🕳  Rabbit Holes
What you went down a hole researching — running notes and links, active or resolved.
An open thread you haven't touched in three weeks is marked cold on its row and surfaces
in the Briefing with a one-tap Resolve, because an abandoned thread should be closed
rather than nagged about forever.

- **HoleLink** — `id`, `url`, `title`
- **RabbitHole** — `id`, `topic`, `notes`, `links`, `status`, `startedDate`, `touchedDate`, `photoBlob`
- `touchedDate` is stamped on every edit; it's what "gone cold" is measured against.
  Records written before it existed fall back to their start date.
- On the phone: camera, gallery, share sheet.

### 📊  The Almanac
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
- On the phone: notifications, pinned notification, read aloud, scheduled alarms.

---

## Management
*Body, home and money.* — 4 modules

### 🔥  Habits
Streaks that reset honestly. A day is checked in or it isn't, and missing one resets;
checking in twice can't inflate it, and a mis-tap today can be undone. Habits you
haven't checked in yet appear on Today, and one with a live streak surfaces in the
Briefing.

- **Habit** — `name`, `checkins`, `notes`
- Starts empty. No seeded example habits — an empty list says so rather than shipping
  two placeholders that look like records you chose.

### ❤  Health
Daily log, workouts with pace maths, metric trends, and imports from Garmin and Apple
Health exports.

- **Reading** — `id`, `metric`, `value`, `unit`, `date`
- **DailyLog** — `date`, `workoutType`, `workoutMinutes`, `waterOz`, `weightLb`, `notes`
- **Workout** — `id`, `date`, `type`, `minutes`, `distance`, `distanceUnit`, `notes`
- On the phone: large-file import.

### 🍳  Recipes
Cook from them, rescale every quantity live to the servings you want, and log each time
you cooked it.

- **GroceryTotal** — `name`, `unit`, `qty`, `exact`, `raw`
- **Ingredient** — `id`, `name`, `qty`, `unit`
- **Step** — `id`, `text`
- **CookLog** — `id`, `date`, `notes`
- **Recipe** — `id`, `title`, `baseServings`, `tags`, `notes`, `ingredients`, `steps`, `cookLogs`, `photoBlob`
- On the phone: camera, gallery, screen wake-lock.

### 💵  Finance
Ledger, bills with payment history, subscriptions, and holdings priced live. Photograph
a receipt and it reads it.

- **Entry** — `id`, `desc`, `amount`, `category`, `recurring`, `date`, `photoBlob`
- **Payment** — `date`, `amount`
- **Bill** — `id`, `name`, `amount`, `dueDate`, `cadence`, `autopay`, `remindDays`, `category`, `paymentHistory`, `contact`, `attachments`
- **Subscription** — `id`, `name`, `amount`, `cycle`, `active`, `category`, `renewalDate`
- Uses: AI, Markets
- On the phone: camera, gallery, scheduled alarms.

---

## Intelligence
*The app thinking about you.* — 5 modules

### 🔎  Ask
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
- On the phone: notifications, pinned notification, read aloud, scheduled alarms.

### 🤖  AI Assistant
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
- On the phone: notifications, pinned notification, read aloud, scheduled alarms.

### 🕸  Knowledge Graph
Labelled links between any two records in the app, resolved live so a renamed record
doesn't leave a stale label.

- **Node** — `source`, `label`
- **Edge** — `aSource`, `aLabel`, `bSource`, `bLabel`
- Uses: AI

### 🌀  Entropy
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
- On the phone: notifications, pinned notification, read aloud, scheduled alarms.

### ⏰  Time Machine
What the app knew on a past day, replayed off the mutation log: what changed and in
which direction, what was deleted, how much of today's record existed then, and what you
actually did. A Compare tab diffs one module between any two days field by field. Any
record can be put back to how it read on a chosen day, and that rewind is itself a normal
edit rather than a silent rewrite.

Replay is exact from the log's first event onwards, and the screen says where that line
falls. Before it, only existence is known — the old approximation, kept and labelled
rather than dressed up.

- **Entry** — `seq`, `at`, `key`, `coll`, `rec`, `label`, `kind`, `fields`, `remote`, `reversible`
- **FieldDiff** — `field`, `before`, `after`
- **RecordDiff** — `key`, `coll`, `rec`, `label`, `kind`, `fields`
- **Horizon** — `from`, `events`
- **Stub** — `key`, `title`
- **StoreSnapshot** — `label`, `stubs`
- **Births** — `seeded`, `born`

---

## People
*Others, and other devices.* — 3 modules

### 👤  Contacts
People and everything you know about them, linked to the places and documents they
appear in.

- **Contact** — `id`, `name`, `emails`, `company`, `title`, `relationship`, `address`, `birthday`, `tags`, `notes`, `photoBlob`
- On the phone: camera, gallery, phone address book.

### 🤝  Sharebox
A shared feed between you and someone else, over your own account, arriving live without
a refresh.

- **ShareItem** — `id`, `kind`, `url`, `title`, `body`, `urgency`, `postedBy`, `createdAt`
- **SpaceRow** — `id`, `name`
- **ItemRow** — `id`, `kind`, `url`, `title`, `body`, `urgency`
- Uses: account, file transfer, shared spaces
- On the phone: file picker, open in another app, share sheet.

### 🔳  QR Sync
Pair another device by scanning a code. Phone-only — the desktop build omits it.

- **UnitDef** — `name`, `toBase`
- **QrMatrix** — `size`, `modules`
- **ScanProposal** — `kind`, `title`, `items`, `text`, `summary`, `fields`, `photoB64`, `suggested`
- Uses: AI, Currency, Markets, Weather, account
- On the phone: QR scanning, camera, code scanning.

---

## System
*Running the app.* — 5 modules

### 🔍  Search
One box across every record in the app, grouped by module.

- **StatusItem** — `text`, `status`
- **NoteItem** — `title`, `note`
- On the phone: read aloud.

### 🧰  Tools
Currency across ~160 currencies with live rates, time zones, unit conversion, weather,
and market prices.

- **UnitDef** — `name`, `toBase`
- **QrMatrix** — `size`, `modules`
- **ScanProposal** — `kind`, `title`, `items`, `text`, `summary`, `fields`, `photoB64`, `suggested`
- Uses: AI, Currency, Markets, Weather, account
- On the phone: QR scanning, camera, code scanning.

### ⚙  Settings
AI keys and provider, account and sync, Telegram, alert thresholds, the app lock, and
backup.

- Uses: Telegram, TelegramLink, account
- On the phone: arrival geofence, screen wake-lock, share sheet, voice enrolment, wake word.

### ↩  History
Trash and an activity log, both read off one app-wide mutation log. Every save is
diffed against what it replaced, so a deleted record waits 30 days before it is really
gone and a recent edit can be put back field by field.

- **Mutation** — `seq`, `at`, `key`, `coll`, `rec`, `change`, `label`, `before`, `after`, `remote`, `truncated`
- Local to the device: the log is never synced and never enters a backup.

### 🏷  Tags
One vocabulary across the seven modules that carry tags — Tasks, Ideas, Links, Contacts,
Recipes, Quartermaster, Collections. Pick a tag to see everything carrying it whatever
module it lives in; rename or merge one and it changes everywhere at once.

- **TagUse** — `tag`, `count`, `sources`
- **TaggedRecord** — `source`, `moduleId`, `id`, `label`, `tags`
- Derived from the records rather than stored, so it cannot drift out of step with them.

---

# Beyond the modules

- **Automations** (off by default): a habit reaching 7, 30, 100 or 365 days writes its
  own milestone; a document nearing expiry writes a renewal task. Both idempotent.
- **App lock**: an optional PIN gating the whole app at launch. A screen lock, not
  encryption — the data on the device isn't scrambled.
- **Backup**: export every module as one JSON file and import it back, independent of the
  account. Attachments stay on the device.
- **Sharebox**: the one place data leaves the phone, and only by posting to a space you
  joined.

# Where it stands

All 41 modules are built and the build runs on device. Outstanding:

- **Blocked on credentials** — Google Photos import and calendar push; both need an
  OAuth client created in the Google Cloud project.
- **Awaiting artwork** — the Knowledge Graph's radial view. It works as a list today.
- **Open decisions** — whether Notifications adds a shared activity feed, and when the
  animated character gets built.
- **Small gaps** — weather is by named city rather than location.

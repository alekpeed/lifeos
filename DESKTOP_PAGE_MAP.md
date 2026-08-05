# Life OS — Desktop Page Map

**What this is:** every page in the Linux/desktop build, what belongs on each one, and
how you move between them. It defines *structure and flow* — nothing about how it
should look.

**No inherited style.** The phone build has an established visual language; this does
not, and shouldn't borrow it. Colour, type, spacing, iconography, whether it feels
like a terminal or a magazine — all of that is open. Nothing in this document is a
constraint on appearance.

**Same data, different machine.** Both builds read the same records through the same
account, so a task added here appears there. What changes is the shape of the screen
and what the hardware can do.

---

## 1. The window

A desktop window is not a phone screen made bigger. Two facts drive everything below:

- **There is room for more than one thing at a time.** The phone shows a list, then
  replaces it with a detail. Here they sit side by side, and the detail changes as you
  move down the list. Nothing needs a back button to undo a navigation that never
  happened.
- **There is a keyboard and a pointer.** Hover can reveal, right-click can offer,
  typing can jump, files can be dragged in. The phone build can't assume any of that.

**Three regions:**

```
┌──────────┬───────────────────┬──────────────────────────┐
│          │                   │                          │
│   NAV    │      LIST         │        DETAIL            │
│          │                   │                          │
│ domains  │  what's in the    │  the selected record,    │
│ and      │  open module      │  open for editing        │
│ modules  │                   │                          │
│          │                   │                          │
└──────────┴───────────────────┴──────────────────────────┘
     ~1                  ~2                    ~3          (rough proportions)
```

- **Nav** is persistent — it never goes away, so you always know where you are and can
  leave in one click.
- **List** is the open module's contents.
- **Detail** is whatever is selected. When nothing is selected it shows the module's
  summary, or an invitation to create the first record — not a blank void.

**Responsive behaviour:** below roughly 1100 px wide, drop the detail pane and let
selection open it over the list. Below roughly 800 px, collapse nav to icons. Minimum
usable window: about 900 × 640. Panes should be draggable to resize, and remember
their widths.

**Some pages ignore this.** Reading a book, the daily paper, and the assistant
conversation want the width — they take the list and detail area as one surface. Those
are called out per page.

---

## 2. Navigation

```
NAV
├── Search box                     always at the top, focuses on Ctrl+K
├── Today                          pinned — the app opens here
├── OPERATIONS      6 modules
├── ARCHIVE         9
├── LOGISTICS       4
├── DISCOVERY       5
├── MANAGEMENT      4
├── INTELLIGENCE    6
├── PEOPLE          2
├── SYSTEM          3
└── Sync status / account          pinned to the bottom
```

Domains expand in place; modules are one click. Whether groups start expanded or
collapsed is a design call — but the app should remember the choice.

**The flow, in full:**

```
open app ─→ [lock screen, if a PIN is set] ─→ Today
                                                │
                     ┌──────────────────────────┼───────────────────────┐
                     ▼                          ▼                       ▼
              click a module            Ctrl+K → command            click Search
                     │                          │                       │
                     ▼                          ▼                       ▼
             list pane fills           type an instruction        results grouped
                     │                          │                  by module
             select a row                 confirm → it files            │
                     ▼                     into a module ──────────────►│
             detail pane fills                                          │
                     │                                                  ▼
             edit in place, saves as you go ◄─────────────────── click a result
```

There is no "save" button anywhere. Edits persist as they're made, with a quiet
confirmation. This is already how the app behaves and it should not change.

---

## 3. What every page needs

Every module page must define all five of these, or it will feel broken in use:

| State | What it is |
|---|---|
| **Empty** | No records yet. Say what this module is for and offer the one action that starts it. Never an empty box. |
| **Loading** | Only where data comes over the network. Local records open instantly and need nothing. |
| **Populated** | The normal case. |
| **Error** | Something failed — say what and what to do. No silent failures. |
| **Nothing selected** | The detail pane before a row is picked. |

And every page has:

- **A list pane** with its own filter/search, sort, and a count.
- **A toolbar** above the list holding the module's create action and any filters.
- **A detail pane** whose fields are listed per page below.
- **Right-click on a row** offering, at minimum: open, duplicate where it makes sense,
  delete.
- **Multi-select** with click, shift-click and ctrl-click, and a bar that appears when
  a selection exists, offering the actions that work in bulk (usually delete, and one
  module-specific verb like complete or archive).
- **Keyboard**: `↑`/`↓` moves the selection, `Enter` opens, `Delete` deletes with
  confirmation, `Ctrl+N` creates, `Ctrl+F` focuses the list's own search, `Esc` clears
  selection or closes an overlay.

---

## 4. What simply isn't here

This machine has no camera, no barcode scanner, no microphone, no GPS, and no way to
fire an alert when the app is closed. Those features are **absent from this build** —
not disabled, not explained, not replaced with an apology. The button isn't drawn.

| Not here | The page still does |
|---|---|
| Camera capture | Documents, Photos, Places, Quartermaster, Finance, Recipes, Milestones all take a file instead — pick one or drag it in. The AI reading of an image works identically once it has the bytes. |
| Barcode / ISBN scanning | Books: type the ISBN, same lookup. |
| Dictation | Command and Ideas are typed. |
| Speech out | No read-aloud on Today, Briefing, or the Paper. |
| Location and geofencing | Places keeps addresses and coordinates; no "nearby", no arrival alerts. |
| Background alarms | Notifications is a list of what needs attention while the app is open. |
| Keep-awake, phone contact import, PDF export | Gone. Recipes, Contacts and the Paper lose nothing else. |

**Modules worth leaving out entirely:** *QR Sync* exists to scan a code, and this build
can't. It can only display one — which the phone can already do in the other
direction. Cut it unless a use for it turns up; it's three lines to put back.

That leaves **39 modules** on the desktop.

**What this build has that the phone doesn't:** a real file picker, drag-and-drop,
screen capture, a resizable window, and a keyboard.

---

## 5. The pages

Each entry gives the module's purpose, what a record actually holds, what goes in each
pane, and anything the page does that a plain list/detail doesn't cover. Fields listed
are the real ones the app stores.

### Operations

**Today** — *the landing page: what today asks of you.*
- One wide surface, no split.
- Sections: **overdue**, **due today**, **also due** (the next stretch), **habits**
  with a check-in per row, **on this day** from previous years, and **surprise me** —
  one thing pulled from want-to-go places, bucket goals, unread books, untried recipes
  and ideas, with a link straight to it.
- Every row acts in place: complete, check in, jump. Nothing here navigates away to do
  its job.
- Empty: "Clear. Nothing due today."

**Daily Paper** — *a newspaper generated from your own life.*
- Full width, read top to bottom.
- Sections in order: **the sky today** (weather), **editorial** (AI-written from the
  day's real facts), **editor's pick** with a re-roll, **on the docket** (tasks,
  assignments, bills and document expiries in the next stretch), **today's habits**,
  **on this day**, **almanac notes**.
- Actions: write today's edition, share, send to Telegram.
- The editorial is generated once a day and kept; it shouldn't rewrite itself on every
  visit.

**Tasks** — *the working list.*
- Record: title, status (not started / in progress / waiting / done), priority (low →
  urgent), due date, project, tags, notes, waiting-on, subtask checklist, repeat rule,
  snooze-until, completed date.
- List: checkbox, title, due, project, priority. Views: **list** and **board** (board
  replaces the list pane with status columns and drag between them).
- Filters: hide done, hide snoozed, by project.
- Detail: every field above, with the checklist inline.
- Bulk: complete or delete a selection. Completing a repeating task spawns its next.

**Command** — *type an instruction, it files itself.*
- One input, wide. Type "pay water bill friday $80" and it proposes a record and a
  destination module.
- The proposal is shown and editable **before** anything is written. Confirm creates.
- History of what it filed, so a wrong guess can be found and fixed.

**Briefing** — *everything wanting attention, each with one action.*
- One column. Rows carry their own verb: complete, check in, snooze, renew a document
  for a year.
- Sourced from tasks, habits, bills, documents and assignments — this page owns
  nothing itself.
- The point of the page is to be emptied.

**Notifications** — *what's overdue, due soon, or expiring.*
- List: the attention feed — icon, what it is, why it's here, jumping to its record.
- Toolbar: set a reminder with quick offsets (in an hour, this evening, tomorrow).
- Thresholds for "due soon" come from Settings.

### Archive

**Documents** — *anything with an expiry date.*
- Record: title, category, issuer, policy/account number, expiry date, linked contact,
  AI transcription, AI summary, your notes, a photo, and any number of attached files.
- List: title, category, expiry, sorted by what expires first. Filter by category and
  by expiry window.
- Detail: the fields above, the attachment list, and the transcription as an editable
  block.
- Drop a scan or a PDF on the window and the AI fills title, issuer, number and expiry.

**Links** — *saved videos and articles.*
- Record: url, type (video/article), title, tags, read state, share-with, and a cached
  thumbnail for YouTube.
- List: thumbnail, title, host, read state. Split by video and article.
- Detail: title, tags, share-with, open in browser.

**Books** — *the shelf, the reading list, and the reader.*
- Record: title, author, genre, status, total pages, current page, started and finished
  dates, rating, notes, a reading log of dated sessions, and **any number of readable
  files** — each with its own kind (text or PDF) and its own remembered place.
- Views: reading, to read, finished, **shelf** (cover grid), **stats** (pages per
  week, streak, finish rate).
- Detail: the fields, the file list with a Read on each, and the reading log with a
  pages-today entry.
- **Reader** takes the whole window: the text, page turning, type size, contents, and
  a close. Position saves per file, so two files of the same book don't fight.

**Photos** — *albums.*
- Record: album name, description, and captioned photos.
- List: albums with a cover and a count. Detail: photo grid, drag to add, click to
  enlarge, caption in place.

**Museum** — *a hall of what you finished.*
- Six wings: tasks and assignments completed, books read, recipes mastered, projects
  completed, milestones achieved, best habit streaks.
- Read-only, derived from other modules. Counts per wing, list under each.

**Collections** — *things you collect.*
- Record: collection name, description, cover photo, and items — each with name,
  acquired date, tags, notes.
- List: collections with counts. Detail: the items, sortable by acquired date.

**Time Capsules** — *sealed notes to your future self.*
- Record: title, body, sealed-until date, created date, photo.
- List: capsules; sealed ones show a countdown and **not** their contents.
- Detail: the body only once the date has passed. Sealing is the deliberate act — make
  it feel like one.

**Milestones** — *what's worth remembering.*
- Record: title, date, category, notes, photo.
- Views: **timeline**, and **yearly recap** — a stat grid pulled from every module for
  a chosen year (tasks completed, places visited, books finished, pages read, recipes
  cooked, bills paid, habit check-ins, workouts, documents and contacts added, average
  sleep) plus an AI-written narrative of the year.
- The recap is its own layout, not a list.

**Ghost Days** — *this date in previous years.*
- Read-only, grouped by year: milestones, places visited, books started and finished,
  recipes cooked, workouts, completions, birthdays.

### Logistics

**Places** — *where you've been and where you want to go.*
- Record: name, category, list (visited / want to go), address, latitude, longitude,
  rating, revisit flag, visit dates, linked contacts, notes, private notes-to-self, a
  photo grid, files.
- Views: visited, want to go, **map**, **bucket list** (goals with target dates and a
  done state).
- Map replaces both panes; a pin selects its place.

**Orrery** — *your life as an orbital system.*
- One surface. Modules are bodies; how long since you touched one sets its orbit —
  inner, outer, or outer dark.
- Read-only. The diagram is the page.

**Quartermaster** — *what you own, where it is, what's running out.*
- Record: name, location, tags, photo, stock status (full / OK / low / out) with the
  date it was checked, and lent-to plus lent-since.
- List: item, location, stock pill. Filters: all, low, out, lent.
- Detail: the fields, the photo, and a stock check. Photo-cataloguing a shelf produces
  many items at once from one image.

**Packing Lists** — *one checklist per trip.*
- Record: trip name, trip date, and items with a category and packed state.
- Four starting templates: weekend, beach, ski, international.
- Detail: the checklist grouped by category with a packed count.

### Discovery

**Education** — *courses, assignments, grades, and study pacing.*
- Three levels: **semester** (name, start, end) → **course** → **assignment**.
- Course: name, credits, letter grade, reading-list tag, notes, and key dates
  (label + date).
- Assignment: title, due date, status, percent complete, time spent in minutes, grade
  out of 100, and a **pacing plan** — a target number of pages or words, checkpoints
  ("by this date, this many"), and dated progress logs of what you actually did.
- List: assignments for the selected course, due-date ordered, with pace state.
- Detail: the assignment with its pacing chart — planned line against logged progress,
  which is the whole point of the module and needs the width a desktop has.
- **GPA & time** view: GPA from credits and letter grades, time spent per course, and
  the reading list drawn from links carrying the course's tag.
- Nothing else in the app has three levels — the nav should let you sit at a semester
  and see everything under it.

**Skill Trees** — *your real activity, as levels.*
- Three branches — scholar, executor, discipline — each with skills that level from
  what you actually did elsewhere (books finished, tasks completed, habit check-ins).
- Read-only. Level, progress to next, and what feeds it.

**Ideas** — *fast capture.*
- Record: text, tags, archived flag, created date.
- List: ideas newest first, tag chips as filters.
- Detail: text, tags, **promote to task**, archive.
- The capture field should be reachable with one key from anywhere.

**Rabbit Holes** — *what you went down a hole researching.*
- Record: topic, running notes, links (url + title), status (active / resolved),
  started date, photo.
- List: holes with their link counts. Detail: notes as a growing document, links
  beneath.

**The Almanac** — *patterns in your own data.*
- Three sections: **correlations** (sleep against mood, spending against day of week),
  **forecasts** (where a trend is heading), **what if** (change one input, see the
  projection).
- Read-only, computed. Each finding is a sentence with the numbers behind it.

### Management

**Habits** — *streaks that reset honestly.*
- Record: name, notes, and the set of days checked in.
- List: habit, current streak, a recent-days strip, and a check-in control.
- Detail: full history, all-time count, longest streak, undo today's check-in.
- A day is either checked in or not — no partial credit, and missing one resets.

**Health** — *daily log, workouts, metrics, imports.*
- Daily log: date, sleep hours, workout type and minutes, water, weight, notes.
- Workout: date, type, minutes, distance and unit, notes — with pace computed.
- Metric readings: any named metric, value, unit, date.
- Views: **daily**, **workouts**, **metrics** (trend charts per metric), **import**
  (Garmin CSV, Apple Health export — both by file).

**Recipes** — *cook from them, scale them, log them.*
- Record: title, base servings, tags, notes, photo, ingredients (name, quantity,
  unit), steps, and a cook log.
- Views: **recipes**, **grocery list** (assembled from chosen recipes).
- Detail: a servings control that rescales every quantity live, steps big enough to
  read from a distance, and "cooked it" to log a session.

**Finance** — *ledger, bills, subscriptions, holdings.*
- Ledger entry: description, amount, category, recurring flag, date, receipt image.
- Bill: name, amount, due date, cadence, autopay, remind-days, category, contact,
  attachments, and a payment history.
- Subscription: name, amount, cycle, active, category, renewal date, notes.
- Holdings: what you hold, priced live.
- Totals stay visible, not buried under a scroll. Import a CSV; drop a receipt to have
  it read.

### Intelligence

**Ask** — *a question about your own data.*
- Two modes: **answer** (AI, grounded in your records, with the sources it used) and
  **find in memory** (semantic search, no AI).
- Question at the top, answer below, the records it drew on under that — each one
  clickable.

**AI Assistant** — *a real conversation, grounded in your records.*
- Full width. Named conversations down one side, thread in the middle, composer at the
  bottom. Enter sends, Shift+Enter newlines.
- Conversations can be renamed and deleted. Each carries its own history.
- It's given the current date and time, so anything time-dependent is answerable.

**Knowledge Graph** — *links between records across modules.*
- Record: a node is any record; an edge joins two with a label.
- Search to find something, search again to find what to link it to, then link.
- The graph is the page; selecting a node shows its connections, each clickable to its
  real record.

**Recall** — *spaced repetition over facts you kept.*
- Record: the fact, its interval (1 → 3 → 7 → 14 → 30 → 90 days), and next-due date.
- One card at a time, centred: the fact, then the review verbs that set the next
  interval. Not a list page.
- Shows how many are due.

**Entropy** — *what you've been neglecting.*
- A row per module: how long since you touched it, as a decay measure, worst first.
- One overall figure at the top. Read-only, and clicking a row goes there.

**Time Machine** — *what the app knew on a past day.*
- A scrubber across the full width, from your earliest record to today, with a date
  field and prev/next.
- Below: **then vs now** — a count per store with how many existed by that day;
  **added that day** — the records that arrived; **lived that day** — the dated
  activity (check-ins, workouts, cooking, reading, completions).
- States plainly what it can't know: deleted records, edited titles, and anything from
  before it started tracking arrivals.

### People

**Contacts** — *people, and what you know about them.*
- Record: name, phones, emails, company, title, relationship, address, birthday, tags,
  notes, photo.
- List: name, company, tags, with search across every field.
- Detail: all fields, and the places and documents linked to them.

**Sharebox** — *a shared feed with someone else.*
- Record: kind (link / note / file), title, body, url or stored file, urgency, who
  posted it, when.
- List: the feed newest first, with who sent it. Composer for each kind.
- Drag a file onto the window to share it. New items arrive on their own.
- Space membership: create a space, invite by id, see who's in it.

### System

**Search** — *one box across everything.*
- Results grouped by module with counts, keyboard navigable, Enter opens the record
  where it lives.

**Tools** — *the small utilities.*
- Currency conversion over the full table with type-ahead by code, name or country,
  live rates, and a hand-set override.
- Time zones with a searchable list, unit conversion, weather for a named city, and
  market prices for a watchlist.
- A grid of independent tools; nothing needs a detail pane.

**Settings** — *keys, sync, integrations, lock, backup.*
- Sections: **AI** (provider, key, model), **Telegram** (bot token, chat id, two-way
  linking), **sync** (account sign-in, status), **alerts** (bill due-soon days,
  document expiry days), **app lock** (PIN), **backup** (export and import).
- Its own layout: section list on one side, that section's fields on the other.

---

## 6. Things that cross pages

The module list isn't the whole app. These behaviours run between pages and need
designing once, not forty times.

**Import and read** — the desktop replacement for the phone's camera scan.
Drop a file on the window, or pick one, and the AI reads it. What comes back is a
*proposal*, not a saved record: what kind of thing it is, a title, an itemized list if
it found one, and a suggested destination. You confirm or redirect it, then it's
written. Destinations: tasks, quartermaster, recipes, contacts, books, documents,
ideas.
- A photographed list becomes many tasks, not one.
- A shelf becomes many inventory items.
- A business card becomes a contact; a receipt becomes a ledger entry.
- If it found nothing separable it says so — "saves as one entry" — rather than
  quietly filing a single record with a guessed name.

**Promote** — a record moving up. An idea becomes a task. A want-to-go place becomes a
bucket goal. A link becomes a course's reading. The original stays; the new record
carries the link back.

**Link** — the knowledge graph joins any two records with a labelled edge, and the
join shows on both. Contacts appear on places and documents. Links tagged for a course
appear in Education.

**Automations** — off by default, in Settings. Two rules run at open, and both are
idempotent so they can't double-fire:
1. A habit reaching 7, 30, 100 or 365 days writes itself a milestone.
2. A document nearing expiry writes a renewal task.
Anything that writes on your behalf should say so where the record appears.

**Everything writes to one store.** A task made in Command, by an automation, from a
scan, or by hand is the same record. There is no per-entry-point behaviour.

---

## 7. The global layer

Things that are true regardless of which page is open.

**First run** — no records, no account. The app should be usable before signing in:
local data works standalone, and sync is something you turn on. Don't gate the app
behind an account it doesn't need.

**Account and sync** — sign in with email and password; the same account on the phone
shares the data. Nav shows sync state at the bottom: signed out, synced, syncing, or
failed with a reason. Sync is per-record and last-write-wins; a conflict shouldn't
surface as a dialog.

**Offline** — everything local keeps working: reading, editing, creating. What needs
the network is AI, weather, markets, currency rates, and Sharebox. Each says so in
place rather than hanging.

**Saving** — there is no save button. Edits land as they're made, with one quiet
confirmation in a corner, debounced so typing doesn't produce a stream of them.

**Deleting** — one confirmation naming the thing. Deleting a record with attached
files deletes those too. There is no undo, which is exactly why the confirmation
exists.

**Backup** — export everything as one file, import it back. Independent of sync and of
the account. Attachments are on the machine, not in the export — say so.

**The lock** — if a PIN is set, it gates the whole window at start, before any page
renders. It's a screen lock, not encryption, and the Settings copy should keep saying
so.

**Window** — remembers size, position, pane widths, and the last module open. Closing
quits; there's no tray or background service, which is also why alarms don't exist
here.

**Overlays** — three, over any page:
- **Command palette** (`Ctrl+K`) — jump to a module, a record, or an action. The most
  valuable desktop-only addition.
- **Import proposal** — the confirm step described above.
- **Confirm delete**.

---

## 8. Also in this build, but not part of the map

**The helper window** is the same program launched with one flag: a single button that
asks for help, and a shared feed. It replaces everything above. It exists for someone
who should never see any of this, and it's specified separately.

---

## 9. Where this should differ most from the phone

If the desktop build only does one thing differently, make it these three:

1. **Nothing is more than two clicks away.** The nav is always there; the phone's
   wheel-then-list-then-record path shouldn't survive the port.
2. **The detail pane is always visible.** Editing is a thing you do next to the list,
   not a screen you go to and come back from.
3. **The keyboard is a first-class way to use it.** `Ctrl+K`, arrow keys through a
   list, Enter to open, Esc to back out — a person who never touches the mouse should
   be able to run the whole app.

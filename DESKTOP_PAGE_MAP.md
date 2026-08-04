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
├── PEOPLE          3
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

## 4. What the hardware can't do here

The phone build leans on hardware this machine doesn't have. Every one of these needs
a designed desktop substitute rather than a disabled button:

| Missing | Affects | What replaces it |
|---|---|---|
| Camera | Documents, Books, Places, Photos, Quartermaster, Finance, Recipes, Milestones | Import a file, or drag one in. The AI reading of that image works identically once it has bytes. |
| Barcode / QR scanning | Books (ISBN), QR Sync | Type the ISBN. QR Sync can *show* a code but not read one — pairing runs from the phone. |
| Dictation and mic | Command, Ideas | Typing only. Don't draw a mic button that does nothing. |
| Speech out | Today, Briefing, Daily Paper | No read-aloud control. |
| Location and geofence | Places | Enter coordinates or search an address; no "check nearby", no arrival alerts. |
| Device alarms and notifications | Notifications | Reminders exist as records and show in-app, but nothing fires when the app is closed. Say so plainly on the page. |
| Keep screen awake | Recipes | Nothing needed — a desktop screen doesn't sleep mid-recipe the same way. |
| Phone contact import | Contacts | Manual entry, or import a file. |
| PDF export | Daily Paper | Print, or copy the text out. |

**What the desktop has instead**, which the phone build has no use for: a real file
picker, drag-and-drop, screen capture, a resizable window, and a keyboard.

---

## 5. The pages

Each entry says what the page is for, what's in each pane, and what's unique to it.
Where a page differs from the phone, that's noted.

### Operations

**Today** — *the landing page; what today actually asks of you.*
- Single wide surface, no list/detail split.
- Sections: overdue, due today, also due, today's habits, on this day, and a
  surprise-me pull from everything you meant to get to.
- Every row is actionable in place — complete a task, check in a habit, jump to a
  record. Nothing here should require navigating away to act.
- Empty: "Clear. Nothing due today." — and it should feel like a reward, not a gap.

**Daily Paper** — *a generated front page about your own life.*
- Full width, reads top to bottom. This is the one page that should feel like reading
  rather than operating.
- Sections: weather, the written editorial, editor's pick, the docket, today's habits,
  on this day, almanac notes.
- Actions: write today's edition, re-roll the pick, share, send to Telegram.

**Tasks** — *the working list.*
- List: rows with a checkbox, title, due date, project, priority. Filters for
  list/board, hide-done, snoozed. The board view replaces the list pane with columns.
- Detail: status, priority, due, repeat, project, tags, waiting-on, notes, snooze,
  and a checklist of subtasks.
- Bulk: complete and delete across a selection.
- `Ctrl+N` creates from anywhere, not just this page.

**Command** — *type an instruction, it files itself.*
- One wide input, results below. No panes.
- Flow: type → it proposes a record and a destination → confirm or edit → created.
- The proposal must be visible and editable before anything is written.

**Briefing** — *everything wanting attention, each with one action.*
- Single column. Each row carries its own verb: done, check in, snooze, renew.
- This page exists to be emptied.

**Notifications** — *what's overdue, expiring, or due soon.*
- List: the attention feed, each row jumping to its record.
- Toolbar: set a reminder, with quick offsets.
- Must state, on the page, that reminders only appear while the app is running here.

### Archive

**Documents** — *anything with an expiry date.*
- List: title, category, expiry, with an expiry-window filter.
- Detail: title, category, issuer, policy number, expiry, linked contact, summary,
  transcription, notes, and attachments.
- Import a scan by file or drag-drop; the AI reads it into the fields.

**Links** — *saved videos and articles.*
- List: split by video/article, with a thumbnail per row, read/unread state.
- Detail: title, tags, share-with, and open-in-browser.
- Pasting a URL into the window should offer to save it.

**Books** — *the shelf, the reading list, and the reader.*
- Tabs: reading, to read, finished, shelf, stats. Shelf is a cover grid.
- Detail: title, author, genre, status, pages, dates, rating, notes, the reading log,
  and the file list — a book can hold several readable files, each remembering its own
  place.
- **Reader** takes the whole window: text, page turning, size controls, table of
  contents, and a close. Nothing else on screen.

**Photos** — *albums.*
- List: albums. Detail: a photo grid, drag-and-drop to add, click to enlarge.

**Museum** — *a trophy hall of what you finished.*
- Six wings, each a count and a list. Read-only. No detail pane.

**Collections** — *things you collect.*
- List: collections. Detail: the collection's items, each with acquired date, tags,
  notes.

**Time Capsules** — *sealed notes to your future self.*
- List: capsules, sealed ones showing a countdown, not their contents.
- Detail: title, body, seal date. A sealed capsule's body must be genuinely hidden.

**Milestones** — *the things worth remembering.*
- Tabs: timeline, yearly recap.
- Recap is its own layout: a stat grid across every module for a chosen year, plus a
  written narrative.

**Ghost Days** — *this date in previous years.*
- Single column, grouped by year. Read-only.

### Logistics

**Places** — *where you've been and where you want to go.*
- Tabs: visited, want to go, map, bucket list.
- Detail: name, category, address, coordinates, rating, visit dates, linked people,
  notes, notes-to-self, a photo grid, files.
- Map tab replaces both panes with the map; clicking a pin selects that place.
- No location services here — coordinates are entered or looked up.

**Orrery** — *your life as an orbital system; neglect as drift.*
- One surface, the diagram is the page. Bodies are modules, distance is how long since
  you touched them.

**Quartermaster** — *what you own, where it is, what's running out.*
- List: item, location, stock state, with filters for all/low/out/lent.
- Detail: name, location, tags, photo, stock status, and who has it if lent.

**Packing Lists** — *one checklist per trip.*
- List: trips. Detail: the checklist grouped by category, with templates to start from.

### Discovery

**Education** — *courses, assignments, grades, study time.*
- Tabs: coursework, GPA & time.
- List: courses; detail: the course, its assignments, dates, and grade.

**Skill Trees** — *your real activity as levels.*
- Three branches, each a set of skills with progress. Read-only — it's a mirror of
  what you did elsewhere.

**Ideas** — *fast capture.*
- List: ideas with tag filters. Detail: text, tags, and promote-to-task.
- The create field should be focusable from anywhere with one key.

**Rabbit Holes** — *what you went down a hole researching.*
- List: holes, active or resolved. Detail: topic, running notes, links.

**The Almanac** — *patterns found in your own data.*
- Three sections: correlations, forecasts, what-ifs. Read-only, generated.

### Management

**Habits** — *streaks.*
- List: habit, streak, a recent-days strip, and a check-in control per row.
- Detail: name, notes, full history.

**Health** — *daily log, workouts, metrics, imports.*
- Tabs: daily, workouts, metrics, import.
- Metrics is charts; import handles Garmin CSV and Apple Health exports by file.

**Recipes** — *cook from them, scale them, log them.*
- Tabs: recipes, grocery list.
- Detail: ingredients that rescale to a servings control, steps, notes, photo, cook
  log. Cooking view wants a larger type size and the steps in reach.

**Finance** — *ledger, bills, subscriptions, holdings.*
- Tabs across those four; each is list + detail.
- Totals are always visible, not buried at the bottom of a scroll.
- Import a CSV; import a receipt as a file for the AI to read.

### Intelligence

**Ask** — *a question about your own data.*
- Tabs: answer, find in memory. One surface, question at the top, answer below,
  matching records under that.

**AI Assistant** — *a real conversation, grounded in your records.*
- Full width. Conversation list down one side, thread in the middle, composer pinned
  to the bottom. Enter sends, Shift+Enter is a newline.
- Long answers need room — this is a page that benefits most from the desktop.

**Knowledge Graph** — *links between records across modules.*
- The graph is the page; selecting a node fills a detail panel with its connections.

**Recall** — *spaced repetition over facts you kept.*
- One card at a time, centred, with the review verbs. Not a list-and-detail page.

**Entropy** — *what you've been neglecting.*
- One surface: a row per module with a decay measure, sorted worst first.

**Time Machine** — *what the app knew on a past day.*
- A date scrubber across the top; below it the then-vs-now counts, what was added that
  day, and what was lived that day.
- The scrubber wants the full window width — this page is much better here than on a
  phone.

### People

**Contacts** — *people, and everything you know about them.*
- List: name, company, tags, with search. Detail: all fields plus photo and notes.

**Sharebox** — *a shared feed with someone else.*
- List: the feed. Composer for a link, a note, or a file, with urgency.
- Drag a file onto the window to share it.
- New items arrive on their own; nothing needs refreshing.

**QR Sync** — *pair another device.*
- Shows this device's code, large enough to scan from a phone held in front of the
  monitor. Cannot scan — say so, and point at the phone for that direction.

### System

**Search** — *one box across everything.*
- Results grouped by module with counts, keyboard-navigable, Enter opens.
- This is also what the nav's search box opens into.

**Tools** — *currency, time zones, units, weather, markets.*
- A grid of small tools, each self-contained. Nothing here needs a detail pane.

**Settings** — *keys, sync, integrations, lock, backup.*
- Its own layout: a section list down the side, the section's fields on the right.
  Sections: interface, AI, Telegram, sync, alerts, app lock, backup.
- Long enough that it must not be one endless scroll.

---

## 6. Overlays

Three things appear over whatever page is open, and each needs designing once:

- **Command palette** (`Ctrl+K`) — type to jump to a module, a record, or an action.
  This is the single most valuable desktop-only addition; it's how experienced use
  should feel.
- **Confirm delete** — anything destructive asks once, naming what will go.
- **Lock screen** — a PIN gate before anything renders, if one is set. No way past it,
  and no back.

---

## 7. Also in this build, but not part of the map

**The helper window** is the same program launched with one flag: a single button that
asks for help, and a shared feed. It replaces everything above. It exists for someone
who should never see any of this, and it's specified separately.

---

## 8. Where this should differ most from the phone

If the desktop build only does one thing differently, make it these three:

1. **Nothing is more than two clicks away.** The nav is always there; the phone's
   wheel-then-list-then-record path shouldn't survive the port.
2. **The detail pane is always visible.** Editing is a thing you do next to the list,
   not a screen you go to and come back from.
3. **The keyboard is a first-class way to use it.** `Ctrl+K`, arrow keys through a
   list, Enter to open, Esc to back out — a person who never touches the mouse should
   be able to run the whole app.

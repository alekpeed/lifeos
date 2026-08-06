# Life OS — Page Map & Interface Art Brief (phone)

> This is the **Android** build. The desktop/Ubuntu build is a separate interface with
> no shared visual language — see `DESKTOP_PAGE_MAP.md`.

**What this is:** the complete inventory of screens in Life OS, and what each one has
to contain, written for an image generator that is drawing the interface art.

**What this is not:** a visual style guide. What the app looks like is Alek's call —
this document only says *what must be on each page* and *which parts have to be
traceable as tap targets*, so that generated art can actually be wired up to a
working app instead of being a picture of one.

**How to use it:** paste **§1 Standing rules** with every prompt, then one page block
from **§6**. One image per page. The rules are what keep 40 pages looking like one
application; the page block is what makes each one correct.

---

## 1. Standing rules — paste these with every prompt

**Canvas**

- 852 × 1846 px, portrait. Export PNG, no transparency needed, one file per page.
- This is the same canvas as the existing home screen (`nexus-home.png`), so every
  page shares one coordinate space.
- The whole image is fitted onto the phone screen — nothing is ever cropped. Margins
  are for the eye, not for safety: keep important content about 110 px clear of the
  top edge and 140 px clear of the bottom edge, so it doesn't crowd the camera cutout
  or the phone's home-swipe bar.

**Palette** (these are the colors the running app already draws with — matching them
is what makes the art and the live text look like the same program)

| Role | Hex |
|---|---|
| Background / ink | `#07080C` |
| Panel / raised surface | `#141821` |
| Primary text | `#EDEFF2` |
| Secondary text | `#8D95A1` |
| Accent | `#E0708F` |
| Accent, bright (glow, active state) | `#FFC2D6` |
| Hairline / divider | white at 10% |

**The rule that matters most: do not draw fake content.**

Almost every page is mostly *live data* — a list of tasks, a feed of items, a chart —
and the app draws that at runtime, over the art. The number of rows is unknown and
changes constantly.

So: where a page shows a list, a feed, a grid, a chart, or a count, **draw the empty
container only** — a panel, a frame, a subtle boundary — and leave the inside flat and
empty. No sample rows. No placeholder names. No dummy numbers. No lorem ipsum. An
elegant empty panel is correct; a beautiful list of invented tasks is unusable,
because the app will draw the real ones on top of it.

The page blocks below name these explicitly as **LIVE:** areas.

**Text**

- Labels that never change — page titles, section headings, button words, tab names —
  should be **drawn into the art**, and they're listed per page below.
- Values that change — counts, dates, times, names — must be **left as empty space**
  with room for the app to print them. Those are listed as **SLOT:** below.

**Tap targets**

- Every interactive element needs to be a visually distinct, closed shape — a button,
  a chip, a panel with a clear edge — so it can be traced as a region.
- One tappable thing per shape. Two buttons sharing one continuous shape can't be
  told apart by a tap.
- Keep tappable shapes at least 90 px on their short side at this canvas size.
- Don't let tap targets overlap each other.

---

## 2. Screen anatomy — the same on every page

```
   y=0    ┌──────────────────────────────────┐
          │  ← back        PAGE TITLE        │   nav band  (identical on every page)
   y=190  ├──────────────────────────────────┤
          │  [ tab ] [ tab ] [ tab ]         │   tabs, only on pages that have them
   y=330  ├──────────────────────────────────┤
          │                                  │
          │        LIVE region               │   the app draws real content here
          │        (empty panel in the art)  │
          │                                  │
   y=1560 ├──────────────────────────────────┤
          │      [ primary action button ]   │   the page's main verb
   y=1846 └──────────────────────────────────┘
```

Two things must be **identical across all 40 pages**, because they get mapped once and
reused:

1. **The back control** — top-left, centered at roughly x=90 y=115, about 100 px
   across. Same shape, same place, every page.
2. **The title band** — the page's name, in the same position and size on every page.
   The names are given per page in §6.

Everything between those two bands is the page's own business.

---

## 3. Naming, so the art can be wired up

After the art exists, Alek traces the shapes in Figma and exports an SVG. Name the
traced shapes with these prefixes — the names become the app's region ids:

| Prefix | Means | Example |
|---|---|---|
| `nav-` | navigation | `nav-back` |
| `tab-` | switches the view within the page | `tab-list`, `tab-board` |
| `btn-` | performs an action | `btn-add`, `btn-scan` |
| `slot-` | empty box where the app prints a changing value | `slot-count`, `slot-date` |
| `live-` | empty panel where the app draws a list, feed, grid or chart | `live-tasks` |

Shapes can be rectangles, ellipses, or traced polygons — all three are supported.

---

## 4. The map

```
Life OS
├── HOME (graphical, already built — see §5)
│
├── OPERATIONS ── run your day
│   ├── Today            ├── Daily Paper      ├── Tasks
│   └── Command          ├── Briefing         └── Notifications
│
├── ARCHIVE ── what you keep
│   ├── Documents  ├── Links      ├── Books      ├── Photos     ├── Museum
│   └── Collections ├── Time Capsules ├── Milestones └── Ghost Days
│
├── LOGISTICS ── places, supply & trips
│   ├── Places  ├── Orrery  ├── Quartermaster  └── Packing Lists
│
├── DISCOVERY ── learning & curiosity
│   ├── Education  ├── Skill Trees  ├── Ideas  ├── Rabbit Holes  └── The Almanac
│
├── MANAGEMENT ── body, home & money
│   ├── Habits  ├── Health  ├── Recipes  └── Finance
│
├── INTELLIGENCE ── the app thinking about you
│   ├── Ask  ├── AI Assistant  ├── Knowledge Graph
│   └── Recall  ├── Entropy  └── Time Machine
│
├── PEOPLE ── others & devices
│   ├── Contacts  ├── Sharebox  └── QR Sync
│
└── SYSTEM ── running the OS
    ├── Search  ├── Tools  └── Settings

Overlays that appear over any page (§7): Scan result · Domain list · Lock screen
```

**40 module pages + 1 home + 3 overlays.**

---

## 5. The home screen — the worked example

The home is already built and is the reference for everything else. It shows the
established pattern: full-screen artwork, eight traced petals around a core, a status
row with empty slots for live values, and an action bar along the bottom.

Its real regions, in canvas coordinates:

| Region | Shape | Purpose |
|---|---|---|
| 8 wheel petals | traced polygons | open a domain |
| `slot-clock` | 359,17 · 138×35 | app prints the time |
| `slot-date` | 359,52 · 138×35 | app prints the date |
| `bell` | 657,17 · 49×51 | notifications |
| `btn-voice` `btn-note` `btn-scandoc` `btn-ai` | 4 boxes at y≈1613, 117×111 | bottom action bar |
| `btn-scan-center` | ellipse at 423,1652 r≈64 | the camera |
| `core` | ellipse at 426,697 r≈150 | centre — reserved |

Note what the artwork does *not* contain: no clock digits, no date text, no
notification count. Those are empty spaces the app fills. Every page should follow
the same discipline.

---

## 6. The pages

Each block is one prompt. Format is the same throughout:

- **Purpose** — one line, so the art reads as the right kind of page.
- **Tabs** — the switcher across the top, if the page has one. Draw the words.
- **Static** — furniture to draw: headings, button labels, fixed structure.
- **LIVE** — panels to leave empty for the app to draw into.
- **SLOT** — small empty boxes where the app prints a changing value.
- **Empty state** — the line shown when there's nothing yet. Leave room for it.

---

### OPERATIONS

#### `today` — Today 🗓
- **Purpose:** the one screen that answers "what am I doing today".
- **Static:** section headings `OVERDUE`, `DUE TODAY`, `ALSO DUE`, `HABITS`,
  `ON THIS DAY`; a `🎲 Surprise me` button; a `Go →` link shape.
- **LIVE:** a task list under each of the three due headings; a habit strip with a
  `Check in` button per habit; an on-this-day list; the surprise result line.
- **Empty state:** "Clear. Nothing due today." / "All habits checked in."

#### `daily-paper` — Daily Paper 📰
- **Purpose:** a newspaper front page generated from your own life.
- **Static:** masthead; section headings `The Sky Today`, `Editorial`,
  `Editor's Pick`, `On the Docket`, `Today's Habits`, `On This Day`, `The Almanac`;
  buttons `Write today's edition`, `Another`, `Share`, `Send to Telegram`, `Export PDF`.
- **LIVE:** every section body — the editorial paragraph, the docket list, the habit
  list, the on-this-day list. All empty panels.
- **SLOT:** the date under the masthead.
- **Empty state:** "The docket is clear for the next seven days."

#### `tasks` — Tasks ✅
- **Purpose:** the working task list.
- **Tabs:** `List` · `Board`; plus filter chips `Hide done`, `Snoozed`, `All`.
- **Static:** a full-width `+ Add task` button; a selection bar holding a trash icon,
  a check icon, `Select all` and `Clear` — this bar appears over the list when rows
  are picked, so draw it as its own strip.
- **LIVE:** the task list (each row is a checkbox, a title, and small chips), and the
  board columns.
- **Empty state:** "Nothing here yet."

#### `command` — Command ⌘
- **Purpose:** type or speak one line and the app files it in the right module.
- **Static:** a large single-line input with placeholder "Type anything…"; a mic
  button; quick buttons for common actions; a confirm strip with `Create` / `Cancel`.
- **LIVE:** the parsed-result card that appears under the input before you confirm.

#### `briefing` — Briefing 📋
- **Purpose:** everything wanting attention, each with a one-tap action.
- **Static:** heading `ALSO WAITING`; a read-aloud button.
- **LIVE:** the briefing list — each row is a line of text plus one or two small
  action buttons (`Done`, `Check in`, `Snooze`, `Renew +1y`).

#### `notifications` — Notifications 🔔
- **Purpose:** what's overdue, due soon, expiring — plus setting a reminder.
- **Static:** an input with placeholder "Remind me to…"; quick chips
  `In 1h`, `This evening`, `Tomorrow AM`; a `Set reminder` button.
- **LIVE:** the attention feed — icon, title, a short "why" line, tappable to jump.

---

### ARCHIVE

#### `documents` — Documents 📄
- **Purpose:** IDs, policies, warranties — anything with an expiry date.
- **Tabs:** category chips, `All` first; an expiry-window chip row (`7d` `30d` `90d`).
- **Static:** `New document` input + `Add`; in the open editor, field labels
  `Title`, `Category`, `Issuer`, `Policy / account #`, `Expiry date`,
  `Linked contact`, `Summary`, `Transcription`, `Notes`, `Photo`, `Files`;
  a `📷 Scan document` button.
- **LIVE:** the document list, and the attachment list inside an open document.

#### `links` — Links 🔗
- **Purpose:** saved videos and articles.
- **Tabs:** `YouTube` · `Articles`.
- **Static:** paste-a-URL input + `Add`; per-row `↗ Open` and a read/unread toggle;
  editor labels `Title`, `Tags (comma separated)`, `Share with`.
- **LIVE:** the link list — each row has room for a thumbnail image on the left.

#### `books` — Books 📚
- **Purpose:** the reading list, the shelf, and the in-app reader.
- **Tabs:** `Reading` · `To read` · `Finished` · `Shelf` · `Stats`.
- **Static:** `New book` input + `Add`; a `📷 Scan ISBN` button; editor labels
  `Title`, `Author`, `Genre`, `Status`, `Total pages`, `Current page`, `Notes`,
  `Files`, `Reading log`, `Photo`; file-row buttons `Read`, `📗 Add ebook`,
  `📕 Add PDF`.
- **LIVE:** the book list; the shelf as a cover grid; the stats panel; the file list.
- **Note:** the reader itself is a separate full-screen page — see `books-reader`.

#### `books-reader` — Reader (full screen, no nav band)
- **Purpose:** reading a book, page by page.
- **Static:** a slim top bar with `‹ Close`, the book title, and a page counter;
  bottom controls `A−` `A+`, `Contents`, and prev/next.
- **LIVE:** the entire page body — the app lays out the text. Leave it completely
  empty; this is a reading surface, not a decorated one.

#### `photos` — Photos 🖼
- **Purpose:** albums of pictures.
- **Static:** `New album` input + `Add`; `📷 Add photo`; editor labels `Album name`,
  `Description`, `Caption`.
- **LIVE:** the album list, and inside an album a square photo grid (3 across).

#### `museum` — Museum 🏛
- **Purpose:** a trophy hall of everything finished.
- **Static:** six wing headings — `Tasks & Assignments Completed`, `Books Read`,
  `Recipes Mastered`, `Projects Completed`, `Milestones Achieved`,
  `Best Habit Streaks`.
- **LIVE:** each wing's contents, plus a count beside each heading.
- **Empty state:** "Nothing here yet."

#### `collections` — Collections 🗂
- **Purpose:** anything you collect — records, cameras, whatever.
- **Static:** inputs "Collection name (e.g. Vinyl records)", "Description (optional)";
  item fields `Item name`, `Tags`, `Notes`; a cover-photo area.
- **LIVE:** the collection list, and the item list inside one.

#### `time-capsules` — Time Capsules ⏳
- **Purpose:** a sealed note to your future self.
- **Static:** inputs "Title (e.g. For my 30th birthday)", "Write to your future
  self…"; a date field; a `Seal` button; a locked-state panel.
- **LIVE:** the capsule list; sealed ones show a countdown, not their contents.
- **SLOT:** the countdown value on a sealed capsule.

#### `milestones` — Milestones 🏆
- **Purpose:** the things worth remembering, on a timeline.
- **Tabs:** `Timeline` · `Yearly recap`.
- **Static:** `New milestone` input + `Add`; editor labels `Title`, `Date`,
  `Category`, `Notes`, `Photo`; on the recap tab, a year selector and a
  `Write the recap` button.
- **LIVE:** the timeline; the recap's stat grid (about a dozen label/number pairs)
  and the written recap paragraph.

#### `ghost-days` — Ghost Days 👻
- **Purpose:** what happened on this date in previous years.
- **Static:** the line "on this day across the years"; kind labels `Milestone`,
  `Visited`, `Cooked`, `Started reading`, `Finished reading`, `Worked out`,
  `Completed`, `Birthday`.
- **LIVE:** the year-grouped list.

---

### LOGISTICS

#### `places` — Places 📍
- **Purpose:** where you've been, where you want to go, and a bucket list.
- **Tabs:** `Visited` · `Want to go` · `Map` · `Bucket list`.
- **Static:** `New place` input + `Add`; `📍 Check nearby places`; editor labels
  `Name`, `Category`, `List`, `Address`, `Linked people`, `Latitude`, `Longitude`,
  `Rating`, `Visit dates`, `Notes`, `Notes-to-self`, `Photos`, `Files`; buttons
  `Find on map`, `Use my location`, `📍 Remind me when I'm back here`.
- **LIVE:** the place list; the photo grid inside a place (3 across); and on the Map
  tab **the whole map area** — the app draws the world map and its pins.

#### `orrery` — Orrery 🪐
- **Purpose:** your life as an orbital system — modules as bodies, neglect as drift.
- **Static:** ring labels `Inner`, `Outer`, `Outer dark`.
- **LIVE:** the entire orbital diagram. *(This page's central visual is on the parked
  graphics list — art for the rings and bodies is exactly what it's waiting for.)*

#### `quartermaster` — Quartermaster 📦
- **Purpose:** what you own, where it is, and what's running out.
- **Tabs:** `All` · `Low` · `Out` · `Lent` (each with a count beside it).
- **Static:** `+ Add` button; the add-prompt fields "What is it?",
  "Location (optional)", "Tags, comma separated (optional)"; row buttons
  `📷 Photo`, `📊 Stock`; a stock pill in four states — Full / OK / Low / Out.
- **LIVE:** the item list; each row has a small square thumbnail on the left.
- **SLOT:** the count inside each tab chip.

#### `packing` — Packing Lists 🧳
- **Purpose:** one checklist per trip.
- **Static:** input "Trip name (e.g. Tokyo)" + `Add`; template buttons (4);
  inputs "Add an item", "Category"; a packed-count line.
- **LIVE:** the trip list; the item checklist grouped by category.

---

### DISCOVERY

#### `education` — Education 🎓
- **Purpose:** courses, assignments, grades and study time.
- **Tabs:** `Coursework` · `GPA & Time`.
- **Static:** course fields `Name`, `Credits`, `Final grade`, `Notes`, `Key dates`;
  assignment fields `Title`, `Due date`, `Status`, `% complete`,
  `Time spent (minutes)`, `Grade (%)`, `Total due`, `Checkpoints`.
- **LIVE:** the course list, the assignment list, and the GPA / time-spent panel.

#### `skill-trees` — Skill Trees 🌳
- **Purpose:** your real activity, expressed as levelling up.
- **Static:** three branch names — `Scholar`, `Executor`, `Discipline`; a level badge
  shape per branch; a progress bar per skill.
- **LIVE:** the skill rows and their fill levels.
- **SLOT:** the level number in each badge.

#### `ideas` — Ideas 💡
- **Purpose:** fast capture, tagged, searchable later.
- **Tabs:** tag chips, `All` first.
- **Static:** "New idea" input + `Add`; a mic button; "Tags (comma-separated)";
  per-row `Promote → Task` and `Archive`.
- **LIVE:** the idea list.

#### `rabbit-holes` — Rabbit Holes 🕳
- **Purpose:** what you went down a hole researching.
- **Static:** inputs "What are you researching?", "Notes as you go…", "https://…",
  "Title (optional)"; an active/resolved toggle.
- **LIVE:** the hole list, and the link list inside one.

#### `almanac` — The Almanac 📊
- **Purpose:** patterns the app noticed in your own data.
- **Static:** section headings `Correlations`, `Forecasts`, `What if…`.
- **LIVE:** all three bodies — each is a list of generated sentences with numbers.

---

### MANAGEMENT

#### `habits` — Habits 🔥
- **Purpose:** streaks that reset honestly.
- **Static:** "New habit" input + `Add`; "Why / how / when" field; a `Check in`
  button per row; a seven-day strip of small squares per row; an `Undo` action.
- **LIVE:** the habit list.
- **SLOT:** the streak number per habit.

#### `health` — Health ❤
- **Purpose:** daily log, workouts, metrics, and imports.
- **Tabs:** `Daily` · `Workouts` · `Metrics` · `Import`.
- **Static:** field labels `Date`, `Sleep (hrs)`, `Water (oz)`, `Workout type`,
  `Workout (min)`, `Weight (lb)`, `Notes`; metric fields `Metric`, `Value`, `Unit`;
  import buttons for Garmin CSV and Apple Health.
- **LIVE:** the day log list, the workout list, and the metric trend charts.

#### `recipes` — Recipes 🍳
- **Purpose:** cook from them, scale them, log them.
- **Tabs:** `Recipes` · `Grocery list`.
- **Static:** "New recipe" + `Add`; labels `Title`, `Base servings`,
  `Tags (comma separated)`, `Notes`, `Ingredients — scale to servings`, `Steps`,
  `Photo`; a servings stepper (`−` / `+`); `Keep screen awake`; `Cooked it` button.
- **LIVE:** the recipe list, the ingredient list, the step list, the grocery list.
- **SLOT:** the servings number between the − and + buttons.

#### `finance` — Finance 💵
- **Purpose:** ledger, bills, subscriptions, and holdings.
- **Tabs:** `Ledger` · `Bills` · `Subscriptions` · `Holdings`; chips `↻ Recurring`,
  `Autopay`, `Category`.
- **Static:** inputs "What was it?", "Amount (– to spend)", "Bill name", "Amount",
  "Subscription name", "Notes"; buttons `📷 Scan receipt`, `Import CSV`,
  `Mark paid`.
- **LIVE:** the ledger list, bill list, subscription list, holdings list, and the
  totals panel.
- **SLOT:** the running total / balance figure.

---

### INTELLIGENCE

#### `ask` — Ask 🔎
- **Purpose:** ask a question about your own data.
- **Tabs:** `Answer` · `Find in memory`.
- **Static:** a question input + `Ask` button.
- **LIVE:** the answer body, and the matching-records list beneath it.

#### `ai-assistant` — AI Assistant 🤖
- **Purpose:** a real conversation, grounded in your data.
- **Static:** conversation chips + `+ New`; `Rename` and `Delete`; a bottom composer
  with placeholder "Ask the assistant…" and a `Send` button.
- **LIVE:** the message thread — alternating bubbles, yours right-aligned and the
  assistant's left-aligned. Draw two empty bubble shapes at most, as a hint of the
  form; do not write dialogue into them.

#### `knowledge-graph` — Knowledge Graph 🕸
- **Purpose:** links between records across modules.
- **Static:** inputs "Search everything…", "Search for something to link…";
  a `Link` button.
- **LIVE:** the focused node panel and its connection list. *(The radial graph view
  is on the parked graphics list — this page is one of the ones waiting on art.)*

#### `recall` — Recall ♻
- **Purpose:** spaced repetition over facts you chose to keep.
- **Static:** input "A fact to remember" + `Add`; a card face; review buttons.
- **LIVE:** the card body and the due-count line.
- **SLOT:** the number of cards due.

#### `entropy` — Entropy 🌀
- **Purpose:** what you've been neglecting.
- **Static:** the heading `Overall`; a decay bar per module.
- **LIVE:** the module rows and their bar fills.
- **SLOT:** the overall percentage.

#### `time-machine` — Time Machine ⏰
- **Purpose:** scrub back and see what the app knew on a past day.
- **Static:** a horizontal slider with an end label at each side; a date field;
  chips `◀ Prev`, `Next ▶`, `Return to today`; headings for the count grid,
  `Added that day`, and `Lived that day`.
- **LIVE:** the two-column count grid (16 store names, each with a then→now figure);
  the added-that-day list; the lived-that-day list.
- **SLOT:** the big headline number, and the date on the scrubber.

---

### PEOPLE

#### `contacts` — Contacts 👤
- **Purpose:** people, with everything you know about them.
- **Static:** "New contact" + `Add`; a search field; `Import from phone`; editor
  labels `Name`, `Phones`, `Emails`, `Company`, `Title`, `Relationship`, `Birthday`,
  `Address`, `Tags (comma separated)`, `Notes`, `Photo`.
- **LIVE:** the contact list, with a round photo slot on each row.

#### `sharebox` — Sharebox 🤝
- **Purpose:** a shared feed between you and someone else.
- **Tabs:** `Mine` · `Shared with a friend`; composer chips `🔗 Link`, `📝 Note`.
- **Static:** inputs "https://…", "Title (optional)", "Write a note…";
  an urgency selector with three levels; `Post`; `Copy space ID`; `Attach file`.
- **LIVE:** the item feed — icon, title, sender line, and an `Open` button per row.

#### `qr-sync` — QR Sync 🔳
- **Purpose:** pair another device by scanning a code.
- **Static:** headings `ON THIS DEVICE`, "Scan on your other device to pair it",
  "Scan a code to pair"; a large square frame for the QR code; a `Scan` button.
- **LIVE:** the QR square itself — the app generates it.
- **Empty state:** "Sign in to pair devices" / "Open Settings → Sync".

---

### SYSTEM

#### `search` — Search 🔍
- **Purpose:** one box across everything stored.
- **Static:** a search input with placeholder "Search everything…".
- **LIVE:** results, grouped under module headings with a count on the right.
- **Empty state:** "Type to search across every module."

#### `tools` — Tools 🧰
- **Purpose:** the small utilities — currency, time zones, units, weather, markets.
- **Static:** section cards for each tool; fields `Amount`, `Code`,
  `Rate per 1 USD`, `Crypto watchlist`; inputs "City, e.g. Austin",
  "Type a code, currency, or country…", "Add a zone"; a `⇄ Swap` button;
  `Refresh`; `Set a rate by hand`.
- **LIVE:** the conversion result, the zone list, the weather card, the markets card.

#### `settings` — Settings ⚙
- **Purpose:** keys, sync, integrations, and the app lock.
- **Static:** section headings `INTERFACE`, `DEVICE`, `AI`, `TELEGRAM`, `SYNC`,
  `ALERTS`, `APP LOCK`, `BACKUP`; field labels `Wake phrase`, `OpenAI API key`,
  `Model`, `Google Gemini API key`, `Anthropic API key`, `Bot token`, `Chat id`,
  `Email`, `Password`, `Bill due-soon alert (days)`, `Document expiry alert (days)`,
  `New PIN (4+ digits)`; buttons `Send test message`, `Connect Telegram`,
  `Sign in`, `Set PIN`, `Export (share)`, `Import from clipboard`.
- **LIVE:** the interface list, the provider selector, and status lines under each
  section.
- **Note:** this is the longest page in the app — it scrolls well past one screen.
  Art should cover the first screenful and tile sensibly, or be built taller.

---

## 7. Overlays

These appear **over** whatever page is open, so they need their own art with a dimmed
backdrop around a centered panel.

#### `overlay-scan` — Scan result
After the camera reads something, this asks where it goes.
- **Static:** a kind label across the top; the words "Save to"; a destination list of
  seven options as radio rows; buttons `Discard` and `Save`.
- **LIVE:** the title, the itemized list of what was found, and the destination rows.
- **Empty state:** "Nothing itemized — saves as one entry."

#### `overlay-domain` — Domain list
Tapping a wheel petal on the home screen opens this.
- **Static:** the domain name in caps at the top; the line "tap anywhere to close".
- **LIVE:** the module list — up to nine rows, each an icon and a label.

#### `overlay-lock` — Lock screen
- **Static:** the app name, the line "Enter your PIN to unlock.", a PIN field, and an
  `Unlock` button, centered on a full dark screen. No back control — this one is a
  wall.

---

## 8. Suggested order

Build in this order — it front-loads the pages that are used daily and the ones whose
shape teaches the rest:

1. **Tasks**, **Today**, **Notifications** — the daily three, and between them they
   cover every pattern: list + selection bar, sectioned feed, composer.
2. **Quartermaster**, **Places**, **Contacts** — list-with-thumbnail pages.
3. **Books**, **Recipes**, **Finance** — tabbed pages with editors.
4. **AI Assistant**, **Ask**, **Search** — conversational and result pages.
5. The three overlays.
6. Everything else.

Once the first three exist and are traced, the mapping work for the remaining pages
is mostly repetition of the same regions.

---

## 9. What comes back to the app

For each page: the **PNG** and the **traced SVG** with shapes named per §3. That's
everything needed to wire it — the same way the home screen was done.

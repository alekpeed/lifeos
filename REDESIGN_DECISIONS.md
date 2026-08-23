# Life OS — Redesign Decisions

**Session date:** 2026-08-22
**Status:** deletions specified as an executable work order (§10); new builds
outstanding as specs
**Baseline:** 40 modules · 27,425 lines Kotlin · 173 `.kt` files (measured, not from docs)
**Result of this session:** 40 modules → 35 (34 after cuts, +1 for the Calendar module)

This document records what was cut, what was kept, why, and what remains. It is
the single reference for the redesign — the reasoning is kept alongside each
decision so none of it has to be reconstructed later.

**Reading order.** §1–§4 are the module-by-module review. §5 holds the
specifications for everything new. §6–§7 cover module count and the in-module
weight review. §8–§9 are the build order and delivery decisions. §10 is an
executable deletion work order — hand it to Claude Code. §11 adds ten features
chosen from a later brainstorm. §12 is the final architecture and the
consolidations it exposed. §13 is the future-features list.

Companion to `FEATURE_LIST.md` (what exists), `NATIVE_FEATURES.md` (what the
machine provides), and `ANDROID_APP.md` (the current build).

---

## 1. Cut — confirmed

Four files, 395 lines. No storage keys, no records, no migration, no data loss.
All four were read-only aggregates over other modules, which is why they are so
small — and why cutting them costs nothing downstream.

| Module | File | Lines | Reason |
|---|---|---|---|
| Orrery | `orrery/OrreryScreen.kt` | 112 | One data class (`Planet`), no input, renders as a list awaiting artwork. Duplicates Entropy's neglect metric with worse ergonomics. |
| Museum | `museum/MuseumScreen.kt` | 133 | Read-only trophy case assembled from six modules. Writes nothing. |
| Ghost Days | `ghostdays/GhostDaysScreen.kt` | 89 | Duplicates Today's "on this day" section. |
| Station Cat | `system/StationCatScreen.kt` | 61 | **Already orphaned** — the file compiles but has no entry in `Modules.kt`. Unreachable dead code; was never one of the 40. |

Also cut, PWA-only — these die with the `js/` deletion, not with this change:

- **Life as Music** — `js/audio/lifemusic.js`. Ambient chord loop generated from
  activity counts. Never ported to native.
- **Theme-from-Photo** — `js/interfaces/default/views/themefromphoto.js`. Accent
  palette extracted from a gallery photo. Never ported. The accent-color storage
  it fed **stays**; only the extraction path goes.

### Why these six

They violate the project's own stated thesis in `FEATURE_LIST.md`: *"a command
center, not a scrapbook. Serious, data-forward, utilitarian."* Every one of them
is a reward layer — computed from other modules, taking no input, existing to
make finished work feel good.

### Wiring points that must be updated

1. `Modules.kt:8, 13, 14` — three imports
2. `Modules.kt:82, 86, 89` — three registry entries
3. `data/Data.kt:38, 42, 43` — three `DataSource` entries. **These feed Search,
   Knowledge Graph, and Ask.** Leaving them produces search results that route to
   a screen that no longer exists.
4. `settings/SettingsScreen.kt:667` — accent help text referencing Theme-from-Photo
5. `App.kt:29` — comment only, cosmetic

---

## 2. Group A — surface redundancy

### Ideas — KEPT, no change

**Proposed:** merge into Tasks as a task with no due date.
**Rejected.** The argument was wrong.

An unfinished task is a failure state. An unfinished idea is just an idea.
Merging them makes every thought ever recorded into an open task, which corrupts
the overdue count, the Briefing, and Today. The task list stops meaning *what I
owe* and starts meaning *everything I ever thought*.

The "promote to task" action was cited as evidence the two are the same object.
That is backwards — you promote *across* a boundary. Its existence proves the
boundary is real.

Watch, but do not act on: Ideas overlaps Rabbit Holes more than it overlaps
Tasks. An idea that accumulates links and notes resembles a rabbit hole. A rabbit
hole has a resolution state; an idea does not. Different enough to keep both.

### Command — SCREEN REMOVED, function preserved

`AskScreen.kt` has no write path — no `Storage` import, no save call. It reads
and answers only.

`CommandScreen.kt` owns `parseAction()` and `createRecord()` and writes to Tasks,
Contacts, and Habits, plus a `capture(type)` shortcut that bypasses the AI parse.

**Command is therefore the only natural-language write path in the app.** Ask
retrieves. Assistant converses. Neither creates a record. Deleting it outright
would delete a capability, not a duplicate.

**Decision:** delete the screen and the nav entry. Move `parseAction` and
`createRecord` into Ask, so one text box both answers and creates, distinguished
by what was typed. This mirrors the propose-then-confirm pattern `SmartScan.kt`
already runs for the camera.

**Built 2026-08-23** as `core/Capture.kt` plus the fold into `AskScreen.kt`.

"Distinguished by what was typed" is the whole design, and it is a local, keyless
read: a question mark, an interrogative opening, or an imperative one. The button
label changes as you type — **Ask** or **Create** — so the reading is visible before
you commit to it rather than discovered afterwards. Nothing is written on that read:
a command becomes a proposal, confirmed on a card, exactly as SmartScan proposes what
it saw in a photo.

Two things the move settled that the entry did not raise:

- **The type is editable on the card.** The old screen's `→ Task` / `→ Idea` buttons
  were asking the question the classifier now answers, so they became chips on the
  proposal instead — the machine's read of one sentence is a good first guess and
  nothing more, and this is what keeps a wrong guess a tap rather than a wrong record.
- **The classifier leans one way on purpose.** A line that could read either way is
  treated as a question, because answering something meant as a note costs a moment,
  while filing something meant as a question leaves a record you did not write. The
  one place the same verb genuinely means both — "did my workout" against "did I pay
  the rent" — is separated by the pronoun after it.

The AI parse is still there and still preferred when a key is set, but it is no longer
load-bearing: an unreachable model or an unparseable reply falls back to the local
read rather than losing the line. 15 tests cover the classifier, the date extraction,
the keyless guess, and every record type it can write.

### Notifications — SCREEN REMOVED, split two ways

Two things are genuinely its own:

**1. Standalone reminders.** It owns `Storage.write("Notifications", ...)` — a
persisted list of `Reminder(text, atEpochMillis)` attached to no record anywhere
in the app. Briefing has zero reminder code. **Deleting the screen without
migrating this key destroys user data.**

**2. It is the only clock-aware code in the codebase.** `In 1h`,
`This evening` (18:00), `Tomorrow AM` (09:00), backed by `epochMillisAt`,
`nextClockTime`, and real `Native.scheduleReminder` alarms. Every other module in
Life OS is date-granular.

That second point is load-bearing for the redesign: Notifications is a partial,
accidental implementation of **M-01 (calendar and time of day)**. Deleting it
before M-01 exists means throwing away the only working clock code and rebuilding
it later.

**Decision:**
- The due / overdue / expiring feed **merges into Briefing**, which already
  renders that feed with per-row actions. Pure duplication.
- The reminder engine and its storage key are **promoted, not deleted** — they
  become the foundation of M-01.

**Built 2026-08-23**, once M-01 existed to promote it into.

The feed needed no merging in the end: Briefing had already moved onto the shared
dated query (§12.1.1), so the duplication was gone before the screen was. Deleting
it removed a fifth walk over the same modules, not a feature.

The reminders are `calendar/Reminder.kt`, and they are ordinary dated records now —
they appear on the month, in the Briefing and in the Daily Paper through the same
query as everything else, because they enter `datedItems` like every other source
rather than being read by one screen. The quick-time chips came across intact onto a
second Calendar tab, which is where a time of day belongs; a reminder is a time with
a sentence attached, and this is the only screen that understands times.

**The storage key is unchanged.** `"Notifications"` is what every install and every
synced row already holds, and renaming it would have stranded every reminder anybody
has written — the precise hazard this entry flagged. The shape inside it upgrades
from tab-delimited lines to JSON the way Tasks did, and the old format still reads,
with ids assigned by position on the one pass that needs them.

Three things beyond the brief, each because moving the code exposed them:

- A reminder now has a `done` state. The old screen offered only a delete, which
  left the notification's own button (§7 D-5) nothing non-destructive to do —
  marking one done from a lock screen should not throw the text away.
- What is pinned as the "next up" ticker is persisted. It was a screen-local
  variable, so the app forgot the moment you navigated away while the notification
  itself stayed up saying otherwise.
- `DataSource` gained an explicit `moduleId`. It was derived from the label, which
  is right until a key outlives the screen it was named for — §10.3's warning about
  a source pointing at a screen that no longer exists, arriving by a different door.

### Known consequence

Ask becomes read-and-write. Briefing absorbs a second feed. This trades module
count for screen density. Intended if the redesign targets fewer, denser screens;
worth reconsidering if it targets simpler ones.

---

## 3. Group B — all three kept, all three get work

### Collections — KEPT, and slated for a full overhaul

**Proposed:** merge into Quartermaster on the grounds that `InventoryItem` is a
superset of `CollItem`.
**Rejected.** The field-comparison argument was the weak form of the case.

The two modules answer different questions:
- **Quartermaster:** *do I have it, where is it, who has it.*
- **Collections:** *what is in the set, and what am I missing.*

But the module as built does not answer its own question. `CollItem` holds
`id`, `name`, `acquiredDate`, `tags`, `notes` — and the file's own comment
concedes it was "ported from the web app's Collections Tracker: any freeform
collection." It implements **listing**, not **collecting**. The defining feature
of a collection — knowing what is missing from a set — is absent.

**Decision:** keep and overhaul. See §5.

### Packing Lists — KEPT, relocated into Travel

Four templates are hardcoded in `Packing.kt:28` — Weekend trip, Beach/warm,
Ski/cold, International. There is currently no way to define a new one.

A packing list is meaningless outside a trip. As a top-level module it competes
for a slot with Tasks and Finance; as a tab inside Travel it sits beside the
itinerary and the reservations, which is where it would actually be looked for.

**Decision:** Packing Lists becomes a tab inside a new Travel module. Templates
become user-definable. See §5.

### Time Capsules — KEPT, currently broken

`sealedUntil` appears **only** inside `timecapsules/`. Nothing else in the
codebase reads it.

Consequence: **nothing announces that a capsule has opened.** Not Today, not
Briefing, not Daily Paper, not Notifications. A note sealed for five years
unseals into a module there is no reason to open, and stays unread. The module's
one job is to surface something at a future moment, and the surfacing was never
wired.

Same defect class as the recurring Tonic finding: built, never wired into the
production path.

**Decision:** keep, and wire the surfacing. See §5.

---

## 4. Group C — reviewed

### QR Sync — KEPT, no change

**Does:** Generates a QR code another device scans to pair; data then moves
peer-to-peer over the LAN, merged last-write-wins with tombstones.

**Kept.** It is the only sync path that works with no account and no internet.
Supabase supersedes it for everyday use, but the fallback has value if a device
moves while offline or Supabase is ever distrusted. It is also the sole consumer
of QR scanning in the app — cutting it would leave that capability unused.

### Recall — CUT, with a salvage

**Does:** Spaced repetition over `Fact(text, intervalDays, nextReview)` on a fixed
six-rung ladder — 1, 3, 7, 14, 30, 90 days. "Know it" advances a rung, "Forgot"
resets to the start.

**Cut.** An orphan of the removed Languages module. `ANDROID_APP.md` lists its own
purpose as still undecided. What shipped is a standalone flashcard box: `Fact` is
free text with no link back to the record it came from, so the "resurface anything
in the app" premise in `FEATURE_LIST.md` was never actually built.

**Salvage before deleting:** the interval ladder in `insight/Recall.kt` (41 lines)
is a decay curve — advance on success, reset on failure. That is the same shape
skill atrophy takes. Extract it for the Mastery module (§6.2) rather than
rewriting it later.

### Time Machine — KEPT, rebuild gated on R-02/R-03

**Does:** Scrub to a past date and see how many records existed then, what was
added that day, and what was genuinely dated that day.

**Kept, and to be made as robust as possible — but the sequencing is not
optional.**

The current version cannot be improved into a real one. It reconstructs history
from creation dates because that is the only history that exists. `TimeMachineScreen.kt:259`
prints an on-screen note that records deleted since then are missing from the
picture; `:73` notes legacy records carry no birth date and cannot anchor the
timeline. Neither is a UI shortcoming — the data was never captured.

**R-02 + R-03 (soft delete + edit history) is the prerequisite.** Once every change
is an event with a timestamp, a before and an after, scrubbing to a date becomes
replaying the log to that point. That yields what the current version cannot:

- What a record **said** on a given day, not merely that it existed
- What changed, when, and in which direction
- What was deleted, when, and by which action — currently invisible
- Field-level diffs between any two dates
- Full undo / restore-to-a-point, which the event log gives for free
- An honest timeline anchor, since every record has a first event

**Build order: R-02/R-03 first, then rebuild Time Machine on the log.** Improving
the current screen beforehand means building the approximation twice.

*Gate opened 2026-08-23.* The log exists and `History.blobAt(key, at)` returns a
module's blob as it stood at any moment inside the retention window, by reversing
every event since.

**Rebuilt 2026-08-23** as `timemachine/Replay.kt` plus a two-tab screen. All six
bullets above are now real: a record reads as it did on a day, changes carry their
direction, deletions are visible, two dates diff field by field, and a record can
be put back to how it read — through the ordinary write path, so the rewind is
logged like any edit rather than silently rewriting the past.

The one thing added beyond the spec is the boundary. The log started when it was
built and is capped, so replay is exact only from its first event onwards. The
screen draws that line rather than blurring it: before it, existence is all that is
known, which is the old approximation kept and labelled instead of dressed up. The
counts looked authoritative before and were partly guesswork — that is the failure
mode worth avoiding twice.

### Skill Trees — MERGED with Mastery into one module

**Does today:** Three hardcoded branches — Executor, Discipline, Scholar — whose XP
is a count of tasks completed, habit check-ins, and books finished. You cannot add
a skill, name one, or log practice against one.

**Decision: one module, not two.** Skill Trees keeps its name, its nav slot, its
tree visualization and its automatic inputs. It gains user-defined skills, practice
logs, benchmarks and decay. Full spec in §5.2.

**The boundary that must hold: integrate the inputs and the presentation, never the
scoring.**

Mixing derived and declared numbers in one score makes both unreadable. If
"Guitar — Level 4" is partly benchmarks cleared and partly unrelated tasks
completed, the 4 means nothing: practice moves it, and so does a busy admin week.
That is precisely why the existing XP is hollow, and why two of five branches
silently died when Chords and Languages were cut.

**Carried over from Skill Trees:**
- The tree visualization. `parentId` already makes Mastery a tree — Guitar
  branching into barre chords, sight-reading and fingerpicking, each with its own
  level and decay state. This is Skill Trees' real contribution and it is better
  than a list.
- The at-a-glance overview: every skill's standing on one screen without drilling in.
- Automatic inputs — but reattributed. Tasks, habits and books feed a skill as
  **evidence and hours**, never as the level.

**Executor / Discipline / Scholar are KEPT** (decision revised 2026-08-22). They
survive as **Standings** — a separate tier with its own vocabulary, so a derived
number can never be mistaken for an earned one. They are no longer hardcoded. See
§5.2.

**Dropped:** the shared vocabulary. Nothing outside the Skills tier uses the word
"level," and no derived number ever changes a skill's level.

---

## 5. New and reworked — specifications


### 5.1 Travel (new module, Logistics domain)

Absorbs Packing Lists. Deliberately over-specified — the instruction was to build
wide and pare down later rather than under-build.

**Trip** — the container
- `id`, `name`, `destinations` (list), `startDate`, `endDate`
- `status` — planning / booked / active / past (derived from dates, overridable)
- `notes`, `coverPhotoBlob`
- `travelers` — links to real Contacts entries
- Countdown on Today and Briefing while a trip is upcoming or active

**Reservation** — the core record
- `id`, `type` — flight / lodging / rail / bus / car / ferry / tour / restaurant / event / other
- `provider`, `confirmationNumber`, `status` (held / confirmed / cancelled)
- `startDateTime`, `endDateTime` — **time-of-day required**, so this depends on M-01
- `location` — address, `lat`, `lng`; links to a Places record where one exists
- `contactPhone`, `contactEmail`, `contactName` — links to Contacts where one exists
- `externalLink` — a URL to the confirmation email in Gmail, the airline's manage
  page, or the booking site
- `cost`, `currency`, `paid` — rolls into the trip budget
- `attachments` — boarding passes, e-tickets, vouchers (existing `attach/` layer)
- `notes`

**Travel documents**
- Passport, visa, vaccination record, insurance, international driving permit
- **Links to existing Documents records rather than duplicating them** — Documents
  already owns `expiryDate` and the expiry alert
- A per-trip check: any linked document expiring before or during the trip
  surfaces on the trip and in Briefing

**Packing** — the existing module, moved in
- One or more lists per trip, items grouped by category, packed tally
- Built-in templates retained
- **New: user-defined templates.** `PACKING_TEMPLATES` becomes a stored list;
  "save this list as a template" is one button

**Trip media**
- Photos and scans from the trip — links to a Photos album rather than a second
  photo store
- Receipts and paperwork as attachments

**Trip budget**
- Estimated vs actual, per-currency, using the existing Tools currency rates
- Reservation costs roll up automatically

**Places integration**
- Places visited on this trip, drawn from `visitDates` falling inside the trip range
- Bucket-list items in the destination surface as suggestions while planning

### 5.2 Skill Trees — rebuilt (Discovery domain)

Not a new module: the existing Skill Trees slot, rebuilt. One module, **two tiers**,
deliberately separate vocabularies.

| | Tier 1 — **Standings** | Tier 2 — **Skills** |
|---|---|---|
| Source | Derived from activity elsewhere | Declared by you |
| Setup needed | None | Skills, scale, benchmarks |
| Unit | **Rank** | **Level** |
| Advances on | Accumulated activity | A benchmark being met |
| Populated on day one | Yes | No |

**The wall:** practice hours may feed a Standing. A Standing may **never** feed a
skill's level. Nothing outside Tier 2 uses the word "level."

Rationale: the concept was never the problem. The problem was that both things
displayed "Level 4" while meaning different things — one earned, one accumulated.
Different words and a visually distinct band solve it without losing the character
sheet.

---

#### Tier 1 — Standings

Preserves the existing behavior exactly, and fixes the two things that made it
fragile.

**Math carried over unchanged** from `skilltrees/SkillTreesScreen.kt`:
- `rank = floor(sqrt(xp / 10)) + 1` — fast early, slower later
- `xpForRank(n) = 10 * (n-1)^2`
- Tasks and assignments completed: 10 XP each
- Habit check-ins: 5 XP each
- Books finished: existing weight retained

**Fix 1 — no longer hardcoded.** A Standing becomes a record:
- `id`, `name`, `icon`, `blurb`
- `sources` — a list of counters, each naming a module and a countable event:
  tasks completed · assignments submitted · habit check-ins · books finished ·
  recipes cooked · places visited · practice hours logged · documents filed ·
  collection items acquired
- `weights` — XP per event, per source
- `rankNames` — optional labels for the rungs

Cutting a module now means reweighting a Standing, not silently losing a branch.
This is what killed two of the original five when Chords and Languages were
removed.

**Fix 2 — seeded, not fixed.** Ships with Executor, Discipline and Scholar
preconfigured at their current weights, so the screen looks and behaves as it does
today on first open. All three are renameable, reweightable and deletable, and new
ones can be added — a Cook standing from recipes cooked, an Explorer standing from
places visited.

**Presentation:** its own band, visually distinct from the skill trees below it.
Reads as *what you have been doing*, not *what you are good at*.

**Honest limit, stated in the module:** a Standing counts activity. It cannot show
that you got better at anything, only that you did more. That is acceptable
precisely because it never shares a slot or a word with a real skill level.

---

#### Tier 2 — Skills

Where mastery is actually tracked. The inversion: Standings **derive** from
activity; Skills let you **declare** what you are learning and log practice against
it, with activity feeding hours rather than levels.

**Skill**
- `id`, `name`, `domain` (music / language / code / physical / craft / other — free text)
- `parentId` — sub-skills, so "Guitar" contains "Barre chords" and "Sight-reading"
  as independent trackable children
- `startedDate`, `notes`, `photoBlob`
- `currentLevel` — a position on the skill's own ladder, set by you or by
  assessment, never derived from a task count
- `levelScale` — user-defined ordered rungs, so a language can use A1→C2, a
  martial art can use belts, and an instrument can use grades, without any of them
  being hardcoded
- `targetLevel` and `targetDate` — optional; drives the Briefing nudge
- `active` — a paused skill stops decaying and stops nagging

**PracticeLog** — the actual record of work
- `id`, `skillId`, `date`, `minutes`
- `focus` — what was worked on this session
- `quality` — self-rated, 1–5; deliberate practice is not the same as time served
- `notes`, `attachments` — a recording, a photo of the page, a diff

**Benchmark** — what "next level" concretely means
- `id`, `skillId`, `label`, `targetLevel`, `achieved`, `achievedDate`
- Example: "play the F barre chord cleanly at 80bpm", "read 100 kanji unaided"
- Achieving one is the honest trigger for a level change, rather than an XP bar

**Decay** — salvaged from Recall
- The interval ladder extracted from `insight/Recall.kt` (1, 3, 7, 14, 30, 90)
  becomes a practice-decay model: a skill unpracticed past its rung surfaces as
  going cold, and practicing advances the rung
- Same pattern already proven in Rabbit Holes' three-week cold-thread detection
- A paused skill is exempt

**Views**
- Per-skill: total hours, current streak, practice frequency, level history,
  benchmarks met and outstanding
- All-skills: hours by skill, what has gone cold, what is closest to its next
  benchmark
- Time-of-day and weekday practice patterns once M-01 lands

**Reads from, does not duplicate**
- **Education** already stores `timeSpentMinutes` per assignment — a course can be
  linked to a skill and contribute hours
- **Habits** already tracks daily practice streaks — a habit can be linked to a
  skill rather than logging the same session twice
- **Books** finished in a skill's domain can be attached as evidence

**Automatic evidence — carried over from the old Skill Trees, reattributed**
- A **Habit** linked to a skill auto-logs a practice session on check-in
- An **Education** course linked to a skill contributes its `timeSpentMinutes`
- A **Book** finished in a skill's domain attaches as evidence
- **Tasks** tagged to a skill count toward its hours

Nothing is logged twice, and the hours shown are real hours.

**Presentation — carried over**
- Tree view: parent skill with sub-skills branching, each carrying its own level,
  hours and decay state
- One overview screen showing every skill's standing without drilling in

**What is deliberately absent from Tier 2:** XP, levels that rise on their own,
and any number computed from unrelated activity. Those belong to Standings, in
their own band, under their own word.

**Built 2026-08-23** as `skilltrees/Standings.kt` and `skilltrees/Skills.kt`, with
the module's screen split into the two bands. Both fixes landed: a Standing is a
record with named sources and per-source weights, and the three are seeded rather
than fixed — deleting one keeps it deleted. `Decay.kt`, salvaged from Recall when
§10 ran, now has its caller: a skill's rung is derived from its practice history
rather than stored, so it cannot drift out of step with the logs.

Two things worth recording beyond the spec. Hours are kept apart by origin —
logged, from linked habits, from linked courses — so the screen can say where an
hour came from; and a habit check-in on a day that already has a written session
is skipped, because the same session counted twice is exactly what "the hours
shown are real hours" was meant to prevent. The two tiers also store separately
(`Skill Trees` and `Skills`), so a Standing's weights and a skill's practice log
cannot corrupt each other.

### 5.3 Collections — overhaul

Category-agnostic by design. Must work equally for baseball cards, stamps,
Pokémon cards, coins, vinyl, or anything else, without per-category code.

**Collection**
- `id`, `name`, `category` (free text), `description`, `coverPhotoBlob`
- `catalogSystem` — free text naming the reference standard in use
  (Scott, Beckett, Discogs, Krause, PSA, whatever). Agnostic: it is a label, not
  an integration.
- `conditionScale` — a user-defined ordered list of grades, so a card collection
  can use Poor→Gem Mint and a coin collection can use G→MS-70 without either
  being hardcoded
- `targetSet` — an optional list of catalog references defining a complete set.
  **This is what makes it a collection rather than a list.**
- `defaultCurrency`

**CollItem**
- `id`, `name`, `catalogNumber`, `series`, `setName`, `year`, `variant`
- `condition` — one value from the collection's own scale
- `graded` (bool), `grader`, `certNumber`, `gradeValue`
- `quantity` — duplicates are normal in collecting and must be first-class
- `status` — owned / wanted / on order / for trade / sold
- `acquiredDate`, `acquiredFrom`, `acquiredPrice`, `acquiredCurrency`
- `estimatedValue`, `valuationDate`, `valuationSource`
- `storageLocation` — binder, page, box, slab, sleeve
- `provenance` — free text: previous owners, signing event, purchase story
- `photos` — multiple per item; front/back is the norm for cards, coins, and stamps
- `tags`, `notes`

**Views the module must provide**
- **Set completeness** — owned vs `targetSet`, as a percentage and a missing list
- **Want list** — every item with status `wanted`, across all collections, in one
  place, so it is usable while standing in a shop
- **Value rollup** — total cost basis, total estimated value, unrealized gain/loss
- **Duplicates** — everything with `quantity > 1`, i.e. trade stock
- **Grouping** — by series, set, year, or condition
- **Insurance export** — a per-collection list with values and photos, suitable for
  attaching to a Documents record

**Reuse — no new native capability required**
- Barcode / catalog scanning via existing `scanAnyCode`
- Camera-vision cataloging via the pattern already shipped in Quartermaster
- Attachments via the existing `attach/` layer

**Boundary with Quartermaster:** once these fields exist the two modules are
permanently and obviously distinct. Today they are distinguished only by intent.

**Built 2026-08-23.** Every field and every view above. Two things worth recording.

A collection with no `targetSet` returns *no* completeness rather than 100% — a
confident number nobody defined is worse than an absent one, and the Set tab says
what to add to make it real. And the value rollup reports its own coverage: how
many items have no purchase price and how many have no valuation, because a total
built from a third of the collection reads exactly like a total built from all of
it. Quantity carries through cost and value throughout, since three of a card cost
and are worth three times as much — the alternative silently under-reports every
duplicate.

The old blob decodes straight into the new shape: every added field has a default,
so an existing item keeps its name, date, tags and notes and arrives at quantity 1,
status Owned.

### 5.4 Time Capsules — wire the surfacing

Two mechanisms, deliberately redundant, because each fails differently.

**1. Scheduled alarm at seal time.** On save, call the existing
`Native.scheduleReminder(id, "Time Capsule", title, sealedUntil)`. Costs almost
nothing — the alarm infrastructure already exists and is already used by Finance
for bills. Fires with the app closed.

**2. A Briefing row on and after the unseal date.** The durable fallback.
Alarms do not survive a reinstall, an OS upgrade, or a device change, and a
capsule sealed for years will very likely outlive its alarm. A Briefing row is
computed from the record itself and cannot be lost.

Recommended additionally: a count on Today while any capsule is unsealed and
unread, so it does not sit unopened in a module with no other reason to visit.

**Requires** a `readAt` or `opened` field on `TimeCapsule` — currently there is no
way to know whether an unsealed capsule has been seen, which is what makes both
mechanisms actionable rather than permanent.

**Built 2026-08-23.** Both mechanisms, the Today count, and `readAt`.

One addition the spec did not call for, because building it exposed the gap: an
opened capsule now keeps its body until you press "Open it". The list used to
render the body inline the moment the date passed, which would have made `readAt`
meaningless — there was no moment to stamp. Requiring the press gives the field
something true to record, and gives a capsule the opening it is supposed to have.

Alarms are also re-armed for every still-sealed capsule at app open. Mechanism 1
is the one that gets lost, and this is the cheapest way to get it back after a
reinstall — a couple of reads and one alarm per sealed capsule, of which there are
never many. Capsule alarm ids sit above 900,000 so they cannot collide with the
bill reminders Finance schedules.

---

## 6. Module count

| Stage | Count |
|---|---|
| Baseline | 40 |
| − Orrery, Museum, Ghost Days | 37 |
| − Command (screen only) | 36 |
| − Notifications (screen only) | 35 |
| − Packing Lists (absorbed), + Travel | 35 |
| − Recall | 34 |
| Skill Trees rebuilt in place | 34 |
| + Calendar (M-01b) | 35 |

Station Cat is not counted — it was already unreachable.

This table tracks the redesign's own subtractions, not the live registry: the
modules added since (Projects, Tags, History, Skills, Time Capsules and the rest)
are counted in §12, not here. `Modules.kt` holds **39** as of 2026-08-23, with
Notifications and Command removed.

---

---

## 7. Group D — reviewed

Weight inside surviving modules. No change to module count.

### D-1 Custom interfaces — ALL CUT

**Decision: NEXUS, Nocturne and Machiya are removed entirely.** They are to be
rebuilt later from a different workflow. Wipe them from the documentation — they
are not parked, not deferred, and should not appear in status recaps.

Delete:

| Path | Lines |
|---|---|
| `interfaces/nexus/NexusCommandRoomHome.kt` | 639 |
| `interfaces/nexus/NexusHome.kt` | 173 |
| `interfaces/nocturne/NocturneOperationsRoom.kt` | 204 |
| `interfaces/nocturne/NocturneHome.kt` | 183 |
| `interfaces/machiya/MachiyaHome.kt` (desktop) | 266 |
| `interfaces/machiya/RainAudio.kt` (desktop) | 65 |

**1,530 lines removed.** Plus PWA `spatial-1` and `mobile-1`, which die with the
`js/` deletion regardless.

**Keep `interfaces/Interfaces.kt` (82 lines).** The registry and the
`Interfaces.Render` fallback stay so future interfaces can register without
touching module logic. What goes is every current implementation, not the
mechanism.

**Required cleanup — Nocturne is currently the baseline.** `Interfaces.kt:33` sets
`BASELINE = "nocturne"`, and there is a one-time migration keyed on
`NocturneHomeMigrated` that promoted it on existing installs. Removing Nocturne
without addressing this leaves installs pointing at an interface that no longer
exists.

- Set `BASELINE = DEFAULT`
- Add a migration that resets any stored `ActiveInterface` value to `default`
- Remove the `NocturneHomeMigrated` key handling

**Result:** the default functional interface becomes the only interface — the
plain, generic one, with no graphical home. New interfaces get built later from a
different workflow, against a clean registry.

**Documentation:** NEXUS, Nocturne and Machiya are to be struck from every project
document, not marked parked or deferred. They should not appear in status recaps.
Affected: `FEATURE_LIST.md`, `NATIVE_FEATURES.md`, `ANDROID_APP.md`,
`NOCTURNE_THEME.md` (delete outright), `UI_INTERFACE_INVENTORY.md`,
`SPATIAL_INTERFACES_SPEC.md`, `MOBILE_INTERFACES_SPEC.md`.

### D-2 Wake word and speaker ID — KEPT, resource-constrained (all five levers approved)

**Keep**, including the intent to grow it into an assistant. **Constraint: battery
cost must not materially exceed the current Google assistant baseline.**

Footprint today — 505 lines across `WakeWordService.kt` (205), `VoskModels.kt`
(112), `VoiceEnroller.kt` (99), `VoiceId.kt` (89), plus a bundled Vosk model and
this manifest surface:

- `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`
- `WakeWordService` with `foregroundServiceType="microphone"`
- `LifeAssistService` bound to `BIND_VOICE_INTERACTION`

**Where the drain comes from — state plainly, because it constrains the target.**
It is not the Vosk model. It is holding the microphone open continuously on the
main CPU with a wake lock. Google's assistant runs hotword detection on a
dedicated low-power DSP core in the audio hardware; the CPU stays asleep until the
phrase hits. **Third-party apps cannot reach that DSP.** Any app-level wake word is
CPU-side by definition.

**Therefore: parity with Google is achievable only by gating when it listens, not
by optimizing how it listens.**

Levers, in order of effect:

1. **Gate on charging and/or screen-on.** The single biggest lever — removes
   almost all cost while preserving the use case (hands-free while cooking, or in
   a car dock).
2. **Gate on time of day** once M-01 lands. Listen 07:00–22:00, not overnight.
3. **Smallest viable Vosk model** and the lowest sample rate that still triggers
   reliably.
4. **Release the wake lock between detections** — hold only during capture.
5. **Remove speaker ID from the always-on path.** Verify the voiceprint *after* the
   trigger fires, not continuously.

Always-on, all-day, ungated will cost more than Google. That is a hardware limit,
not an implementation defect.

**Built 2026-08-23** as `wakeword/Gating.kt` plus the gate in `WakeWordService`.

Two of the five levers turned out to need no work, and saying so is more useful than
claiming them:

- **Smallest viable model (3)** — already `vosk-model-small-en-us-0.15` at 16 kHz,
  which is the smallest English model Vosk publishes and the rate it is trained at.
  There is no smaller one to move to.
- **Release the wake lock (4)** — there was no wake lock. The service never took one;
  the foreground service is what keeps the process alive. Nothing to release.

The other three are real, and one of them is the whole thing:

- **Power gate (1).** Three settings — charging only, charging or screen on, always —
  defaulting to *charging or screen on*, so an existing install becomes gated on
  upgrade rather than keeping the old cost until somebody notices. Screen and power
  changes arrive as broadcasts to a receiver the running service registers itself;
  they cannot be declared in a manifest, which suits, because when the service is not
  running there is nothing to gate.
- **Hours gate (2).** Default 07:00–22:00, and it outranks the power gate — a phone
  charging overnight is the exact case it exists for. The window may run past
  midnight, which somebody working nights needs and which a naive `from < until` gets
  wrong. It is one alarm at the next boundary, not a timer that wakes the CPU to ask
  what time it is.
- **Speaker ID off the always-on path (5).** The x-vector is computed per decoded
  speech segment, so it costs nothing while nothing is being said — and now costs
  nothing at all while a gate is closed, because the decoder is stopped rather than
  idling. Verifying strictly *after* a trigger is not reachable with Vosk without
  either buffering audio or splitting the interaction into two utterances; both are
  worse than what closing the gate already achieves.

Closing a gate stops the microphone and the decoder and leaves the model in memory —
D-2's own diagnosis is that the drain is the open mic on the CPU, not the model, and
reloading 40 MB from disk on every screen-on would be its own cost several times an
hour. The notification says which gate is closed rather than leaving "Life OS is
listening" over a microphone that is shut.

The policy is pure and lives in commonMain, tested on the desktop JVM (13 cases). It
is the only part of a microphone service that can be tested without a device, and the
part where being wrong costs a day of battery while looking exactly like working code.

Ungated remains available and remains labelled, in the Settings copy as well as here:
it costs more than the system assistant, and that is the hardware limit above rather
than something an update fixes.

### D-3 Telegram — KEPT, contingent on scheduled push

**Keep.** Its value depends almost entirely on scheduled sending, which does not
exist yet.

**Current state:** `scheduleReminder` exists in `Native.kt:102` with three callers
— two in Finance for bill reminders, one in Notifications. **Telegram has no
scheduled caller. Every send today is a manual button press.**

Three tiers, in ascending cost:

1. **Local scheduled send — buildable now, no backend.** An AlarmManager alarm
   fires at a set time, wakes the app, the app posts the digest. Adds a fourth
   caller to plumbing that already works. Android only; fires only if the phone is
   on. Desktop gets nothing, since desktop has no alarm support at all.
2. **Supabase Edge Function on a cron — the real answer.** `supabase/functions/`
   already exists in the repo. A scheduled function composes and sends the digest
   server-side, independent of whether any device is awake, and reaches desktop.
   This is what makes Telegram genuinely worth keeping.
3. **True background push** — remains moonshot tier in `FUTURE_FEATURES.md`.

**DECISION: build tier 2 and tier 3 both.** Scheduled push must land. See §7 D-5.

### D-4 Almanac forecasts and what-if — RAISE FLOORS, SHOW SAMPLE SIZE

Two distinct things live here and only one is trustworthy.

- **Correlation** — *"these two moved together."* Describes what happened. Claims
  no causation, predicts nothing.
- **Forecast** — *"here is what happens next."* Draws a straight line through past
  points and extends it: next month's spend, a book's finish date, the weekday a
  habit is most likely to be skipped.

**The problem is the thresholds**, read from `AlmanacScreen.kt`:

| Constant | Value | Meaning |
|---|---|---|
| `CORR_MIN` | 5 | Five paired days produces a correlation coefficient on screen |
| `TREND_MIN` | 5 | Five points produces a trend line |
| `MONTHS_MIN` | 3 | Three months projects next month's spend |
| `WEEKDAY_MIN_DAYS` | 14 | Two weeks names your most-skipped weekday |
| `linregress` | `size >= 2` | **A regression fits on two points** |

A straight line through two points is not a prediction — it is the line between
them, extended. It renders a precise decimal and is wrong in a way that looks
authoritative. The what-if slider compounds this: it refits that same
two-to-five-point regression live and returns a confident answer built on a fit
with no confidence.

**Decision — both of the following:**

1. **Raise the floors.** Revised after review — lower than first proposed, still
   high enough that a number means something when it appears:

| Constant | Current | Revised |
|---|---|---|
| `CORR_MIN` | 5 days | **21 days** |
| `TREND_MIN` | 5 points | **21 points** |
| `MONTHS_MIN` (spend forecast) | 3 months | **6 months** |
| `WEEKDAY_MIN_DAYS` | 14 days | **42 days (6 weeks)** |
| Reading pace | 2 logs | **4 logs** |
| `linregress` minimum | 2 points | **6 points** |

   Six months is the floor for spend and should not go lower — under that, any
   annual bill lands inside the window as a false trend.
2. **Always display the sample size.** `r = 0.62 · 34 days`, not `r = 0.62`. This
   is cheap and moves the trust judgment to the point of reading.

Nothing is deleted. The numbers simply stop appearing before they mean anything.

**Built — floors 2026-08-22, sample sizes 2026-08-23.** The arithmetic and the floors
moved into `insight/Almanac.kt`, out of the screen, because a floor is exactly the kind
of constant that gets quietly lowered to make a demo look better and a test is what
notices. Ten cases pin them, including that nothing fits on two points.

Every figure now carries its sample, and the types enforce it: a correlation, a trend,
a spend projection and a reading estimate each travel with their count, so there is no
way to render one without it. The what-if slider says it too, which is where it matters
most — it refits nothing, it reads off the same line live and returns a confident number
for whatever you drag to.

Two things the pass turned up that the entry did not:

- **The sleep↔tasks correlation counted one set and fitted another.** It gated on days
  with a completed task and then fitted over every day with a sleep figure, scoring the
  rest as zero. Both are defensible; showing only one number is not, because a hundred
  zeros and five real days would have printed as a hundred-day finding. It reads
  `· 100 days, 5 with a task`.
- **"Most likely to skip X on Monday" had no sample at all** — it named a weekday from
  a raw count with nothing to judge it by. It now reads `kept 3 of 12`, which is the
  evidence rather than the conclusion.

### D-5 Server push — REQUIRED BUILD

**Decision: true server push lands.** Anything needing attention reaches the user
as a high-priority notification, whether or not the app is open.

**The constraint that shapes everything:** on Android, exactly one mechanism can
wake a device with the app closed — **Firebase Cloud Messaging**. No third-party
app can do it another way. `Native.scheduleReminder` (AlarmManager) only fires
alarms the device already knows about; it cannot deliver anything decided
server-side.

**Two transports, built in this order.**

**Phase 1 — Telegram via Supabase cron.** Days of work, no new external service.
- A Supabase Edge Function on a cron schedule (`supabase/functions/` already exists)
- Queries overdue / due-soon / expiring across modules
- Posts the digest through the existing `TelegramClient`
- **Reaches desktop as well as phone**, which FCM cannot
- Borrows Telegram's own push infrastructure — no Firebase, no device tokens, no
  service account

**Built 2026-08-23** as `supabase/functions/telegram-digest/`, scheduled by
`sql/supabase-telegram-digest-cron.sql`, deployed by the existing Telegram
workflow (both functions use the same bot, so the secret is already there).

A *new* function rather than an edit of `send-push`, and the reason matters:
`send-push` queries `store in ('bills','tasks','assignments','documents')` with
one row per record, which was the web app's schema. The native app syncs one row
per Storage **key** under `store='kv'`, each holding a whole module's JSON blob in
`data.text` — so that query matches nothing the native app has ever written.
Sharing one file would have produced a function that is half dead code whichever
way it runs. `send-push` stays for Phase 2, which reuses its VAPID plumbing rather
than its query.

Two things beyond the brief. The pure half is split into `digest.ts` and unit
tested (16 cases, `node --test`), because this code mirrors six Kotlin data shapes
across a language boundary with no compiler holding the two sides together: rename
a field in Kotlin and the digest silently goes empty, and the failure mode is
silence, which nobody notices. The fixtures are the exact JSON the Kotlin
serializers emit, so a rename fails the test by name. And the digest stays quiet
on a day with nothing due — one that arrives every morning regardless is one you
stop reading.

**Phase 2 — FCM for per-item high-priority alerts.**

**Correction (2026-08-22): far more of this exists than first assessed.** Already
in the tree and verified working per `FUTURE_FEATURES.md` §7:
- `supabase/functions/send-push/index.ts` (141 lines) — reads due-soon items from
  `sync_records`, signs a VAPID payload, sends
- `supabase/functions/telegram-webhook/index.ts` (165 lines) — **a Telegram webhook
  already exists**; two-way Telegram is not blocked on a backend that was never built
- `sql/supabase-push-schema.sql` — `push_subscriptions` table with RLS
- `sql/supabase-push-cron.sql` — the cron schedule
- Deploys from GitHub Actions, no local tooling

**The gap is the client half only.** What exists is Web Push (browser subscription,
service worker, VAPID) — the native Kotlin app cannot receive it. Android needs FCM.

Remaining work:
- A Firebase project and service account key (key in Supabase secrets, never in the
  repo or client)
- Kotlin registers an FCM token on launch, stored against the account — replacing
  the Web Push subscription path
- Swap the send call inside the existing Edge Function; the due-items query, cron,
  schema and RLS all stay
- **Only FCM can carry notification actions** — Done / Snooze on the notification
  itself, without opening the app. That is the reason to build it despite Phase 1
  existing.
- New secrets-handling surface: the service account key lives in Supabase secrets,
  never in the repo or the client

**Built 2026-08-23, in two halves — and the first half was not the transport.**

The premise above is wrong on one point, which reading the tree settled: the action
buttons already existed. Every reminder notification has rendered a Done and a Snooze
since the notification path was written. Both called `nm.cancel(id)` and nothing else
— `NotificationActionReceiver`'s own comment admitted it ("a real timed snooze via
AlarmManager is a follow-up"). What was missing was not a transport that could carry
actions; it was the actions doing anything. That needs no Firebase at all, and it
landed first.

A notification now carries a **subject** — `"<storage key>|<record id>"`. The
resolution lives in `push/Actions.kt` in commonMain, not in the Android receiver, so
the same code serves a local alarm and an FCM message alike and can be tested without
a device. Tasks: Done finishes and stamps `completedDate`; Tomorrow snoozes and
re-arms. Time Capsules: one button, "Mark read". No subject: one button, "Dismiss" —
a Done that changes nothing should not be labelled Done. Bills are deliberately
absent: marking one paid writes a payment with an amount and a date, and doing that
from a lock screen without seeing the figure is not a convenience. Tasks also gained
alarms of their own (`tasks/Reminders.kt`), since the module most likely to hold
something you meant to do today had none, which left the buttons nothing to act on.

The second half is the FCM transport, and the *server* side of it is complete:

- `sql/supabase-fcm-schema.sql` — `fcm_tokens` (one row per device) and `fcm_sent`
  (one row per record per day), both with RLS. Not an edit of `push_subscriptions`:
  that table holds Web Push endpoints the Kotlin app cannot receive on.
- `supabase/functions/send-fcm/` — signs a service-account JWT (RS256, Web Crypto),
  trades it for a Google access token, and sends per-item pushes. It reads module
  blobs through `telegram-digest/digest.ts`, so there is one mirror of the Kotlin data
  shapes rather than three; `digest.ts` gained the record id so a push can name what
  it is about. 13 unit tests, including a real sign-and-verify round trip, gate the
  deploy — nothing else in this path can be exercised without a Firebase project.
- `sql/supabase-fcm-cron.sql` — hourly, versus the digest's once a morning, with each
  record sent at most once a day so frequency does not become noise.
- `push/Registration.kt` — uploads the device token at app open, remembers the last
  one under a reserved key so the ordinary case costs no request, and forgets it on
  sign-out.

**One correction worth recording:** FCM does not carry notification actions either.
There is no field for them in the v1 API. What it carries is a payload, and the app
builds the notification — which is why `send-fcm` sends **data-only** messages. With
a `notification` block Android draws the notification itself while the app is
backgrounded, the app never sees the payload, and the buttons never appear. The
buttons come from our own client code, keyed by the subject, on either transport.

**What is left, and it is only Alek's to do:** create a Firebase project with
`com.alekpeed.lifeos` registered in it, set `FCM_SERVICE_ACCOUNT` (the whole key file,
in Supabase secrets or as a GitHub Actions secret — never in this repo), and run the
two SQL files. The app-side follow-on is four steps written out in full at
`Native.android.kt`'s `devicePushToken`: `google-services.json` into
`native/composeApp/`, the `google-services` plugin plus `firebase-messaging`, a
`FirebaseMessagingService` whose `onMessageReceived` calls
`postReminder(title, body, subject)`, and the token fetch itself. The plugin **fails
the build** without `google-services.json`, which is why it is not applied now — a
dependency added early would be weight with nothing behind it. Until then
`devicePushToken` returns null, `send-fcm` reports "no registered devices", and the
local alarms and the Telegram digest are what reach you.

**Division of labor once both exist:**
- Telegram cron → the scheduled digest, both platforms
- FCM → individual urgent items with actions, phone only
- AlarmManager → retained for locally-known future events (bills, reminders,
  time capsules)

---

## 7.1 Review status

All four groups reviewed. Nothing outstanding.


---

## 8. Standing build order

30 of 34 items selected from the gap register on 2026-08-22. Deselected: M-08
maintenance intervals, A-05 Wear OS, A-06 location-based weather, P-02 task
importers.

**Schema-layer, must land before new modules** — building these after the new
modules means retrofitting across 49 modules instead of 40:

- **R-01** attachment durability — the blob store never syncs and never enters the
  backup export; every photo, PDF, ebook and scan is single-device
- **R-02 + R-03** soft delete and edit history — *these are one build.* A delete
  flag plus a mutation log is the `FUTURE_FEATURES` "event-sourced core" arriving
  through the back door. Doing them separately costs roughly double and produces
  two overlapping mechanisms. Doing them together also makes a real Time Machine
  possible.
  **Built 2026-08-23** as `history/History.kt` — one app-wide mutation log, hooked
  into `Storage.write`/`Storage.remove`, with a History module (Trash + Activity)
  for recovery. The flag was dropped: a flag *and* a log is exactly the two
  overlapping mechanisms this entry warns about, so soft delete is derived from the
  log instead — no `deleted` field on 38 data classes, and no filter to forget.
  `History.blobAt(key, at)` is the replay API the Time Machine rebuild needs.
- **M-01** calendar and time of day — Travel reservations depend on it, and the
  Notifications reminder engine is its existing seed
- **W-03** global tag taxonomy
  **Built 2026-08-23** as `tags/Tags.kt` plus a Tags module. The vocabulary is derived
  from the seven modules that carry tags rather than stored in a table of its own — a
  table drifts the moment the last record using a tag is deleted. `ui/TagField.kt`
  replaces the seven bespoke comma boxes and suggests tags already in use, which is what
  actually prevents duplicates; case is preserved rather than folded, with clashing
  spellings surfaced for merging instead of silently rewritten.
- **W-04** projects as first-class records
  **Built 2026-08-23** as `projects/Project.kt` plus a Projects module. `Task.project`
  (free text) becomes `Task.projectId`; the existing names migrate into records on first
  run, matched case-insensitively so two devices converge on one project rather than
  two. A project links documents, links, contacts and milestones by id — never copies —
  and its target date joins the shared Calendar query. Deleting a project releases its
  tasks; finishing one leaves its open tasks open.

Remaining: 4 L · 9 M · 17 S. The seventeen S items are largely independent and can
run in any order once the schema work is down.

**Added 2026-08-22 (§12.1.1):** M-01(b) Calendar should follow M-01(a) closely
rather than being deferred. Today, Daily Paper and Briefing each maintain their own
dated-items query; Calendar is meant to replace all three with one. Every month it
is delayed is another month those three drift apart, and the eventual work becomes
a refactor of four surfaces instead of a consolidation of three.

---

## 9. Resolved — delivery and M-01

### 9.1 Deliverable format — SPLIT BY TASK TYPE

**Deletion work order** — an executable section of this document (§10) that Claude
Code runs directly. Originally planned as git patches; changed because the workflow
is GUI-only with no terminal, and `git am` needs one. Covers:
- Deleting Orrery, Museum, Ghost Days, Station Cat
- Deleting NEXUS, Nocturne, Machiya
- `Interfaces.kt` — `BASELINE = DEFAULT`, `ActiveInterface` reset migration,
  removing the `NocturneHomeMigrated` key
- `Modules.kt` import and registry cleanup
- `data/Data.kt` `DataSource` cleanup
- `AlmanacScreen.kt` threshold constants
- Deleting Recall, after salvaging the ladder
- The `js/`, `vendor/`, `android/` and stale-document deletions

Rationale: exact, mechanical, small. Every path and string was verified against the
tree, so the work order can be followed literally.

**Spec documents** (markdown handoff files for Claude Code to implement locally):
- Travel (§5.1)
- Skill Trees, both tiers (§5.2)
- Collections overhaul (§5.3)
- Time Capsule surfacing (§5.4)
- Server push, both phases (§7 D-5)
- Wake word gating (§7 D-2)
- The schema-layer work: R-01, R-02/R-03, M-01, W-03, W-04

Rationale: thousands of lines against a live tree with a compiler. Claude Code runs
Gradle; this environment cannot.

### 9.2 M-01 — BOTH, in order

**(a) Time on existing date fields — first.** The shared `ui/DateField.kt` gains an
optional time component. A task can be due at 15:00; a flight departs at 07:25.
Touches every module, adds no new screen. **Hard prerequisite for Travel
reservations and for meaningful push scheduling.**

**(b) A Calendar module — second.** A real day / week / month agenda showing
everything dated across every module in one place. New module, new surface,
+1 to the module count (34 → 35).

The Notifications reminder engine (§2) is the existing seed for (a) — it is already
clock-aware with `epochMillisAt`, `nextClockTime` and real alarm scheduling. It is
promoted into this work rather than deleted.

---

---

## 10. Implementation — deletion work order

**This section is executable. Hand it to Claude Code and say: "execute section 10
of REDESIGN_DECISIONS.md."** Everything below was verified against the tree on
2026-08-22 — every path exists, every string to be replaced was matched exactly.

Work on a branch. Build with `./gradlew assembleDebug` in `native/` before merging.

---

### 10.1 Delete these files and directories

```
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/interfaces/nexus/
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/interfaces/nocturne/
native/composeApp/src/desktopMain/kotlin/com/alekpeed/lifeos/interfaces/machiya/
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/orrery/
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/museum/
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/ghostdays/
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/system/StationCatScreen.kt
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/insight/RecallScreen.kt
native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/insight/Recall.kt

js/
vendor/
android/
css/
icons/
index.html
manifest.json
service-worker.js
capacitor.config.json
package.json
package-lock.json
nexusred.png
nexustest.png

MUSIC_APP.md
CHORDS_APP_HANDOFF.md
CAPACITOR_BUILD.md
CAPACITOR_HANDOFF.md
PLAID_SPEC.md
PAGE_MAP.md
DESKTOP_PAGE_MAP.md
UI_INTERFACE_INVENTORY.md
MOBILE_INTERFACES_SPEC.md
SPATIAL_INTERFACES_SPEC.md
NOCTURNE_THEME.md

.github/workflows/build-android.yml
scripts/assemble-www.mjs
scripts/gen-icons.py
```

`build-android.yml` is the dead Capacitor workflow — it triggers on `index.html`,
`manifest.json` and `service-worker.js`, all deleted above. `build-native.yml` is
the live one; leave it alone.

**Do not delete** `native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/interfaces/Interfaces.kt`.
The registry and the `Render` fallback are retained so new interfaces can attach
later without touching module logic.

---

### 10.2 `Modules.kt` — remove imports and registry entries

Delete these six lines from
`native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/Modules.kt`:

```kotlin
import com.alekpeed.lifeos.ghostdays.GhostDaysScreen
import com.alekpeed.lifeos.museum.MuseumScreen
import com.alekpeed.lifeos.orrery.OrreryScreen
```

```kotlin
    Module("museum", "🏛", "Museum", "Archive", true) { MuseumScreen() },
    Module("ghost-days", "👻", "Ghost Days", "Archive", true) { GhostDaysScreen() },
    Module("orrery", "🪐", "Orrery", "Logistics", true) { OrreryScreen() },
```

Also remove the `RecallScreen` import and the `Module("recall", ...)` entry.

After this, `grep -c '^    Module(' Modules.kt` must return **37**.

---

### 10.3 `data/Data.kt` — remove the DataSource entries

Delete these four lines:

```kotlin
    DataSource("Orrery", "Orrery"),
    DataSource("Museum", "Museum"),
    DataSource("Ghost Days", "Ghost Days"),
    DataSource("Recall", "Recall"),
```

**These matter more than they look.** `DataSource` feeds Search, the Knowledge
Graph and Ask. Left in place, a search result routes to a screen that no longer
exists.

---

### 10.4 `Interfaces.kt` — reset the baseline

**This is the one edit with a live-data hazard.** Existing installs hold
`"nocturne"` (or `"machiya"` on Linux desktop) in the `ActiveInterface` key. Without
this change the app starts up pointing at an interface that no longer exists.

Replace the header comment block:

```kotlin
// screen. This is what keeps interfaces interchangeable: Alek designs a graphical
// interface, registers its per-module screens under an interface id, and every
// page can accept it without touching module logic or the data it persists.
```

with:

```kotlin
// screen. This is what keeps interfaces interchangeable: a graphical interface
// registers its per-module screens under an interface id, and every page can accept
// it without touching module logic or the data it persists.
//
// No graphical interfaces ship today (NEXUS, Nocturne and Machiya were removed
// 2026-08-22). This layer is retained deliberately so new ones can be attached later
// without touching any module. Every module must keep rendering through `Render`.
```

Replace the baseline and migration block:

```kotlin
    private const val K_NOCTURNE_HOME_MIGRATED = "NocturneHomeMigrated"

    // Nocturne is the baseline interface. Other graphical interfaces, including
    // NEXUS, remain registered and selectable in Settings.
    const val BASELINE = "nocturne"

    // Promote Nocturne once on existing installs so the new canonical home actually
    // becomes visible. After this one-time migration, user interface choices persist
    // normally and are not overwritten again.
    private var activeState by mutableStateOf(
        if (Storage.read(K_NOCTURNE_HOME_MIGRATED) != "1") {
            Storage.write(K_ACTIVE, BASELINE)
            Storage.write(K_NOCTURNE_HOME_MIGRATED, "1")
            BASELINE
        } else {
            Storage.read(K_ACTIVE)?.ifBlank { null } ?: BASELINE
        },
    )
```

with:

```kotlin
    private const val K_RESET_2026_08 = "InterfaceResetToDefault"

    // The functional interface is the baseline and, for now, the only one.
    const val BASELINE = DEFAULT

    // One-time reset. Existing installs hold "nocturne" or "machiya" in K_ACTIVE from
    // the removed graphical interfaces; without this they would start up pointing at an
    // interface that no longer exists. Runs once, then user choices persist normally.
    private var activeState by mutableStateOf(
        if (Storage.read(K_RESET_2026_08) != "1") {
            Storage.write(K_ACTIVE, DEFAULT)
            Storage.write(K_RESET_2026_08, "1")
            DEFAULT
        } else {
            Storage.read(K_ACTIVE)?.ifBlank { null } ?: DEFAULT
        },
    )
```

---

### 10.5 `Shell.kt` — remove interface registration

Delete two imports:

```kotlin
import com.alekpeed.lifeos.interfaces.nexus.registerNexusCommandRoom
import com.alekpeed.lifeos.interfaces.nocturne.registerNocturne
```

and this block from inside `fun Shell()`:

```kotlin
    // Make the graphical interfaces available for selection in Settings.
    remember {
        registerNexusCommandRoom()
        registerNocturne()
    }
```

---

### 10.6 `Main.kt` (desktopMain) — stop forcing Machiya on Linux

**Second live-data hazard.** This ran on *every* launch, not just the first, so it
overrode any interface choice on Linux desktop.

Delete three imports:

```kotlin
import com.alekpeed.lifeos.interfaces.Interfaces
import com.alekpeed.lifeos.interfaces.machiya.MACHIYA
import com.alekpeed.lifeos.interfaces.machiya.registerMachiyaHome
```

and this block from `fun main`:

```kotlin
    val isLinux = System.getProperty("os.name").contains("linux", ignoreCase = true)
    if (!AppMode.helper && isLinux) {
        registerMachiyaHome()
        Interfaces.setActive(MACHIYA)
    }
```

---

### 10.7 `FullscreenApplication.kt` (androidMain) — comment only

Two lines in the KDoc referencing Nocturne:

- `Compose's Nocturne screen also requests immersive mode, but activity-level`
  → `A graphical interface may also request immersive mode, but activity-level`
- `through Native.setImmersive(false) after leaving the Nocturne home.`
  → `through Native.setImmersive(false) after leaving such a home.`

---

### 10.8 `scripts/make-backup.sh` — remove a dangling path

Delete the line referencing `js/interfaces/vespera/img/README.txt`. It points at an
interface renamed a month ago and a directory deleted in §10.1.

---

### 10.9 New file — salvage the Recall ladder

Recall is deleted, but its interval ladder is the decay model the rebuilt Skill
Trees needs (§5.2). Create
`native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/skilltrees/Decay.kt`:

```kotlin
package com.alekpeed.lifeos.skilltrees

import com.alekpeed.lifeos.data.today
import kotlinx.datetime.LocalDate

// Interval ladder, salvaged from the removed Recall module (insight/Recall.kt).
//
// Recall used it for spaced repetition: answer correctly and the interval advances a
// rung, forget and it drops back to the start. That is the same shape skill atrophy
// takes — practice extends how long a skill holds, neglect collapses it — so the
// ladder is kept here rather than rewritten when Skill Trees gains practice tracking.
//
// Deliberately a fixed ladder rather than tracked ease factors: the rungs are legible,
// and nobody looks at an ease factor.
val PRACTICE_LADDER = listOf(1, 3, 7, 14, 30, 90)

// How long a skill holds before it should resurface, and when that falls due.
data class Freshness(val intervalDays: Int, val staleAfter: LocalDate)

fun Freshness.isStale(): Boolean = staleAfter <= today()

// A logged practice session advances the rung — the skill holds longer each time.
fun Freshness.practiced(): Freshness {
    val next = PRACTICE_LADDER.firstOrNull { it > intervalDays } ?: PRACTICE_LADDER.last()
    return Freshness(next, today().plusDays(next))
}

// Gone cold: back to the bottom rung. Recovering a lapsed skill starts over.
fun Freshness.lapsed(): Freshness =
    Freshness(PRACTICE_LADDER.first(), today().plusDays(PRACTICE_LADDER.first()))

fun freshnessStart(): Freshness =
    Freshness(PRACTICE_LADDER.first(), today().plusDays(PRACTICE_LADDER.first()))
```

This file is unreferenced until Skill Trees is rebuilt and will produce an unused
warning. That is expected.

Also update the comment in `data/Dates.kt` that reads
`(Tasks' due date, Habits' streak, Recall's schedule, Today/Briefing)` — replace
`Recall's schedule` with `skill freshness`.

---

### 10.10 `insight/AlmanacScreen.kt` — raise the sample-size floors

Replace:

```kotlin
private const val CORR_MIN = 5
private const val TREND_MIN = 5
private const val MONTHS_MIN = 3
private const val WEEKDAY_MIN_DAYS = 14
```

with:

```kotlin
// Minimum sample sizes. Raised 2026-08-22 — the previous floors (5/5/3/14) let a
// number reach the screen long before it carried any information, and a precise
// decimal on six days of self-reported data reads as authoritative when it isn't.
private const val CORR_MIN = 21
private const val TREND_MIN = 21
private const val MONTHS_MIN = 6
private const val WEEKDAY_MIN_DAYS = 42

// A straight line through two points is not a forecast, it is the line between them
// extended. Nothing fits under this.
private const val LINREG_MIN = 6
private const val READING_MIN = 4
```

Then replace the bare literals:

| Find | Replace with |
|---|---|
| `if (pairs.size < 2) return null` | `if (pairs.size < LINREG_MIN) return null` |
| `if (pts.size < 2) return null` | `if (pts.size < LINREG_MIN) return null` |
| `it.logs.size >= 2 }` | `it.logs.size >= READING_MIN }` |
| `if (logs.size < 2) return@mapNotNull null` | `if (logs.size < READING_MIN) return@mapNotNull null` |

**Still outstanding, not done here:** display sample size alongside each figure
(`r = 0.62 · 34 days`), so the trust judgement happens at the point of reading.

---

### 10.11 Verify

**Nothing in §10 has been compiled.** It was verified by exhaustive grep against
the tree, but grep is not a compiler. Build before merging.

```
grep -ri "orrery\|museum\|ghostdays\|stationcat\|recall\|nexus\|nocturne\|machiya" native/ --include="*.kt"
```

Expected survivors only: the explanatory comments in `Interfaces.kt` and
`skilltrees/Decay.kt`. Anything else is a dangling reference.

```
cd native && ./gradlew assembleDebug
```

**On device, after building:**

1. **The interface reset fires.** Launch on a device that ran a previous build. It
   must open on the plain functional interface. A blank or broken home means §10.4
   did not run. Check the Linux desktop build separately — §10.6 was a second path.
2. **Settings → Interface** lists only `default`.
3. **Search, Ask, Knowledge Graph** — run a query that previously matched something
   in a removed module. No result may route to a missing screen.
4. **Module count is 37.**
5. **The Almanac says "not enough data yet" more often.** Expected, and the point.
6. **`build-native.yml` still runs.** It is untouched; only the Capacitor workflow
   was deleted.

---

### 10.12 Expected result

| | Before | After |
|---|---|---|
| Modules | 40 | 37 |
| Kotlin | 27,425 lines | ~25,300 |
| JavaScript | 18,541 lines | 0 |
| Repo size | 40 MB | 12 MB |
| Root markdown files | 26 | 15 |

Suggested commit split, one per group, so a bisect lands somewhere useful:
interfaces · ornament modules · Recall + salvage · Almanac floors · PWA and docs.

---

### 10.13 Not included

Everything in §10 is deletion or constants. Nothing creates a module or changes a
data model.

Outstanding as specs (§9.1): Group A rewiring, Travel (§5.1), Skill Trees (§5.2),
Collections (§5.3), Time Capsules (§5.4), server push (§7 D-5), and the schema
layer — R-01, R-02/R-03, M-01, W-03, W-04.

**One hazard in the Group A work, restated because it destroys data if missed:**
Notifications owns a real `Storage` key holding standalone reminders attached to no
record. Do not delete that screen without migrating the key.

---

## 11. Additional features — selected 2026-08-22

Ten items chosen from a 50-item brainstorm. All extend existing modules — none is
a new module or changes the count in §6. Two overlaps surfaced during review and
are resolved below rather than built as written.

### 11.1 Contacts — merged expansion (was three separate items)

Gift tracker, recurring non-birthday dates, and a contact interaction log were
proposed separately. All three extend the same record and two were already
pending: **W-07** ("birthdays that do something," §8) and the original gap
register's **M-10** ("contact cadence"). Building them as one pass:

- **Recurring dates** — a list per contact, not just `birthday`: anniversary,
  adoption day, work anniversary, anything with a yearly cadence. Each entry can
  carry a lead time.
- **Gift tracker** — per occasion (a recurring date, or one-off): idea, budget,
  status (idea / bought / wrapped / given), linked to a Finance entry if paid.
  Idea list is reusable across years.
- **Interaction log** — a lightweight dated note per call, text or meetup.
  Separate from Milestones (that's for what mattered) and from journaling (that's
  about the user, not the other person).
- **Cadence** — derived from the interaction log: days since last contact, sortable,
  same neglect pattern Entropy already uses for modules, applied per person.

**Resolves 12.3's collision:** any gift idea *is* a wishlist entry. No separate
module.

### 11.2 Health — two independent additions

- **Mood log** — daily 1–5 scale, one entry per day, optional note. Feeds the
  Almanac's existing correlation engine (§7 D-4) as a fourth pair — mood vs.
  sleep, mood vs. habits — under the same raised floors (21+ paired days).
- **Medication schedule** — drug name, dose, schedule, a take/skip log. Deliberately
  small: this is the first slice of **M-05 Medical** (§8 build order), not the
  whole thing. Ships alone, folds into Medical when that lands rather than being
  rebuilt.

### 11.3 Wishlist — not built as a separate module

Proposed as a shareable list of wanted items. Collides with two things already
specced:

- An item wanted **for a collection** is `status: wanted` in the Collections
  overhaul (§5.3) — it already has a want-list view.
- An item wanted **as a gift** is a gift-tracker idea (§11.1).

A third, general-purpose wishlist would be a fourth place to look for the same
kind of record. Not building it. If a wishlist that isn't collection- or
occasion-specific turns out to be genuinely needed later, it is a filtered view
over Ideas (tagged `wishlist`), not a new record type.

### 11.4 Finance — unused subscription flag

Subscriptions already store `renewalDate` and `active`. Add `lastUsedDate`
(manually marked, since usage isn't otherwise tracked) and surface anything
untouched 60+ days as a Briefing row — reuses the same neglect pattern as Entropy
and the new Contacts cadence in §11.1, applied a third time to the same shape of
problem.

### 11.5 Books — reading highlights export

The in-app EPUB reader has no way to capture a passage. Add: select text while
reading, save as a highlight with a page/location reference, per book. An export
action compiles all of a book's highlights into one document — genuinely useful
only once highlighting exists, so this is two builds in sequence, not one.

### 11.6 Travel — end-of-trip recap

Extends Travel (§5.1), not a new module. Once a trip's `endDate` passes: a
generated summary of photos taken (from the linked Photos album), total spend
against the trip budget, and places visited that fell inside the date range.
Reuses the Yearly Recap's narrative pattern (`FEATURE_LIST.md`) at trip scale
instead of year scale.

**Built 2026-08-23** as `travel/TripRecap.kt` plus a Recap tab that only appears
once the trip's end date has passed — a recap of a trip you are still on is a
status report. Every number is derived from records that already exist (the
linked Photos album, `tripBudget`, `tripPlaces`, the live reservations), so
there are no new fields for the recap to disagree with later, and the model is
handed exactly the stat list the screen shows and nothing else.

---

## 12. Final architecture — 35 modules, 8 domains

Synthesis of every decision in §1–§12. This is the target state; §10 gets the
deletions there, §5 and §12 specify everything new or changed.

| Domain | Count | Modules |
|---|---|---|
| Operations | 5 | Today · Daily Paper · Tasks · Briefing (absorbed Notifications' feed) · Calendar (new, §9.2) |
| Archive | 7 | Documents · Links · Books (+ highlights, §11.5) · Photos · Collections (overhauled, §5.3) · Time Capsules (wired, §5.4) · Milestones |
| Logistics | 3 | Places · Quartermaster · Travel (new, absorbed Packing Lists, + trip recap §11.6) |
| Discovery | 5 | Education · Skill Trees (rebuilt, two tiers, §5.2) · Ideas · Rabbit Holes · Almanac (raised floors, §7 D-4) |
| Management | 4 | Habits · Health (+ mood log, + medication, §11.2) · Recipes · Finance (+ unused-subscription flag, §11.4) |
| Intelligence | 5 | Ask (+ Command's create function) · AI Assistant · Knowledge Graph · Entropy · Time Machine (rebuild gated on R-02/R-03) |
| People | 3 | Contacts (expanded: gifts, dates, cadence, §11.1) · Sharebox · QR Sync* |
| System | 3 | Search · Tools · Settings |

**Total: 35.** Cross-checks against §6: 40 − 6 cut (Orrery, Museum, Ghost Days,
Station Cat, Command screen, Notifications screen, Recall) + 1 (Calendar) = 35.

**\* QR Sync recategorized** — device pairing, not people. Listed under People only
because that is where the original build put it; move to System when §10 is
executed. Cosmetic, no functional change.

### 12.1 Consolidations — ACCEPTED 2026-08-22

Not visible reviewing modules individually; only once the final shape existed.
All four approved.

#### 12.1.1 Calendar becomes the single source of dated items — ACCEPTED

Today, Daily Paper, Briefing and Calendar all surface dated or due items. Three
were flagged at the start of this session; Calendar would add a fourth
independent query over the same data — rebuilding the redundancy this session
just cut, with an extra module attached.

**Decision:** when M-01(b) lands, Calendar owns the dated-items query. Today,
Daily Paper and Briefing become **filtered views over it**, not separate
computations.

- One function returns everything dated in a range, across every module
- Today = that function, today's range, grouped by urgency
- Briefing = that function, overdue and due-soon, with per-row actions
- Daily Paper = that function, today's range, rendered as prose
- Calendar = that function, arbitrary range, agenda layout

**Done 2026-08-23.** `calendar/Calendar.kt` owns `datedItems` (any range) and
`datedWorklist` (overdue plus due-soon). Today, Briefing and Daily Paper are all
filters over them now. The per-module horizons moved into `datedWorklist`, which
is where the drift actually was: Briefing gave bills `billDueSoonDays()` and
documents `docExpiryDays()`, Daily Paper gave bills their window but documents a
flat week, and Today gave bills a flat week and documents their window — three
answers to "is this bill due soon" depending on which screen you opened. The
horizon belongs to the module that owns the setting, not to whoever is drawing
the list. Notifications still walks its own; retiring that screen is its own item.

**Sequencing:** this is why M-01(b) should not be deferred far behind M-01(a). The
longer three separate implementations exist, the more expensive the convergence.
If Calendar is delayed, whoever builds it must refactor the other three, not add
a fourth. Recorded against the build order in §8.

#### 12.1.2 Shared staleness utility — ACCEPTED

Entropy (module neglect), Contacts cadence (§11.1) and Finance's
unused-subscription flag (§11.4) are the same computation — days since last
touch, sorted worst-first — heading for three implementations in three domains.
Skill Trees' decay (§5.2, `skilltrees/Decay.kt`) is a fourth variant, though a
deliberately different one: it uses a rung ladder rather than a flat threshold.

**Decision:** one shared utility, decided before the second implementation ships.

- Lives in `data/`, not in any module
- Takes a last-touched date and a threshold; returns days elapsed and a stale flag
- Called by Entropy, Contacts cadence and the subscription flag
- **`skilltrees/Decay.kt` stays separate.** Its ladder advances on success, which
  is a different model, not a special case of the flat threshold. Forcing them
  together would distort both.

#### 12.1.3 Skill Trees evidence inputs — ACCEPTED as a build constraint

Skill Trees reads from Habits, Education, Books and Tasks. Every additional
evidence source is another place the Standings/Skills wall (§5.2) can be blurred
during implementation.

**Decision:** written into the Skill Trees spec as a hard constraint —

> Any input may contribute **hours and evidence** to a skill. No input may move a
> skill's **level**. Levels move only when a benchmark is marked met. If a level
> ever changes because a task was completed, the implementation is wrong.

Applies to every future evidence source, not just the current four.

#### 12.1.4 Shared "moment" shape for Archive — ACCEPTED

Time Capsules and Milestones are the only single-moment records left in Archive
now that Ghost Days and Museum are gone. Both are a date, a title, a note and
optionally a photo.

**Decision:** a shared underlying shape, with the modules keeping their own
screens and semantics.

- Common fields: `date`, `title`, `note`, `photoBlob`
- **Milestones** adds: retrospective, visible immediately
- **Time Capsules** adds: `sealedUntil`, `readAt` (§5.4), hidden until unsealed
- Both surface through the same Briefing and Calendar paths once §12.1.1 lands

Not a module merge. One is future-facing, one is past — they stay distinct.

## 13. Future features — reviewed 2026-08-22

### 13.1 Cut permanently — do not resurface

Dead, not parked. No description retained by design; the names exist only so these
are not re-proposed.

- Generative "Year in Review" film
- Digital twin / life-simulation engine
- Bidirectionally-linked personal wiki
- Generative "personal mythology"
- ChatGPT panel
- Spotify listening stats
- 3D Memory Palace
- Android Auto voice capture
- All interface themes and concepts, including bio-futurism / jungle

Strike from `FUTURE_FEATURES.md` §0, §4, §5, §8, §9 and §13, and from any status
recap.

### 13.2 Interfaces — the only thing that survives

**All interface work is parked.** No themes, no graphical homes, no alternate
chrome. The default functional interface is the only interface until the app itself
is correct.

**What must be preserved:** `interfaces/Interfaces.kt` (82 lines) — the registry and
the `Interfaces.Render` fallback. An interface registers per-module screens under an
interface id; anything it does not supply falls back to the functional default.

This is the only interface-related requirement that matters now: **new interfaces
must remain attachable later without touching module logic or persisted data.**
Every module built from here — Travel, Collections, Skill Trees, Calendar — must
render through `Interfaces.Render` so it inherits that capability for free.

### 13.3 Still live

Carried forward, not scheduled.

- **Smart home / Home Assistant bridge** — one Home module over a single HA local
  API; HA speaks Tuya, BLE mesh, Zigbee and Matter to the gear. Covers the real
  hardware: Smart Life (Tuya Wi-Fi), DayBetter (mixed), BrMesh (Fastcon BLE via a
  ~$5 ESP32 bridge). Ties into arrival geofences. **Note: `FUTURE_FEATURES.md` §14
  flags a mixed-content wall stopping an HTTPS PWA reaching a local `http://` hub —
  that wall does not exist for the native app, which improves the case.**
- **Zero-knowledge encrypted vault** — end-to-end encryption at rest; the sync
  server holds ciphertext it cannot read.
- **Garmin / Fitbit ingestion** — OAuth health imports. Apple Health already ships.
- **Personal local API + plugin SDK** — a documented interface to build against
  without touching core.
- **Autonomous chief-of-staff** — Briefing's unbuilt half: acting without asking,
  for pre-approved categories. A trust and design problem more than an engineering
  one.
- **Trained ML pattern engine** — beyond Pearson on curated pairs; a model that
  retrains on full history and surfaces non-linear multi-variable patterns.

### 13.4 Still parked

- **Financial Center (Plaid)** — bank and investment linking. Sandbox free;
  production roughly $0.30–$3 per connected account per month. Needs its own backend
  token exchange. The most security-sensitive item remaining.


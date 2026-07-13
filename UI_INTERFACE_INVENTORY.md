# Life OS — UI & Interface Inventory

**Purpose of this document:** a factual inventory of the existing Life OS application (a vanilla-JS, no-build-step, offline-first PWA backed by IndexedDB), for use by another AI designing a **new mobile interface**. It documents what exists today — screens, actions, live data, states, constraints — with file:line citations. It does **not** propose visual design, layout, or styling. Where something is unclear from the code, it is explicitly marked `UNCERTAIN`.

Source repo: `/home/user/lifeos`. Architecture reference: `ARCHITECTURE.md`. The app currently ships two interfaces over the same data — **Equator** (the default rail+canvas interface; this doc's screenshots of behavior come from its view files under `js/interfaces/default/views/`) and **Vespera** (a spatial alternate, desktop-only). A third, a planned mobile-only remote, was in scoping at the time this doc was written (see `MOBILE_INTERFACES_SPEC.md`) — the module curation described in this doc (the `remote: true` flag) is the already-negotiated boundary for that project, and is reused here as the most defensible existing signal for "primary vs. secondary" mobile navigation. **Editorial note, added after this doc's original delivery:** a first mobile interface (`mobile-1`) has since been built from Alek's own mockup — this doc predates it and was never updated against it, so treat its "planned mobile-only remote" framing as historical rather than current.

Every module's view is a single function `render<Name>(canvas, ctx, rerender)` registered in `js/interfaces/view-library.js`, hosted inside whichever interface's chrome is active. `ctx.data` is the shared data API (`js/data/api.js`); `ctx.navigate(moduleId)` changes the hash route; `ctx.events` is a pub/sub for data-change topics (`js/data/events.js`). Every write to an entity store bumps `updatedAt` and emits an event; nothing polls.

---

## Table of contents

1. [Application structure](#1-application-structure)
2. [Navigation inventory](#2-navigation-inventory)
3. [Actions and buttons](#3-actions-and-buttons)
4. [Dashboard data inventory](#4-dashboard-data-inventory)
5. [Dynamic states](#5-dynamic-states)
6. [Content rules](#6-content-rules)
7. [Existing design and technical constraints](#7-existing-design-and-technical-constraints)
8. [Recommended component map](#8-recommended-component-map)
9. [Feature preservation checklist](#9-feature-preservation-checklist)
10. [Machine-readable appendix](#10-machine-readable-appendix)

---

## 1. Application structure

The app is a flat set of **43 modules**, each a single top-level nav destination (`#/<moduleId>`) — there is no deeper URL structure (`js/shell.js:19-27` parses only the first hash segment as `module`, the rest as an opaque `rest` array most modules ignore). Modules are grouped for nav-label purposes only (`js/modules.js:60-66`): **Core** (9), **Life** (15), **Study** (3), **Health** (5), **Utility** (11).

Every module's source file lives at `js/interfaces/default/views/<id>.js` and is registered in `js/interfaces/view-library.js:59-103`. "Whether it must be directly accessible from the main interface" below reflects the existing `remote: true` flag in `js/modules.js:14-58` — the already-negotiated set of 21 modules meant for the mobile remote's curated nav (see `MOBILE_INTERFACES_SPEC.md`'s "Draft: what actually ships on the remote"). This is a real, already-made product decision, not a guess.

| # | Module (display name) | Route | Source file | Purpose | Directly accessible on mobile? |
|---|---|---|---|---|---|
| 1 | Today (Dashboard) | `#/dashboard` | `views/dashboard.js` | Due-soon agenda, on-this-day, weather, "Surprise me" — the landing screen | **Yes** (remote) |
| 2 | Orrery | `#/orrery` | `views/orrery.js` | Alternate dashboard: life areas as a solar system (neglect=orbit, activity=speed) | No |
| 3 | Daily Paper | `#/paper` | `views/paper.js` | Newspaper-style daily brief + AI editorial | **Yes** (remote) |
| 4 | Tasks | `#/tasks` | `views/tasks.js` | Projects/areas, priority, due dates, recurring, subtasks, kanban | **Yes** (remote) |
| 5 | Ideas | `#/ideas` | `views/ideas.js` | Unstructured quick-capture notes, voice dictation, promote to Task | **Yes** (remote) |
| 6 | Places | `#/places` | `views/places.js` | Visited/want-to-go/map/bucket-list, geofenced notes, nearby nudge | **Yes** (remote) |
| 7 | Links | `#/links` | `views/links.js` | YouTube watch-later + article read-later | **Yes** (remote) |
| 8 | Education | `#/education` | `views/education.js` | Semesters→Courses→Assignments, GPA, academic pacing check | No |
| 9 | Finance | `#/finance` | `views/finance.js` | Bills, Subscriptions, Yearly Spend, crypto/DJIA tickers | No |
| 10 | Books | `#/books` | `views/books.js` | Reading list, EPUB/PDF/text reader, shelf view, stats | No |
| 11 | Recipes | `#/recipes` | `views/recipes.js` | Ingredients/steps, cook log, grocery-list generator | **Yes** (remote) |
| 12 | Documents | `#/documents` | `views/documents.js` | Document vault + camera-to-data AI scan | **Yes** (remote) |
| 13 | Contacts | `#/contacts` | `views/contacts.js` | Address book; single source of truth for people app-wide | **Yes** (remote) |
| 14 | Milestones | `#/milestones` | `views/milestones.js` | Life-events timeline + AI yearly recap | No |
| 15 | Photos | `#/photos` | `views/photos.js` | Albums, lightbox, Google Photos import | **Yes** (remote) |
| 16 | Sharebox | `#/sharebox` | `views/sharebox.js` | Small space shared with a friend (Drive v1 or Supabase v2) | No |
| 17 | Museum | `#/museum` | `views/museum.js` | Read-only trophy case of finished things across modules | No |
| 18 | Time Capsules | `#/timecapsules` | `views/timecapsules.js` | Sealed note to your future self | No |
| 19 | Collections | `#/collections` | `views/collections.js` | Freeform collection + item tracker | No |
| 20 | Packing Lists | `#/packing` | `views/packing.js` | Per-trip checklist with templates | **Yes** (remote) |
| 21 | Quartermaster | `#/quartermaster` | `views/quartermaster.js` | Physical inventory + lending ledger | **Yes** (remote) |
| 22 | Ghost Days | `#/ghostdays` | `views/ghostdays.js` | Read-only "on this day in history" across modules | No |
| 23 | Conversation Starters | `#/starters` | `views/starters.js` | Openers generated from a picked contact | **Yes** (remote) |
| 24 | Rabbit Holes | `#/rabbitholes` | `views/rabbitholes.js` | Research-tangent journal with links | No |
| 25 | Languages | `#/languages` | `views/languages.js` | Flashcard SRS decks + AI-generated reading library | **Yes** (remote) |
| 26 | Chords | `#/chords` | `views/chords.js` | Music-theory study tool: dictionary, Barry Harris, calculator, Harmony Map, lessons, practice drills, synth Sound tab | No |
| 27 | Life as Music | `#/lifeasmusic` | `views/lifeasmusic.js` | Sonifies your stats into a short ambient progression | No |
| 28 | Habits | `#/habits` | `views/habits.js` | Streak check-in for anything | **Yes** (remote) |
| 29 | Health | `#/health` | `views/health.js` | Manual sleep/workout/water/weight log + Apple Health import | **Yes** (remote) |
| 30 | Skill Trees | `#/skilltree` | `views/skilltree.js` | Read-only RPG-style stats computed from real activity | No |
| 31 | Dream Journal | `#/dreamjournal` | `views/dreamjournal.js` | Dream entries + recurring-pattern detection | No |
| 32 | The Almanac | `#/almanac` | `views/almanac.js` | Correlations, forecasts, what-if sandbox | No |
| 33 | AI Assistant | `#/assistant` | `views/assistant.js` | Provider-switchable (Gemini/Claude) chat | No |
| 34 | Knowledge Graph | `#/knowledge` | `views/knowledge.js` | Link any record to any other record; AI-suggested edges | No |
| 35 | Recall | `#/recall` | `views/recall.js` | App-wide spaced-repetition resurfacing | **Yes** (remote) |
| 36 | Time Machine | `#/timemachine` | `views/timemachine.js` | Scrub to a past date, see approximate state | No |
| 37 | QR Sync | `#/qrsync` | `views/qrsync.js` | Device-to-device WebRTC pairing/sync, no server | **Yes** (remote) |
| 38 | Entropy | `#/entropy` | `views/entropy.js` | Read-only neglect score per module | No |
| 39 | Station Cat | `#/stationcat` | `views/stationcat.js` | Cosmetic companion whose mood reflects recent activity | No |
| 40 | Theme from Photo | `#/themefromphoto` | `views/themefromphoto.js` | Extract an accent-color palette from a photo | No |
| 41 | Tools | `#/tools` | `views/tools.js` | Currency/unit converter, saved timezones | **Yes** (remote) |
| 42 | Search | `#/search` | `views/search.js` | One query across every module | **Yes** (remote) |
| 43 | Settings | `#/settings` | `views/settings.js` | Theme/density/accent, Account, sync, AI, App Lock, Automations, Telegram | **Yes** (remote) |

**Modals / drawers / overlays (not top-level routes):** across all 43 modules there are only two true overlay components:
- **EPUB/PDF/text reader** (Books) — `js/interfaces/default/views/reader.js`, opened via `openReader()`, a full-screen-ish `.mer-reader` overlay with its own TOC drawer.
- **Photo lightbox** (Photos) — `views/photos.js` — full-size single-photo view with prev/next, closes on background click or `✕`.

Every other "detail" view (a Task's edit panel, a Place's detail, a Contact's card, a Chord Harmony Map's node-detail panel, etc.) is an **inline in-canvas panel**, not an overlay — it's appended into the same scrollable canvas below the list, not a stacked modal. This is a consistent, deliberate pattern across the entire app (every module researched confirms it). The only native browser dialogs used are `confirm()` for destructive deletes (inconsistently applied — see §9) and `alert()` for simple errors.

**Major cross-cutting workflows** (span multiple modules, not owned by one screen):
- **Camera-to-data document scan** (Documents) — photograph → AI drafts fields → opens as an editable record. The one AI-vision capture flow in the app today.
- **Global Search** (`ctx.data.globalSearch`, `js/data/api.js:1068-1080`) — one query across 16 stores, results grouped by module, capped at 200.
- **Knowledge Graph** — link any two searchable records; reuses Search's same "what's linkable" definition.
- **Recall** — schedule any searchable record for spaced-repetition review; also reuses Search's definition.
- **Rules & Automations** (`runAutomations`, `js/data/api.js:1343-1350`) — two silent, off-by-default background rules run on every app boot: habit-streak milestone logging, and document-renewal task creation. Not a user-facing screen of their own; toggled in Settings.
- **Google Drive / Calendar / Sharebox sync** — background sync engines, triggered from Settings, not their own screens.

---

## 2. Navigation inventory

All 43 destinations are flat, top-level, and reached identically (`ctx.navigate(moduleId)` → `#/<moduleId>`). There is currently no "contextual-only" screen that isn't also a full top-level destination — every inline detail panel is part of its parent module's route, not a separate one. **Nav category** below is derived from the existing `remote` flag (`js/modules.js`) for Primary/Secondary, and the `settings` module id for Settings; there is no existing "contextual" nav category to report (flagging per instructions rather than inventing one).

Icon concept columns reference the **already-built** icon set at `js/interfaces/default/icons.js` (43 original SVG line icons, one per module, built from plain primitives — see §7). "Existing label" is blank where it matches the recommended label exactly (`js/modules.js:14-58` is both the existing and only label source — there is no separate "recommended" label elsewhere in the app; recommended = existing throughout, since this document does not invent new labels).

| Stable ID | Label | Route | Opens | Badge/status data available | Nav category |
|---|---|---|---|---|---|
| `dashboard` | Today | `#/dashboard` | Dashboard view | Due-soon count, overdue flag (from `getDueSoonFeed`) | Primary |
| `orrery` | Orrery | `#/orrery` | Orrery view | Per-area overdue pulse (from same due-soon logic) | Secondary |
| `paper` | Daily Paper | `#/paper` | Daily Paper view | None (editorial cached per day, no badge) | Primary |
| `tasks` | Tasks | `#/tasks` | Tasks view | Overdue/due-soon count via `getDueSoonFeed` | Primary |
| `ideas` | Ideas | `#/ideas` | Ideas view | Active idea count | Primary |
| `places` | Places | `#/places` | Places view | Nearby-nudge result count (only after manual "check nearby") | Primary |
| `links` | Links | `#/links` | Links view | Unread/unwatched count possible (not currently badge-rendered anywhere) | Primary |
| `education` | Education | `#/education` | Education view | Assignment due-soon/overdue count; **pacing-gap count** (`getAssignmentPacingGaps`) | Secondary |
| `finance` | Finance | `#/finance` | Finance view | Unpaid bill due-soon/overdue count | Secondary |
| `books` | Books | `#/books` | Books view | Reading-streak (days) | Secondary |
| `recipes` | Recipes | `#/recipes` | Recipes view | None | Primary |
| `documents` | Documents | `#/documents` | Documents view | Expiring/expired count via `getDueSoonFeed` | Primary |
| `contacts` | Contacts | `#/contacts` | Contacts view | Upcoming-birthday count possible (not currently badge-rendered) | Primary |
| `milestones` | Milestones | `#/milestones` | Milestones view | None | Secondary |
| `photos` | Photos | `#/photos` | Photos view | None | Primary |
| `sharebox` | Sharebox | `#/sharebox` | Sharebox view | Unread/urgent-item count possible (urgency field exists, not badge-rendered) | Secondary |
| `museum` | Museum | `#/museum` | Museum view (read-only) | None | Secondary |
| `timecapsules` | Time Capsules | `#/timecapsules` | Time Capsules view | Sealed-count / newly-opened count possible | Secondary |
| `collections` | Collections | `#/collections` | Collections view | None | Secondary |
| `packing` | Packing Lists | `#/packing` | Packing Lists view | Packed-progress `{n}/{m}` per trip | Primary |
| `quartermaster` | Quartermaster | `#/quartermaster` | Quartermaster view | Lent-out count | Primary |
| `ghostdays` | Ghost Days | `#/ghostdays` | Ghost Days view (read-only) | "Ghosts today" count | Secondary |
| `starters` | Conversation Starters | `#/starters` | Starters view | None | Primary |
| `rabbitholes` | Rabbit Holes | `#/rabbitholes` | Rabbit Holes view | Active (unresolved) count | Secondary |
| `languages` | Languages | `#/languages` | Languages view | Cards-due count per deck | Primary |
| `chords` | Chords | `#/chords` | Chords view | Weak-concept count (Practice tab) | Secondary |
| `lifeasmusic` | Life as Music | `#/lifeasmusic` | Life as Music view | None | Secondary |
| `habits` | Habits | `#/habits` | Habits view | Done-today `{n}/{total}` count | Primary |
| `health` | Health | `#/health` | Health view | "Logged today?" boolean possible (not currently badge-rendered) | Primary |
| `skilltree` | Skill Trees | `#/skilltree` | Skill Trees view (read-only) | Character level | Secondary |
| `dreamjournal` | Dream Journal | `#/dreamjournal` | Dream Journal view | None | Secondary |
| `almanac` | The Almanac | `#/almanac` | Almanac view | None | Secondary |
| `assistant` | AI Assistant | `#/assistant` | AI Assistant view | None (no unread concept — synchronous chat) | Secondary |
| `knowledge` | Knowledge Graph | `#/knowledge` | Knowledge Graph view | None | Secondary |
| `recall` | Recall | `#/recall` | Recall view | Due-for-review count | Primary |
| `timemachine` | Time Machine | `#/timemachine` | Time Machine view (read-only) | None | Secondary |
| `qrsync` | QR Sync | `#/qrsync` | QR Sync view | None | Primary |
| `entropy` | Entropy | `#/entropy` | Entropy view (read-only) | Overall neglect-days average | Secondary |
| `stationcat` | Station Cat | `#/stationcat` | Station Cat view (cosmetic) | Mood (derived, not a count) | Secondary |
| `themefromphoto` | Theme from Photo | `#/themefromphoto` | Theme from Photo view | None | Secondary |
| `tools` | Tools | `#/tools` | Tools view | None | Primary |
| `search` | Search | `#/search` | Search view | None | Primary |
| `settings` | Settings | `#/settings` | Settings view | Sync/App-Lock/Account connection status (see §3) | Settings |

**Cross-navigation targets that are NOT top-level nav items** (reached only via an in-app link, never from the main nav):
- `assistant` ← "Go to Settings" link from a no-API-key gate (goes the other way: Assistant → Settings).
- `knowledge` ← "Open `<Module>` →" button, target is whatever module the focused graph node belongs to.
- `recall` ← "Open →" button on a due review card, target is the scheduled record's module.
- Search results ← click any result row → `ctx.navigate(result.module)` (module only, not the specific record — see §9 for the deep-linking caveat).

---

## 3. Actions and buttons

Organized by module group, then module. Every action below was read directly from its view file; stable IDs follow the `<module>.<action>` convention. "Result" describes what happens after the action fires — almost universally a `rerender()` of the same in-canvas view, never a route change, unless noted.

### Core

#### dashboard (`views/dashboard.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `dashboard.surpriseMe` | 🎲 Surprise me | `ctx.data.getSurpriseMe()` | none | Shows a random pick or "nothing in the queue" (dashboard.js:10-14) |
| `dashboard.goToSurprise` | Go there → | `ctx.navigate(surprise.module)` | none | Navigates to the picked module (dashboard.js:28) |
| `dashboard.useMyLocation` | 📍 Use my location | `navigator.geolocation.getCurrentPosition` → `Settings.set('weatherLocation', ...)` + clear `weatherCache` | none | Weather section starts showing conditions (dashboard.js:37-49) |
| `dashboard.removeWeather` | × (Remove weather) | `Settings.set('weatherLocation', null)` + clear cache | none | Reverts to "No weather set" (dashboard.js:57-64) |

#### orrery (`views/orrery.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `orrery.flyToPlanet` | click/Enter a planet (or a "flight log" row) | `ctx.navigate(area.module)` | none | Jumps to that life area's module (orrery.js:113-116, 158) |

No create/edit/delete actions — fully read-only, animated visualization (orrery.js:1-173 read in full).

#### paper (`views/paper.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `paper.toggleChecklistItem` | checkbox per docket item | `Settings.set('paperChecklistDate'/'paperChecklistChecked')` | item key | Ticks off today's agenda item (paper.js:63-71) |
| `paper.toggleHabit` | checkbox per habit | `HabitLogs.create`/`.remove` | habitId | Same check-in mechanic as Habits module (paper.js ~130-138) |
| `paper.rerollPick` | Another → | `ctx.data.getSurpriseMe()` | none | New Editor's Pick (paper.js:161-164) |
| `paper.regenerateEditorial` | 🔄 Regenerate / Retry | clears cached editorial Settings keys, re-triggers AI call | none | Re-writes the AI editorial (paper.js:220-227) |
| `paper.print` | 🖨️ Print | builds a `.mer-print-sheet`, `window.print()` | none | Browser print-to-PDF of the paper (paper.js:347-354) |
| `paper.sendToTelegram` | Send to Telegram | `ctx.data.sendDigestToTelegram(text)` | plain-text digest | Pushes today's brief to Telegram (paper.js ~420-430) |

#### tasks (`views/tasks.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `tasks.create` | + New task — type a title and press Enter | `Tasks.create({title, status:'open'})` | title | New task in Inbox |
| `tasks.toggleDone` | checkbox on row | `Tasks.update(id, {status})` | open↔done | Strikes through / unstrikes |
| `tasks.select` | click row | toggles `state.selectedTaskId` | — | Opens/closes detail panel |
| `tasks.editField` | Project/Status/Priority/Due date/Repeats/Waiting-on/Tags/Notes fields | `Tasks.update(id, {field})` | field value | Persists edit |
| `tasks.snoozeTomorrow` / `tasks.snoozePlusWeek` / `tasks.clearSnooze` | Snooze buttons | `Tasks.update(id, {snoozedUntil})` | date | Hides from due lists until date |
| `tasks.addChecklistItem` | Add checklist item and press Enter | `Tasks.update(id, {subtasks: [...]})` | text | New subtask row |
| `tasks.toggleChecklistItem` | checkbox on subtask | `Tasks.update(id, {subtasks})` | subtaskId | Marks subtask done |
| `tasks.removeChecklistItem` | × on subtask | `Tasks.update(id, {subtasks})` | subtaskId | Removes it |
| `tasks.delete` | Delete task | `Tasks.remove(id)` | — | Removes task, closes panel |
| `tasks.newProject` | + Project | `Projects.create({name})` | name | New project for grouping |
| `tasks.viewToggle` | List / Kanban | client-side only | — | Switches layout |
| `tasks.toggleShowSnoozed` | Show snoozed checkbox | client-side filter | — | Reveals snoozed tasks |

#### ideas (`views/ideas.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `ideas.dictate` | 🎤 (mic button, only if `SpeechRecognition` available) | browser Speech Recognition API → fills textarea | — | **Not private**: uses cloud speech recognition in Chrome, not on-device (ideas.js:12-18) |
| `ideas.save` | + Save idea (or Ctrl/Cmd+Enter) | `Ideas.create({text, archived:false})` | text | Adds to active list |
| `ideas.promoteToTask` | → Task | `Tasks.create({title:idea.text})` + `Ideas.update(id,{archived:true})` | — | Converts idea into a real task |
| `ideas.archive` / `ideas.unarchive` | Archive / Unarchive | `Ideas.update(id, {archived})` | — | Moves between active/archived lists |
| `ideas.delete` | × (confirm) | `Ideas.remove(id)` | — | Deletes permanently |
| `ideas.toggleShowArchived` | Show/Hide archived (N) | client-side | — | Reveals archived list |

#### places (`views/places.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `places.create` | + New place / + New place to visit | `Places.create({name, listType})` | name | New card in Visited or Want-to-Go |
| `places.checkNearby` | 📍 Check nearby places | `navigator.geolocation` → haversine distance vs. all places' lat/lng within 1000m | — | Shows want-to-go/revisit/note nudges within range (places.js:363-408) |
| `places.dismissNearbyBanner` | × on nearby banner | client-side | — | Clears the nudge banner |
| `places.select` | click card | toggles `state.selectedPlaceId` | — | Opens detail panel |
| `places.editField` | Name/Category/List/Address/Lat/Lng/Notes | `Places.update(id, {field})` | value | Persists |
| `places.useMyLocation` | Use my location | geolocation → `Places.update(id,{lat,lng})` | — | Fills coordinates |
| `places.rate` | star widget (1-5, click same to clear) | `Places.update(id,{rating})` | 0-5 | Visited-only |
| `places.toggleRevisit` | Want to revisit checkbox | `Places.update(id,{revisit})` | bool | Feeds nearby-nudge logic |
| `places.addVisitDate` / `places.removeVisitDate` | date input / × on chip | `Places.update(id,{visitDates})` | date | Visited-only |
| `places.addNoteToSelf` / `places.removeNoteToSelf` | + New note-to-self / × | `PlaceNotes.create`/`.remove` | text | Geofenced note, resurfaces via nearby-check |
| `places.linkContact` / `places.unlinkContact` / `places.quickCreateContact` | select/×/+New contact | `Places.update(peopleIds)`, may also `Contacts.create` | — | Links a real Contacts record |
| `places.addPhoto` / `places.removePhoto` | + Add photo / × | `createAttachment`/`Attachments.remove` | file | — |
| `places.delete` | Delete place | `Places.remove(id)` | — | — |
| `places.tabSwitch` | Visited / Want to Go / Map / Bucket List | client-side | — | — |
| `places.bucketAdd` / `places.bucketToggle` / `places.bucketRemove` | quick-add / checkbox / × | `BucketListItems.*` | — | Separate goal list |

#### links (`views/links.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `links.create` | + Paste a YouTube/article link and press Enter | `Links.create({type,url,...})`; auto-derives YouTube thumbnail from URL | url | New card; type fixed by active tab |
| `links.select` | click card | toggles selection | — | Opens detail |
| `links.editField` | Title/Tags/Share-with | `Links.update(id,{field})` | value | — |
| `links.toggleWatchedRead` | checkbox | `Links.update(id,{status})` | unread↔done | — |
| `links.delete` | Delete link | `Links.remove(id)` | — | — |
| `links.tabSwitch` | YouTube / Articles | client-side | — | — |
| `links.toggleShowDone` | Show watched/read checkbox | client-side | — | — |

#### education (`views/education.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `education.createSemester` / `Course` / `Assignment` | quick-add inputs | `Semesters/Courses/Assignments.create` | name/title | Drill-down hierarchy |
| `education.selectSemester`/`Course`/`Assignment` | click card/row | navigates drill-down `state` | — | — |
| `education.editCourseField` | Name/Credits/Grade/Reading-tag/Notes | `Courses.update` | value | Grade feeds GPA calc |
| `education.addKeyDate` / `removeKeyDate` | label+date / × | `Courses.update({keyDates})` | — | — |
| `education.deleteCourse` / `deleteSemester` / `deleteAssignment` | Delete buttons | `.remove()` | — | — |
| `education.toggleAssignmentDone` | checkbox on row | `Assignments.update({status})` | — | — |
| `education.editAssignmentField` | Title/Due/Status/Time/Grade/%complete | `Assignments.update` | value | Grade feeds time-vs-grade table |
| `education.setPacingTarget` / `setPacingUnit` | Total due / Unit select | `Assignments.update({pacingTarget,pacingUnit})` | number/'pages'\|'words' | Enables pacing math |
| `education.addPacingCheckpoint` / `removePacingCheckpoint` | + Add checkpoint / × | `Assignments.update({paceCheckpoints})` | date+target | Self-set "N by date" |
| `education.logProgress` / `deleteProgressLog` | Log session / × | `AssignmentProgressLogs.create`/`.remove` | unitsAdded | Feeds pacing-gap chip and Daily Paper fact |
| `education.tabSwitch` | Coursework / GPA & Time | client-side | — | — |

#### finance (`views/finance.js`)
| ID | Label | Calls | Params | Result |
|---|---|---|---|---|
| `finance.createBill` / `createSubscription` | quick-add inputs | `Bills.create`/`Subscriptions.create` | name | — |
| `finance.toggleBillPaid` | checkbox | `BillPayments.create` (+ recurrence roll-forward) then `Bills.update({paid})` | bool | Logs a payment; recurring bills auto-advance `dueDate` |
| `finance.selectBill`/`Subscription` | click row | toggles selection | — | Opens detail |
| `finance.editBillField`/`editSubscriptionField` | Name/Category/Amount/Due/Repeats/Autopay/Billing/Renewal/Notes/Still-using | `.update(...)` | value | — |
| `finance.linkContact` | contact field | `contactLinkField` shared helper | — | — |
| `finance.addAttachment` / `removeAttachment` | file input / × | `createAttachment`/`Attachments.remove` | PDF/image | Bill PDFs |
| `finance.logPayment` / `deletePayment` | Log payment / × | `BillPayments.create`/`.remove` | amount/date/method | Manual payment history entry |
| `finance.deleteBill` / `deleteSubscription` | Delete buttons | `.remove()` | — | — |
| `finance.addCoin` / `removeCoin` | + Add coin / × | `Settings.set('cryptoWatchlist', ...)` | CoinGecko slug | — |
| `finance.refreshCrypto` | 🔄 Refresh | clears `cryptoPricesCache` | — | Forces re-fetch |
| `finance.tabSwitch` | Bills / Subscriptions / Yearly Spend / Markets | client-side | — | — |
| `finance.filterCategory` / `toggleShowPaid`/`toggleShowCancelled` | selects/checkboxes | client-side | — | — |

### Life

#### books (`views/books.js`)
`books.create`, `.select`, `.editField` (title/author/genre/status/totalPages/currentPage/dates/notes — status change to reading/finished auto-stamps start/finish date), `.rate` (1-5 stars), `.delete`, `.logReadingSession` (→ `ReadingLogs.create` + bumps `currentPage`), `.deleteReadingLog`, `.addCover`/`.removeCover`, `.addFile`/`.removeFile` (EPUB/PDF/text), `.readFile` (opens reader overlay, tracks `readingLocation`), `.downloadFile`, `.tabSwitch` (Reading/To Read/Finished/Shelf/Stats).

#### recipes (`views/recipes.js`)
`recipes.create`, `.select`, `.editField` (title/servings/tags/notes), `.delete`, `.addCover`/`.removeCover`, `.editIngredient`/`.addIngredient`/`.removeIngredient`, `.scaleServings` (display-only, not persisted), `.addStep`/`.removeStep`, `.logCook` (→ `CookLogs.create`), `.deleteCookLog`, `.grocery.toggleRecipe`/`.setServings` (in-memory grocery-list picker, **not persisted across reload** — see §9), `.tabSwitch` (Recipes/Grocery List).

#### documents (`views/documents.js`)
`documents.create`, `.filterCategory`, `.select`, `.editField` (title/category/issuer/policyNumber/expiryDate/notes), `.linkContact` (shared `contactLinkField`), `.delete`, `.addAttachment`/`.removeAttachment`, **`documents.scan`** — 📷 Scan a document, `capture=environment` camera input → `ctx.data.extractDocumentFromImage(file)` (AI vision) → drafts a record → `Documents.create(draft)` → attaches the photo → opens for editable review. The headline reason this module is slated for the mobile remote.

#### contacts (`views/contacts.js`)
`contacts.create`, `.search`, `.filterTag`, `.select`, `.editField` (name/company/jobTitle/relationship/birthday/tags/address/notes), `.addPhoto`/`.removePhoto`, `.editPhone`/`.editEmail`/`.addPhone`/`.addEmail`/`.removePhone`/`.removeEmail` (repeatable arrays), `.delete`. Contacts is also the **target** of quick-create actions from Places/Documents/Finance's shared `contactLinkField` helper (`dom.js:83-108`).

#### milestones (`views/milestones.js`)
`milestones.create`, `.select`, `.editField` (title/date/category/notes), `.delete`, `.addPhoto`/`.removePhoto`, `.tabSwitch` (Timeline/Yearly Recap), `.recap.setYear`, `.recap.generateNarrative` (auto-fires if a key is set and no cached narrative for that year+account), `.recap.regenerateNarrative`.

#### photos (`views/photos.js`)
`photos.createAlbum`, `.selectAlbum`, `.backToAlbums`, `.deleteAlbum` (cascades attachment deletes), `.addPhotos`, `.removePhoto`, `.openLightbox`/`.lightbox.close`/`.prev`/`.next`, `.importGooglePhotos` (one-shot picker via `pickGooglePhotos`, not a live sync).

#### sharebox (`views/sharebox.js`)
Two parallel backends, toggled via `sharebox.setBackend` (Supabase primary / Drive fallback). **v1 (Drive):** `.v1.connectFolder`, `.syncNow`, `.disconnect`, `.setName`, `.setKind`(Link/Note/File), `.setUrgency`, `.shareLink`/`.shareNote`/`.shareFile`, `.removeItem`, `.downloadFile`. **v2 (Supabase):** `.v2.signIn`(Google)/`.signOut`, `.createSpace`/`.joinSpace`/`.switchSpace`, `.copySpaceId`, same share/remove/download action set scoped to `spaceId`, `.removeItem` gated to the poster's own items.

#### museum (`views/museum.js`)
Read-only. No actions — a pure aggregation over Books/Tasks/Assignments/Milestones/Recipes+CookLogs/Projects/Habits+HabitLogs.

#### timecapsules (`views/timecapsules.js`)
`timecapsules.create` (title/body/`sealedUntil` date — "Seal it"), `.delete`.

#### collections (`views/collections.js`)
`collections.create`, `.delete` (cascades items), `.open`/`.close`, `.addItem` (name/date/tags/notes), `.removeItem`.

#### packing (`views/packing.js`)
`packing.createList`, `.deleteList` (cascades items), `.openList`/`.closeList`, `.applyTemplate` (Weekend/Beach/Ski/International bulk-add), `.addItem`, `.toggleItem`, `.removeItem` (no confirm — inconsistent with `.deleteList`, see §9).

#### quartermaster (`views/quartermaster.js`)
`quartermaster.addItem` (name/location/tags), `.lendOut` (name → `lentTo`+`lentSince`), `.markReturned`, `.removeItem`.

#### ghostdays (`views/ghostdays.js`)
Read-only. No actions.

#### starters (`views/starters.js`)
`starters.selectContact` (single dropdown; generates openers from relationship/tags/birthday/notes + a static fallback list). No create/edit actions.

#### rabbitholes (`views/rabbitholes.js`)
`rabbitholes.create`, `.open`/`.close`, `.editNotes` (**note:** doesn't call `rerender()` after save — UNCERTAIN if intentional), `.addLink`/`.removeLink`, `.toggleResolved`, `.delete`, `.toggleShowResolved`.

### Study & Health

#### languages (`views/languages.js`)
`languages.addPack`, `.switchPack`, `.newDeck`, `.addStarterDeck` (only offered for packs with a `STARTER_DECKS` entry — Japanese today), `.deleteDeck`, `.openDeck`, `.addCard`/`.deleteCard`, `.startStudy` (builds a due-card queue), `.playCardAudio` (browser TTS via `speechSynthesis`), `.showAnswer`, `.gradeCard` (Again/Good/Easy → SRS interval update + `LanguageReviewLogs`), `.switchSubTab` (Decks/Library), `.generateStory` (AI, gated on API key), `.addManualStory`, `.openStory`/`.closeStory`, `.toggleTranslation`, `.markRead`/`.markUnread`, `.deleteStory`, `.rateStoryDifficulty` (Too easy/Just right/Too hard → nudges pack's tracked level).

#### chords (`views/chords.js`)
8 tabs, each with its own actions (full list in the life-a/study-health agent transcripts above; summarized): **Dictionary** — pick root/quality/type a symbol, toggle piano/guitar, play voicing, jump to Barry Harris. **Barry Harris** — pick a ♭9-family chip. **Calculator** — pick key, jump to any chord in the diatonic/secondary-dominant/borrowed tables. **Harmony Map** — set key context, toggle "Adventurous" edges, switch Walk/Atlas, click a node for a detail panel (common tones, smoothness, bass motion), walk/pin/hear a move, build/save/load/delete a trail (`ChordProgressions`). **Lessons** — expand a topic, jump to a chord from an example. **Practice** — start an adaptive drill session, hear/show/grade a question (Missed it/Got it/Instant → `ChordSkills` SRS + `ChordDrillLogs`), print a practice sheet. **Log** — freeform practice-session log (date/minutes/notes), separate from graded drills. **Sound** — change panel skin/control style, apply a factory or custom preset, adjust any synth param live, save/delete a custom preset.

#### lifeasmusic (`views/lifeasmusic.js`)
`lifeasmusic.play` (▶ Play — plays the 6-chord sonified sequence). No other actions; fully read-only sonification.

#### habits (`views/habits.js`)
`habits.create`, `.checkIn`/`.uncheckIn` (checkbox), `.select`, `.editName`/`.editNotes`, `.delete` (**no confirm dialog** — the one delete in the app without one, see §9).

#### health (`views/health.js`)
`health.logToday` (finds or creates today's log), `.select`, `.editDate`/`.editSleep`/`.editWorkoutType`/`.editWorkoutMinutes`/`.editWater`/`.editWeight`/`.editNotes`, `.delete` (no confirm), `.importAppleHealthFile` → `.confirmImport`/`.cancelImport` (preview-before-write pattern, merges field-by-field into existing logs rather than overwriting).

#### skilltree (`views/skilltree.js`)
Read-only. No actions.

#### dreamjournal (`views/dreamjournal.js`)
`dreamjournal.addEntry` (date/title/body/tags), `.deleteEntry`. **No edit action exists** — entries can't be modified after creation (confirmed absence of any `.update` call).

#### almanac (`views/almanac.js`)
`almanac.sleepWhatIfSlider` (live-recomputes projection, not persisted), `.subscriptionWhatIfToggle` (in-memory selection, not persisted). No create/edit/delete — fully computed/read-only.

### Utility

#### assistant (`views/assistant.js`)
`assistant.newConversation`, `.selectConversation`, `.sendMessage` (Enter or Send button), `.deleteConversation` (confirm-gated, cascades messages), `.closeConversation`, `.goToSettings` (only shown on the no-API-key gate).

#### knowledge (`views/knowledge.js`)
`knowledge.searchFocus` → `.setFocus`, `.refocusOnNeighbor` (click a graph node), `.changeFocus`, `.openModule` (→ `ctx.navigate`), `.unlink`, `.suggestConnections` (AI, gated on key) → `.acceptSuggestion`, `.searchAddConnection` → `.addConnection`.

#### recall (`views/recall.js`)
`recall.searchToSchedule` → `.scheduleItem`, `.openModule`, `.removeFromReview`, `.grade` (Again/Good/Easy — SRS + `ResurfaceReviewLogs`), `.removeFromUpcoming`. Silent auto-cleanup: scheduled items whose source record was deleted are removed on every render, no user action.

#### timemachine (`views/timemachine.js`)
`timemachine.scrubSlider`, `.pickDate`, `.returnToToday`. Fully read-only lens (16 stores' `createdAt` + a fixed set of dated-log stores), with an always-shown honesty disclaimer about its approximations.

#### qrsync (`views/qrsync.js`)
`qrsync.startOffer` (side A), `.pasteOffer`/auto-entry via scanned URL (side B), `.completeOffer` (side A), `.copyCode`, `.startOver`. WebRTC peer-to-peer, no server/account.

#### entropy (`views/entropy.js`)
Read-only. No actions. Covers only 10 of the 43 modules by design (Tasks, Places, Links, Books, Recipes, Contacts, Milestones, Habits, Health, Documents) — UNCERTAIN why exactly this set, not explained in-file.

#### stationcat (`views/stationcat.js`)
`stationcat.listen` (🔊 Listen — plays a synthesized purr/mew/hiss matching current mood, self-disables 900ms). Purely cosmetic, explicitly documented as such in its own header comment.

#### themefromphoto (`views/themefromphoto.js`)
`themefromphoto.uploadPhoto` (file input), `.pickFromGallery` (from existing Albums, capped at first 24 photos), `.applySwatch` (→ `Settings.set('accent','custom')` + `customAccent`).

#### tools (`views/tools.js`)
`tools.currency.setAmount`/`.setFrom`/`.setTo`, `.refreshRates` (manual + one silent auto-attempt per session if stale >24h), `.editRate`, `.addCurrency`; `tools.units.setCategory`/`.setFrom`/`.setTo`/`.setAmount`; `tools.timezones.addTimezone` (validates IANA tz name), `.removeTimezone`.

#### search (`views/search.js`)
`search.query` (fires on blur/Enter, not every keystroke), `.openResult` (→ `ctx.navigate(result.module)` — **module only, not the specific record**, see §9).

#### settings (`views/settings.js`)
Grouped by sub-section (all render unconditionally on one long page, no tabs):
- **Prefs:** `settings.setTheme`, `.setAccent`, `.setDensity`, `.setInterface`, `.setBillDueSoonDays`, `.setDocumentExpiryDays`.
- **Backup:** `.exportBackup` (downloads full JSON incl. attachments), `.importBackup` (confirm-gated full-replace, then reloads).
- **Account** (hidden entirely if Supabase isn't configured): `.account.signIn`/`.signUp`/`.signInWithGoogle`/`.sendPasswordReset`/`.setNewPassword`/`.saveDisplayName`/`.signOut`.
- **Google Drive sync:** `.drive.connect`/`.syncNow`/`.disconnect`.
- **Google Calendar sync:** `.calendar.setHorizon`, `.connect`/`.syncNow`/`.disconnect`.
- **AI Assistant:** `.ai.unlockProviderToggle` (10 taps on the section label, in-memory only), `.setProvider`, `.setApiKey`, `.setModel`.
- **App Lock:** `.appLock.setup` (WebAuthn enroll), `.turnOff`.
- **Automations:** `.automations.toggleHabitMilestone`, `.toggleDocumentRenewal`.
- **Telegram:** `.telegram.setBotToken`, `.setChatId`, `.sendTest`.

---

## 4. Dashboard data inventory

All of the following are **real, already-computed** values — nothing below is proposed or invented. Everything reads through `ctx.data`.

| Metric | Source | Type | Possible values | Update frequency | Logic | File |
|---|---|---|---|---|---|---|
| Due-soon feed | `getDueSoonFeed(days, billDays, docDays)` | array of `{module,id,title,dueDate,overdue}` | Tasks/Bills/Assignments/Documents, filtered by configurable horizon (default 7d; bills/docs independently configurable via Settings) | On demand, every render | Merges 4 stores, sorts by date; `overdue = dueDate < today` | `api.js:615-640` |
| On this day | `getOnThisDay()` | array of `{year,title,kind}` | Milestone / Visited / Finished reading / Started reading | On demand | Matches today's month-day across past years in Milestones/Places/Books | `api.js:645-674` |
| Surprise Me pick | `getSurpriseMe()` | `{module,title,kind}` or `null` | Place to visit / Book to read / Recipe to try / Bucket-list goal | User-triggered (button) | Random pick from 4 untouched pools; `null` is a legitimate "empty queue" result | `api.js:680-694` |
| Weather | `getWeather()` | `{code,tempF,highF,lowF}` or `null` | Open-Meteo weather codes (`describeWeatherCode`, `api.js:702-714`) | Cached 1h, opt-in via geolocation | `api.js:719+` |
| Assignment pacing gaps | `getAssignmentPacingGaps()` | array of `{assignment,course,checkpoint,loggedTotal,gap}` | Only assignments with a real positive gap vs. their own most-recent past checkpoint | On demand | Pure function shared by Education view and Daily Paper's AI packet | `api.js:180-215` |
| Yearly Review stats | `getYearInReview(year)` | object, see below | — | On demand, per year | `api.js` ~936-1033 |
| Crypto prices | `getCryptoPrices()` | map of coinId→`{usd, usd_24h_change}` | User's `cryptoWatchlist` (Settings) | Cached 5 min | CoinGecko, keyless | `api.js:756+` |
| DJIA quote | `getDjiaPrice()` | `{price, changePct}` | — | Cached | Stooq, keyless | `api.js:784+` |
| Neglect score (per module) | Entropy view's own computation | `{module,label,days,severity}` | fresh(≤6d)/stale(7-29d)/neglected(>29d)/no-data | On demand | Max `updatedAt`/date across each store | `views/entropy.js:7-51` |
| Orrery planets | Orrery view's own computation | `{module,label,color,count,days,activity,overdueCount,orbit,size,speed}` | 8 fixed life areas | On demand, animated | Orbit=neglect, size=log(count), speed=this-week activity, pulsing ring=overdue | `views/orrery.js:48-86` |
| Almanac correlations | Almanac view | Pearson `r` per fixed pair (sleep×habits, sleep×tasks, workout×sleep) | strong/moderate/weak/none, or "not enough data" (`n<5`) | On demand | `views/almanac.js:14-74` |
| Almanac forecasts | Almanac view | bill-spend trend, per-habit weekday breakpoint, per-book reading-pace ETA | Each independently gated on its own minimum sample | On demand | `views/almanac.js:111-199` |
| Museum tallies | Museum view | counts of finished books/completed tasks+assignments/milestones/cooked recipes/archived projects/longest habit streaks | — | On demand | `views/museum.js` |
| Ghost Days | Ghost Days view | array of `{year,title,kind}` (Milestone/Visited/Finished/Started reading/Birthday/Cooked/Completed) | — | On demand | `views/ghostdays.js` |
| Skill Tree stats | Skill Tree view | 5 skills' level+XP (Executor/Discipline/Scholar/Music Theory/Linguist) + character level (sum) | — | On demand, fully computed, no storage | `views/skilltree.js:56-76` |
| Habit streak / totals | Habits view | per-habit `{streak, doneToday, totalCheckIns}` | — | On demand | `views/habits.js:7-24` |
| Reading streak | Books view | days | — | On demand | `views/books.js:245-259` |
| Health 7-day averages | Health view | avg sleep, avg water, workout count | — | On demand | `views/health.js:154-169` |
| Cards-due count | Languages view | per-deck count | — | On demand | `views/languages.js` |
| Recall due/upcoming | Recall view | due-today count + upcoming list | — | On demand | `views/recall.js` |

**Full `getYearInReview(year)` field list** (`api.js` ~936-1033): `year`, `tasksCompleted`, `assignmentsCompleted`, `placesVisitedCount`, `totalVisits`, `booksFinished`, `pagesRead`, `recipesCookedCount`, `cookSessions`, `billsPaidTotal`, `documentsAdded`, `contactsAdded`, `milestones` (array), `habitCheckIns`, `avgSleepHours` (nullable), `healthLogCount`.

**Confirmed safe for a home/command dashboard today (already assembled for exactly that purpose):** due-soon feed with overdue flag, on-this-day, weather, Surprise Me, and — newly — assignment pacing gaps (all four already power the existing Dashboard and/or Daily Paper). Everything else above is real and computed but currently lives on its own module screen, not the dashboard.

---

## 5. Dynamic states

| State | Trigger (real, cited) | Example |
|---|---|---|
| **Loading** | Async data-fetch placeholders, always synchronously replaced within the same render pass except one confirmed genuine gap | Documents' attachment list ("Loading attachments…", `documents.js:56`) is a real async gap; most others (Places' photos/people, Bills' attachment/history) resolve via `.then()` before paint — UNCERTAIN if a visible flash ever occurs |
| **Empty** | Zero records in a filtered list | Nearly universal — every module has its own empty-state string (e.g. "No bills match the current filters.", `finance.js:216`; "Nothing here yet.", `places.js:277`) |
| **Normal** | Default rendered state | — |
| **Active** | Selected nav item / selected tab | `.is-active` class, e.g. `mer-nav-item.is-active`, `.mer-toggle-group button.is-active` |
| **Selected** | A record's detail panel is open | Module-level `state.selectedId` pattern, used identically across ~30 modules |
| **Completed / Done** | `status === 'done'` (Tasks, Assignments) or checkbox state | Strikethrough via `.mer-task-title.is-done` |
| **Due soon** | Within a configurable horizon (default 7d; Bills/Documents independently configurable) | `getDueSoonFeed` |
| **Overdue** | `dueDate < today` and not done/paid | `isOverdue()` (`api.js:605-608`); drives `.is-overdue` class → red text/border across chips, feed items, Orrery's pulsing ring |
| **Warning / Caution** | Not a distinct existing app-wide state — currently collapsed into the same "due soon" (amber, proposed for the mobile remote) vs. "overdue" (red) binary. No third tier exists today. |
| **Critical** | Same as Overdue above — the app has no separate "critical" tier beyond overdue |
| **Disabled** | Buttons mid-async-operation | e.g. Drive/Calendar sync buttons disable + show "Connecting…"/"Syncing…" while in flight (`settings.js:188-190`); Documents' scan button shows "Scanning…"; base.css's global `button:disabled { opacity:0.6 }` |
| **Error** | A thrown exception from an API/AI/sync call | Styled `.mer-sync-error` (red text) — used consistently for: AI editorial/narrative errors, sync errors, scan errors, Sharebox connection errors, App Lock cancel/failure |
| **Offline / Unavailable** | No network for a network-dependent feature | Places' Map: "Map unavailable (offline?): …"; Currency/Crypto tools fall back to last-cached or manual values; Weather is opt-in and silently absent if never set |

**No global connectivity indicator exists** — offline handling is per-feature (each module that hits the network degrades gracefully on its own), not a single app-wide "you are offline" banner. Confirm this gap with the design if a mobile interface wants one.

---

## 6. Content rules

- **Titles/names:** free-text, no enforced max length anywhere in the data layer. Longest realistic titles: recipe/book titles, task titles, document titles — commonly 40-80 characters. `.mer-nav-label` truncates with `overflow:hidden;text-overflow:ellipsis;white-space:nowrap` (mobile nav only) — no truncation applied to canvas content titles; they wrap naturally.
- **Counts:** most counts are small (single/double digit — task counts, habit streaks). A few can plausibly exceed 2 digits: total check-ins (`habits.js`, unbounded), search results (capped at 200, `api.js:1079`), knowledge-graph search (capped at 30/20 depending on context).
- **Percentages:** Assignment `percentComplete` is 0-100 (slider). Reading progress is computed `currentPage/totalPages*100`. GPA is a decimal 0.0-4.0 (`toFixed(2)`).
- **Dates:** `fmtDate()` (`dom.js:23-28`) renders as locale `{month:'short', day:'numeric', year:'numeric'}` (e.g. "Jul 12, 2026"). Raw ISO date strings (`YYYY-MM-DD`) are the storage format throughout; `todayStr()` (`dom.js:30-32`) is `new Date().toISOString().slice(0,10)` — **UTC-based**, not local-midnight-based (a real, existing quirk worth knowing: near midnight, "today" can flip a few hours off from true local midnight depending on timezone).
- **Times:** no dedicated time formatter found; timestamps (`createdAt`/`updatedAt`) are full ISO 8601, occasionally rendered via `new Date(...).toLocaleString()` (e.g. crypto price fetch time, `finance.js:407`).
- **User-customizable labels:** category/tag fields are entirely freeform user text across Places, Documents, Finance (Bills/Subscriptions), Recipes, Contacts, Collections — **do not hardcode any specific category value**; the app never ships a fixed category enum for these fields. Language pack names/codes are user-defined (`ensureLanguagePack`). Custom synth presets (Chords) are user-named.
- **Text that must not be hard-coded:** AI provider labels (`getActiveAiProvider().label` — currently "Gemini"/"Claude" but provider-switchable via Settings), the active accent color name, the active interface name (`ctx.listInterfaces()`), weather condition labels (`describeWeatherCode`, 20+ codes), currency code labels (`CURRENCY_NAMES` map in `tools.js`), and every module's display label (`js/modules.js` — the single source of truth for nav text).

---

## 7. Existing design and technical constraints

- **Framework:** none. Vanilla JS, ES modules, no build step, no bundler, no TypeScript, no JSX. Runs directly from static files (GitHub Pages).
- **Styling system:** plain CSS with custom properties (`css/tokens.css` — design tokens for type scale, spacing, radius, shadow, transitions, and three theme dimensions: light/dark, density (comfortable/compact), and accent (brass/teal/garnet/custom-from-photo)). No CSS framework (no Tailwind/Bootstrap/etc.), no CSS-in-JS.
- **Component library:** none, beyond a single ~20-line `el(tag, attrs, children)` DOM-builder helper (`js/interfaces/default/dom.js:5-21`) shared by every view. No React/Vue/Svelte/Web Components.
- **Icon library:** a newly-built original set at `js/interfaces/default/icons.js` — 43 inline-SVG line icons (one per module), built from plain primitives (line/circle/rect/polyline) on a shared 20×20 grid, `stroke="currentColor"`. No third-party icon font/library (no Font Awesome, Feather, Lucide, Material Icons). Elsewhere in the app, emoji are used liberally as inline glyphs (🎲, 📍, 🔥, 👤, etc.) — not a formal icon system, just Unicode characters in button text.
- **Fonts:** system stacks only, no CDN/webfont loading in the default (Equator) or Vespera interfaces — `--font-display: Georgia, 'Iowan Old Style', 'Palatino Linotype', serif` (headings, module titles, card titles), `--font-ui: -apple-system, 'Segoe UI', Roboto, system-ui, sans-serif` (everything else), `--font-mono: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace` (dates in feed rows). Two vendored variable-weight webfonts (`Oxanium`, `Rajdhani`, `.woff2`) exist in `vendor/fonts/` but are earmarked for a future mobile interface specifically — **not currently used** by Equator or Vespera.
- **Mobile breakpoint:** exactly one, `@media (max-width: 44rem)` (704px) — collapses Equator's sidebar nav into a hamburger-triggered dropdown. No tablet-specific breakpoint exists.
- **Safe-area handling:** `viewport-fit=cover` is set in `index.html:5`, but **no `env(safe-area-inset-*)` CSS exists anywhere in the codebase** — confirmed via full-repo search. This is a real, currently-unaddressed gap for notch/home-indicator devices; a new mobile interface needs to add this itself.
- **Routing:** hash-based (`#/<module>[/...]`), owned entirely by `js/shell.js`. No history API / pushState routing. Deep-linking beyond the module id (e.g. to a specific record) is **not implemented** anywhere in the app today (confirmed absence across Search, Recall, Knowledge Graph — all navigate to the module only).
- **State management:** no global store (no Redux/MobX/Zustand). Each view file keeps its own module-level plain-object `state` (selection, tab, filters) that persists only for the life of that view's mount, reset on interface switch. Reactivity is a single pub/sub (`js/data/events.js`): every data write emits `{topic: storeName}`; a view calls `ctx.events.on('*', ...)` and re-renders on any change (coalesced via `queueMicrotask` in `js/interfaces/default/index.js:115-123` so bursts collapse to one render).
- **Data storage:** IndexedDB (`js/data/db.js`), accessed exclusively through `js/data/api.js`'s generic CRUD (`list/get/create/update/remove/byIndex` per store) — interfaces never touch `db.js` or `schema.js` directly (the one architectural boundary rule, `ARCHITECTURE.md:13-16`). ~60 stores defined in `js/data/schema.js`. Optional sync: Google Drive (bidirectional, last-write-wins + tombstones), Google Calendar (one-way push), Supabase (accounts + Sharebox v2), all behind the same `ctx.data` surface.
- **APIs used:** Open-Meteo (weather, keyless), CoinGecko (crypto, keyless), Stooq (DJIA, keyless), open.er-api.com (currency rates, keyless), Google Drive/Calendar/Photos-Picker (OAuth), Supabase (accounts/Sharebox v2), Gemini/Claude (AI features, user's own API key, device-local, never synced), browser `SpeechRecognition` (Ideas' voice dictation — cloud-backed in Chrome, not private), browser `speechSynthesis` (Languages' card TTS), WebRTC (QR Sync, no server).
- **Accessibility conventions:** minimal today — a full-repo search found only **3** instances of `aria-label`/`role` across the entire default interface (`js/interfaces/default/index.js:20,31`: nav landmark + hamburger button; Orrery's SVG planets use `role="img"`/`tabindex` for gone nodes). No systematic ARIA pattern, no skip-links, no live-region announcements. This is a real gap, not a hidden convention to preserve.
- **Animation & reduced motion:** `prefers-reduced-motion: reduce` is respected in exactly two places — Orrery (falls back to a static scattered layout, no RAF loop, `views/orrery.js:129,166`) and Station Cat (disables its CSS mood animations, `style.css:1959-1964`). It is **not** applied globally; most transitions (nav hover, card hover-lift, button hover) are short (120-200ms, `--transition-fast`/`--transition-normal`) CSS transitions with no reduced-motion guard, on the reasoning that a sub-200ms hover transition isn't the kind of motion that class of query is meant to suppress.
- **Sound settings:** **none exist as an app-wide concept.** No global mute/volume control, no "sound enabled" setting (confirmed via full-repo search for mute/volume/sound-toggle patterns). The Chords module has its own full Web Audio synthesis engine (oscillators/FM/ADSR/EQ) used for chord playback and Station Cat's mood sounds — that's app **content**, not a UI feedback/notification sound system. If a new mobile interface wants tap sounds or alerts, that's new territory, not something to preserve from an existing setting.
- **Print support:** exists for exactly two things — the Daily Paper (`@media print`, `.mer-print-sheet`) and Chords' practice sheet — irrelevant to a phone interface but noted for completeness.

---

## 8. Recommended component map

Neutral, reusable component names inferred from actual repeated patterns across the 43 modules (not proposed styling). Every one of these patterns appears near-identically in at least 5+ modules.

- **NavigationControl** — `{ id, label, iconMarkup, isActive, badgeCount?, onSelect }`. Maps 1:1 to the existing `MODULES` array + `icons.js`.
- **QuickAddInput** — `{ placeholder, onSubmit(text) }`. The single most repeated pattern in the app — a bare text input with an Enter-to-create handler, used in Tasks/Places/Links/Recipes/Contacts/Milestones/Documents/Ideas/Rabbit Holes/Collections/Packing/Quartermaster/Time Capsules/Languages(decks)/etc.
- **RecordCard** — `{ title, subtitle?, thumbnailUrl?, chips: [{label, variant}], onSelect, onDelete? }`. The card-grid pattern (Places/Recipes/Links/Museum-style visuals) — `mer-place-card` equivalent.
- **RecordRow** — `{ title, isDone?, meta: [{label, variant}], onSelect }`. The list-row pattern (Tasks/Bills/Assignments) — `mer-task-row` equivalent.
- **DetailPanel** — `{ title, fields: [{label, input}], onClose, onDelete? }`. The universal inline (non-modal) edit panel appended below every list.
- **StatusChip** — `{ label, variant: 'default'|'overdue'|'tag'|... }`. `mer-chip` — used for due dates, categories, tags, streak counts, everywhere.
- **TabBar** — `{ tabs: [{id,label}], activeId, onSelect }`. `mer-toggle-group` — used in ~15 modules (Places, Links, Finance, Books, Milestones, Recipes, Languages, Chords, etc.).
- **EmptyState** — `{ message }`. Every list has its own literal empty-state string (see §6) — should stay text, not an icon-only illustration, to match the app's plain/serious tone.
- **ConfirmAction** — currently native `confirm()`/`alert()` throughout; **inconsistently applied** (see §9) — a real component here would also need to fix that inconsistency.
- **StreakBadge** — `{ count, unit: 'day' }`. Habits, Books (reading), Languages (study), Recall (review), Recipes-adjacent (cook streak via Museum).
- **DueDateBadge** — `{ date, isOverdue }`. Directly maps to the existing `isOverdue()`/`isWithinDays()` logic (`api.js:596-608`).
- **ProgressBar** — `{ value, max }`. Skill Tree (XP), Packing (packed/total), Almanac forecasts, reading progress.
- **RatingStars** — `{ value: 0-5, onChange }`. Places, and the same widget shape reused conceptually wherever a 1-5 scale appears.
- **TagChipList** — `{ tags: string[], onRemove? }`. `#tag` rendering, freeform user tags across ~10 modules.
- **EntityLinkPicker** — `{ items, selectedId, onLink, onQuickCreate(name) }`. The shared `contactLinkField` helper (`dom.js:83-108`) — select-existing-or-create-new, reused for Contacts links from Places/Documents/Finance.
- **FileUploadTile** — `{ accept, multiple?, onUpload(files) }`. Photo/attachment upload, near-identical across Places/Contacts/Milestones/Recipes/Books/Documents/Finance/Photos.
- **AIProviderGate** — `{ hasApiKey, providerLabel, onGoToSettings }`. The "add your {Provider} API key in Settings" pattern — AI Assistant, Knowledge Graph suggestions, Milestones' yearly narrative, Daily Paper's editorial, Languages' story generation all share this exact gate shape.
- **SyncStatusRow** — `{ label, enabled, lastSyncedAt, onConnect, onSyncNow, onDisconnect }`. Drive sync and Calendar sync in Settings share this exact shape.
- **SearchResultRow** — `{ title, moduleLabel, onSelect }`. Global Search, Knowledge Graph's search, Recall's scheduler search all consume the same `globalSearch()` result shape.
- **GradeButtons** — `{ onAgain, onGood, onEasy }`. The identical SRS-grading control in Languages, Recall, and Chords' Practice tab.

---

## 9. Feature preservation checklist

Things that are easy to accidentally drop when replacing the interface, because they're not obviously "a screen" — they're small interaction details buried inside a screen.

- [ ] **Camera-to-data document scan** (Documents) — the AI-vision capture flow; described in `MOBILE_INTERFACES_SPEC.md` as "the headline reason this module belongs on a phone at all."
- [ ] **Voice dictation on Ideas** (🎤 mic button) — browser `SpeechRecognition`, not private (cloud-backed in Chrome) but real and working; easy to miss since it's feature-detected and silently absent on unsupported browsers.
- [ ] **Geolocation "check nearby places" nudge** (Places) — a genuinely useful on-the-go feature (want-to-go places, stale revisits, geofenced notes-to-self within 1km), manually triggered (no passive background geofencing).
- [ ] **QR Sync** (device-to-device WebRTC, no server/account) — a legitimately different sync mechanism from Drive/Supabase; don't conflate or drop it.
- [ ] **App Lock** (WebAuthn biometric/PIN gate) — a local-only device gate, not account auth; verify a mobile redesign doesn't accidentally bypass or duplicate this.
- [ ] **Academic pacing check** (Education) — very new (this session); a self-set checkpoint + dated progress log + gap-surfacing, distinct from the older `percentComplete` slider. Easy to overlook since it's additive to an existing module.
- [ ] **Recurring bill/task roll-forward** — marking a recurring Bill paid auto-advances `dueDate` and logs a `BillPayments` record in the same action (`finance.js:32-47`); a naive "just toggle paid" reimplementation would silently break recurrence.
- [ ] **Grocery list is NOT persisted** — Recipes' grocery-list picker state is in-memory only, resets on reload; if a mobile redesign expects to persist it, that's new work, not an existing guarantee to carry over.
- [ ] **Apple Health import is preview-before-write and field-merges** rather than overwriting whole records — a naive reimplementation could silently clobber manually-entered health data.
- [ ] **Provider-switchable AI** (Gemini/Claude) — every AI-gated feature (Assistant, Daily Paper editorial, Milestones narrative, Knowledge Graph suggestions, Languages stories, Documents scan) reads the *same* active-provider setting; don't hardcode "Gemini" anywhere.
- [ ] **Telegram is send-only by design** — no incoming-message listener exists; don't imply two-way chat.
- [ ] **Sharebox has two live backends** (Drive v1, Supabase v2) simultaneously supported — a redesign needs to handle both, or explicitly decide to drop v1, as a real decision, not an oversight.
- [ ] **Search/Recall/Knowledge-Graph navigation is module-level only, not record-level** — clicking a result opens the module's list, not the specific record's detail view. If a mobile redesign wants true deep-linking, that's genuinely new work, not something being carried forward incorrectly.
- [ ] **Delete-confirmation coverage is inconsistent today** — most deletes use `confirm()`, but Habits' delete, Health's delete, and Packing's item-removal do not. A redesign should make a deliberate choice here rather than copying the inconsistency by accident.
- [ ] **`prefers-reduced-motion` is only honored in Orrery and Station Cat**, not globally — if the new interface adds more motion, it needs its own reduced-motion handling, it won't inherit one.
- [ ] **No `env(safe-area-inset-*)` handling exists anywhere** — a real, currently-open gap on notch/home-indicator phones that the current app hasn't solved either; don't assume there's existing CSS to reuse here.
- [ ] **Rules & Automations run silently on every boot** (`runAutomations()`, off by default) — habit-milestone logging and document-renewal task creation. Both idempotent, but they mutate data without a screen of their own; make sure Settings' toggles for them survive a redesign.
- [ ] **Local-first / offline-first is the whole point** — nothing in the app requires a live connection to function day-to-day; any new interface needs to preserve that, not add a hidden network dependency.

---

## 10. Machine-readable appendix

```json
{
  "routes": [
    "#/dashboard", "#/orrery", "#/paper", "#/tasks", "#/ideas", "#/places", "#/links",
    "#/education", "#/finance", "#/books", "#/recipes", "#/documents", "#/contacts",
    "#/milestones", "#/photos", "#/sharebox", "#/museum", "#/timecapsules", "#/collections",
    "#/packing", "#/quartermaster", "#/ghostdays", "#/starters", "#/rabbitholes",
    "#/languages", "#/chords", "#/lifeasmusic", "#/habits", "#/health", "#/skilltree",
    "#/dreamjournal", "#/almanac", "#/assistant", "#/knowledge", "#/recall",
    "#/timemachine", "#/qrsync", "#/entropy", "#/stationcat", "#/themefromphoto",
    "#/tools", "#/search", "#/settings"
  ],
  "navigationDestinations": [
    {"id":"dashboard","label":"Today","group":"core","navCategory":"primary","remote":true},
    {"id":"orrery","label":"Orrery","group":"core","navCategory":"secondary","remote":false},
    {"id":"paper","label":"Daily Paper","group":"core","navCategory":"primary","remote":true},
    {"id":"tasks","label":"Tasks","group":"core","navCategory":"primary","remote":true},
    {"id":"ideas","label":"Ideas","group":"core","navCategory":"primary","remote":true},
    {"id":"places","label":"Places","group":"core","navCategory":"primary","remote":true},
    {"id":"links","label":"Links","group":"core","navCategory":"primary","remote":true},
    {"id":"education","label":"Education","group":"core","navCategory":"secondary","remote":false},
    {"id":"finance","label":"Finance","group":"core","navCategory":"secondary","remote":false},
    {"id":"books","label":"Books","group":"life","navCategory":"secondary","remote":false},
    {"id":"recipes","label":"Recipes","group":"life","navCategory":"primary","remote":true},
    {"id":"documents","label":"Documents","group":"life","navCategory":"primary","remote":true},
    {"id":"contacts","label":"Contacts","group":"life","navCategory":"primary","remote":true},
    {"id":"milestones","label":"Milestones","group":"life","navCategory":"secondary","remote":false},
    {"id":"photos","label":"Photos","group":"life","navCategory":"primary","remote":true},
    {"id":"sharebox","label":"Sharebox","group":"life","navCategory":"secondary","remote":false},
    {"id":"museum","label":"Museum","group":"life","navCategory":"secondary","remote":false},
    {"id":"timecapsules","label":"Time Capsules","group":"life","navCategory":"secondary","remote":false},
    {"id":"collections","label":"Collections","group":"life","navCategory":"secondary","remote":false},
    {"id":"packing","label":"Packing Lists","group":"life","navCategory":"primary","remote":true},
    {"id":"quartermaster","label":"Quartermaster","group":"life","navCategory":"primary","remote":true},
    {"id":"ghostdays","label":"Ghost Days","group":"life","navCategory":"secondary","remote":false},
    {"id":"starters","label":"Conversation Starters","group":"life","navCategory":"primary","remote":true},
    {"id":"rabbitholes","label":"Rabbit Holes","group":"life","navCategory":"secondary","remote":false},
    {"id":"languages","label":"Languages","group":"study","navCategory":"primary","remote":true},
    {"id":"chords","label":"Chords","group":"study","navCategory":"secondary","remote":false},
    {"id":"lifeasmusic","label":"Life as Music","group":"study","navCategory":"secondary","remote":false},
    {"id":"habits","label":"Habits","group":"health","navCategory":"primary","remote":true},
    {"id":"health","label":"Health","group":"health","navCategory":"primary","remote":true},
    {"id":"skilltree","label":"Skill Trees","group":"health","navCategory":"secondary","remote":false},
    {"id":"dreamjournal","label":"Dream Journal","group":"health","navCategory":"secondary","remote":false},
    {"id":"almanac","label":"The Almanac","group":"health","navCategory":"secondary","remote":false},
    {"id":"assistant","label":"AI Assistant","group":"utility","navCategory":"secondary","remote":false},
    {"id":"knowledge","label":"Knowledge Graph","group":"utility","navCategory":"secondary","remote":false},
    {"id":"recall","label":"Recall","group":"utility","navCategory":"primary","remote":true},
    {"id":"timemachine","label":"Time Machine","group":"utility","navCategory":"secondary","remote":false},
    {"id":"qrsync","label":"QR Sync","group":"utility","navCategory":"primary","remote":true},
    {"id":"entropy","label":"Entropy","group":"utility","navCategory":"secondary","remote":false},
    {"id":"stationcat","label":"Station Cat","group":"utility","navCategory":"secondary","remote":false},
    {"id":"themefromphoto","label":"Theme from Photo","group":"utility","navCategory":"secondary","remote":false},
    {"id":"tools","label":"Tools","group":"utility","navCategory":"primary","remote":true},
    {"id":"search","label":"Search","group":"utility","navCategory":"primary","remote":true},
    {"id":"settings","label":"Settings","group":"utility","navCategory":"settings","remote":true}
  ],
  "actionsByModule": {
    "dashboard": ["dashboard.surpriseMe","dashboard.goToSurprise","dashboard.useMyLocation","dashboard.removeWeather"],
    "orrery": ["orrery.flyToPlanet"],
    "paper": ["paper.toggleChecklistItem","paper.toggleHabit","paper.rerollPick","paper.regenerateEditorial","paper.print","paper.sendToTelegram"],
    "tasks": ["tasks.create","tasks.toggleDone","tasks.select","tasks.editField","tasks.snoozeTomorrow","tasks.snoozePlusWeek","tasks.clearSnooze","tasks.addChecklistItem","tasks.toggleChecklistItem","tasks.removeChecklistItem","tasks.delete","tasks.newProject","tasks.viewToggle","tasks.toggleShowSnoozed"],
    "ideas": ["ideas.dictate","ideas.save","ideas.promoteToTask","ideas.archive","ideas.unarchive","ideas.delete","ideas.toggleShowArchived"],
    "places": ["places.create","places.checkNearby","places.dismissNearbyBanner","places.select","places.editField","places.useMyLocation","places.rate","places.toggleRevisit","places.addVisitDate","places.removeVisitDate","places.addNoteToSelf","places.removeNoteToSelf","places.linkContact","places.unlinkContact","places.quickCreateContact","places.addPhoto","places.removePhoto","places.delete","places.tabSwitch","places.bucketAdd","places.bucketToggle","places.bucketRemove"],
    "links": ["links.create","links.select","links.editField","links.toggleWatchedRead","links.delete","links.tabSwitch","links.toggleShowDone"],
    "education": ["education.createSemester","education.createCourse","education.createAssignment","education.selectSemester","education.selectCourse","education.selectAssignment","education.editCourseField","education.addKeyDate","education.removeKeyDate","education.deleteCourse","education.deleteSemester","education.deleteAssignment","education.toggleAssignmentDone","education.editAssignmentField","education.setPacingTarget","education.setPacingUnit","education.addPacingCheckpoint","education.removePacingCheckpoint","education.logProgress","education.deleteProgressLog","education.tabSwitch"],
    "finance": ["finance.createBill","finance.createSubscription","finance.toggleBillPaid","finance.selectBill","finance.selectSubscription","finance.editBillField","finance.editSubscriptionField","finance.linkContact","finance.addAttachment","finance.removeAttachment","finance.logPayment","finance.deletePayment","finance.deleteBill","finance.deleteSubscription","finance.addCoin","finance.removeCoin","finance.refreshCrypto","finance.tabSwitch","finance.filterCategory","finance.toggleShowPaid","finance.toggleShowCancelled"],
    "books": ["books.create","books.select","books.editField","books.rate","books.delete","books.logReadingSession","books.deleteReadingLog","books.addCover","books.removeCover","books.addFile","books.removeFile","books.readFile","books.downloadFile","books.tabSwitch"],
    "recipes": ["recipes.create","recipes.select","recipes.editField","recipes.delete","recipes.addCover","recipes.removeCover","recipes.editIngredient","recipes.addIngredient","recipes.removeIngredient","recipes.scaleServings","recipes.addStep","recipes.removeStep","recipes.logCook","recipes.deleteCookLog","recipes.grocery.toggleRecipe","recipes.grocery.setServings","recipes.tabSwitch"],
    "documents": ["documents.create","documents.filterCategory","documents.select","documents.editField","documents.linkContact","documents.delete","documents.addAttachment","documents.removeAttachment","documents.scan"],
    "contacts": ["contacts.create","contacts.search","contacts.filterTag","contacts.select","contacts.editField","contacts.addPhoto","contacts.removePhoto","contacts.editPhone","contacts.editEmail","contacts.addPhone","contacts.addEmail","contacts.removePhone","contacts.removeEmail","contacts.delete"],
    "milestones": ["milestones.create","milestones.select","milestones.editField","milestones.delete","milestones.addPhoto","milestones.removePhoto","milestones.tabSwitch","milestones.recap.setYear","milestones.recap.generateNarrative","milestones.recap.regenerateNarrative"],
    "photos": ["photos.createAlbum","photos.selectAlbum","photos.backToAlbums","photos.deleteAlbum","photos.addPhotos","photos.removePhoto","photos.openLightbox","photos.lightbox.close","photos.lightbox.prev","photos.lightbox.next","photos.importGooglePhotos"],
    "sharebox": ["sharebox.setBackend","sharebox.v1.connectFolder","sharebox.v1.syncNow","sharebox.v1.disconnect","sharebox.v1.setName","sharebox.v1.setKind","sharebox.v1.setUrgency","sharebox.v1.shareLink","sharebox.v1.shareNote","sharebox.v1.shareFile","sharebox.v1.removeItem","sharebox.v1.downloadFile","sharebox.v2.signIn","sharebox.v2.signOut","sharebox.v2.createSpace","sharebox.v2.joinSpace","sharebox.v2.switchSpace","sharebox.v2.copySpaceId","sharebox.v2.setKind","sharebox.v2.setUrgency","sharebox.v2.shareLink","sharebox.v2.shareNote","sharebox.v2.shareFile","sharebox.v2.removeItem","sharebox.v2.downloadFile"],
    "museum": [],
    "timecapsules": ["timecapsules.create","timecapsules.delete"],
    "collections": ["collections.create","collections.delete","collections.open","collections.close","collections.addItem","collections.removeItem"],
    "packing": ["packing.createList","packing.deleteList","packing.openList","packing.closeList","packing.applyTemplate","packing.addItem","packing.toggleItem","packing.removeItem"],
    "quartermaster": ["quartermaster.addItem","quartermaster.lendOut","quartermaster.markReturned","quartermaster.removeItem"],
    "ghostdays": [],
    "starters": ["starters.selectContact"],
    "rabbitholes": ["rabbitholes.create","rabbitholes.open","rabbitholes.close","rabbitholes.editNotes","rabbitholes.addLink","rabbitholes.removeLink","rabbitholes.toggleResolved","rabbitholes.delete","rabbitholes.toggleShowResolved"],
    "languages": ["languages.addPack","languages.switchPack","languages.newDeck","languages.addStarterDeck","languages.deleteDeck","languages.openDeck","languages.addCard","languages.deleteCard","languages.startStudy","languages.playCardAudio","languages.showAnswer","languages.gradeCard","languages.switchSubTab","languages.generateStory","languages.addManualStory","languages.openStory","languages.closeStory","languages.toggleTranslation","languages.markRead","languages.markUnread","languages.deleteStory","languages.rateStoryDifficulty"],
    "chords": ["chords.pickRootQuality","chords.toggleInstrument","chords.playVoicing","chords.jumpToBarryFromDictionary","chords.pickFamilyMember","chords.calcPickKey","chords.calcJumpToChord","chords.mapSetKeyContext","chords.mapToggleAdventurous","chords.mapSwitchView","chords.mapInspectEdge","chords.mapPickFromAtlas","chords.mapHearMove","chords.mapWalkTo","chords.mapPinWithoutWalking","chords.mapCloseDetail","chords.mapDiatonicQuickJump","chords.trailRemoveStep","chords.trailHear","chords.trailSave","chords.trailClear","chords.trailLoad","chords.trailDeleteSaved","chords.trailHearSaved","chords.lessonsOpen","chords.lessonsExampleChip","chords.soundChangeSkin","chords.soundChangeControlStyle","chords.soundApplyPreset","chords.soundTestChord","chords.soundAdjustParam","chords.soundSavePreset","chords.soundDeleteCustomPresets","chords.practiceStartSession","chords.practicePrintSheet","chords.drillShowAnswer","chords.drillPlayQuestion","chords.drillGrade","chords.drillBackToPractice","chords.logAddSession","chords.logDeleteEntry"],
    "lifeasmusic": ["lifeasmusic.play"],
    "habits": ["habits.create","habits.checkIn","habits.uncheckIn","habits.select","habits.editName","habits.editNotes","habits.delete"],
    "health": ["health.logToday","health.select","health.editDate","health.editSleep","health.editWorkoutType","health.editWorkoutMinutes","health.editWater","health.editWeight","health.editNotes","health.delete","health.importAppleHealthFile","health.confirmImport","health.cancelImport"],
    "skilltree": [],
    "dreamjournal": ["dreamjournal.addEntry","dreamjournal.deleteEntry"],
    "almanac": ["almanac.sleepWhatIfSlider","almanac.subscriptionWhatIfToggle"],
    "assistant": ["assistant.newConversation","assistant.selectConversation","assistant.sendMessage","assistant.deleteConversation","assistant.closeConversation","assistant.goToSettings"],
    "knowledge": ["knowledge.searchFocus","knowledge.setFocus","knowledge.refocusOnNeighbor","knowledge.changeFocus","knowledge.openModule","knowledge.unlink","knowledge.suggestConnections","knowledge.acceptSuggestion","knowledge.searchAddConnection","knowledge.addConnection"],
    "recall": ["recall.searchToSchedule","recall.scheduleItem","recall.openModule","recall.removeFromReview","recall.grade","recall.removeFromUpcoming"],
    "timemachine": ["timemachine.scrubSlider","timemachine.pickDate","timemachine.returnToToday"],
    "qrsync": ["qrsync.startOffer","qrsync.pasteOffer","qrsync.completeOffer","qrsync.copyCode","qrsync.startOver"],
    "entropy": [],
    "stationcat": ["stationcat.listen"],
    "themefromphoto": ["themefromphoto.uploadPhoto","themefromphoto.pickFromGallery","themefromphoto.applySwatch"],
    "tools": ["tools.currency.setAmount","tools.currency.setFrom","tools.currency.setTo","tools.currency.refreshRates","tools.currency.editRate","tools.currency.addCurrency","tools.units.setCategory","tools.units.setFrom","tools.units.setTo","tools.units.setAmount","tools.timezones.addTimezone","tools.timezones.removeTimezone"],
    "search": ["search.query","search.openResult"],
    "settings": ["settings.setTheme","settings.setAccent","settings.setDensity","settings.setInterface","settings.setBillDueSoonDays","settings.setDocumentExpiryDays","settings.exportBackup","settings.importBackup","settings.account.signIn","settings.account.signUp","settings.account.signInWithGoogle","settings.account.sendPasswordReset","settings.account.setNewPassword","settings.account.saveDisplayName","settings.account.signOut","settings.drive.connect","settings.drive.syncNow","settings.drive.disconnect","settings.calendar.setHorizon","settings.calendar.connect","settings.calendar.syncNow","settings.calendar.disconnect","settings.ai.unlockProviderToggle","settings.ai.setProvider","settings.ai.setApiKey","settings.ai.setModel","settings.appLock.setup","settings.appLock.turnOff","settings.automations.toggleHabitMilestone","settings.automations.toggleDocumentRenewal","settings.telegram.setBotToken","settings.telegram.setChatId","settings.telegram.sendTest"]
  },
  "dashboardMetrics": [
    "dueSoonFeed","onThisDay","surpriseMe","weather","assignmentPacingGaps","yearInReview",
    "cryptoPrices","djiaPrice","entropyScores","orreryPlanets","almanacCorrelations",
    "almanacForecasts","museumTallies","ghostDays","skillTreeStats","habitStreaks",
    "readingStreak","health7DayAverages","languagesCardsDue","recallDueUpcoming"
  ],
  "dynamicStates": [
    "loading","empty","normal","active","selected","completed","dueSoon","overdue",
    "disabled","error","offline"
  ]
}
```

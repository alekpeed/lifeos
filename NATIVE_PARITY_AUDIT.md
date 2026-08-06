# Native vs Web — parity audit (2026-07-21, fresh source read)

Branch `claude/lifeos-dev-setup-dpipr6`. Native = Kotlin/Compose screens under
`native/composeApp/src/commonMain/.../<area>/`; Web = the reference
implementation in `js/interfaces/default/views/*.js`. Every verdict below was
re-read from the **current** native Kotlin source (6 parallel per-group audits),
not carried over from the prior doc.

> **Method caveat (per the project's own rule):** these are **source-level**
> verdicts — feature-surface parity in the code, **not device-verified**. Nothing
> here is "done" until Alek sees it work on device. This is a map of where things
> stand, not a sign-off.

## Closed since this audit (2026-07-21, later same day)

The matrix below was the morning read. These landed after it (batches 1–3 +
the four solo builds) and have gone **green on CI** — treat their rows'
named gaps as closed (device-verify still pending):

- **command / ideas** — voice dictation wired (🎤 → RecognizerIntent). *(gap closed)*
- **places** — link contacts to a place (ContactField picker). *(gap closed; multi-photo grid still open)*
- **quartermaster** — per-item persisted stock status + chips. *(→ PARITY)*
- **books** — in-app PDF reader (PdfRenderer) **and** EPUB chapter/TOC nav. *(both reader gaps closed; many-files-per-book still open)*
- **documents** — multi-file/PDF attachments + configurable expiry window. *(→ PARITY)*
- **finance** — PDF bill attachments, sub renewal/notes/category, manual payment history, CSV dedup. *(→ PARITY)*
- **links** — tap-to-open in browser (↗ Open → Native.openUrl). *(→ PARITY)*
- **education** — renders the tagged Links list. *(→ PARITY)*
- **tasks** — project picker (FilterChips) + hide-snoozed toggle. *(→ near/at PARITY)*
- **briefing** — snooze-task / renew-document one-tap actions. *(→ PARITY)*
- **knowledge-graph** — stale-label gap fixed (live resolution). *(only the graphics-deferred radial viz remains)*
- **sharebox** — copy-space-ID button. *(file uploads + Realtime still open)*

**Shared attachment layer** (multi-file incl. PDF, on the device blob store —
`attach/Attachment.kt` + `AttachmentsSection.kt`) is built and wired into
Documents, Books, Places, and Finance — Lever #1 from the old audit, done.

**Still genuinely open** (see the handoff for the network-gated split):
Sharebox file uploads + Realtime · Photos Google-Photos import · Time Machine
createdAt existence grid · Places multi-photo grid · Station Cat mood logic +
audio · Settings (calendar push, two-way Telegram, app-lock, threshold
inputs). **Graphics-parked:** Orrery solar system, KG radial graph, Station
Cat face. **Product calls for Alek:** Recall (flashcard vs resurfacing),
Notifications (device-alarms vs activity feed).

## Tally

**20 PARITY · 17 PARTIAL · 3 DIVERGED (by design) · 1 THIN** — 41 modules.
*(Pre-delta snapshot; the block above supersedes ~11 of the PARTIAL rows.)*

The prior doc's "13 / 22 / 4" was stale in the pessimistic direction: the
Tier-B pass filled most of the named gaps. No module is a text stub; every
native screen implements its module's core reason to exist. The remaining
PARTIALs are each missing one or two specific web features, and they cluster
into a few shared themes (see "Biggest levers").

## Matrix

### Core
| Module | Native | Web | Verdict | Gap / notes |
|---|---|---|---|---|
| today | 260 | 113 | **PARITY** | Meets/exceeds (inline task + habit check-ins, TTS). Surprise-me is idea-only vs web's cross-module pool; weather is city vs geolocation. |
| daily-paper | 363 | 448 | **PARITY** | Masthead, grounded AI editorial w/ callbacks, editor's pick, docket, on-this-day, PDF export, Telegram. Minor: docket omits bills; no editor's-pick re-roll. |
| tasks | 409+93 | 338 | PARTIAL | No first-class named **Projects** (free-text field); snooze sets a date but never hides snoozed tasks. Extras: priority sort, completedDate, status-nudge. |
| ideas | 130+49 | 118 | PARTIAL | No **voice dictation**; no per-row created-date. Extra: tags + tag filter. Promote→Task present. |
| places | 473+296 | 492 | PARTIAL | No **link contacts to a place**; single photo vs web photo grid. Extras: geocode find-on-map, geofence alert, camera. |
| links | 219+65 | 145 | PARTIAL | No **tap-to-open in browser** (Share + static URL only). Extra: real YouTube thumbnail fetch/cache. |
| command | 214 | 131 | PARTIAL | No **voice/mic input**. NL parse→confirm→create present; extras: keyless quick buttons, local due-date parse. |
| finance | 1048 | 549 | PARTIAL | No **PDF bill attachments**, sub renewal-date/notes/editable-category, manual payment-history entry, CSV dedup/sub-matching. Extras (exceed web): whole **Ledger tab** + **AI receipt scan**. |
| education | 584+127 | 495 | PARTIAL | Near-parity; only real gap: stores a reading-list tag but doesn't **render the tagged Links list**. |
| orrery | 113 | 173 | PARTIAL | Missing the **animated SVG solar system** (the signature viz) — renders as a flight-log list; drops the weekly-activity/speed signal. *Graphics-deferred.* |

### People
| Module | Native | Web | Verdict | Gap / notes |
|---|---|---|---|---|
| contacts | 419 | 198 | **PARITY** | All fields + search + tag filter. Extras: import-from-phone, camera, richer search. |
| recipes | 473 | 367 | **PARITY** | Full (scaling, cook log, grocery tab, keep-awake). Extra: camera capture. |
| packing | 268 | 183 | **PARITY** | Trips, 4 templates, categories, packed count, strike/delete. |
| documents | 436 | 186 | PARTIAL | **Multi-file/PDF attachments** (native = single image); expiry window hardcoded 30d vs configurable. Extras: AI transcription + summary. |
| quartermaster | 510 | 214 | PARTIAL | **Per-item persisted stock status** (native's stock check is a global dialog, not saved per item). Extra: per-item photo. |
| sharebox | 618 | 584 | PARTIAL | Multi-user Supabase spaces now work. Missing: **file uploads/downloads**, **Realtime** live push (manual refresh), copy-space-ID button, the v1 Drive backend. |

### Memory
| Module | Native | Web | Verdict | Gap / notes |
|---|---|---|---|---|
| milestones | 366 | 299 | **PARITY** | Timeline + recap + AI narrative. Recap omits docs/contacts stat lines, adds workouts. Extra: photo. |
| museum | 134 | 194 | **PARITY** | All six wings, longest-streak logic, cover art rendered. Not a viz gap. |
| time-capsules | 225 | 85 | **PARITY** | Full seal/open/countdown. Extra: photo. |
| collections | 263 | 131 | **PARITY** | Full CRUD + detail. Extra: cover photo. |
| ghost-days | 90 | 103 | **PARITY** | All "on this day" kinds. Extra: workouts. |
| rabbit-holes | 267 | 123 | **PARITY** | Topic/notes/links, active/resolved, reopen. Extras: photo, share. |
| books | 696 | 409 | PARTIAL | Reader is **EPUB/TXT only — no PDF**, and flattens EPUB (no chapter/TOC nav); one ebook+cover vs many files. Extras: ISBN scan → Open Library + auto cover, real paginated reader. |
| photos | 265 | 165 | PARTIAL | No **Google Photos import**. Extras: camera, captions, lightbox. |

### Health
| Module | Native | Web | Verdict | Gap / notes |
|---|---|---|---|---|
| habits | 206 | 116 | **PARITY** | Extras: 7-day strip, undo check-in, all-time count. |
| health | 958 | 186 | **PARITY** | Far exceeds: Garmin CSV import, workouts tab w/ sport pace math, metrics trends — on top of web's daily log + Apple Health. |
| skill-trees | 87 | 71 | **PARITY** | Identical curve/skills/weights. |
| almanac | 270 | 343 | **PARITY** | All correlations/forecasts/what-ifs. Adds a sleep-trend forecast. |

### Insight
| Module | Native | Web | Verdict | Gap / notes |
|---|---|---|---|---|
| ask | 236+107 | 102 | **PARITY** | Exceeds: OpenAI embeddings + LLM answer mode + keyword fallback (web = Gemini embeddings only). |
| ai-assistant | 197 | 122 | **PARITY** | Multiple named conversations (create/switch/rename/delete), grounded by data + conversation tail. |
| entropy | 130 | 77 | **PARITY** | Neglect dashboard across all sources (broader than web's 10). |
| briefing | 171 | 87 | PARTIAL | Missing web's **snooze-task / renew-document** one-tap actions (only Done/Check-in). Extras: habit check-in, rollup counts. |
| knowledge-graph | 227+39 | 275 | PARTIAL | No **radial SVG graph** (*graphics-deferred*, focus+list instead); edges store labels literally so **deleted/renamed nodes show stale labels** (no live resolution/tombstones — the non-deliberate gap). |
| time-machine | 158 | 168 | PARTIAL | Has the scrubber + then-vs-now. Missing web's **createdAt existence grid** across content stores + the "born that day" record list (native tallies dated events instead). |
| recall | 125+41 | 142 | **DIVERGED** | Native = manual flashcard SRS (1→3→7→14→30→90d). Web = cross-module resurfacing of any record w/ Again/Good/Easy + streak. Different products — decision, not a gap. |
| notifications | 221 | 82 | **DIVERGED** | Shared attention feed, then native reimagines the rest as real **device alarms** (AlarmManager) + pinned ticker; web adds **Sharebox activity feed**. Native missing that feed. |

### System
| Module | Native | Web | Verdict | Gap / notes |
|---|---|---|---|---|
| search | 90 | 52 | **PARITY** | Live search, tappable-to-navigate confirmed. Cosmetic: flat list vs grouped headers. |
| tools | 480 | 310 | **PARITY** | Exceeds: 161 currencies + type-ahead + live rates, IANA tz search, unit converter, + native-only Weather & Markets cards. Missing: hand-editable rate table, custom tz labels. |
| qr-sync | 162 | 216 | **DIVERGED** | Web = WebRTC P2P. Native = account-pairing QR (pre-fills sign-in; sync via account). WebRTC isn't viable in Compose — the intended path, not a regression. |
| settings | 578 | 881 | PARTIAL | Substantial real screen. Platform-appropriate misses (Drive, WebAuthn, Web Push). Genuine gaps: calendar push, two-way Telegram linking, app-lock, bill-due/doc-expiry threshold inputs. |
| station-cat | 61 | 182 | **THIN** | More than graphics: missing the **activity-neglect mood logic** (5 tiers from days-since-activity), the **cat-face rendering**, and **Web Audio purr/mew/hiss**. Native = manual pet-counter + static emoji. |

## Biggest levers (shared themes, most impactful first)

1. **A shared multi-file / PDF attachment layer.** The single most common gap —
   it's the whole of Documents' and Photos'(files) partials and a piece of
   Books (PDF + many files), Sharebox (file kind), Finance (bill PDFs), and
   Places (multiple photos). Build one attachment primitive (multi-file, incl.
   PDF, in the blob store) and 5–6 modules move toward parity at once.
2. **Voice input.** Browser SpeechRecognition is gone from every module that had
   it — Ideas and Command. Native already has Vosk wake-word + voice; wiring a
   dictation entry point into these two closes it.
3. **Cross-record links instead of free-text.** Web makes Projects (Tasks),
   people (Places), and bill contacts (Finance) first-class linked records;
   native stores plain strings. A small "link an existing record" picker
   (Search-backed, like Knowledge Graph's) would upgrade all three.
4. **Graphics-deferred visualizations.** Orrery's solar system, Knowledge
   Graph's radial graph, Station Cat's cat-face. **Parked on Alek's assets** —
   excluded from "fix now."  (Knowledge Graph also has a *non-graphics* gap:
   stale labels for deleted/renamed linked records.)
5. **One-off small wins.** Links tap-to-open in browser; Sharebox copy-space-ID
   button; Education render-tagged-Links; Tasks hide-snoozed; Briefing
   snooze/renew actions; Museum/Milestones recap stat lines.

## Product decisions (not gaps — need Alek's call)

- **Recall** — keep the native flashcard SRS, or port the web's cross-module
  resurfacing?
- **Notifications** — native device-alarms vs web's Sharebox-activity feed. Keep
  native's, or add the activity feed on top?

## Not counted as gaps

QR Sync (platform-diverged, intended), Settings' browser-only sections
(Drive/WebAuthn/Web-Push), Theme-from-Photo (deliberately removed, commit
`63cdf76`). Realtime for Sharebox is a native-platform limitation (no websocket
layer yet), tracked under Sharebox's partial.

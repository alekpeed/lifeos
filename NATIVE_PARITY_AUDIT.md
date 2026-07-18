# Native vs Web — parity audit (2026-07-18)

Branch `claude/lifeos-dev-setup-dpipr6`. Native = Kotlin/Compose screens under
`native/composeApp/src/commonMain/.../<area>/`; Web = the reference
implementation in `js/interfaces/default/views/*.js`. Sorted worst-parity first.
LOC via `wc -l`.

> **Method caveat (per the project's own rule):** these verdicts come from
> reading source, not running the app. "PARITY" means feature-surface parity in
> the code, **not device-verified**. Nothing here is "done" until Alek sees it
> work on device. This doc is a map of where to look, not a sign-off.

## Matrix

| Module | Native (LOC) | Web (LOC) | Verdict | Key web features MISSING in native |
|---|---|---|---|---|
| **Sharebox** | 137 | 584 | **THIN** | Entire sharing core absent: Drive folder sync, Supabase v2 (Google sign-in, create/join/switch spaces, members, invite-by-ID, Realtime, per-user delete), file upload/download, backend toggle. Native = local link/note list + OS shareText. *Backend-dependent.* |
| **QR Sync** | 120 | 216 | **THIN** | Real sync missing: WebRTC P2P offer/answer handshake, live session events, last-write-wins + tombstone merge, scanned-URL routing. Native only shows a fingerprint QR + displays a scanned string. *Platform-dependent (WebRTC).* |
| **Station Cat** | 61 | 182 | **THIN** | Activity-driven mood (neglect tiers), Web Audio purr/mew/hiss, CSS-drawn cat face. Native = manual "hello" tap + emoji. *Thin by the no-graphics rule.* |
| **Orrery** | 113 | 173 | **THIN** | Animated SVG solar system (orbit rings, log-scaled sizes, activity-driven speed, pulsing overdue ring, click-to-navigate). Native is a text flight-log. *By design per its own comment.* |
| **Recall** | 125 | 142 | **PARTIAL** | Different model: web resurfaces ANY record (global picker, live titles), 3-grade SRS, streak, open-to-module. Native = standalone 2-button plain-text flashcards. |
| **Notifications** | 138 | 82 | **PARTIAL** | Web = aggregated due-soon/overdue attention feed + Sharebox activity, click-to-navigate. Native = device reminder scheduler (AlarmManager). Disjoint purpose. |
| **Time Machine** | 103 | 168 | **PARTIAL** | Missing the centerpiece: scrubber + per-store "then vs now" count grid + total-records headline. Native lists dated events for a day. |
| **Knowledge Graph** | 227 | 275 | **PARTIAL** | Radial SVG graph (center node + edges, click-to-refocus), deleted-record tombstones. Data features present. |
| **Health** | 172 | 186 | **PARTIAL** | Structured per-day log (sleep/workout/water/weight/notes), rolling 7-day stats, Apple Health import. Native = generic metric/value/unit + bar trend. *Amber.* |
| **Tasks** | 265 | 338 | **PARTIAL** | Kanban board + list/kanban toggle, real Project entities. Native has a rich task model but free-text project + list view only. |
| **Daily Paper** | 302 | 448 | **PARTIAL** | Print/Save-PDF, Send-to-Telegram, Weather, re-roll Editor's Pick, masthead issue number. Core sections present. *Several backend/key-dependent.* |
| **Museum** | 102 | 194 | **PARTIAL** | "Projects Completed" wing absent; book-cover grid reduced to text plaques; per-completion dates dropped. |
| **Places** | 425 | 492 | **PARTIAL** | Map tab (Leaflet markers), multiple photos per place (native = single blob), linked contacts. |
| **Books** | 453 | 409 | **PARTIAL** | Whole e-reader subsystem: EPUB/PDF/TXT attach + in-app reader w/ saved location, manual dates, words-per-page. Native adds ISBN scan + Open Library lookup + auto cover. |
| **Command** | 165 | 131 | **PARTIAL** | Voice STT, `bill` action, dueDate extraction, post-create "Open module". Native AI-parse is key-gated. |
| **Today / Dashboard** | 144 | 113 | **PARTIAL** | Weather w/ geolocation, multi-module due-soon feed, "On this day", "Surprise me". Native adds habit check-in + read-aloud. |
| **Tools** | 386 | 310 | **PARTIAL** | Editable currency rate table + arbitrary currency (~160), user-saved timezones. Native has fixed sets but adds Weather + Markets/crypto. |
| **Contacts** | 222 | 198 | **PARTIAL** | Search, tag-filter, address field, labeled multi phone/email. Native stores plain comma lists. |
| **Ideas** | 118 | 118 | **PARTIAL** | Voice dictation, promote idea → Task, multiline capture. Native adds tags + tag filter. |
| **Ask** | 120 | 102 | **PARTIAL** | Web = semantic memory (Gemini embeddings, cosine ranking, index UI, % match). Native does LLM grounding OR keyword fallback — different, non-embedding approach. |
| **Assistant** | 126 | 122 | **PARTIAL** | Multiple named conversations (per-conversation provider). Native = single flat chat log + Clear. |
| **Milestones** | 284 | 299 | **PARTIAL** | Yearly recap covers ~5 of web's ~13 cross-module stats. Timeline half at parity. |
| **Briefing** | 93 | 87 | **PARTIAL** | Per-item one-tap actions (check-in/snooze/renew), open-to-module, more kinds. Native = tasks + habits only. |
| **Habits** | 115 | 116 | **PARTIAL** | Un-check today, detail editor, notes, editable name. Native adds a 7-day dot strip. |
| **Ghost Days** | 84 | 103 | **PARTIAL** | "Completed tasks/assignments" ghost kind omitted (deferred until they carry native dates). |
| **Links** | 187 | 145 | **PARTIAL** | YouTube thumbnail images (native parses video id but shows a ▶ glyph). Otherwise full. |
| **Finance** | ~1026 | 549 | **PARITY** | Native richer overall (Ledger tab + AI receipt-scan OCR + receipt photo + Markets tab). Missing only detail fields: free-text bill categories/filter, bill attachments, arbitrary dated payment logging, sub renewalDate/notes, show-paid/cancelled toggles. *Amber but clears the bar.* |
| **Documents** | 369 | 186 | **PARITY** | Multi-file attach (native = single blob), configurable expiry. Native adds summary + transcription. |
| **Quartermaster** | 456 | 214 | **PARITY** | Persisted per-item stock status (native's global stock check isn't saved). Native adds per-item photos. |
| **Education** | 584 | 495 | **PARITY** | Near-complete. Only gap: reading-list tag captured but tagged Links never rendered. |
| **Recipes** | 403 | 367 | **PARITY** | Essentially full; native adds keep-awake cook mode. Single-photo blob is the only divergence. |
| **Almanac** | 261 | 343 | **PARITY** | Reproduces Pearson correlations, regression forecasts, both what-if tools. Only "sleep vs tasks-completed" pair absent. |
| **Collections** | 212 | 131 | **PARITY** | Full CRUD + detail; native adds photo attach. |
| **Rabbit Holes** | 221 | 123 | **PARITY** | Full; native adds photo attach + link share. |
| **Time Capsules** | 172 | 85 | **PARITY** | Full seal/countdown/honor-system; native adds photo attach. |
| **Skill Trees** | 87 | 71 | **PARITY** | Identical XP/level curve + computed skills + character level. |
| **Packing** | 195 | 183 | **PARITY** | Faithful port (templates, categories, packed count, strike/delete). |
| **Entropy** | 130 | 77 | **PARITY** | Rebuilt neglect dashboard (days-since-touched per area, sorted). |
| **Search** | 83 | 52 | **PARITY** (near) | Global search across modules. Lacks tap-a-result-to-navigate; results aren't clickable. |

**No direct pair:** Settings (native 577 / web 881 — platform config, differs by
design, not a stub). Web `reader.js` folds into the Books e-reader gap. Web
`themefromphoto.js` has **no native module — by design**: Theme-from-Photo was
deliberately removed (commit `63cdf76`), so it is not an unstarted port.

## Summary

Of ~39 web-backed modules: roughly **13 PARITY, ~22 PARTIAL, 4 THIN.** The old
"42/42 done" framing was wrong, but `SESSION_HANDOFF.md`'s "~half are text stubs"
is now stale in the *pessimistic* direction — most modules have their real
behavior; the PARTIALs are mostly missing one or two named capabilities each, not
everything.

**Biggest genuine gaps (candidates to fix first):**

- **Sharebox and QR Sync** are the two true holes — each missing its whole reason
  to exist (multi-user backend sync; P2P device sync). Both need real infra work,
  not just a Compose port.
- **Recall and Notifications diverged into different products** rather than being
  ported (flashcard toy vs cross-module SRS; device alarms vs attention feed).
  Decide: keep the native reinterpretation, or port the web behavior.
- **Visualization-heavy modules are text-only in native** (Orrery's orrery,
  Knowledge Graph's radial SVG, Time Machine's scrubber, Museum's cover grid).
  Three of the four THIN verdicts trace to missing *visuals*, not missing data —
  and under the "Alek brings the graphics" rule these may be deliberate. Worth a
  yes/no from Alek before investing.
- **Health** is the more real of the two amber modules (no structured daily log,
  rolling stats, or Apple Health import). **Finance** actually *exceeds* web.
- **Books' in-app EPUB/PDF reader** is the biggest single missing subsystem among
  otherwise-solid modules.

**Surprisingly complete:** Education, Quartermaster, Documents, Recipes, Finance,
Almanac — several add native-only capabilities the web never had (AI receipt/ISBN
scan, per-item photos, cook mode). The Memory group (Collections, Rabbit Holes,
Time Capsules, Skill Trees) is essentially done.

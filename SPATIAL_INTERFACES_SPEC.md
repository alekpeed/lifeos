# Spatial Interface(s) — Plan

**Naming, settled 2026-07-13:** same rule as `MOBILE_INTERFACES_SPEC.md` —
no interface gets a permanent "name brand." This was originally built and
named "Vespera"; the product name is dropped, the spatial *concept*
persists as the first realized instance, registered generically as
**`spatial-1`** (`js/interfaces/spatial-1/`). A future `spatial-2` (or
beyond) would follow the same pattern.

A second full interface (alongside Test Mode) built around the concept
ChatGPT drafted: LifeOS as a place — an orbital station — rather than a
dashboard. Full source concept in `LifeOS_Vespera_Spatial_Architecture_v3.md`
(as uploaded, kept under its original filename since it's a historical
source document, not live spec). This doc is the build-ready version of it:
every space below maps to something the app actually does, or is
explicitly marked as reserved for a module that doesn't exist yet. Nothing
here is decoration with no function behind it — that's the one hard rule
carried over from the source concept.

Status: **v1 built** (2026-07-10, Fable+high, flagged and confirmed per
protocol; renamed to `spatial-1` 2026-07-13). `js/interfaces/spatial-1/`
implements the Hub (now seven district plaques + Central Directory — the
Conservatory district was removed 2026-07-13 when Languages and Chords
were cut from the app entirely, see `CHORDS_APP_HANDOFF.md`), district
door screens, travel transitions, and Spaces hosting the canonical module
views (shared via `js/interfaces/view-library.js`) inside station chrome.
The hub expects its concourse art at `js/interfaces/spatial-1/img/hub.png`
and paints a pure-CSS starfield until that file is uploaded — art is
atmosphere, never load-bearing. Remaining from this spec: per-district
room art (the ~16 other images) and richer per-space chrome.

## Navigation shape

```
Hub → District → Space → Content
```

Camera transitions (fade/zoom/pan) happen between every level, per the
source concept. Hash routing underneath is unchanged — a Space is just a
route (`#/books`, `#/places/map`, etc.) with a spatial skin and a travel
animation instead of an instant cut.

## Scope for v1: build only what's real

The source concept lists ~40+ named spaces across 8 districts. Most
don't have a distinct feature behind them yet — either because the
underlying module isn't built (Japanese, Chords, AI Relay, Drive sync)
or because the space was pure atmosphere (Observation Ring, Atlas Vault,
Meditation Atrium, etc.). Building those now means either shipping empty
rooms or inventing filler content, both of which break the source
concept's own "facilities, not metaphors" rule.

**v1 builds only the spaces below.** Everything else is listed under
"Reserved" per district — not cut permanently, just waiting on the
module that would make it real.

## Hub

| Space | Maps to |
|---|---|
| Grand Concourse | The door-selection screen itself — already exists as the one hub image generated so far |
| Station News | Dashboard's due-soon feed (tasks/bills/assignments/documents) |

**Reserved:** Central Directory (needs global search — not built), AI
Concierge (needs an AI module), Transit Network (this is the travel
animation itself, not a destination), Observation Ring / Recent Activity
Plaza (no feature behind them).

## Archive Wing — knowledge & permanent memory

| Space | Maps to |
|---|---|
| Reception | District lobby/doorway screen |
| Personal Library | Books |
| Education Institute | Education |
| Link Repository | Links (both lanes — video + article) |
| Deep Archive | **Documents** (moved here from Ledger — leases/insurance/warranties are long-term records, not money flow) |

**Reserved:** Reading Hall (redundant with Personal Library), Research
Alcoves (no distinct feature yet).

## Operations Deck — execution

| Space | Maps to |
|---|---|
| Task Operations | Tasks (single room — only one real module here, so no invented sub-rooms) |

**Reserved:** Mission Control (redundant with Hub's Station News),
Project Bay / Planning Room / Briefing Room / Dispatch Center (no
distinct feature beyond what Tasks already does — revisit if
Projects/Areas grows its own feature set).

## Navigation Bay — geography & exploration

| Space | Maps to |
|---|---|
| Navigation Bridge | Places' map tab |
| Places Gallery | Places' visited tab |
| Expedition Planning | Places' want-to-go + bucket-list tabs |

**Reserved:** Journey Log (would need a cross-place visit-history timeline
— not built), Atlas Vault / Observation Deck (no feature behind them).

## The Ledger — financial & administrative

| Space | Maps to |
|---|---|
| Bills Office | Finance's Bills tab |
| Subscriptions Bureau | Finance's Subscriptions tab |
| Treasury | Finance's Yearly Spend tab |

**Reserved:** Records Hall / Planning Office / Secure Vault — these were
the net-worth/savings-goals rooms; shelved along with that feature (see
`PROJECT_SPEC.md`). Revive if that feature comes back.

## Personal Quarters — personal life

| Space | Maps to |
|---|---|
| Living Room | District lobby/doorway screen |
| Kitchen | Recipes |
| Communications Lounge | Contacts |
| Memories Gallery | Milestones (next module to be built — space reserved now, wired up once it ships) |

**Reserved:** Reflection Room (good future home for an AI-written yearly
recap), Guest Lounge (good future home for the Sharebox companion-app
collaboration space).

## Conservatory — learning & mastery

**Not built in v1 — no module here exists yet.** Japanese and the
chord/voicing tool are both still on the roadmap. Until at least one
ships, this district is a "coming soon" door on the Hub, not a built-out
space.

**Reserved (for when the modules exist):** Language Garden (Japanese),
Music Studio / Composition Room (Chords — merge into one space, not two),
Practice Pods (possible home for the future unified habit/streak
tracker). Performance Hall / Meditation Atrium / Botanical Walk have no
feature behind them — cut permanently unless a real feature emerges.

## Systems Core — infrastructure

| Space | Maps to |
|---|---|
| Core Control | Settings |

**Reserved:** Synchronization Hub (Google Drive sync, once built —
the name is exact, keep it), AI Relay (the future Claude/ChatGPT/Gemini
modules — matches the label already used in the Hub image), Automation
Lab (possible home for Telegram integration). Power Distribution /
Diagnostics / Security Center have no feature behind them, except that
Diagnostics is the likely home for the future **Tools** module (utility
converters) once built.

## v1 image count

Hub (1, exists) + Archive Wing (5) + Operations Deck (1) + Navigation Bay
(3) + The Ledger (3) + Personal Quarters (4) + Systems Core (1) = **~17
images**, vs. ~40+ implied by building every district to full source-doc
depth. Every one of the 17 maps to a real, currently-built feature.

## Open items before building

- Room art doesn't exist yet beyond the one Hub image. Per earlier
  discussion, the image-generation brief for the remaining spaces gets
  written once core module build-out is further along (matches
  `PROJECT_SPEC.md` §5, which puts additional interfaces after the
  remaining modules).
- Actual implementation (hotspot regions, travel animations, a new
  `js/interfaces/vespera/` folder) is Tier 2 work — flag the model switch
  before starting it, per the standing protocol.

## Possible: HUD-chrome visual direction (not built, saved for later)

While iterating on hub label alignment (2026-07-10), a standalone concept
mockup explored a different visual language entirely — worth revisiting
even though it isn't what shipped. Rather than labels that try to look
painted into the scene, this direction leans into the labels being
*visibly* a HUD layer on top of the station, and makes that layer good
enough to be a feature rather than a compromise:

- **Reactor core spine** — a pulsing hexagonal core down the center of
  the concourse, layered depth (three nested hexes at different scales/
  delays), a glowing vertical spine connecting it top to bottom.
- **Glass district panels** — blurred/translucent cards with a moving
  glare that tracks the cursor, a light-sweep animation on hover, a
  metallic gradient edge, cut/octagon corners to match sci-fi panel
  language rather than plain rounded rectangles.
- **Corner telemetry HUD** — live clock, orbital position, power/crew/
  signal readouts in the four corners, purely atmospheric (no real data
  behind them) but sells "this is a live station" well.
- **Warp transition** — clicking a district triggers a zoom-toward-the-
  doorway animation with an "ESTABLISHING LINK" overlay before arriving,
  richer than the current plain zoom.
- Chromatic-aberration masthead text, parallax starfield (Canvas, cursor-
  driven depth), full boot-in stagger sequence on load.

Layout leaves plenty of room to grow into (per-panel status/stats, more
telemetry, alert states) if this direction gets picked up later. The
full working reference is a standalone HTML/CSS/Canvas mockup (not in
this repo — built as a one-off Artifact, not wired to real data). Revisit
by asking for it to be rebuilt/ported into `js/interfaces/vespera/` if
this direction is chosen instead of (or blended with) real-signage
integration.

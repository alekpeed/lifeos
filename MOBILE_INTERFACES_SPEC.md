# Mobile Interface(s) — Planning Doc

**Naming, settled 2026-07-13:** no interface gets a permanent "name brand"
— not "LCARS," not "NEXUS," not anything else. Alek's plan is to build
several interchangeable mobile interfaces over time, so this doc tracks the
*category* (mobile interfaces in general — device detection, module
curation, packaging, functional requirements every one of them has to
meet) rather than a single named product. Individual interfaces are
registered generically: the first one is `mobile-1`
(`js/interfaces/mobile-1/`), built from Alek's own Figma mockup. A second,
third, etc. would be `mobile-2`, `mobile-3` and so on, each its own
folder/registry entry, each free to look completely different — this doc's
plumbing, module list, and functional contracts apply to all of them; only
the visual layer is per-interface.

**Status:** `mobile-1` is built and shipped (see "Visual direction —
realized as `mobile-1`" below) — but only the visual layer and its own
dashboard-route navigation.
The device-detection/module-curation/packaging plumbing this doc tracks
predates it and was built mobile-interface-agnostically on purpose; how
much of it `mobile-1` actually plugs into is tracked honestly in "Still
open" below, not assumed.

**Revised 2026-07-12 (pre-`mobile-1`):** this was originally scoped as "an
LCARS skin over the existing app," then revised to a new, separate
initiative — **the mobile remote** (see the "Device philosophy" note in
`PROJECT_SPEC.md`, section 3): a deliberately stripped-down phone
experience, not a second complete copy of the desktop app. No Spatial 1, no
spatial "living world," no attempt at parity with desktop. Just the
modules genuinely useful away from your desk, syncing through the same
backend as everything else. That framing still holds; only the "LCARS" name
and the assumption of a single interface have been dropped.

## Plumbing built so far (2026-07-12, updated 2026-07-12)

All interface-agnostic — none of it depends on the eventual visual design,
so it's correct regardless of what the design package says.

- **Device detection** — `js/data/device-context.js` exports two separate
  signals, used for two separate questions:
  - `isTouchPrimaryDevice()` — coarse pointer, no hover. Any touch-first
    device, installed or not, browser tab or not.
  - `isMobileRemoteContext()` — `isTouchPrimaryDevice()` *and* an
    installed-app launch context (`display-mode: standalone`/`fullscreen`/
    `minimal-ui`, or iOS Safari's legacy `navigator.standalone`).
    Standalone alone isn't enough — installing the PWA on a desktop also
    reports `display-mode: standalone`, and would wrongly narrow the
    module set if that were the only signal.
- **Module curation, wired live** — `js/modules.js` carries a `remote: true`
  flag on each module in the draft list below, plus `getRemoteModules()` /
  `isRemoteModule(id)` helpers. `js/shell.js` now actually consumes this:
  `ctx.modules` is filtered to the remote set, and hash navigation to a
  non-remote module redirects to the dashboard, but **only** when
  `isMobileRemoteContext()` is true (installed + touch). A phone browser
  tab that isn't installed deliberately keeps the full module list — the
  escape hatch back to full functionality when you're on a phone but
  haven't set up the dedicated remote.
- **Spatial 1 gated on touch, not install state** (2026-07-12, revised same
  day after Alek caught Spatial 1 — then still named Vespera — loading in
  phone Chrome) — Spatial 1 is marked `touchSafe: false`; the shell falls
  back to Test Mode (the plain default interface, still the full module
  list) on **any** `isTouchPrimaryDevice()`, not just the installed remote.
  Spatial 1's spatial hub navigation genuinely doesn't work on a small
  touchscreen
  regardless of install state, which is a different question from module
  *scope* — that's still gated on the narrower `isMobileRemoteContext()`
  above. The fallback never persists over your real (synced)
  `activeInterface` preference.
- **APK packaging groundwork** — `.well-known/assetlinks.json` scaffolded
  with placeholder values (real ones need a package name and signing-cert
  fingerprint that don't exist until there's an actual Android project).
  `manifest.json` was already TWA-ready going in: `display: standalone`,
  full icon set including maskable 192/512.
- **Mobile layout bug fixed along the way** — Test Mode's `.mer-root` grid
  had `min-height: 100vh` with no `align-content`, so its two rows (nav
  bar, canvas) stretched to fill the screen by default on short content
  (a sparse Dashboard: collapsed nav + "nothing due yet"), pulling content
  apart with dead space above and below. `align-content: start` fixes it;
  desktop is unaffected since its nav column is normally taller than any
  realistic viewport anyway.
- **`mobile-1` registered as a real interface** (2026-07-13) —
  `js/interfaces/mobile-1/`, built from Alek's own mockup image mapped with
  real hotspots (see "Realized: mobile-1" below). It inherits the curated
  module list "for free" through `ctx.modules` (the shell computes that
  based on device context, not on which interface is active), but is
  **not** gated to touch/mobile-only the way Spatial 1 is gated off
  non-touch devices — it currently shows up as a switchable interface on
  any device. Whether that should change is an open decision, not an
  oversight — see "Still open."

**A real blocker found while scaffolding this, worth flagging rather than
quietly working around:** Digital Asset Links verification (the file
above) has to be served from the **origin root** — `https://<domain>/
.well-known/assetlinks.json` — not from within a sub-path. If LifeOS
deploys the default GitHub Pages way for a repo named `lifeos`
(`https://alekpeed.github.io/lifeos/`), the origin root is
`alekpeed.github.io/`, which this repo doesn't control — that's whatever
publishes to the *account's* root Pages site, a different repo (if one
even exists) or nothing at all. No CNAME file was found in this repo
either, so there's currently no custom domain in play. Practically: **a
real installable TWA needs either a custom domain for LifeOS specifically
(so this repo controls its own origin root), or the asset-links file
placed in whatever repo actually owns `alekpeed.github.io`'s root** — it
can't just live here as-is and work. Filed as an open question below
rather than guessed at.

## Decisions already made

- **Mobile-first, mobile-only for v1.** Desktop is explicitly deferred —
  revisit later, not part of this build. *(Not yet enforced for `mobile-1`
  — see "Still open.")*
- **This is a new, dedicated interface**, not a filtered/reskinned version
  of Test Mode. The "chrome-skin vs. new interface" fork from the first
  scoping round is resolved: it's its own thing, purpose-built as a remote
  control, not trying to look or behave like the desktop app at all.
- **Curated module set, not the whole app.** Breadth-over-depth from the
  first scoping round no longer applies — the whole point now is *not*
  shipping everything. See the draft list below.
- **Full console energy** — sound and motion are in scope. See Motion &
  Sound below for the accessibility guardrails that come with that.
- **Alert styling is functional, not decorative** — the interface should
  actually react to real due/overdue data, not just look alert-y.
- **Visual lineage: Voyager / later-90s LCARS** — cooler and denser than the
  classic minimal TNG look; more button grid, less empty black space.

## Draft: what actually ships on the remote

A starting proposal, not a locked list — Alek edits this before anything
gets built. Grouped by *why* each one earns a spot in your pocket, since
"decoration with no function" is the one hard rule this app doesn't break
(same rule Spatial 1's own spec holds itself to).

**Home base**
- Today / Dashboard
- The Daily Paper (a quick morning glance, not the print/AI-editorial ceremony)
- Tasks (quick add, check off)

**Capture-anywhere** — the actual reason a stripped phone app is worth
building at all
- Ideas (fleeting-thought capture)
- Links (save something you found on your phone)
- Documents (camera-to-data scan — this flatly needs a phone camera)
- Photos (import/attach on the go)

**Physical-world companions** — only make sense away from a desk
- Places (find a spot, the "check nearby places" nudge)
- Habits (check in when it's actually happening)
- Health (log sleep/workout/water right after it happens)
- Recipes (cook-along reference, grocery list)
- Trip Packing Lists (checking off while you actually pack)
- Quartermaster (who has my stuff, while you're the one who needs it back)

**Idle-moment tools**
- Recall (spaced review — waiting in line, on a train)
- Search
- Contacts (look someone up before a call)
- Tools (currency/unit convert while traveling)
- QR Airgap Sync (pairing already needs a phone camera)
- The Station Computer (the voice remote — this is arguably the most
  mobile-native thing in the whole app; scoping doc for it doesn't exist
  yet, tracked separately)

**Settings, trimmed** — App Lock, sync status, account. Not full AI
provider config or Automations tuning; those stay desktop-side.

**Deliberately left off**: Spatial 1 (desktop's whole reason to exist), The
Orrery, Knowledge Graph, Time Machine, The Almanac, Museum of Finished
Things, Skill Trees, Entropy — all either need more screen than a phone
gives you, or are "sit down and reflect" tools rather than "quick action"
ones. (Languages and Chords/Harmony Study were also on this list at one
point — both were cut from the app entirely 2026-07-13, so they're no
longer relevant here at all, not just left off mobile.)
Education, Milestones' Yearly Recap, Collections, and Rabbit Holes are
judgment calls either way — left off the draft to keep v1 lean, easy to
add back in.

## Functional requirements — what each screen has to do

**Division of labor, settled 2026-07-12:** Alek owns the visual design
end to end — shapes, sizes, coordinates, everything about *how* this
looks, placed by hand in Figma (irregular/non-uniform shapes ruled out
deriving a clean spacing "system," so exact per-element coordinates are
the right call here, not an inferred rule set). This section is the other
half: what each screen has to be *capable of*, with zero opinion about
how any of it looks. No shapes, no colors, no coordinates below — just
the contract each screen has to fulfill. Grounded in what each module
actually does today, not new functionality invented for this pass.

**Global requirements, apply everywhere:**
- **Minimum tap target: 44×44 CSS px**, with real spacing between
  adjacent targets — the standard accessibility floor (iOS HIG / Android
  Material both land near here), non-negotiable regardless of how dense
  a screen's layout gets.
- **All text is real, live DOM text — never baked into an image.**
  Almost everything below shows dynamic, personal data; a picture can't
  do that.
- **A way back to Home, reachable in one tap, from anywhere.** Every
  screen needs it; where exactly it sits is Alek's call.
- **Alert-color states** (nominal / caution / critical, per the mapping
  already in this doc) apply specifically to: Dashboard's and Daily
  Paper's due-soon items, Documents' expiring-soon items, and Tasks'
  overdue items — driven by the same due-soon/overdue thresholds already
  computed elsewhere in the app, nothing new to calculate.

**Per-screen contract:**

- **Today (Dashboard)** — *Shows:* due-soon agenda with overdue items
  flagged, an on-this-day flashback, one "Surprise me" pick. *Does:* tap
  a due item to jump to its module; tap Surprise Me for a new pick and a
  way to jump to it.
- **Daily Paper** — *Shows:* today's agenda, on-this-day, a tickable
  habit checklist, the editor's pick, weather, the AI editorial (when a
  key's configured), a small almanac of counts. *Does:* tick a habit off
  the checklist; retry/regenerate the editorial. *Not needed here:* the
  Print button (no printer on a phone) — Send-to-Telegram may still be
  worth keeping, genuinely useful on the go.
- **Tasks** — *Shows:* task list — title, due date, priority, overdue
  flag, "waiting on someone" state. *Does:* quick-add a task, check one
  off, snooze it, mark it waiting-on-someone.
- **Ideas** — *Shows:* the running idea list. *Does:* capture a new idea
  in as close to one tap as possible — this is the core reason this
  screen exists — and archive/mark one handled.
- **Places** — *Shows:* place list, a map pin, the "check nearby places"
  nudge. *Does:* trigger the nearby-places check, log a visit, flag
  something to revisit, add a new place on the spot.
- **Links** — *Shows:* watch-later and read-later lists. *Does:*
  quick-add a link (paste a URL), mark something watched/read.
- **Recipes** — *Shows:* recipe list, a selected recipe's ingredients and
  steps, the grocery list. *Does:* reference a recipe hands-on while
  cooking, log "made it," generate/view the grocery list.
- **Documents** — *Shows:* document list. *Does:* the camera-to-data scan
  flow — photograph a document, review the AI-drafted fields, save. This
  is the headline reason this module belongs on a phone at all.
- **Contacts** — *Shows:* contact list and detail (phone, email, notes).
  *Does:* look someone up, jot a note right after a call.
- **Photos** — *Shows:* albums, a photo grid. *Does:* import from Google
  Photos, browse an album.
- **Trip Packing Lists** — *Shows:* a trip's checklist. *Does:* check off
  packed items, add a freeform item, start a list from a template.
- **Quartermaster** — *Shows:* inventory and the lending ledger. *Does:*
  mark something lent or returned, look up who has something.
- **Habits** — *Shows:* habit list with today's check-in state and
  current streak. *Does:* check a habit in or out for today.
- **Health** — *Shows:* recent logs, 7-day rolling averages. *Does:*
  quick-log today's sleep, workout, water, or weight.
- **Recall** — *Shows:* today's due-for-review queue. *Does:* run a
  review — show the item, grade Again/Good/Easy — and schedule a new item
  via search.
- **QR Airgap Sync** — *Shows:* pairing status. *Does:* scan a QR to
  pair, or generate one to be scanned.
- **Tools** — *Shows:* currency/unit converter, saved timezones. *Does:*
  convert a value, compare a saved timezone against local time.
- **Search** — *Shows:* results grouped by module. *Does:* run a query,
  tap a result to jump to it. Edge case worth flagging: a result can
  belong to a desktop-only module (Knowledge Graph, The Orrery, etc.) —
  needs *some* honest handling (even just "this lives on desktop"), not a
  dead tap.
- **Settings, trimmed** — *Shows:* App Lock status, sync status, account.
  *Does:* toggle App Lock, trigger a sync, sign in/out. Explicitly not
  here: AI provider config, Automations tuning — those stay desktop-side.
- **The Station Computer** — reserve a spot in the nav and a persistent
  trigger control, but it's a "coming soon" stub for this pass — no real
  function yet, tracked separately.

## How a static design becomes a live screen

Real question, worth real documentation rather than living only in chat:
Alek's reference imagery (and eventually his Figma placements) show
specific numbers — a 68% gauge, a "3" notification badge, a live clock,
exactly three priority cards. None of that is meant to be reproduced
literally. Here's the actual mechanism that reconciles a static design
with the live, reactive screen it has to become:

- **Every visual widget is a template, not a picture of one state.** A
  stat ring in the design is a ring-drawing rule (size, stroke, color,
  glow) that gets built as a real component taking a number as input —
  not a frozen 68%. Today it renders whatever the real value is; that
  value changes, the same component redraws.
- **Updates ride the app's existing event system — nothing new to
  invent.** Every action that changes data already fires an event; every
  screen already listens for those events and redraws itself
  automatically when one fires. This is the exact mechanism every
  existing module already uses (check a habit off right now and watch
  the streak update instantly) — the remote doesn't need its own version
  of this, it's the same wiring with a different visual skin on top.
- **A live clock is just a ticking timestamp**, unrelated to app data,
  updating on its own.
- **Repeating content (the Priority Feed, or any list) is one card
  template, not N individually placed cards.** The reference shows
  exactly 3 — real due items could be 0 or 12 on any given day. Design
  one card's shape/spacing/severity-color rule; it gets repeated once per
  real item, however many there are, not locked to the mockup's count.

Practical implication for the Figma work: design each distinct widget
*type* once, with a placeholder value just to see how it reads with real
content in it — not every possible value it could ever show.

**Where `mobile-1` actually landed vs. this principle (2026-07-13):** its
dashboard/home route does *not* follow this — it ships Alek's mockup image
directly as background art with real click regions mapped onto it, mockup
numbers and all (a specific temperature, specific fake task titles — see
`js/interfaces/mobile-1/index.js`'s file comment for why: the image is
decorative chrome, not a data surface, and every hotspot still navigates
somewhere real). That was a deliberate, explicit call this session, not an
oversight — Alek wanted the actual design shipped as-is rather than
re-templated. Every *other* screen in `mobile-1` (anything reached by
tapping a hotspot) is the real thing: the shared, live, event-driven view
library described above, same as every other interface uses. So the
principle above is fully true for the app in general and for `mobile-1`'s
non-home screens; the home screen specifically is a deliberate exception.
Whether a future mobile interface follows the templated-widget approach
instead is an open, per-interface design choice, not a rule this doc
enforces.

## Resolved: how does the app know it's the remote?

**Decided:** a real installable Android package via **Trusted Web
Activity (TWA)** — not a Capacitor-style separate build. A TWA wraps the
*existing* PWA (same code, same URL) in a thin native shell for the Play
Store; it doesn't bundle a second copy of the app or need a build step,
which matters for a codebase that's been deliberately zero-build-step
throughout. Distribution (real APK) and behavior (curated nav) are two
separate axes, not one either/or:
- **Distribution** — TWA, so it installs as a real app with a home-screen
  icon, no browser chrome, Play-Store-submittable.
- **Behavior** — `isMobileRemoteContext()` (built, see above), requiring
  both an installed-app launch context *and* a touch-primary device. This
  means a desktop PWA install still correctly gets the full app, and only
  an actual phone-installed launch gets the curated remote — regardless of
  whether that install came through the Play Store TWA or a plain "Add to
  Home Screen."

Still open: the origin-root blocker above (needs a custom domain or the
right repo before a real TWA can be Digital-Asset-Links-verified), and the
actual Bubblewrap/keystore/Play Console steps, which are manual and
external to this environment (no Android SDK or Play Console access here)
— tracked as a to-do, not attempted.

## Visual direction — realized as `mobile-1`

**"Deck Nine" and "Red Squad" (below, struck from active use) were
speculative starting points from before any real design existed** — kept
here only as a historical record, not live guidance. Alek's own design
work is the actual direction, realized concretely as `mobile-1`
(`js/interfaces/mobile-1/`): a general sci-fi HUD/console aesthetic (dark
ground, magenta/hot-pink + cyan neon accent pairing, beveled glass-panel
cards with soft glow, circular gauge/ring readouts, badge counters,
sparkline mini-charts, a bottom nav bar with a central circular "core"
button) rather than the strict rounded-pill/elbow-connector LCARS grammar
originally scoped — a deliberate evolution, not a gap to reconcile.
Real palette/shape values come from Alek's own mockup image, shipped
directly (see "How a static design becomes a live screen" above for how
that reconciles with live data), not from anything speculative below. A
future `mobile-2` or later interface is free to look nothing like this.

<details>
<summary>Struck: original speculative starting palettes (historical record only)</summary>

**Variant "Deck Nine" — cooler, mission-console.**
| Role | Hex | Notes |
|---|---|---|
| Ground | `#000000` | true black, LCARS' non-negotiable |
| Panel/pill primary | `#9999CC` | cool lavender-blue |
| Panel/pill secondary | `#CC99CC` | dusty mauve |
| Accent / active | `#FF9966` | warm orange, used sparingly against the cool field |
| Alert — caution | `#FFCC66` | amber |
| Alert — critical | `#CC6666` | muted red, not neon |
| Text on black | `#FFFFFF` | |
| Text on pills | `#000000` | pills are light, text stays dark for contrast |

**Variant "Red Squad" — warmer, alert-forward.**
| Role | Hex | Notes |
|---|---|---|
| Ground | `#0A0A0F` | near-black with a cool bias |
| Panel/pill primary | `#FF9F55` | warm amber-orange |
| Panel/pill secondary | `#C87DA0` | rose |
| Accent / active | `#7FB2E5` | cool blue, the "cold" note against a warm field |
| Alert — caution | `#F2C14E` | |
| Alert — critical | `#E0524A` | brighter, more urgent than Deck Nine's |
| Text on black | `#F5F0E6` | warm-white, not pure white |
| Text on pills | `#101010` | |

</details>

## Typography

The real LCARS font (Okuda) was never ours to use. The app already vendors
**Oxanium and Rajdhani** (`vendor/fonts/`) for Spatial 1's district signage —
both are condensed/geometric sci-fi faces that would fit a console-style
mobile interface too, zero new asset weight. **Not actually used by
`mobile-1`, though** — its dashboard route's type is baked into Alek's
mockup image (not live text), and its own slim header/module-screen chrome
uses the system font stack (`var(--font-ui)`), same as Test Mode. Oxanium/
Rajdhani remain available, unused, for a future mobile interface that
wants real DOM type instead of image-baked text.

## Alert styling — proposed mapping (confirm or adjust)

| State | Trigger | Color |
|---|---|---|
| Nominal | default | primary/secondary pills, no alert tint |
| Caution | something due soon (reuses existing due-soon thresholds) | amber |
| Critical | something overdue | red |

Driven by the same due-soon/overdue data the Dashboard and Daily Paper
already compute — no new logic, just a new coat of paint reacting to
numbers that already exist.

## Motion & sound

In scope: a boot/power-up sequence on switching into the interface, panel
highlight/slide transitions on navigation, and short button-press tones.

**Decided: sound is generated, not sourced.** The app already has a full
Web Audio synthesis engine (`js/audio/synth.js` — oscillators, ADSR, EQ;
originally built for the now-cut Chords module, and now also driving the
Life as Music ambient background feature) — reused here for simple
procedural UI tones and a boot chime rather than sourcing external audio
files. No licensing question, no new assets, reuses infra that already
works.

Two hard constraints, non-negotiable regardless of how "full console" this
gets:
- **Respects `prefers-reduced-motion`** — same as every other animated
  thing in this app already does (The Orrery, card reveals, etc.). Motion
  drops to instant/static, no exceptions.
- **Sound needs an explicit, persistent mute control**, defaulting to a
  sensible state — browsers block un-requested autoplay audio anyway, so
  this isn't optional even before it's a courtesy.

## What "v1" concretely means

In scope: the curated module list above (confirmed as-is), restyled
chrome and navigation per whichever design lands, the alert-color
behavior above, generated motion/sound per the constraints above, mobile
only.

Out of scope for v1: desktop entirely (not yet enforced for `mobile-1` —
see "Still open"), every module *not* on the curated list (`mobile-1`'s
wheel currently reaches 6 of the ~23 on the list — also "Still open"), any
custom illustration/room art beyond what an individual mobile interface's
own design brings, any change to a module's actual underlying
functionality — views behave identically to their desktop counterparts,
they just live in a smaller shell and there are fewer of them — and, for
now, the Station Computer itself (reserved a spot on the list, stubbed as
"coming soon" until it gets its own real build pass).

## Still open

1. **`mobile-1`'s mobile-only gating** — it's registered as a normal
   switchable interface with no `touchSafe`-style restriction, so it
   currently shows up as an option on desktop too, unlike this doc's
   "mobile-first, mobile-only for v1" decision. Whether to gate it off
   non-touch devices (Spatial-1-style) is an open call, not yet made.
2. **`mobile-1`'s module coverage** — its wheel reaches 6 modules
   (Today/Tasks/Settings/Ideas/Places/Search) against the ~23 on the
   curated list below. The other ~17 are only reachable by URL/search,
   not from `mobile-1`'s own navigation. Whether that's intentional (a
   deliberately minimal v1 surface) or a gap to close with more hotspots/
   a directory screen is Alek's call.
3. **The per-screen functional contract** — the requirements below (what
   each screen has to *show* and *do*) haven't been checked against
   `mobile-1`'s actual hosted views screen-by-screen. They're hosted via
   the same shared view library every interface uses, so most of this is
   likely already satisfied, but it hasn't been verified as a pass.
4. **Future mobile interfaces** — `mobile-2` and beyond don't exist yet.
   Nothing about `mobile-1`'s specific visual choices (its palette, its
   image-based dashboard) should be assumed to carry over; only this
   doc's functional/plumbing layer does.
5. **The origin-root blocker** — a real TWA needs either a custom domain
   for LifeOS, or the asset-links file living in whatever repo actually
   controls `alekpeed.github.io`'s root. Not resolved.
6. **The actual packaging steps** — keystore generation, Bubblewrap CLI,
   Play Console setup. Manual/external, not attempted in this environment.
7. **The Station Computer's real build** — wake word, token-mint backend,
   WebRTC session — tracked separately, not part of this pass.

## Design process note (2026-07-12, revised 2026-07-13)

Worth recording since it shaped (and then didn't quite hold up against)
the plan above: the original idea was "GPT designs the images, then
reports back exact coordinates for every button." Ruled out at the time —
AI-generated images aren't geometrically precise (wobbly edges, drifting
alignment at the pixel level), and asking a model to then extract exact
coordinates from its own imprecise image compounds the problem rather than
solving it. A concrete, already-lived example: Spatial 1's door hotspots
were genuinely painful to get right because the art had real perspective
(depth, a vanishing point), which meant the click regions needed true
polygons matching that distortion, not simple rectangles — and getting
those polygon points right meant guessing blind against a fixed image with
no way to read out real coordinates. The conclusion drawn at the time:
Alek placing exact coordinates by hand in a real design tool sidesteps the
whole failure mode.

**What actually happened for `mobile-1`, in practice:** a hybrid, not a
clean win for either side of that argument. Alek's mockup image genuinely
was AI-generated. Getting real hotspot coordinates out of it did *not* use
Figma at all — it used a GPT-drawn reference trace (red outlines over the
mockup) run through actual image-processing (color thresholding, connected-
component clustering, contour extraction), not hand-placement. It also did
*not* go smoothly on the first pass: the first extraction over-merged
several separate buttons into one region and mischaracterized what was
missing, caught and corrected only after Alek directly flagged it as
wrong. So the original worry (imprecise AI output compounding into worse
extracted coordinates) was real and did happen once — but got caught and
fixed through direct correction rather than derailing the approach
entirely. Net: don't treat "AI-generated image → extracted coordinates" as
categorically ruled out anymore; treat it as a real option that needs a
verification/correction pass, same as any other coordinate-extraction
method would. Hand-placement in a real design tool (Figma's Dev Mode
inspector, or its REST API for reading real vector-layer geometry) is
still the better option **when the source has actual vector data to read**
— it just doesn't apply to a flattened AI-generated raster image, which
has none.

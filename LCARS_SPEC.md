# LCARS Interface — Handoff Doc

Status: **plumbing in progress, visual interface on hold.** Alek is
bringing a design package for the actual LCARS chrome later — until then,
the interface-agnostic groundwork (device detection, module curation, APK
packaging groundwork) is being built now, since none of it depends on what
the interface ends up looking like. Original execution throughout once the
visual work starts — no Okuda font, no ripped assets, nothing that could
get flagged as a copyright issue. The goal is "unmistakably LCARS-flavored,"
not "a prop replica."

**Revised 2026-07-12:** this is no longer "an LCARS skin over the existing
app." LCARS is now confirmed as the visual language for a new, separate
initiative — **the mobile remote** (see the "Device philosophy" note in
`PROJECT_SPEC.md`, section 3): a deliberately stripped-down phone
experience, not a second complete copy of the desktop app. No Vespera, no
spatial "living world," no attempt at parity with desktop. Just the
modules genuinely useful away from your desk, LCARS-styled, syncing
through the same backend as everything else.

## Plumbing built so far (2026-07-12)

All interface-agnostic — none of it depends on the eventual visual design,
so it's correct regardless of what the design package says.

- **Device detection** — `js/data/device-context.js`, `isMobileRemoteContext()`.
  Requires *both* an installed-app launch context (`display-mode: standalone`
  /`fullscreen`/`minimal-ui`, or iOS Safari's legacy `navigator.standalone`)
  *and* a touch-primary device (coarse pointer, no hover). Standalone alone
  isn't enough — installing the PWA on a desktop also reports
  `display-mode: standalone`, and would wrongly trigger the remote if that
  were the only signal. A regular browser tab, on any device, is never
  treated as the remote — only an actual installed-app launch is.
- **Module curation** — `js/modules.js` now carries a `remote: true` flag on
  each module in the draft list below, plus `getRemoteModules()` /
  `isRemoteModule(id)` helpers. Not yet wired into any interface's actual
  navigation (there's no remote-facing interface to wire it into yet) —
  this is the source of truth waiting to be consumed once one exists.
- **APK packaging groundwork** — `.well-known/assetlinks.json` scaffolded
  with placeholder values (real ones need a package name and signing-cert
  fingerprint that don't exist until there's an actual Android project).
  `manifest.json` was already TWA-ready going in: `display: standalone`,
  full icon set including maskable 192/512.

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
  revisit later, not part of this build.
- **This is a new, dedicated interface**, not a filtered/reskinned version
  of Equator. The "chrome-skin vs. new interface" fork from the first
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
(same rule Vespera's own spec holds itself to).

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
- Languages (flashcard review)
- Search
- Contacts / Conversation Starters (before a call)
- Tools (currency/unit convert while traveling)
- QR Airgap Sync (pairing already needs a phone camera)
- The Station Computer (the voice remote — this is arguably the most
  mobile-native thing in the whole app; scoping doc for it doesn't exist
  yet, tracked separately)

**Settings, trimmed** — App Lock, sync status, account. Not full AI
provider config or Automations tuning; those stay desktop-side.

**Deliberately left off**: Vespera (desktop's whole reason to exist),
Chords/Harmony Study (needs real screen real estate), The Orrery,
Knowledge Graph, Time Machine, The Almanac, Museum of Finished Things,
Skill Trees, Entropy — all either need more screen than a phone gives you,
or are "sit down and reflect" tools rather than "quick action" ones.
Education, Milestones' Yearly Recap, Collections, and Rabbit Holes are
judgment calls either way — left off the draft to keep v1 lean, easy to
add back in.

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

## Visual direction — two starting points

Two named variants in the Voyager-adjacent family, meant to be reacted to
and merged/adjusted, not picked as-is.

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

Both keep the real LCARS grammar: rounded-rectangle "pills," elbow
connectors joining horizontal and vertical bars, and dense sidebar button
stacks — that shape language is what reads as LCARS, more than any single
color choice does.

## Typography

The real LCARS font (Okuda) is not ours to use. Rather than sourcing a new
webfont, the app already vendors **Oxanium and Rajdhani**
(`vendor/fonts/`) for Vespera's district signage — both are
condensed/geometric sci-fi faces already proven to fit this app's spatial
interfaces. Reusing them here is the default: zero new asset weight, and
it ties LCARS visually to Vespera as "the app's two sci-fi registers"
instead of introducing a third unrelated typeface.

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

**Decided: sound is generated, not sourced.** The Chords module already
has a full Web Audio synthesis engine (oscillators, ADSR, EQ) — reused
here for simple procedural UI tones and a boot chime rather than sourcing
external audio files. No licensing question, no new assets, reuses infra
that already works.

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
chrome and navigation once the design package lands, the alert-color
behavior above, generated motion/sound per the constraints above, mobile
only.

Out of scope for v1: desktop entirely, every module *not* on the curated
list, any custom illustration/room art (this is a chrome build, not a
Vespera-style art project), any change to a module's actual underlying
functionality — views behave identically to their desktop counterparts,
they just live in a smaller, LCARS-styled shell and there are fewer of
them — and, for now, the Station Computer itself (reserved a spot on the
list, stubbed as "coming soon" until it gets its own real build pass).

## Still open

1. **The visual design package itself** — palette (Deck Nine vs. Red
   Squad vs. a merge), typography (default: reuse Oxanium/Rajdhani, no new
   assets), the alert-color mapping above (confirm or adjust) — all on
   hold pending what Alek brings back.
2. **The origin-root blocker** — a real TWA needs either a custom domain
   for LifeOS, or the asset-links file living in whatever repo actually
   controls `alekpeed.github.io`'s root. Not resolved.
3. **The actual packaging steps** — keystore generation, Bubblewrap CLI,
   Play Console setup. Manual/external, not attempted in this environment.
4. **The Station Computer's real build** — wake word, token-mint backend,
   WebRTC session — tracked separately, not part of this pass.

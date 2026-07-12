# LCARS Interface — Handoff Doc

Status: **not started — scoping only.** This is the brief for whoever builds
it (future Claude session, GPT, or Alek by hand), not an implementation.
Original execution throughout — no Okuda font, no ripped assets, nothing
that could get flagged as a copyright issue. The goal is "unmistakably
LCARS-flavored," not "a prop replica."

## Decisions already made (this scoping round)

- **Mobile-first, mobile-only for v1.** Desktop is explicitly deferred —
  revisit later, not part of this build. This is a deliberate exception to
  the app's general desktop-immersive/mobile-companion philosophy, called
  out here so it isn't read as a contradiction later.
- **Whole app reachable in v1**, at chrome level — every module gets the
  reskin, breadth over depth, rather than a few flagship modules done deeply
  first.
- **Full console energy** — sound and motion are in scope, not just a color
  swap. See Motion & Sound below for the accessibility guardrails that come
  with that.
- **Alert styling is functional, not decorative** — the interface should
  actually react to real due/overdue data, not just look alert-y.
- **Visual lineage: Voyager / later-90s LCARS** — cooler and denser than the
  classic minimal TNG look; more button grid, less empty black space.

## Open decision: chrome-skin vs. a new spatial interface

This is the one real architecture fork, and it's yours to make — laid out
straight rather than picked for you.

**Option A — Chrome/skin over the existing rail+canvas layout.**
Reuses Equator's structure and every existing view completely unchanged —
same routing, same data, same module code. The build is a new entry in the
interface registry (`js/interfaces/registry.js`) plus its own stylesheet
and nav chrome, the same shape as how Equator itself is registered. Small,
bounded, low-risk. This is *how the app already supports multiple looks* —
Settings' interface picker just swaps which registered interface mounts.

**Option B — A new spatial interface, Vespera's scale.**
A real console/bridge navigation metaphor: its own layout logic, its own
module-to-panel mapping, its own sense of "place" the way Vespera's
hub → district → space model is. Vespera took real, multi-session effort to
get right (district geometry, travel transitions, room art) — this would be
comparable, not a weekend job.

**My honest lean, given the mobile-first call above:** Option A fits mobile
much better. Vespera's own spec calls desktop "the real immersive canvas"
for exactly this reason — a walk-through-a-station metaphor wants screen
real estate a phone doesn't have. A chrome-skin approach also means v1
ships fast and every module works from day one, satisfying "whole app in
v1" without a second navigation system to design from scratch. If desktop
LCARS becomes a real goal later, Option B could still happen *then*,
informed by what actually worked in the mobile skin.

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
Two hard constraints, non-negotiable regardless of how "full console" this
gets:
- **Respects `prefers-reduced-motion`** — same as every other animated
  thing in this app already does (The Orrery, card reveals, etc.). Motion
  drops to instant/static, no exceptions.
- **Sound needs an explicit, persistent mute control**, defaulting to a
  sensible state — browsers block un-requested autoplay audio anyway, so
  this isn't optional even before it's a courtesy.

## What "v1" concretely means

In scope: every module reachable, restyled chrome and navigation, the
alert-color behavior above, motion/sound per the constraints above, mobile
only.

Out of scope for v1: desktop, any custom illustration/room art (this is a
chrome reskin, not a Vespera-style art project), and any change to a
module's actual underlying functionality — views behave identically, they
just look different.

## Still needs a decision before building starts

1. **Chrome-skin vs. spatial interface** (Option A vs. B above) — the one
   that actually changes the size of the build.
2. **Deck Nine vs. Red Squad** vs. some merge of the two.
3. Confirm the alert-color mapping, or adjust it.
4. Confirm reusing Oxanium/Rajdhani for typography, or say if something
   else should be sourced.

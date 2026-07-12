# LCARS Interface — Handoff Doc

Status: **not started — scoping only.** This is the brief for whoever builds
it (future Claude session, GPT, or Alek by hand), not an implementation.
Original execution throughout — no Okuda font, no ripped assets, nothing
that could get flagged as a copyright issue. The goal is "unmistakably
LCARS-flavored," not "a prop replica."

**Revised 2026-07-12:** this is no longer "an LCARS skin over the existing
app." LCARS is now confirmed as the visual language for a new, separate
initiative — **the mobile remote** (see the "Device philosophy" note in
`PROJECT_SPEC.md`, section 3): a deliberately stripped-down phone
experience, not a second complete copy of the desktop app. No Vespera, no
spatial "living world," no attempt at parity with desktop. Just the
modules genuinely useful away from your desk, LCARS-styled, syncing
through the same backend as everything else.

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

## Open: how does the app know it's the remote?

Not decided — laid out straight rather than picked.

**Option A — same PWA, viewport detects it.** One codebase, no separate
build. A phone-sized screen automatically gets the curated nav instead of
the full module list — the app already treats mobile layout differently
today, this extends that. Simplest to ship and keep in sync with the main
app, but "stripped down" becomes a responsive behavior rather than a
genuinely distinct product — someone resizing a browser window on desktop
would also see the remote.

**Option B — a real separate packaged build.** A distinct entry point/
config baked in specifically for the APK, independent of screen size —
same underlying view code, but its own module manifest shipped only in
that build target. Cleaner conceptually ("the phone app" is really its own
thing), but is new territory for a codebase that currently has zero build
step — needs its own packaging story (Bubblewrap/PWABuilder/Capacitor,
still an open question in its own right).

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

In scope: the curated module list above (or Alek's edited version of it),
restyled chrome and navigation, the alert-color behavior above, motion/
sound per the constraints above, mobile only.

Out of scope for v1: desktop entirely, every module *not* on the curated
list, any custom illustration/room art (this is a chrome build, not a
Vespera-style art project), and any change to a module's actual
underlying functionality — views behave identically to their desktop
counterparts, they just live in a smaller, LCARS-styled shell and there
are fewer of them.

## Still needs a decision before building starts

1. **Edit the module list** — cut/add from the draft above.
2. **Same-PWA-viewport-detection vs. a real separate packaged build**
   (Option A vs. B above) — the packaging question, separate from anything
   visual.
3. **Deck Nine vs. Red Squad** vs. some merge of the two.
4. Confirm the alert-color mapping, or adjust it.
5. Confirm reusing Oxanium/Rajdhani for typography, or say if something
   else should be sourced.

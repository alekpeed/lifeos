# Music App — Design Spec

Working title only; the name is yours to choose. This is the **design/product
spec** for turning Life OS's old **Chords** module into a standalone music app.
Its companion is `CHORDS_APP_HANDOFF.md`, which carries the actual engine source
in dependency order — read that for *how it works*; read this for *what the app
is and how it should feel to use*.

---

## 1. What it is

A **jazz-harmony study tool**: look any chord up, see and *hear* every voicing,
walk the real relationships between chords, learn Barry Harris's system, and keep
yourself sharp with spaced-repetition drills — all offline, all synthesized, no
accounts and no licensing.

It is **not** a DAW, a sequencer, or a play-along backing-track app. There's no
tempo, no timeline, no recording. Every "play" button is an *audition* — hear
this chord, hear this move, hear this trail — in service of understanding, not
production.

**Who it's for:** an intermediate-to-advanced player (piano or guitar) who reads
chord symbols and wants to internalize voicings, reharmonization, and the logic
of where chords go. It assumes real musical literacy; it rewards it.

**Why it's different from every other chord app:** *curation*. The relationship
graph only surfaces moves that are actually worthwhile, each with a stated reason
— not a naive "everything connects to everything" that's useless in practice.
The spelling engine is degree-aware, so a chord's ♯9 is always some kind of D,
never a wrong-looking E♭. Correctness and taste are the product.

---

## 2. Design principles (carried over, and why)

1. **Curation over completeness.** The harmony map is hand-weighted (strength 3 =
   strong functional pull, 1 = color move). Surface real relationships, each with
   a *reason*. Never dump an exhaustive matrix.
2. **Audition-first.** Nothing is text-only. Every chord, voicing, move, and
   trail is playable inline. The synth is the primary "read-back" channel.
3. **Correct spelling, always.** Degree-aware enharmonics are non-negotiable —
   it's what makes the app trustworthy to a literate musician.
4. **A study tool, not a sequencer.** Fixed audition gaps, not a tempo. This
   stance keeps the UI honest and the scope tight.
5. **Offline, synthesized, self-contained.** No samples, no network, no licensing
   baggage. The whole thing runs on a pure theory engine + a Web Audio synth.
6. **The engine is sacred and portable; the UI is disposable.** All nine theory
   files + the synth are pure functions on plain objects (notes, chords, MIDI
   numbers). The old Life-OS UI file does not port — a fresh UI is built on the
   untouched engine.

---

## 3. Information architecture

The old module had nine surfaces. For a standalone app, group them into three
intents so the nav reads as a purpose, not a pile of tabs:

**LOOK UP** — reference, one chord at a time
- **Dictionary** — the home surface. Look up any chord → tones, all voicings, hear
  everything.
- **Calculator** — build a chord from root + quality directly (the "I don't know
  the symbol" path into the Dictionary).
- **Barry Harris** — enter a chord, get its 6th-diminished "scale of chords,"
  spelled and playable.

**EXPLORE** — relationships, the territory
- **Harmony Map** — the walkable graph: from one chord, every worthwhile place to
  go, as a clickable node diagram. Key-context toggle; an "adventurous" mode that
  reveals color moves. Build a **trail** (an ordered walk) and save it.
- **Atlas** — the whole territory at once (vs. the Map's one-chord walk), plus the
  four dim7 families shown simultaneously.

**PRACTICE** — get better, over time
- **Lessons** — 18 written lessons (Foundations → Voicings → Harmony → Barry
  Harris), each with clickable chord-symbol chips that load the Dictionary.
- **Practice** — the adaptive spaced-repetition drill session (spell / name /
  voicing-recognition), with a streak counter and a printable sheet.
- **Log** — the freeform practice-session log (what you actually worked on at the
  instrument), kept separate from drill accuracy.

**Settings surface**
- **Sound** — the synth's parameter controls; dial in and save your own preset.
  Lives in settings, but every surface plays through whatever's selected here.

Recommended primary nav: **Dictionary · Harmony Map · Practice**, with the rest
reachable from within those (Calculator + Barry Harris hang off Dictionary; Atlas
off Harmony Map; Lessons + Log off Practice; Sound in settings).

---

## 4. Screen specs

Each surface below: its job, the key elements, the interactions, and the audio
behavior. States: empty (no chord chosen), loaded, playing.

### 4.1 Dictionary (home)
- **Job:** the fastest path from "what is this chord" to seeing and hearing it.
- **Input:** a chord-symbol field that parses live (`Cmaj7`, `F#m7b5`, `Bb13`,
  `AΔ`, `Eø`). Case matters (`M7` ≠ `m7`); show a gentle "not a chord I know" when
  parsing fails, never a hard error.
- **Shows:** the chord's tones (each with degree label + note name), the full
  symbol, and **every applicable voicing grouped by family** — Shells, Drop
  voicings (close / drop 2 / drop 3 / drop 2&4), Rootless A/B (Bill Evans), color
  voicings (Kenny Barron, "So What" quartal), upper-structure triads. Each voicing
  carries a one-line "what/why" description.
- **Guitar view:** any voicing can render as a real fretboard grip (frets 0–15,
  fingerable span) — drop 2 / drop 3 *are* guitar shapes, so these are idiomatic,
  not approximations.
- **Audio:** a play button on the chord and on each voicing. Chords roll low→high
  slightly, so a voicing reads as a shape, not a block.
- **Cross-links:** "See in Harmony Map," "Barry Harris view," "Which keys contain
  this."

### 4.2 Calculator
- **Job:** build a chord when you don't know the symbol. Root picker × quality
  picker (35 qualities, grouped by family) → drops you into the Dictionary result.

### 4.3 Barry Harris
- **Job:** show how Barry would treat the entered chord.
- **Shows:** the mapped 6th-diminished scale spelled out, the alternating
  6th-chord / dim7 "scale of chords" positions (each playable), and a plain-prose
  headline + explanation of *why* this chord maps to that scale (e.g. "a m7 is a
  major 6 from its 6th").
- **Audio:** step through the harmonized positions; hear the motion-over-a-static-
  tonic effect.

### 4.4 Harmony Map
- **Job:** the signature surface — walk real chord relationships.
- **Shows:** the center chord as a node, surrounded by its curated destinations,
  grouped (Resolves to / Substitutes / Approached by / Barry family), each edge
  weighted (thickness/emphasis = strength 3→1) and labeled with its reason.
- **Interactions:** click a node to re-center on it (walk the graph). A **key
  context** toggle adds Roman-numeral labels relative to a chosen key. An
  **adventurous** toggle reveals the weaker color moves that are hidden by default.
- **Trails:** the sequence of chords you walk becomes a **trail** — an ordered
  list you can name and **save** (see data model: `ChordProgressions`), then
  replay as a sequence ("hear the trail").
- **Audio:** "hear the move" auditions any two-chord edge with minimum-motion
  voice leading; "hear the trail" plays the saved walk.

### 4.5 Atlas
- **Job:** the whole territory at once, for orientation rather than a single walk.
- **Shows:** the full relationship field, plus a **diminished-engines** view of
  all four dim7 families at once (the symmetry that underlies dominant
  substitution).

### 4.6 Voice-leading detail (a panel, not a tab)
- Wherever two chords are shown as a move (Harmony Map edge, a saved trail step),
  offer a **"why it's smooth"** panel: common tones held, voices that move (and by
  how much), tritone resolutions, bass-motion type, guide-tone continuity, and a
  one-line smoothness verdict — all computed from the actual notes, in prose.

### 4.7 Lessons
- **Job:** structured learning path. 18 lessons across Foundations → Voicings →
  Harmony → Barry Harris. Each lesson is written prose with **clickable chord-symbol
  chips** that jump to the Dictionary loaded with that chord. Reading and doing are
  the same gesture.

### 4.8 Practice
- **Job:** keep skills sharp with spaced repetition.
- **Drills:** spell-the-chord, name-the-chord, recognize-the-voicing.
- **Grading:** language-app-style SRS — "same again / good / easy" maps to an
  interval scheme per tracked concept (e.g. `spell:maj7`, `voicing:drop2`).
- **Session shape:** adaptive — due reviews first, then weak spots, then a few new
  concepts. A streak counter; a printable practice sheet.

### 4.9 Log
- **Job:** a freeform record of *actual instrument practice* (date, duration, what
  you worked on) — deliberately separate from drill accuracy. Two different
  signals: "am I good at m7♭5 spelling" vs. "did I pick up the instrument today."

### 4.10 Sound (settings)
- **Job:** shape the instrument. Per-preset controls: two oscillators (+ optional
  FM "tine"), full ADSR, 3-band EQ, volume. Four factory presets (Piano / Rhodes
  EP / Organ / Pad). Save your own; the selection is global to every play button.

---

## 5. The engine (what powers all of it)

Nine pure theory files + one synth, all portable, zero framework/DOM/storage
dependency (full source in `CHORDS_APP_HANDOFF.md`):

- **notes → chords → voicings → harmony → barry → graph → voicelead**, plus
  **lessons** (content) and **drills** (SRS). Every function takes plain
  notes/chords/MIDI in and returns notes/chords/analysis out.
- **synth** — Web Audio, fully synthesized, knows no theory; give it MIDI numbers
  + a params object and it plays.

Design consequence: the app is a thin, replaceable UI over a correct, tested core.
Any platform that can render diagrams and produce audio can host it.

---

## 6. Data model

Four persisted stores (IndexedDB in the original; the shapes port to any store),
plus one for synth presets:

```
ChordProgressions  { id, name, chords: [{ symbol, … }] }
   — saved "trails" from the Harmony Map: an ordered, replayable chord walk.

ChordSkills        { id, interval, dueDate, attempts, correct }
   — one row per tracked concept (e.g. "spell:maj7", "voicing:drop2").
     interval/dueDate = SRS state; attempts/correct = accuracy.

ChordDrillLogs     { id, date, conceptId, … }
   — append-only history of every graded drill answer (indexed by date, concept).

ChordPracticeLogs  { id, date, duration, notes, … }
   — freeform instrument-practice sessions (the Log surface).

SynthPresets       { id, name, params:{…} }   ← new for the standalone app
   — user-saved Sound presets beyond the four factory ones.
```

No server, no auth required for the core. Optional cloud sync (see §9) would layer
on top; local-first stays the default.

---

## 7. Audio & interaction spec

- **Three audition affordances, everywhere they apply:** *hear the chord* (a
  voicing), *hear the move* (a two-chord edge, minimum-motion voiced), *hear the
  trail* (a saved sequence). Fixed audition gap between chords — a study cadence,
  not a tempo.
- **One shared audio context**, resumed on first user gesture (mobile/browser
  autoplay rules). The selected Sound preset feeds every play button.
- **Chord roll:** notes fire low→high a few ms apart so a voicing is heard as a
  shape.
- **No transport controls** (no play/pause/loop of a timeline). If it starts to
  feel like a sequencer, it's out of scope.

---

## 8. Visual & platform direction

**Visual identity is yours to bring** — this spec deliberately does not invent a
palette, type, or brand. What the design *must* accommodate, whatever the look:

- **Diagram-heavy.** Three diagram types carry the app: a **keyboard/staff** chord
  view, a **guitar fretboard** grip, and a **node-graph** (Harmony Map / Atlas).
  These need to render crisply and legibly at a glance and scale to phone width.
  They're information graphics, not decoration — clarity beats flourish.
- **Dense but scannable.** A literate musician wants a lot on screen (tones,
  labels, multiple voicings) without it turning to noise. Strong typographic
  hierarchy and tabular alignment matter more than imagery.
- **Dark-friendly.** Practice happens at instruments in low light; design both
  themes.

**Platform recommendation:** the engine is pure JS and the synth is Web Audio, so
the lowest-friction path is a **local-first web app / PWA** (installable, offline,
no store review). If you'd rather it match the Life OS native rebuild (Kotlin +
Compose Multiplatform), the theory engine ports cleanly to Kotlin as pure
functions, and the synth maps to the platform audio API — more work, but a real
native app on Android + desktop. Pick based on whether "runs everywhere from one
URL" or "native app you install" matters more.

---

## 9. Scope: MVP → full

**MVP (the smallest thing that's genuinely useful):**
- Dictionary (parse + tones + voicings + guitar shapes + audio)
- Sound (presets so it's pleasant to listen to)
- Calculator (the no-symbol entry path)

That alone is a better chord dictionary than most, because of correct spelling +
every voicing + real audio.

**V1 (the thing that's *distinctive*):**
- Harmony Map + trails + voice-leading panel  ← the reason to use this over anything else
- Barry Harris
- Practice + Log

**Later / optional:**
- Atlas (orientation view — powerful but not load-bearing)
- Lessons (content-heavy; can grow over time)
- **"Life as Music" companion mode** — the old ambient sonification that turned
  live personal-data counts into a slow generative chord loop on the Pad preset.
  It's a charming, self-contained extra that reuses the same synth; include it only
  if the app has a data source to sonify, otherwise leave it in Life OS.
- Optional account + cloud sync for trails/practice history across devices.

---

## 10. Open decisions (yours)

1. **Name** — deliberately unset here.
2. **Platform** — web/PWA (fast, universal) vs native Kotlin (matches Life OS,
   installs as an app). §8 has the trade.
3. **Instrument focus** — piano-first, guitar-first, or truly both co-equal? Both
   are supported by the engine; the UI emphasis is a choice.
4. **Include "Life as Music"?** — only if this app has data to sonify.
5. **Free / paid / distribution** — out of scope for this spec, but the offline,
   no-licensing, no-server design keeps every option open.

---

*Source of truth for the engine: `CHORDS_APP_HANDOFF.md`. This spec covers the
product and design; that one covers the code.*

# Music App — Complete Build Brief

A single, self-contained brief for building an independent music app from a
working jazz-harmony engine. Everything needed is in this one document:

- **Part I — The App** — what it is, how it should feel, every screen, and scope.
- **Part II — Build Plan** — how to build it as a native **Kotlin + Compose
  Multiplatform** app (Android + desktop): porting the engine, the synth, the
  data model, the extra features, and milestones.
- **Part III — The Engine** — the complete source (ten files, in dependency
  order) that you port. It's pure logic + audio synthesis, with no dependency on
  any other app or framework.

Nothing here points to anything outside this file.

---

# Part I — The App (product & design)

Working title only; the name is yours to choose. This is the **design/product
spec** for turning an existing **Chords** module into a standalone music app.
The complete engine source (all ten files) is in **Part III** of this document;
this part covers *what the app is and how it should feel to use*.

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
   numbers). The old web UI file does not port — a fresh UI is built on the
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
dependency (full source in Part III):

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
no store review). If you'd rather it match a native build (Kotlin +
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
  if the app has a data source to sonify, otherwise leave it out.
- **Riff Pads & musical typing** (§10) — keyboard-as-instrument + one-key phrase
  triggers, with shareable "packs." Could equally be pulled forward into V1.
- Optional account + cloud sync for trails/practice history across devices.

---

## 10. Feature idea: Riff Pads & musical typing

Make the computer keyboard an instrument — but a pad is **not** "this key = this
note." A pad is a **programmable musical event**: a chord/voicing (any number of
notes) with an articulation, and pads can chain into a riff. All of it rides on
the existing synth, which already plays voicings, rolls, and timed sequences.

**Musical typing — real-time play.** A QWERTY→piano layout (GarageBand/Ableton
convention: `A`–`K` = white keys, `W E T Y U` = black keys, `Z`/`X` shift the
octave), with a **chord mode** where a key fires a whole voicing rather than a
single note. Doubles as an input method the Dictionary, Calculator, and Practice
surfaces can borrow.

**Riff pads — one key, a programmed event.** Bind a key (or short combo) to
anything from a single note, to a full **voicing**, to a rolled/strummed chord, to
a multi-chord riff with timing — one press fires it through the selected preset.

**Per-event articulation.** Each note-event carries *how* it sounds:
- **block** — all notes at once,
- **roll up / roll down** — spread low→high or high→low, with a settable roll
  speed (the synth already rolls chords; this exposes the control),
- **strum** — a faster, guitar-like spread.
Plus onset, sustain, and optional velocity per event.

**Voicing-aware — build pads from the library.** Because the app already computes
named voicings (drop 2, rootless A/B, Kenny Barron, "So What", guitar grips…), a
pad event can *reference a voicing* (`{ symbol, voicingId }`) instead of raw notes
— so it stays editable and transposable, and you can "send this voicing to a pad"
straight from the Dictionary.

**Store pitches, not keys.** Pads store absolute MIDI (with octave), so a figure
repeated an octave up plays back faithfully — the octave *is* the data. (This is
why key-position storage fails: same fingers, wrong octave.) If you ever want the
app to *recognize* a riff regardless of octave or key — a guessing game, or
matching your playing to a pack — match on **intervals / pitch-classes** instead,
so octave and transposition fall away. Two different jobs: play it back exactly
(store pitches) vs. recognize it anywhere (compare shapes).

**Packs — named, shareable collections of pads.** Record your own (play it once,
save-to-key), or load a curated pack. This generalizes the app's existing
**trails** (saved chord walks) to full voiced, articulated, timed phrases.

New store:
```
RiffPacks { id, name, pads: [{
  key,                       // trigger: a key or short combo
  name,
  preset?,                   // optional per-pad synth preset override
  events: [{
    midis: […],              // a voicing — one or many notes, absolute pitch
    voicingRef?,             // optional { symbol, voicingId } from the library
    articulation,            // 'block' | 'roll-up' | 'roll-down' | 'strum'
    rollMs,                  // note-to-note spread when rolled/strummed
    atMs, durMs, velocity?,  // onset from trigger, sustain, dynamics
  }]
}]}
```

**Copyright, for the record:** this app is personal, not for sale, so play
whatever you like. (For completeness: a pad is synthesized note-data, not a
sampled recording — but compositions are still copyrighted, so *distributing* a
"famous songs" pack as a product would carry licensing implications. User-made and
original/public-domain packs don't.)

Fits with near-zero new engine work: block + rolled chords and timed sequences are
already in the synth (`playChord` rolls low→high; `playSequence` schedules
events), and voicings come from the voicing engine — so this is mostly a pad
editor + a key listener. Lives as a persistent bar across surfaces, or its own
"Pads" surface.

## 11. Open decisions (yours)

1. **Name** — deliberately unset here.
2. **Platform** — web/PWA (fast, universal) vs native Kotlin (installs as an app). §8 has the trade.
3. **Instrument focus** — piano-first, guitar-first, or truly both co-equal? Both
   are supported by the engine; the UI emphasis is a choice.
4. **Include "Life as Music"?** — only if this app has data to sonify.
5. **Riff Pads (§10)** — ship it as a core input method, or a later add-on?
6. **Free / paid / distribution** — out of scope for this spec, but the offline,
   no-licensing, no-server design keeps every option open.

---

---

# Part II — Build Plan (native Kotlin / Compose Multiplatform)

Everything the app *is* — screens, interactions, scope — is in Part I above. This
part is **how to build it as a native app**, and Part III is the **complete engine
source** you port. This document is self-contained: you need nothing outside it.

## B1. Target & project shape

**Native Kotlin + Compose Multiplatform**, one module producing an **Android APK**
and a **desktop app** (Windows/macOS/Linux) from shared code:

```
music-app/
  composeApp/
    src/
      commonMain/kotlin/…/
        theory/     ← the 9 theory files (Part III), ported to pure Kotlin (no UI, no audio, no IO)
        audio/      ← the synth: pure-Kotlin DSP (commonMain) + an `expect` audio sink
        data/       ← @Serializable stores + local persistence + (optional) cloud sync
        ui/         ← Compose screens (Dictionary, Harmony Map, Practice, Pads, …)
        platform/   ← `expect` declarations (audio out, storage, key input)
      androidMain/…  ← `actual`: AudioTrack, storage, hardware-key handling
      desktopMain/…  ← `actual`: javax.sound SourceDataLine, storage, key handling
    build.gradle.kts
  .github/workflows/build.yml   ← two CI jobs (see B7)
```

The guiding principle: **the engine is portable and correct; the UI is
disposable.** Port the ten engine files faithfully and test them, then build a
fresh Compose UI on top. (The engine originally ran inside a web app; that old
web UI is *not* part of this — build the UI new for Compose.)

## B2. Porting the theory engine (9 files)

All nine theory files (Part III) are pure functions over plain data — ideal to
port. Do it in **dependency order**: `notes → chords → voicings → harmony → barry
→ graph → voicelead`, plus the two leaf files `lessons` (pure content) and
`drills` (depends on notes/chords/voicings).

**Data-shape mapping (JS object → Kotlin):**

| JS shape | Kotlin |
|---|---|
| `{ letter, acc, pc }` (a note) | `data class Note(val letter: Char, val acc: Int, val pc: Int)` |
| `{ root, quality, tones, symbol }` (a chord) | `data class Chord(val root: Note, val quality: Quality, val tones: List<Tone>, val symbol: String)` |
| quality-table entry | `data class Quality(val id: String, val display: String, val label: String, val cat: String, val intervals: List<Pair<Int,Int>>, val aliases: List<String>)` + `val QUALITIES: List<Quality>` |
| `{ id, name, group, description, notes:[{midi,name,label}] }` (a voicing) | `data class Voicing(val id: String, val name: String, val group: String, val description: String, val notes: List<VoiceNote>)` |
| MIDI note numbers | `Int` (unchanged) |
| a module of `export function`s | a Kotlin `object` (e.g. `object Notes { … }`) |

**Worked example — `notes.js` → `Notes.kt`** (the base layer; port this first so
the idiom is set for the rest):

```kotlin
package …theory

data class Note(val letter: Char, val acc: Int, val pc: Int)

object Notes {
    val LETTERS = listOf('C', 'D', 'E', 'F', 'G', 'A', 'B')
    val NATURAL_PC = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val ACC_GLYPH = mapOf(-2 to "𝄫", -1 to "♭", 0 to "", 1 to "♯", 2 to "𝄪")
    private val DEGREE_NATURAL = mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 7 to 11, 9 to 14, 11 to 17, 13 to 21)

    fun parseNote(input: String): Note? {
        val m = Regex("^([A-Ga-g])(bb|##|b|#|♭♭|♯♯|♭|♯)?$").find(input.trim()) ?: return null
        val letter = m.groupValues[1].uppercase()[0]
        val acc = when (m.groupValues[2]) {
            "" -> 0
            "bb", "♭♭" -> -2
            "##", "♯♯" -> 2
            "b", "♭" -> -1
            else -> 1
        }
        return Note(letter, acc, ((NATURAL_PC[letter]!! + acc) % 12 + 12) % 12)
    }

    fun noteName(n: Note): String = n.letter + ACC_GLYPH[n.acc]

    // Spell the note `semitones` above `root` functioning as scale degree `degree`.
    fun spellInterval(root: Note, degree: Int, semitones: Int): Note {
        val letter = LETTERS[(LETTERS.indexOf(root.letter) + degree - 1) % 7]
        var acc = ((root.pc + semitones) % 12) - NATURAL_PC[letter]!!
        if (acc > 6) acc -= 12
        if (acc < -6) acc += 12
        return Note(letter, acc, ((NATURAL_PC[letter]!! + acc) % 12 + 12) % 12)
    }

    fun degreeLabel(degree: Int, semitones: Int): String {
        val diff = semitones - (DEGREE_NATURAL[degree] ?: 0)
        val glyph = when (diff) { 0 -> ""; -1 -> "♭"; 1 -> "♯"; -2 -> "𝄫"; else -> "𝄪" }
        return glyph + degree
    }
}
```

Two things this pins that the whole port must keep:
- **Musical astral glyphs (double-sharp/double-flat) as `\uXXXX` escapes**, never
  literal characters — they're surrogate pairs and literal ones break some
  toolchains. (Shown above.)
- **JS `%` and Kotlin `%` differ on negatives** — JS `-1 % 12 == -1` and some of
  this code relies on it. Wherever a pitch class must stay 0–11, force it with
  `((x % 12) + 12) % 12`.

**Test the port — don't eyeball it.** Each theory file is deterministic. Write
common tests that pin known outputs *before* building UI on top: `parseChord("F#m7b5")`
spells F♯–A–C–E; the ♯9 of C spells D♯ (not E♭); `barryAnalysis` of a m7 names the
right 6th-diminished scale. A ported engine with a green test suite is what lets
everything above it move fast.

## B3. Porting the synth (the one genuinely new piece)

`synth.js` (Part III) is a Web-Audio node graph (oscillators → gain envelopes →
3-band EQ → output) that doesn't exist outside a browser. This is the only part
that isn't a mechanical port — it's real, but bounded, DSP.

**Approach: a small software synth that renders PCM, played through a platform sink.**

- **Pure-Kotlin DSP in commonMain.** Keep `PARAM_DEFS` and the four
  `FACTORY_PRESETS` verbatim (they're just data). Reimplement `playVoice` as a
  per-sample renderer: two oscillators (`sine/triangle/sawtooth/square` by direct
  waveform math), optional FM on osc1's frequency, an ADSR gain envelope, summed
  across voices, then a 3-band biquad EQ (lowshelf 220 Hz / peaking 1 kHz Q0.8 /
  highshelf 3.6 kHz — the exact values in the source). Emit a `ShortArray` at
  44.1 kHz.
- **`expect` audio sink, `actual` per platform:**
  - Android → `android.media.AudioTrack` (streaming, PCM 16-bit).
  - Desktop → `javax.sound.sampled.SourceDataLine`.
  ```kotlin
  // commonMain
  expect object AudioOut { fun play(samples: ShortArray, sampleRate: Int) }
  ```
- **Keep the three audition affordances** (Part I §7): *hear the chord* (roll
  low→high ~22 ms apart), *hear the move* (two-chord minimum-motion via the
  engine's `voiceLeadMidis`), *hear the trail* (a sequence with a fixed ~0.9 s
  gap). Fixed gaps, **no transport/tempo** — it's a study tool, not a sequencer.
- **One shared output**, started on the first user gesture (audio focus), fed by
  whichever Sound preset is selected.

Scope: an additive two-oscillator synth with an envelope and EQ — a few hundred
lines of Kotlin, testable by rendering a buffer and asserting it isn't silent and
has the right fundamental. It's the single biggest net-new item; everything else
is a port.

## B4. Data model (Kotlin)

Six persisted stores. Model each as a `@Serializable` data class; persist locally
first (JSON-per-store is plenty), then optionally sync (B5.3):

```kotlin
@Serializable data class ChordProgression(val id: String, val name: String, val chords: List<SavedChord>)   // Harmony-Map "trails"
@Serializable data class ChordSkill(val id: String, val interval: Int, val dueDate: String, val attempts: Int, val correct: Int) // SRS state, one per concept
@Serializable data class ChordDrillLog(val id: String, val date: String, val conceptId: String, val correct: Boolean)            // append-only drill history
@Serializable data class ChordPracticeLog(val id: String, val date: String, val durationMin: Int, val notes: String)             // freeform instrument log
@Serializable data class SynthPreset(val id: String, val name: String, val params: Map<String, Double>)                          // user Sound presets
@Serializable data class RiffPack(val id: String, val name: String, val pads: List<Pad>)                                         // shareable pad collections (B5.1)
```

`ChordSkill` is keyed by concept id (`"spell:maj7"`, `"voicing:drop2"` — from
`drills.js`'s CONCEPTS). Drill accuracy (`ChordDrillLog`) is deliberately separate
from the instrument-practice log (`ChordPracticeLog`) — two different signals.

## B5. The three extra features

### B5.1 Riff Pads + musical typing
Full design is in **Part I §10**. A pad is *not* "one key = one note" — it's a
**programmable musical event**: a voicing (one or many notes) with an
articulation, and pads can chain into a timed riff. It rides on the ported synth,
which already does rolls and timed sequences, so this is mostly a **pad editor + a
key listener**.

```kotlin
@Serializable data class Pad(val key: String, val name: String, val preset: String? = null, val events: List<PadEvent>)
@Serializable data class PadEvent(
    val midis: List<Int>,                 // a voicing — absolute pitch, one or many notes
    val voicingRef: VoicingRef? = null,   // optional { symbol, voicingId } from the library
    val articulation: String,             // "block" | "roll-up" | "roll-down" | "strum"
    val rollMs: Int, val atMs: Int, val durMs: Int, val velocity: Double? = null,
)
```
- **Store absolute MIDI, not key positions** — the octave *is* the data. To later
  *recognize* a riff regardless of octave/key, match on intervals/pitch-classes.
- **Key input:** desktop uses `Modifier.onPreviewKeyEvent` for real hardware keys
  (QWERTY→piano: `A`–`K` white, `W E T Y U` black, `Z`/`X` octave shift; a chord
  mode fires a whole voicing per key). Android is an on-screen pad grid, with the
  same key path for a paired Bluetooth/USB keyboard.
- **Packs are shareable:** export/import a `RiffPack` as JSON (share sheet / file),
  and/or sync via the cloud table (B5.3).

### B5.2 Practice + Lessons + Log
- **Practice** — port `drills.js`: question generation (spell / name /
  voicing-recognition), SRS grading (`gradeSkill`: same-again / good / easy →
  interval scheme), adaptive session (due reviews → weak spots → a few new). UI: a
  card per question, three grade buttons, a streak. State = `ChordSkill`; each
  answer appends a `ChordDrillLog`.
- **Lessons** — port `lessons.js` (18 written lessons) as content; render prose
  with **clickable chord-symbol chips** that deep-link into the Dictionary loaded
  with that chord.
- **Log** — the freeform instrument-practice log (`ChordPracticeLog`).

### B5.3 Cloud sync + accounts
A backend-light, local-first design:
- **Auth:** email + password against a hosted auth service (e.g. Supabase GoTrue,
  or Firebase Auth) over plain HTTPS — no OAuth redirect, so it works cleanly on
  Android + desktop. Store the access/refresh tokens locally; refresh on 401.
  Same account on two devices → same user id → same rows.
- **Storage:** one table with a generic per-key-blob shape —
  `records(user_id, store, record_id, data jsonb, updated_at, deleted_at, primary
  key(user_id, store, record_id))` — with a row-level-security rule
  `user_id = <authenticated user>`. The six stores (B4) map onto `store`
  namespaces (`progressions`, `skills`, `drilllogs`, `practicelogs`, `presets`,
  `riffpacks`).
- **Local-first stays default:** everything works signed-out on-device; sign-in is
  additive (push local up, pull remote down, last-write-wins on `updated_at`).
  Sharing a pack with *another person* is a separate, later concern (a public row
  or an exported file); the sync above is device-to-device for your own data.

## B6. Milestones (each independently shippable)

| # | Milestone | Contents | Done when |
|---|---|---|---|
| **M0** | Repo + CI | CMP module skeleton; two-job CI (Android APK + desktop package). | An empty app builds green on both jobs. |
| **M1** | Engine port + tests | Port the 9 theory files in dependency order; tests pin known outputs. | Test suite green; parsing/voicings/barry match. |
| **M2** | Audio + MVP | Synth (B3); Dictionary (parse → tones → voicings → guitar shapes → audio); Calculator; Sound presets. | You can look up a chord and *hear* every voicing on device. |
| **M3** | V1 distinctive | Harmony Map (curated graph, key/adventurous toggles, trails) + voice-leading panel; Barry Harris; Atlas. | You can walk relationships and audition moves/trails. |
| **M4** | Practice | Drills + SRS + streak; Lessons (clickable chips); Log. | A drill session grades and schedules; lessons deep-link. |
| **M5** | Riff Pads | Musical typing + programmable pads + articulations; packs export/import. | A key fires a voiced, articulated pad through the synth. |
| **M6** | Cloud sync | Auth + `records` table + RLS; local-first sync of all six stores. | Same account on two devices shares trails/practice/packs. |

MVP = **M0–M2**. V1 = **through M4**. Extras = **M5–M6**.

## B7. Build & verify

- **Compile in CI, test on device.** A minimal two-job GitHub Actions workflow
  (adjust names to taste):

  ```yaml
  name: Build
  on: { push: {}, workflow_dispatch: {} }
  jobs:
    android:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: temurin, java-version: '17' }
        - uses: android-actions/setup-android@v3
        - uses: gradle/actions/setup-gradle@v3
        - run: gradle :composeApp:assembleDebug --no-daemon --stacktrace
        - uses: actions/upload-artifact@v4
          with: { name: android-apk, path: composeApp/build/outputs/apk/debug/*.apk }
    desktop:
      runs-on: windows-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: temurin, java-version: '17' }
        - uses: gradle/actions/setup-gradle@v3
        - run: gradle :composeApp:packageDistributionForCurrentOS --no-daemon --stacktrace
        - uses: actions/upload-artifact@v4
          with: { name: desktop-app, path: composeApp/build/compose/binaries/main/**/* }
  ```

- **Compose Multiplatform gotchas worth knowing up front:** in commonMain use
  `StringBuilder.appendRange(cs, start, end)` (the 3-arg `append(CharSequence,…)`
  is JVM-only); fully-qualify `androidx.compose.material3.AlertDialog` (a bare
  `AlertDialog` can resolve to a non-Composable overload); lazy-list `items { }`
  needs `import androidx.compose.foundation.lazy.items`; write astral glyphs
  (double-sharp/double-flat) as `\uXXXX` escapes, not literals.
- **Platform pattern:** `expect` in `commonMain/platform/` (AudioOut, Storage,
  key-input helpers) with `android` + `desktop` actuals.

## B8. Do NOT port
- The original web UI that hosted this engine — build the Compose UI fresh.
- Any "sonify live data" mode — out of scope for a standalone music app.

## B9. Still to decide (won't block the build)
1. **Name.** 2. **Instrument emphasis** — piano-first / guitar-first / co-equal
(the engine supports both; it's a UI choice). 3. **Distribution** — free / paid /
store vs. sideload; the offline, no-licensing, no-server core keeps every option open.

---

# Part III — The Engine (complete source)

**What this is:** the full music-theory + audio-synthesis engine built for the original app's Chords module, extracted here as a starting point for a standalone music app. The engine is real, working, and has zero dependency on any host app's data layer or UI framework. It's pasted in full below, in dependency order, plus a data-model reference and a tour of how it was wired into a UI (that last part is reference only — it's tightly coupled to the original app's own conventions and won't port directly).

## Architecture: how the pieces fit together

Nine files, in two groups. **The theory engine** (originally `js/theory/*.js`) is pure music theory — no audio, no DOM, no storage, just functions that take notes/chords in and return notes/chords/analysis out. **The synth** (originally `js/audio/synth.js`) is pure Web Audio — no music-theory knowledge at all, it just plays whatever MIDI note numbers it's given.

Dependency order (each file only depends on the ones above it):

1. **`notes.js`** — the base layer. Pitch-spelling primitives: parse a note name (`"F#"` → `{letter, acc, pc}`), name a note, and spell an interval degree-aware (a chord's ♯9 is always some kind of D, never spelled as E♭, because it's the 9th degree). Nothing else depends on anything but this.
2. **`chords.js`** (theory) — chord construction and parsing. A table of 35 chord qualities as `(degree, semitones)` interval formulas, a symbol parser (`"F#m7b5"` → a chord object), and `buildChord(root, qualityId)` → the one function everything else calls to make a chord.
3. **`voicings.js`** — depends on `chords.js`. Turns a chord object into concrete, playable voicings: close position, drop 2/3/2&4, shell voicings, rootless A/B (Bill Evans), Kenny Barron, "So What" quartal, upper-structure triads — plus a guitar-fretboard realizer that maps any voicing onto real fret positions.
4. **`harmony.js`** — depends on `notes.js` + `chords.js`. Key-level harmony: diatonic chords for a key, secondary dominants, tritone substitutes, borrowed (modal interchange) chords, "what keys contain this chord," and a `relatedChords()` function that generates a chord's full relationship map (resolves-to / substitutes / approached-by) with a stated reason for every relationship.
5. **`barry.js`** — depends on `notes.js` + `chords.js`. Barry Harris's 6th-diminished system as a standalone, self-contained analysis: builds the 8-note "scale of chords" for any root, and `barryAnalysis(chord)` explains how Barry would treat any given chord (which 6-dim scale it maps to, and why).
6. **`graph.js`** — depends on `notes.js` + `chords.js` + `harmony.js`. The walkable harmony graph: for any chord, every hand-curated "worthwhile" place to go, weighted by strength (3 = strong functional pull, 1 = color move). Curation is the actual feature here — a naive "everything connects to everything" graph is useless; this one only surfaces real relationships. Also includes negative-harmony mirroring and Roman-numeral labeling relative to a key.
7. **`voicelead.js`** — depends on `notes.js` only (takes chord objects, doesn't import chord-building logic). The "why" behind any two-chord move: common tones, moving voices, tritone resolutions, bass motion classification, guide-tone continuity, and a smoothness verdict — all computed from the actual notes, then translated into prose. `voiceLeadMidis()` also computes minimum-motion voicings for playback.
8. **`lessons.js`** — no imports, pure content. 18 written lessons (Foundations → Voicings → Harmony → Barry Harris), each with example chord symbols meant to be clickable chips that load the Dictionary view.
9. **`drills.js`** — depends on `notes.js` + `chords.js` + `voicings.js`. The spaced-repetition practice engine: question generation (spell/name/voicing-recognition drills), SRS grading (same again/good/easy → interval scheme as a language-learning app), and adaptive session building (due reviews first, then weak spots, then a few new concepts).

Independent of all of the above:

10. **`synth.js`** (originally `js/audio/synth.js`) — a fully synthesized (no samples) Web Audio chord engine: two oscillators per voice with optional FM (for an electric-piano "tine" sound), full ADSR envelope, 3-band EQ, four factory presets (Piano/Rhodes/Organ/Pad). Takes a plain array of MIDI note numbers and a params object; knows nothing about music theory.

## What's portable vs. what's app-specific

**Fully portable, zero framework dependency:** all nine theory files and `synth.js`. Every function is a pure function operating on plain objects (notes, chords, MIDI numbers) — no DOM references, no `import` from anything outside this set, no storage calls. You can drop these ten files into any JS project (or port the logic to another language) and they'll work exactly as they do here.

**Not portable, reference only:** the original UI file (`js/interfaces/default/views/chords.js`, ~1,130 lines) that hosted this engine inside the original web app. It's tightly coupled to that app's own conventions — a shared `el()`/`svgEl()` DOM-building helper, a `ctx.data.*` IndexedDB access pattern, a `makeKnob()` UI widget shared with other modules. See "How it was wired into a UI" at the bottom for a tour of what it did, so you know what a real UI built on this engine needs to cover — but the code itself won't drop into a new project as-is.

## Data model reference

If you want a starting schema for your own app, this is what the original app persisted around the engine (IndexedDB, but the shapes translate to any storage):

```
ChordProgressions   { id, name, chords: [{ symbol, ... }] }
  — a named, saved "trail": an ordered list of chords (built from the
    Harmony Map's walkable-trail feature), replayable as a sequence.

ChordSkills         { id, interval, dueDate, attempts, correct }
  — one record per trackable CONCEPT (see drills.js's CONCEPTS list —
    e.g. "spell:maj7", "voicing:drop2"), keyed by concept id directly.
    interval/dueDate are the SRS state (see drills.js's gradeSkill);
    attempts/correct drive the accuracy() computation.

ChordDrillLogs      { id, date, conceptId, ... }
  — append-only history of every graded drill answer. Indexed by date
    and conceptId.

ChordPracticeLogs   { id, date, ... }
  — separate freeform log: actual instrument practice sessions (date,
    duration, what was worked on), NOT auto-tracked drill accuracy.
    Two different signals: "am I good at spelling m7b5 chords" vs.
    "did I actually pick up my instrument today."
```

## How it was wired into a UI (reference only)

The original view had nine tabs over this engine, worth knowing about as a checklist of what a full-featured version of this covers:

- **Dictionary** — look up any chord, see its tones, hear it, see all its voicings.
- **Barry Harris** — enter a chord, get its 6th-diminished scale spelled and playable.
- **Calculator** — build a chord from root + quality directly.
- **Harmony Map** — the walkable graph from `graph.js`, rendered as an actual clickable node diagram (`graphSVG`), with a key context toggle and an "adventurous" mode that reveals color moves.
- **Atlas** — the whole territory at once (vs. the Map's one-chord-at-a-time walk) — `atlasSVG` plus a diminished-engines view (`dimEnginesSVG`) showing all four dim7 families simultaneously.
- **Lessons** — the 18 written lessons from `lessons.js`, with clickable chord-symbol chips.
- **Sound** — the synth's parameter controls (`paramControl`, using a shared knob widget), so you can dial in your own preset.
- **Practice** — the adaptive drill session from `drills.js`, with a streak counter and a printable practice sheet.
- **Log** — the freeform practice-session log (separate from drill stats).

If you rebuild a UI around this engine, that's roughly the feature surface it supported — not a spec to follow exactly, just a record of what existed.

---

## Source: `notes.js`

```js
// Pitch-spelling primitives for the harmony engine. A "note" is
// { letter, acc, pc } — letter A–G, acc = accidental offset (-2..+2),
// pc = pitch class 0–11. Spelling is degree-aware: a chord tone is named
// by its interval degree first and accidental second, so the ♯9 of C is
// always D♯ (a ninth = some kind of D), never E♭.

export const LETTERS = ['C', 'D', 'E', 'F', 'G', 'A', 'B'];
export const NATURAL_PC = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };

const ACC_GLYPH = { '-2': '𝄫', '-1': '♭', 0: '', 1: '♯', 2: '𝄪' };

export function parseNote(input) {
  const m = /^([A-Ga-g])(𝄫|bb|♭♭|𝄪|##|♯♯|b|♭|#|♯)?$/.exec(String(input).trim());
  if (!m) return null;
  const letter = m[1].toUpperCase();
  const accStr = m[2] || '';
  const acc =
    accStr === '' ? 0
    : /^(bb|♭♭|𝄫)$/.test(accStr) ? -2
    : /^(##|♯♯|𝄪)$/.test(accStr) ? 2
    : /^(b|♭)$/.test(accStr) ? -1
    : 1;
  return { letter, acc, pc: (NATURAL_PC[letter] + acc + 12) % 12 };
}

export function noteName(note) {
  return note.letter + ACC_GLYPH[note.acc];
}

// Spell the note `semitones` above `root` that functions as scale degree
// `degree` (1-based; 9/11/13 work — letters wrap every 7 degrees).
export function spellInterval(root, degree, semitones) {
  const letter = LETTERS[(LETTERS.indexOf(root.letter) + degree - 1) % 7];
  let acc = ((root.pc + semitones) % 12) - NATURAL_PC[letter];
  if (acc > 6) acc -= 12;
  if (acc < -6) acc += 12;
  const pc = (NATURAL_PC[letter] + acc + 12) % 12;
  return { letter, acc, pc };
}

// What each degree is "naturally" in semitones, so alterations label
// correctly: 14 semitones over degree 9 is a plain 9, 13 is a ♭9.
const DEGREE_NATURAL = { 1: 0, 2: 2, 3: 4, 4: 5, 5: 7, 6: 9, 7: 11, 9: 14, 11: 17, 13: 21 };

export function degreeLabel(degree, semitones) {
  const diff = semitones - DEGREE_NATURAL[degree];
  const glyph = diff === 0 ? '' : diff === -1 ? '♭' : diff === 1 ? '♯' : diff === -2 ? '𝄫' : '𝄪';
  return glyph + degree;
}
```

## Source: `chords.js`

```js
// Chord construction: quality formulas as (degree, semitones) pairs, a
// symbol parser, and degree-aware tone spelling built on notes.js.

import { parseNote, spellInterval, noteName, degreeLabel } from './notes.js';

// `cat` buckets qualities into families the voicing/Barry engines key off:
// triad-maj, triad-min, dim, aug, sus, 6, m6, maj7, dom, m7, mMaj7, m7b5, dim7.
export const QUALITIES = [
  { id: 'maj',     display: '',        label: 'Major',            cat: 'triad-maj', intervals: [[1, 0], [3, 4], [5, 7]], aliases: ['', 'maj', 'M'] },
  { id: 'm',       display: 'm',       label: 'Minor',            cat: 'triad-min', intervals: [[1, 0], [3, 3], [5, 7]], aliases: ['m', 'min', '-'] },
  { id: 'dim',     display: '°',       label: 'Diminished',       cat: 'dim',       intervals: [[1, 0], [3, 3], [5, 6]], aliases: ['dim', '°', 'o'] },
  { id: 'aug',     display: '+',       label: 'Augmented',        cat: 'aug',       intervals: [[1, 0], [3, 4], [5, 8]], aliases: ['aug', '+', '#5'] },
  { id: 'sus4',    display: 'sus4',    label: 'Suspended 4th',    cat: 'sus',       intervals: [[1, 0], [4, 5], [5, 7]], aliases: ['sus4', 'sus'] },
  { id: 'sus2',    display: 'sus2',    label: 'Suspended 2nd',    cat: 'sus',       intervals: [[1, 0], [2, 2], [5, 7]], aliases: ['sus2'] },
  { id: 'add9',    display: 'add9',    label: 'Added 9th',        cat: 'triad-maj', intervals: [[1, 0], [3, 4], [5, 7], [9, 14]], aliases: ['add9'] },
  { id: 'madd9',   display: 'm(add9)', label: 'Minor added 9th',  cat: 'triad-min', intervals: [[1, 0], [3, 3], [5, 7], [9, 14]], aliases: ['madd9', 'm(add9)'] },

  { id: '6',       display: '6',       label: 'Major 6th',        cat: '6',   intervals: [[1, 0], [3, 4], [5, 7], [6, 9]], aliases: ['6', 'maj6', 'M6'] },
  { id: 'm6',      display: 'm6',      label: 'Minor 6th',        cat: 'm6',  intervals: [[1, 0], [3, 3], [5, 7], [6, 9]], aliases: ['m6', 'min6', '-6'] },
  { id: '69',      display: '6/9',     label: 'Six-nine',         cat: '6',   intervals: [[1, 0], [3, 4], [5, 7], [6, 9], [9, 14]], aliases: ['69', '6/9', '6add9'] },
  { id: 'm69',     display: 'm6/9',    label: 'Minor six-nine',   cat: 'm6',  intervals: [[1, 0], [3, 3], [5, 7], [6, 9], [9, 14]], aliases: ['m69', 'm6/9'] },

  { id: 'maj7',    display: 'maj7',    label: 'Major 7th',        cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11]], aliases: ['maj7', 'ma7', 'M7', 'Δ', 'Δ7'] },
  { id: 'maj9',    display: 'maj9',    label: 'Major 9th',        cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11], [9, 14]], aliases: ['maj9', 'M9'] },
  { id: 'maj7s11', display: 'maj7♯11', label: 'Major 7th ♯11',    cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11], [9, 14], [11, 18]], aliases: ['maj7#11', 'maj7♯11', 'M7#11'] },
  { id: 'maj13',   display: 'maj13',   label: 'Major 13th',       cat: 'maj7', intervals: [[1, 0], [3, 4], [5, 7], [7, 11], [9, 14], [13, 21]], aliases: ['maj13', 'M13'] },

  { id: '7',       display: '7',       label: 'Dominant 7th',     cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10]], aliases: ['7', 'dom7'] },
  { id: '9',       display: '9',       label: 'Dominant 9th',     cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 14]], aliases: ['9'] },
  { id: '13',      display: '13',      label: 'Dominant 13th',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 14], [13, 21]], aliases: ['13'] },
  { id: '7sus4',   display: '7sus4',   label: '7 suspended 4th',  cat: 'sus', intervals: [[1, 0], [4, 5], [5, 7], [7, 10]], aliases: ['7sus4', '7sus'] },
  { id: '7b9',     display: '7♭9',     label: 'Dominant 7 ♭9',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 13]], aliases: ['7b9', '7♭9'] },
  { id: '7s9',     display: '7♯9',     label: 'Dominant 7 ♯9',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 7], [7, 10], [9, 15]], aliases: ['7#9', '7♯9'] },
  { id: '7s11',    display: '7♯11',    label: 'Dominant 7 ♯11',   cat: 'dom', intervals: [[1, 0], [3, 4], [7, 10], [9, 14], [11, 18]], aliases: ['7#11', '7♯11'] },
  { id: '7b13',    display: '7♭13',    label: 'Dominant 7 ♭13',   cat: 'dom', intervals: [[1, 0], [3, 4], [7, 10], [9, 14], [13, 20]], aliases: ['7b13', '7♭13'] },
  { id: '7s5',     display: '7♯5',     label: 'Dominant 7 ♯5',    cat: 'dom', intervals: [[1, 0], [3, 4], [5, 8], [7, 10]], aliases: ['7#5', '7♯5', 'aug7', '+7'] },
  { id: '7alt',    display: '7alt',    label: 'Altered dominant', cat: 'dom', intervals: [[1, 0], [3, 4], [7, 10], [9, 13], [13, 20]], aliases: ['7alt', 'alt'] },

  { id: 'm7',      display: 'm7',      label: 'Minor 7th',        cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10]], aliases: ['m7', 'min7', '-7'] },
  { id: 'm9',      display: 'm9',      label: 'Minor 9th',        cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10], [9, 14]], aliases: ['m9', 'min9', '-9'] },
  { id: 'm11',     display: 'm11',     label: 'Minor 11th',       cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10], [9, 14], [11, 17]], aliases: ['m11', 'min11', '-11'] },
  { id: 'm13',     display: 'm13',     label: 'Minor 13th',       cat: 'm7', intervals: [[1, 0], [3, 3], [5, 7], [7, 10], [9, 14], [13, 21]], aliases: ['m13', 'min13'] },
  { id: 'mMaj7',   display: 'm(maj7)', label: 'Minor-major 7th',  cat: 'mMaj7', intervals: [[1, 0], [3, 3], [5, 7], [7, 11]], aliases: ['mmaj7', 'm(maj7)', 'minmaj7', '-Δ', 'mM7'] },
  { id: 'm7b5',    display: 'm7♭5',    label: 'Half-diminished',  cat: 'm7b5', intervals: [[1, 0], [3, 3], [5, 6], [7, 10]], aliases: ['m7b5', 'm7♭5', 'ø', 'ø7', 'min7b5', '-7b5'] },
  { id: 'dim7',    display: '°7',      label: 'Diminished 7th',   cat: 'dim7', intervals: [[1, 0], [3, 3], [5, 6], [7, 9]], aliases: ['dim7', '°7', 'o7'] },
];

const QUALITY_BY_ID = new Map(QUALITIES.map((q) => [q.id, q]));

// Case matters in chord symbols — "M7" is major 7, "m7" is minor 7 — so
// matching is exact-case first. A case-insensitive fallback (for typed
// input like "cMAJ7") only covers aliases whose lowercase form is
// unambiguous across qualities.
const ALIAS_EXACT = new Map();
const lowerCounts = new Map();
for (const q of QUALITIES) {
  for (const a of q.aliases) {
    ALIAS_EXACT.set(a, q.id);
    lowerCounts.set(a.toLowerCase(), (lowerCounts.get(a.toLowerCase()) || new Set()).add(q.id));
  }
}
const ALIAS_LOWER = new Map();
for (const q of QUALITIES) {
  for (const a of q.aliases) {
    if (lowerCounts.get(a.toLowerCase()).size === 1) ALIAS_LOWER.set(a.toLowerCase(), q.id);
  }
}

export function getQuality(id) {
  return QUALITY_BY_ID.get(id) || null;
}

// Parse a chord symbol like "Cmaj7", "F#m7b5", "Bb13", "AΔ", "Eø".
export function parseChord(text) {
  const m = /^([A-Ga-g])(𝄫|bb|♭♭|𝄪|##|♯♯|b|♭|#|♯)?(.*)$/.exec(String(text).trim());
  if (!m) return null;
  const root = parseNote(m[1] + (m[2] || ''));
  if (!root) return null;
  const rest = m[3].trim().replace(/\s+/g, '');
  const qualityId = ALIAS_EXACT.get(rest) ?? ALIAS_LOWER.get(rest.toLowerCase());
  if (qualityId === undefined) return null;
  return buildChord(root, qualityId);
}

export function buildChord(root, qualityId) {
  const quality = QUALITY_BY_ID.get(qualityId);
  if (!quality || !root) return null;
  const tones = quality.intervals.map(([degree, semitones]) => {
    const note = spellInterval(root, ((degree - 1) % 7) + 1, semitones % 12);
    return { degree, semitones, note, name: noteName(note), label: degreeLabel(degree, semitones) };
  });
  return {
    root,
    quality,
    tones,
    symbol: noteName(root) + quality.display,
  };
}

// Look up a tone by degree, with a fallback (degree, semitones) synthesized
// if the quality doesn't define it — voicings use this to add e.g. a 9th to
// a plain 7 chord while respecting an altered 9 when the quality has one.
export function toneOf(chord, degree, fallbackSemis) {
  const existing = chord.tones.find((t) => t.degree === degree);
  if (existing) return existing;
  if (fallbackSemis === undefined) return null;
  const note = spellInterval(chord.root, ((degree - 1) % 7) + 1, fallbackSemis % 12);
  return { degree, semitones: fallbackSemis, note, name: noteName(note), label: degreeLabel(degree, fallbackSemis) };
}
```

## Source: `voicings.js`

```js
// Voicing families. A voicing is { id, name, group, description, notes },
// where notes = [{ midi, name, label }] sorted low→high. All builders place
// the chord root in the octave C3–B3 and then nudge the whole stack back
// into a playable register, so diagrams and playback share one placement.

import { toneOf } from './chords.js';

function rootMidi(chord) {
  return 48 + chord.root.pc; // C3..B3
}

function place(chord, entries) {
  // entries: [{ tone, offset }] — offset = semitones above the root position.
  let notes = entries.map(({ tone, offset }) => ({
    midi: rootMidi(chord) + offset,
    name: tone.name,
    label: tone.label,
  })).sort((a, b) => a.midi - b.midi);
  while (notes[0].midi < 36) notes = notes.map((n) => ({ ...n, midi: n.midi + 12 }));
  while (notes[notes.length - 1].midi > 86) notes = notes.map((n) => ({ ...n, midi: n.midi - 12 }));
  return notes;
}

// The 4-note "core" of a chord: its first four defined intervals (1-3-5-7,
// or 1-3-5-6 for sixth chords; altered 5ths ride along). Drop voicings are
// defined on this core; extensions live in the rootless/UST families.
function coreFour(chord) {
  const core = chord.tones.slice(0, 4);
  return core.length === 4 ? core : null;
}

function closeStack(core) {
  return [...core].sort((a, b) => a.semitones - b.semitones).map((tone) => ({ tone, offset: tone.semitones }));
}

function voicing(chord, id, name, group, description, entries) {
  return { id, name, group, description, notes: place(chord, entries) };
}

// --- Drop family: from the close stack, drop the Nth-from-top an octave ---

function dropVoicing(chord, id, name, dropIdxFromTop) {
  const core = coreFour(chord);
  if (!core) return null;
  const close = closeStack(core);
  const entries = close.map((e, i) => {
    const fromTop = close.length - 1 - i;
    return dropIdxFromTop.includes(fromTop) ? { ...e, offset: e.offset - 12 } : e;
  });
  const desc = {
    drop2: 'Close position with the 2nd note from the top dropped an octave — the workhorse of jazz guitar comping and big-band voicing.',
    drop3: '3rd from the top dropped an octave — wide, open sound; on guitar this is the classic 6-x-4-3-2 shape family.',
    drop24: '2nd and 4th from the top dropped — the widest spread; two-hand friendly on piano.',
  }[id];
  return voicing(chord, id, name, 'Drop voicings', desc, entries);
}

// --- Shell / Root-Shell-Pretty ---

function shellTones(chord) {
  const third = toneOf(chord, 3) || toneOf(chord, 4) || toneOf(chord, 2);
  const seventh = toneOf(chord, 7) || toneOf(chord, 6);
  return { third, seventh };
}

function shellVoicings(chord) {
  const { third, seventh } = shellTones(chord);
  if (!third || !seventh) return [];
  return [
    voicing(chord, 'shellA', `Shell (1–${seventh.label}–${third.label})`, 'Shells',
      'Root plus the two tones that define the chord quality — the guide tones. Seventh below, third on top.',
      [{ tone: toneOf(chord, 1), offset: 0 }, { tone: seventh, offset: seventh.semitones % 12 }, { tone: third, offset: (third.semitones % 12) + 12 }]),
    voicing(chord, 'shellB', `Shell (1–${third.label}–${seventh.label})`, 'Shells',
      'Same guide tones, stacked the other way: third below, seventh on top.',
      [{ tone: toneOf(chord, 1), offset: 0 }, { tone: third, offset: third.semitones % 12 }, { tone: seventh, offset: seventh.semitones % 12 }]),
  ];
}

// "Pretty" color tones per family, honoring alterations the quality defines.
function prettyTones(chord) {
  const cat = chord.quality.cat;
  if (cat === 'dom') return [toneOf(chord, 9, 14), toneOf(chord, 13, 21)];
  if (cat === 'maj7' || cat === '6') return [toneOf(chord, 9, 14), toneOf(chord, 6, 9)];
  if (cat === 'm7') return [toneOf(chord, 9, 14), toneOf(chord, 11, 17)];
  if (cat === 'm6' || cat === 'mMaj7') return [toneOf(chord, 9, 14)];
  return [];
}

// Root → Shell → Pretty, as three cumulative stages (Adam Maness / Open
// Studio's progressive-voicing teaching device).
export function rootShellPretty(chord) {
  const { third, seventh } = shellTones(chord);
  if (!third || !seventh) return null;
  const root = { tone: toneOf(chord, 1), offset: -12 };
  const shell = [
    { tone: third, offset: third.semitones % 12 },
    { tone: seventh, offset: seventh.semitones % 12 },
  ];
  const pretty = prettyTones(chord).filter(Boolean).map((tone) => ({
    tone, offset: (tone.semitones % 12) + 12,
  }));
  return {
    stages: [
      { name: 'Root', notes: place(chord, [root]) },
      { name: 'Root + Shell', notes: place(chord, [root, ...shell]) },
      { name: 'Root + Shell + Pretty', notes: place(chord, [root, ...shell, ...pretty]) },
    ],
  };
}

// --- Rootless (Bill Evans A / B forms) ---

function rootlessDegrees(chord, form) {
  const cat = chord.quality.cat;
  if (cat === 'dom') {
    // 3–13–♭7–9 (A) / ♭7–9–3–13 (B), with alterations riding along.
    const t3 = toneOf(chord, 3), t13 = toneOf(chord, 13, 21), t7 = toneOf(chord, 7), t9 = toneOf(chord, 9, 14);
    return form === 'A' ? [t3, t13, t7, t9] : [t7, t9, t3, t13];
  }
  if (cat === 'maj7' || cat === '6') {
    const t3 = toneOf(chord, 3), t5 = toneOf(chord, 5, 7), t7 = toneOf(chord, 7, 11), t9 = toneOf(chord, 9, 14);
    return form === 'A' ? [t3, t5, t7, t9] : [t7, t9, t3, t5];
  }
  if (cat === 'm7') {
    const t3 = toneOf(chord, 3), t5 = toneOf(chord, 5, 7), t7 = toneOf(chord, 7), t9 = toneOf(chord, 9, 14);
    return form === 'A' ? [t3, t5, t7, t9] : [t7, t9, t3, t5];
  }
  return null;
}

function rootlessVoicing(chord, form) {
  const tones = rootlessDegrees(chord, form);
  if (!tones || tones.some((t) => !t)) return null;
  // Stack ascending from the first tone; each next tone goes above the last.
  let prev = -Infinity;
  const entries = tones.map((tone) => {
    let offset = tone.semitones % 12;
    while (offset <= prev) offset += 12;
    prev = offset;
    return { tone, offset };
  });
  // B-form sits lower by convention (7th below the root's octave).
  const shift = form === 'B' ? -12 : 0;
  return voicing(chord, `rootless${form}`, `Rootless ${form} (${tones.map((t) => t.label).join('–')})`,
    'Rootless (Bill Evans)',
    form === 'A'
      ? 'Left-hand voicing built 3–5(13)–7–9: the root is left to the bass. Alternating A and B forms through a ii–V–I keeps the guide tones nearly motionless.'
      : 'The A form flipped: 7–9 on the bottom, 3 on top. Pairs with the A form for minimal-motion voice leading.',
    entries.map((e) => ({ ...e, offset: e.offset + shift })));
}

// --- Color voicings ---

function kennyBarron(chord) {
  if (chord.quality.cat !== 'm7') return null;
  const t = (d, s) => toneOf(chord, d, s);
  const entries = [
    { tone: t(1, 0), offset: -12 }, { tone: t(5, 7), offset: -5 }, { tone: t(9, 14), offset: 2 },
    { tone: t(3, 3), offset: 3 }, { tone: t(7, 10), offset: 10 }, { tone: t(11, 17), offset: 17 },
  ];
  if (entries.some((e) => !e.tone)) return null;
  return voicing(chord, 'kennyBarron', 'Kenny Barron voicing (1–5–9 / ♭3–♭7–11)', 'Color voicings',
    'Two stacks of perfect fifths a half step apart — Kenny Barron\'s signature m11 sound. Left hand 1–5–9, right hand ♭3–♭7–11. Piano-specific; too wide for one guitar shape.', entries);
}

function soWhat(chord) {
  if (chord.quality.cat !== 'm7') return null;
  const t = (d, s) => toneOf(chord, d, s);
  const entries = [
    { tone: t(1, 0), offset: 0 }, { tone: t(4, 5), offset: 5 }, { tone: t(7, 10), offset: 10 },
    { tone: t(3, 3), offset: 15 }, { tone: t(5, 7), offset: 19 },
  ];
  if (entries.some((e) => !e.tone)) return null;
  return voicing(chord, 'soWhat', '"So What" voicing (quartal)', 'Color voicings',
    'Three perfect fourths capped by a major third — Bill Evans\'s voicing from Miles Davis\'s "So What." The quartal sound of modal jazz.', entries);
}

// Each triad note: [degree, canonical semitones for labeling, octave offset].
const UPPER_STRUCTURES = [
  { id: 'ustII', numeral: 'II', triad: [[9, 14, 14], [11, 18, 18], [13, 21, 21]], sound: '13♯11 — the Lydian dominant sound' },
  { id: 'ustVI', numeral: 'VI', triad: [[13, 21, 21], [9, 13, 25], [3, 4, 28]], sound: '13♭9 — bright but altered' },
  { id: 'ustbVI', numeral: '♭VI', triad: [[13, 20, 20], [1, 0, 24], [9, 15, 27]], sound: '7♯9♭13 — the full altered color' },
];

function upperStructures(chord) {
  if (chord.quality.cat !== 'dom') return [];
  const t3 = toneOf(chord, 3), t7 = toneOf(chord, 7);
  return UPPER_STRUCTURES.map((ust) => {
    const triad = ust.triad.map(([deg, canonical, offset]) => {
      const tone = toneOf(chord, deg, canonical);
      return tone ? { tone, offset } : null;
    });
    if (triad.some((x) => !x)) return null;
    return voicing(chord, ust.id, `Upper structure ${ust.numeral}`, 'Upper structures',
      `A major triad on the ${ust.numeral} over the tritone (3 + ♭7). Yields ${ust.sound}.`,
      [{ tone: t3, offset: 4 }, { tone: t7, offset: 10 }, ...triad]);
  }).filter(Boolean);
}

// --- Public: every applicable voicing for a chord, grouped ---

export function voicingsFor(chord) {
  const list = [];
  const core = coreFour(chord);
  list.push(...shellVoicings(chord));
  if (core) {
    list.push(voicing(chord, 'close', 'Close position', 'Drop voicings',
      'All four core tones stacked within one octave — the reference position the drop voicings are derived from.', closeStack(core)));
    list.push(dropVoicing(chord, 'drop2', 'Drop 2', [1]));
    list.push(dropVoicing(chord, 'drop3', 'Drop 3', [2]));
    list.push(dropVoicing(chord, 'drop24', 'Drop 2 & 4', [1, 3]));
  }
  const rA = rootlessVoicing(chord, 'A');
  const rB = rootlessVoicing(chord, 'B');
  if (rA) list.push(rA);
  if (rB) list.push(rB);
  const kb = kennyBarron(chord);
  if (kb) list.push(kb);
  const sw = soWhat(chord);
  if (sw) list.push(sw);
  list.push(...upperStructures(chord));
  return list.filter(Boolean);
}

// --- Guitar realization ---
// Maps a voicing's pitch stack onto string sets (notes on consecutive-ish
// strings, low→high), trying octave shifts, keeping frets 0–15 and the
// span fingerable. Drop 2 / drop 3 voicings ARE guitar chord shapes, so
// this yields the idiomatic grips rather than approximations.

const TUNING = [40, 45, 50, 55, 59, 64]; // E2 A2 D3 G3 B3 E4

const STRING_SETS = {
  1: [[5], [4], [3], [2], [1], [0]],
  2: [[0, 2], [1, 3], [2, 4], [3, 5]],
  3: [[0, 2, 3], [1, 3, 4], [1, 2, 3], [2, 3, 4], [3, 4, 5], [0, 1, 2]],
  4: [[2, 3, 4, 5], [1, 2, 3, 4], [0, 1, 2, 3], [0, 2, 3, 4], [1, 3, 4, 5]],
  5: [[1, 2, 3, 4, 5], [0, 1, 2, 3, 4], [0, 2, 3, 4, 5]],
  6: [[0, 1, 2, 3, 4, 5]],
};

export function guitarShape(voicingNotes) {
  const pitches = voicingNotes.map((n) => n.midi);
  const sets = STRING_SETS[pitches.length];
  if (!sets) return null;

  let best = null;
  for (const set of sets) {
    for (let shift = -24; shift <= 24; shift += 12) {
      const frets = set.map((s, i) => pitches[i] + shift - TUNING[s]);
      if (frets.some((f) => f < 0 || f > 15)) continue;
      const fretted = frets.filter((f) => f > 0);
      const span = fretted.length ? Math.max(...fretted) - Math.min(...fretted) : 0;
      if (span > 4) continue;
      const avg = frets.reduce((a, b) => a + b, 0) / frets.length;
      const score = span * 3 + avg * 0.4 + (Math.max(...frets) > 12 ? 6 : 0);
      if (!best || score < best.score) {
        best = { score, strings: set, frets };
      }
    }
  }
  if (!best) return null;

  // Full 6-string picture: null = muted.
  const byString = Array(6).fill(null);
  best.strings.forEach((s, i) => { byString[s] = best.frets[i]; });
  const fretted = best.frets.filter((f) => f > 0);
  const baseFret = fretted.length && Math.max(...fretted) > 4 ? Math.min(...fretted) : 1;
  return { frets: byString, baseFret, labels: voicingNotes.map((n) => n.label) };
}
```

## Source: `harmony.js`

```js
// Key-level harmony: diatonic chords, secondary dominants, tritone subs,
// modal interchange, key membership, and the relationship groups behind
// the Harmony Map. Everything is derived from spelled scale degrees so
// chord symbols come out with correct enharmonics (A♭7, not G♯7).

import { spellInterval, noteName } from './notes.js';
import { buildChord, getQuality } from './chords.js';

const MAJOR_STEPS = [[1, 0], [2, 2], [3, 4], [4, 5], [5, 7], [6, 9], [7, 11]];
const MINOR_STEPS = [[1, 0], [2, 2], [3, 3], [4, 5], [5, 7], [6, 8], [7, 10]]; // natural minor

export function majorScale(root) {
  return MAJOR_STEPS.map(([deg, semis]) => spellInterval(root, deg, semis));
}

export function minorScale(root) {
  return MINOR_STEPS.map(([deg, semis]) => spellInterval(root, deg, semis));
}

const MAJOR_DIATONIC = [
  { numeral: 'Imaj7', triadNumeral: 'I', quality: 'maj7', triad: 'maj' },
  { numeral: 'iim7', triadNumeral: 'ii', quality: 'm7', triad: 'm' },
  { numeral: 'iiim7', triadNumeral: 'iii', quality: 'm7', triad: 'm' },
  { numeral: 'IVmaj7', triadNumeral: 'IV', quality: 'maj7', triad: 'maj' },
  { numeral: 'V7', triadNumeral: 'V', quality: '7', triad: 'maj' },
  { numeral: 'vim7', triadNumeral: 'vi', quality: 'm7', triad: 'm' },
  { numeral: 'viim7♭5', triadNumeral: 'vii°', quality: 'm7b5', triad: 'dim' },
];

// Jazz-practice minor: natural-minor sevenths with the harmonic-minor V7
// and vii°7 noted alongside (the V is almost always played dominant).
const MINOR_DIATONIC = [
  { numeral: 'im7', triadNumeral: 'i', quality: 'm7', triad: 'm', note: 'often played im6 / im(maj7) as a tonic' },
  { numeral: 'iim7♭5', triadNumeral: 'ii°', quality: 'm7b5', triad: 'dim' },
  { numeral: '♭IIImaj7', triadNumeral: '♭III', quality: 'maj7', triad: 'maj' },
  { numeral: 'ivm7', triadNumeral: 'iv', quality: 'm7', triad: 'm' },
  { numeral: 'vm7', triadNumeral: 'v', quality: 'm7', triad: 'm', note: 'raised to V7 via harmonic minor at cadences' },
  { numeral: '♭VImaj7', triadNumeral: '♭VI', quality: 'maj7', triad: 'maj' },
  { numeral: '♭VII7', triadNumeral: '♭VII', quality: '7', triad: 'maj' },
];

export function diatonicChords(root, mode) {
  const scale = mode === 'minor' ? minorScale(root) : majorScale(root);
  const table = mode === 'minor' ? MINOR_DIATONIC : MAJOR_DIATONIC;
  return table.map((row, i) => ({
    ...row,
    root: scale[i],
    chord: buildChord(scale[i], row.quality),
    triadChord: buildChord(scale[i], row.triad),
  }));
}

export function secondaryDominants(root) {
  const scale = majorScale(root);
  // V7 of ii, iii, IV, V, vi — a dominant a perfect 5th above each target.
  return [1, 2, 3, 4, 5].map((idx) => {
    const target = scale[idx];
    const domRoot = spellInterval(target, 5, 7);
    return {
      label: `V7/${MAJOR_DIATONIC[idx].triadNumeral}`,
      chord: buildChord(domRoot, '7'),
      resolvesTo: buildChord(target, MAJOR_DIATONIC[idx].quality),
    };
  });
}

// Tritone substitute: the dominant whose root is a tritone away shares the
// same guide tones (3↔♭7 swap). Spelled as ♭2 of the resolution target.
export function tritoneSub(domChord) {
  const target = spellInterval(domChord.root, 4, 5); // where the dominant resolves
  const subRoot = spellInterval(target, 2, 1); // ♭2 of the target
  return buildChord(subRoot, '7');
}

export function borrowedChords(root) {
  const s = (deg, semis) => spellInterval(root, deg, semis);
  return [
    { label: 'ivm7', chord: buildChord(s(4, 5), 'm7'), from: 'parallel minor — the classic "backdoor" setup' },
    { label: '♭VImaj7', chord: buildChord(s(6, 8), 'maj7'), from: 'parallel minor' },
    { label: '♭VII7', chord: buildChord(s(7, 10), '7'), from: 'parallel minor — the backdoor dominant, resolving up a whole step to I' },
    { label: '♭IIImaj7', chord: buildChord(s(3, 3), 'maj7'), from: 'parallel minor' },
    { label: 'iim7♭5', chord: buildChord(s(2, 2), 'm7b5'), from: 'parallel minor — darkens the ii–V' },
    { label: '♭IImaj7', chord: buildChord(s(2, 1), 'maj7'), from: 'Neapolitan — chromatic color above the tonic' },
  ];
}

// Which keys contain this chord diatonically, and as what.
export function keysContaining(chord) {
  const results = [];
  const pcs = new Set(chord.tones.map((t) => t.note.pc));
  for (const [mode] of [['major'], ['minor']]) {
    for (const tonicName of ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B']) {
      const tonic = { letter: tonicName[0], acc: tonicName.length > 1 ? (tonicName[1] === '♭' ? -1 : 1) : 0, pc: 0 };
      tonic.pc = (({ C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 })[tonic.letter] + tonic.acc + 12) % 12;
      for (const row of diatonicChords(tonic, mode)) {
        if (row.chord.root.pc !== chord.root.pc) continue;
        const rowPcs = new Set(row.chord.tones.map((t) => t.note.pc));
        if ([...pcs].every((pc) => rowPcs.has(pc)) || [...rowPcs].every((pc) => pcs.has(pc))) {
          results.push({ key: `${tonicName} ${mode}`, numeral: row.numeral });
        }
      }
    }
  }
  return results;
}

// --- Harmony Map: related-chord groups for a center chord, each with a
// stated reason. Purely tonal-theory relations; no key context required. ---

export function relatedChords(chord) {
  const groups = [];
  const cat = chord.quality.cat;
  const s = (deg, semis) => spellInterval(chord.root, deg, semis);
  const item = (c, why) => (c ? { symbol: c.symbol, why } : null);

  if (cat === 'dom') {
    const target = s(4, 5);
    groups.push({
      label: 'Resolves to',
      items: [
        item(buildChord(target, 'maj7'), 'down a fifth — the V7→I resolution'),
        item(buildChord(target, 'm7'), 'down a fifth to minor — V7→i'),
        item(buildChord(target, '6'), 'Barry Harris tonic: the 6th chord as home'),
        item(buildChord(spellInterval(target, 6, 9), 'm7'), 'deceptive resolution — vi instead of I'),
      ],
    });
    groups.push({
      label: 'Substitutes',
      items: [
        item(tritoneSub(chord), 'tritone sub — same guide tones, chromatic bass'),
        item(buildChord(s(5, 7), 'm6'), 'Barry Harris: the m6 on the 5th — same sound as this 9/13 chord'),
        item(buildChord(s(2, 1), 'dim7'), 'dim7 on the ♭9 — the 7♭9 upper structure'),
      ],
    });
    groups.push({
      label: 'Barry Harris family (shared dim7)',
      items: [3, 6, 9].map((semis) => {
        const deg = { 3: 3, 6: 5, 9: 6 }[semis];
        return item(buildChord(s(deg, semis), '7'), 'shares this chord\'s 7♭9 diminished core — a minor 3rd apart');
      }),
    });
  }

  if (cat === 'maj7' || cat === '6' || cat === 'triad-maj') {
    groups.push({
      label: 'Substitutes',
      items: [
        item(buildChord(s(6, 9), 'm7'), 'relative minor — the same notes as this chord\'s 6th voicing'),
        item(buildChord(s(3, 4), 'm7'), 'iii for I — shares three tones'),
        item(buildChord(chord.root, cat === '6' ? 'maj7' : '6'), 'Barry Harris: maj7 and 6 are two faces of the same tonic'),
      ],
    });
    groups.push({
      label: 'Approached by',
      items: [
        item(buildChord(s(5, 7), '7'), 'its V7'),
        item(buildChord(s(2, 2), 'm7'), 'its ii — start of the ii–V'),
        item(buildChord(s(2, 1), '7'), 'sub-V — tritone sub resolving down a half step'),
        item(buildChord(s(7, 10), '7'), 'backdoor dominant (♭VII7), borrowed from the parallel minor'),
      ],
    });
  }

  if (cat === 'm7' || cat === 'triad-min' || cat === 'm6' || cat === 'mMaj7') {
    groups.push({
      label: 'Moves to',
      items: [
        item(buildChord(s(4, 5), '7'), 'as ii: on to its V7'),
        item(buildChord(s(7, 10), 'maj7'), 'as ii: through V to the I a whole step below', ),
      ].filter(Boolean),
    });
    groups.push({
      label: 'Substitutes',
      items: [
        item(buildChord(s(3, 3), '6'), 'relative major 6 — literally the same four notes (Barry Harris)'),
        item(buildChord(chord.root, cat === 'm6' ? 'm7' : 'm6'), 'the minor-tonic alter ego'),
        item(buildChord(chord.root, 'mMaj7'), 'minor-major 7 — the melodic-minor tonic color'),
      ],
    });
    groups.push({
      label: 'Approached by',
      items: [
        item(buildChord(s(5, 7), '7'), 'its V7 (usually with a ♭9)'),
        item(buildChord(s(2, 2), 'm7b5'), 'its iiø — the minor ii–V'),
      ],
    });
  }

  if (cat === 'm7b5') {
    groups.push({
      label: 'Moves to',
      items: [item(buildChord(s(4, 5), '7b9'), 'iiø–V7♭9: the minor cadence engine')],
    });
    groups.push({
      label: 'Substitutes',
      items: [
        item(buildChord(s(3, 3), 'm6'), 'the same four notes as the m6 a minor 3rd up (Barry Harris)'),
        item(buildChord(s(6, 8), '9'), 'rootless dominant 9 a major 3rd below'),
      ],
    });
  }

  if (cat === 'dim7') {
    const resolutions = [1].map(() => {
      const up = spellInterval(chord.root, 2, 1);
      return item(buildChord(up, 'maj7'), 'resolves up a half step — leading-tone diminished');
    });
    groups.push({ label: 'Resolves to', items: resolutions });
    groups.push({
      label: 'The four dominants that contain it (7♭9)',
      items: [11, 2, 5, 8].map((semis) => {
        const domRoot = spellInterval(chord.root, semis === 11 ? 7 : semis === 2 ? 2 : semis === 5 ? 4 : 6, semis);
        return item(buildChord(domRoot, '7b9'), 'this dim7 sits on its ♭9');
      }),
    });
  }

  return groups
    .map((g) => ({ ...g, items: g.items.filter(Boolean) }))
    .filter((g) => g.items.length);
}
```

## Source: `barry.js`

```js
// Barry Harris's 6th-diminished system. The core object: an 8-note "scale
// of chords" interleaving a 6th chord with the diminished 7th built on its
// major 7th degree, so harmonizing every scale step alternates
// 6th-chord inversion / dim7 inversion — motion over a static tonic.

import { spellInterval, noteName } from './notes.js';
import { buildChord } from './chords.js';

// [degree, semitones] — major: 1 2 3 4 5 ♭6 6 7; minor: 1 2 ♭3 4 5 ♭6 6 7.
const MAJOR_6DIM = [[1, 0], [2, 2], [3, 4], [4, 5], [5, 7], [6, 8], [6, 9], [7, 11]];
const MINOR_6DIM = [[1, 0], [2, 2], [3, 3], [4, 5], [5, 7], [6, 8], [6, 9], [7, 11]];

function spellScale(root, steps) {
  // The ♭6/6 pair shares a letter degree; spell the ♭6 as ♯5 when the 6 is
  // natural so both appear (G♯ vs A in C major 6-dim) — Barry's own habit.
  return steps.map(([deg, semis], i) => {
    if (semis === 8 && steps[i + 1]?.[1] === 9) {
      const note = spellInterval(root, 5, 8); // ♯5 spelling
      return { note, name: noteName(note), label: '♯5/♭6' };
    }
    const note = spellInterval(root, deg, semis);
    return { note, name: noteName(note), label: (semis === 3 ? '♭3' : String(deg)) };
  });
}

export function sixthDimScale(root, mode) {
  const steps = mode === 'minor' ? MINOR_6DIM : MAJOR_6DIM;
  const scale = spellScale(root, steps);
  const semis = steps.map(([, s]) => s);

  // Harmonize: chord on step i = scale indices i, i+2, i+4, i+6 (mod 8).
  // Odd steps land on the 6th chord's tones, even on the dim7's.
  const sixthChord = buildChord(root, mode === 'minor' ? 'm6' : '6');
  const dimRoot = spellInterval(root, 7, 11);
  const dimChord = buildChord(dimRoot, 'dim7');

  const positions = semis.map((_, i) => {
    const idxs = [i, (i + 2) % 8, (i + 4) % 8, (i + 6) % 8];
    let prev = -Infinity;
    const notes = idxs.map((idx) => {
      let midi = 48 + root.pc + semis[idx];
      while (midi <= prev) midi += 12;
      prev = midi;
      return { midi, name: scale[idx].name, label: scale[idx].label };
    });
    const isSixth = i % 2 === 0;
    return {
      step: i + 1,
      chordName: isSixth ? `${sixthChord.symbol}${i === 0 ? '' : ' (inv)'}` : `${dimChord.symbol} (inv)`,
      family: isSixth ? 'sixth' : 'dim',
      notes,
    };
  });

  return { root, mode, scale, sixthChord, dimChord, positions };
}

// A dim7 is symmetrical: the same four notes read from any of its tones.
// Those four notes are also the ♭9-upper-structure of four dominants a
// minor 3rd apart — Barry's bridge from diminished to dominant harmony.
export function dominantFamily(domRoot) {
  const members = [[1, 0], [3, 3], [5, 6], [6, 9]].map(([deg, semis]) => {
    const r = spellInterval(domRoot, deg, semis);
    return buildChord(r, '7');
  });
  const dimRoot = spellInterval(domRoot, 3, 4); // dim7 on the dominant's 3rd
  return { members, dim: buildChord(dimRoot, 'dim7') };
}

// How Barry hears each chord family — which 6-dim scale to reach for.
export function barryAnalysis(chord) {
  const cat = chord.quality.cat;
  const s = (deg, semis) => spellInterval(chord.root, deg, semis);

  if (cat === '6' || cat === 'maj7' || cat === 'triad-maj') {
    return {
      scale: sixthDimScale(chord.root, 'major'),
      headline: `${chord.symbol} → ${noteName(chord.root)} major 6th-diminished scale`,
      explanation: cat === 'maj7'
        ? 'Barry treats maj7 as a 6 chord — the 6th replaces the 7th as the resting tone, and the major 7th becomes a scale tone you pass through. Harmonize the scale below and every other step is your tonic chord.'
        : 'This is the home sound: the 6th chord and its leading-tone dim7 woven into one scale. Moving stepwise through the positions creates motion while the harmony stands still.',
    };
  }
  if (cat === 'm6' || cat === 'mMaj7' || cat === 'triad-min') {
    return {
      scale: sixthDimScale(chord.root, 'minor'),
      headline: `${chord.symbol} → ${noteName(chord.root)} minor 6th-diminished scale`,
      explanation: 'The minor tonic in Barry\'s world is the m6 chord. Same construction as the major version with a ♭3 — the dim7 on the major 7th supplies the motion.',
    };
  }
  if (cat === 'm7') {
    const rel = s(3, 3);
    return {
      scale: sixthDimScale(rel, 'major'),
      headline: `${chord.symbol} = ${noteName(rel)}6 → ${noteName(rel)} major 6th-diminished scale`,
      explanation: `A m7 chord is a major 6 chord starting from its 6th: ${chord.symbol} and ${noteName(rel)}6 are the same four notes. Barry runs the ${noteName(rel)} major 6th-diminished scale over both.`,
    };
  }
  if (cat === 'm7b5') {
    const rel = s(3, 3);
    return {
      scale: sixthDimScale(rel, 'minor'),
      headline: `${chord.symbol} = ${noteName(rel)}m6 → ${noteName(rel)} minor 6th-diminished scale`,
      explanation: `Half-diminished is a minor 6 chord in disguise: ${chord.symbol} inverts to ${noteName(rel)}m6. Think from ${noteName(rel)} and the ø chord voices itself.`,
    };
  }
  if (cat === 'dom') {
    const fifth = s(5, 7);
    return {
      scale: sixthDimScale(fifth, 'minor'),
      family: dominantFamily(chord.root),
      headline: `${chord.symbol} → ${noteName(fifth)}m6 (the m6 on the 5th) + the ♭9 diminished family`,
      explanation: `Two Barry moves: (1) ${noteName(fifth)}m6 over a ${noteName(chord.root)} bass IS ${noteName(chord.root)}9 — so the ${noteName(fifth)} minor 6th-diminished scale harmonizes this dominant. (2) Add the ♭9 and the top of the chord is a dim7, shared with three sibling dominants a minor 3rd apart — each can substitute for the others.`,
    };
  }
  if (cat === 'dim7') {
    return {
      family: { members: [11, 2, 5, 8].map((semis, i) => buildChord(spellInterval(chord.root, [7, 2, 4, 6][i], semis), '7')), dim: chord },
      headline: `${chord.symbol} → four dominants, four resolutions`,
      explanation: 'A dim7 is completely symmetrical — the same notes from any of its four tones. It lives inside four different 7♭9 chords (roots a major 3rd below each tone), so it can resolve four ways. Barry: "the diminished is the mother of the dominants."',
    };
  }
  return null;
}
```

## Source: `graph.js`

```js
// The walkable harmony graph: for any center chord, every worthwhile place to
// go, every common way in, and every stand-in — each edge carrying a short
// reason (the long "why" comes from voicelead.js when an edge is inspected).
//
// Curation IS the feature. In tonal music almost anything can follow anything,
// so a naive graph is a hairball. Edges here are hand-curated per chord family
// and weighted: strength 3 = strong functional pull, 2 = natural continuation,
// 1 = color move (hidden unless "adventurous" is on).
//
// dir: 'out' = where this chord goes · 'in' = what leads here · 'sub' = swaps.

import { spellInterval, noteName, parseNote } from './notes.js';
import { buildChord, parseChord, QUALITIES } from './chords.js';
import { diatonicChords } from './harmony.js';

const MAJ_FAMILY = new Set(['maj7', '6', 'triad-maj']);
const MIN_FAMILY = new Set(['m7', 'triad-min', 'm6', 'mMaj7']);

// Chord-quality family, for Illustrated-Harmony-style color coding: the node's
// fill answers "what kind of chord is this?" independent of its relationship
// to the current center (which stays on the connecting line).
export function qualityFamily(chord) {
  const cat = chord.quality.cat;
  if (MAJ_FAMILY.has(cat)) return 'major';
  if (MIN_FAMILY.has(cat)) return 'minor';
  if (cat === 'dom' || cat === 'sus') return 'dominant';
  if (cat === 'dim' || cat === 'dim7' || cat === 'm7b5') return 'diminished';
  if (cat === 'aug') return 'augmented';
  return 'other';
}

export const EDGE_CATEGORIES = [
  { id: 'resolution', label: 'Resolves to',   dir: 'out' },
  { id: 'motion',     label: 'Moves on to',   dir: 'out' },
  { id: 'color',      label: 'Color moves',   dir: 'out' },
  { id: 'approach',   label: 'Approached by', dir: 'in'  },
  { id: 'sub',        label: 'Stands in for', dir: 'sub' },
];

export function harmonyEdges(chord, { adventurous = false, keyCtx = null } = {}) {
  const cat = chord.quality.cat;
  const s = (deg, semis) => spellInterval(chord.root, deg, semis);
  const edges = [];
  const seen = new Set([chord.symbol]);
  const add = (category, c, reason, strength = 2) => {
    if (!c || seen.has(c.symbol)) return;
    seen.add(c.symbol);
    edges.push({ category, symbol: c.symbol, reason, strength });
  };

  if (cat === 'dom' || cat === 'aug') {
    const target = s(4, 5);
    add('resolution', buildChord(target, 'maj7'), 'V7→I — the fundamental cadence', 3);
    add('resolution', buildChord(target, 'm7'), 'V7→i — resolving into minor', 3);
    add('resolution', buildChord(target, '6'), 'V7→I6 — the Barry Harris tonic', 2);
    add('resolution', buildChord(spellInterval(target, 6, 9), 'm7'), 'deceptive — vi arrives where I was promised', 2);
    add('motion', buildChord(target, '7'), 'dominant chain — each V7 becomes ii of the next', 2);
    add('color', buildChord(spellInterval(target, 6, 8), 'maj7'), '♭VI landing — the parallel-minor deceptive cadence', 1);
    add('color', buildChord(s(2, 1), 'maj7'), 'common-tone surprise — up a half step on the ♭9 axis', 1);
    add('approach', buildChord(s(5, 7), 'm7'), 'the ii that makes this a V', 3);
    add('approach', buildChord(s(5, 7), '7'), 'its own dominant (V of V)', 2);
    add('approach', buildChord(s(2, 1), '7'), 'chromatic dominant from a half step above', 2);
    add('approach', buildChord(chord.root, '7sus4'), 'the suspension that releases into it', 2);
    add('sub', buildChord(s(5, 6), '7'), 'tritone sub — same tritone, chromatic bass', 3);
    add('sub', buildChord(s(5, 7), 'm6'), 'Barry Harris: the m6 on the 5th', 2);
    add('sub', buildChord(s(2, 1), 'dim7'), 'dim7 on the ♭9 — its own upper structure', 2);
    for (const semis of [3, 6, 9]) {
      const deg = { 3: 3, 6: 5, 9: 6 }[semis];
      add('sub', buildChord(s(deg, semis), '7'), 'shares the same dim7 core — minor-3rd family', semis === 6 ? 1 : 2);
    }
  }

  if (MAJ_FAMILY.has(cat)) {
    add('motion', buildChord(s(2, 2), 'm7'), 'to ii — set out on a ii–V journey', 3);
    add('motion', buildChord(s(4, 5), 'maj7'), 'to IV — the plagal neighbor', 2);
    add('motion', buildChord(s(6, 9), '7'), 'V7/ii — a secondary dominant departure', 2);
    add('motion', buildChord(s(3, 4), '7'), 'V7/vi — darkening toward the relative minor', 2);
    add('color', buildChord(s(6, 8), 'maj7'), '♭VI — chromatic mediant shimmer (shared tone, shifted world)', 1);
    add('color', buildChord(s(3, 3), 'maj7'), '♭III — borrowed mediant', 1);
    add('color', buildChord(s(3, 4), 'maj7'), 'III — brightened mediant', 1);
    add('color', buildChord(chord.root, 'm7'), 'parallel minor — same root, darkened world', 1);
    add('color', buildChord(s(4, 6), 'm7b5'), '♯ivø — the key\'s far corner, the Lydian pull', 1);
    add('approach', buildChord(s(5, 7), '7'), 'its V7', 3);
    add('approach', buildChord(s(2, 1), '7'), 'subV — sliding down onto it', 2);
    add('approach', buildChord(s(7, 10), '7'), 'backdoor ♭VII7 — arriving up a whole step', 2);
    add('approach', buildChord(s(7, 11), 'dim7'), 'leading-tone dim7 from below', 2);
    add('sub', buildChord(chord.root, cat === '6' ? 'maj7' : '6'), 'maj7 and 6 — two faces of one tonic', 2);
    add('sub', buildChord(s(6, 9), 'm7'), 'relative minor — the same notes as the 6th chord', 2);
    add('sub', buildChord(s(3, 4), 'm7'), 'iii for I — tonic substitute', 2);
  }

  if (MIN_FAMILY.has(cat)) {
    add('motion', buildChord(s(4, 5), '7'), 'as ii → its V7: the engine of jazz motion', 3);
    add('motion', buildChord(s(3, 3), 'maj7'), 'lift to the relative major', 2);
    add('motion', buildChord(s(4, 5), 'm7'), 'to iv — deeper into minor', 2);
    add('motion', buildChord(s(6, 8), 'maj7'), 'to ♭VI — the minor key\'s warm plateau', 2);
    add('color', buildChord(chord.root, 'maj7'), 'parallel major — the Picardy lift', 1);
    add('color', buildChord(s(7, 10), '7'), '♭VII7 — modal, backdoor color', 1);
    add('approach', buildChord(s(5, 7), '7b9'), 'its V7♭9 — the minor cadence', 3);
    add('approach', buildChord(s(2, 2), 'm7b5'), 'iiø — the minor ii–V', 2);
    add('approach', buildChord(s(2, 1), '7'), 'subV from above', 1);
    add('sub', buildChord(s(3, 3), '6'), 'relative major 6 — literally the same four notes', 2);
    add('sub', buildChord(chord.root, cat === 'm6' ? 'm7' : 'm6'), 'the minor-tonic alter ego', 2);
    add('sub', buildChord(chord.root, 'mMaj7'), 'melodic-minor tonic color', cat === 'mMaj7' ? 0 : 1);
  }

  if (cat === 'm7b5') {
    add('motion', buildChord(s(4, 5), '7b9'), 'iiø → V7♭9 — the minor-key cadence engine', 3);
    add('approach', buildChord(s(5, 7), '7'), 'a dominant a fifth up sets it in motion', 1);
    add('sub', buildChord(s(3, 3), 'm6'), 'the same four notes as the m6 a minor 3rd up', 2);
    add('sub', buildChord(s(6, 8), '9'), 'rootless dominant 9 a major 3rd below', 2);
    add('sub', buildChord(chord.root, 'm7'), 'lift the ♭5 — the softer plain minor', 1);
  }

  if (cat === 'dim7' || cat === 'dim') {
    add('resolution', buildChord(s(2, 1), 'maj7'), 'resolves up a half step — leading-tone diminished', 3);
    add('resolution', buildChord(s(2, 1), 'm7'), 'up a half step into minor', 3);
    if (cat === 'dim7') {
      add('sub', buildChord(s(3, 3), 'dim7'), 'the same four notes, renamed (dim7 symmetry)', 2);
      add('sub', buildChord(s(5, 6), 'dim7'), 'the same four notes, renamed (dim7 symmetry)', 2);
      for (const [deg, semis] of [[7, 11], [2, 2], [4, 5], [6, 8]]) {
        add('approach', buildChord(s(deg, semis), '7b9'), 'this dim7 lives inside it as the 7♭9', 2);
      }
    }
  }

  if (cat === 'sus') {
    add('resolution', buildChord(chord.root, chord.quality.id === '7sus4' ? '7' : 'maj'), 'the suspension releases — 4 falls to 3', 3);
    add('resolution', buildChord(s(4, 5), 'maj7'), 'or skip the release and resolve home directly', 2);
    add('sub', buildChord(s(5, 7), 'm7'), 'the ii it contains (a m7 over the 5th)', 2);
  }

  // Universal color: negative-harmony mirror, when a key context exists.
  if (keyCtx) {
    const neg = negativeHarmony(chord, keyCtx);
    if (neg) add('color', neg, `negative harmony mirror in ${noteName(keyCtx.root)} — every interval reflected around the key's axis`, 1);
  }

  const kept = edges.filter((e) => e.strength > 0 && (adventurous || e.strength >= 2));
  return EDGE_CATEGORIES
    .map((c) => ({ ...c, edges: kept.filter((e) => e.category === c.id) }))
    .filter((g) => g.edges.length);
}

// --- Negative harmony -------------------------------------------------------
// Reflect every pitch class around the key's tonic–dominant axis
// (pc → 2·tonic + 7 − pc). If the mirrored set spells a nameable chord,
// return it. Rooted search prefers plainer qualities.

const NEG_PREF = ['maj', 'm', '7', 'm7', 'maj7', 'm7b5', 'dim7', '6', 'm6', 'dim', 'aug'];

export function negativeHarmony(chord, keyCtx) {
  const t = keyCtx.root.pc;
  const mirrored = new Set(chord.tones.map((tone) => ((2 * t + 7 - tone.note.pc) % 12 + 12) % 12));
  const roots = ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];
  for (const qid of NEG_PREF) {
    const q = QUALITIES.find((x) => x.id === qid);
    if (q.intervals.length !== mirrored.size) continue;
    for (const rName of roots) {
      const root = parseNote(rName);
      const pcs = new Set(q.intervals.map(([, semis]) => (root.pc + semis) % 12));
      if (pcs.size === mirrored.size && [...pcs].every((pc) => mirrored.has(pc))) {
        return buildChord(root, qid);
      }
    }
  }
  return null;
}

// --- Roman numeral of a chord relative to a key ------------------------------

const OFFSET_NUMERAL = ['I', '♭II', 'II', '♭III', 'III', 'IV', '♯IV', 'V', '♭VI', 'VI', '♭VII', 'VII'];
const LOWER_CATS = new Set(['triad-min', 'm7', 'm6', 'mMaj7', 'm7b5', 'dim', 'dim7']);

export function romanNumeral(chord, keyCtx) {
  if (!keyCtx) return null;
  // Exact diatonic match first (correct numeral including ° and ø forms).
  for (const row of diatonicChords(keyCtx.root, keyCtx.mode)) {
    if (row.chord.root.pc === chord.root.pc && row.quality === chord.quality.id) return row.numeral;
  }
  const offset = ((chord.root.pc - keyCtx.root.pc) % 12 + 12) % 12;
  let base = OFFSET_NUMERAL[offset];
  if (LOWER_CATS.has(chord.quality.cat)) base = base.replace(/[IV]+/, (m) => m.toLowerCase());
  return base + (chord.quality.display || '');
}
```

## Source: `voicelead.js`

```js
// Voice-leading analysis: WHY a move from chord A to chord B sounds the way
// it does. Everything here is computed from the actual notes — common tones,
// semitone pulls, tritone resolutions, bass-root motion — and then translated
// into prose by mapping those measured facts onto their standard, textbook
// perceptual descriptions. No claim is generated that isn't backed by a
// computed fact about the two chords.

import { noteName } from './notes.js';

// Signed shortest pitch-class distance from a to b, in -6..+6 semitones.
function pcDist(a, b) {
  let d = ((b - a) % 12 + 12) % 12;
  if (d > 6) d -= 12;
  return d;
}

const tName = (tone) => tone.name || noteName(tone.note);

// --- The measured facts ---------------------------------------------------
// from/to are chord objects from buildChord()/parseChord().
// Returns:
// {
//   commonTones: [{ name, fromLabel, toLabel }],
//   moves:       [{ fromName, fromLabel, toName, toLabel, semis }],  // nearest-tone, non-common
//   bass:        { semisUp (0..11), type, text },
//   tritone:     null | { aName, bName, aToName, bToName, direction }, // both resolve by half step
//   guide:       [ strings ],   // guide-tone continuity facts
//   smoothness:  { total, movedVoices, verdict },
// }
export function voiceLeading(from, to) {
  const fromTones = from.tones;
  const toTones = to.tones;
  const toPcs = new Set(toTones.map((t) => t.note.pc));

  // Common tones: pitch classes present in both chords.
  const commonTones = [];
  for (const ft of fromTones) {
    if (!toPcs.has(ft.note.pc)) continue;
    const tt = toTones.find((t) => t.note.pc === ft.note.pc);
    const name = tName(ft) === tName(tt) ? tName(ft) : `${tName(ft)}/${tName(tt)}`;
    commonTones.push({ name, fromLabel: ft.label, toLabel: tt.label });
  }

  // Moving voices: each non-common source tone maps to its nearest target tone.
  const moves = [];
  for (const ft of fromTones) {
    if (toPcs.has(ft.note.pc)) continue;
    let best = null;
    for (const tt of toTones) {
      const d = pcDist(ft.note.pc, tt.note.pc);
      if (!best || Math.abs(d) < Math.abs(best.semis)) {
        best = { fromName: tName(ft), fromLabel: ft.label, toName: tName(tt), toLabel: tt.label, semis: d };
      }
    }
    if (best) moves.push(best);
  }
  moves.sort((a, b) => Math.abs(a.semis) - Math.abs(b.semis));

  // Smoothness: how far does each TARGET tone sit from the nearest source
  // tone? (Target-side, so added color tones count as motion to reach.)
  const fromPcs = fromTones.map((t) => t.note.pc);
  let total = 0;
  for (const tt of toTones) {
    total += Math.min(...fromPcs.map((pc) => Math.abs(pcDist(pc, tt.note.pc))));
  }
  const movedVoices = moves.length;
  const verdict =
    total <= 2 ? 'velvet — barely any travel' :
    total <= 4 ? 'smooth — mostly stepwise' :
    total <= 7 ? 'moderate — a real change of place' :
    'a leap — the ear lands somewhere new';

  // Tritone resolution: a pair of source tones 6 semitones apart where both
  // resolve by half step. The engine of dominant tension→release.
  let tritone = null;
  for (let i = 0; i < fromTones.length && !tritone; i++) {
    for (let j = i + 1; j < fromTones.length && !tritone; j++) {
      const a = fromTones[i], b = fromTones[j];
      if (Math.abs(pcDist(a.note.pc, b.note.pc)) !== 6) continue;
      const resolve = (t) => {
        if (toPcs.has(t.note.pc)) return { to: toTones.find((x) => x.note.pc === t.note.pc), semis: 0 };
        let best = null;
        for (const tt of toTones) {
          const d = pcDist(t.note.pc, tt.note.pc);
          if (Math.abs(d) === 1 && (!best || Math.abs(d) < Math.abs(best.semis))) best = { to: tt, semis: d };
        }
        return best;
      };
      const ra = resolve(a), rb = resolve(b);
      if (ra && rb && (ra.semis !== 0 || rb.semis !== 0) && Math.abs(ra.semis) <= 1 && Math.abs(rb.semis) <= 1) {
        tritone = {
          aName: tName(a), bName: tName(b),
          aToName: tName(ra.to), bToName: tName(rb.to),
          aSemis: ra.semis, bSemis: rb.semis,
        };
      }
    }
  }

  // Guide-tone continuity: the 3rd/7th threads that stitch progressions together.
  const guide = [];
  const deg = (chord, d) => chord.tones.find((t) => t.degree === d);
  const f3 = deg(from, 3), f7 = deg(from, 7), t3 = deg(to, 3), t7 = deg(to, 7);
  if (f7 && t3 && Math.abs(pcDist(f7.note.pc, t3.note.pc)) === 1) {
    guide.push(`${tName(f7)} (the 7th) falls a half step to ${tName(t3)} (the new 3rd) — the classic guide-tone resolution.`);
  }
  if (f3 && t7 && f3.note.pc === t7.note.pc) {
    guide.push(`${tName(f3)} stays put, changing hats: 3rd of ${from.symbol} becomes the 7th of ${to.symbol} — the thread that stitches ii–V motion together.`);
  }
  if (f7 && t7 && Math.abs(pcDist(f7.note.pc, t7.note.pc)) === 1) {
    guide.push(`the 7ths walk chromatically: ${tName(f7)} → ${tName(t7)}.`);
  }

  // Bass (root) motion classification.
  const semisUp = ((to.root.pc - from.root.pc) % 12 + 12) % 12;
  const bass = { semisUp, ...BASS_CLASSES[semisUp] };

  return { commonTones, moves, bass, tritone, guide, smoothness: { total, movedVoices, verdict } };
}

const BASS_CLASSES = {
  0:  { type: 'static',       text: 'The bass holds still — this is transformation, not travel. The ear hears one place changing color.' },
  5:  { type: 'down-fifth',   text: 'The root falls a fifth — the strongest gravitational move in tonal music. The second chord lands as an arrival.' },
  7:  { type: 'up-fifth',     text: 'The root rises a fifth — away from gravity. This opens tension rather than releasing it (the I→V feeling).' },
  1:  { type: 'up-half',      text: 'The bass pushes up a half step — leading-tone energy in the bass itself; the ear is pulled bodily upward.' },
  11: { type: 'down-half',    text: 'The bass slides down a half step — the chromatic glide that makes tritone-sub motion feel like a velvet ramp instead of a jump.' },
  2:  { type: 'up-whole',     text: 'The bass steps up a whole tone — a stepwise arrival without a leading tone. Softer, rounder landing (the backdoor stride).' },
  10: { type: 'down-whole',   text: 'The bass settles down a whole step — an easy, unhurried descent.' },
  4:  { type: 'up-maj3',      text: 'Roots a major third apart — mediant territory: chords that share a tone but no function, so the move reads as color, not logic.' },
  3:  { type: 'up-min3',      text: 'Roots a minor third apart — close cousins (they often share two tones), so the ear hears kinship with a changed mood.' },
  8:  { type: 'down-maj3',    text: 'The root drops a major third — a mediant fall; shared tones keep it coherent while the ground shifts underneath.' },
  9:  { type: 'down-min3',    text: 'The root falls a minor third — the gentle, nostalgic drop (the sound of falling-thirds cycles).' },
  6:  { type: 'tritone',      text: 'The roots are a tritone apart — the most distant bass move possible. Maximum surprise; it works when the upper voices barely move.' },
};

// --- Prose: measured facts → why it sounds that way -----------------------

export function explainMove(from, to) {
  const vl = voiceLeading(from, to);
  const prose = [];

  prose.push(vl.bass.text);

  if (vl.tritone) {
    const t = vl.tritone;
    const intro = `${from.symbol} carries a tritone (${t.aName}–${t.bName}) — the most unstable interval there is —`;
    if (t.aSemis !== 0 && t.bSemis !== 0) {
      const contrary = (t.aSemis > 0) !== (t.bSemis > 0);
      prose.push(
        `${intro} and here it resolves by ${contrary ? 'contrary' : 'parallel'} half-step motion onto ${t.aToName}–${t.bToName}. That double half-step release is the physical mechanism of dominant tension and resolution.`
      );
    } else {
      const [stay, move, moveTo] = t.aSemis === 0
        ? [t.aName, t.bName, t.bToName] : [t.bName, t.aName, t.aToName];
      prose.push(
        `${intro} and here ${move} resolves by half step to ${moveTo} while ${stay} holds as an anchor. Releasing just one side of the tritone is enough to discharge its tension.`
      );
    }
  }

  for (const g of vl.guide) prose.push(g.charAt(0).toUpperCase() + g.slice(1));

  if (vl.commonTones.length >= 2) {
    prose.push(
      `${vl.commonTones.map((c) => c.name).join(' and ')} are shared by both chords — anchors the ear holds onto while the rest moves. High common-tone counts are why a change can feel like a shading rather than a departure.`
    );
  } else if (vl.commonTones.length === 1) {
    prose.push(
      `One tone (${vl.commonTones[0].name}) carries over — a single thread of continuity. Everything else moves around it, which is what gives common-tone progressions their pivoting, kaleidoscope quality.`
    );
  } else {
    prose.push('No tones are shared at all — the ear gets no anchor, so the second chord registers as a genuinely new place. That clean break is a color of its own.');
  }

  const halfSteps = vl.moves.filter((m) => Math.abs(m.semis) === 1);
  if (halfSteps.length) {
    const list = halfSteps.map((m) => `${m.fromName} ${m.semis > 0 ? 'rises' : 'falls'} to ${m.toName}`).join('; ');
    prose.push(
      `Half-step pulls: ${list}. The half step is the most magnetic move a voice can make — the ear hears these resolutions as inevitable, and they do most of the emotional work of the change.`
    );
  }

  prose.push(
    `Total voice travel: ${vl.smoothness.total} semitone${vl.smoothness.total === 1 ? '' : 's'} across ${vl.smoothness.movedVoices} moving voice${vl.smoothness.movedVoices === 1 ? '' : 's'} — ${vl.smoothness.verdict}.`
  );

  return { facts: vl, prose };
}

// --- Minimal-motion playback voicing ---------------------------------------
// Given the actual MIDI notes just played for the source chord, choose MIDI
// notes for the target chord where each target tone sits as close as possible
// to the sounding voices. This is what lets "hear the move" demonstrate the
// smooth voice-leading instead of jumping to a root-position block chord.

export function voiceLeadMidis(sourceMidis, toChord) {
  const out = [];
  for (const tt of toChord.tones) {
    let best = null;
    for (const src of sourceMidis) {
      // candidate octave placements of this tone around each source voice
      const base = src + pcDist(src % 12, tt.note.pc);
      for (const cand of [base, base - 12, base + 12]) {
        if (cand < 34 || cand > 88) continue;
        const cost = Math.min(...sourceMidis.map((s) => Math.abs(s - cand)));
        if (!best || cost < best.cost) best = { midi: cand, cost };
      }
    }
    if (best && !out.includes(best.midi)) out.push(best.midi);
  }
  out.sort((a, b) => a - b);
  return out;
}
```

## Source: `lessons.js`

```js
// Reference lessons for the harmony module. App content, not user data —
// they live in code, not IndexedDB. `examples` are chord symbols the view
// turns into clickable chips that load the Dictionary.

export const THEORY_LESSONS = [
  {
    topic: 'Foundations',
    title: 'Intervals: the raw material',
    body: 'Every chord and scale is a stack of intervals — distances measured in semitones and named by letter-distance (degree). The same pitch distance can have two names: three semitones is a minor 3rd (C→E♭) or an augmented 2nd (C→D♯), and the name matters because it tells you the note\'s function.\n\nThe intervals that do the most harmonic work: the major 3rd (4 semitones) and minor 3rd (3) that set a chord\'s gender; the perfect 5th (7) that anchors it; the minor 7th (10) and major 7th (11) that set its direction; and the tritone (6) — the unstable interval that makes dominant chords demand resolution.',
    examples: ['C', 'Cm', 'C7', 'Cmaj7'],
  },
  {
    topic: 'Foundations',
    title: 'Triads and seventh chords',
    body: 'Stack two 3rds and you have a triad: major (4+3), minor (3+4), diminished (3+3), augmented (4+4). Add one more 3rd for a seventh chord — the basic jazz unit. Five essential qualities: maj7 (bright, at rest), 7 (dominant — tense, wants to move), m7 (soft, mobile), m7♭5 (the minor world\'s dominant setup), and dim7 (pure instability, three stacked minor 3rds).\n\nLearn these five in all twelve keys and nearly every page of a real book becomes readable.',
    examples: ['Cmaj7', 'C7', 'Cm7', 'Cm7b5', 'Cdim7'],
  },
  {
    topic: 'Foundations',
    title: 'Extensions and alterations',
    body: 'Keep stacking 3rds past the 7th and you get the extensions: 9, 11, 13. They add color, not function — a C13 still behaves like C7. Alterations bend extensions chromatically (♭9, ♯9, ♯11, ♭13) and live mostly on dominant chords, where extra tension is welcome because resolution is coming.\n\nRules of thumb: the 11 clashes with the major 3rd (raise it to ♯11 on major/dominant chords; plain 11 is at home on minor chords); the ♭13 and the 5 fight, so one usually replaces the other.',
    examples: ['C9', 'C13', 'C7b9', 'C7s9', 'Cmaj7s11', 'Cm11'],
  },
  {
    topic: 'Voicings',
    title: 'Shell voicings and guide tones',
    body: 'The 3rd and 7th are the guide tones — the two notes that define a chord\'s quality. Root + 3 + 7 (or root + 7 + 3) is a shell voicing: the minimum that sounds like the chord. Everything else is decoration.\n\nThe deeper point: through a ii–V–I, the guide tones move by half step or not at all (the 7th of Dm7 falls to the 3rd of G7; the 7th of G7 falls to the 3rd of Cmaj7). Voice leading isn\'t something you add to shells — it\'s built into them.',
    examples: ['Dm7', 'G7', 'Cmaj7'],
  },
  {
    topic: 'Voicings',
    title: 'Root – Shell – Pretty',
    body: 'A progressive way to build voicings (as taught by Adam Maness at Open Studio): start with just the Root. Add the Shell — the 3rd and 7th, the chord\'s identity. Then add the Pretty — color tones on top: the 9th, the 13th on dominants, the 11th on minor chords.\n\nThe stages are the lesson: hear how the root alone is bass, root+shell is harmony, and the pretty notes are personality. The Dictionary tab plays all three stages for any chord.',
    examples: ['Cmaj7', 'G13', 'Dm9'],
  },
  {
    topic: 'Voicings',
    title: 'Drop 2, Drop 3, Drop 2&4',
    body: 'Take a close-position seventh chord (all four notes inside one octave) and "drop" the 2nd note from the top down an octave: that\'s Drop 2 — the most-used chord voicing in jazz guitar and big-band writing, because the spread fits four adjacent strings and four horns equally well. Drop the 3rd from the top instead for Drop 3 (wider, with that low-string anchor), or both the 2nd and 4th for Drop 2&4 (the widest spread).\n\nEvery inversion of every drop voicing is its own grip — four inversions × three drop types per chord quality is a lifetime of vocabulary, all generated from one close stack.',
    examples: ['Cmaj7', 'C7', 'Cm7', 'Cm7b5'],
  },
  {
    topic: 'Voicings',
    title: 'Rootless voicings (Bill Evans A & B)',
    body: 'Leave the root to the bass player. The A form stacks 3–5–7–9 (on dominants, the 13 usually replaces the 5); the B form starts from the 7th: 7–9–3–13. The magic is in alternating them: play A on Dm7, B on G7, A on Cmaj7, and your hand barely moves while the harmony walks a full ii–V–I.\n\nThese are the default left-hand voicings of post-1950s jazz piano — Bill Evans, Wynton Kelly, and everyone after.',
    examples: ['Dm9', 'G13', 'Cmaj9'],
  },
  {
    topic: 'Voicings',
    title: 'Kenny Barron and quartal voicings',
    body: 'The Kenny Barron voicing is a specific, named minor-11 sound: two stacks of perfect 5ths a half step apart — left hand 1–5–9, right hand ♭3–♭7–11. Huge, modern, and completely stable.\n\nQuartal harmony stacks 4ths instead of 3rds. The "So What" voicing (three 4ths capped by a major 3rd) is Bill Evans\'s answer on the Miles Davis record of the same name, and the backbone of McCoy Tyner\'s modal sound. Quartal voicings blur which chord is "really" being played — that ambiguity is the point in modal music.',
    examples: ['Cm11', 'Dm7'],
  },
  {
    topic: 'Voicings',
    title: 'Upper structure triads',
    body: 'On a dominant chord, keep the tritone (3 + ♭7) in the left hand and place a simple major triad from another key on top: II gives 9–♯11–13 (Lydian dominant), VI gives 13–♭9, ♭VI gives ♯9–♭13 (the full altered sound).\n\nWhy it works: your ear organizes the top notes as one familiar shape (a major triad), so a very dissonant total sonority still sounds intentional and clear. It\'s the fastest route to sophisticated dominant colors.',
    examples: ['C7', 'C7b9', 'C7alt'],
  },
  {
    topic: 'Harmony',
    title: 'The ii–V–I',
    body: 'The engine of tonal jazz: subdominant → dominant → tonic, with roots falling in 5ths and guide tones sliding by half step. In major: iim7–V7–Imaj7. In minor: iiø7–V7♭9–im6 (or im maj7).\n\nMost standards are chains of ii–Vs — some resolving, some not, some borrowed from other keys for a few bars. Learn to spot a ii–V faster than you can name the individual chords and tunes stop looking like lists of chords and start looking like sentences.',
    examples: ['Dm7', 'G7', 'Cmaj7', 'Dm7b5', 'G7b9', 'Cm6'],
  },
  {
    topic: 'Harmony',
    title: 'Secondary dominants',
    body: 'Any diatonic chord can be preceded by its own personal V7 — a dominant built a 5th above it. In C major: A7 (V7/ii) pulls to Dm7, D7 (V7/V) pulls to G7, C7 (V7/IV) pulls to F. Each one borrows a little chromatic urgency from outside the key, then resolves right back into it.\n\nThey\'re the most common "wrong" notes in standards: that F♯ in a D7 inside C major isn\'t an error — it\'s a leading tone on loan.',
    examples: ['A7', 'Dm7', 'D7', 'G7'],
  },
  {
    topic: 'Harmony',
    title: 'Tritone substitution',
    body: 'Two dominant 7th chords a tritone apart share the same guide tones with roles swapped — the 3rd of G7 (B) is the ♭7 of D♭7 (C♭), and vice versa. So either can resolve to C: G7 falls a 5th, D♭7 slides down a half step.\n\nThe sub turns circle-of-5ths root motion into chromatic root motion (Dm7–D♭7–Cmaj7) and automatically supplies altered tensions: the ♭9 and ♭13 of G7 are the 5 and 9 of D♭7 — the same notes, renamed.',
    examples: ['G7', 'Db7', 'Cmaj7'],
  },
  {
    topic: 'Harmony',
    title: 'Modal interchange (borrowed chords)',
    body: 'A major key can borrow any chord from its parallel minor: ivm7, ♭VImaj7, ♭VII7, ♭IIImaj7, iiø7. The borrowed chord keeps its minor-world color while the key stays major — instant bittersweetness.\n\nThe most idiomatic borrow is the backdoor cadence: ivm7–♭VII7 resolving up a whole step to Imaj7 (Fm7–B♭7–Cmaj7 in C). It lands on the tonic as convincingly as a V7, from the opposite direction.',
    examples: ['Fm7', 'Bb7', 'Cmaj7'],
  },
  {
    topic: 'Harmony',
    title: 'Diminished symmetry',
    body: 'A dim7 chord divides the octave into four equal minor 3rds, so it has no true root — C°7, E♭°7, G♭°7 and A°7 are the same four notes. Consequences: any dim7 can resolve up a half step to four different chords, and every dim7 lives inside four different 7♭9 chords (roots a major 3rd below each tone).\n\nThat four-way ambiguity is why diminished chords are jazz\'s favorite pivot: enter from one key, leave into another, and the chord itself never has to change.',
    examples: ['Cdim7', 'B7b9', 'D7b9', 'F7b9', 'Ab7b9'],
  },
  {
    topic: 'Barry Harris',
    title: 'The 6th-diminished scale',
    body: 'Barry Harris\'s central object: take a 6th chord (say C6: C–E–G–A) and the dim7 built on its major 7th (B°7: B–D–F–A♭), and interleave them into one 8-note scale: C D E F G A♭(♯5) A B. Harmonize every step and the chords alternate — C6, B°7, C6, B°7 — all the way up.\n\nThe result: you can move stepwise, constantly, and never leave the tonic. The 6th chord is home; the dim7 is the tension between homes; "movement" and "staying" stop being opposites. The Barry Harris tab builds this scale, spelled and playable, for any chord you enter.',
    examples: ['C6', 'Cm6', 'Cmaj7'],
  },
  {
    topic: 'Barry Harris',
    title: 'Applying it: major, minor, m7, ø',
    body: 'Barry\'s translations, all exact note-for-note identities:\n\nmaj7 → play the 6 chord; the major 7th becomes a passing tone in the scale. m7 → it IS the relative major 6 (Cm7 = E♭6), so use the E♭ major 6th-diminished scale. m7♭5 → it IS a m6 chord from its ♭3 (Am7♭5 = Cm6), so use the C minor 6th-diminished scale. Minor tonic → the m6 chord, with its own minor 6th-diminished scale.\n\nOne system, four chord families — this economy is why players spend years inside it.',
    examples: ['Cmaj7', 'Cm7', 'Am7b5', 'Cm6'],
  },
  {
    topic: 'Barry Harris',
    title: 'Dominants and the ♭9 family',
    body: 'Two dominant moves. First: a m6 chord on the dominant\'s 5th IS that dominant\'s 9/13 sound (Dm6 over G = G9), so the D minor 6th-diminished scale harmonizes G7. Second: add the ♭9 to any dominant and its top four notes form a dim7 — which, by symmetry, belongs equally to four dominants a minor 3rd apart (G7♭9, B♭7♭9, D♭7♭9, E7♭9 share one diminished core).\n\nAny member of the family can substitute for any other. This is Barry\'s "the diminished is the mother of the dominants" — and it\'s why a single dim7 shape under your fingers is secretly four V7 chords.',
    examples: ['G7', 'Dm6', 'G7b9', 'Bb7', 'Db7', 'E7'],
  },
  {
    topic: 'Barry Harris',
    title: 'Voice leading inside the scale of chords',
    body: 'Because the harmonized 6th-diminished scale alternates tonic and diminished, moving every voice up one scale step turns C6 into B°7 into C6/E into B°7/D and so on — four-voice, contrary-to-nothing, perfectly smooth motion. This is how Barry players harmonize melodies: put the tune on top, and each melody note picks its position in the scale of chords; the inner voices follow automatically.\n\nStudy approach with the Barry tab: step through the 8 positions in order and listen for the alternation; then jump between distant positions and hear how it still sounds like one chord — motion inside stillness.',
    examples: ['C6', 'F6', 'Bb6'],
  },
];
```

## Source: `drills.js`

```js
// Chord practice drills: question generation, spaced-repetition grading, and
// adaptive session building. Pure functions (no DOM, no IndexedDB) so the
// scheduling and generation logic is unit-testable in Node — the Practice tab
// in the chords view is just a renderer over this.
//
// A "concept" is the unit of skill tracking — a chord-quality family or a
// voicing type, not an individual chord (there are too many single chords to
// track meaningfully; "shaky on drop 3" is the useful signal). Each concept
// generates endless concrete questions by picking random roots.

import { parseNote } from './notes.js';
import { buildChord, getQuality } from './chords.js';
import { voicingsFor } from './voicings.js';

const ROOTS = ['C', 'D♭', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

// Qualities drilled for spelling ("spell Cmaj7") and naming ("what chord is
// C–E–G–B?"). A curated working set, not all 35 — the workhorse qualities.
const DRILL_QUALITIES = ['maj7', '6', 'm7', 'm6', '7', 'm7b5', 'dim7', '9', 'maj9', '7b9', '13', 'mMaj7'];

// Voicing-recognition drills: which qualities can host each voicing type
// (mirrors voicingsFor's own applicability rules).
const CORE_QUALITIES = ['maj7', '7', 'm7', 'm6', '6', 'm7b5'];
const VOICING_DRILLS = [
  { id: 'close', qualities: CORE_QUALITIES },
  { id: 'drop2', qualities: CORE_QUALITIES },
  { id: 'drop3', qualities: CORE_QUALITIES },
  { id: 'drop24', qualities: CORE_QUALITIES },
  { id: 'shellA', qualities: ['maj7', '7', 'm7'] },
  { id: 'shellB', qualities: ['maj7', '7', 'm7'] },
  { id: 'rootlessA', qualities: ['maj7', '7', 'm7'] },
  { id: 'rootlessB', qualities: ['maj7', '7', 'm7'] },
  { id: 'kennyBarron', qualities: ['m7'] },
  { id: 'soWhat', qualities: ['m7'] },
];

// All trackable concepts, in a deliberate teaching order (new concepts are
// introduced in this order, easiest families first).
export const CONCEPTS = [
  ...DRILL_QUALITIES.map((q) => ({
    id: `spell:${q}`, kind: 'spell',
    label: `Spell ${getQuality(q).display || 'maj'} chords`,
  })),
  ...DRILL_QUALITIES.map((q) => ({
    id: `name:${q}`, kind: 'name',
    label: `Recognize ${getQuality(q).display || 'maj'} chords`,
  })),
  ...VOICING_DRILLS.map((v) => ({
    id: `voicing:${v.id}`, kind: 'voicing',
    label: `Identify: ${voicingLabel(v.id)}`,
  })),
];

const CONCEPT_BY_ID = new Map(CONCEPTS.map((c) => [c.id, c]));
export function conceptById(id) { return CONCEPT_BY_ID.get(id) || null; }

function voicingLabel(vid) {
  return {
    close: 'close position', drop2: 'Drop 2', drop3: 'Drop 3', drop24: 'Drop 2 & 4',
    shellA: 'shell (7th low)', shellB: 'shell (3rd low)',
    rootlessA: 'Rootless A', rootlessB: 'Rootless B',
    kennyBarron: 'Kenny Barron', soWhat: '"So What"',
  }[vid] || vid;
}

// Root-position stack (48 = C3 base) for spell/name questions — deliberately
// the plain chord tones, not a stylized voicing, so the question tests the
// chord itself.
function rootStack(chord) {
  return chord.tones.map((t) => ({ midi: 48 + chord.root.pc + t.semitones, name: t.name, label: t.label }));
}

// Build a concrete question for a concept. `rootName` override keeps tests
// deterministic; otherwise a random root is chosen.
export function makeQuestion(conceptId, rootName) {
  const concept = conceptById(conceptId);
  if (!concept) return null;
  const [kind, key] = conceptId.split(':');
  const root = parseNote(rootName || ROOTS[Math.floor(Math.random() * ROOTS.length)]);

  if (kind === 'spell') {
    const chord = buildChord(root, key);
    const notes = rootStack(chord);
    return {
      conceptId, kind,
      prompt: `Spell ${chord.symbol}`,
      noteNames: null, preNotes: null,
      answer: chord.tones.map((t) => t.name).join(' – '),
      answerDetail: `(${chord.tones.map((t) => t.label).join(' – ')}) — ${chord.quality.label}`,
      postNotes: notes,
      play: notes.map((n) => n.midi),
    };
  }

  if (kind === 'name') {
    const chord = buildChord(root, key);
    const notes = rootStack(chord);
    return {
      conceptId, kind,
      prompt: 'Name this chord:',
      noteNames: chord.tones.map((t) => t.name),
      preNotes: notes,
      answer: chord.symbol,
      answerDetail: chord.quality.label,
      postNotes: null,
      play: notes.map((n) => n.midi),
    };
  }

  // kind === 'voicing'
  const drill = VOICING_DRILLS.find((v) => v.id === key);
  const quality = drill.qualities[Math.floor(Math.random() * drill.qualities.length)];
  const chord = buildChord(root, quality);
  const v = voicingsFor(chord).find((x) => x.id === key)
    || voicingsFor(chord).find((x) => x.id === 'close');
  return {
    conceptId, kind,
    prompt: `Which voicing of ${chord.symbol} is this?`,
    noteNames: null,
    preNotes: v.notes, // diagram only — tone labels would give it away
    answer: v.name,
    answerDetail: v.group,
    postNotes: null,
    play: v.notes.map((n) => n.midi),
  };
}

// --- Spaced repetition (same semantics as the Languages module's cards:
// again → back to 1 day; good → interval ×2; easy → interval ×3) plus
// accuracy counters, which is what "good at / not good at" is computed from.

function addDaysTo(dateStr, days) {
  const d = new Date(dateStr + 'T00:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function gradeSkill(skill, grade, today) {
  const prevInterval = skill.interval || 1;
  const interval = grade === 'again' ? 1 : grade === 'good' ? prevInterval * 2 : prevInterval * 3;
  return {
    interval,
    dueDate: addDaysTo(today, interval),
    attempts: (skill.attempts || 0) + 1,
    correct: (skill.correct || 0) + (grade === 'again' ? 0 : 1),
  };
}

export function accuracy(skill) {
  return skill && skill.attempts ? skill.correct / skill.attempts : null;
}

// --- Adaptive session building: due reviews first, then weak spots, then a
// few new concepts, topped up with strong-review so a session is never empty.

export function buildSession(skillsById, { size = 14, today, newLimit = 3 } = {}) {
  const chosen = [];
  const used = new Set();
  const take = (id) => { if (!used.has(id) && chosen.length < size) { used.add(id); chosen.push(id); } };
  const skillOf = (id) => skillsById[id];
  const acc = (id) => accuracy(skillOf(id)) ?? 1;

  // 1. Due for review (SRS), weakest first.
  CONCEPTS
    .filter((c) => skillOf(c.id) && skillOf(c.id).dueDate <= today)
    .sort((a, b) => acc(a.id) - acc(b.id) || (skillOf(a.id).dueDate < skillOf(b.id).dueDate ? -1 : 1))
    .forEach((c) => take(c.id));

  // 2. Known weak spots, even if not due yet.
  CONCEPTS
    .filter((c) => { const s = skillOf(c.id); return s && s.attempts >= 2 && accuracy(s) < 0.75; })
    .sort((a, b) => acc(a.id) - acc(b.id))
    .forEach((c) => take(c.id));

  // 3. Introduce a few unseen concepts, in teaching order.
  let introduced = 0;
  for (const c of CONCEPTS) {
    if (chosen.length >= size || introduced >= newLimit) break;
    if (!skillOf(c.id) && !used.has(c.id)) { take(c.id); introduced++; }
  }

  // 4. Fill with strong-review (best-known first — light confidence reps).
  CONCEPTS
    .filter((c) => skillOf(c.id) && !used.has(c.id))
    .sort((a, b) => acc(b.id) - acc(a.id))
    .forEach((c) => take(c.id));

  return chosen;
}

// --- "Good at / not good at" summary for the Practice tab header + the
// printable sheet. Only concepts with a real sample size (3+ attempts) count.

export function skillSummary(skillsById) {
  const rated = CONCEPTS
    .map((c) => ({ ...c, skill: skillsById[c.id] }))
    .filter((c) => c.skill && c.skill.attempts >= 3)
    .map((c) => ({ id: c.id, label: c.label, acc: accuracy(c.skill), attempts: c.skill.attempts }));
  const totals = Object.values(skillsById).reduce(
    (t, s) => ({ attempts: t.attempts + (s.attempts || 0), correct: t.correct + (s.correct || 0) }),
    { attempts: 0, correct: 0 }
  );
  return {
    weak: rated.filter((r) => r.acc < 0.75).sort((a, b) => a.acc - b.acc).slice(0, 6),
    strong: rated.filter((r) => r.acc >= 0.9).sort((a, b) => b.acc - a.acc).slice(0, 6),
    totals,
  };
}
```

## Source: `synth.js`

```js
// Web Audio chord synth for the harmony module. Fully synthesized — no
// samples, no network, no licensing baggage. One shared AudioContext, a
// per-preset parameter set (two oscillators, optional FM for the electric-
// piano "tine", ADSR, 3-band EQ, volume), everything user-adjustable.

export const PARAM_DEFS = [
  { key: 'osc1Type', label: 'Osc 1 wave', type: 'select', options: ['sine', 'triangle', 'sawtooth', 'square'] },
  { key: 'osc2Type', label: 'Osc 2 wave', type: 'select', options: ['sine', 'triangle', 'sawtooth', 'square'] },
  { key: 'osc2Level', label: 'Osc 2 level', type: 'range', min: 0, max: 1, step: 0.05 },
  { key: 'osc2Coarse', label: 'Osc 2 pitch (semitones)', type: 'range', min: -12, max: 24, step: 1 },
  { key: 'osc2Fine', label: 'Osc 2 detune (cents)', type: 'range', min: -25, max: 25, step: 1 },
  { key: 'fmRatio', label: 'FM ratio', type: 'range', min: 0, max: 14, step: 0.5 },
  { key: 'fmDepth', label: 'FM depth', type: 'range', min: 0, max: 400, step: 5 },
  { key: 'attack', label: 'Attack (s)', type: 'range', min: 0.001, max: 1, step: 0.001 },
  { key: 'decay', label: 'Decay (s)', type: 'range', min: 0.05, max: 4, step: 0.05 },
  { key: 'sustain', label: 'Sustain level', type: 'range', min: 0, max: 1, step: 0.05 },
  { key: 'release', label: 'Release (s)', type: 'range', min: 0.05, max: 2, step: 0.05 },
  { key: 'eqLow', label: 'EQ low (dB)', type: 'range', min: -12, max: 12, step: 1 },
  { key: 'eqMid', label: 'EQ mid (dB)', type: 'range', min: -12, max: 12, step: 1 },
  { key: 'eqHigh', label: 'EQ high (dB)', type: 'range', min: -12, max: 12, step: 1 },
  { key: 'volume', label: 'Volume', type: 'range', min: 0, max: 1, step: 0.05 },
];

export const FACTORY_PRESETS = {
  piano: {
    name: 'Piano', osc1Type: 'triangle', osc2Type: 'sine', osc2Level: 0.35, osc2Coarse: 12, osc2Fine: 0,
    fmRatio: 0, fmDepth: 0, attack: 0.004, decay: 1.6, sustain: 0, release: 0.3,
    eqLow: 0, eqMid: 0, eqHigh: 2, volume: 0.7,
  },
  rhodes: {
    name: 'Rhodes EP', osc1Type: 'sine', osc2Type: 'sine', osc2Level: 0.2, osc2Coarse: 12, osc2Fine: 0,
    fmRatio: 14, fmDepth: 60, attack: 0.003, decay: 2.2, sustain: 0.12, release: 0.4,
    eqLow: 3, eqMid: -1, eqHigh: 1, volume: 0.7,
  },
  organ: {
    name: 'Organ', osc1Type: 'sine', osc2Type: 'sine', osc2Level: 0.5, osc2Coarse: 12, osc2Fine: 0,
    fmRatio: 0, fmDepth: 0, attack: 0.01, decay: 0.1, sustain: 0.9, release: 0.12,
    eqLow: 1, eqMid: 2, eqHigh: -2, volume: 0.55,
  },
  pad: {
    name: 'Pad', osc1Type: 'sawtooth', osc2Type: 'sawtooth', osc2Level: 0.6, osc2Coarse: 0, osc2Fine: 8,
    fmRatio: 0, fmDepth: 0, attack: 0.35, decay: 1, sustain: 0.8, release: 0.9,
    eqLow: 0, eqMid: -2, eqHigh: -4, volume: 0.5,
  },
};

let ctx = null;
let eqChainIn = null;
let eq = null;

function ensureContext() {
  if (ctx) return ctx;
  ctx = new (window.AudioContext || window.webkitAudioContext)();
  const low = ctx.createBiquadFilter();
  low.type = 'lowshelf';
  low.frequency.value = 220;
  const mid = ctx.createBiquadFilter();
  mid.type = 'peaking';
  mid.frequency.value = 1000;
  mid.Q.value = 0.8;
  const high = ctx.createBiquadFilter();
  high.type = 'highshelf';
  high.frequency.value = 3600;
  low.connect(mid);
  mid.connect(high);
  high.connect(ctx.destination);
  eqChainIn = low;
  eq = { low, mid, high };
  return ctx;
}

function midiToFreq(midi) {
  return 440 * Math.pow(2, (midi - 69) / 12);
}

function playVoice(midi, params, when, chordGainNode) {
  const f = midiToFreq(midi);
  const stopAt = when + Math.min(2.4, params.attack + params.decay + 0.8) + params.release;

  const env = ctx.createGain();
  env.gain.setValueAtTime(0, when);
  env.gain.linearRampToValueAtTime(1, when + params.attack);
  env.gain.setTargetAtTime(params.sustain, when + params.attack, Math.max(0.05, params.decay / 3));
  env.gain.setTargetAtTime(0, stopAt - params.release, Math.max(0.03, params.release / 3));
  env.connect(chordGainNode);

  const osc1 = ctx.createOscillator();
  osc1.type = params.osc1Type;
  osc1.frequency.value = f;
  osc1.connect(env);

  const osc2 = ctx.createOscillator();
  osc2.type = params.osc2Type;
  osc2.frequency.value = f * Math.pow(2, params.osc2Coarse / 12);
  osc2.detune.value = params.osc2Fine;
  const osc2Gain = ctx.createGain();
  osc2Gain.gain.value = params.osc2Level;
  osc2.connect(osc2Gain);
  osc2Gain.connect(env);

  const nodes = [osc1, osc2];
  if (params.fmRatio > 0 && params.fmDepth > 0) {
    const mod = ctx.createOscillator();
    mod.frequency.value = f * params.fmRatio;
    const modGain = ctx.createGain();
    // The tine "ping": modulation depth spikes at the attack, then decays.
    modGain.gain.setValueAtTime(params.fmDepth, when);
    modGain.gain.setTargetAtTime(params.fmDepth * 0.1, when + 0.01, 0.15);
    mod.connect(modGain);
    modGain.connect(osc1.frequency);
    nodes.push(mod);
  }

  for (const n of nodes) {
    n.start(when);
    n.stop(stopAt + 0.1);
  }
}

// Play a set of MIDI notes as a chord (slight low-to-high roll).
// `atOffset` (seconds from now) lets playSequence schedule several chords.
export function playChord(midiNotes, params, atOffset = 0) {
  const c = ensureContext();
  if (c.state === 'suspended') c.resume();
  eq.low.gain.value = params.eqLow;
  eq.mid.gain.value = params.eqMid;
  eq.high.gain.value = params.eqHigh;

  const chordGain = c.createGain();
  chordGain.gain.value = params.volume / Math.sqrt(Math.max(1, midiNotes.length));
  chordGain.connect(eqChainIn);

  const now = c.currentTime + 0.02 + atOffset;
  [...midiNotes].sort((a, b) => a - b).forEach((midi, i) => {
    playVoice(midi, params, now + i * 0.022, chordGain);
  });
}

// Play several chords one after another — used by the harmony map's "hear
// the move" / "hear the trail". A fixed gap, not a tempo: this stays a study
// tool (audition a motion), not a sequencer.
export function playSequence(midiChords, params, gapSeconds = 0.9) {
  midiChords.forEach((midis, i) => playChord(midis, params, i * gapSeconds));
}
```

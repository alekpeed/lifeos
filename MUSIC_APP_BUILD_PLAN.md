# Music App — Build Plan (native Kotlin / Compose Multiplatform)

**What this is:** the build-ready brief that ties the music app's two existing
documents together and turns them into a concrete plan for a **native
Kotlin + Compose Multiplatform** app. It's the single entry point — start here.

**Read order:**
1. **This file** — the target, the stack, the port plan, milestones.
2. **`MUSIC_APP_DESIGN_SPEC.md`** — the product/design source of truth: what the
   app *is*, information architecture, every screen, MVP→full scope, and the
   feature ideas. Nothing here overrides it; this file is *how to build it native*.
3. **`CHORDS_APP_HANDOFF.md`** — the complete engine: all ten source files
   (`notes → chords → voicings → harmony → barry → graph → voicelead → lessons →
   drills → synth`) in dependency order, pasted in full. This is what you port.

**Why the music section "disappeared":** the Chords module was lifted *out* of
Life OS to keep that app's scope tight. The engine files are gone from `js/` —
they survive only inside `CHORDS_APP_HANDOFF.md`. Nothing was lost; it just
lives in these three docs now instead of in the running app.

---

## 0. Decisions locked (2026-07-21)

These were open in the design spec (§11); they're now settled and drive this plan:

| Decision | Choice |
|---|---|
| **Platform** | **Native Kotlin + Compose Multiplatform** (Android + desktop), mirroring the Life OS native rebuild. |
| **Extra features (beyond the harmony core)** | **Riff Pads + musical typing**, **Practice + Lessons + Log**, **Cloud sync + accounts**. |
| **Left out for now** | "Life as Music" data sonification (no data source in a standalone music app — stays in Life OS). |

Still yours to decide (don't block the build on them): the **name**,
**instrument emphasis** (piano-first / guitar-first / co-equal), and
**distribution** (free / paid / store vs. sideload). See §9.

---

## 1. The shape of the app

One Compose Multiplatform module, exactly the topology the Life OS `native/`
build already proves out — so the toolchain, CI, and platform patterns are known-good:

```
music-app/
  composeApp/
    src/
      commonMain/kotlin/…/
        theory/     ← the 9 theory files, ported to pure Kotlin (no UI, no audio, no IO)
        audio/      ← the synth: pure-Kotlin DSP (commonMain) + an expect audio sink
        data/       ← @Serializable stores + local persistence + Supabase sync
        ui/         ← Compose screens (Dictionary, Harmony Map, Practice, Pads, …)
        platform/   ← expect declarations (audio out, storage, key input)
      androidMain/…  ← actual: AudioTrack, storage, hardware-key handling
      desktopMain/…  ← actual: javax.sound SourceDataLine, storage, key handling
    build.gradle.kts
  .github/workflows/build.yml   ← two jobs: Android APK + desktop package (copy Life OS's)
```

**The load-bearing idea from the design spec (§2.6):** *the engine is sacred and
portable; the UI is disposable.* Port the ten engine files faithfully and test
them; then build a fresh Compose UI on top. The old Life-OS UI file does **not**
port — it's DOM/IndexedDB-coupled (the handoff says as much) and is reference only.

---

## 2. Porting the theory engine (the 9 files)

All nine theory files are pure functions over plain data — the ideal thing to
port. Do it in **dependency order** (each depends only on the ones above it):
`notes → chords → voicings → harmony → barry → graph → voicelead`, plus the two
leaf files `lessons` (pure content) and `drills` (depends on notes/chords/voicings).

**Data-shape mapping (JS object → Kotlin):**

| JS shape | Kotlin |
|---|---|
| `{ letter, acc, pc }` (a note) | `data class Note(val letter: Char, val acc: Int, val pc: Int)` |
| `{ root, quality, tones, symbol }` (a chord) | `data class Chord(val root: Note, val quality: Quality, val tones: List<Tone>, val symbol: String)` |
| quality table entries | `data class Quality(val id: String, val display: String, val label: String, val cat: String, val intervals: List<Pair<Int,Int>>, val aliases: List<String>)` + a `val QUALITIES: List<Quality>` |
| `{ id, name, group, description, notes:[{midi,name,label}] }` (a voicing) | `data class Voicing(val id: String, val name: String, val group: String, val description: String, val notes: List<VoiceNote>)` |
| MIDI note numbers | `Int` (unchanged) |
| module of `export function`s | a Kotlin `object` (e.g. `object Notes { fun parseNote(…)… }`) or top-level funcs in a file |

**Worked example — `notes.js` → `Notes.kt`** (the base layer; port this first so
the idiom is set):

```kotlin
package …theory

data class Note(val letter: Char, val acc: Int, val pc: Int)

object Notes {
    val LETTERS = listOf('C', 'D', 'E', 'F', 'G', 'A', 'B')
    val NATURAL_PC = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val ACC_GLYPH = mapOf(-2 to "𝄫", -1 to "♭", 0 to "", 1 to "♯", 2 to "𝄪")

    // 14 semitones over degree 9 is a plain 9, 13 is a ♭9 — natural size per degree.
    private val DEGREE_NATURAL = mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 7 to 11, 9 to 14, 11 to 17, 13 to 21)

    fun parseNote(input: String): Note? {
        val m = Regex("^([A-Ga-g])(𝄫|bb|♭♭|𝄪|##|♯♯|b|♭|#|♯)?$").find(input.trim()) ?: return null
        val letter = m.groupValues[1].uppercase()[0]
        val acc = when (m.groupValues[2]) {
            "" -> 0
            "bb", "♭♭", "𝄫" -> -2
            "##", "♯♯", "𝄪" -> 2
            "b", "♭" -> -1
            else -> 1
        }
        return Note(letter, acc, ((NATURAL_PC[letter]!! + acc) % 12 + 12) % 12)
    }

    fun noteName(n: Note): String = n.letter + ACC_GLYPH[n.acc]

    // Spell the note `semitones` above `root` that functions as scale degree `degree`.
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

Two things this example nails that the rest of the port must keep:
- **Astral glyphs as `\uXXXX` escapes**, never literal (double-sharp 𝄪 /
  double-flat 𝄫 are surrogate pairs — same lesson the Life OS native build
  learned the hard way with EPUB markers).
- **JS `%` on negatives differs from Kotlin `%`** — JS `-1 % 12` is `-1` and the
  original relies on that, so port modulo carefully; use `((x % 12) + 12) % 12`
  wherever a pitch class must stay 0–11.

**Test the port, don't eyeball it.** Each theory file is deterministic, so write
commonTest cases that pin known outputs *before* trusting a screen on top:
`parseChord("F#m7b5")` tones spell F♯–A–C–E; `spellInterval` of a ♯9 on C is D♯
not E♭; `barryAnalysis` of a m7 names the right 6-dim scale. A ported engine with
a green test suite is the one milestone that lets everything above it move fast.

---

## 3. Porting the synth (the one genuinely new bit)

`synth.js` is Web Audio — a node graph (oscillators → gain envelopes → biquad EQ
→ destination) that doesn't exist off the browser. This is the only part that
isn't a mechanical port; it's real (but bounded) DSP work.

**Approach: a tiny software synth that renders PCM, played through a platform sink.**

- **Pure-Kotlin DSP in commonMain.** Keep `PARAM_DEFS` and the four
  `FACTORY_PRESETS` verbatim (they're just data — see the handoff's synth
  section). Reimplement `playVoice` as a per-sample renderer: two oscillators
  (`sine/triangle/sawtooth/square` by direct waveform math), optional FM on
  osc1's frequency, an ADSR gain envelope, summed across voices, then a 3-band
  biquad EQ (lowshelf 220 Hz / peaking 1 kHz Q0.8 / highshelf 3.6 kHz — the exact
  values in the source). Output a `FloatArray`/`ShortArray` buffer at 44.1 kHz.
- **`expect` audio sink, `actual` per platform:**
  - Android → `android.media.AudioTrack` (streaming mode, PCM 16-bit).
  - Desktop → `javax.sound.sampled.SourceDataLine`.
  ```kotlin
  // commonMain
  expect object AudioOut { fun play(samples: ShortArray, sampleRate: Int) }
  ```
- **Keep the three audition affordances** from the design spec (§7): *hear the
  chord* (roll low→high ~22 ms apart — already in `playChord`), *hear the move*
  (two-chord min-motion via `voiceLeadMidis`), *hear the trail* (a sequence with
  a fixed ~0.9 s gap — `playSequence`). Fixed gaps, **no transport/tempo** — it's
  a study tool, not a sequencer.
- **One shared output**, started on first user gesture (Android/desktop audio
  focus), fed by whatever Sound preset is selected. Presets are the same object
  shape as `FACTORY_PRESETS` plus user-saved ones (see the data model).

Scope note: this is a *monophonic-per-oscillator additive synth with an envelope
and EQ* — a few hundred lines of Kotlin, testable by rendering a buffer and
asserting it isn't silent / has the right fundamental. It is the single biggest
net-new engineering item versus the JS original; everything else is a port.

---

## 4. Data model (Kotlin, local-first + cloud)

Six persisted stores. Model each as a `@Serializable` data class; persist
locally first (a simple key/value or JSON-per-store, exactly like Life OS
native's `Storage`), then layer cloud sync on top (§5.3). The first four come
straight from the design spec / handoff; `SynthPresets` is new for standalone;
`RiffPacks` powers the pads feature.

```kotlin
@Serializable data class ChordProgression(val id: String, val name: String, val chords: List<SavedChord>)   // Harmony-Map trails
@Serializable data class ChordSkill(val id: String, val interval: Int, val dueDate: String, val attempts: Int, val correct: Int) // SRS state, one per concept
@Serializable data class ChordDrillLog(val id: String, val date: String, val conceptId: String, val correct: Boolean)            // append-only drill history
@Serializable data class ChordPracticeLog(val id: String, val date: String, val durationMin: Int, val notes: String)             // freeform instrument log
@Serializable data class SynthPreset(val id: String, val name: String, val params: Map<String, Double>)                          // user Sound presets
@Serializable data class RiffPack(val id: String, val name: String, val pads: List<Pad>)                                         // shareable pad collections (see §5.1)
```

Key modeling rules carried from the design spec: **`ChordSkill` is keyed by
concept id** (`"spell:maj7"`, `"voicing:drop2"` — from `drills.js`'s CONCEPTS),
and drill accuracy (`ChordDrillLog`) is deliberately **separate** from the
instrument-practice `ChordPracticeLog` — two different signals.

---

## 5. The three chosen extra features

### 5.1 Riff Pads + musical typing

Full design is in **`MUSIC_APP_DESIGN_SPEC.md` §10** — read it; this is the
native-specific delta. A pad is *not* "one key = one note"; it's a **programmable
musical event**: a voicing (one or many notes) with an articulation, and pads can
chain into a timed riff. It rides entirely on the ported synth — which already
does rolls (`playChord` spreads low→high) and timed sequences (`playSequence`) —
so this is mostly a **pad editor + a key listener**, near-zero new engine work.

- **Data model (from spec §10), as Kotlin:**
  ```kotlin
  @Serializable data class Pad(
      val key: String, val name: String, val preset: String? = null,
      val events: List<PadEvent>,
  )
  @Serializable data class PadEvent(
      val midis: List<Int>,                 // a voicing — absolute pitch, ONE OR MANY notes
      val voicingRef: VoicingRef? = null,   // optional { symbol, voicingId } from the library
      val articulation: String,             // "block" | "roll-up" | "roll-down" | "strum"
      val rollMs: Int, val atMs: Int, val durMs: Int, val velocity: Double? = null,
  )
  ```
- **Store absolute MIDI, not key positions** (spec §10) — the octave *is* the
  data. If you later want to *recognize* a riff regardless of octave/key
  (a guessing game, matching a pack), match on intervals/pitch-classes instead.
- **Native key input:**
  - **Desktop** — `Modifier.onKeyEvent` / `onPreviewKeyEvent` gives real hardware
    keys; implement the GarageBand/Ableton QWERTY→piano layout (`A`–`K` white,
    `W E T Y U` black, `Z`/`X` octave shift) with a **chord mode** where a key
    fires a whole voicing.
  - **Android** — an on-screen pad grid is primary (touch); the same
    `onKeyEvent` path handles a paired Bluetooth/USB keyboard when present.
- **Packs are shareable:** export/import a `RiffPack` as JSON (share sheet / file),
  and/or publish to the cloud table (§5.3) so packs sync across your devices.
  This generalizes the Harmony-Map **trails** (saved chord walks) to full voiced,
  articulated, timed phrases. Lives as a persistent bar across surfaces or its
  own "Pads" screen.

### 5.2 Practice + Lessons + Log

These are already **V1** in the design spec (§4.7–4.9) — confirmed in scope.

- **Practice** — port `drills.js` (question generation for spell / name /
  voicing-recognition; `gradeSkill` SRS "same again / good / easy" → interval
  scheme; adaptive session: due reviews → weak spots → a few new concepts).
  Compose UI: a card per question, three grade buttons, a streak counter. The SRS
  state is the `ChordSkill` store; every answer appends a `ChordDrillLog`.
- **Lessons** — port `lessons.js` (18 written lessons, Foundations → Voicings →
  Harmony → Barry Harris) as content; render prose with **clickable chord-symbol
  chips** that deep-link into the Dictionary loaded with that chord (reading and
  doing are the same gesture).
- **Log** — the freeform instrument-practice log (`ChordPracticeLog`): date,
  duration, what you worked on. Deliberately separate from drill accuracy.

### 5.3 Cloud sync + accounts

Use **exactly the pattern Life OS native already runs** — it's proven, and you
can lift the auth code almost verbatim:

- **Auth:** Supabase email+password over GoTrue (`SupabaseAuth` in the Life OS
  native tree is a drop-in reference: sign-up / sign-in / token refresh, tokens
  in local storage). No OAuth redirect, so it works cleanly on Android + desktop.
  Cross-device sync = sign into the same account on each device → same
  `auth.uid()` → same rows.
- **Storage:** one Postgres table (its own Supabase project for the music app),
  e.g. `music_records(user_id uuid default auth.uid(), store text, record_id
  text, data jsonb, updated_at, deleted_at, primary key(user_id, store,
  record_id))`, with **RLS** `user_id = auth.uid()` — the same generic
  per-key-blob shape Life OS uses. The six stores in §4 map onto `store`
  namespaces (`progressions`, `skills`, `drilllogs`, `practicelogs`, `presets`,
  `riffpacks`).
- **Local-first stays the default:** everything works signed-out on-device;
  sign-in is purely additive (push local rows up, pull remote down, last-write-
  wins on `updated_at`). Sharing a RiffPack with someone else is a later,
  separate concern (a public/shared row or an exported file) — the account sync
  above is device-to-device for *your own* data.

---

## 6. Milestones (each independently shippable / testable)

| # | Milestone | Contents | Done when |
|---|---|---|---|
| **M0** | Repo + CI | CMP module skeleton (copy Life OS `native/` topology); two-job CI (Android APK + desktop package). | An empty app builds green on both jobs. |
| **M1** | Engine port + tests | Port the 9 theory files in dependency order; commonTest pins known outputs. | Test suite green; `parseChord`/voicings/barry match the JS. |
| **M2** | Audio + MVP | Synth (§3); Dictionary (parse → tones → voicings → guitar shapes → audio); Calculator; Sound presets. | You can look up a chord and *hear* every voicing on device. |
| **M3** | V1 distinctive | Harmony Map (curated graph, key/adventurous toggles, trails) + voice-leading panel; Barry Harris; Atlas. | You can walk relationships and audition moves/trails. |
| **M4** | Practice | Drills + SRS + streak; Lessons (18, clickable chips); Log. | A drill session grades and schedules; lessons deep-link. |
| **M5** | Riff Pads | Musical typing + programmable pads + articulations; packs export/import. | A key fires a voiced, articulated pad through the synth. |
| **M6** | Cloud sync + accounts | Supabase auth + `music_records` + RLS; local-first sync of all six stores. | Same account on two devices shares trails/practice/packs. |

MVP = **M0–M2** (already a better chord dictionary than most: correct spelling +
every voicing + real audio). V1 = **through M4**. Extras = **M5–M6**.

---

## 7. Build & verify

Mirror the Life OS native workflow so there are no surprises:

- **CI is the compiler.** Copy `.github/workflows/build-native.yml` (two jobs:
  `assembleDebug` for the Android APK, `packageDistributionForCurrentOS` for the
  desktop app). Push → read the Actions result → grab the APK artifact for device
  testing. Nothing is "done" until it runs on a real device.
- **commonMain gotchas that already cost Life OS a round-trip each** — inherit the
  fixes: `StringBuilder.appendRange` (not the 3-arg JVM `append`); fully-qualify
  `androidx.compose.material3.AlertDialog`; `import
  androidx.compose.foundation.lazy.items`; write astral glyphs (𝄪 𝄫) as `\uXXXX`
  escapes, not literals; split large source writes into small edits.
- **Platform pattern:** `expect` in `commonMain/platform/` (AudioOut, Storage,
  key-input helpers) with `android` + `desktop` actuals — same as Life OS.

---

## 8. What NOT to port

- The old Life-OS Chords **UI file** (`js/interfaces/default/views/chords.js`) —
  DOM/IndexedDB-coupled, reference only. Build a fresh Compose UI.
- **"Life as Music" sonification** — excluded (no data source standalone).
- Life OS's `el()`/`svgEl()`/`makeKnob()` helpers — Compose replaces them.

---

## 9. Still yours to decide (don't block on these)

1. **Name** — deliberately unset. Pick when you want.
2. **Instrument emphasis** — piano-first, guitar-first, or co-equal (the engine
   supports both; it's a UI emphasis choice).
3. **Distribution** — free / paid / Play Store vs. sideload. The offline,
   no-licensing, no-server-required core keeps every option open.

---

*Companion docs: `MUSIC_APP_DESIGN_SPEC.md` (product/UX) · `CHORDS_APP_HANDOFF.md`
(the ten engine source files). This file is the native build plan and the index
to both.*

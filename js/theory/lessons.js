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

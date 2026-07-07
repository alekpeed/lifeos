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
export function playChord(midiNotes, params) {
  const c = ensureContext();
  if (c.state === 'suspended') c.resume();
  eq.low.gain.value = params.eqLow;
  eq.mid.gain.value = params.eqMid;
  eq.high.gain.value = params.eqHigh;

  const chordGain = c.createGain();
  chordGain.gain.value = params.volume / Math.sqrt(Math.max(1, midiNotes.length));
  chordGain.connect(eqChainIn);

  const now = c.currentTime + 0.02;
  [...midiNotes].sort((a, b) => a - b).forEach((midi, i) => {
    playVoice(midi, params, now + i * 0.022, chordGain);
  });
}

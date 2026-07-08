// A rotary knob bound to one numeric parameter — the desktop control style
// for the synth's Sound tab (mobile/tablet use detented sliders instead).
// Pure DOM/SVG, no dependency. Turned by dragging vertically (up = more), the
// standard virtual-hardware convention since tracing a circle with a pointer
// is fiddly; also supports mouse wheel, arrow keys when focused, and
// double-click to snap back to the preset's default.
//
// Colors come from CSS custom properties set by the active panel skin
// (--knob-face / --knob-ring / --knob-indicator / --knob-tick), so one knob
// component reskins into Rhodes / Wurlitzer / Nord / Moog / Equator looks
// without any JS change.

import { el } from './dom.js';

const NS = 'http://www.w3.org/2000/svg';
function svg(tag, attrs = {}, children = []) {
  const node = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  for (const c of children) node.append(c);
  return node;
}

const ARC = 270;           // degrees of travel
const START = -135;        // angle at min (0 = pointing up)
const FULL_TRAVEL_PX = 180; // vertical drag distance for the whole range

function decimalsOf(step) {
  const s = String(step);
  return s.includes('.') ? s.split('.')[1].length : 0;
}

// opts: { min, max, step, value, defaultValue, label, onInput(v) }
export function makeKnob(opts) {
  const { min, max, step = 1, label, onInput } = opts;
  const decimals = decimalsOf(step);
  let value = opts.value;

  const quantize = (v) => {
    v = Math.min(max, Math.max(min, v));
    v = Math.round((v - min) / step) * step + min;
    return Number(v.toFixed(decimals + 2));
  };
  const angleFor = (v) => START + ((v - min) / (max - min)) * ARC;
  const fmt = (v) => (decimals ? v.toFixed(decimals) : String(v));

  const indicatorGroup = svg('g', { class: 'mer-knob-ind-group' }, [
    svg('line', { x1: 32, y1: 32, x2: 32, y2: 13, class: 'mer-knob-indicator', 'stroke-width': 3, 'stroke-linecap': 'round' }),
  ]);
  const dial = svg('svg', { viewBox: '0 0 64 64', class: 'mer-knob', role: 'slider', tabindex: '0',
    'aria-label': label, 'aria-valuemin': min, 'aria-valuemax': max });
  // ticks
  for (let i = 0; i <= 10; i++) {
    const g = svg('g', { transform: `rotate(${START + (i / 10) * ARC} 32 32)` }, [
      svg('line', { x1: 32, y1: 3, x2: 32, y2: 7, class: 'mer-knob-tick' }),
    ]);
    dial.append(g);
  }
  dial.append(
    svg('circle', { cx: 32, cy: 32, r: 22, class: 'mer-knob-ring' }),
    svg('circle', { cx: 32, cy: 32, r: 18, class: 'mer-knob-face' }),
    indicatorGroup,
  );

  const readout = el('div', { class: 'mer-knob-readout' });

  function render() {
    indicatorGroup.setAttribute('transform', `rotate(${angleFor(value)} 32 32)`);
    readout.textContent = fmt(value);
    dial.setAttribute('aria-valuenow', value);
  }
  function set(v, fire = true) {
    const q = quantize(v);
    if (q === value) { if (fire) onInput(q); return; }
    value = q;
    render();
    if (fire) onInput(q);
  }

  // --- drag (pointer events: mouse + touch unified) ---
  let dragStartY = 0, dragStartVal = 0, dragging = false;
  dial.addEventListener('pointerdown', (e) => {
    dragging = true; dragStartY = e.clientY; dragStartVal = value;
    dial.setPointerCapture(e.pointerId);
    dial.classList.add('is-dragging');
    e.preventDefault();
  });
  dial.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dy = dragStartY - e.clientY; // up = positive
    set(dragStartVal + (dy / FULL_TRAVEL_PX) * (max - min));
  });
  const endDrag = () => { dragging = false; dial.classList.remove('is-dragging'); };
  dial.addEventListener('pointerup', endDrag);
  dial.addEventListener('pointercancel', endDrag);

  dial.addEventListener('wheel', (e) => { e.preventDefault(); set(value + (e.deltaY < 0 ? step : -step)); }, { passive: false });
  dial.addEventListener('dblclick', () => { if (opts.defaultValue != null) set(opts.defaultValue); });
  dial.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowUp' || e.key === 'ArrowRight') { e.preventDefault(); set(value + step); }
    else if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') { e.preventDefault(); set(value - step); }
    else if (e.key === 'Home') { e.preventDefault(); set(min); }
    else if (e.key === 'End') { e.preventDefault(); set(max); }
  });

  render();
  return el('div', { class: 'mer-knob-wrap' }, [dial, el('div', { class: 'mer-knob-label', text: label }), readout]);
}

// Round a raw step up to a "nice" value, so detented sliders click through a
// sensible number of stops with clean numbers instead of the param's very
// fine native step (which felt glassy-smooth).
const NICE = [0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 50];
export function niceStep(range, minStep) {
  const target = range / 28; // aim for ~28 detents
  const pick = NICE.find((n) => n >= target) ?? minStep;
  return Math.max(minStep, pick);
}

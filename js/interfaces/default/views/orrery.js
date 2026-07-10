// The Orrery — the dashboard reimagined as a solar system. An ALTERNATE
// view alongside the Dashboard (which stays the default landing page), not
// a replacement — agreed before building.
//
// Every visual property encodes a real signal; nothing orbits decoratively:
//   • Orbit radius = neglect — days since the area's data was last touched
//     (the same signal Entropy reads). Fresh areas hug the sun; neglected
//     ones drift toward the outer dark. An area with no data yet parks on
//     the outermost ring, dimmed.
//   • Planet size   = how much lives there (record count, log-scaled).
//   • Orbital speed = this week's activity — busy areas visibly move.
//   • Pulsing ring  = something is overdue (tasks past due, unpaid bills
//     past due).
//   • Click a planet → jump to that module.
//
// Animation is a single requestAnimationFrame loop that stops itself the
// moment its SVG leaves the document (the render contract clears the canvas
// on navigation, so `svg.isConnected` is the unmount signal — no lifecycle
// hook needed). Users with prefers-reduced-motion get a static, evenly
// scattered layout with no loop at all.

import { el, todayStr } from '../dom.js';

const SVG_NS = 'http://www.w3.org/2000/svg';

function svgEl(tag, attrs = {}, children = []) {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  for (const child of children) node.append(child);
  return node;
}

function daysSince(dateStr) {
  if (!dateStr) return null;
  const ms = new Date(todayStr() + 'T00:00:00') - new Date(dateStr.slice(0, 10) + 'T00:00:00');
  return Math.max(0, Math.round(ms / 86400000));
}

function isoDaysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

// One entry per life area: which store feeds it, which field dates a record,
// and (optionally) what counts as "overdue" there. Colors are a fixed
// palette — hue identifies the area across sessions, it doesn't encode data.
const AREAS = [
  { module: 'tasks', label: 'Tasks', color: '#d9a441', list: (d) => d.Tasks.list(), dateOf: (r) => r.updatedAt, overdue: (r) => r.dueDate && r.dueDate < todayStr() && r.status !== 'done' },
  { module: 'finance', label: 'Finance', color: '#4ba3a0', list: (d) => d.Bills.list(), dateOf: (r) => r.updatedAt, overdue: (r) => r.dueDate && r.dueDate < todayStr() && !r.paid },
  { module: 'habits', label: 'Habits', color: '#bd6577', list: (d) => d.HabitLogs.list(), dateOf: (r) => r.date },
  { module: 'health', label: 'Health', color: '#6f8ecb', list: (d) => d.HealthLogs.list(), dateOf: (r) => r.date },
  { module: 'books', label: 'Books', color: '#a06ec9', list: (d) => d.Books.list(), dateOf: (r) => r.updatedAt },
  { module: 'places', label: 'Places', color: '#67a7ba', list: (d) => d.Places.list(), dateOf: (r) => r.updatedAt },
  { module: 'recipes', label: 'Recipes', color: '#c98a5e', list: (d) => d.Recipes.list(), dateOf: (r) => r.updatedAt },
  { module: 'contacts', label: 'Contacts', color: '#8b909a', list: (d) => d.Contacts.list(), dateOf: (r) => r.updatedAt },
];

async function computePlanets(ctx) {
  const weekAgo = isoDaysAgo(7);
  return Promise.all(AREAS.map(async (area) => {
    const records = await area.list(ctx.data);
    const dates = records.map(area.dateOf).filter(Boolean);
    const lastTouch = dates.length ? dates.reduce((a, b) => (b > a ? b : a)) : null;
    const days = daysSince(lastTouch);
    const activity = dates.filter((d) => d.slice(0, 10) >= weekAgo).length;
    const overdueCount = area.overdue ? records.filter(area.overdue).length : 0;
    return {
      ...area,
      count: records.length,
      days,
      activity,
      overdueCount,
      hasData: days !== null,
      // Orbit: fresh = close (70), 30+ days stale = outer dark (230).
      // No data at all parks on the outermost ring.
      orbit: days === null ? 230 : 70 + (Math.min(days, 30) / 30) * 160,
      // Size: log-scaled record count, so 10 vs 1000 reads as big-vs-bigger
      // rather than invisible-vs-screen-filling.
      size: days === null ? 4 : Math.min(17, 6 + Math.log2(1 + records.length) * 2.1),
      // Speed (rad/s): this week's activity. Idle areas still creep so the
      // system reads as alive, active ones visibly lap.
      speed: 0.05 + (Math.min(activity, 20) / 20) * 0.4,
    };
  }));
}

function orrerySvg(planets, ctx, reducedMotion) {
  const W = 640, H = 520, CX = W / 2, CY = H / 2;
  const svg = svgEl('svg', { viewBox: `0 0 ${W} ${H}`, class: 'mer-orrery-svg' });

  // Orbit rings (one per distinct radius, faint).
  for (const p of planets) {
    svg.append(svgEl('circle', { cx: CX, cy: CY, r: p.orbit, class: 'mer-orrery-ring' }));
  }

  // The sun: today, you, the center everything else is measured from.
  svg.append(svgEl('circle', { cx: CX, cy: CY, r: 16, class: 'mer-orrery-sun' }));
  svg.append(svgEl('text', { x: CX, y: CY + 32, 'text-anchor': 'middle', class: 'mer-orrery-sun-label' }, ['today']));

  // Golden-angle starting positions: scattered, deterministic, never bunched.
  const GOLDEN = Math.PI * (3 - Math.sqrt(5));
  const bodies = planets.map((p, i) => {
    const g = svgEl('g', {
      class: p.hasData ? 'mer-orrery-planet' : 'mer-orrery-planet is-empty',
      tabindex: '0', role: 'button',
    });
    if (p.overdueCount > 0) {
      g.append(svgEl('circle', { r: p.size + 5, class: 'mer-orrery-alert', fill: 'none' }));
    }
    g.append(svgEl('circle', { r: p.size, fill: p.color, class: 'mer-orrery-body' }));
    g.append(svgEl('text', { y: -(p.size + 7), 'text-anchor': 'middle', class: 'mer-orrery-label' }, [p.label]));
    const go = () => ctx.navigate(p.module);
    g.addEventListener('click', go);
    g.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); } });
    svg.append(g);
    return { p, g, angle: i * GOLDEN };
  });

  const place = () => {
    for (const b of bodies) {
      const x = CX + b.p.orbit * Math.cos(b.angle);
      const y = CY + b.p.orbit * Math.sin(b.angle);
      b.g.setAttribute('transform', `translate(${x.toFixed(2)} ${y.toFixed(2)})`);
    }
  };
  place();

  if (!reducedMotion) {
    let last = performance.now();
    const tick = (now) => {
      if (!svg.isConnected) return; // view unmounted — stop the loop
      const dt = Math.min(0.1, (now - last) / 1000); // clamp tab-resume jumps
      last = now;
      for (const b of bodies) b.angle += b.p.speed * dt;
      place();
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  return svg;
}

function legend(planets, ctx) {
  const area = el('div', { class: 'mer-task-list-area' });
  const sorted = [...planets].sort((a, b) => (b.days ?? Infinity) === (a.days ?? Infinity) ? 0 : ((b.days ?? Infinity) - (a.days ?? Infinity)));
  for (const p of sorted) {
    const row = el('div', { class: 'mer-task-row' }, [
      el('span', { class: 'mer-orrery-swatch', style: `background:${p.color}` }),
      el('span', { class: 'mer-task-title', text: p.label }),
      el('div', { class: 'mer-task-meta' }, [
        p.overdueCount ? el('span', { class: 'mer-chip is-overdue', text: `${p.overdueCount} overdue` }) : null,
        el('span', { class: 'mer-chip', text: p.hasData ? `touched ${p.days === 0 ? 'today' : `${p.days}d ago`}` : 'no data' }),
        el('span', { class: 'mer-chip', text: `${p.activity} this week` }),
      ].filter(Boolean)),
    ]);
    row.addEventListener('click', () => ctx.navigate(p.module));
    area.append(row);
  }
  return area;
}

export async function renderOrrery(canvas, ctx) {
  const planets = await computePlanets(ctx);
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

  canvas.append(el('h1', { text: 'The Orrery' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Your life as a solar system: close-in planets were touched recently, far ones are drifting; size is how much lives there; speed is this week’s activity; a pulsing ring means something’s overdue. Click a planet to fly there.' }));
  canvas.append(orrerySvg(planets, ctx, reducedMotion));
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Flight log' }));
  canvas.append(legend(planets, ctx));
}

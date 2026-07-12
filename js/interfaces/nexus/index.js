// "NEXUS" — a dense, card-based mobile dashboard interface, built from a
// user-supplied mockup (see the project's NEXUS-mockup coordinate-precision
// exercise). Visually distinct from Equator (calm rail-and-canvas) and
// Vespera (spatial station) — this one is a control-panel-style home screen
// plus a persistent slim header everywhere else, closer to a phone widget
// board than either.
//
// Every widget below is backed by a real ctx.data call. Where the mockup
// showed something the app has no data for (a live heart-rate reading, a
// "focus %" score, an hourly weather forecast, a timed daily-schedule
// agenda, a "send wishes" action, a distinct "notes" concept), that element
// was either dropped or re-mapped to the closest real feature rather than
// invented — see the commit that introduced this file for the full mapping
// rationale. The radial wheel's geometry is fresh math (six equal donut
// sectors), not the mockup's hand-traced petals: those were art-specific to
// a raster image this app doesn't ship.

import { registerInterface } from '../registry.js';
import { VIEWS } from '../view-library.js';

function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value == null || value === false) continue;
    if (key === 'text') node.textContent = value;
    else if (key === 'class') node.className = value;
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value);
  }
  for (const child of [].concat(children)) {
    if (child) node.append(child);
  }
  return node;
}

const SVG_NS = 'http://www.w3.org/2000/svg';
function svgEl(tag, attrs = {}) {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value == null) continue;
    node.setAttribute(key, value);
  }
  return node;
}

let ctx = null;
let els = null; // { root }
let unsubscribe = null;
let currentRoute = null;
let renderPending = false;
let renderChain = Promise.resolve();
let clockTimer = null;

function moduleLabel(id) {
  return ctx.modules.find((m) => m.id === id)?.label || id;
}

// --- Header (persistent on every screen) ---

function timeParts() {
  const now = new Date();
  const time = now.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  const date = now.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).toUpperCase();
  return { time, date };
}

function greeting() {
  const h = new Date().getHours();
  if (h < 5) return 'Still up, Operator';
  if (h < 12) return 'Good morning, Operator';
  if (h < 18) return 'Good afternoon, Operator';
  return 'Good evening, Operator';
}

async function renderHeader(dueSoonCount) {
  const { time, date } = timeParts();
  const timeEl = el('span', { class: 'nxs-clock-time', text: time });
  clockTimer = setInterval(() => { timeEl.textContent = timeParts().time; }, 30_000);

  return el('header', { class: 'nxs-header' }, [
    el('button', {
      type: 'button', class: 'nxs-brand', onclick: () => ctx.navigate('dashboard'),
    }, [
      el('span', { class: 'nxs-brand-mark', text: '◈' }),
      el('span', { class: 'nxs-brand-text' }, [
        el('span', { class: 'nxs-brand-name', text: 'NEXUS' }),
        el('span', { class: 'nxs-brand-sub', text: 'LIFE OS' }),
      ]),
    ]),
    el('div', { class: 'nxs-clock' }, [timeEl, el('span', { class: 'nxs-clock-date', text: date })]),
    el('div', { class: 'nxs-header-actions' }, [
      el('button', {
        type: 'button', class: 'nxs-icon-btn', title: 'Due soon', 'aria-label': `${dueSoonCount} due soon`,
        onclick: () => ctx.navigate('tasks'),
      }, [
        el('span', { text: '🔔' }),
        dueSoonCount > 0 ? el('span', { class: 'nxs-badge', text: String(dueSoonCount) }) : null,
      ]),
      el('button', {
        type: 'button', class: 'nxs-icon-btn', title: 'Settings', 'aria-label': 'Settings',
        onclick: () => ctx.navigate('settings'),
      }, [el('span', { text: '⚙' })]),
    ]),
  ]);
}

// --- Home screen widgets ---

function card(label, body, { wide = false } = {}) {
  return el('section', { class: wide ? 'nxs-card nxs-card--wide' : 'nxs-card' }, [
    el('h2', { class: 'nxs-card-label', text: label }),
    body,
  ]);
}

function greetingBar() {
  return el('section', { class: 'nxs-greet' }, [
    el('div', { class: 'nxs-greet-text' }, [
      el('p', { class: 'nxs-greet-title', text: greeting().toUpperCase() }),
      el('p', { class: 'nxs-greet-sub', text: "Let's make today count." }),
    ]),
    el('div', { class: 'nxs-greet-actions' }, [
      el('button', { type: 'button', class: 'nxs-pill', onclick: () => ctx.navigate('search') }, [
        el('span', { text: '🔍' }), el('span', { text: 'Search' }),
      ]),
      el('button', {
        type: 'button', class: 'nxs-pill', onclick: () => ctx.navigate('ideas'),
        title: 'Capture by typing or voice (Ideas has a mic button)',
      }, [
        el('span', { text: '🎙' }), el('span', { text: 'Capture' }),
      ]),
      el('button', { type: 'button', class: 'nxs-pill', onclick: () => ctx.navigate('assistant') }, [
        el('span', { text: '✨' }), el('span', { text: 'AI Assist' }),
      ]),
    ]),
  ]);
}

function dueSoonCard(feed) {
  if (!feed.length) {
    return card('Due Soon', el('p', { class: 'nxs-muted', text: 'Nothing due in the next 7 days.' }));
  }
  const list = el('ul', { class: 'nxs-list' });
  for (const item of feed.slice(0, 5)) {
    list.append(el('li', { class: item.overdue ? 'nxs-list-row is-overdue' : 'nxs-list-row' }, [
      el('span', { class: 'nxs-list-tag', text: item.module }),
      el('span', { class: 'nxs-list-title', text: item.title || '(untitled)' }),
    ]));
  }
  return card(`Due Soon (${feed.length})`, list);
}

function weatherCard(weather) {
  if (!weather) {
    return card('Weather', el('p', { class: 'nxs-muted', text: 'No location set — add one in Settings.' }));
  }
  const { icon, label } = ctx.data.describeWeatherCode(weather.code);
  return card('Weather', el('div', { class: 'nxs-weather' }, [
    el('span', { class: 'nxs-weather-temp', text: `${icon} ${Math.round(weather.tempF)}°F` }),
    el('span', { class: 'nxs-muted', text: label }),
    weather.highF != null
      ? el('span', { class: 'nxs-muted', text: `H:${Math.round(weather.highF)}° L:${Math.round(weather.lowF)}°` })
      : null,
  ]));
}

function onThisDayCard(items) {
  if (!items.length) return null;
  const list = el('ul', { class: 'nxs-list' });
  for (const item of items.slice(0, 4)) {
    list.append(el('li', { class: 'nxs-list-row' }, [
      el('span', { class: 'nxs-list-tag', text: item.kind }),
      el('span', { class: 'nxs-list-title', text: item.title || '(untitled)' }),
      el('span', { class: 'nxs-list-meta', text: item.year }),
    ]));
  }
  return card('On This Day', list);
}

function surpriseCard(surprise, rerender) {
  const button = el('button', {
    type: 'button', class: 'nxs-action-btn',
    onclick: async () => { surprise.value = await ctx.data.getSurpriseMe(); rerender(); },
    text: '🎲 Surprise me',
  });
  if (surprise.value === undefined) return card('Not sure what to do?', button);
  if (!surprise.value) {
    return card('Not sure what to do?', el('div', {}, [
      button,
      el('p', { class: 'nxs-muted', text: 'Nothing in the queue yet.' }),
    ]));
  }
  return card('Not sure what to do?', el('div', {}, [
    button,
    el('p', { class: 'nxs-muted' }, [
      el('span', { class: 'nxs-list-tag', text: surprise.value.kind }),
      el('span', { text: ` ${surprise.value.title}` }),
    ]),
    el('button', { type: 'button', class: 'nxs-link-btn', text: 'Go there →', onclick: () => ctx.navigate(surprise.value.module) }),
  ]));
}

function taskProgressCard(tasks) {
  const counts = { done: 0, in_progress: 0, not_started: 0, waiting: 0 };
  for (const t of tasks) counts[t.status] = (counts[t.status] || 0) + 1;
  const total = tasks.length;
  const pct = total ? Math.round((counts.done / total) * 100) : 0;
  return card('Task Progress', el('div', { class: 'nxs-progress' }, [
    el('div', { class: 'nxs-progress-ring', style: `--pct: ${pct}` }, [el('span', { text: `${pct}%` })]),
    el('div', { class: 'nxs-progress-breakdown' }, [
      el('span', { text: `${counts.done} done` }),
      el('span', { text: `${counts.in_progress} in progress` }),
      el('span', { text: `${counts.not_started} not started` }),
    ]),
  ]));
}

function habitsCard(habits, allLogs) {
  const today = new Date().toISOString().slice(0, 10);
  const doneToday = habits.filter((h) => allLogs.some((l) => l.habitId === h.id && l.date === today)).length;
  const body = el('div', {}, [
    el('p', { class: 'nxs-muted', text: habits.length ? `${doneToday} / ${habits.length} done today` : 'No habits yet.' }),
    el('button', { type: 'button', class: 'nxs-link-btn', text: 'View Habit Tracker →', onclick: () => ctx.navigate('habits') }),
  ]);
  return card('Habits', body);
}

function quickActionsRow() {
  const actions = [
    { icon: '✅', label: 'Add Task', module: 'tasks' },
    { icon: '💡', label: 'Capture Idea', module: 'ideas' },
    { icon: '📎', label: 'Scan Doc', module: 'documents' },
    { icon: '🔗', label: 'Save Link', module: 'links' },
  ];
  const row = el('div', { class: 'nxs-quick-actions' });
  for (const a of actions) {
    row.append(el('button', {
      type: 'button', class: 'nxs-quick-btn', onclick: () => ctx.navigate(a.module),
    }, [el('span', { text: a.icon }), el('span', { text: a.label })]));
  }
  return row;
}

async function reviewsDueCard() {
  const due = await ctx.data.getDueResurfaceItems();
  if (!due.length) {
    return card('Reviews Due', el('p', { class: 'nxs-muted', text: 'Nothing due for recall right now.' }));
  }
  const nodes = (await Promise.all(due.slice(0, 4).map((item) => ctx.data.resolveGraphNode(item.key))))
    .filter((n) => n.exists);
  const list = el('ul', { class: 'nxs-list' });
  for (const n of nodes) {
    list.append(el('li', { class: 'nxs-list-row' }, [el('span', { class: 'nxs-list-title', text: n.title })]));
  }
  return card(`Reviews Due (${due.length})`, el('div', {}, [
    list,
    el('button', { type: 'button', class: 'nxs-link-btn', text: 'Review all →', onclick: () => ctx.navigate('recall') }),
  ]), { wide: true });
}

async function pacingInsightsCard() {
  const [assignments, allLogs] = await Promise.all([ctx.data.Assignments.list(), ctx.data.AssignmentProgressLogs.list()]);
  const logsByAssignment = new Map();
  for (const log of allLogs) {
    if (!logsByAssignment.has(log.assignmentId)) logsByAssignment.set(log.assignmentId, []);
    logsByAssignment.get(log.assignmentId).push(log);
  }
  let ahead = 0, onTrack = 0, behind = 0;
  for (const a of assignments) {
    if (a.status === 'done') continue;
    const status = ctx.data.pacingStatusFor(a, logsByAssignment.get(a.id) || []);
    if (!status) continue;
    if (status.gap < 0) ahead++;
    else if (status.gap === 0) onTrack++;
    else behind++;
  }
  const tracked = ahead + onTrack + behind;
  const body = !tracked
    ? el('p', { class: 'nxs-muted', text: 'No assignments with a pacing checkpoint due yet.' })
    : el('div', { class: 'nxs-pacing-counts' }, [
      el('span', { class: 'nxs-pacing-count is-ahead', text: `${ahead} ahead` }),
      el('span', { class: 'nxs-pacing-count is-ontrack', text: `${onTrack} on track` }),
      el('span', { class: 'nxs-pacing-count is-behind', text: `${behind} behind` }),
    ]);
  return card('Pacing Insights', el('div', {}, [
    body,
    el('button', { type: 'button', class: 'nxs-link-btn', text: 'View insights →', onclick: () => ctx.navigate('education') }),
  ]));
}

function ideaSparkCard(ideas) {
  const open = ideas.filter((i) => !i.archived);
  const body = !open.length
    ? el('p', { class: 'nxs-muted', text: 'No ideas captured yet.' })
    : el('p', { class: 'nxs-muted', text: open[Math.floor(Math.random() * open.length)].text || '(untitled)' });
  return card('Idea Spark', el('div', {}, [
    body,
    el('button', { type: 'button', class: 'nxs-link-btn', text: 'More ideas →', onclick: () => ctx.navigate('ideas') }),
  ]));
}

// --- Radial wheel: six equal donut sectors, fresh geometry (not the
// mockup's hand-traced petals -- those were tied to a raster image this app
// doesn't ship). NEXUS CORE at the center is deliberately non-interactive:
// the reference trace this interface was built from has no hub hotspot,
// only the six petals.
const WHEEL = [
  { module: 'dashboard', label: 'Today', icon: '🌅', start: -30, end: 30 },
  { module: 'tasks', label: 'Tasks', icon: '✅', start: 30, end: 90 },
  { module: 'settings', label: 'Settings', icon: '⚙', start: 90, end: 150 },
  { module: 'search', label: 'Search', icon: '🔍', start: 150, end: 210 },
  { module: 'places', label: 'Places', icon: '📍', start: 210, end: 270 },
  { module: 'ideas', label: 'Ideas', icon: '💡', start: 270, end: 330 },
];
const WHEEL_R_INNER = 34;
const WHEEL_R_OUTER = 96;

function polar(cx, cy, r, deg) {
  const rad = (deg * Math.PI) / 180;
  return [cx + r * Math.sin(rad), cy - r * Math.cos(rad)];
}

function sectorPath(cx, cy, rInner, rOuter, startDeg, endDeg) {
  const [ox0, oy0] = polar(cx, cy, rOuter, startDeg);
  const [ox1, oy1] = polar(cx, cy, rOuter, endDeg);
  const [ix1, iy1] = polar(cx, cy, rInner, endDeg);
  const [ix0, iy0] = polar(cx, cy, rInner, startDeg);
  return `M ${ox0} ${oy0} A ${rOuter} ${rOuter} 0 0 1 ${ox1} ${oy1} L ${ix1} ${iy1} A ${rInner} ${rInner} 0 0 0 ${ix0} ${iy0} Z`;
}

function renderWheel() {
  const cx = 100, cy = 100;
  const svg = svgEl('svg', { class: 'nxs-wheel', viewBox: '0 0 200 200' });
  for (const sector of WHEEL) {
    const mid = (sector.start + sector.end) / 2;
    const [labelX, labelY] = polar(cx, cy, (WHEEL_R_INNER + WHEEL_R_OUTER) / 2, mid);
    const g = svgEl('g', { class: 'nxs-wheel-sector' });
    const path = svgEl('path', {
      d: sectorPath(cx, cy, WHEEL_R_INNER, WHEEL_R_OUTER, sector.start, sector.end),
      class: 'nxs-wheel-hit',
    });
    const iconText = svgEl('text', { x: labelX, y: labelY - 6, class: 'nxs-wheel-icon', 'text-anchor': 'middle' });
    iconText.textContent = sector.icon;
    const labelText = svgEl('text', { x: labelX, y: labelY + 12, class: 'nxs-wheel-label', 'text-anchor': 'middle' });
    labelText.textContent = sector.label;
    g.append(path, iconText, labelText);
    g.addEventListener('click', () => ctx.navigate(sector.module));
    svg.append(g);
  }
  const centerCircle = svgEl('circle', { cx, cy, r: WHEEL_R_INNER, class: 'nxs-wheel-core' });
  const centerText = svgEl('text', { x: cx, y: cy, class: 'nxs-wheel-core-label', 'text-anchor': 'middle' });
  centerText.textContent = 'NEXUS CORE';
  const wrap = el('div', { class: 'nxs-wheel-wrap' });
  svg.append(centerCircle, centerText);
  wrap.append(svg);
  return wrap;
}

// --- Home (dashboard route) ---

async function renderHome(canvas, rerender) {
  const [billDueSoonDays, documentExpiryDays] = await Promise.all([
    ctx.data.Settings.get('billDueSoonDays'),
    ctx.data.Settings.get('documentExpiryDays'),
  ]);
  const [onThisDay, weather, dueSoonFeed, tasks, habits, habitLogs, ideas] = await Promise.all([
    ctx.data.getOnThisDay(),
    ctx.data.getWeather(),
    ctx.data.getDueSoonFeed(7, billDueSoonDays, documentExpiryDays),
    ctx.data.Tasks.list(),
    ctx.data.Habits.list(),
    ctx.data.HabitLogs.list(),
    ctx.data.Ideas.list(),
  ]);

  canvas.append(await renderHeader(dueSoonFeed.length));
  canvas.append(greetingBar());

  const grid = el('div', { class: 'nxs-grid' });
  grid.append(dueSoonCard(dueSoonFeed));
  grid.append(weatherCard(weather));
  const otd = onThisDayCard(onThisDay);
  if (otd) grid.append(otd);

  const surprise = { value: undefined };
  grid.append(surpriseCard(surprise, rerender));
  grid.append(taskProgressCard(tasks));
  grid.append(habitsCard(habits, habitLogs));
  canvas.append(grid);

  canvas.append(quickActionsRow());

  const grid2 = el('div', { class: 'nxs-grid' });
  grid2.append(await reviewsDueCard());
  grid2.append(await pacingInsightsCard());
  grid2.append(ideaSparkCard(ideas));
  canvas.append(grid2);

  canvas.append(renderWheel());
}

// --- Any other module (persistent slim header + hosted view) ---

async function renderModuleScreen(canvas, route) {
  canvas.append(await renderHeader(0));
  const bar = el('div', { class: 'nxs-subbar' }, [
    el('button', { type: 'button', class: 'nxs-back', text: '◂ Home', onclick: () => ctx.navigate('dashboard') }),
    el('span', { class: 'nxs-subbar-title', text: moduleLabel(route.module) }),
  ]);
  canvas.append(bar);

  const well = el('div', { class: 'nxs-content', 'data-interface': 'default' });
  const inner = el('main', { class: 'mer-canvas' });
  well.append(inner);
  canvas.append(well);

  const view = VIEWS[route.module];
  if (!view) {
    inner.append(el('h1', { text: moduleLabel(route.module) }), el('p', { class: 'mer-muted', text: 'This space is still under construction.' }));
    return;
  }
  await view(inner, ctx, () => requestRender(route));
}

// --- Interface contract ---

async function doRender(route) {
  if (clockTimer) { clearInterval(clockTimer); clockTimer = null; }
  els.root.innerHTML = '';
  const canvas = el('div', { class: 'nxs-canvas' });
  els.root.append(canvas);
  if (route.module === 'dashboard') {
    await renderHome(canvas, () => requestRender(route));
  } else {
    await renderModuleScreen(canvas, route);
  }
}

function requestRender(route) {
  currentRoute = route;
  renderChain = renderChain.then(() => doRender(route)).catch((err) => {
    console.error('nexus: render failed', err);
  });
  return renderChain;
}

async function renderRoute(route) {
  await requestRender(route);
  window.scrollTo({ top: 0 });
}

function scheduleRerender() {
  if (renderPending || !currentRoute) return;
  renderPending = true;
  queueMicrotask(() => {
    renderPending = false;
    requestRender(currentRoute);
  });
}

registerInterface({
  id: 'nexus',
  name: 'NEXUS',
  description: 'Dense card-based mobile dashboard with a radial quick-nav wheel.',
  stylesheet: 'js/interfaces/nexus/style.css',

  async mount(container, context) {
    ctx = context;
    const root = el('div', { class: 'nxs-root' });
    container.append(root);
    els = { root };
    unsubscribe = ctx.events.on('*', ({ topic }) => {
      if (topic !== 'settings') scheduleRerender();
    });
  },

  renderRoute,

  unmount() {
    if (clockTimer) { clearInterval(clockTimer); clockTimer = null; }
    unsubscribe?.();
    unsubscribe = null;
    ctx = null;
    els = null;
    currentRoute = null;
  },
});

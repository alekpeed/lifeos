// "NEXUS" — the user's own Figma mockup, shipped close to literally: the
// mockup image (img/hub.png) is the actual home-screen background art, with
// real, precisely-positioned click regions mapped onto it — the same
// technique Vespera uses for its hub (img/hub.png + hotspot polygons), and
// the direct continuation of this project's NEXUS-mockup coordinate-
// precision exercise (nexustest.png / nexusred.png at the repo root): those
// hotspot coordinates, extracted from a GPT-drawn trace and corrected twice
// against direct user feedback, are reused here verbatim.
//
// The image is decorative chrome, not a live data surface — its baked-in
// numbers (a specific temperature, specific fake task titles) are mockup
// content, not real app data. Every hotspot is real, wired to ctx.navigate()
// (or, for "Surprise me", the actual getSurpriseMe() call). Where the
// mockup's hotspot had no matching real destination (a live heart-rate
// reading, an hourly forecast, a "send wishes" action, a timed daily
// schedule), it's mapped to the closest real module rather than left dead —
// see the HOTSPOTS table below for the exact mapping.
//
// Away from the dashboard route, there's no mockup art for other screens,
// so those get a plain slim header (brand/clock/back) hosting the real
// module views from the shared view library — same pattern as Vespera's
// Space screens.

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

// --- Slim header (module screens only -- the dashboard route's header is
// baked into the mockup art itself) ---

function timeParts() {
  const now = new Date();
  const time = now.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  const date = now.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).toUpperCase();
  return { time, date };
}

function renderHeader() {
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
    el('button', {
      type: 'button', class: 'nxs-icon-btn', title: 'Settings', 'aria-label': 'Settings',
      onclick: () => ctx.navigate('settings'),
    }, [el('span', { text: '⚙' })]),
  ]);
}

// --- Dashboard: the mockup image itself, with real click regions ---

const IMG_W = 941;
const IMG_H = 1672;

// Rectangular hotspots: left/top/width/height as % of the 941x1672 mockup,
// pixel-detected from the GPT-drawn reference trace (nexusred.png) and
// corrected against direct user review (see this file's history / the
// nexus-test scratch work this session). `module` is the real destination;
// `special` marks the one hotspot ("Surprise me") that's a real action, not
// pure navigation.
const HOTSPOTS = [
  { id: 'header.logo', left: 2.444, top: 0.957, width: 28.905, height: 6.639, module: 'dashboard' },
  // No live heart-rate data exists anywhere in the app (see Health module) --
  // routed to Health as the closest real destination rather than left dead.
  { id: 'header.heartrate', left: 73.007, top: 2.572, width: 7.439, height: 4.127, module: 'health' },
  { id: 'header.notifications', left: 80.553, top: 2.572, width: 8.289, height: 4.127, module: 'tasks' },
  { id: 'header.settings', left: 89.054, top: 2.572, width: 7.120, height: 4.127, module: 'settings' },
  { id: 'voice.chevron', left: 89.586, top: 9.809, width: 6.270, height: 3.589, module: 'assistant' },
  // Ideas has a real mic-capture button (browser speech recognition) --
  // "Voice Link" and "Quick Note" both land there, same as Capture Idea.
  { id: 'voice.voiceLink', left: 31.456, top: 14.892, width: 21.573, height: 4.306, module: 'ideas' },
  { id: 'voice.quickNote', left: 53.454, top: 14.892, width: 20.935, height: 4.306, module: 'ideas' },
  { id: 'voice.aiAssist', left: 74.601, top: 14.833, width: 20.723, height: 4.366, module: 'assistant' },
  // No "send a message" feature exists -- Contacts is the closest real place
  // to reach a person.
  { id: 'onthisday.sendWishes', left: 72.476, top: 39.653, width: 10.733, height: 2.033, module: 'contacts' },
  { id: 'duesoon.viewAll', left: 3.294, top: 42.165, width: 29.862, height: 3.409, module: 'tasks' },
  // No hourly-forecast data exists -- Settings is where the weather location
  // that drives the real (current-conditions-only) weather lives.
  { id: 'weather.hourlyForecast', left: 36.876, top: 42.165, width: 26.355, height: 3.409, module: 'settings' },
  { id: 'onthisday.viewAllMemories', left: 67.375, top: 42.165, width: 29.224, height: 3.409, module: 'milestones' },
  { id: 'habits.edit', left: 90.967, top: 46.890, width: 6.589, height: 2.811, module: 'habits' },
  { id: 'surpriseme.planIt', left: 3.188, top: 60.586, width: 28.480, height: 3.409, special: 'surprise' },
  { id: 'habits.viewHabitTracker', left: 66.950, top: 60.586, width: 30.074, height: 3.529, module: 'habits' },
  { id: 'quickactions.addTask', left: 2.869, top: 66.029, width: 19.554, height: 3.589, module: 'tasks' },
  { id: 'quickactions.newNote', left: 23.486, top: 66.029, width: 18.172, height: 3.589, module: 'ideas' },
  { id: 'quickactions.captureIdea', left: 41.658, top: 66.029, width: 18.491, height: 3.589, module: 'ideas' },
  { id: 'quickactions.scanDoc', left: 60.149, top: 66.029, width: 18.172, height: 3.589, module: 'documents' },
  { id: 'quickactions.saveLink', left: 78.427, top: 66.029, width: 18.491, height: 3.589, module: 'links' },
  { id: 'reviewsdue.reviewAll', left: 2.976, top: 81.579, width: 25.824, height: 3.170, module: 'recall' },
  // No timed daily-schedule/calendar concept exists -- Tasks is the closest
  // real "what's on for today" destination.
  { id: 'focusschedule.viewCalendar', left: 32.625, top: 82.656, width: 19.872, height: 2.990, module: 'tasks' },
  { id: 'pacinginsights.viewInsights', left: 2.763, top: 95.634, width: 22.848, height: 3.050, module: 'education' },
  { id: 'ideaspark.moreIdeas', left: 29.649, top: 95.754, width: 22.848, height: 3.110, module: 'ideas' },
];

// The radial wheel's six true traced petal polygons (vertices in the full
// 941x1672 image coordinate system -- see the nexus-test scratch work this
// session for the contour-extraction method). No separate hotspot exists
// for the wheel's center hub, confirmed against the reference trace.
const WHEEL_PETALS = [
  { id: 'today', module: 'dashboard', points: '729,1353.5 702,1348.5 672,1350.5 649.5,1347 638.5,1326 628.5,1304 618,1282.5 608,1260.5 597.5,1239 589,1216.5 604,1199.5 625,1188.5 652,1183.5 680,1179.5 710,1179.5 739,1182.5 766,1187.5 787,1198.5 800.5,1217 791.5,1240 781,1261.5 770,1282.5 759.5,1304 748.5,1325 737.5,1346' },
  { id: 'tasks', module: 'tasks', points: '796,1418.5 783.5,1403 774,1384.5 761,1369.5 746.5,1356 751.5,1337 760.5,1318 770,1299.5 779.5,1281 789,1262.5 798.5,1244 817.5,1242 833.5,1254 849,1266.5 863.5,1280 876,1295.5 887.5,1312 897.5,1330 905.5,1350 900.5,1371 891.5,1390 875,1401.5 852,1406.5 829,1411.5 806,1416.5' },
  { id: 'settings', module: 'settings', points: '592,1417.5 569,1413.5 547,1408.5 525.5,1403 505,1396.5 496,1378.5 488.5,1359 492,1339.5 500.5,1321 510,1303.5 522,1288.5 534.5,1274 548.5,1261 563.5,1249 581,1239.5 593,1254.5 602,1272.5 610.5,1291 619.5,1309 628,1327.5 637.5,1345 633.5,1362 620,1375.5 608.5,1391 599.5,1409' },
  { id: 'ideas', module: 'ideas', points: '829,1603.5 812.5,1591 801,1573.5 789,1556.5 777,1539.5 765,1522.5 767,1504.5 779.5,1488 787.5,1467 791.5,1442 805.5,1427 829.5,1422 854,1417.5 879,1413.5 896,1421.5 909.5,1437 917.5,1458 914.5,1484 910,1508.5 902.5,1530 893.5,1550 883,1568.5 870.5,1585 855,1598.5 830,1602.5' },
  { id: 'places', module: 'places', points: '567,1603.5 542,1600.5 523.5,1591 512,1574.5 501.5,1557 491.5,1539 484.5,1518 479,1495.5 476.5,1470 478.5,1446 491,1430.5 504,1415.5 527.5,1416 551,1420.5 574,1425.5 592.5,1435 593.5,1460 600,1481.5 610.5,1499 624.5,1513 617,1529.5 609,1543.5 597.5,1560 586.5,1577 575.5,1594' },
  { id: 'search', module: 'search', points: '710,1660.5 679,1660.5 652,1656.5 627.5,1650 605,1641.5 590.5,1625 585.5,1607 597.5,1588 610,1569.5 622.5,1551 634.5,1532 655,1525.5 680,1531.5 710,1530.5 734.5,1524 753.5,1531 766,1549.5 778.5,1568 791,1586.5 803,1605.5 799.5,1624 784.5,1640 763,1649.5 738.5,1656 711,1659.5' },
];

function hubImgUrl() {
  return new URL('./img/hub.png', import.meta.url).href;
}

function showToast(stage, text) {
  const toast = el('div', { class: 'nxs-toast', text });
  stage.append(toast);
  requestAnimationFrame(() => toast.classList.add('is-visible'));
  setTimeout(() => {
    toast.classList.remove('is-visible');
    setTimeout(() => toast.remove(), 250);
  }, 2600);
}

async function handleSurprise(stage) {
  const result = await ctx.data.getSurpriseMe();
  if (result) { ctx.navigate(result.module); return; }
  showToast(stage, 'Nothing in the queue — add a want-to-go place, an unread book, an untried recipe, or a bucket-list goal.');
}

function renderHome(canvas) {
  const stage = el('div', { class: 'nxs-stage' });
  // The picture and the petal hotspots live in ONE svg, sharing a single
  // viewBox/coordinate transform -- putting the <img> and the petal <svg> as
  // two separately-laid-out elements (each independently computing "100% of
  // .nxs-stage") looked right in testing but drifted out of alignment on a
  // real device, almost certainly subpixel rounding between the two
  // elements' box computations. An <image> inside the same <svg> as the
  // <polygon>s can't drift from them; there's only one transform to get
  // wrong instead of two that have to agree.
  // preserveAspectRatio="none": the stage now fills the real viewport
  // rather than being locked to the image's own 941:1672 ratio (see the
  // .nxs-stage comment in style.css), so the box it's stretching to fill
  // usually isn't that exact ratio. "none" stretches non-uniformly to
  // match it exactly -- mild distortion, but the image and the <polygon>
  // hotspots share this one transform, so they stay aligned regardless.
  const svg = svgEl('svg', { class: 'nxs-petal-layer', viewBox: `0 0 ${IMG_W} ${IMG_H}`, preserveAspectRatio: 'none' });
  const img = svgEl('image', { href: hubImgUrl(), x: 0, y: 0, width: IMG_W, height: IMG_H });
  svg.append(img);
  for (const p of WHEEL_PETALS) {
    const poly = svgEl('polygon', { points: p.points, class: 'nxs-petal' });
    poly.addEventListener('click', () => ctx.navigate(p.module));
    svg.append(poly);
  }
  stage.append(svg);

  for (const h of HOTSPOTS) {
    stage.append(el('button', {
      type: 'button', class: 'nxs-hotspot',
      style: `left:${h.left}%; top:${h.top}%; width:${h.width}%; height:${h.height}%;`,
      title: moduleLabel(h.module) || 'Surprise me',
      onclick: () => (h.special === 'surprise' ? handleSurprise(stage) : ctx.navigate(h.module)),
    }));
  }

  canvas.append(stage);
}

// --- Any other module (persistent slim header + hosted view) ---

async function renderModuleScreen(canvas, route) {
  canvas.append(renderHeader());
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
  const isHome = route.module === 'dashboard';
  const canvas = el('div', { class: isHome ? 'nxs-canvas nxs-canvas--home' : 'nxs-canvas' });
  els.root.append(canvas);
  if (isHome) {
    renderHome(canvas);
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
  description: "The user's own mockup, mapped: real click regions on the actual design.",
  stylesheet: 'js/interfaces/nexus/style.css',

  async mount(container, context) {
    ctx = context;
    const root = el('div', { class: 'nxs-root' });
    container.append(root);
    els = { root };
    // Module screens host real views that need to react to data changes
    // from elsewhere (sync, another tab). The dashboard route's hotspots
    // are static navigation, so a rerender there just rebuilds the same
    // image+hotspot layer -- cheap, and the browser has the image cached.
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

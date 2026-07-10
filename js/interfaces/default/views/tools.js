import { el } from '../dom.js';

let state = {
  tab: 'currency', // currency | units | timezones
  fromCurrency: 'USD',
  toCurrency: 'EUR',
  amount: 1,
  unitCategory: 'length',
  fromUnit: 'm',
  toUnit: 'ft',
  unitAmount: 1,
  triedLiveRateRefresh: false, // one auto-refresh attempt per app session, not per render
};

const UNIT_TABLES = {
  length: { label: 'Length', base: 'm', units: { m: 1, km: 1000, cm: 0.01, mm: 0.001, mi: 1609.344, yd: 0.9144, ft: 0.3048, in: 0.0254 } },
  weight: { label: 'Weight', base: 'kg', units: { kg: 1, g: 0.001, lb: 0.453592, oz: 0.0283495, ton: 907.185 } },
  volume: { label: 'Volume', base: 'l', units: { l: 1, ml: 0.001, gal: 3.78541, qt: 0.946353, cup: 0.24, floz: 0.0295735 } },
  temperature: { label: 'Temperature', units: { C: 'C', F: 'F', K: 'K' } },
};

function toCelsius(value, unit) {
  if (unit === 'C') return value;
  if (unit === 'F') return (value - 32) * (5 / 9);
  if (unit === 'K') return value - 273.15;
  return value;
}

function fromCelsius(value, unit) {
  if (unit === 'C') return value;
  if (unit === 'F') return value * (9 / 5) + 32;
  if (unit === 'K') return value + 273.15;
  return value;
}

function convertUnit(category, value, fromUnit, toUnit) {
  if (category === 'temperature') return fromCelsius(toCelsius(value, fromUnit), toUnit);
  const table = UNIT_TABLES[category].units;
  return (value * table[fromUnit]) / table[toUnit];
}

function fmtNum(n) {
  if (!Number.isFinite(n)) return '';
  return Math.round(n * 10000) / 10000;
}

// --- Currency ---
// Rates come from open.er-api.com (free, keyless, ~160 currencies -- wider
// coverage than Frankfurter/ECB's ~30, which is why every seeded default
// currency actually gets a live update instead of some staying static)
// when online, cached into Settings so the converter still works offline
// from whatever was last fetched (or the manual defaults, on a fresh
// install). The merge below (`{...rates, ...data.rates}`) also backfills
// any currency added to the seeded defaults after this was first installed
// -- an existing install's stored rates gain the new codes on the next
// refresh, not just fresh installs.

const LIVE_RATES_URL = 'https://open.er-api.com/v6/latest/USD';
const STALE_RATES_MS = 24 * 60 * 60 * 1000;

async function refreshLiveRates(ctx, rerender, { silent } = {}) {
  try {
    const res = await fetch(LIVE_RATES_URL);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.result !== 'success' || !data.rates) throw new Error('Unexpected response shape');
    const rates = await ctx.data.Settings.get('currencyRates');
    await ctx.data.Settings.set('currencyRates', { ...rates, ...data.rates, USD: 1 });
    await ctx.data.Settings.set('currencyRatesFetchedAt', new Date().toISOString());
    rerender();
  } catch (err) {
    if (!silent) alert(`Couldn't fetch live rates (offline?): ${err.message}`);
  }
}

function currencyEditor(rates, ctx, rerender) {
  const rows = el('div', {}, Object.entries(rates).map(([code, rate]) => {
    const codeInput = el('input', { type: 'text', value: code, style: 'width:4rem;text-transform:uppercase' });
    const rateInput = el('input', { type: 'number', step: 'any', value: rate });
    const commit = async () => {
      const newRates = { ...rates };
      delete newRates[code];
      newRates[codeInput.value.trim().toUpperCase()] = Number(rateInput.value) || 0;
      await ctx.data.Settings.set('currencyRates', newRates);
      rerender();
    };
    codeInput.onchange = commit;
    rateInput.onchange = commit;
    const reverseText = rate ? `1 ${code} = ${fmtNum(1 / rate)} USD` : '';
    return el('div', { class: 'mer-person-form' }, [
      codeInput, rateInput,
      el('span', { class: 'mer-chip', text: reverseText }),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => {
          const newRates = { ...rates };
          delete newRates[code];
          await ctx.data.Settings.set('currencyRates', newRates);
          rerender();
        },
      }),
    ]);
  }));

  const newCode = el('input', { type: 'text', placeholder: 'Code (e.g. MXN)' });
  const newRate = el('input', { type: 'number', step: 'any', placeholder: 'Rate vs USD' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add currency',
    onclick: async () => {
      if (!newCode.value.trim() || !newRate.value) return;
      const newRates = { ...rates, [newCode.value.trim().toUpperCase()]: Number(newRate.value) };
      await ctx.data.Settings.set('currencyRates', newRates);
      rerender();
    },
  });

  return el('div', {}, [rows, el('div', { class: 'mer-person-form' }, [newCode, newRate, addBtn])]);
}

async function renderCurrency(container, ctx, rerender) {
  const rates = await ctx.data.Settings.get('currencyRates');
  const fetchedAt = await ctx.data.Settings.get('currencyRatesFetchedAt');
  const codes = Object.keys(rates);
  if (!codes.includes(state.fromCurrency)) state.fromCurrency = codes[0];
  if (!codes.includes(state.toCurrency)) state.toCurrency = codes[1] || codes[0];

  const isStale = !fetchedAt || (Date.now() - new Date(fetchedAt).getTime()) > STALE_RATES_MS;
  if (isStale && !state.triedLiveRateRefresh) {
    state.triedLiveRateRefresh = true;
    refreshLiveRates(ctx, rerender, { silent: true });
  }

  const amountInput = el('input', { type: 'number', step: 'any', value: state.amount, onchange: (e) => { state.amount = Number(e.target.value) || 0; rerender(); } });
  const fromSelect = el('select', { onchange: (e) => { state.fromCurrency = e.target.value; rerender(); } },
    codes.map((c) => el('option', { value: c, text: c, selected: c === state.fromCurrency })));
  const toSelect = el('select', { onchange: (e) => { state.toCurrency = e.target.value; rerender(); } },
    codes.map((c) => el('option', { value: c, text: c, selected: c === state.toCurrency })));

  const result = (state.amount / rates[state.fromCurrency]) * rates[state.toCurrency];

  const statusText = fetchedAt
    ? `Live rates (open.er-api.com) as of ${new Date(fetchedAt).toLocaleString()}.`
    : 'No live rates fetched yet — using manual/default rates.';

  container.append(
    el('div', { class: 'mer-field-grid' }, [
      el('label', { class: 'mer-field' }, [el('span', { text: 'Amount' }), amountInput]),
      el('label', { class: 'mer-field' }, [el('span', { text: 'From' }), fromSelect]),
      el('label', { class: 'mer-field' }, [el('span', { text: 'To' }), toSelect]),
    ]),
    el('p', {}, [el('strong', { text: `${state.amount} ${state.fromCurrency} = ${fmtNum(result)} ${state.toCurrency}` })]),
    el('p', { class: 'mer-muted' }, [
      document.createTextNode(statusText + ' '),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '🔄 Refresh', onclick: () => refreshLiveRates(ctx, rerender) }),
    ]),
    el('div', { class: 'mer-subsection-label', text: 'Exchange rates (per 1 USD)' }),
    el('p', { class: 'mer-muted', text: 'Editable — overrides live rates until the next refresh, and keeps this working fully offline.' }),
    currencyEditor(rates, ctx, rerender),
  );
}

// --- Units ---

async function renderUnits(container, ctx, rerender) {
  const categorySelect = el('select', {
    onchange: (e) => {
      state.unitCategory = e.target.value;
      const units = Object.keys(UNIT_TABLES[state.unitCategory].units);
      state.fromUnit = units[0];
      state.toUnit = units[1] || units[0];
      rerender();
    },
  }, Object.entries(UNIT_TABLES).map(([key, t]) => el('option', { value: key, text: t.label, selected: key === state.unitCategory })));

  const units = Object.keys(UNIT_TABLES[state.unitCategory].units);
  if (!units.includes(state.fromUnit)) state.fromUnit = units[0];
  if (!units.includes(state.toUnit)) state.toUnit = units[1] || units[0];

  const amountInput = el('input', { type: 'number', step: 'any', value: state.unitAmount, onchange: (e) => { state.unitAmount = Number(e.target.value) || 0; rerender(); } });
  const fromSelect = el('select', { onchange: (e) => { state.fromUnit = e.target.value; rerender(); } },
    units.map((u) => el('option', { value: u, text: u, selected: u === state.fromUnit })));
  const toSelect = el('select', { onchange: (e) => { state.toUnit = e.target.value; rerender(); } },
    units.map((u) => el('option', { value: u, text: u, selected: u === state.toUnit })));

  const result = convertUnit(state.unitCategory, state.unitAmount, state.fromUnit, state.toUnit);

  container.append(
    el('div', { class: 'mer-field-grid' }, [
      el('label', { class: 'mer-field' }, [el('span', { text: 'Category' }), categorySelect]),
      el('label', { class: 'mer-field' }, [el('span', { text: 'Amount' }), amountInput]),
      el('label', { class: 'mer-field' }, [el('span', { text: 'From' }), fromSelect]),
      el('label', { class: 'mer-field' }, [el('span', { text: 'To' }), toSelect]),
    ]),
    el('p', {}, [el('strong', { text: `${state.unitAmount} ${state.fromUnit} = ${fmtNum(result)} ${state.toUnit}` })]),
  );
}

// --- Timezones ---

async function renderTimezones(container, ctx, rerender) {
  const saved = await ctx.data.Settings.get('savedTimezones');
  const now = new Date();

  const localRow = el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: 'Your local time' })]),
    el('div', { text: now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) }),
  ]);

  const rows = saved.map((entry) => el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: entry.label }),
      el('div', { class: 'mer-person-meta', text: entry.tz }),
    ]),
    el('div', { text: now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', timeZone: entry.tz }) }),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×',
      onclick: async () => {
        await ctx.data.Settings.set('savedTimezones', saved.filter((s) => s !== entry));
        rerender();
      },
    }),
  ]));

  const labelInput = el('input', { type: 'text', placeholder: 'Label (e.g. Mom in Tokyo)' });
  const tzInput = el('input', { type: 'text', placeholder: 'IANA zone (e.g. Asia/Tokyo)' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add',
    onclick: async () => {
      if (!labelInput.value.trim() || !tzInput.value.trim()) return;
      try {
        Intl.DateTimeFormat(undefined, { timeZone: tzInput.value.trim() });
      } catch {
        alert('Not a recognized timezone name (e.g. "America/New_York", "Europe/London").');
        return;
      }
      await ctx.data.Settings.set('savedTimezones', [...saved, { label: labelInput.value.trim(), tz: tzInput.value.trim() }]);
      rerender();
    },
  });

  container.append(
    el('div', { class: 'mer-people-list' }, [localRow, ...rows]),
    el('div', { class: 'mer-person-form' }, [labelInput, tzInput, addBtn]),
  );
}

// --- Root ---

function tabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'currency' ? 'is-active' : '', text: 'Currency', onclick: () => { state.tab = 'currency'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'units' ? 'is-active' : '', text: 'Units', onclick: () => { state.tab = 'units'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'timezones' ? 'is-active' : '', text: 'Timezones', onclick: () => { state.tab = 'timezones'; rerender(); } }),
  ]);
}

export async function renderTools(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Tools' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'currency') await renderCurrency(area, ctx, rerender);
  else if (state.tab === 'units') await renderUnits(area, ctx, rerender);
  else await renderTimezones(area, ctx, rerender);
}

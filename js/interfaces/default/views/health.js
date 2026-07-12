import { el, fmtDate, todayStr } from '../dom.js';

let state = {
  selectedId: null,
  importStatus: null,   // text shown while reading/parsing
  importError: null,
  importPreview: null,  // parsed days, awaiting confirm
  importResult: null,   // { created, updated } after a confirmed import
};

function isWithinLastDays(dateStr, days) {
  if (!dateStr) return false;
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - days);
  return new Date(dateStr + 'T00:00:00') >= cutoff;
}

function avg(nums) {
  const valid = nums.filter((n) => Number.isFinite(n));
  return valid.length ? valid.reduce((a, b) => a + b, 0) / valid.length : null;
}

function logRow(log, onSelect) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('span', { class: 'mer-task-title', text: fmtDate(log.date) }),
    el('div', { class: 'mer-task-meta' }, [
      log.sleepHours ? el('span', { class: 'mer-chip', text: `😴 ${log.sleepHours}h` }) : null,
      log.workoutType ? el('span', { class: 'mer-chip', text: `🏋️ ${log.workoutType}${log.workoutMinutes ? ` (${log.workoutMinutes}m)` : ''}` }) : null,
      log.waterOz ? el('span', { class: 'mer-chip', text: `💧 ${log.waterOz}oz` }) : null,
    ]),
  ]);
  row.addEventListener('click', () => onSelect(log.id));
  return row;
}

function detailEditor(log, ctx, rerender) {
  const patch = (fields) => ctx.data.HealthLogs.update(log.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const dateInput = el('input', { type: 'date', value: log.date || '', onchange: (e) => patch({ date: e.target.value || todayStr() }) });
  const sleepInput = el('input', { type: 'number', step: '0.1', min: '0', value: log.sleepHours ?? '', placeholder: 'Hours', onchange: (e) => patch({ sleepHours: e.target.value ? Number(e.target.value) : null }) });
  const workoutTypeInput = el('input', { type: 'text', value: log.workoutType || '', placeholder: 'Run, lift, yoga…', onchange: (e) => patch({ workoutType: e.target.value }) });
  const workoutMinutesInput = el('input', { type: 'number', min: '0', value: log.workoutMinutes ?? '', placeholder: 'Minutes', onchange: (e) => patch({ workoutMinutes: e.target.value ? Number(e.target.value) : null }) });
  const waterInput = el('input', { type: 'number', min: '0', value: log.waterOz ?? '', placeholder: 'Ounces', onchange: (e) => patch({ waterOz: e.target.value ? Number(e.target.value) : null }) });
  const weightInput = el('input', { type: 'number', step: '0.1', min: '0', value: log.weight ?? '', placeholder: 'Weight (optional)', onchange: (e) => patch({ weight: e.target.value ? Number(e.target.value) : null }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(log.notes || '')]);

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: fmtDate(log.date) }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Date', dateInput),
      field('Sleep (hrs)', sleepInput),
      field('Workout type', workoutTypeInput),
      field('Workout (min)', workoutMinutesInput),
      field('Water (oz)', waterInput),
      field('Weight', weightInput),
    ]),
    field('Notes', notesInput),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete log',
      onclick: async () => { await ctx.data.HealthLogs.remove(log.id); state.selectedId = null; rerender(); },
    }),
  ]);
}

// Apple Health import: a one-time manual file pick (raw export.xml, or the
// .zip containing it), parsed client-side, then a preview + explicit
// confirm before anything is written -- an import can overwrite fields on
// existing logs for the same dates, so it's never silent.
async function handleAppleHealthFile(file, ctx, rerender) {
  state.importError = null;
  state.importPreview = null;
  state.importResult = null;
  state.importStatus = 'Reading…';
  rerender();
  try {
    const days = await ctx.data.parseAppleHealthExport(file, { onStatus: (s) => { state.importStatus = s; rerender(); } });
    if (!days.length) {
      state.importError = 'No sleep, workout, water, or weight data found in that file.';
    } else {
      state.importPreview = days;
    }
  } catch (err) {
    state.importError = err.message || String(err);
  }
  state.importStatus = null;
  rerender();
}

async function confirmAppleHealthImport(ctx, rerender) {
  state.importStatus = 'Importing…';
  rerender();
  try {
    state.importResult = await ctx.data.importAppleHealthDays(state.importPreview);
  } catch (err) {
    state.importError = err.message || String(err);
  }
  state.importPreview = null;
  state.importStatus = null;
  rerender();
}

function appleHealthImportSection(ctx, rerender) {
  const parts = [el('div', { class: 'mer-subsection-label', text: 'Import from Apple Health' })];
  parts.push(el('p', { class: 'mer-muted', text: 'A one-time import of an Apple Health export (Health app → profile picture → Export All Health Data). Aggregates sleep, workouts, water, and weight down to one entry per day, filling in matching fields on existing logs and creating new ones as needed -- not a live sync.' }));

  if (state.importStatus) {
    parts.push(el('p', { class: 'mer-muted', text: state.importStatus }));
    return el('div', {}, parts);
  }
  if (state.importError) {
    parts.push(el('p', { class: 'mer-muted mer-sync-error', text: state.importError }));
  }
  if (state.importResult) {
    parts.push(el('p', {}, [el('strong', { text: `Imported: ${state.importResult.created} new day(s), ${state.importResult.updated} updated.` })]));
  }
  if (state.importPreview) {
    parts.push(
      el('p', {}, [el('strong', { text: `Found ${state.importPreview.length} day(s) of data.` }), document.createTextNode(' This will fill in matching fields on existing logs for those dates and create new ones otherwise.')]),
      el('div', { class: 'mer-toolbar' }, [
        el('button', { type: 'button', text: 'Confirm import', onclick: () => confirmAppleHealthImport(ctx, rerender) }),
        el('button', { type: 'button', class: 'mer-reader-btn', text: 'Cancel', onclick: () => { state.importPreview = null; rerender(); } }),
      ]),
    );
    return el('div', {}, parts);
  }

  parts.push(el('input', {
    type: 'file', accept: '.xml,.zip,application/xml,application/zip',
    onchange: (e) => { if (e.target.files[0]) handleAppleHealthFile(e.target.files[0], ctx, rerender); e.target.value = ''; },
  }));
  return el('div', {}, parts);
}

export async function renderHealth(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Health' }));

  const quickAdd = el('button', {
    type: 'button', class: 'mer-quick-add', text: "+ Log today's entry",
    onclick: async () => {
      const existing = (await ctx.data.HealthLogs.byIndex('date', todayStr()))[0];
      if (existing) { state.selectedId = existing.id; rerender(); return; }
      const created = await ctx.data.HealthLogs.create({ date: todayStr() });
      state.selectedId = created.id;
      rerender();
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [quickAdd]));

  const logs = await ctx.data.HealthLogs.list();
  const recent = logs.filter((l) => isWithinLastDays(l.date, 7));
  const avgSleep = avg(recent.map((l) => l.sleepHours));
  const avgWater = avg(recent.map((l) => l.waterOz));
  const workoutCount = recent.filter((l) => l.workoutType).length;

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  area.append(el('p', {}, [
    el('strong', { text: 'Last 7 days: ' }),
    el('span', { text: [
      avgSleep !== null ? `avg sleep ${avgSleep.toFixed(1)}h` : null,
      avgWater !== null ? `avg water ${avgWater.toFixed(0)}oz` : null,
      `${workoutCount} workout${workoutCount === 1 ? '' : 's'}`,
    ].filter(Boolean).join(' · ') }),
  ]));

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; rerender(); };

  if (!logs.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No health logs yet.' }));
  } else {
    const sorted = [...logs].sort((a, b) => (a.date < b.date ? 1 : -1));
    for (const log of sorted) area.append(logRow(log, onSelect));
  }

  if (state.selectedId) {
    const log = logs.find((l) => l.id === state.selectedId);
    if (log) canvas.append(detailEditor(log, ctx, rerender));
  }

  canvas.append(appleHealthImportSection(ctx, rerender));
}

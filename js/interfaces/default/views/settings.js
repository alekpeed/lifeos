import { el, fmtDate } from '../dom.js';

function fmtSyncTime(iso) {
  if (!iso) return 'never';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${fmtDate(iso.slice(0, 10))}, ${d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`;
}

async function renderSyncSection(canvas, ctx, rerender) {
  const state = await ctx.data.getSyncState();

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Google Drive sync' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Syncs your data between your devices through your own Google Drive — no company server in between. Uses the drive.file scope, so Life OS can only ever see the LifeOS folder it creates, never the rest of your Drive. Your device preferences (theme, density, active interface) stay local and are not synced.' }));

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (text, isError) => { status.textContent = text; status.classList.toggle('mer-sync-error', !!isError); };
  setStatus(state.enabled ? `Last synced: ${fmtSyncTime(state.lastSyncedAt)}` : 'Not connected yet.');

  const runSync = async (fn, label) => {
    row.querySelectorAll('button').forEach((b) => (b.disabled = true));
    setStatus(`${label}…`);
    try {
      const res = await fn();
      const changed = res?.affected?.length ? ` (updated ${res.affected.join(', ')})` : '';
      setStatus(`Synced ${fmtSyncTime(new Date().toISOString())}${changed}.`);
      await rerender();
    } catch (err) {
      setStatus(err.message || String(err), true);
      row.querySelectorAll('button').forEach((b) => (b.disabled = false));
    }
  };

  const connectBtn = el('button', {
    type: 'button', text: state.enabled ? 'Sync now' : 'Connect Google Drive',
    onclick: () => runSync(state.enabled ? ctx.data.syncNow : ctx.data.connectDrive, state.enabled ? 'Syncing' : 'Connecting'),
  });
  const buttons = [connectBtn];
  if (state.enabled) {
    buttons.push(el('button', {
      type: 'button', text: 'Disconnect',
      onclick: async () => { await ctx.data.disconnectDrive(); await rerender(); },
    }));
  }
  const row = el('div', { class: 'mer-toolbar' }, buttons);
  canvas.append(row, status);
}

export async function renderSettings(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Settings' }));
  const settings = await ctx.data.Settings.getAll();

  const select = (label, options, current, onChange) => {
    const sel = el('select', { onchange: (e) => onChange(e.target.value) });
    for (const opt of options) {
      sel.append(el('option', { value: opt.value, text: opt.label, selected: opt.value === current }));
    }
    return el('label', { class: 'mer-setting' }, [el('span', { text: label }), sel]);
  };

  canvas.append(
    select('Theme', [
      { value: 'dark', label: 'Dark' },
      { value: 'light', label: 'Light' },
    ], settings.theme, (v) => ctx.data.Settings.set('theme', v)),

    select('Accent', [
      { value: 'brass', label: 'Brass' },
      { value: 'teal', label: 'Teal' },
      { value: 'garnet', label: 'Garnet' },
    ], settings.accent, (v) => ctx.data.Settings.set('accent', v)),

    select('Density', [
      { value: 'comfortable', label: 'Comfortable' },
      { value: 'compact', label: 'Compact' },
    ], settings.density, (v) => ctx.data.Settings.set('density', v)),

    select('Interface',
      ctx.listInterfaces().map((i) => ({ value: i.id, label: i.name })),
      settings.activeInterface,
      (v) => ctx.switchInterface(v)),

    el('label', { class: 'mer-setting' }, [
      el('span', { text: 'Bill due-soon alert (days)' }),
      el('input', {
        type: 'number', min: '1', max: '90', value: settings.billDueSoonDays,
        onchange: (e) => ctx.data.Settings.set('billDueSoonDays', Number(e.target.value) || 7),
      }),
    ]),

    el('label', { class: 'mer-setting' }, [
      el('span', { text: 'Document expiry alert (days)' }),
      el('input', {
        type: 'number', min: '1', max: '365', value: settings.documentExpiryDays,
        onchange: (e) => ctx.data.Settings.set('documentExpiryDays', Number(e.target.value) || 30),
      }),
    ]),
  );

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Backup' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'A manual, Drive-independent backup -- exports every module\'s data (including photos/attachments) as one JSON file.' }));

  const exportBtn = el('button', {
    type: 'button', text: 'Export all data as JSON',
    onclick: async () => {
      exportBtn.disabled = true;
      exportBtn.textContent = 'Exporting…';
      try {
        const payload = await ctx.data.exportAllData();
        const json = JSON.stringify(payload);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `lifeos-backup-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
      } finally {
        exportBtn.disabled = false;
        exportBtn.textContent = 'Export all data as JSON';
      }
    },
  });

  const importInput = el('input', {
    type: 'file', accept: 'application/json',
    onchange: async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      if (!confirm('This replaces all current Life OS data with the contents of this backup file. Continue?')) {
        e.target.value = '';
        return;
      }
      try {
        const text = await file.text();
        const payload = JSON.parse(text);
        await ctx.data.importAllData(payload);
        alert('Import complete. Reloading…');
        location.reload();
      } catch (err) {
        alert(`Import failed: ${err.message}`);
      }
    },
  });

  canvas.append(el('div', { class: 'mer-toolbar' }, [exportBtn, el('label', { class: 'mer-setting' }, [el('span', { text: 'Import from JSON' }), importInput])]));

  await renderSyncSection(canvas, ctx, rerender || (() => {}));
}

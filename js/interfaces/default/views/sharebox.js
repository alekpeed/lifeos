// Sharebox — a small space shared with a friend through a Drive folder you
// both pick. Links, notes, and files, each with an urgency flag and a
// "posted by" name. Add things anytime (they live locally first); they
// reconcile with your friend's copy on sync through the shared folder.

import { el, fmtDate } from '../dom.js';

let state = {
  kind: 'link', // link | note | file
  urgency: 'normal',
};

const URGENCY = [
  { value: 'normal', label: 'Normal' },
  { value: 'soon', label: 'Soon' },
  { value: 'urgent', label: 'Urgent' },
];
const URGENCY_RANK = { urgent: 0, soon: 1, normal: 2 };
const KIND_ICON = { link: '🔗', note: '📝', file: '📎' };

// A bare "espn.com" with no scheme is a valid <a href> — browsers resolve it
// as a RELATIVE link against the current page instead of an external site.
// Default to https:// whenever no scheme is present.
function normalizeUrl(raw) {
  const trimmed = (raw || '').trim();
  if (!trimmed || /^[a-z][a-z0-9+.-]*:/i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}

function fmtWhen(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${fmtDate(iso.slice(0, 10))}, ${d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`;
}

// --- Connect / sync section ---

async function renderConnect(canvas, ctx, rerender) {
  const st = await ctx.data.getShareboxState();

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Shared folder' }));

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (t, err) => { status.textContent = t; status.classList.toggle('mer-sync-error', !!err); };
  if (st.enabled && st.folderId) setStatus(`Connected to “${st.folderName || 'shared folder'}” · last synced ${fmtWhen(st.lastSyncedAt) || 'never'}`);
  else setStatus('Not connected. Connect the folder your friend shared with you to sync this space.');

  const run = async (fn, label) => {
    row.querySelectorAll('button').forEach((b) => (b.disabled = true));
    setStatus(`${label}…`);
    try {
      const res = await fn();
      if (res?.cancelled) { setStatus('Folder selection cancelled.'); row.querySelectorAll('button').forEach((b) => (b.disabled = false)); return; }
      await rerender();
    } catch (err) {
      setStatus(err.message || String(err), true);
      row.querySelectorAll('button').forEach((b) => (b.disabled = false));
    }
  };

  const buttons = [];
  if (st.enabled && st.folderId) {
    buttons.push(el('button', { type: 'button', text: 'Sync now', onclick: () => run(ctx.data.syncShareboxNow, 'Syncing') }));
    buttons.push(el('button', { type: 'button', text: 'Disconnect', onclick: async () => { await ctx.data.disconnectSharebox(); await rerender(); } }));
  } else {
    buttons.push(el('button', { type: 'button', text: 'Connect shared folder', onclick: () => run(ctx.data.connectSharebox, 'Opening picker') }));
  }
  const row = el('div', { class: 'mer-toolbar' }, buttons);
  canvas.append(el('p', { class: 'mer-muted', text: 'Links, notes and files here sync with one friend through a Google Drive folder you both choose — separate from the rest of your data, which is never shared. Uses the same drive.file scope; the app only ever sees the folder you pick.' }));
  canvas.append(row, status);
}

// --- Add form ---

function addForm(ctx, name, rerender) {
  const kindToggle = el('div', { class: 'mer-toggle-group' }, [
    ['link', '🔗 Link'], ['note', '📝 Note'], ['file', '📎 File'],
  ].map(([k, label]) => el('button', {
    type: 'button', class: state.kind === k ? 'is-active' : '', text: label,
    onclick: () => { state.kind = k; rerender(); },
  })));

  const urgencySelect = el('select', { onchange: (e) => { state.urgency = e.target.value; } },
    URGENCY.map((u) => el('option', { value: u.value, text: u.label, selected: u.value === state.urgency })));

  const postedBy = name || 'Someone';
  const fields = el('div', { class: 'mer-person-form' });

  if (state.kind === 'link') {
    const titleIn = el('input', { type: 'text', placeholder: 'Title (optional)' });
    const urlIn = el('input', { type: 'url', placeholder: 'https://…' });
    fields.append(urlIn, titleIn, urgencySelect, el('button', {
      type: 'button', text: 'Share link',
      onclick: async () => {
        if (!urlIn.value.trim()) return;
        await ctx.data.ShareboxItems.create({ kind: 'link', url: normalizeUrl(urlIn.value), title: titleIn.value.trim(), urgency: state.urgency, postedBy });
        rerender();
      },
    }));
  } else if (state.kind === 'note') {
    const bodyIn = el('input', { type: 'text', placeholder: 'Write a note…' });
    fields.append(bodyIn, urgencySelect, el('button', {
      type: 'button', text: 'Share note',
      onclick: async () => {
        if (!bodyIn.value.trim()) return;
        await ctx.data.ShareboxItems.create({ kind: 'note', body: bodyIn.value.trim(), urgency: state.urgency, postedBy });
        rerender();
      },
    }));
  } else {
    const fileIn = el('input', { type: 'file' });
    fields.append(fileIn, urgencySelect, el('button', {
      type: 'button', text: 'Share file',
      onclick: async () => {
        const file = fileIn.files[0];
        if (!file) return;
        const item = await ctx.data.ShareboxItems.create({ kind: 'file', title: file.name, urgency: state.urgency, postedBy });
        await ctx.data.createShareboxFile(file, item.id);
        rerender();
      },
    }));
  }

  return el('div', {}, [kindToggle, fields]);
}

// --- Item list ---

function itemCard(item, files, ctx, rerender) {
  const badge = el('span', { class: `mer-chip mer-urg-${item.urgency || 'normal'}`, text: (item.urgency || 'normal') });
  const meta = el('div', { class: 'mer-person-meta', text: `${item.postedBy || 'Someone'} · ${fmtWhen(item.updatedAt || item.createdAt)}` });

  let bodyEl;
  if (item.kind === 'link') {
    // Defensive: fixes links saved before normalizeUrl() existed too.
    bodyEl = el('a', { href: normalizeUrl(item.url), target: '_blank', rel: 'noopener', text: item.title || item.url });
  } else if (item.kind === 'note') {
    bodyEl = el('div', { class: 'mer-person-name', text: item.body || '' });
  } else {
    const file = files.find((f) => f.itemId === item.id);
    bodyEl = el('div', { class: 'mer-person-name' }, [
      el('span', { text: item.title || 'file' }),
      file ? el('button', {
        type: 'button', class: 'mer-reader-btn', text: 'Download',
        onclick: () => {
          const url = ctx.data.shareboxFileUrl(file);
          if (!url) return;
          const a = document.createElement('a'); a.href = url; a.download = file.filename || 'file'; a.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        },
      }) : el('span', { class: 'mer-muted', text: '(syncing…)' }),
    ]);
  }

  return el('div', { class: 'mer-person-card mer-sharebox-item' }, [
    el('div', { class: 'mer-sharebox-icon', text: KIND_ICON[item.kind] || '•' }),
    el('div', { class: 'mer-person-info' }, [bodyEl, meta]),
    badge,
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove',
      onclick: async () => { if (confirm('Remove this from Sharebox?')) { await ctx.data.ShareboxItems.remove(item.id); rerender(); } },
    }),
  ]);
}

export async function renderSharebox(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Sharebox' }));

  await renderConnect(canvas, ctx, rerender);

  const name = await ctx.data.Settings.get('shareboxName');
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Your name here' }));
  const nameInput = el('input', {
    type: 'text', value: name || '', placeholder: 'e.g. Alek',
    onchange: (e) => ctx.data.Settings.set('shareboxName', e.target.value.trim()),
  });
  canvas.append(el('div', { class: 'mer-person-form' }, [nameInput, el('span', { class: 'mer-muted', text: 'Shown next to things you post (no accounts — each person sets their own).' })]));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add to Sharebox' }));
  canvas.append(addForm(ctx, name, rerender));

  const [items, files] = await Promise.all([ctx.data.ShareboxItems.list(), ctx.data.ShareboxFiles.list()]);
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Shared (${items.length})` }));

  if (!items.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing shared yet.' }));
    return;
  }

  const sorted = [...items].sort((a, b) => {
    const r = (URGENCY_RANK[a.urgency] ?? 2) - (URGENCY_RANK[b.urgency] ?? 2);
    if (r) return r;
    return (b.updatedAt || b.createdAt || '').localeCompare(a.updatedAt || a.createdAt || '');
  });
  const list = el('div', { class: 'mer-people-list' });
  for (const item of sorted) list.append(itemCard(item, files, ctx, rerender));
  canvas.append(list);
}

// Sharebox — a small space shared with a friend. Links, notes, and files, each
// with an urgency flag and a "posted by" name.
//
// Two backends live here side by side during the migration:
//   • v1 (Drive): the original — syncs through a Google Drive folder you both
//     pick, reconciling snapshots on a manual "Sync now". Untouched, still the
//     default. See js/data/sharebox-sync.js.
//   • v2 (Supabase): the replacement — Google sign-in, a "space" you're both
//     members of, Postgres as the source of truth with Row Level Security, and
//     live updates over Realtime (no manual sync). See js/data/sharebox-
//     supabase.js. Opt-in via the backend toggle, only shown once Supabase is
//     configured. Nothing is removed until v2 is proven live side by side.

import { el, fmtDate } from '../dom.js';

let state = {
  kind: 'link', // link | note | file
  urgency: 'normal',
};

// The v2 Realtime subscription for the currently-open space. Kept at module
// scope (not per-render) so a burst of re-renders reuses one subscription
// instead of stacking a new one each time; keyed by space id so switching
// spaces re-subscribes. Torn down when we leave the v2 items view or Sharebox
// entirely (lazily, on the next event, if navigation happened underneath us).
let liveSub = null; // { spaceId, unsub }

function teardownLive() {
  if (liveSub) { liveSub.unsub(); liveSub = null; }
}

function ensureLive(spaceId, ctx, rerender) {
  if (liveSub && liveSub.spaceId === spaceId) return; // already watching this space
  teardownLive();
  const unsub = ctx.data.ShareboxV2.subscribeToItems(spaceId, () => {
    // A friend's change came in. Only refresh if Sharebox is still on screen;
    // otherwise tear down so a background event can't clobber another view.
    if (ctx.parseRoute().module === 'sharebox') rerender();
    else teardownLive();
  });
  liveSub = { spaceId, unsub };
}

// Sign-in/out subscription, separate from the items Realtime subscription
// above. Needed because the Google redirect's code-exchange (see app.js's
// completePendingRedirectIfAny) can still finish *after* this view has already
// rendered the "not signed in" state on a slow connection — without this, the
// screen would be stuck on the sign-in button until the user manually navigates
// away and back. Subscribed once and left running while the v2 view is active.
let authSub = null;

function ensureAuthWatch(ctx, rerender) {
  if (authSub) return;
  authSub = ctx.data.ShareboxV2.onAuthChange(() => {
    if (ctx.parseRoute().module === 'sharebox') rerender();
    else { authSub?.(); authSub = null; }
  });
}

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

// A small "copy to clipboard" button — used for things a friend needs to paste
// elsewhere (a space id to join). Flashes "Copied" briefly on success.
function copyButton(getText, label = 'Copy') {
  const btn = el('button', {
    type: 'button', class: 'mer-reader-btn', text: label,
    onclick: async () => {
      try {
        await navigator.clipboard.writeText(getText());
        const prev = btn.textContent;
        btn.textContent = 'Copied';
        setTimeout(() => { btn.textContent = prev; }, 1200);
      } catch {
        btn.textContent = 'Copy failed';
      }
    },
  });
  return btn;
}

function sortItems(items) {
  return [...items].sort((a, b) => {
    const r = (URGENCY_RANK[a.urgency] ?? 2) - (URGENCY_RANK[b.urgency] ?? 2);
    if (r) return r;
    return (b.updatedAt || b.createdAt || '').localeCompare(a.updatedAt || a.createdAt || '');
  });
}

// ============================================================
// Backend chooser (only rendered once Supabase is configured)
// ============================================================

async function renderBackendToggle(canvas, ctx, rerender) {
  if (!ctx.data.ShareboxV2.isSupabaseConfigured()) return 'drive';
  // Supabase is primary once configured; Drive stays only as a fallback while
  // the live path is being confirmed. Absence of a stored choice means "use
  // the primary one".
  const backend = (await ctx.data.Settings.get('shareboxBackend')) || 'supabase';

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Backend' }));
  const group = el('div', { class: 'mer-toggle-group' }, [
    ['supabase', 'Supabase (primary)'], ['drive', 'Drive (fallback)'],
  ].map(([id, label]) => el('button', {
    type: 'button', class: backend === id ? 'is-active' : '', text: label,
    onclick: async () => { await ctx.data.Settings.set('shareboxBackend', id); rerender(); },
  })));
  canvas.append(group);
  canvas.append(el('p', { class: 'mer-muted', text: backend === 'supabase'
    ? 'Live Sharebox: sign in with Google, share a space with a friend, updates appear instantly. Drive is kept as a fallback until this is confirmed working end to end.'
    : 'Fallback: the original Drive shared-folder sync. Switch back to Supabase (primary) above once you’re done here.' }));
  return backend;
}

// ============================================================
// v1 — Drive-folder Sharebox (original, unchanged behavior)
// ============================================================

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

function addFormV1(ctx, name, rerender) {
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
        try {
          await ctx.data.ShareboxItems.create({ kind: 'link', url: normalizeUrl(urlIn.value), title: titleIn.value.trim(), urgency: state.urgency, postedBy });
          rerender();
        } catch (err) { alert(`Could not share link: ${err.message || err}`); }
      },
    }));
  } else if (state.kind === 'note') {
    const bodyIn = el('input', { type: 'text', placeholder: 'Write a note…' });
    fields.append(bodyIn, urgencySelect, el('button', {
      type: 'button', text: 'Share note',
      onclick: async () => {
        if (!bodyIn.value.trim()) return;
        try {
          await ctx.data.ShareboxItems.create({ kind: 'note', body: bodyIn.value.trim(), urgency: state.urgency, postedBy });
          rerender();
        } catch (err) { alert(`Could not share note: ${err.message || err}`); }
      },
    }));
  } else {
    const fileIn = el('input', { type: 'file' });
    fields.append(fileIn, urgencySelect, el('button', {
      type: 'button', text: 'Share file',
      onclick: async () => {
        const file = fileIn.files[0];
        if (!file) return;
        try {
          const item = await ctx.data.ShareboxItems.create({ kind: 'file', title: file.name, urgency: state.urgency, postedBy });
          await ctx.data.createShareboxFile(file, item.id);
          rerender();
        } catch (err) { alert(`Could not share file: ${err.message || err}`); }
      },
    }));
  }

  return el('div', {}, [kindToggle, fields]);
}

function itemCardV1(item, files, ctx, rerender) {
  const badge = el('span', { class: `mer-chip mer-urg-${item.urgency || 'normal'}`, text: (item.urgency || 'normal') });
  const meta = el('div', { class: 'mer-person-meta', text: `${item.postedBy || 'Someone'} · ${fmtWhen(item.updatedAt || item.createdAt)}` });

  let bodyEl;
  if (item.kind === 'link') {
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

async function renderShareboxV1(canvas, ctx, rerender) {
  teardownLive(); // v1 doesn't use Realtime; drop any lingering v2 subscription

  await renderConnect(canvas, ctx, rerender);

  const name = await ctx.data.Settings.get('shareboxName');
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Your name here' }));
  const nameInput = el('input', {
    type: 'text', value: name || '', placeholder: 'e.g. Alek',
    onchange: (e) => ctx.data.Settings.set('shareboxName', e.target.value.trim()),
  });
  canvas.append(el('div', { class: 'mer-person-form' }, [nameInput, el('span', { class: 'mer-muted', text: 'Shown next to things you post (no accounts — each person sets their own).' })]));

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add to Sharebox' }));
  canvas.append(addFormV1(ctx, name, rerender));

  const [items, files] = await Promise.all([ctx.data.ShareboxItems.list(), ctx.data.ShareboxFiles.list()]);
  canvas.append(el('div', { class: 'mer-subsection-label', text: `Shared (${items.length})` }));

  if (!items.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing shared yet.' }));
    return;
  }

  const list = el('div', { class: 'mer-people-list' });
  for (const item of sortItems(items)) list.append(itemCardV1(item, files, ctx, rerender));
  canvas.append(list);
}

// ============================================================
// v2 — Supabase Sharebox (Google sign-in, spaces, Realtime)
// ============================================================

function addFormV2(ctx, space, rerender) {
  const kindToggle = el('div', { class: 'mer-toggle-group' }, [
    ['link', '🔗 Link'], ['note', '📝 Note'], ['file', '📎 File'],
  ].map(([k, label]) => el('button', {
    type: 'button', class: state.kind === k ? 'is-active' : '', text: label,
    onclick: () => { state.kind = k; rerender(); },
  })));

  const urgencySelect = el('select', { onchange: (e) => { state.urgency = e.target.value; } },
    URGENCY.map((u) => el('option', { value: u.value, text: u.label, selected: u.value === state.urgency })));

  const fields = el('div', { class: 'mer-person-form' });

  if (state.kind === 'link') {
    const titleIn = el('input', { type: 'text', placeholder: 'Title (optional)' });
    const urlIn = el('input', { type: 'url', placeholder: 'https://…' });
    fields.append(urlIn, titleIn, urgencySelect, el('button', {
      type: 'button', text: 'Share link',
      onclick: async () => {
        if (!urlIn.value.trim()) return;
        try {
          await ctx.data.ShareboxV2.addItem({ spaceId: space.id, kind: 'link', url: normalizeUrl(urlIn.value), title: titleIn.value.trim(), urgency: state.urgency });
          rerender();
        } catch (err) { alert(`Could not share link: ${err.message || err}`); }
      },
    }));
  } else if (state.kind === 'note') {
    const bodyIn = el('input', { type: 'text', placeholder: 'Write a note…' });
    fields.append(bodyIn, urgencySelect, el('button', {
      type: 'button', text: 'Share note',
      onclick: async () => {
        if (!bodyIn.value.trim()) return;
        try {
          await ctx.data.ShareboxV2.addItem({ spaceId: space.id, kind: 'note', body: bodyIn.value.trim(), urgency: state.urgency });
          rerender();
        } catch (err) { alert(`Could not share note: ${err.message || err}`); }
      },
    }));
  } else {
    const fileIn = el('input', { type: 'file' });
    const btn = el('button', { type: 'button', text: 'Share file' });
    btn.onclick = async () => {
      const file = fileIn.files[0];
      if (!file) return;
      btn.disabled = true; btn.textContent = 'Uploading…';
      try {
        const storagePath = await ctx.data.ShareboxV2.uploadFile(space.id, file);
        await ctx.data.ShareboxV2.addItem({ spaceId: space.id, kind: 'file', title: file.name, urgency: state.urgency, storagePath });
        rerender();
      } catch (err) {
        btn.disabled = false; btn.textContent = 'Share file';
        alert(`Upload failed: ${err.message || err}`);
      }
    };
    fields.append(fileIn, urgencySelect, btn);
  }

  return el('div', {}, [kindToggle, fields]);
}

function itemCardV2(item, currentUserId, ctx, rerender) {
  const badge = el('span', { class: `mer-chip mer-urg-${item.urgency || 'normal'}`, text: (item.urgency || 'normal') });
  const meta = el('div', { class: 'mer-person-meta', text: `${item.postedBy || 'Someone'} · ${fmtWhen(item.updatedAt || item.createdAt)}` });

  let bodyEl;
  if (item.kind === 'link') {
    bodyEl = el('a', { href: normalizeUrl(item.url), target: '_blank', rel: 'noopener', text: item.title || item.url });
  } else if (item.kind === 'note') {
    bodyEl = el('div', { class: 'mer-person-name', text: item.body || '' });
  } else {
    const dl = el('button', {
      type: 'button', class: 'mer-reader-btn', text: 'Download',
      onclick: async () => {
        dl.disabled = true; dl.textContent = 'Fetching…';
        try {
          const url = await ctx.data.ShareboxV2.getFileUrl(item.storagePath);
          if (url) window.open(url, '_blank', 'noopener');
          dl.disabled = false; dl.textContent = 'Download';
        } catch (err) {
          dl.disabled = false; dl.textContent = 'Download';
          alert(`Could not fetch file: ${err.message || err}`);
        }
      },
    });
    bodyEl = el('div', { class: 'mer-person-name' }, [el('span', { text: item.title || 'file' }), dl]);
  }

  const children = [
    el('div', { class: 'mer-sharebox-icon', text: KIND_ICON[item.kind] || '•' }),
    el('div', { class: 'mer-person-info' }, [bodyEl, meta]),
    badge,
  ];
  // RLS only allows deleting your own items, so only offer × on those.
  if (item.postedById === currentUserId) {
    children.push(el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove',
      onclick: async () => {
        if (confirm('Remove this from Sharebox?')) { await ctx.data.ShareboxV2.removeItem(item.id); rerender(); }
      },
    }));
  }
  return el('div', { class: 'mer-person-card mer-sharebox-item' }, children);
}

// The create-a-space / join-a-space forms, reused for both first-run onboarding
// (when you belong to no spaces yet) and the always-available "new space or join
// another" control once you already have one. Creating selects the new space;
// joining selects the joined one, so you land where you just acted.
async function renderSpaceForms(ctx, displayName, rerender) {
  const V2 = ctx.data.ShareboxV2;
  const defaultName = (await ctx.data.Settings.get('shareboxName')) || displayName;
  const wrap = el('div', {});

  wrap.append(el('div', { class: 'mer-subsection-label', text: 'Create a space' }));
  const nameIn = el('input', { type: 'text', placeholder: 'Space name (e.g. “Alek & Sam”)' });
  const dispIn = el('input', { type: 'text', value: defaultName, placeholder: 'Your name in this space' });
  wrap.append(el('div', { class: 'mer-person-form' }, [nameIn, dispIn, el('button', {
    type: 'button', text: 'Create space',
    onclick: async () => {
      try {
        const space = await V2.createSpace(nameIn.value.trim() || 'Sharebox', dispIn.value.trim() || displayName);
        if (space?.id) await ctx.data.Settings.set('shareboxV2SpaceId', space.id);
        rerender();
      } catch (err) { alert(`Could not create space: ${err.message || err}`); }
    },
  })]));

  wrap.append(el('div', { class: 'mer-subsection-label', text: 'Or join a space' }));
  wrap.append(el('p', { class: 'mer-muted', text: 'Paste the space ID someone shared with you.' }));
  const joinIn = el('input', { type: 'text', placeholder: 'Space ID' });
  const joinDisp = el('input', { type: 'text', value: defaultName, placeholder: 'Your name in this space' });
  wrap.append(el('div', { class: 'mer-person-form' }, [joinIn, joinDisp, el('button', {
    type: 'button', text: 'Join space',
    onclick: async () => {
      const id = joinIn.value.trim();
      if (!id) return;
      try {
        await V2.joinSpace(id, joinDisp.value.trim() || displayName);
        await ctx.data.Settings.set('shareboxV2SpaceId', id);
        rerender();
      } catch (err) { alert(`Could not join space: ${err.message || err}`); }
    },
  })]));

  return wrap;
}

async function renderShareboxV2(canvas, ctx, rerender) {
  const V2 = ctx.data.ShareboxV2;
  ensureAuthWatch(ctx, rerender);

  let user;
  try {
    user = await V2.getCurrentUser();
  } catch (err) {
    teardownLive();
    canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: `Couldn’t reach Supabase: ${err.message || err}` }));
    return;
  }

  // --- Signed out: offer Google sign-in ---
  if (!user) {
    teardownLive();
    canvas.append(el('div', { class: 'mer-subsection-label', text: 'Sign in' }));
    canvas.append(el('p', { class: 'mer-muted', text: 'Sign in with Google to use the live Sharebox. You and your friend each sign in and join the same space — no shared folder needed.' }));
    canvas.append(el('div', { class: 'mer-toolbar' }, [
      el('button', {
        type: 'button', text: 'Sign in with Google',
        onclick: async () => {
          const res = await V2.signInWithGoogle();
          if (res?.error) alert(`Sign-in failed: ${res.error.message || res.error}`);
          // On success the browser redirects to Google; nothing else to do here.
        },
      }),
    ]));
    return;
  }

  // --- Signed in: identity + sign out ---
  const displayName = V2.displayNameOf(user);
  const idRow = el('div', { class: 'mer-person-form' }, [
    el('span', { class: 'mer-person-name', text: `Signed in as ${displayName}` }),
    el('button', {
      type: 'button', class: 'mer-reader-btn', text: 'Sign out',
      onclick: async () => { teardownLive(); await V2.signOut(); rerender(); },
    }),
  ]);
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Account' }));
  canvas.append(idRow);

  // --- Load spaces ---
  let spaces;
  try {
    spaces = await V2.getMySpaces();
  } catch (err) {
    teardownLive();
    canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: `Couldn’t load your spaces: ${err.message || err}` }));
    return;
  }

  // --- No spaces yet: first-run onboarding (create or join) ---
  if (!spaces.length) {
    teardownLive();
    canvas.append(el('p', { class: 'mer-muted', text: 'Create your own space, or join one someone shared with you.' }));
    canvas.append(await renderSpaceForms(ctx, displayName, rerender));
    return;
  }

  // --- Pick the active space (stored choice, else first) ---
  const storedId = await ctx.data.Settings.get('shareboxV2SpaceId');
  const space = spaces.find((s) => s.id === storedId) || spaces[0];
  if (space.id !== storedId) await ctx.data.Settings.set('shareboxV2SpaceId', space.id);

  if (spaces.length > 1) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: 'Space' }));
    const picker = el('select', {
      onchange: async (e) => { await ctx.data.Settings.set('shareboxV2SpaceId', e.target.value); rerender(); },
    }, spaces.map((s) => el('option', { value: s.id, text: s.name, selected: s.id === space.id })));
    canvas.append(el('div', { class: 'mer-person-form' }, [picker]));
  }

  // Always let the user spin up a new space or join another, collapsed so it
  // doesn't clutter the board. (When you have 0 spaces the onboarding above
  // shows these forms expanded instead.)
  const more = el('details', { class: 'mer-sharebox-more' }, [
    el('summary', { class: 'mer-subsection-label', text: '＋ New space or join another' }),
  ]);
  more.append(await renderSpaceForms(ctx, displayName, rerender));
  canvas.append(more);

  // --- Members + invite (share the space id) ---
  let members = [];
  try { members = await V2.getMembers(space.id); } catch { /* non-fatal */ }
  canvas.append(el('div', { class: 'mer-subsection-label', text: space.name }));
  if (members.length) {
    canvas.append(el('p', { class: 'mer-muted', text: `Members: ${members.map((m) => m.display_name).join(', ')}` }));
  }
  canvas.append(el('p', { class: 'mer-muted', text: 'Invite a friend: send them this space ID to paste into “Join a space”.' }));
  canvas.append(el('div', { class: 'mer-person-form' }, [
    el('input', { type: 'text', value: space.id, readonly: true, onclick: (e) => e.target.select() }),
    copyButton(() => space.id, 'Copy ID'),
  ]));

  // --- Add form ---
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Add to Sharebox' }));
  canvas.append(addFormV2(ctx, space, rerender));

  // --- Items (with live updates) ---
  let items;
  try {
    items = await V2.listItems(space.id);
  } catch (err) {
    teardownLive();
    canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: `Couldn’t load items: ${err.message || err}` }));
    return;
  }

  ensureLive(space.id, ctx, rerender); // Realtime: friend's changes refresh this view live

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Shared (${items.length}) · live` }));
  if (!items.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing shared yet.' }));
    return;
  }
  const list = el('div', { class: 'mer-people-list' });
  for (const item of sortItems(items)) list.append(itemCardV2(item, user.id, ctx, rerender));
  canvas.append(list);
}

// ============================================================
// Entry point
// ============================================================

export async function renderSharebox(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Sharebox' }));

  const backend = await renderBackendToggle(canvas, ctx, rerender);
  if (backend === 'supabase' && ctx.data.ShareboxV2.isSupabaseConfigured()) {
    await renderShareboxV2(canvas, ctx, rerender);
  } else {
    await renderShareboxV1(canvas, ctx, rerender);
  }
}

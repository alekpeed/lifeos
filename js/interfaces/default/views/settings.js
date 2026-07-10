import { el, fmtDate } from '../dom.js';

// --- Account (email/password + Google) ---
// App-wide identity, shared with Sharebox v2 (same Supabase auth session --
// signing in here also signs you into Sharebox). `mode` drives which form
// shows when signed out; 'reset' is entered automatically when a password
// -recovery email link lands back on the app.

let accountState = {
  authSub: null,
  mode: 'signin', // signin | signup | forgot | reset
  message: null, // { text, isError }
};

function ensureAccountAuthWatch(ctx, rerender) {
  if (accountState.authSub) return;
  accountState.authSub = ctx.data.Account.onAuthChange((_user, event) => {
    if (event === 'PASSWORD_RECOVERY') accountState.mode = 'reset';
    rerender();
  });
}

function accountMessage() {
  if (!accountState.message) return null;
  return el('p', { class: accountState.message.isError ? 'mer-muted mer-sync-error' : 'mer-muted', text: accountState.message.text });
}

function signInForm(ctx, rerender) {
  const emailInput = el('input', { type: 'email', placeholder: 'Email' });
  const passwordInput = el('input', { type: 'password', placeholder: 'Password' });
  const submit = async () => {
    if (!emailInput.value.trim() || !passwordInput.value) return;
    const res = await ctx.data.Account.signInWithEmail(emailInput.value.trim(), passwordInput.value);
    if (res?.error) { accountState.message = { text: res.error.message || String(res.error), isError: true }; rerender(); return; }
    accountState.message = null;
    rerender();
  };
  return el('div', { class: 'mer-person-form' }, [
    emailInput, passwordInput,
    el('button', { type: 'button', text: 'Sign in', onclick: submit }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: 'Forgot password?', onclick: () => { accountState.mode = 'forgot'; accountState.message = null; rerender(); } }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: 'Need an account? Sign up', onclick: () => { accountState.mode = 'signup'; accountState.message = null; rerender(); } }),
  ]);
}

function signUpForm(ctx, rerender) {
  const emailInput = el('input', { type: 'email', placeholder: 'Email' });
  const passwordInput = el('input', { type: 'password', placeholder: 'Password (min 6 characters)' });
  const submit = async () => {
    if (!emailInput.value.trim() || !passwordInput.value) return;
    const res = await ctx.data.Account.signUpWithEmail(emailInput.value.trim(), passwordInput.value);
    if (res?.error) { accountState.message = { text: res.error.message || String(res.error), isError: true }; rerender(); return; }
    accountState.message = res.needsConfirmation
      ? { text: 'Account created — check your email to confirm before signing in.' }
      : { text: 'Account created and signed in.' };
    accountState.mode = 'signin';
    rerender();
  };
  return el('div', { class: 'mer-person-form' }, [
    emailInput, passwordInput,
    el('button', { type: 'button', text: 'Sign up', onclick: submit }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: 'Already have an account? Sign in', onclick: () => { accountState.mode = 'signin'; accountState.message = null; rerender(); } }),
  ]);
}

function forgotForm(ctx, rerender) {
  const emailInput = el('input', { type: 'email', placeholder: 'Email' });
  const submit = async () => {
    if (!emailInput.value.trim()) return;
    const res = await ctx.data.Account.sendPasswordReset(emailInput.value.trim());
    if (res?.error) { accountState.message = { text: res.error.message || String(res.error), isError: true }; rerender(); return; }
    accountState.message = { text: 'If that email has an account, a reset link is on its way.' };
    accountState.mode = 'signin';
    rerender();
  };
  return el('div', { class: 'mer-person-form' }, [
    emailInput,
    el('button', { type: 'button', text: 'Send reset link', onclick: submit }),
    el('button', { type: 'button', class: 'mer-reader-btn', text: '← Back to sign in', onclick: () => { accountState.mode = 'signin'; accountState.message = null; rerender(); } }),
  ]);
}

function resetPasswordForm(ctx, rerender) {
  const passwordInput = el('input', { type: 'password', placeholder: 'New password (min 6 characters)' });
  const submit = async () => {
    if (!passwordInput.value) return;
    const res = await ctx.data.Account.updatePassword(passwordInput.value);
    if (res?.error) { accountState.message = { text: res.error.message || String(res.error), isError: true }; rerender(); return; }
    accountState.message = { text: 'Password updated.' };
    accountState.mode = 'signin';
    rerender();
  };
  return el('div', { class: 'mer-person-form' }, [
    passwordInput,
    el('button', { type: 'button', text: 'Set new password', onclick: submit }),
  ]);
}

function signedInPanel(user, ctx, rerender) {
  const nameInput = el('input', { type: 'text', value: '', placeholder: 'Display name' });
  ctx.data.Account.getProfile()
    .then((profile) => { if (profile) nameInput.value = profile.display_name || ''; })
    .catch(() => {}); // best-effort -- the sign-out/sign-in controls above still work if this fails

  const saveNameBtn = el('button', {
    type: 'button', text: 'Save',
    onclick: async () => {
      try {
        await ctx.data.Account.updateDisplayName(nameInput.value.trim());
        accountState.message = { text: 'Display name updated.' };
      } catch (err) {
        accountState.message = { text: err.message || String(err), isError: true };
      }
      rerender();
    },
  });

  return el('div', {}, [
    el('div', { class: 'mer-person-form' }, [
      el('span', { class: 'mer-person-name', text: `Signed in as ${ctx.data.Account.displayNameOf(user)}` }),
      el('span', { class: 'mer-person-meta', text: user.email || '' }),
      el('button', { type: 'button', class: 'mer-reader-btn', text: 'Sign out', onclick: async () => { await ctx.data.Account.signOut(); rerender(); } }),
    ]),
    el('label', { class: 'mer-field' }, [el('span', { text: 'Display name' }), nameInput]),
    saveNameBtn,
  ]);
}

async function renderAccountSection(canvas, ctx, rerender) {
  if (!ctx.data.Account.isSupabaseConfigured()) return;
  ensureAccountAuthWatch(ctx, rerender);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Account' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Sign in with email/password or Google. This is the same account used by Sharebox, and the foundation for per-user features (AI Daily Paper, notifications) as they land.' }));

  const msg = accountMessage();
  if (msg) canvas.append(msg);

  if (accountState.mode === 'reset') {
    canvas.append(resetPasswordForm(ctx, rerender));
    return;
  }

  let user;
  try {
    user = await ctx.data.Account.getCurrentUser();
  } catch (err) {
    canvas.append(el('p', { class: 'mer-muted mer-sync-error', text: `Couldn't reach Supabase: ${err.message || err}` }));
    return;
  }
  if (user) {
    canvas.append(signedInPanel(user, ctx, rerender));
    return;
  }

  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('button', {
      type: 'button', text: 'Sign in with Google',
      onclick: async () => {
        const res = await ctx.data.Account.signInWithGoogle();
        if (res?.error) { accountState.message = { text: res.error.message || String(res.error), isError: true }; rerender(); }
      },
    }),
  ]));

  if (accountState.mode === 'signup') canvas.append(signUpForm(ctx, rerender));
  else if (accountState.mode === 'forgot') canvas.append(forgotForm(ctx, rerender));
  else canvas.append(signInForm(ctx, rerender));
}

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

async function renderCalendarSection(canvas, ctx, rerender) {
  const [state, horizon] = await Promise.all([
    ctx.data.getCalendarState(),
    ctx.data.Settings.get('calendarHorizonDays'),
  ]);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Google Calendar' }));
  canvas.append(el('p', { class: 'mer-muted', text: `Mirrors your due-soon items — open tasks, unpaid bills, assignments and document expiries — into a dedicated "Life OS" calendar, so they show up on your phone and desktop calendar with reminders. It's a one-way push: Life OS only ever writes to the calendar it creates and never reads or changes your other calendars. Uses the calendar.app.created scope, plus a read-only scope just to see your calendars' names (so it can find its own instead of making a duplicate) — never their events. Independent of Drive sync — you can use either on its own.` }));

  const horizonInput = el('label', { class: 'mer-setting' }, [
    el('span', { text: 'Push items due within (days)' }),
    el('input', {
      type: 'number', min: '7', max: '365', value: horizon,
      onchange: (e) => ctx.data.Settings.set('calendarHorizonDays', Number(e.target.value) || 90),
    }),
  ]);
  canvas.append(horizonInput);

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (text, isError) => { status.textContent = text; status.classList.toggle('mer-sync-error', !!isError); };
  setStatus(state.enabled ? `Last pushed: ${fmtSyncTime(state.lastSyncedAt)}` : 'Not connected yet.');

  const runPush = async (fn, label) => {
    row.querySelectorAll('button').forEach((b) => (b.disabled = true));
    setStatus(`${label}…`);
    try {
      const res = await fn();
      const bits = [];
      if (res?.added) bits.push(`${res.added} added`);
      if (res?.updated) bits.push(`${res.updated} updated`);
      if (res?.removed) bits.push(`${res.removed} removed`);
      setStatus(`Pushed ${fmtSyncTime(new Date().toISOString())}${bits.length ? ` (${bits.join(', ')})` : ''}.`);
      await rerender();
    } catch (err) {
      setStatus(err.message || String(err), true);
      row.querySelectorAll('button').forEach((b) => (b.disabled = false));
    }
  };

  const connectBtn = el('button', {
    type: 'button', text: state.enabled ? 'Sync calendar now' : 'Connect Google Calendar',
    onclick: () => runPush(state.enabled ? ctx.data.syncCalendarNow : ctx.data.connectCalendar, state.enabled ? 'Pushing' : 'Connecting'),
  });
  const buttons = [connectBtn];
  if (state.enabled) {
    buttons.push(el('button', {
      type: 'button', text: 'Disconnect calendar',
      onclick: async () => { await ctx.data.disconnectCalendar(); await rerender(); },
    }));
  }
  const row = el('div', { class: 'mer-toolbar' }, buttons);
  canvas.append(row, status);
}

// --- AI Assistant (Claude, direct browser-to-API) ---

async function renderAiAssistantSection(canvas, ctx, rerender) {
  const [apiKey, model] = await Promise.all([
    ctx.data.Settings.get('anthropicApiKey'),
    ctx.data.Settings.get('anthropicModel'),
  ]);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'AI Assistant' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Your own Anthropic API key, used to chat with Claude directly from this browser -- no server in between. Kept device-local (not synced to Drive or the cloud), sent only to api.anthropic.com.' }));

  const keyInput = el('input', { type: 'password', value: apiKey, placeholder: 'sk-ant-…', onchange: (e) => ctx.data.Settings.set('anthropicApiKey', e.target.value.trim()) });
  const modelInput = el('input', { type: 'text', value: model, placeholder: 'claude-sonnet-5', onchange: (e) => ctx.data.Settings.set('anthropicModel', e.target.value.trim() || 'claude-sonnet-5') });

  canvas.append(
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Anthropic API key' }), keyInput]),
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Model' }), modelInput]),
  );
}

// --- Telegram (send-only) ---

async function renderTelegramSection(canvas, ctx, rerender) {
  const [botToken, chatId] = await Promise.all([
    ctx.data.Settings.get('telegramBotToken'),
    ctx.data.Settings.get('telegramChatId'),
  ]);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Telegram' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Send-only: the app can message you through a bot you create yourself (via @BotFather in Telegram). There\'s no listener for incoming messages -- a static PWA can\'t run one when it\'s not open.' }));

  const tokenInput = el('input', { type: 'password', value: botToken, placeholder: 'Bot token from @BotFather', onchange: (e) => ctx.data.Settings.set('telegramBotToken', e.target.value.trim()) });
  const chatIdInput = el('input', { type: 'text', value: chatId, placeholder: 'Your chat ID', onchange: (e) => ctx.data.Settings.set('telegramChatId', e.target.value.trim()) });

  canvas.append(
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Bot token' }), tokenInput]),
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Chat ID' }), chatIdInput]),
  );

  const status = el('p', { class: 'mer-muted' });
  canvas.append(el('div', { class: 'mer-toolbar' }, [
    el('button', {
      type: 'button', text: 'Send test message',
      onclick: async () => {
        status.textContent = 'Sending…';
        status.classList.remove('mer-sync-error');
        try {
          await ctx.data.sendDigestToTelegram('Life OS: this is a test message. If you got this, Telegram is wired up correctly.');
          status.textContent = 'Sent! Check Telegram.';
        } catch (err) {
          status.textContent = err.message || String(err);
          status.classList.add('mer-sync-error');
        }
      },
    }),
  ]), status);
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

  await renderAccountSection(canvas, ctx, rerender || (() => {}));
  await renderSyncSection(canvas, ctx, rerender || (() => {}));
  await renderCalendarSection(canvas, ctx, rerender || (() => {}));
  await renderAiAssistantSection(canvas, ctx, rerender || (() => {}));
  await renderTelegramSection(canvas, ctx, rerender || (() => {}));
}

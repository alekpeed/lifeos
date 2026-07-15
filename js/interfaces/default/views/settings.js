import { el, fmtDate } from '../dom.js';
import { isNativePlatform, hasCapability } from '../../../native/capabilities.js';
import { canNotify, notifyPermissionState, requestNotifyPermission } from '../../../native/notify.js';
import { refreshDeviceReminders, refreshNextUp } from '../../../native/native-boot.js';
import { canImportContacts, requestContactsPermission, importPhoneContacts } from '../../../native/contacts.js';

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

async function renderSupabaseSyncSection(canvas, ctx, rerender) {
  const state = await ctx.data.getSupabaseSyncState();
  if (!state.configured) return; // no Supabase project wired -> hide entirely

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Cloud sync (Supabase)' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'The newer sync path: your data reconciles between devices through the app\'s Supabase backend instead of Google Drive, scoped to your signed-in account. Same last-write-wins behavior as Drive sync, and your device preferences (theme, density, active interface) still stay local. Being rolled out alongside Drive sync — you can run either, or both, during the transition.' }));

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (text, isError) => { status.textContent = text; status.classList.toggle('mer-sync-error', !!isError); };

  if (!state.signedIn) {
    setStatus('Sign in with your account above to enable cloud sync.');
    canvas.append(status);
    return;
  }
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
    type: 'button', text: state.enabled ? 'Sync now' : 'Turn on cloud sync',
    onclick: () => runSync(state.enabled ? ctx.data.syncSupabaseNow : ctx.data.connectSupabaseSync, state.enabled ? 'Syncing' : 'Connecting'),
  });
  const buttons = [connectBtn];
  if (state.enabled) {
    buttons.push(el('button', {
      type: 'button', text: 'Turn off',
      onclick: async () => { await ctx.data.disconnectSupabaseSync(); await rerender(); },
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

// --- AI Assistant (provider-switchable, direct browser-to-API) ---
// A toggle picks the active provider (ctx.data.AI_PROVIDERS -- currently
// Gemini and Claude, both of which support calling straight from the
// browser with no backend; OpenAI doesn't support that at all, no CORS
// headers on browser-origin requests, so it's not offered here as a toggle
// option until/unless a proxy server exists in front of it). Both
// providers' key/model fields stay filled in even when inactive, so
// flipping the toggle never requires re-entering a key.
//
// The toggle itself is gated behind a tap gesture on the section label (10
// clicks, Android-hidden-developer-options style) so it doesn't clutter the
// page for normal use -- only the currently active provider's key/model
// fields show unconditionally, since those are needed for everyday setup,
// not just switching. devUnlocked is in-memory only (module state, not a
// Settings value): it resets on reload by design, an actual "gate," not a
// persisted preference.
let aiAssistantState = { devTapCount: 0, devUnlocked: false };

async function renderAiAssistantSection(canvas, ctx, rerender) {
  const activeId = (await ctx.data.Settings.get('aiProvider')) || 'gemini';

  canvas.append(el('div', {
    class: 'mer-subsection-label', text: 'AI Assistant',
    onclick: () => {
      if (aiAssistantState.devUnlocked) return;
      aiAssistantState.devTapCount += 1;
      if (aiAssistantState.devTapCount >= 10) { aiAssistantState.devUnlocked = true; rerender(); }
    },
  }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Picks which model powers the AI Assistant chat, the Daily Paper editorial, and Library of Babel story generation. Each provider uses your own API key, called directly from this browser -- no server in between. Keys are device-local (not synced to Drive or the cloud).' }));

  if (aiAssistantState.devUnlocked) {
    const group = el('div', { class: 'mer-toggle-group' }, Object.entries(ctx.data.AI_PROVIDERS).map(([id, meta]) => el('button', {
      type: 'button', class: activeId === id ? 'is-active' : '', text: meta.label,
      onclick: async () => { await ctx.data.Settings.set('aiProvider', id); rerender(); },
    })));
    canvas.append(group);
  }

  const active = ctx.data.AI_PROVIDERS[activeId];
  const [apiKey, model] = await Promise.all([
    ctx.data.Settings.get(active.keySetting),
    ctx.data.Settings.get(active.modelSetting),
  ]);
  const keyInput = el('input', { type: 'password', value: apiKey, placeholder: active.keyPlaceholder, onchange: (e) => ctx.data.Settings.set(active.keySetting, e.target.value.trim()) });
  const modelInput = el('input', { type: 'text', value: model, placeholder: active.modelDefault, onchange: (e) => ctx.data.Settings.set(active.modelSetting, e.target.value.trim() || active.modelDefault) });

  canvas.append(
    el('label', { class: 'mer-setting' }, [el('span', { text: `${active.label} API key` }), keyInput]),
    el('label', { class: 'mer-setting' }, [el('span', { text: 'Model' }), modelInput]),
  );
}

// --- App Lock (WebAuthn platform authenticator) ---

async function renderAppLockSection(canvas, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'App Lock' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Requires Face ID / fingerprint / device PIN to open Life OS. Purely a local gate on this device -- there\'s no password and nothing is sent anywhere, so if you lose access to your device\'s biometrics the only way back in is clearing site data (which erases anything not synced to Drive).' }));

  const available = await ctx.data.isAppLockAvailable();
  if (!available) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Not available -- this device or browser has no biometric/PIN authenticator to use.' }));
    return;
  }

  const enabled = await ctx.data.Settings.get('appLockEnabled');
  const status = el('p', { class: 'mer-muted' });

  if (enabled) {
    status.textContent = 'App Lock is on.';
    canvas.append(status, el('div', { class: 'mer-toolbar' }, [
      el('button', {
        type: 'button', text: 'Turn off',
        onclick: async () => {
          await ctx.data.Settings.set('appLockEnabled', false);
          await ctx.data.Settings.set('appLockCredentialId', '');
          await rerender();
        },
      }),
    ]));
    return;
  }

  canvas.append(status, el('div', { class: 'mer-toolbar' }, [
    el('button', {
      type: 'button', text: 'Set up App Lock',
      onclick: async (e) => {
        e.target.disabled = true;
        status.textContent = 'Follow the prompt…';
        try {
          const credentialId = await ctx.data.enrollAppLock();
          await ctx.data.Settings.set('appLockCredentialId', credentialId);
          await ctx.data.Settings.set('appLockEnabled', true);
          await rerender();
        } catch (err) {
          status.textContent = err.name === 'NotAllowedError' ? 'Cancelled.' : (err.message || String(err));
          e.target.disabled = false;
        }
      },
    }),
  ]));
}

// --- Rules & automation engine v1 ---

function automationToggle(label, description, key, checked, ctx, rerender) {
  return el('label', { class: 'mer-setting' }, [
    el('span', {}, [
      el('input', {
        type: 'checkbox', checked,
        onchange: async (e) => { await ctx.data.Settings.set(key, e.target.checked); rerender(); },
      }),
      document.createTextNode(` ${label}`),
    ]),
    el('span', { class: 'mer-muted', text: description }),
  ]);
}

async function renderAutomationsSection(canvas, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Automations' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'A small, fixed set of built-in automations -- not a general rule-builder. Each is off by default, since it mutates your data on your own behalf. Runs once each time the app opens (no background execution while it\'s closed).' }));

  const [habitOn, docOn] = await Promise.all([
    ctx.data.Settings.get('automationHabitMilestoneEnabled'),
    ctx.data.Settings.get('automationDocumentRenewalEnabled'),
  ]);

  canvas.append(
    automationToggle('Log a Milestone when a habit streak hits 7 / 30 / 100 / 365 days', 'Creates a Milestones entry once per threshold, per habit.', 'automationHabitMilestoneEnabled', !!habitOn, ctx, rerender),
    automationToggle('Create a "Renew" task when a document is expiring or expired', 'Creates a Task due on the document\'s expiry date; updating the document\'s expiry (i.e. actually renewing it) lets the next cycle fire again.', 'automationDocumentRenewalEnabled', !!docOn, ctx, rerender),
  );
}

// --- Life as Music ---

async function renderLifeMusicSection(canvas, ctx, rerender) {
  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Life as Music' }));
  const on = !!(await ctx.data.Settings.get('ambientMusicEnabled'));
  canvas.append(automationToggle(
    'Play your life back as ambient background music',
    'A short, quiet chord loop generated from your own numbers (tasks done, habit check-ins, books finished, recipes cooked, places visited, contacts) -- regenerates as you go, no screen of its own.',
    'ambientMusicEnabled', on, ctx, rerender,
  ));
}

// --- Device reminders (native local notifications) ---
// Native-only. On the web this section is hidden entirely -- Web Push above is
// the browser's equivalent. These are scheduled on the phone itself, so they
// fire offline with the app closed and need no server.

// Opt-in "next up" ticker: a persistent notification always showing the top
// Briefing item. Toggling it flips the setting and immediately shows/clears the
// ticker via refreshNextUp (which reads the same setting).
async function nextUpTickerToggle(ctx) {
  const on = !!(await ctx.data.Settings.get('nextUpTickerEnabled'));
  return el('label', { class: 'mer-setting' }, [
    el('span', {}, [
      el('input', {
        type: 'checkbox', checked: on,
        onchange: async (e) => {
          await ctx.data.Settings.set('nextUpTickerEnabled', e.target.checked);
          await refreshNextUp();
        },
      }),
      document.createTextNode(' Pinned “next up” ticker'),
    ]),
    el('span', { class: 'mer-muted', text: 'Keep a persistent notification showing your single most important item (top of the Briefing). Updates each time the app opens.' }),
  ]);
}

async function renderNativeRemindersSection(canvas, ctx, rerender) {
  if (!isNativePlatform() || !canNotify()) return;

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Device reminders' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'On-device reminders for bills and due items — scheduled right on your phone, so they fire even offline with the app closed. No server involved.' }));

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (text, isError) => { status.textContent = text; status.classList.toggle('mer-sync-error', !!isError); };

  const perm = await notifyPermissionState();
  if (perm === 'granted') {
    const n = await refreshDeviceReminders();
    setStatus(`On — ${n} upcoming reminder${n === 1 ? '' : 's'} scheduled on this device.`);
    canvas.append(status);
    canvas.append(await nextUpTickerToggle(ctx));
    return;
  }
  if (perm === 'denied') {
    setStatus('Notifications are blocked for LifeOS in your phone settings — allow them there, then reload.', true);
    canvas.append(status);
    return;
  }

  setStatus('Off.');
  const btn = el('button', {
    type: 'button', text: 'Turn on device reminders',
    onclick: async () => {
      btn.disabled = true;
      setStatus('Requesting permission…');
      try {
        const ok = await requestNotifyPermission();
        if (ok) {
          const n = await refreshDeviceReminders();
          setStatus(`On — ${n} reminder${n === 1 ? '' : 's'} scheduled on this device.`);
        } else {
          setStatus('Permission not granted.', true);
          btn.disabled = false;
        }
      } catch (err) {
        setStatus(err.message || String(err), true);
        btn.disabled = false;
      }
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [btn]), status);
}

// --- Phone contacts import (native only) ---
// Native-only. One-tap pull of the device address book into the Contacts
// module, deduped by name so re-running is safe. Hidden entirely on web/iOS.

async function renderNativeContactsSection(canvas, ctx, rerender) {
  if (!isNativePlatform() || !canImportContacts()) return;

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Phone contacts' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Import your phone’s contacts into the Contacts module in one tap. Anyone already in Contacts by the same name is skipped, so it’s safe to run again.' }));

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (text, isError) => { status.textContent = text; status.classList.toggle('mer-sync-error', !!isError); };

  const btn = el('button', {
    type: 'button', text: 'Import phone contacts',
    onclick: async () => {
      btn.disabled = true;
      setStatus('Requesting permission…');
      try {
        const ok = await requestContactsPermission();
        if (!ok) { setStatus('Permission not granted.', true); btn.disabled = false; return; }
        setStatus('Importing…');
        const { imported, skipped } = await importPhoneContacts();
        setStatus(`Imported ${imported} contact${imported === 1 ? '' : 's'}${skipped ? `, skipped ${skipped} (already present or unnamed)` : ''}.`);
        btn.disabled = false;
        rerender();
      } catch (err) {
        setStatus(err.message || String(err), true);
        btn.disabled = false;
      }
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [btn]), status);
}

// --- Charging-cable evening ritual (native only) ---
// When you plug in at night with the app open, LifeOS offers to read your
// evening Briefing aloud. Opt-in, once per day. Hidden on web/iOS.

async function renderNativeChargingSection(canvas, ctx, rerender) {
  if (!isNativePlatform() || !hasCapability('battery')) return;

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Evening ritual' }));
  const on = !!(await ctx.data.Settings.get('chargingRitualEnabled'));
  canvas.append(automationToggle(
    'Plug-in-at-night evening briefing',
    'When you plug the phone in during the evening with LifeOS open, it offers to read your Briefing aloud as an end-of-day check-in. Once per day.',
    'chargingRitualEnabled', on, ctx, rerender,
  ));
}

// --- Web Push (real background notifications) ---

async function renderPushSection(canvas, ctx, rerender) {
  const state = await ctx.data.getPushState();
  if (!state.supported) return; // browser can't do Web Push -> hide entirely

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Push notifications' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Real background alerts — due bills and streak reminders that reach you even when Life OS is closed. Requires being signed in (so the server knows where to send). On iPhone, this only works if you\'ve added Life OS to your Home Screen.' }));

  const status = el('p', { class: 'mer-muted' });
  const setStatus = (text, isError) => { status.textContent = text; status.classList.toggle('mer-sync-error', !!isError); };

  if (!state.configured) {
    setStatus('Push isn\'t set up on the server yet (no VAPID key / Supabase backend).');
    canvas.append(status);
    return;
  }
  if (!state.signedIn) {
    setStatus('Sign in with your account above to enable push notifications.');
    canvas.append(status);
    return;
  }
  if (state.permission === 'denied') {
    setStatus('Notifications are blocked for this site in your browser settings — allow them there first, then reload.', true);
    canvas.append(status);
    return;
  }

  setStatus(state.subscribed ? 'On — this device will receive background alerts.' : 'Off.');

  const toggleBtn = el('button', {
    type: 'button', text: state.subscribed ? 'Turn off' : 'Turn on push notifications',
    onclick: async () => {
      toggleBtn.disabled = true;
      setStatus(state.subscribed ? 'Turning off…' : 'Requesting permission…');
      try {
        if (state.subscribed) await ctx.data.disablePush();
        else await ctx.data.enablePush();
        await rerender();
      } catch (err) {
        setStatus(err.message || String(err), true);
        toggleBtn.disabled = false;
      }
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [toggleBtn]), status);
}

// --- Telegram (send-only) ---

async function renderTelegramSection(canvas, ctx, rerender) {
  const [botToken, chatId] = await Promise.all([
    ctx.data.Settings.get('telegramBotToken'),
    ctx.data.Settings.get('telegramChatId'),
  ]);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Telegram' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Message yourself through a bot you create (via @BotFather in Telegram). The send path (below) works from any device with the token + chat ID; two-way chat is further down.' }));

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

  // --- Two-way chat (linking) ---
  const linkState = await ctx.data.getTelegramLinkState();
  if (!linkState.configured) return;

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Two-way chat' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'Text your bot to capture ideas and tasks, or ask what\'s due — from anywhere, even with the app closed. It replies and files things straight into Life OS. Requires being signed in.' }));

  const tgStatus = el('p', { class: 'mer-muted' });
  if (!linkState.signedIn) {
    tgStatus.textContent = 'Sign in with your account above to connect.';
    canvas.append(tgStatus);
    return;
  }
  if (linkState.linked) {
    tgStatus.textContent = 'Connected — text your bot /help to see what it can do.';
    canvas.append(el('div', { class: 'mer-toolbar' }, [
      el('button', { type: 'button', text: 'Disconnect', onclick: async () => { await ctx.data.unlinkTelegram(); await rerender(); } }),
    ]), tgStatus);
    return;
  }

  const connectBtn = el('button', {
    type: 'button', text: 'Connect Telegram',
    onclick: async () => {
      connectBtn.disabled = true;
      tgStatus.textContent = 'Generating link…';
      tgStatus.classList.remove('mer-sync-error');
      try {
        const url = await ctx.data.createTelegramDeepLink(botToken);
        tgStatus.textContent = '';
        tgStatus.append(el('a', { href: url, target: '_blank', rel: 'noopener', text: 'Tap to open your bot and connect →' }));
      } catch (err) {
        tgStatus.textContent = err.message || String(err);
        tgStatus.classList.add('mer-sync-error');
        connectBtn.disabled = false;
      }
    },
  });
  canvas.append(el('div', { class: 'mer-toolbar' }, [connectBtn]), tgStatus);
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
  await renderSupabaseSyncSection(canvas, ctx, rerender || (() => {}));
  await renderCalendarSection(canvas, ctx, rerender || (() => {}));
  await renderAiAssistantSection(canvas, ctx, rerender || (() => {}));
  await renderAppLockSection(canvas, ctx, rerender || (() => {}));
  await renderAutomationsSection(canvas, ctx, rerender || (() => {}));
  await renderPushSection(canvas, ctx, rerender || (() => {}));
  await renderNativeRemindersSection(canvas, ctx, rerender || (() => {}));
  await renderNativeContactsSection(canvas, ctx, rerender || (() => {}));
  await renderNativeChargingSection(canvas, ctx, rerender || (() => {}));
  await renderLifeMusicSection(canvas, ctx, rerender || (() => {}));
  await renderTelegramSection(canvas, ctx, rerender || (() => {}));
}

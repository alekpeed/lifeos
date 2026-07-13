// Notifications — a single place that pulls together what actually needs
// your attention: the existing due-soon/overdue feed (tasks, bills,
// assignments, documents -- same computation Dashboard already uses, see
// getDueSoonFeed in js/data/api.js) plus, if you're signed in and in a
// Sharebox space, activity posted by OTHER members since you last checked
// (the one genuinely per-account signal the app has -- everything else is
// local-first data with no real second "user" to notify). Viewing this page
// marks Sharebox activity as seen; there's no separate unread badge
// elsewhere in the nav, so this is a page you check, not a push alert (see
// "Real background push" in FUTURE_FEATURES.md for the not-yet-built
// alternative).

import { el, fmtDate } from '../dom.js';

const MODULE_LABELS = { tasks: 'Tasks', bills: 'Finance', assignments: 'Education', documents: 'Documents' };

function dueSoonRow(item, ctx) {
  return el('li', {
    class: item.overdue ? 'mer-feed-item is-overdue mer-feed-item-clickable' : 'mer-feed-item mer-feed-item-clickable',
    onclick: () => ctx.navigate(item.module),
  }, [
    el('span', { class: 'mer-feed-module', text: MODULE_LABELS[item.module] || item.module }),
    el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
    el('span', { class: 'mer-feed-date', text: item.overdue ? `Was due ${fmtDate(item.dueDate)}` : fmtDate(item.dueDate) }),
  ]);
}

function shareboxRow(item, ctx) {
  return el('li', {
    class: 'mer-feed-item mer-feed-item-clickable',
    onclick: () => ctx.navigate('sharebox'),
  }, [
    el('span', { class: 'mer-feed-module', text: item.postedBy }),
    el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
    el('span', { class: 'mer-feed-date', text: fmtDate(item.createdAt) }),
  ]);
}

async function getShareboxActivitySinceLastSeen(ctx, lastSeenAt) {
  if (!ctx.data.ShareboxV2.isSupabaseConfigured()) return [];
  const user = await ctx.data.ShareboxV2.getCurrentUser();
  if (!user) return [];
  const spaces = await ctx.data.ShareboxV2.getMySpaces();
  const perSpace = await Promise.all(spaces.map((s) => ctx.data.ShareboxV2.listItems(s.id)));
  return perSpace.flat()
    .filter((item) => item.postedById !== user.id && (!lastSeenAt || item.createdAt > lastSeenAt))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export async function renderNotifications(canvas, ctx) {
  canvas.append(el('h1', { text: 'Notifications' }));
  canvas.append(el('p', { class: 'mer-muted', text: 'What needs your attention, in one place.' }));

  const lastSeenAt = await ctx.data.Settings.get('notificationsLastSeenAt');
  const [dueSoon, shareboxActivity] = await Promise.all([
    ctx.data.getDueSoonFeed(),
    getShareboxActivitySinceLastSeen(ctx, lastSeenAt),
  ]);

  canvas.append(el('div', { class: 'mer-subsection-label', text: `Due soon & overdue (${dueSoon.length})` }));
  if (!dueSoon.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing due soon.' }));
  } else {
    const list = el('ul', { class: 'mer-feed' });
    for (const item of dueSoon) list.append(dueSoonRow(item, ctx));
    canvas.append(list);
  }

  const signedIn = await ctx.data.ShareboxV2.getCurrentUser();
  if (shareboxActivity.length || signedIn) {
    canvas.append(el('div', { class: 'mer-subsection-label', text: `Sharebox activity (${shareboxActivity.length})` }));
    if (!shareboxActivity.length) {
      canvas.append(el('p', { class: 'mer-muted', text: 'Nothing new since you last checked.' }));
    } else {
      const list = el('ul', { class: 'mer-feed' });
      for (const item of shareboxActivity) list.append(shareboxRow(item, ctx));
      canvas.append(list);
    }
  }

  await ctx.data.Settings.set('notificationsLastSeenAt', new Date().toISOString());
}

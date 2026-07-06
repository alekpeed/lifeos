import { el, fmtDate } from '../dom.js';

export async function renderDashboard(canvas, ctx) {
  const billDueSoonDays = await ctx.data.Settings.get('billDueSoonDays');
  const feed = await ctx.data.getDueSoonFeed(7, billDueSoonDays);
  canvas.append(el('h1', { text: 'Today' }));
  if (!feed.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing due in the next 7 days.' }));
    return;
  }
  const list = el('ul', { class: 'mer-feed' });
  for (const item of feed) {
    list.append(
      el('li', { class: item.overdue ? 'mer-feed-item is-overdue' : 'mer-feed-item' }, [
        el('span', { class: 'mer-feed-module', text: item.module }),
        el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
        el('span', { class: 'mer-feed-date', text: fmtDate(item.dueDate) }),
      ])
    );
  }
  canvas.append(list);
}

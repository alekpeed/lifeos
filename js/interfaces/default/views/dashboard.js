import { el, fmtDate } from '../dom.js';

let state = {
  surprise: null,
};

function surpriseSection(ctx, rerender) {
  const button = el('button', {
    type: 'button', text: '🎲 Surprise me',
    onclick: async () => { state.surprise = await ctx.data.getSurpriseMe(); rerender(); },
  });
  if (!state.surprise) return el('div', {}, [button]);

  return el('div', {}, [
    button,
    el('p', {}, [
      el('span', { class: 'mer-chip', text: state.surprise.kind }),
      el('span', { text: ` ${state.surprise.title}` }),
      el('button', { type: 'button', text: 'Go there →', onclick: () => ctx.navigate(state.surprise.module) }),
    ]),
  ]);
}

function onThisDaySection(items) {
  if (!items.length) return null;
  const list = el('ul', { class: 'mer-feed' });
  for (const item of items) {
    list.append(el('li', { class: 'mer-feed-item' }, [
      el('span', { class: 'mer-feed-module', text: item.kind }),
      el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
      el('span', { class: 'mer-feed-date', text: item.year }),
    ]));
  }
  return el('div', {}, [el('div', { class: 'mer-subsection-label', text: 'On this day' }), list]);
}

export async function renderDashboard(canvas, ctx, rerender) {
  const [billDueSoonDays, documentExpiryDays, onThisDay] = await Promise.all([
    ctx.data.Settings.get('billDueSoonDays'),
    ctx.data.Settings.get('documentExpiryDays'),
    ctx.data.getOnThisDay(),
  ]);
  const feed = await ctx.data.getDueSoonFeed(7, billDueSoonDays, documentExpiryDays);
  canvas.append(el('h1', { text: 'Today' }));

  if (!feed.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing due in the next 7 days.' }));
  } else {
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

  const onThisDayEl = onThisDaySection(onThisDay);
  if (onThisDayEl) canvas.append(onThisDayEl);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Not sure what to do?' }));
  canvas.append(surpriseSection(ctx, rerender));
}
